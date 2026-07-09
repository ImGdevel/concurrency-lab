# rate-limiter — 토큰버킷 vs 슬라이딩윈도우

## 문제
쿠폰 발급/대기열 API를 특정 IP나 유저가 초당 N회 이상 두드리는 걸 막아야 한다.
알고리즘에 따라 순간 버스트 허용 여부와 정확도가 달라진다.

## 목표
- 토큰버킷(Token Bucket): 일정 버스트 허용, 평균 처리율 제한
- 슬라이딩 윈도우 로그/카운터(Sliding Window): 더 정확한 순간 rate 계산
- 같은 트래픽 패턴에 두 알고리즘을 각각 적용해서 통과/차단 결과 비교

## 구현 범위 (TODO)
- [ ] `POST /api/limited/token-bucket` — Redis 기반 토큰버킷 (Lua 스크립트로 원자적 리필+소비)
- [ ] `POST /api/limited/sliding-window` — Redis ZSET 기반 슬라이딩 윈도우 로그
- [ ] 동일 부하 패턴(예: 1초에 20개, 순간 50개 버스트)으로 두 방식 통과율 비교
- [ ] 429 Too Many Requests 응답 + Retry-After 헤더

## 실행
```
docker compose up -d
./gradlew bootRun
```
서버: http://localhost:8085, Redis: localhost:6385

## 참고
- Redis 공식 블로그의 "Rate limiting with Redis" Lua 예제가 출발점으로 좋음
