# Astatine 심각 버그 감사

목표: `jungwuk-ryu`가 추가한 경로에서 크래시, 교착, 데이터 손실·복제, 무한 지연을 재현 가능한 근거로 찾는다.

## 조사 영역

- [ ] 지역 소유권: merge/split, epoch, stale owner, lock 해제
- [ ] 작업 전달: mailbox 포화, 거절, 재시도, 중요 작업 기아
- [ ] 청크·저장: sync load, unload/save, IO QoS, Linear 무결성
- [ ] 게임 상태: block/entity/player/portal의 잘못된 스레드 접근
- [ ] 외부 경계: plugin lifecycle, async callback, network/disconnect 순서
- [ ] 최적화: async tracker/pathfinding/worldgen cache의 경쟁·누수

## 판정 체크

- [x] 현재 런타임 가드 인벤토리 통과: 31/31
- [ ] 소스 → 스케줄 순서 → 실패 결과를 연결
- [ ] 기존 테스트가 놓치는 이유 확인
- [ ] 최소 테스트 또는 결정적 실행으로 검증
- [ ] 영향 범위와 남은 불확실성 기록

