# queue-sequential — 순번 대기열

## 문제
동시 접속이 폭주할 때(콘서트 예매, 수강신청) 서버 자원을 보호하면서 "누가 먼저 왔는지" 순서를 보장해야 한다.

## 목표
- Redis Sorted Set(ZSET)에 진입 요청 시각을 score로 넣어 순번을 매긴다.
- 클라이언트는 발급받은 토큰으로 자신의 현재 순번/예상 대기시간을 폴링(polling) 조회한다.
- 순번이 임계값 이하로 내려오면 "입장 가능" 상태로 전환한다.

## 구현 범위 (완료)
- `POST /api/queue/token` — 대기열 진입, UUID 토큰 발급 + ZADD
- `GET /api/queue/rank/{token}` — ZRANK로 내 순번 조회
- `POST /api/queue/enter/{token}` — 순번이 컷라인 이내면 입장 허용, 아니면 403

## 실행
```
docker compose up -d
./gradlew bootRun
```
서버: http://localhost:8081, Redis: localhost:6381

## 검증 포인트
- 동시에 수천 개 토큰을 발급해도 ZRANK 순서가 요청 순서와 일치하는지 (k6 부하테스트로 검증)
- ZSET 크기가 커졌을 때 ZRANK 연산 비용(O(log N)) 확인

## 검증 로그 (실측)

로컬(Windows, Redis 단일 인스턴스, 같은 머신에서 Python `ThreadPoolExecutor` 기반
`../bench.py`로 부하 발생) 환경 실측치. 프로덕션 수치 아님 — 참고용 상대 수치.

| 엔드포인트 | 요청수 | 동시성 | TPS | 평균 지연 | p95 | p99 |
|---|---|---|---|---|---|---|
| `POST /api/queue/token` (INCR+ZADD) | 3000 | 100 | 2877 | 27.4ms | 43.8ms | 52.6ms |
| `GET /api/queue/rank/{token}` (ZRANK) | 3000 | 100 | 3468 | 26.2ms | 39.7ms | 51.9ms |

토큰 발급(쓰기, INCR+ZADD 2회 왕복)이 순번 조회(읽기, ZRANK 1회 왕복)보다 느린 게
당연한 그림인데 실측 TPS 차이는 약 17% — Redis 자체가 워낙 빨라서 왕복 횟수 차이보다
네트워크/HTTP 스택 오버헤드 비중이 더 크다는 뜻. ZSET 크기가 커져도(수천~수만 단위)
ZRANK는 O(log N)이라 이 실험 규모에서는 크기에 따른 유의미한 지연 증가는 관찰 안 됨.

기능 검증(1회성, curl):
- 토큰 발급 → `GET rank`로 순번 확인 → `POST enter`로 컷라인 이내 입장 성공 → 같은 토큰으로
  재입장 시도 시 404 (`대기열에 존재하지 않는 토큰이거나 이미 처리된 토큰입니다`) 확인

재현: `python ../bench.py --url http://localhost:8081/api/queue/token --concurrency 100 --total 3000 --label "..."`
