# coupon-fcfs — 선착순 쿠폰 발급 (Race Condition 재현/해결)

## 문제
"100개 한정 쿠폰"에 1000명이 동시 요청하면 단순 `SELECT COUNT → INSERT` 구조는 재고 이상으로 쿠폰이 발급되는
race condition이 발생한다. 이걸 일부러 재현하고, Redis 원자적 연산으로 해결한다.

## 목표
- **1단계 (재현)**: DB 비관적 락 없이 단순 카운트 체크로 구현 → 동시 요청 시 초과 발급 확인
- **2단계 (해결)**: Redis Lua 스크립트로 "재고 확인 + 차감"을 원자적(atomic) 단일 연산으로 처리
- **3단계 (검증)**: k6로 동시 요청 재현, 초과 발급 0건 확인 + 처리량(TPS) 측정

## 구현 범위 (완료)
- `POST /api/coupon/{couponId}/issue-naive` — race condition 있는 버전 (비교용)
- `POST /api/coupon/{couponId}/issue` — Redis Lua atomic decrement 버전
- `GET /api/coupon/{couponId}/stock` — 남은 재고 조회
- Lua 스크립트: `src/main/resources/scripts/issue_coupon.lua`

## 실행
```
docker compose up -d
./gradlew bootRun
```
서버: http://localhost:8083, Redis: localhost:6383

## 검증 포인트
- `issue-naive`를 k6로 동시 1000 요청 → 재고 100개인데 100개 초과 발급되는지 확인 (실패 재현)
- `issue`를 동일 조건으로 재실행 → 정확히 100개만 발급되는지 확인 (해결 검증)

## 검증 로그 (실측)

로컬(Windows, Redis 단일 인스턴스, 같은 머신에서 Python `ThreadPoolExecutor` 기반
`../bench.py`로 부하 발생) 환경 실측치. 프로덕션 수치 아님 — 두 구현의 **상대적** 차이 확인용.

### 정확성 (재고 100개, 500명 동시·유니크 유저 요청)

| 버전 | 발급된 쿠폰 수 | 최종 재고 | 오버셀 |
|---|---|---|---|
| `issue-naive` (락 없음, check-then-act) | 114 | **-14** | 14건 초과 발급 |
| `issue` (Redis Lua atomic) | 100 | 0 | 0건 |

naive 버전은 재고 확인과 차감 사이 5ms 인위적 지연을 넣어 경쟁 구간을 넓혔음에도
"틀리게 동작하는 게 아니라 가끔 틀린다"가 아니라 **매 실행마다 재현**되는 수준으로 오버셀됨.

### 처리량 (재고 100,000개로 넉넉히 잡아 sold-out 없이 순수 처리 성능만 비교)

**1차 측정 (실험 결함 있음) — `issue-naive`에 박혀있는 인위적 `Thread.sleep(5)`를 그대로 둔 채 측정:**

| 시나리오 | 버전 | 요청수 | 동시성 | TPS | 평균 지연 | p99 |
|---|---|---|---|---|---|---|
| A | issue-naive (sleep 포함) | 1000 | 50 | 1831 | 23.5ms | 85.5ms |
| A | issue (atomic) | 1000 | 50 | 2344 | 17.5ms | 62.5ms |
| B | issue-naive (sleep 포함) | 5000 | 200 | 2693 | 53.9ms | 111.5ms |
| B | issue (atomic) | 5000 | 200 | 2904 | 43.5ms | 98.6ms |

이 1차 수치로 "atomic이 TPS 8~28% 빠르다"고 결론냈었는데 **잘못된 비교였음**. `issue-naive`의
sleep(5)은 race condition 재현을 쉽게 하려고 넣은 것이지 "naive 구현의 본질적 비용"이 아님.
closed-loop 벤치(고정 동시성)에서는 Little's Law로 `TPS ≈ 동시성 / 평균지연`이 거의 그대로
성립하기 때문에, 지연에 껴있는 고정 5ms가 그대로 TPS 격차로 번역돼버림 — Lua가 빨라서가 아니라
naive에 내가 인위적으로 5ms를 심어놔서 생긴 착시일 가능성이 큼.

**2차 측정 (정정) — sleep 제거하고 동일 조건 재측정 (커밋 안 된 임시 코드로 실험 후 원복):**

| 시나리오 | 버전 | 요청수 | 동시성 | TPS | 평균 지연 | p99 |
|---|---|---|---|---|---|---|
| A' | issue-naive (sleep 제거) | 1000 | 50 | 1910 | 22.4ms | 81.2ms |
| A' | issue (atomic) | 1000 | 50 | 1857 | 23.1ms | 92.9ms |
| B' | issue-naive (sleep 제거) | 5000 | 200 | 2631 | 48.4ms | 110.8ms |
| B' | issue (atomic) | 5000 | 200 | 2837 | 44.1ms | 98.0ms |

sleep을 빼고 다시 재니 동시성 50에서는 오히려 naive가 살짝 빠르거나 거의 동률(노이즈 범위),
동시성 200에서만 atomic이 약 8% 앞섬. 즉:
- **1차 결과의 "TPS 8~28% 차이"는 대부분(특히 낮은 동시성 구간)이 sleep 때문이었고, Redis
  왕복 횟수(naive 4번 vs Lua 1번) 자체의 순수 효과는 이 실험 규모(로컬 단일 Redis, HTTP
  오버헤드 포함)에서는 미미하거나 동시성이 높아질 때만 관측됨.**
- atomic(Lua)의 진짜 장점은 "빠름"이 아니라 **정확함**(위 정확성 표 참고) — 성능 차이를
  근거로 원자적 연산을 선택하면 안 되고, 정확성(오버셀 방지)이 선택 이유여야 함.
- 이 실험도 로컬 단일 머신·단일 Redis 인스턴스·클라이언트 스레드풀 오버헤드가 섞여 있어서
  "몇 % 차이"라는 절대 수치 자체를 신뢰하긴 어려움. 결론은 방향성(정확성 vs 무시할만한 성능차)
  정도로만 받아들이는 게 맞음. 진짜 원인 분리하려면 Redis 서버 자체 커맨드 레이턴시(MONITOR/
  SLOWLOG)나 순수 Redis client(HTTP 오버헤드 제외) 벤치가 필요함 — 여기선 안 함.

재현(atomic): `python ../bench.py --url http://localhost:8083/api/coupon/{id}/issue --concurrency 200 --total 5000 --body-user-prefix "u-" --label "..."`
재현(naive sleep-제거 버전): `CouponService.issueNaive`의 `Thread.sleep(5)` 블록을 임시로 주석 처리하고 동일하게 실행
