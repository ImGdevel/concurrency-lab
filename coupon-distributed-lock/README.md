# coupon-distributed-lock — 분산락으로 같은 문제 재해결

## 문제
`coupon-fcfs`는 Redis Lua 스크립트(단일 명령 원자성)로 해결했다. 이 모듈은 같은 문제를
**Redisson 분산락(Redlock 기반 RLock)** 으로 다시 풀어보고 두 방식을 비교한다.

## 목표
- `RLock.tryLock()`으로 임계구역(재고 확인 + 차감 + DB 기록)을 보호
- Lua 스크립트 방식 vs 분산락 방식의 처리량(TPS)/지연시간 비교
- 락 획득 실패 시 재시도 전략(backoff) 비교

## 구현 범위 (TODO)
- [ ] `POST /api/coupon/{couponId}/issue` — Redisson `RLock` 기반 발급
- [ ] 락 타임아웃/재시도 설정 실험 (`tryLock(wait, lease, unit)`)
- [ ] k6로 `coupon-fcfs`와 동일 시나리오 부하테스트 → TPS 비교표 작성
- [ ] 락 보유 중 인스턴스가 죽는 경우(lease time 만료) 시나리오 재현

## 실행
```
docker compose up -d
./gradlew bootRun
```
서버: http://localhost:8084, Redis: localhost:6384

## 참고
- Lua 원자적 연산: "단일 명령"으로 끝나는 단순 카운터류에 적합, 락 오버헤드 없음
- 분산락: "재고차감 + DB insert + 외부 API 호출"처럼 여러 단계를 묶어야 할 때 필요
