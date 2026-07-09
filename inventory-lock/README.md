# inventory-lock — DB 비관락 vs 낙관락 vs Redis

## 문제
쿠폰/재고 차감을 DB에서 처리할 때 3가지 방식의 정확성과 성능이 다르다:
1. Lock 없이 (race condition 재현용)
2. DB 비관적 락 (`SELECT ... FOR UPDATE`)
3. DB 낙관적 락 (버전 컬럼 + 재시도)
4. Redis 원자적 연산 (다른 모듈에서 이미 검증한 방식과 DB 방식 비교)

## 목표
- 같은 재고차감 시나리오를 4가지 방식으로 각각 구현
- 동시성/정확성/처리량(TPS)을 표로 정리해서 비교

## 구현 범위 (TODO)
- [ ] `Product` 엔티티 (stock, version 컬럼)
- [ ] `POST /api/inventory/{id}/decrease-none` — 락 없음 (재현용)
- [ ] `POST /api/inventory/{id}/decrease-pessimistic` — `@Lock(PESSIMISTIC_WRITE)`
- [ ] `POST /api/inventory/{id}/decrease-optimistic` — `@Version` + 재시도 로직
- [ ] `POST /api/inventory/{id}/decrease-redis` — Redis Lua atomic decrement 후 DB 반영
- [ ] k6로 4개 엔드포인트 동일 조건 부하테스트, 결과 비교표 README에 기록

## 실행
```
docker compose up -d
./gradlew bootRun
```
서버: http://localhost:8086, Redis: localhost:6386, MySQL: localhost:3316 (db: inventory_lock / user: lab / pw: lab1234)

## 검증 포인트
- 비관락: 정확하지만 처리량 낮음 (락 대기)
- 낙관락: 충돌 적으면 빠르지만 충돌 많으면 재시도 폭증
- Redis: 가장 빠르지만 DB 정합성은 별도 동기화 필요 (outbox-pattern과 연결됨)
