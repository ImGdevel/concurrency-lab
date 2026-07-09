# concurrency-lab

대기열 시스템, 쿠폰 발급 시스템을 중심으로 한 **동시성 제어 실험실**.
하나의 서비스를 만드는 게 아니라, 실무에서 자주 부딪히는 동시성 문제 하나하나를
독립된 Spring Boot 모듈로 도려내서 "문제 재현 → 원인 분석 → 해결 → 검증" 사이클로 공부한다.

각 모듈은 완전히 독립적인 Gradle 프로젝트다 (자체 `build.gradle`, `docker-compose.yml`, 포트).
서로 의존하지 않으며, 필요한 모듈만 골라서 실행하면 된다.

## 스택
- Java 21, Spring Boot 3.5.16 (Gradle)
- Redis 7 (동시성 제어 primitive 대부분 여기서)
- MySQL 8 (DB 락/Outbox 비교가 필요한 모듈만)
- Redisson (분산락 필요 모듈)

## 모듈 목록

| 모듈 | 포트 (app/redis) | 주제 |
|---|---|---|
| [queue-sequential](./queue-sequential) | 8081 / 6381 | Redis ZSET 기반 순번 대기열 |
| [queue-waiting-room](./queue-waiting-room) | 8082 / 6382 | 동시 입장 인원 제한 + SSE 알림 |
| [coupon-fcfs](./coupon-fcfs) | 8083 / 6383 | 선착순 쿠폰 발급, race condition 재현/해결 (Lua atomic) |
| [coupon-distributed-lock](./coupon-distributed-lock) | 8084 / 6384 | 같은 문제를 Redisson 분산락으로 재해결, Lua 방식과 비교 |
| [rate-limiter](./rate-limiter) | 8085 / 6385 | 토큰버킷 vs 슬라이딩윈도우 |
| [inventory-lock](./inventory-lock) | 8086 / 6386 / mysql 3316 | DB 비관락 vs 낙관락 vs Redis 비교 |
| [idempotency-key](./idempotency-key) | 8087 / 6387 | 중복 요청 방지 |
| [outbox-pattern](./outbox-pattern) | 8088 / 6388 / mysql 3318 | 쿠폰 발급 이벤트의 신뢰성 있는 발행 |

각 모듈 폴더의 `README.md`에 문제 정의, 목표, 구현 범위(체크리스트), 실행법, 검증 포인트가 있다.
현재는 프로젝트 구조/설정만 세팅되어 있고 실제 로직은 각 모듈별로 브랜치를 나눠 하나씩 채워나간다.

## 실행 방법 (모듈 공통)
```
cd <module-name>
docker compose up -d      # Redis(+MySQL) 기동
./gradlew bootRun         # 앱 실행
```

## 진행 순서
1. `queue-sequential`
2. `coupon-fcfs`
3. `coupon-distributed-lock` — `coupon-fcfs`와 바로 비교되니 이어서
4. `idempotency-key` — `coupon-fcfs`에 그대로 적용해볼 수 있음
5. `inventory-lock` — DB 레벨 락 비교, 처음 등장하는 JPA/MySQL 모듈
6. `outbox-pattern` — `inventory-lock`과 이어지는 주제
7. `rate-limiter`, `queue-waiting-room` — 독립적으로 아무 때나

## 브랜치 전략
모듈별로 `feature/<module-name>` 브랜치에서 구현한다. `main`은 스캐폴딩(뼈대) 상태를 유지한다.
