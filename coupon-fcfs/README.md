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
