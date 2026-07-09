# queue-sequential — 순번 대기열

## 문제
동시 접속이 폭주할 때(콘서트 예매, 수강신청) 서버 자원을 보호하면서 "누가 먼저 왔는지" 순서를 보장해야 한다.

## 목표
- Redis Sorted Set(ZSET)에 진입 요청 시각을 score로 넣어 순번을 매긴다.
- 클라이언트는 발급받은 토큰으로 자신의 현재 순번/예상 대기시간을 폴링(polling) 조회한다.
- 순번이 임계값 이하로 내려오면 "입장 가능" 상태로 전환한다.

## 구현 범위 (TODO)
- [ ] `POST /api/queue/token` — 대기열 진입, UUID 토큰 발급 + ZADD
- [ ] `GET /api/queue/rank/{token}` — ZRANK로 내 순번 조회
- [ ] `POST /api/queue/enter/{token}` — 순번이 컷라인 이내면 입장 허용, 아니면 403

## 실행
```
docker compose up -d
./gradlew bootRun
```
서버: http://localhost:8081, Redis: localhost:6381

## 검증 포인트
- 동시에 수천 개 토큰을 발급해도 ZRANK 순서가 요청 순서와 일치하는지 (k6 부하테스트로 검증)
- ZSET 크기가 커졌을 때 ZRANK 연산 비용(O(log N)) 확인
