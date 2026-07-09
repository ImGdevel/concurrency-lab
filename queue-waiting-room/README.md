# queue-waiting-room — 대기열 → 실서비스 진입 제어

## 문제
`queue-sequential`이 "순번"만 매긴다면, 이 모듈은 "몇 명까지 실서비스에 동시 접속시킬지"를 제어한다.
네이버 예약, 티켓링크류 대기열의 핵심은 순번보다 **동시 입장 인원 제한**이다.

## 목표
- Redis에 "현재 입장 중인 인원" 카운터를 두고 TTL로 세션을 관리한다.
- 대기 중인 사용자는 SSE(Server-Sent Events)로 실시간 순번/입장 알림을 받는다.
- 입장 인원이 퇴장(TTL 만료 or 명시적 퇴장)하면 다음 대기자를 자동으로 승격시킨다.

## 구현 범위 (TODO)
- [ ] `GET /api/waiting-room/subscribe` — SSE 구독, 대기 상태 실시간 push
- [ ] 입장 슬롯 카운터 (Redis INCR/DECR + TTL 세션 키)
- [ ] 슬롯 여유 발생 시 대기열 앞순번 자동 승격 스케줄러
- [ ] 부하테스트: 슬롯 100개 고정 상태에서 10,000명 동시 요청 시 정확히 100명만 입장하는지 검증

## 실행
```
docker compose up -d
./gradlew bootRun
```
서버: http://localhost:8082, Redis: localhost:6382

## 참고 접근법
- 네이버/인터파크 대기열 시스템 블로그 글에서 흔히 쓰는 "입장 티켓 + TTL 세션" 패턴
