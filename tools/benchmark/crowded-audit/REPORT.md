# Astatine 밀집 지역 성능 조사 — 2026-09-22

대상은 **한곳에 플레이어와 몹이 몰리는 상황**이다. 조사 기준 HEAD는
`56699f6`이며, 서버 구현을 변경하거나 운영 서버에 배포하지 않았다.
기존 소유권 검사 최적화의 미커밋 변경은 보존했다. 측정한 기존 빌드에는
그 변경이 포함되어 있으므로, 이 보고서를 깨끗한 HEAD만의 성능으로 해석하면 안 된다.

## 판단

밀집 지역에서는 **추적·패킷 전송의 반복 비용을 먼저 줄이고, 이동·충돌 처리의
할당과 탐색 비용을 다음으로 줄이는 것**이 유력하다. 지역 워커 수를 늘리거나
락 클래스를 교체하는 작업보다 측정 근거가 직접적이다.
소 240마리까지 늘린 정상 완료 실행에서는 긴 지역 틱과 GC 정지가 겹쳤다.
전송 및 엔티티 처리의 할당량을 줄이는 작업은 이 순간 지연을 겨냥한다.

큰 구조 변경이 필요하다면 동일 소유 영역 안에서 **불변 입력을 읽는 계산만
병렬화하고 게임 상태 변경은 소유 스레드에서 검증·적용**하는 방향이다.
여러 프로세스로 서버를 나눌 필요는 없다. 서로 영향을 주는 엔티티의 전체
`tick()`을 동시에 실행하는 방식은 현재 소유권 계약과 맞지 않는다.

## 측정 범위

- Java 25.0.2 / aarch64, 공유 8코어 호스트. 별도 loopback 서버, 1–3 GiB 힙,
  `ActiveProcessorCount=2`, 일반/과부하 지역 워커 각 1개. CPU affinity 제한은 아니다.
- 사용자 플러그인을 추가하지 않은 평지, view/simulation distance 2, 독립 지역 틱, 셀 크기 8청크,
  추적 제한 500, 추적 전체 갱신 주기 20. 네트워크 압축·인증·암호화는 사용하지 않았다.
- 통과 여부를 확인하는 대조 부하: 80명 이동 → 소 80마리를 더한 혼합 부하.
  별도 실행에서는 소 240마리의 부하도 정상 완료했다. 소의 AI와 충돌은 켰으며 플레이어
  팀 충돌, 자연 스폰, 끼임 피해는 껐다. 소는 울타리 안에서 무적·영속 상태다.
- 클라이언트 패킷 해석은 4개의 Node worker thread로 분산했다. 게임 서버는
  하나의 JVM이다. 각 클라이언트가 다른 79명을 계속 보는지도 검사한다.
- JFR의 실행 샘플·틱 이벤트·GC·스레드별 할당 계수·1ms 이상 park/monitor
  이벤트를 조사했다. `DelayQueue`의 다음 틱 대기는 애플리케이션 락 대기와 구분했다.

일반 profile 설정에는 Minecraft 패킷 통계가 포함된다. 그 통계 자체가
`JfrProfiler.networkStatFor()`에서 패킷마다 주소 문자열을 만들고 맵을 조회했다.
이를 정상 실행 비용으로 오인하지 않도록 두 정상 완료 실행의 모든 구간에서
`minecraft.NetworkSummary`, `minecraft.PacketSent`, `minecraft.PacketReceived`를 껐다.
원시 프로파일과 실제 실행 명령을 함께 보관한다.

소 240마리의 초기 두 스트레스 시도는 keepalive timeout으로 완료 조건을 통과하지
못했다. 이때에는 Minecraft의 네트워크 JFR 통계가 켜져 있었다.
단일 클라이언트 루프에서는 폴링 간격도 크게 늘었다. 네 개로 분산한
뒤에는 관측된 클라이언트 루프 최대 지연이 약 111ms였지만 timeout은 재발했다.
해당 통계를 끈 후속 실행은 240마리에서도 통과했다. 따라서 초기 실패를 정상
운영의 수용 한계로 사용하지 않는다. 호스트 상태와 실행별 차이도 있으므로
timeout 원인을 부하 발생기나 JFR 하나로 확정하지 않는다. 실패 원인을 추가로
분리하려면 패킷 큐 체류시간과 keepalive 송수신 시각을 계측해야 한다.

## 실제 측정 결과

80마리 대조 실행은 **80명 모두 연결 유지, 서로 다른 79명 전원 가시성 유지,
190회 상태 검사, 연결 오류 0건**으로 완료했다. 혼합 구간 전후 모두 소 80마리를
확인했고 서버도 종료 코드 0으로 끝났다. 한 소유 지역 `world:0,0`만 틱했다.
클라이언트 워커에서 관측한 최대 이벤트 루프 지연은 133.6ms였다.

| 측정값 | 플레이어 80명, 45초 | 플레이어 80명 + 소 80마리, 60초 |
| --- | ---: | ---: |
| 완료된 지역 틱 이벤트 | 898 | 1,198 |
| 지역 틱 p50 | 9.53ms | 14.68ms |
| 지역 틱 p95 | 20.06ms | 25.94ms |
| 지역 틱 p99 | 25.00ms | 32.87ms |
| 지역 틱 최대 | 61.79ms | 85.38ms |
| 스케줄 지연 p99 | 2.27ms | 3.68ms |
| 전체 Java 실행 샘플 | 2,233 | 5,644 |
| Netty 실행 샘플 비중 | 64.3% | 68.9% |
| 지역 워커 실행 샘플 비중 | 31.4% | 29.0% |
| 지역 워커 할당률 | 10.27 MB/s | 19.65 MB/s |
| Netty 할당률 | 7.30 MB/s | 15.32 MB/s |
| GC 정지 횟수 / 최대 | 1회 / 40.56ms | 5회 / 66.31ms |

MB는 십진 단위다. 할당률은 `ThreadAllocationStatistics`의 구간 내 계수 차이로
계산했다. 실행 샘플 비중은 정확한 CPU 시간 비중이나 TPS의 결정 요인과 같지 않다.
Netty는 네 개의 event loop를 사용하지만 이 지역의 틱은 한 번에 한 워커만 실행한다.
따라서 표본이 많다는 이유만으로 네트워크가 틱 지연의 원인이라고 확정하지 않는다.
이 표는 한 구현에 부하를 추가한 관측값이며, 최적화 전후 비교가 아니다.

혼합 구간에서 중요한 스택은 다음과 같다.

- Netty 3,890개 표본 중 3,649개가 `Connection.doSendPacket`을 포함했다.
  인코더뿐 아니라 연결별 큐 작업, outbound pipeline, 버퍼 할당이 반복되었다.
  `PacketEncoder.encode`는 989개, `AdaptiveByteBufAllocator.newDirectBuffer`는
  719개 표본에 포함되었다. 이 포괄 스택 수치는 서로 겹치므로 합산하지 않는다.
- 지역 워커 1,639개 표본에서 추적·전송 단계 437개(26.7%), 플레이어 틱 436개,
  그 밖의 엔티티 틱 451개, AI goal/navigation 153개, 길찾기 30개,
  `pushEntities` 57개였다. 분류는 [summarize.py](summarize.py)의 우선순위를 따른다.
- 지역 락 취득·정리로 분류된 표본은 합계 5개(0.3%)였다. 1ms 이상 지역 워커
  park는 모두 다음 틱을 기다리는 `DelayQueue` 경로였고, 해당 워커의 다른
  park/monitor 대기는 기록되지 않았다. **실패한 try-lock의 예약 재시도나
  1ms 미만 경합까지 없었다는 뜻은 아니다.** 메인 스레드의 정상 틱 대기도
  지역 락 경합으로 계산하지 않았다.
- Netty의 leaf 표본에는 `StampedLock.casState`도 132개 있었다. 이는 버퍼
  allocator 경로여서 지역 락과 구분해야 한다. 전송 할당을 줄일 때 함께 확인할 대상이다.
- 지역 할당 샘플에서 빈 block-effect 목록 복사, 이동 경로의 블록 방문 집합,
  전송용 실행 객체가 나타났다. 할당 샘플의 초기 가중치가 클 수 있으므로
  그 가중치를 위 구간의 실제 할당량으로 환산하지 않았다.

실제 런타임 클래스를 별도 JVM 세 개에서 호출한 방법 단위 검사도 수행했다.
각 경우 20,000회 워밍업 후 5,000회씩 9개 표본을 수집했다.

| 호출 | JVM별 중앙값의 중앙값 | 호출당 할당량 |
| --- | ---: | ---: |
| 1개 수정 셀 + 주변 셀 락 취득·해제 | 2.19µs | 약 1.74–1.78 KB |
| 64개 수정 셀 + 주변 셀 락 취득·해제 | 30.71µs | 27,928 B |
| 자기 락이 없는 정리, 타 스레드 점유 0셀 | 0.61µs | 568 B |
| 자기 락이 없는 정리, 타 스레드 점유 512셀 | 10.47µs | 12,728 B |
| 빈 `StepBasedCollector.applyAndClear()` | 0.57µs | 192 B |

락 호출은 경합이 없는 경우다. JIT·공유 호스트 영향이 있으며 서버 전체 처리량으로
확대 해석하지 않는다. 빈 이펙트 collector는 이미 생성한 객체를 재사용해도
세 JVM 모두 192 B를 할당했다. 이는 작고 구체적인 할당 제거 후보를 확인한 것이다.
원시 표본과 실행별 차이는 [RESULTS.json](RESULTS.json)에 남겼다.

### 240마리 정상 완료 실행: 순간 지연과 GC

같은 JAR과 부하 발생기로 패킷 JFR 통계를 끈 채 재실행했다. **80명 전원,
가시성 검사 190회, 연결 오류 0건, 전후 소 240마리, 서버 종료 코드 0**을 확인했다.
이 실행도 소유자는 하나였으며 대표 셀은 `world:-1,-1`이었다. 대표 셀은 실제
플레이어 위치와 다를 수 있다. 클라이언트 워커 최대 루프 지연은 463.2ms였다.

| 측정값 | 같은 실행의 플레이어 80명, 45초 | 플레이어 80명 + 소 240마리, 60초 |
| --- | ---: | ---: |
| 완료된 지역 틱 이벤트 | 899 | 1,163 |
| 지역 틱 p50 / p95 / p99 | 5.55 / 13.12 / 16.81ms | 32.82 / 55.11 / 139.14ms |
| 지역 틱 최대 | 35.51ms | 284.62ms |
| Netty / 지역 워커 실행 샘플 비중 | 59.9% / 32.8% | 69.8% / 29.4% |
| 지역 워커 / Netty 할당률 | 10.39 / 7.62 MB/s | 51.72 / 77.04 MB/s |
| GC 정지 횟수 / 누적 / 최대 | 3회 / 80.27 / 55.39ms | 26회 / 2,352.37 / 203.86ms |

혼합 구간의 지역 워커 표본 3,239개 중 추적·전송은 1,012개(31.2%),
`pushEntities`는 320개(9.9%)였다. 락 취득·정리는 합계 5개(0.15%)였다.
1ms 이상 지역 monitor 대기는 한 건, 3.60ms였다. 경로는 지역 락이 아니라
`ServerLevel.getWorldBorder()` → `DimensionDataStorage.computeIfAbsent()` →
`ConcurrentHashMap.compute()`였다.

**가장 긴 284.62ms 지역 틱 구간에는 약 203.86ms의 GC 정지가 겹쳤다.**
264.54ms 틱에는 182.97ms, 214.45ms 틱에는 158.19ms가 겹쳤다.
JFR 완료 이벤트 시각에서 보고된 wallNanos를 뺀 근사 구간과 GC 이벤트를 비교했으며,
이벤트 생성 지연 때문에 세밀한 서브밀리초 귀속까지 보장하지 않는다.
큰 순간 지연에서 할당량·GC를 함께 봐야 한다는 직접적인 근거다.

Netty 큐 확장의 `MpscUnboundedAtomicArrayQueue` 배열 할당도 지역 스택에 나타났다.
패킷마다 전송 작업을 등록하는 경로가 할당량과 큐 압력을 함께 늘릴 수 있다.
큐 길이·체류시간을 측정하지 않았으므로 이 표본만으로 무제한 백로그가 지속됐다고
확정하지 않는다. 전송 배치 실험에서는 반드시 함께 계측한다.

이 실행 당시 호스트 load average는 약 9–11이었고, 앞선 80마리 실행은 약 5–6이었다.
소유자 대표 셀과 JIT 상태도 달라졌으므로 두 실행의 수치를 선형 확장률로 비교하지
않는다. 압축·플러그인이 없는 1–3 GiB 시험 JVM에서 재현한 현상이다.
운영 JVM의 GC나 플레이어 체감 지연이 같은 수치라는 뜻은 아니다.

측정 아티팩트는 기존 Paperclip 빌드다. SHA-256은
`b9318dcdb2613054e2ada3df8daa88321aed232595e6396e909acd20cd0b6d97`,
추출된 런타임 JAR은 `dd2a65641dc057d34c0c735d13edb0fb0e13671fa2e8b5497ae1ae364e70f560`이다.
지역·스케줄러·소유권 관련 클래스 21개의 바이트가 로컬 빌드 출력과 일치함을
확인했다. 전체 소스를 새로 빌드해 비교한 실험은 아니다.
원시 JFR·연결 검사·manifest는 저장소의 `run/crowded-audit-20260922-control/`과
`run/crowded-audit-20260922-control240/`에 보관했다. 파일 해시·설정 해시·기존 소스
변경 해시는 `RESULTS.json`에 기록했다.
저장소에는 재현 도구와 작은 결과 파일을 커밋하고 큰 실행 출력은 포함하지 않는다.

## 우선순위와 구체적 수정 지점

### 1. 같은 상태의 수신자별 전송 비용과 추적 비용

[ChunkMap.TrackedEntity](../../../shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ChunkMap.java)의
`moonrise$tick()`은 엔티티마다 후보 플레이어를 순회하고 거리, 가시성, 전송한
청크 여부를 확인한다. 기존 단일 순회 최적화는 이미 적용되어 있다.
엔티티 E개와 후보 플레이어 P명이 같은 범위에 있으면 후보 검사는 여전히 대략
`E × P`다. 80명과 몹 80마리라면 전원이 후보라는 조건에서 틱당 12,800회,
몹 240마리라면 25,600회다. 이는 코드상 반복 횟수 예시이며 전송 패킷 수는 아니다.

[ShreddedPaperEntityTicker.processTrackQueue](../../../shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperEntityTicker.java)는
추적 갱신 후 `ServerEntity.sendChanges()`를 실행한다.
[Connection.sendPacket](../../../shreddedpaper-server/src/minecraft/java/net/minecraft/network/Connection.java)은
Netty 스레드 밖에서 호출하면 연결의 event loop에 작업을 등록한다.
[PacketEncoder](../../../shreddedpaper-server/src/minecraft/java/net/minecraft/network/PacketEncoder.java)는
해당 연결의 버퍼에 다시 인코딩한다. 추적기와 Netty 양쪽을 측정해야 하는 이유다.

첫 구현 후보는 연결별 전송 작업을 **순서가 보장되는 유한한 배치**로 묶어
패킷마다 생기는 실행 객체와 큐 등록을 줄이는 것이다. 이후 내용이 동일하고
불변인 이동·회전 패킷에 한해 인코딩 결과 공유를 검토한다. 연결 종료,
pairing/unpairing, teleport, 프로토콜 전환, 번들 경계, 송신 콜백을 보존해야 한다.
PacketEvents/ProtocolLib의 수신자별 변환이나 언어·프로토콜·압축 상태가 다르면
일괄 공유하지 말고 기존 경로로 돌아가야 한다.

현재 flush 억제는 `MinecraftServer.tickChildren()`의 전역 suspend와
`ShreddedPaperPlayerTicker.tickPlayer()` 끝의 resume에 걸쳐 있다. 전역 resume
루프는 독립 틱 모드에서는 실행하지 않는다. 독립 지역의
추적 단계는 플레이어 단계보다 뒤에서 실행된다. 따라서 기존 전역 틱의 전송
배치 구간이 독립 지역의 추적 단계까지 정확하게 감싼다고 가정할 수 없다.
배치 구간과 연결별 drain을 지역 실행에 맞추는 것이 구체적인 검토 지점이다.
`MinecraftServer.isSameThread()`는 `TickThread.isTickThread()`로 확장되어 있으므로,
지역 스레드가 언제나 false를 반환한다고 가정하는 분석은 틀리다.

이미 `FlushConsolidationHandler`가 있으므로 `writeAndFlush` 호출 수를 곧바로
시스템 호출 수로 간주하면 안 된다. Netty도 이 핸들러가 여러 flush 요청을
합친다고 명시한다. [Netty 공식 설명](https://netty.io/4.2/api/io/netty/handler/flush/FlushConsolidationHandler.html)
따라서 단순히 flush 옵션을 켜는 것보다 배치 전후 **큐 작업 수, 할당량,
실제 송신 지연**을 비교해야 한다.

추적 검사는 불변 값의 반복 계산을 틱 내에서 묶고, 상태 변경에 따른 정확한
무효화가 가능한 항목부터 줄인다. 여러 틱 동안 `canSee`를 무작정 캐시하면
숨김 처리가 늦어질 수 있다. 전원이 같은 위치에 있으면 공간 인덱스만으로
수신자 수를 줄이기도 어렵다.

### 2. 이동·블록 접촉의 임시 객체와 근접 엔티티 탐색

[Entity.checkInsideBlocks](../../../shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/Entity.java),
[BlockGetter.forEachBlockIntersectedBetween](../../../shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/BlockGetter.java),
[InsideBlockEffectApplier.StepBasedCollector](../../../shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/InsideBlockEffectApplier.java)가
구체적인 후보다. 움직일 때 경로 방문 집합, 벡터·AABB, 이펙트 목록 처리에
임시 객체가 생긴다. 빈 이펙트 목록을 복사하는 경로, 작은 방문 집합, 재진입에
안전한 임시 저장소부터 검토한다. 단순히 이동이나 블록 접촉 검사를 생략해서는 안 된다.

[LivingEntity.pushEntities](../../../shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/LivingEntity.java)는
`getPushableEntities()`로 후보 목록을 만든 **다음** 충돌 횟수 제한을 적용한다.
충돌 제한이 있어도 후보 탐색 비용까지 사라지는 것은 아니다. 아주 조밀한
구역의 세부 공간 인덱스나 소비 가능한 후보 탐색을 검토하되, 끼임 판정에 필요한
개수와 기존 처리 순서·팀 규칙은 유지해야 한다. 실제로 거의 모든 엔티티가 서로
접촉하는 경우 공간 인덱스의 효과도 제한된다.

이 작업은 대규모 스케줄러 변경보다 범위가 작다. 효과는 평균 틱뿐 아니라
할당률, GC pause, 지역 틱 p95/p99로 확인한다.

### 3. 같은 지역 안의 순수 계산을 분리하는 구조 변경

[ShreddedPaperChunkTicker](../../../shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperChunkTicker.java)는
독립 틱 모드에서 플레이어, 청크, 엔티티, 추적, 블록 엔티티 단계를 같은 소유
스레드에서 수행한다. `process-track-queue-in-parallel` 설정이 있어도 이 모드의
지역 내부 추적을 병렬화하지 않는다.
[DivineConfig](../../../shreddedpaper-server/src/main/java/org/bxteam/divinemc/config/DivineConfig.java)는
독립 틱을 켜면 기존 비동기 길찾기를 명시적으로 끈다.
[PathFinder](../../../shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/pathfinder/PathFinder.java)에도
동일한 보호 조건이 있다. 설정 하나로 기존 비동기 경로를 강제 활성화하면 안 된다.

확장 방향은 `소유 스레드의 불변 스냅샷 → 한도 있는 계산 워커 → 소유 스레드에서
epoch·위치·대상 상태 확인 후 적용`이다. 처음부터 모든 AI를 옮기기보다 길찾기나
추적 후보의 순수 수학 계산 하나를 택한다. 스냅샷 비용이 작은 작업보다 크면
기존 동기 경로를 사용하고, 늦은 결과는 재검증하거나 폐기한다. 플러그인 호출과
월드 쓰기는 소유권 아래에 남긴다. 소만 사용한 이번 부하로 주민 Brain이나
적대 몹 길찾기의 개선 폭을 판단할 수는 없다.

Folia 역시 함께 틱해야 하는 청크와 주변의 독립성 조건을 먼저 보장한다.
스레드 수만 늘려 가까운 엔티티의 틱을 분산시키는 방식은 그 조건을 해결하지 않는다.
[Folia 지역 불변식](https://docs.papermc.io/folia/reference/region-logic/)

### 4. 락은 자료구조 교체보다 보호 범위·재시도 계측을 먼저

[ShreddedPaperRegionLocker](../../../shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperRegionLocker.java)의
read-only isolation도 **다른 스레드가 공유할 수 있는 읽기 락이 아니다**.
각 셀은 한 스레드만 점유한다. 기본 8청크 셀은 한 변이 128블록이고, 단일 셀도
주변 한 셀을 포함한 3×3 범위를 잠근다.

실제 런타임 클래스를 사용한 두 스레드 검사에서 중심 셀 `(0,0)`과 `(2,0)`은
서로 다른 수정 셀이지만 보호 범위가 겹쳐 동시 취득에 실패했고, `(3,0)`은 성공했다.
`read`라는 이름만 보고 공유 읽기로 바꾸면 경계의 쓰기 승격과 충돌한다.

[LevelChunkRegionMap](../../../shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/LevelChunkRegionMap.java)은
인접 소유자를 최대 64셀까지 합치고, 기본 분할은 활성 셀 연결 성분이 끊어졌을 때
수행한다. 바쁘다는 이유만으로 연결된 소유자를 더 작은 병렬 작업으로 나누지 않는다.
다만 실제로 상호작용하는 한 무리의 엔티티가 같은 셀 안에 있으면 보호 범위를
줄여도 그 무리 자체의 순차 실행 한계는 남는다.

[RegionTickScheduler](../../../shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionTickScheduler.java)는
락 취득 실패 후 1, 2, 4, …, 최대 50ms 뒤 재시도한다. 이때 재시도용
`scheduledStartNanos`가 갱신되므로 현재 lag와 park 이벤트만으로 최초 마감부터의
총 락 대기를 알 수 없다. 우선 **최초 예정 시각, 실패 횟수, 실패한 셀/소유자,
누적 대기**를 별도로 기록하고 경계 부하에서 평가해야 한다.

매 작업 후 `releaseCurrentThreadLocks()`가 전체 `lockedRegions`를 스캔하는
것도 확인했다. 자기 락이 없는 정상 종료에서 전체 맵을 훑지 않는 개선은 가능하지만,
고아 락 복구·예외 종료 의미를 보존해야 한다. 이 메서드와 원시 락 교체를
밀집 지역의 첫 대형 최적화로 삼을 근거는 현재 약하다.

운영 플러그인을 넣으면 우선순위가 바뀔 수 있다.
[SynchronousPluginExecution](../../../shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/SynchronousPluginExecution.java)은
호환 실행에서 hard/soft dependency를 따라 계산한 공통 플러그인 락을 잡는다.
별도 지역의 콜백도 같은 의존 락을 공유할 수 있다. 이번 플러그인 없는 실행으로
이 경합을 측정한 것은 아니므로, 실제 플러그인 스택의 대기 시간을 별도로 확인한다.

과부하 lane의 일반 워커 차단 정책도 여러 혼잡 지역에는 영향을 줄 수 있지만,
한 소유 영역의 엔티티를 여러 워커로 나누지는 않는다. 이번 요청의 첫 개선안으로
워커 수나 degraded-thread 수를 바꾸는 것은 근거가 부족하다.

## 후속 구현의 통과 기준

먼저 전송 배치 하나를 독립된 패치로 만들고 같은 JAR 기준 A/B를 반복한다.
80/160명, 몹 0/80/240마리, 정지/이동을 분리하고 압축 활성화 및 실제 플러그인 조합도
추가한다. 다음 기준을 함께 만족해야 개선으로 판정한다.

- 연결·가시성·메타데이터·pairing 순서가 유지되고 플러그인 훅이 누락되지 않는다.
- 지역 틱 p95/p99, 서버와 Netty CPU, 큐 깊이·체류시간, 할당률과 GC가 개선된다.
- 전송 배치 때문에 이동·teleport·keepalive 지연이 악화되지 않는다.
- 소유권 회귀, 경계 이동, merge/split, 월드 언로드, 종료 테스트를 통과한다.

이번 조사는 개선 지점을 찾은 결과다. 구현 전후 성능 향상률, 실서버 수용 인원,
클라이언트 FPS 향상을 측정하거나 보장한 것이 아니다.
