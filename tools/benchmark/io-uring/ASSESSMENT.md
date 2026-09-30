# io_uring 적용 검토

검토일: 2026-09-30 KST. 소스 기준: `c55b13d`, `ver/1.21.11`.

기존 TCP transport 옵션으로 격리 A/B 실험을 진행할 가치는 있다. 운영 기본값은 비활성화로 유지하고, Netty의 관련 수정 반영과 Minecraft 부하 검증을 거쳐 활성화를 결정하는 것을 권장한다. 청크 파일 I/O 도입은 별도 병목 측정 이후에 판단한다.

이번 검토는 운영 프로세스·설정의 읽기 전용 확인과 별도 JVM의 TCP 기능 검사다. 운영 설정 변경, 서버 재시작, 의존성 업그레이드, 성능 A/B 실험은 수행하지 않았다.

## 현재 구현과 운영 상태

| 항목 | 확인 결과 |
| --- | --- |
| TCP 구현 | `d1dfd59`에서 opt-in io_uring transport가 이미 추가됨 |
| 설정 | `optimizations.prefer-io-uring-transport`, 기본값 `false` |
| native 전제 | `server.properties`의 `use-native-transport=true` 필요 |
| 선택 순서 | native 비활성 → NIO. native 활성 → KQueue, 선호 설정과 가용성을 만족하는 TCP io_uring, Epoll, NIO 순 |
| Unix domain socket | 현재 선택기는 io_uring 후보에서 제외하고 Epoll domain channel 사용 |
| 의존성 | Netty BOM 및 io_uring native artifact `4.2.12.Final`; x86_64/aarch_64/riscv64 포함 |
| 운영 호스트 | Linux `6.17.0-1016-oracle`, aarch64, `kernel.io_uring_disabled=0` |
| 운영 JVM | PID `3148370`, GraalVM Java `25.0.3`, UID 1001 |
| 운영 transport | 선호 설정 `false`, Netty Epoll 스레드 4개, epoll native library 로드, io_uring ring descriptor 0개 |
| 운영 저장 형식 | `LINEAR`, compression level 9, I/O thread count 3, virtual threads 활성 |

운영 JVM이 열어 둔 Netty JAR descriptor의 버전은 모두 `4.2.12.Final`이었다. native library 파일이 디스크에 존재한다는 사실과 실제 transport 선택은 구분했다.

구현 근거:

- [선택 정책](../../../shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/network/NetworkTransportSelection.java)
- [기본 설정](../../../shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/config/ShreddedPaperConfiguration.java)
- [적용되는 EventLoopGroupHolder 패치](../../../shreddedpaper-server/minecraft-patches/sources/net/minecraft/server/network/EventLoopGroupHolder.java.patch)
- [Netty 의존성](../../../shreddedpaper-server/build.gradle.kts)
- [기존 선택 정책 테스트](../../../shreddedpaper-server/src/test/java/io/multipaper/shreddedpaper/network/NetworkTransportSelectionTest.java)

Netty 4.2의 io_uring은 정식 transport 모듈이다. 현재 구현은 `MultiThreadIoEventLoopGroup`과 `IoUringIoHandler`를 사용하는 공식 API 구조와 일치한다. 오래된 incubator 모듈로 대체할 이유는 없다. [Netty 4.2 Migration Guide](https://github.com/netty/netty/wiki/Netty-4.2-Migration-Guide)

## 기대할 수 있는 효과와 범위

io_uring은 제출·완료 큐를 통해 I/O 작업을 묶어 처리할 수 있어 syscall과 I/O 처리 비용을 줄일 여지가 있다. 실제 이득은 요청 크기, 동시 연결 수, batching, 커널과 장치, 애플리케이션 비용에 따라 달라진다. [설계 설명 — Jens Axboe](https://www.kernel.dk/io_uring.pdf)

| 경로 | 예상 효과 | 판단 |
| --- | --- | --- |
| 많은 TCP 연결의 송수신 | transport CPU와 tail latency가 줄 가능성 | 기존 옵션으로 실험할 우선 대상 |
| 패킷 인코딩·압축·암호화·플러그인 변환 | 해당 계산을 직접 제거하지 않음 | Netty 스레드 사용률만으로 이득을 추정할 수 없음 |
| 엔티티 AI·충돌·추적·지역 소유권 | 해당 게임 로직을 직접 병렬화하지 않음 | 밀집 지역 MSPT 개선을 보장할 근거 없음 |
| 청크 파일 읽기·쓰기 | 별도 저장 backend를 만들면 실험 가능 | TCP 옵션만으로는 변경되지 않음 |

현재 서버는 이미 연결별 MPSC 송신 batching과 flush 범위를 사용한다. io_uring은 그 아래 transport를 교체하므로 기존 batching의 효과를 추가 이득으로 다시 계산하면 안 된다.

2026-09-22의 [저장된 프로파일](../crowded-audit/RESULTS.json)에서 `crowded_240_control.phases.mixed`의 Netty 실행 샘플은 7,691개였다. 그중 `runAllTasks`를 포함한 샘플은 7,677개이고, 패킷 전송·encoder 호출도 크게 나타났다. inclusive stack 집계는 서로 겹치므로 합산할 수 없으며, CPU 시간 비율이나 syscall 비용 비율도 아니다. 이 기록은 최종 MPSC 개선 전 자료로, 현재 운영 병목을 확정하지 않는다. 다만 높은 Netty 부하를 곧바로 epoll 병목으로 해석할 근거가 부족하다는 점은 보여 준다.

로컬 Netty `4.2.12.Final` 소스에서 zero-copy write threshold는 기본 `-1`이다. 서버 패치는 이 옵션을 설정하지 않는다. 호스트의 `SEND_ZC_SUPPORTED=true`와 실제 zero-copy 송신 활성화는 별개의 사실이다.

## 이번에 수행한 native 기능 검사

운영과 같은 호스트·UID·JDK에서 운영 라이브러리의 Netty `4.2.12.Final`만 classpath에 넣은 별도 JVM을 실행했다. IPv4 loopback의 임시 포트에만 bind했으며, 운영 서버나 월드에 연결하지 않았다.

- `IoUring.isAvailable()=true`, `Epoll.isAvailable()=true`.
- 두 transport 모두 연결 4개, 연결당 256회, 회당 1,024바이트 echo 성공: 각각 1,024회 왕복과 1MiB payload, 내용/순서 오류 0건.
- listener의 `AUTO_READ=false → true`와 각 accepted channel의 읽기 중지·예약 재개를 통과했다.
- 실제 server/child channel이 각각 Epoll 및 IoUring 클래스임을 확인했다. io_uring 검사에서는 ring descriptor 2개가 생성됐다.
- 두 event loop group의 정상 종료와 프로브 exit code 0을 확인했다.
- Java 25 native access 경고가 stderr에 기록됐다. 검사 실패나 transport 예외는 없었다.

이 결과는 TCP 구동 가능성의 증거다. Minecraft 로그인·암호화·압축·프로토콜 전환·플러그인 호환성·장시간 backpressure·성능 향상을 검증한 결과는 아니다.

로컬 원본은 Git에서 제외되는 `run/io-uring-review-20260930/`에 보관했다. `NativeTransportProbe.java`, `stdout.txt`, `stderr.txt`, `evidence.json`, `upstream-metadata.json`이 포함된다.

- 프로브 소스 SHA-256: `310e9c9cfcb1ec6ec606347c813421bb50f0e887b8cbdf98aa68ddd4adb867d4`.
- stdout SHA-256: `40071dfb8d2b93aa2454d03efffa525ec16bb0557b1ca73c5133ab70d8b6d875`.

재실행은 같은 JDK의 Java source-file launcher로 위 Java 파일을 실행하고, classpath를 운영 라이브러리의 `io/netty/*/4.2.12.Final/*.jar`로 제한하면 된다. 사용 인수는 `-Xms32m -Xmx128m -XX:ActiveProcessorCount=2 -Dio.netty.eventLoopThreads=2`이며, 정확한 최초 실행 명령은 `evidence.json`에 남겼다. 전체 Gradle 테스트와 서버 빌드는 이번 문서 검토에서 재실행하지 않았다.

## 활성화 전 보완할 사항

**Netty 패치 버전을 먼저 검증한다.** 검토 시 GitHub의 최신 release는 `4.2.18.Final`이었다. 해당 release에는 진행 중 쓰기의 버퍼 수명을 보호하는 io_uring 수정 [#17238](https://github.com/netty/netty/pull/17238)이 포함된다. upstream 설명상 `shutdownOutput()` 또는 일부 쓰기 오류 경로가 진행 중 I/O와 겹치면 버퍼가 먼저 반환될 수 있었고, 일반 `close()` 경로는 같은 문제의 영향을 받지 않는다. 이 결함을 저장소에서 재현한 것은 아니지만, TCP echo 성공만으로 이런 경계 조건을 배제할 수는 없다. [4.2.18 release](https://netty.io/news/2026/09/09/4-2-18-Final.html)

공통 모듈·transport classes·native artifact를 같은 검증 버전으로 맞추고 플러그인 회귀를 확인한다. 그 동일한 새 아티팩트에서 epoll과 io_uring을 비교해야 transport 효과와 의존성 업그레이드 효과가 섞이지 않는다.

**fallback의 범위를 구분한다.** 현재 선택기는 `IoUring.isAvailable()`이 false면 Epoll/NIO를 선택한다. 반면 true 판정 이후 실제 event loop 생성이나 listener bind가 실패하는 경우를 잡아서 Epoll로 재생성하는 코드까지 있는 것은 아니다. 선호 설정뿐 아니라 실제 transport, native 가용성 실패 이유, 기동 실패 경로도 확인해야 한다. 기존 선택 정책 단위 테스트는 이 전체 native 기동 과정을 검증하지 않는다.

**Minecraft A/B를 완주한다.** 기존 [crowded audit harness](../crowded-audit/README.md)를 사용해 같은 JAR·JVM·heap·Netty thread 수·압축·시야·충돌 설정에서 transport만 바꾼다. 양쪽 모두 native transport를 활성화하고, 준비 구간 뒤 실행 순서를 바꾼 유효한 비교를 최소 3쌍 확보한다.

- 40명 + 소 240마리의 기존 조건과 이동·청크 전송 조건을 구분한다.
- 지역 MSPT p50/p95/p99, Netty CPU, 송신 큐 지연, echo RTT p95/p99, 할당/GC, direct memory를 함께 비교한다. syscall 비용의 검증에는 native profiling을 추가한다.
- PacketEvents/ProtocolLib의 수신자별 변환, 실제 운영 anticheat, 로그인, 월드 이동, 재접속, 순서/내용, 느린 수신자와 쓰기 중 종료를 확인한다.
- keepalive, 전체 상호 가시성, 몹 수, 정상 종료가 통과한 실행만 성능 비교에 사용한다. 연결이 떨어져 부하가 줄어든 실행은 제외한다.
- 장시간 접속에서 FD·direct memory·큐가 계속 증가하지 않는지도 확인한다. 반복 변동폭을 넘는 이득이 있고 기능 및 tail latency의 퇴행이 없어야 채택한다.

## 청크 파일 I/O는 별도 검토

Moonrise는 이미 청크 I/O를 작업 큐로 처리한다. [LinearRegionFile](../../../shreddedpaper-server/src/main/java/org/bxteam/divinemc/region/type/LinearRegionFile.java)은 압축과 임시 파일 작성, `force(true)`, 파일 교체를 수행한다. [BufferedRegionFile](../../../shreddedpaper-server/src/main/java/org/bxteam/divinemc/region/type/BufferedRegionFile.java)은 `FileChannel`과 헤더 갱신·force·compact를 사용한다. TCP transport를 바꿔도 이 경로는 그대로다.

저장 지연에서 큐 대기, NBT 처리, 압축/해제, 실제 read/write, force가 차지하는 시간을 먼저 나눈다. 운영 compression level 9가 비용의 후보라는 사실만으로 값을 낮추거나 저장 backend를 교체할 근거는 부족하다.

파일 I/O가 병목으로 확인되면 기존 월드의 읽기 경로부터 격리 실험하는 것을 권장한다. 쓰기 도입에는 partial read/write, 버퍼의 completion까지 수명, 같은 파일/청크의 순서, 오류 전파, force와 파일 교체, 종료·월드 언로드 시 drain, 기존 지역 QoS와 소유권 적용을 모두 유지해야 한다. Linux 파일 backend와 기존 Java fallback까지 추가해야 하므로 TCP 옵션 활성화보다 범위가 크다.

권장 순서는 Netty 관련 수정 검증 → 동일 아티팩트의 TCP A/B → 유효한 이득이 있으면 선택적 활성화다. 파일 I/O는 별도의 측정에서 필요성이 입증된 뒤 진행한다.
