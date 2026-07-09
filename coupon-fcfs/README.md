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

| 시나리오 | 버전 | 요청수 | 동시성 | TPS | 평균 지연 | p95 | p99 |
|---|---|---|---|---|---|---|---|
| A | issue-naive | 1000 | 50 | 1831 | 23.5ms | 59.8ms | 85.5ms |
| A | issue (atomic) | 1000 | 50 | 2344 | 17.5ms | 45.0ms | 62.5ms |
| B | issue-naive | 5000 | 200 | 2693 | 53.9ms | 92.5ms | 111.5ms |
| B | issue (atomic) | 5000 | 200 | 2904 | 43.5ms | 80.0ms | 98.6ms |

atomic(Lua) 버전이 naive보다 **TPS 8~28% 높고 지연은 15~25% 낮음** — 직관과 반대로
"락/원자적 연산이 항상 더 느리다"가 아니라, Lua 스크립트 1회 호출이 naive의
GET → SISMEMBER → SLEEP(5ms) → DECR → SADD **5번 왕복**보다 오히려 Redis 라운드트립이
적어서 더 빠르다. naive의 인위적 sleep(5ms)이 지연 차이의 상당 부분을 차지하므로
절대 수치보다 "정확성 확보가 무조건 성능을 희생시키지 않는다"는 결론이 더 중요함.

재현: `python ../bench.py --url http://localhost:8083/api/coupon/{id}/issue --concurrency 200 --total 5000 --body-user-prefix "u-" --label "..."`
