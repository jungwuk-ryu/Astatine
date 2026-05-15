# ShreddedPaper 보안 감사 및 조치 보고서 - 2026-05-16

이 문서는 PaperMC 기반 ShreddedPaper 커스텀 구동기 소스에 대해 수행한 구조 분석, 위협 시나리오, 취약점 조치 내역, 검증 결과를 기록한다. 사용자의 지시에 따라 **SP-SEC-009 "위험한 설정값" 항목은 코드 수정 대상에서 제외**했다.

## 감사 범위

- 저장소 루트: `/home/ubuntu/works/ShreddedPaper`
- 주요 구조: patch/build pipeline, Minecraft/Paper patch set, region ownership, region mailbox, chunk IO tracker, network send path, DivineMC region file formats, C2ME bytecode cache cleanup, command permissions, plugin compatibility locking, async ownership scanner.
- 검증 기준: 패치 재적용 가능성, 컴파일 가능성, async ownership 후보 0건, 관련 단위 테스트, 전체 서버 테스트, Gradle `check`.

## 구조별 시나리오

| 구조 | 주요 공격/오류 시나리오 | 조치 상태 |
| --- | --- | --- |
| Patch/build pipeline | 보안 패치가 실제 생성 소스에 적용되지 않아 릴리스가 취약한 코드로 빌드됨 | 해결 |
| Network send path | 연결 종료 후에도 패킷 send 작업이 큐에 쌓여 원격 DoS로 이어짐 | 해결 |
| Region ownership | 다른 region 소유 chunk/entity/block entity에 비동기 또는 잘못된 thread에서 접근 | 해결 |
| Region mailbox / chunk IO | critical/transferred/retry 작업이 reserve를 넘어 무제한 증가 | 해결 |
| LINEAR/B_LINEAR region file | 악성 world 파일이 거대한 크기를 선언해 OOM 유발 | 해결 |
| C2ME cache cleanup | `cache/c2me-dfc` symlink를 통해 서버 접근 가능 경로 삭제 | 해결 |
| Diagnostic commands | 일반 플레이어가 chunk lifecycle/tick 상태를 정찰 | 해결 |
| Plugin compatibility | `folia-supported: true` 메타데이터만으로 sync lock 우회 | 해결 |
| 위험한 설정값 | anti-cheat/privacy/startup 안정성을 약화시키는 설정 | 제외, 미수정 |

## 조치 결과

### SP-SEC-001 - Patch pipeline failure

- 상태: 해결
- 조치:
  - Minecraft source patch와 Paper file patch의 malformed/outdated hunk를 재생성했다.
  - `0035-Region-aware-lag-compensation.patch`의 중복 hunk를 제거했다.
  - `paper-server` 생성 트리에 중복 테스트를 만들던 `RegionMailboxTest*.patch` 파일을 제거했다.
- 검증:
  - `./gradlew applyAllPatches` 성공
  - 적용 결과: Minecraft source patches 274개, Paper server file patches 35개, Purpur server file patches 4개 적용

### SP-SEC-002 - Disconnected connection send bypass

- 상태: 해결
- 조치:
  - `Connection.send` 패치의 `true || this.isConnected()` 우회를 제거하고 실제 connected 상태를 다시 사용하도록 복구했다.
- 보안 효과:
  - 연결 종료 후 lazy packet enqueue가 계속 누적되는 원격 DoS 경로를 차단했다.

### SP-SEC-003 - Async ownership scanner candidates

- 상태: 해결
- 조치:
  - patch pipeline을 정상화해 scanner가 stale/generated source를 보지 않도록 했다.
  - `PortalForcer` 등 sync-load 위험 경로를 loaded-only 접근으로 정리했다.
  - `ShreddedPaperAccess.writeLoadedBlockEntity`에 최종 소유권 재검사를 추가해 TOCTOU 성격의 block entity mutation 위험을 닫았다.
  - scanner root guard를 보강했다.
- 검증:
  - `node tools/async-audit/scan-async-ownership.mjs --fail-on-critical`
  - 결과: `critical=0 high=0 medium=0`

### SP-SEC-004 - Mailbox / chunk IO retry unbounded growth

- 상태: 해결
- 조치:
  - `RegionMailbox`의 `CRITICAL_SYSTEM` 큐를 bounded queue로 변경했다.
  - critical/transferred task가 capacity를 넘으면 reject 및 metric 기록으로 fail-closed 처리한다.
  - `RegionChunkIoTracker.deferExecutorBackpressureRetry`가 reserve를 넘는 retry를 더 이상 누적하지 않도록 hard cap을 적용했다.
  - 관련 설정 설명도 warning reserve가 아니라 hard cap 의미로 정리했다.
- 검증:
  - `RegionMailboxTestSuite` 통과
  - 전체 `:shreddedpaper-server:test` 통과

### SP-SEC-005 - LINEAR/B_LINEAR region file OOM

- 상태: 해결
- 조치:
  - `LinearRegionFile`에 region file, bucket compressed/decompressed, v1 decompressed, chunk size, feature name 길이 상한을 추가했다.
  - Zstd `readAllBytes()` 경로를 bounded read로 바꾸고 파일 metadata/remaining length를 할당 전에 검증한다.
  - `BufferedRegionFile`에 uncompressed chunk, stored section, sector offset/length, decompression output 크기 검증을 추가했다.
- 보안 효과:
  - 악성 또는 손상된 `.linear`/`.b_linear` 파일이 heap/direct memory를 무제한 할당하게 만드는 경로를 차단했다.

### SP-SEC-006 - C2ME symlink-unsafe recursive delete

- 상태: 해결
- 조치:
  - `com.ishland.c2me.opts.dfc.util.Files.deleteRecursively`를 `walkFileTree` 기반으로 재작성했다.
  - symlink root는 무시하고, child symlink는 target을 따라가지 않고 link 자체만 삭제한다.
  - 삭제 대상이 root 밖으로 벗어나지 않는지 검증한다.
- 검증:
  - `FilesTestSuite` 추가 및 통과
  - 전체 `:shreddedpaper-server:test` 통과

### SP-SEC-007 - Public mpmap diagnostic permission

- 상태: 해결
- 조치:
  - `shreddedpaper.command.mpmap` 기본 권한을 `PermissionDefault.TRUE`에서 `PermissionDefault.OP`로 변경했다.
- 보안 효과:
  - 일반 플레이어가 주변 chunk lifecycle/tick 상태를 정찰하는 기본 노출을 막았다.

### SP-SEC-008 - Plugin metadata compatibility lock bypass

- 상태: 해결
- 조치:
  - `SynchronousPluginExecution.execute`에서 `folia-supported` 메타데이터만으로 sync compatibility locking을 건너뛰던 우회를 제거했다.
  - dependency traversal은 legacy `PluginDescriptionFile` 대신 `PluginMeta` dependency API를 사용하도록 바꿨다.
  - 테스트/초기화 환경처럼 `MinecraftServer.getServer()`가 아직 없는 경우에는 compatibility lock 경로를 실행하지 않도록 NPE-safe guard를 추가했다.
- 검증:
  - `org.bukkit.support.suite.NormalTestSuite` 통과
  - 전체 `:shreddedpaper-server:test` 통과

### SP-SEC-009 - 위험한 설정값

- 상태: 사용자 요청에 따라 미수정
- 남은 내용:
  - `alwaysAllowWeirdMovement`, `disableVanishApi`, 과도한 `regionSize`, 보안 의미가 애매한 scheduler 관련 설정 등은 코드 변경하지 않았다.
  - 운영 문서/기본 설정 경고 강화는 별도 작업으로 남는다.

## 검증 결과

| 명령 | 결과 |
| --- | --- |
| `./gradlew applyAllPatches` | 성공 |
| `./gradlew :shreddedpaper-server:compileJava` | 성공, 기존 deprecation/unchecked 경고 14개 |
| `node tools/async-audit/scan-async-ownership.mjs --fail-on-critical` | 성공, `critical=0 high=0 medium=0` |
| `./gradlew :shreddedpaper-server:test --tests 'io.multipaper.shreddedpaper.threading.region.RegionMailboxTestSuite' --tests 'com.ishland.c2me.opts.dfc.util.FilesTestSuite'` | 성공 |
| `./gradlew :shreddedpaper-server:test --tests 'org.bukkit.support.suite.NormalTestSuite'` | 성공 |
| `./gradlew :shreddedpaper-server:test` | 성공 |
| `./gradlew :shreddedpaper-server:check` | 성공 |

## 잔여 위험

- 사용자가 제외한 "위험한 설정값" 계열은 의도적으로 남겨두었다.
- 테스트 중 출력된 JDK restricted/native-access 및 `sun.misc.Unsafe` 경고는 JLine/JCTools/JOML/ByteBuddy 등 의존성 경고이며 이번 취약점 수정의 실패 신호는 아니다.
- 실제 운영 서버에서의 장시간 부하/chaos 검증은 별도 런타임 검증 범위다. 이번 조치는 소스 정적 검증, patch 재적용, 컴파일, 단위/전체 테스트, Gradle check 기준으로 완료했다.
