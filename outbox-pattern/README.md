# outbox-pattern — 쿠폰 발급 후 이벤트 신뢰성 발행

## 문제
쿠폰을 발급(DB commit)한 뒤 "발급 완료" 이벤트를 카프카/메시지큐로 보내야 하는데,
DB 트랜잭션 커밋과 메시지 발행은 원자적으로 묶이지 않는다 (Dual Write 문제).
DB는 커밋됐는데 메시지 발행이 실패하면 다른 시스템(알림, 포인트 적립 등)이 이 사실을 영영 모른다.

## 목표
- 쿠폰 발급과 "발행할 이벤트"를 같은 DB 트랜잭션 안에서 `outbox` 테이블에 함께 저장한다.
- 별도 스케줄러/폴러가 `outbox` 테이블을 주기적으로 읽어 미발행 이벤트를 발행하고 상태를 갱신한다.
- 이렇게 하면 "DB 커밋 = 이벤트 발행 보장"이 성립한다 (최종적 일관성, at-least-once).

## 구현 범위 (TODO)
- [ ] `CouponIssue` 엔티티 + `OutboxEvent` 엔티티 (같은 트랜잭션에 저장)
- [ ] `POST /api/coupon/{id}/issue` — 쿠폰 발급 + outbox 이벤트 저장을 한 트랜잭션으로
- [ ] `@Scheduled` 폴러 — `PENDING` 상태 outbox 이벤트를 읽어 발행(로그 출력으로 시뮬레이션) 후 `PUBLISHED`로 상태 변경
- [ ] 발행 중 실패 시 재시도 + 중복 발행 방지(idempotency-key 모듈과 연결 가능)
- [ ] 강제로 발행 단계에서 예외를 던져도 DB에는 이벤트가 남아있는지 검증 (신뢰성 증명)

## 실행
```
docker compose up -d
./gradlew bootRun
```
서버: http://localhost:8088, Redis: localhost:6388, MySQL: localhost:3318 (db: outbox_pattern / user: lab / pw: lab1234)

## 참고
- Debezium CDC 기반 outbox 패턴도 있지만, 여기선 폴링 방식(Polling Publisher)부터 구현
