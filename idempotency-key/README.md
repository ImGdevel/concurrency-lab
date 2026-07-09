# idempotency-key — 중복 요청 방지

## 문제
쿠폰 발급/결제 API는 네트워크 재시도, 사용자의 새로고침/중복클릭으로 같은 요청이
두 번 들어올 수 있다. 같은 요청을 두 번 처리하면 쿠폰이 중복 발급되거나 결제가 두 번 나간다.

## 목표
- 클라이언트가 요청마다 고유한 `Idempotency-Key` 헤더를 보낸다.
- 서버는 해당 키로 처리 결과를 Redis에 캐싱해두고, 같은 키가 다시 오면 재실행 없이 캐시된 응답을 반환한다.
- "처리 중(in-flight)" 상태와 "완료" 상태를 구분해서 동시에 같은 키로 들어온 요청도 안전하게 처리한다.

## 구현 범위 (TODO)
- [ ] `Idempotency-Key` 헤더 인터셉터/필터
- [ ] Redis `SETNX` + TTL로 "처리 중" 마킹, 완료 시 응답 body 캐싱
- [ ] 동시에 같은 키로 N개 요청이 들어왔을 때 실제 비즈니스 로직은 1번만 실행되는지 검증
- [ ] 쿠폰 발급 API(`coupon-fcfs`와 동일 로직)에 적용해서 통합 테스트

## 실행
```
docker compose up -d
./gradlew bootRun
```
서버: http://localhost:8087, Redis: localhost:6387

## 참고
- Stripe API의 Idempotency Key 설계 문서가 레퍼런스로 좋음
