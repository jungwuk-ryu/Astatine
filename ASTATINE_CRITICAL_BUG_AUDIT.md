# Astatine 심각 버그 감사

목표: `jungwuk-ryu`가 추가한 경로에서 크래시, 교착, 데이터 손실·복제, 무한 지연을 재현 가능한 근거로 찾는다.

## 조사 영역

- [x] 지역 소유권: merge/split, epoch, stale owner, lock 해제
- [x] 작업 전달: mailbox 포화, 거절, 재시도, 중요 작업 기아
- [x] 청크·저장: sync load, unload/save, IO QoS, Linear 무결성
- [x] 게임 상태: block/entity/player/portal의 잘못된 스레드 접근
- [x] 외부 경계: plugin lifecycle, async callback, network/disconnect 순서
- [x] 최적화: async tracker/pathfinding/worldgen cache의 경쟁·누수

## 확인된 치명 결함

1. **몹 스폰 상태 경쟁 → 서버 크래시**
   - 모든 독립 영역이 같은 `SpawnState`를 공유한다.
   - `PotentialCalculator.charges`는 동기화 없는 `ArrayList`다.
   - 16스레드 × 20,000회 재현: 320,000개 중 63,747개만 남고 `null` 8,761개 생성, 읽기에서 `NullPointerException`.
2. **인접 영역 강퇴 → 교착**
   - 호출 영역 락을 쥔 채 대상 영역에 `Waitable`을 넣고 완료를 기다린다.
   - 결정적 락 테스트에서 대상은 호출 락 해제 전까지 실행 불가했다.

기존 테스트는 각 자료구조·플러그인 락만 단독 검사해 두 조합을 놓친다. 실제 서버 플러그인 재현은 미실시했다.

## 판정 체크

- [x] 현재 런타임 가드 인벤토리 통과: 31/31
- [x] 소스 → 스케줄 순서 → 실패 결과를 연결
- [x] 기존 테스트가 놓치는 이유 확인
- [x] 최소 테스트 또는 결정적 실행으로 검증
- [x] 영향 범위와 남은 불확실성 기록
