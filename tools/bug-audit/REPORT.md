# ShreddedPaper 버그 감사 및 수정 — 2026-09-19

기준: `696da21`과 당시 작업 트리. 서버의 영역 소유권·스케줄러, 저장, 습격, Bukkit/Paper API 및 최적화 경로를 검토했다. 기존 추적기·영역 소유권 작업은 보존했다. 아래 12건은 수정 전 실제 클래스의 동작 또는 파일 내용으로 재현했으며, 정상 동작을 요구하는 회귀 테스트를 추가했다.

## 확인한 결함과 수정

| ID | 우선순위 | 발생 조건과 수정 전 결과 | 수정 |
| --- | --- | --- | --- |
| 01 | P1 | 비동기 플레이어 저장이 밀린 상태에서 최신 동기 저장을 실행하면, 오래된 스냅샷이 나중에 `.dat`를 덮어쓴다. 검사에서 저장 값이 `2 → 1`로 되돌아갔다. | 플레이어별 저장 순서를 연결하고 종료 시 저장과 재접속 읽기가 선행 저장을 기다리게 했다. 최신 `.dat=2`, 이전 `.dat_old=1`도 실제 NBT 파일로 검증한다. |
| 02 | P1 | B_LINEAR 청크를 삭제하고 flush한 뒤 정상 close 이전에 다시 열면, 삭제 표시와 남은 길이가 모순되어 리전 파일 전체가 `Invalid sector data`로 거부된다. 정상 close의 압축 정리는 이 문제를 가린다. | 삭제 시 위치·길이도 초기화한다. 이전 작성기가 만든 삭제 헤더는 읽을 때 정규화한다. |
| 03 | P1 | B_LINEAR 청크를 덮어쓰다가 I/O 오류가 나면, 아직 기록하지 못한 위치가 현재 청크의 위치로 게시된다. 기존 정상 청크도 `Truncated sector`로 읽지 못한다. | 새 데이터의 append가 모두 성공한 뒤에만 위치·길이·할당 끝을 바꾼다. 쓰기 시작 전 오류와 일부 바이트를 쓴 뒤 오류를 각각 주입했고, 이후 flush·close·재개방에서도 이전 데이터가 유지된다. |
| 04 | P2 | Linear 파일을 다시 연 뒤 한 버킷만 수정하면, 열지 않은 버킷의 청크 존재 비트가 0으로 저장된다. 실제 청크 바이트는 남아 있지만 파일 메타데이터가 잘못된다. | 미개방 버킷의 비트맵을 보존하고, 실제 연 버킷과 수정·삭제한 청크의 상태를 반영한다. |
| 05 | P1 | 여러 영역에서 습격을 동시에 생성하면 평범한 `++nextId`가 중복 ID를 낸다. 360,000회 경쟁 할당의 한 실행에서 41,712개가 중복됐다. 등록 후 공유 `nextId`를 다시 읽는 코드도 맵 키와 습격 ID를 어긋나게 한다. | 원자적 ID 발급을 사용하고, 발급받은 동일 ID를 객체에 설정한 뒤 맵에 게시한다. 기존 저장 형식도 유지한다. |
| 06 | P1 | Purpur 습격 쿨다운을 켜면, 영역의 쿨다운 추가와 전역 틱의 `HashMap` 복사가 경쟁해 `ConcurrentModificationException`이 발생한다. | 동시성 맵과 키별 원자적 갱신으로 변경했다. |
| 07 | P2 | 실행 중인 일회성 영역 작업에 `cancel()`을 호출하면 취소 불가인 `RUNNING`을 반환하면서 내부 상태는 취소로 바뀐다. 재호출은 반복 작업용 결과를 반환한다. | 실행 중인 일회성 작업의 상태를 유지하고, 경쟁하는 취소는 CAS 반복문으로 처리한다. 반복 작업의 다음 실행 취소도 검증한다. |
| 08 | P3 | `runAtFixedRate(..., period=-2)`가 허용되고 반복 없이 한 번만 실행된다. | 공개 반복 API는 모든 0 이하 주기를 거부한다. 일회성 작업 생성은 별도 내부 경로를 사용한다. |
| 09 | P2 | `max-fluid-ticks=3`, `max-block-ticks=17`이어도 유체 틱에 17이 전달된다. | 유체 틱에는 `maxFluidTicks`를 전달한다. |
| 10 | P2 | 독립 영역 틱에서 `/tick rate 5`를 설정해도 다음 틱 간격이 200ms가 아닌 50ms다. | 현재 설정된 틱 간격을 사용하고 영역 스레드에 변경이 보이도록 게시한다. 5·40·20 TPS 전환을 검증한다. |
| 11 | P1 | 월드의 청크 스케줄러가 종료되어도 기존 영역 핸들이 계속 틱을 실행하고 자신을 재등록한다. | 월드별 수명주기 잠금으로 실행 중인 틱이 끝나기를 기다린 후 핸들을 제거한다. 저장소를 닫기 전에 영역 틱을 중단하고 종료된 월드의 재등록과 공개 API의 새 작업 등록을 차단하며 전역 런타임 참조도 해제한다. 등록 도중의 언로드 경쟁도 검증한다. 월드 언로드는 전역 스케줄러에서 실행해야 한다. |
| 12 | P2 | B_LINEAR의 헤더 읽기나 close 중 force가 실패하면 파일 채널이 열린 채 남는다. 잘못된 파일을 20번 열자 해당 파일 디스크립터 20개가 남았고, close 실패 후에도 채널은 열려 있었다. | 생성 실패 시 채널을 닫고, close의 저장·압축 단계가 실패하더라도 finally에서 채널을 닫는다. |

P1은 데이터 보존·서버 진행에 직접 영향을 주는 문제, P2는 기능·설정·자원 관리 문제, P3은 잘못된 API 입력 검증 문제다. 경쟁 부하의 중복 수는 결함 재현값이며 실서버 발생 빈도를 뜻하지 않는다.

## 코드와 테스트

- 플레이어 저장: [PlayerDataSaveQueue](../../shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/util/PlayerDataSaveQueue.java), [Minecraft 연결 패치](../../shreddedpaper-server/minecraft-patches/features/0040-Order-player-data-saves.patch).
- 리전 저장: [BufferedRegionFile](../../shreddedpaper-server/src/main/java/org/bxteam/divinemc/region/type/BufferedRegionFile.java), [LinearRegionFile](../../shreddedpaper-server/src/main/java/org/bxteam/divinemc/region/type/LinearRegionFile.java).
- 습격: [동시성 패치](../../shreddedpaper-server/minecraft-patches/features/0039-Make-raid-state-safe-across-regions.patch).
- 영역 작업 API: [ShreddedPaperRegionSchedulerApiImpl](../../shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/ShreddedPaperRegionSchedulerApiImpl.java).
- 유체 틱: [ShreddedPaperChunkTicker](../../shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperChunkTicker.java).
- 독립 영역 틱: [RegionTickScheduler](../../shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionTickScheduler.java), [틱 간격 게시](../../shreddedpaper-server/minecraft-patches/features/0041-Publish-configured-tick-period-to-region-workers.patch), [저장소 종료 연결](../../shreddedpaper-server/minecraft-patches/features/0042-Stop-regions-before-closing-world-storage.patch), [Bukkit 언로드 연결](../../shreddedpaper-server/paper-patches/files/src/main/java/org/bukkit/craftbukkit/CraftServer.java.patch).
- [회귀 테스트 디렉터리](../../shreddedpaper-server/src/test/java/io/multipaper/shreddedpaper/audit), [수정 전 재현 출력 발췌](BEFORE.txt).

## 검증

- `applyAllPatches`: 성공. 적용된 Minecraft/Paper 저장소도 변경 없이 깨끗한 상태임을 확인했다.
- 전체 서버 테스트(`291b29e`): 9,063건 중 **9,041 통과, 22 건너뜀, 실패·오류 0**. 저장소 기본 설정에 따라 `Slow` 태그는 제외한다. 새 회귀 테스트 20건이 이 실행에 포함된다.
- 이후 공개 API의 종료된 월드 등록 차단(`a2efc49`)과 경계 조건 테스트를 보완했다. 최종 관련 테스트 **15건 모두 통과**: 월드 수명주기 3건, 영역 작업 API 5건, 리전 저장 7건. 추가 보완 후 전체 테스트를 다시 실행한 결과로 혼동하지 않도록 [집계 파일](TEST_RESULTS.json)에 실행별 결과를 구분했다.
- 이번 감사에서 추가해 통과를 확인한 회귀 사례는 합계 **23건**이다. 임시 파일 읽기·쓰기, 파일 채널 오류 주입, 실제 NBT 저장, 경쟁하는 습격 ID 발급, 실제 스케줄러 실행 경로를 사용한다.
- 최종 JAR 빌드 성공. Paperclip ZIP 무결성, 주요 수정 클래스 8개의 컴파일 결과와 일반 서버 JAR 간 바이트 일치, 번들에 들어간 서버와 실제 검사 서버가 로드한 JAR 간 바이트 일치를 확인했다. 패키징 전후 Minecraft 두 클래스의 바이트 차이는 상수 풀 재배열과 상위 클래스 필드 참조 정규화 때문임을 디스어셈블 결과로 확인했다.

수정한 서버 코드의 최종 리비전은 `a2efc49`이며, 검사한 Paperclip JAR의 SHA-256은 다음과 같다.

```text
b9318dcdb2613054e2ada3df8daa88321aed232595e6396e909acd20cd0b6d97
```

현재 작업 트리에서 빌드했으므로 기존 영역 소유권 작업의 미커밋 변경도 산출물에는 포함된다. 이 감사의 커밋에는 해당 변경을 넣지 않았다. 운영 배포용으로 분리된 깨끗한 릴리스라고 주장하지 않는다.

Minecraft 1.21.11, Java 25, 영역 작업 스레드 2개로 격리된 실제 서버 검사 결과는 [원본 출력](SMOKE_RESULTS.txt)에 기록했다.

| 검사 | 결과 |
| --- | --- |
| 5 TPS 설정, 약 2초간 영역 작업 계수 | 10회, 관측 4.993 TPS |
| 40 TPS 설정, 약 2초간 영역 작업 계수 | 80회, 관측 39.990 TPS |
| 네더 언로드 후 약 1초간 이전 작업 | 0회 |
| 언로드한 월드에 새 영역 작업 등록 | `RejectedExecutionException`으로 거부 |
| 같은 네더 재로딩 후 영역 작업 | 17회 실행 |
| 전체 검사 및 종료 | `AUDIT_SMOKE_PASS`, 프로세스 종료 코드 0 |

최종 검사 서버의 로그에는 `ERROR` 또는 `Exception`이 없었다. 짧은 격리 서버 동작 검사이며 다중 접속 부하 성능 측정은 아니다.

```bash
./gradlew applyAllPatches --no-configuration-cache
./gradlew :shreddedpaper-server:test --no-configuration-cache
./gradlew :shreddedpaper-server:createMojmapPaperclipJar --no-configuration-cache
```

대상 회귀 테스트만 실행하려면 다음을 사용한다.

```bash
./gradlew :shreddedpaper-server:test \
  --tests 'io.multipaper.shreddedpaper.audit.*Suite' --no-configuration-cache
```

[격리 서버 검사](smoke/run.py)는 입력 JAR을 새 임시 디렉터리에 복사한 뒤 loopback 주소, 임의 포트에서 서버를 실행한다. [검사 플러그인](smoke/WorldClockSmoke.java)은 실제 영역 작업을 세어 5·40 TPS를 확인하고, 네더를 언로드한 뒤 작업 호출이 멈추고 새 작업 등록이 거부되는지, 같은 월드를 다시 로드하면 틱이 재개되는지 검사하고 서버를 종료한다. 운영 서버용 플러그인이 아니다.

```bash
python3 tools/bug-audit/smoke/run.py \
  --server-jar shreddedpaper-server/build/libs/astatine-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar \
  --libraries /path/to/cached/1.21.11/libraries
```

## 범위와 한계

재현한 결함을 수정한 결과이며, 모든 실행 조합에서 다른 버그가 없다는 증명은 아니다. 자동 소유권 스캐너 결과만으로 안전성을 판정하지 않았다. 실제 호출이 없는 유틸리티, 재현되지 않은 아이템 중력 후보, 이미 수정된 자연 스폰·disconnect 경로는 새 결함 수에 넣지 않았다.

검증은 테스트 프로세스·임시 파일·별도 테스트 서버를 대상으로 한다. 운영 서버의 데이터와 실행 프로세스에는 적용하지 않았다. 기존 Linear 파일에서 이미 잘못 저장된 비트맵을 일괄 복구하는 작업은 포함하지 않으며, B_LINEAR의 이전 삭제 헤더는 읽을 때 복구한다. 이미 덮어쓴 과거 플레이어 데이터나 청크 데이터가 이 수정으로 되살아나는 것은 아니다.

## 수정 커밋

- `0710412` 플레이어 저장 순서와 읽기 대기
- `0c27c22` 리전 파일의 메타데이터·I/O 실패 처리
- `ac4335d` 습격 ID와 쿨다운 동시성
- `e2c591f` 영역 작업 API 계약
- `4176857` 유체 틱 제한
- `291b29e` 독립 영역 틱의 월드 수명주기와 틱 속도
- `a2efc49` 언로드한 월드의 공개 API 작업 등록 차단
- `92611a4` 일부 바이트 기록 후 I/O 실패 회귀 테스트
- `4958141` 작업 등록과 월드 종료의 경쟁 회귀 테스트
