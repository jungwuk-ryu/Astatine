# Astatine Performance Optimization PRD

## 0. 조사 상태와 신뢰 경계

이번 세션에서 **로컬 `git clone`과 빌드 실행은 실패**했습니다. 컨테이너에서 실행한 명령은 다음과 같았고, DNS 해석 실패로 저장소를 받을 수 없었습니다.

```text
git clone --branch ver/1.21.11 --single-branch https://github.com/jungwuk-ryu/Astatine /mnt/data/Astatine

fatal: unable to access 'https://github.com/jungwuk-ryu/Astatine/':
Could not resolve host: github.com
```

따라서 아래 명령은 실제 실행하지 못했습니다.

```bash
./gradlew applyAllPatches --no-configuration-cache --stacktrace
./gradlew :shreddedpaper-server:compileJava --rerun-tasks --no-configuration-cache --stacktrace
./gradlew :shreddedpaper-server:test --no-configuration-cache --stacktrace
```

대신 GitHub 웹/Raw 파일로 확인 가능한 문서, 빌드 파일, 핵심 region/scheduler/mailbox/ownership/chunk-QoS 코드 일부를 정적 조사했습니다. **전체 `find . -type f` 인벤토리는 생성하지 못했으므로 “전체 파일 전수 완료”라고 주장하지 않습니다.** 아래 PRD는 “웹 기반 정적 감사 v0.1”이며, 코드 위치가 있는 P0/P1 항목은 구현 이슈로 바로 분해 가능하게 작성했습니다. redstone/fluid/piston/explosion/network/NBT/Minecraft patch stack 전체는 이번 세션에서 충분히 열람하지 못했으므로 별도 “미검토/부분 검토”로 표시합니다.

---

## Codex Review: 수락/보류/거절 판정 (2026-04-29)

이 문서의 원본 제안은 주니어 조사자가 제한된 정적 감사로 작성한 초안입니다. 현재 로컬 코드베이스를 다시 확인한 결과, 일부 제안은 이미 부분 적용되어 있거나, 성능 이점은 있어도 tick order, ownership, chunk admission, plugin compatibility에 영향을 줄 수 있습니다. 따라서 아래 판정 중 즉시 구현 가능한 저위험 작업은 [`SAFE_PERFORMANCE_OPTIMIZATION_PRD_2026-04-29.md`](SAFE_PERFORMANCE_OPTIMIZATION_PRD_2026-04-29.md)에, feature-flag와 ordering proof가 필요한 통제 실험은 [`EXPERIMENTAL_PERFORMANCE_OPTIMIZATION_TRACK_2026-04-29.md`](EXPERIMENTAL_PERFORMANCE_OPTIMIZATION_TRACK_2026-04-29.md)에 분리합니다.

판정 기준:

* **ACCEPT**: 현재 코드 기준으로 부작용이 거의 없고, 테스트로 동일 동작을 쉽게 증명할 수 있음.
* **CONDITIONAL**: 이점은 타당하지만, 구현 범위를 좁히고 invariant test를 먼저 붙여야 함.
* **MEASURE_ONLY**: 병목일 가능성은 있으나 알고리즘/동시성 변경은 위험하므로 계측만 먼저 수행.
* **DEFER/REJECT**: 지금 PRD에는 넣지 않음. tick/order/ownership/chunk safety 리스크가 크거나 현재 코드에 이미 해당 보완이 있음.

| ID | 판정 | 현재 코드 확인 | 수락 범위 | 거절/보류 사유 |
| --- | --- | --- | --- | --- |
| PERF-001 | **ACCEPT, corrected scope** | `RegionOwner.cellPositionsSnapshot()`은 이미 정렬된 `List<RegionPos>`를 캐시하지만, `ShreddedPaperRegionLocker.sortedUniqueRegions()`가 lock 시점마다 다시 복사/정렬하고, radius-1 isolation snapshot은 정렬 없이 `List.copyOf`됩니다. | `RegionOwner`에 sorted packed `long[]` snapshot 추가, radius-1 isolation 정렬 보장, locker에 sorted input fast path 추가. | 내부 lock map을 곧바로 primitive map으로 바꾸는 것은 `ConcurrentHashMap` 동시성 의미가 바뀌므로 이번 안전 PRD에서 제외. |
| PERF-002 | **MEASURE_ONLY** | `ConcurrentHashMap<RegionPos, LockedRegion>`, global `StampedLock`, `CompletableFuture` unlock chain은 실제 hot path입니다. | wait/hold/failure/park count JFR 계측만 수락. | lock representation, CHM 제거, unlock continuation 교체는 deadlock/ordering 위험이 커서 수치와 jcstress 전까지 보류. |
| PERF-003 | **ACCEPT, merged with PERF-001** | radius-1 snapshot은 캐시되지만 primitive 정렬 snapshot은 없습니다. | PERF-001에 포함. | 별도 작업으로 두면 같은 invalidation 로직을 두 번 건드리므로 병합. |
| PERF-004 | **CONDITIONAL** | `RegionChunkIoTracker`에 admission-critical `AtomicInteger/AtomicLong/AtomicBoolean`이 많습니다. | event/string guard, counter grouping/manual padding, non-admission diagnostic counter 분리만 수락. | cap 판단에 쓰이는 counters를 `LongAdder`나 plain long으로 바꾸는 것은 정확한 admission limit을 깨므로 거절. |
| PERF-005 | **MEASURE_ONLY** | static single-thread `EXECUTOR_BACKPRESSURE_RETRY_EXECUTOR`가 있습니다. | queue depth, drain latency, backlog age 계측만 수락. | shard executor나 worker-local drain은 retry ordering/fairness를 바꾸므로 chunk stress 수치 전까지 보류. |
| PERF-006 | **DEFER** | `PermitTask`는 이미 primary lifecycle을 `AtomicInteger state`로 bit-pack하지만, permit/overflow/backlog ownership은 여러 `AtomicBoolean`으로 남아 있습니다. | 이번 PRD에서는 event guard와 leak detector 수준만 허용. | permit/overflow/backlog flag를 한 번에 bit-pack하면 permit leak, duplicate release, chunk future hang 위험이 큽니다. |
| PERF-007 | **CONDITIONAL** | `RegionMailbox`는 `EnumMap`과 class별 Atomic counters를 씁니다. | `RegionTaskClass.ordinal()` 기반 arrays, drain order parity test, capacity/reject invariant test가 붙는 범위만 수락. | queue 종류, class ordering, critical non-dropping semantics 변경은 거절. |
| PERF-008 | **CONDITIONAL, metrics only** | critical/transferred high-water warning/event는 일부 이미 있습니다. | oldest age, peak depth, producer context, coalescing-candidate diagnostics만 수락. | critical/owner handoff/player action을 drop/coalesce/backpressure wait하는 변경은 gameplay와 plugin order를 바꿀 수 있어 거절. |
| PERF-009 | **REJECT for safe PRD** | delayed task는 `PriorityQueue` 기반입니다. | 없음. 별도 실험 branch에서만 검토. | timing wheel은 same readyTick ordering과 Bukkit/Folia scheduler callback timing을 바꿀 수 있습니다. |
| PERF-010 | **MEASURE_ONLY** | scheduler는 normal/degraded `DelayQueue`와 `normalWorkersMayStealDegraded = degradedThreads == 0` 정책을 씁니다. | lane utilization, queue wait, degraded backlog age, wakeup latency 계측만 수락. | idle-steal, per-worker wheel, DelayQueue 제거는 tick cadence/fairness 영향이 커서 보류. |
| PERF-011 | **ACCEPT** | `RegionTickScheduler.snapshots()`가 stream/filter/map/sorted/toList를 사용합니다. | explicit loop, bounded top-N command path, diagnostic sampling interval. | 없음. diagnostic-only라 리스크가 낮음. |
| PERF-012 | **ACCEPT** | fallback sample이 stack trace, `ArrayList`, `String.join`, synchronized global buffer를 사용합니다. | sampling gate, fixed ring buffer, StackWalker/JFR-enabled-only caller capture. | ownership guard 자체는 변경하지 않음. |
| PERF-013 | **MEASURE_ONLY** | handoff path는 lambda/future continuation allocation 가능성이 있습니다. | owner handoff/requeue count, epoch mismatch, max requeue metrics만 수락. | typed task rewrite는 callback order와 captured state lifetime을 바꿀 수 있어 보류. |
| PERF-014 | **MEASURE_ONLY** | merge/split는 global write lock과 exact lock 아래 수행됩니다. | write-lock hold time, abort/retry count, owner remap invariant 계측만 수락. | two-phase merge/split rewrite는 lost cell/duplicate owner/retired owner tick 위험이 커서 거절. |
| PERF-015 | **ACCEPT** | `RegionOwner.absorbCellsFrom()`이 `this.cells` 후 `source.cells` nested lock을 잡습니다. | deterministic lock ordering 또는 assert/test guard. | 없음. correctness guard이며 성능 부작용은 거의 없음. |
| PERF-016 | **CONDITIONAL** | `RegionOverloadController.recordTick()`는 여러 volatile metric을 씁니다. `ewmaMspt`와 `quarantineStrikes`는 degrade/quarantine 판정의 누적 상태입니다. | `loadClass`, EWMA 계산, quarantine strike semantics는 유지하고 `lastMailboxDepth`, `lastChunkIo*`, `lastDeferredWork` 같은 표시용 last-observation metrics만 immutable snapshot/periodic publish 후보로 분리. | `ewmaMspt`, `loadClass`, chunk admission이 읽는 값까지 stale snapshot으로 바꾸는 것은 거절. |
| PERF-017 | **ACCEPT** | `ChunkRequestEvent.shouldCommitSample()` guard는 많지만 일부 call site는 action string을 먼저 구성할 수 있습니다. `RegionTickEvent`, `RegionQueueEvent`, `CrossRegionTaskEvent`도 `event.isEnabled()` 확인 없이 객체/문자열 필드를 채웁니다. | JFR enabled/sample guard를 앞당기고 action string table/lazy event fill 적용. Mailbox queue/cross-region event도 같은 안전 범위에 포함. | event 이름을 바꿔 tooling을 깨는 변경은 거절. |
| PERF-018 | **ACCEPT** | `RegionRuntimeState.STATES`는 static CHM이고 orphan/leak diagnostics가 부족합니다. | state removal reason, orphan count, active owner parity check, heap-dump aid. | pending mailbox/chunk work가 있는 state를 강제 제거하는 변경은 거절. |
| PERF-019 | **ACCEPT test/documentation only** | 현재 `RegionTickScheduler.get()` 자체에는 config guard가 없지만, 확인된 호출부 `ShreddedPaperChunkTicker.tickChunks`는 `independentRegionTicking` true일 때만 호출합니다. | config false boot/thread test와 call-site assertion. | `get()`에서 hard fail을 넣으면 테스트/관리 코드가 깨질 수 있어 이번 안전 PRD에서는 보류. |
| PERF-020 | **ACCEPT audit only** | redstone/fluid/piston/explosion patch stack은 order-sensitive hot path입니다. | grep inventory, fixture coverage, canonical contraption replay 목록 작성. | 실제 propagation batching/coalescing 최적화는 regression suite 전까지 거절. |

외부 라이브러리 판정:

| 제안 | 판정 | 이유 |
| --- | --- | --- |
| 기존 JCTools/fastutil 확장 사용 | **ACCEPT** | 이미 의존성/패턴이 존재하고 boxing 감소 목적에 맞습니다. 단, queue producer/consumer shape를 테스트해야 합니다. |
| Agrona | **DEFER** | padded counters/ring buffer는 매력적이지만 새 dependency와 shading 비용이 있습니다. manual padding/JDK primitive 구조로 먼저 검증합니다. |
| Caffeine | **REJECT for region tick PRD** | cache eviction/adaptive policy는 deterministic game state와 섞기 위험합니다. 순수 계산 캐시가 특정되기 전까지 제외. |
| Netty utilities / Recycler | **REJECT for game logic** | thread-local retention과 lifecycle leak 위험이 있습니다. network/serialization 별도 PRD가 아니면 제외. |
| LMAX Disruptor | **REJECT** | 일반 per-region mailbox에는 topology가 맞지 않고 ordering 검증 비용이 큽니다. |
| RoaringBitmap | **DEFER** | owner/isolation cell set은 보통 작아서 현재 hot path에는 과합니다. 큰 sparse diagnostic set이 확인될 때만 검토. |
| Netty io_uring | **REJECT for this PRD** | 최근 접속 호환성 이슈가 있었고 network transport는 별도 안정화 주제입니다. low-side-effect 성능 PRD에서 제외. |
| Chronicle Queue/Map | **REJECT** | licensing/operational complexity가 크고 현재 병목과 직접 연결되지 않습니다. |

JVM/Java 판정:

* **ACCEPT:** JFR/async-profiler/perf 기반 상시 측정, G1/ZGC/Shenandoah 비교, GC/safepoint 로그.
* **REJECT:** region worker/mailbox/tick loop에 virtual thread 적용. affinity와 latency가 더 중요합니다.
* **DEFER:** VarHandle weaker access mode. ARM에서 이점은 가능하지만 필드별 happens-before proof 전까지 적용하지 않습니다.

## Experimental Track: Safe PRD 밖에서 유지할 통제 실험

아래 항목들은 safe PRD에 즉시 구현 항목으로 넣지는 않지만, 아이디어를 폐기하지 않습니다. 반드시 feature flag 기본 off, baseline profile, invariant test, rollback threshold를 갖춘 실험 PRD로만 진행합니다.

| ID | 재분류 | 허용되는 실험 범위 | 절대 금지 |
| --- | --- | --- | --- |
| PERF-005 | **MEASURE_FIRST_THEN_CONDITIONAL_SHARDED_RETRY_EXPERIMENT** | retry latency/queue depth 계측 후, `worldId + ownerId` 또는 scheduler lane shard를 feature flag로 실험. same-owner FIFO, existing cap check, future completion invariant 유지. | task drop, cap 초과 admission, region worker tick logic 중 core gameplay drain. |
| PERF-009 | **DEFER_EXPERIMENTAL_WITH_ORDER_PROOF** | `readyTick + sequenceNumber` golden ordering test를 먼저 만들고, short-delay bucket + long-delay fallback heap을 feature flag로 실험. | same-readyTick ordering 변경, Bukkit/Folia callback timing 조기 실행, fallback heap/bucket 간 comparator 불일치. |
| PERF-010 | **MEASURE_FIRST_THEN_GATED_IDLE_STEAL_EXPERIMENT** | normal queue에 ready work가 없고 degraded task가 already-due일 때만 제한된 steal budget으로 실험. earliest deadline보다 빠른 실행 금지. | normal region p99 오염, no-catch-up-storm invariant 위반, due 전 tick 실행. |
| PERF-013 | **CONDITIONAL_METADATA_WRAPPER** | existing `Runnable` delegate는 그대로 두고 owner id, epoch, requeue count, task class metadata wrapper만 추가. debugging/metrics 목적. | object pooling, captured lambda lifetime 변경, callback order 변경, gameplay task coalescing. |
| PERF-014 | **MEASURE_ONLY + TWO_PHASE_FEASIBILITY_DESIGN** | write lock hold section을 phase별로 계측하고, read-snapshot candidate discovery feasibility 문서화. commit은 owner id/epoch/cell snapshot version 재검증 설계까지만. | 바로 two-phase commit 구현, unbounded retry, global write lock 밖 owner remap. |
| PERF-008 | **CONDITIONAL_CLASSIFICATION** | tracker broadcast 같은 idempotent 후보를 별도 class로 분류할 수 있는지 계측/분류. critical/player action/owner handoff는 non-dropping 유지. | critical task drop, owner handoff coalescing, player action backpressure wait, plugin callback order 변경. |

---

## 1. Executive Summary

Astatine은 Purpur/Paper 계열 patch stack 위에 ShreddedPaper/Folia 스타일의 regionized execution을 강하게 확장한 서버입니다. README와 설계 문서 기준으로, 목표는 단일 프로세스 안에서 여러 loaded region을 독립 tick하면서 명시적 ownership, exact-cell lock, mailbox handoff, async chunk I/O QoS, hidden sync chunk load guard를 유지하는 것입니다. GitHub 상의 저장소는 `MultiPaper/ShreddedPaper` fork로 표시되고, README는 Astatine을 “1.21.11 Purpur fork derived from ShreddedPaper”로 설명합니다. ([GitHub][1])

가장 큰 개선 기회는 다음입니다.

1. **per-region tick마다 반복되는 ownership lock 입력 정렬/객체 churn 제거**
   `RegionTickScheduler.runOneTick`가 owner cell snapshot/isolation snapshot을 이용해 exact-cell lock을 잡고, `ShreddedPaperRegionLocker`는 매 lock 시점에 `ArrayList` 생성, sort, duplicate 제거를 수행합니다. `RegionOwner`도 cell snapshot과 isolation snapshot을 `RegionPos` 객체 리스트로 생성합니다. 이는 20 TPS × region 수 × isolation cell 수에 비례하는 allocation/CPU 비용입니다. ([GitHub][2])

2. **chunk I/O QoS path의 Atomic/CAS/CLQ/단일 retry executor 압력 완화**
   `RegionChunkIoTracker`는 per-region에 다수의 `AtomicInteger`, `AtomicLong`, `AtomicBoolean`, `ConcurrentLinkedQueue`를 두고, backlog/deferred/backpressure drain을 단일 scheduled executor로 처리합니다. Chunk generation/load throughput이 이미 프로젝트 문서상 active risk인 만큼 최우선 측정 대상입니다. ([GitHub][3])

3. **mailbox counter/queue layout 개선**
   `RegionMailbox`는 JCTools `MpscArrayQueue`를 사용하지만, class별 counters와 queues는 `EnumMap<RegionTaskClass, AtomicInteger/AtomicLong/Queue>` 형태이고, critical/transferred path에는 `ConcurrentLinkedQueue`가 있습니다. 이 구조는 ARM Linux에서 volatile/CAS, pointer chasing, false sharing 가능성이 큽니다. ([GitHub][4])

4. **scheduler DelayQueue contention과 lane steal 정책 검증**
   `RegionTickScheduler`는 normal/degraded `DelayQueue`를 두고 region handle을 재enqueue합니다. `DelayQueue`는 내부 lock/condition을 사용하는 전역 우선순위 큐 계열이므로 region 수가 커질수록 contention 가능성이 있습니다. 또한 코드상 normal worker가 degraded queue를 steal하는 조건이 `degradedThreads == 0`일 때로 보이며, 문서의 “normal workers steal degraded only when no normal work”와 차이가 날 수 있습니다. ([GitHub][2])

5. **diagnostics/JFR event allocation gating**
   `RegionChunkIoTracker.commitEvent`와 scheduler tick event는 이벤트 객체를 만들고 문자열 필드를 채웁니다. JFR은 유용하지만 hot pressure path에서는 `shouldCommitSample`, `event.isEnabled()` 계열 guard를 더 앞당겨야 합니다. JDK 25는 JFR CPU-Time Profiling과 Cooperative Sampling 개선이 있어 측정 도구로 적극 활용할 가치가 있습니다. ([GitHub][5])

가장 위험한 병목 5개는 **global/exact-cell locking**, **chunk I/O retry/backpressure path**, **unbounded critical mailbox**, **region scheduler DelayQueue**, **sync compatibility fallback/owner handoff storm**입니다.

---

## 2. Scope / Non-goals

### Scope

* Java 25, Linux, ARM 서버 기준.
* preview 기능 사용 가능. 단, 실제 JDK 25에서 compile/run 가능한 기능만 허용.
* Paper/Purpur/ShreddedPaper/Folia 계열 plugin compatibility 및 tick determinism 유지.
* scheduler, mailbox, ownership, chunk I/O, entity/pathfinding, redstone/block/fluid/piston/explosion, plugin path, diagnostics, build/runtime flags 분석.
* game logic side effect 없는 자료구조, allocation, lock scope, queue, metrics, caching, key packing, backpressure 개선.

### Non-goals

* tick order, block update order, redstone order, entity callback order 변경.
* RNG 호출 순서 변경.
* chunk generation determinism 변경.
* unsafe cross-owner mutation 허용.
* fail-closed ownership guard를 fail-open으로 바꾸는 최적화.
* profiler/benchmark 없는 scheduler/mailbox 대규모 rewrite.
* x86 전용 최적화.
* plugin API compatibility 파괴.

---

## 3. Repository Understanding

Astatine의 build stack은 paperweight patcher 기반입니다. Root build는 JDK toolchain 25, `--enable-preview`, `options.release = 25`를 설정하고, root settings는 `shreddedpaper-api`, `shreddedpaper-server` 두 subproject를 포함합니다. `gradle.properties`는 `mcVersion=1.21.11`, `purpurRef=f57bd865...`, Gradle JVM args로 G1GC와 6G heap을 설정합니다. 서버 build patch는 Purpur server 위에 ShreddedPaper fork를 등록하고, server dependency에 JCTools, zstd-jni, zero-allocation-hashing 등을 추가합니다. ([GitHub][6])

설계 문서 기준 핵심 모델은 다음입니다.

* fixed region cell 기본값은 8 chunks.
* `RegionOwner`가 cell ownership을 들고, `RegionRuntimeState`가 mailbox, chunk IO tracker, overload controller, scheduler state를 가짐.
* independent scheduler는 normal/degraded lanes, no catch-up storm 정책.
* cross-owner work는 mailbox/handoff로 지연.
* `ShreddedPaperAccess`는 block update, redstone, fluid, piston, rails, movement, teleport, spawning, portals, item/vehicle movement, chunk reads, structure/POI에 대한 central ownership guard.
* mailbox는 task class별 capacity/QoS를 갖고, critical task는 non-dropping, plugin task는 fail-fast.
* chunk I/O QoS는 degraded region의 load/generation pressure를 지역적으로 제한하는 방향입니다. ([GitHub][7])

Folia와 비교하면, Folia도 loaded chunks를 independently ticking regions로 나눠 parallel tick하며, 각 region은 사실상 “자기 main thread”에서 tick loop를 실행합니다. Folia API도 region scheduler와 entity scheduler를 분리하고, entity에는 entity scheduler 사용을 요구합니다. Astatine은 이 방향 위에 exact-cell ownership, bounded mailbox, per-region chunk I/O QoS, fail-closed guards를 더 강하게 넣은 구조로 보입니다. ([GitHub][8])

---

## 4. Audit Coverage Table

| Area                 |                                                                                                Files/Packages |             Reviewed? |             Hot Path? | Notes                                                                 |
| -------------------- | ------------------------------------------------------------------------------------------------------------: | --------------------: | --------------------: | --------------------------------------------------------------------- |
| Root docs            |                         `README.md`, `HOW_IT_WORKS.md`, `ASTATINE_YAML.md`, `REGIONIZED_ENGINE_MILESTONES.md` |              Reviewed |                   Yes | Architecture, config, release gates, known risks 확인.                  |
| Build                | `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `shreddedpaper-server/build.gradle.kts.patch` |              Reviewed |         Build/runtime | JDK 25, preview, JCTools/zstd/hash dependency 확인.                     |
| Region scheduler     |                                                                                    `RegionTickScheduler.java` | Reviewed, static only |             Very high | DelayQueue, Atomic/volatile, exact lock 호출, snapshots stream 확인.      |
| Mailbox              |                                               `RegionMailbox.java`, `RegionTaskClass.java`, `RegionTask.java` |               Partial |             Very high | Queue/counter 구조 확인. `RegionTask` 세부 ordering은 부분 확인.                 |
| Runtime state        |                           `RegionRuntimeState.java`, `RegionOverloadController.java`, `RegionTickBudget.java` |               Partial |                  High | CHM state registry, volatile EWMA/load class, metrics fields 확인.      |
| Chunk QoS            |                                                `RegionChunkIoTracker.java`, `RegionChunkExecutorLimiter.java` |      Reviewed/Partial |             Very high | Atomic/CAS/CLQ/single retry executor/emergency executor 확인.           |
| Ownership            |      `RegionOwner.java`, `ShreddedPaperRegionLocker.java`, `ShreddedPaperAccess.java`, `OwnershipIntent.java` |      Reviewed/Partial |             Very high | exact-cell lock, owner snapshots, handoff, hidden sync load guard 확인. |
| Region map           |                             `LevelChunkRegionMap.java`, `LevelChunkRegion.java`, `LevelTicksRegionProxy.java` |               Partial |             Very high | owner map, merge/split, lock, region snapshot path 확인.                |
| Commands/diagnostics |                                                                 `commands`, JFR event classes, `/region` docs |               Partial |                Medium | CLI/diagnostic impact은 일부만 확인.                                        |
| Config               |                                                      `io/multipaper/shreddedpaper/config`, `ASTATINE_YAML.md` |               Partial |            Low/medium | YAML options 확인, parser hot path는 미검토.                                |
| DivineMC/C2ME        |                                                                    `org/bxteam/divinemc/**`, `com/ishland/**` |   Package listed only |        High for chunk | 실제 algorithm code 미검토.                                                |
| Lithium              |                                                                              `net/caffeinemc/mods/lithium/**` |   Package listed only | High for entity/block | 실제 hot code 미검토.                                                      |
| Paper/Purpur patches |                  `paper-patches`, `purpur-patches`, `minecraft-patches/features`, `minecraft-patches/sources` |          Not reviewed |             Very high | redstone/fluid/piston/chunk/NBT/network 대부분 여기에 있을 가능성.               |
| API/plugin           |                                                           `shreddedpaper-api`, Folia-compatible scheduler API |               Partial |           Medium/high | compatibility path는 `ShreddedPaperAccess` 중심만 확인.                     |
| Tools/benchmarks     |                                           `tools/benchmark`, `tools/region-load-test-plugin`, `tools/runtime` |           Listed only |   High for validation | 실제 benchmark code 미검토.                                                |
| Tests                |                                                                    `src/test`, `paper-patches/files/src/test` |          Not reviewed |            Validation | 테스트 coverage 확인 못함.                                                   |

**Coverage conclusion:** 이번 PRD의 P0/P1 finding은 확인된 scheduler/mailbox/ownership/chunk-QoS 코드에 한정합니다. redstone/fluid/piston/explosion/network/NBT 최적화는 반드시 patch stack checkout 후 별도 pass가 필요합니다.

---

## 5. Benchmark Baseline and Measurement Plan

### 현재 확인된 baseline

로컬 빌드와 benchmark 실행은 실패했기 때문에 수치 baseline은 없습니다. 다만 프로젝트 문서가 chunk generation throughput under QoS, teleport/disconnect, End/Nether behavior, scanner regression, watchdog/main-thread blocking을 active risk로 명시합니다. ([GitHub][1])

### ARM Linux testbed

권장 최소 환경:

* ARM Neoverse N2/V2 또는 Graviton3/4 계열.
* 32 vCPU 이상, 64–128 GB RAM.
* NVMe SSD.
* Linux 6.x.
* JDK 25 GA build. OpenJDK 프로젝트는 JDK 25 GA가 2025-09-16이라고 공지했고, JDK 25 프로젝트 페이지는 JFR, Shenandoah, Compact Object Headers 등 JEP 목록을 제공합니다. ([OpenJDK Mail][9])

### JVM flag matrix

기본:

```bash
--enable-preview
-Xms16G -Xmx16G
-XX:+UseG1GC
-XX:+AlwaysPreTouch
-XX:+UnlockDiagnosticVMOptions
-XX:+DebugNonSafepoints
-Xlog:gc*,safepoint:file=logs/gc-%p.log:time,uptime,level,tags
```

비교:

```bash
# ZGC
-XX:+UseZGC

# Shenandoah, JDK 25에서 Generational Shenandoah 확인 필요
-XX:+UseShenandoahGC

# JFR
-XX:StartFlightRecording=filename=profile.jfr,settings=profile,dumponexit=true
```

JDK 25는 Linux에서 더 정확한 JFR CPU-time profiling을 위한 experimental JEP 509와 cooperative sampling JEP 518을 포함합니다. JFR과 async-profiler를 같이 써서 Java/native/kernel frame을 분리해야 합니다. ([OpenJDK][10])

### 필수 시나리오

1. baseline idle.
2. 30 players spread across regions.
3. 100 players spread across regions.
4. one hostile TNT/explosion region + far control region.
5. redstone lag machine region + far control region.
6. villager/pathfinding-heavy region + far control region.
7. chunk generation stress.
8. chunk load-only stress.
9. chunk save/autosave stress.
10. plugin scheduler/task storm.
11. teleport/disconnect churn.
12. End/Nether/Overworld mixed load.
13. cross-region movement and portal case.
14. worst-case mailbox overflow/backpressure.
15. degraded lane saturation.
16. ARM server run with realistic flags.

### 지표

* global TPS, per-region TPS/MSPT, schedule lag.
* mailbox depth, class pressure, enqueue/dequeue latency.
* rejected/deferred task count.
* cross-owner handoff count, ownership fallback count.
* lock wait/hold time, exact-cell lock failure count.
* CAS retry count on mailbox/chunk tracker.
* chunk load/sec, generation/sec, save/sec.
* p50/p95/p99 tick duration.
* allocation MB/s, young/full GC frequency, GC pause p95/p99.
* CPU utilization per worker lane.
* executor queue depth.
* context switches, futex wait.
* cache misses, branch misses, cycles/instructions where ARM perf counter allows.

### Command skeleton

```bash
# JFR
java \
  --enable-preview \
  -XX:StartFlightRecording=filename=astatine.jfr,settings=profile,dumponexit=true \
  -Xlog:gc*,safepoint:file=logs/gc.log:time,level,tags \
  -jar astatine-paperclip.jar nogui

# async-profiler CPU
./profiler.sh -e cpu -d 120 -f flame-cpu.html <pid>

# async-profiler allocation
./profiler.sh -e alloc -d 120 -f flame-alloc.html <pid>

# async-profiler lock/wall if supported
./profiler.sh -e lock -d 120 -f flame-lock.html <pid>

# Linux perf
perf stat -d -d -d -p <pid> -- sleep 120
perf record -F 99 -g -p <pid> -- sleep 120
```

JFR custom event는 allocation 후 field를 채우고 `commit()`하는 방식이므로, high-frequency path에서는 sampling/enable guard를 앞쪽에 두어야 합니다. Oracle JDK 25 JFR API 문서는 `Event` allocation 후 `commit()`으로 기록한다고 설명합니다. ([Oracle Docs][11])

---

## 6. Findings

### PERF-001

**제목:** exact-cell lock 입력 `RegionPos` 리스트 재생성/정렬 제거

**분류:** allocation / lock-contention / ownership / ARM

**코드 위치:**
`shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionTickScheduler.java`
`RegionHandle.runOneTick()` → `internalTryTakeExactLockNow(ownerCells, isolationCells)`
`shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperRegionLocker.java`
`internalTryTakeExactReadOnlyLockNow()`, `sortedUniqueRegions()`
`shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/RegionOwner.java`
`cellPositionsSnapshot()`, `isolationCellPositionsSnapshot()` ([GitHub][2])

**현재 동작:**
Region tick마다 owner cells와 isolation cells snapshot을 가져와 exact-cell lock을 시도합니다. Locker 쪽은 입력 region positions를 `ArrayList`로 복사하고 `RegionPos::toLong` 기준 sort/unique 처리합니다. `RegionOwner`도 cell key를 `RegionPos` 객체 리스트로 변환해 snapshot을 구성합니다.

**왜 비효율인가:**
매 tick, 매 region, 매 lock attempt마다 `ArrayList`, iterator, comparator, `RegionPos` 객체 churn이 발생합니다. 작은 리스트라도 ARM에서 pointer chasing과 allocation은 young GC 및 cache locality에 불리합니다. lock acquisition 직전 비용이므로 tick p95/p99에 직접 반영됩니다.

**게임 로직 부작용 위험:** 낮음. Lock acquire order를 동일하게 유지하면 tick/order semantics 변화 없음.

**개선 제안:**
`RegionOwner`에 `volatile long[] sortedCellKeysSnapshot`, `volatile long[] sortedIsolationRadiusOneKeysSnapshot`을 추가하고, locker에 `internalTryTakeExactLockNow(long[] sortedWriteKeys, long[] sortedIsolationKeys)` overload를 추가합니다. 기존 `RegionPos` API는 compatibility용으로 유지합니다. `long` key 정렬 순서는 기존 `RegionPos.toLong()` sort와 동일해야 합니다.

**Correctness proof sketch:**
기존 deadlock 회피 조건은 “모든 thread가 동일 key ordering으로 lock 획득”입니다. `long[]` ordering이 기존 `RegionPos.toLong()` ordering과 동일하고 duplicate 제거가 동일하면 lock order invariant는 유지됩니다. ownership epoch check와 fail-closed behavior는 변경하지 않습니다.

**예상 성능 이점:**
측정 전 가설. Region 수가 많은 100-player spread에서 lock acquisition allocation 감소, young GC 감소, exact-lock 실패 재시도 CPU 감소. 목표: allocation rate 3–8% 감소, scheduler CPU 1–3% 감소.

**검증 방법:**
JMH: `sortedUniqueRegions(List<RegionPos>)` vs cached `long[]`.
jcstress/race test: concurrent split/merge/owner retirement 중 lock order invariant.
Replay: 30/100 spread, TNT + far control, cross-region portal.

**롤백 기준:**
deadlock, incorrect ownership assertion, changed lock conflict count, p99 MSPT 악화 > 5%.

**우선순위:** P0
**구현 난이도:** M
**신뢰도:** High

---

### PERF-002

**제목:** `ShreddedPaperRegionLocker`의 per-cell `ConcurrentHashMap.compute`와 `CompletableFuture` unlock chain 비용 측정 및 primitive-key fast path 도입

**분류:** lock-contention / allocation / ownership / ARM

**코드 위치:**
`ShreddedPaperRegionLocker.java`
`lockRegion()`, `internalTryTakeExactLockNow()`, `ReadOnlyRegionLock.tryLock()`, `LockedRegion.onUnlock()` ([GitHub][12])

**현재 동작:**
`ConcurrentHashMap<RegionPos, LockedRegion>`와 `StampedLock globalLock`을 사용합니다. Lock 실패 시 `LockSupport.parkNanos()` backoff를 수행합니다. `LockedRegion`은 `CompletableFuture` head/tail unlock chain을 `synchronized onUnlock`으로 연결합니다.

**왜 비효율인가:**
hot tick path에서 CHM compute lambda, `RegionPos` object key, `CompletableFuture` chain, global read lock stamp가 반복됩니다. ARM에서는 CAS, volatile, full/volatile ordering 비용이 x86보다 더 잘 드러납니다. Java VarHandle 문서도 volatile access가 acquire/release보다 더 강한 total ordering을 제공한다고 설명하므로, 불필요한 volatile/CAS 빈도를 줄이는 것이 ARM에서 중요합니다. ([Oracle Docs][13])

**게임 로직 부작용 위험:** 중간. Lock semantics 직접 변경은 위험합니다.

**개선 제안:**
1차 PR은 측정/계측: lock wait nanos, hold nanos, `tryLock` failure count, `parkNanos` count, cells per lock.
2차 PR은 `long` packed key 기반 fast path: `RegionPos` object key를 `long`으로 치환 가능한 내부 map을 검토합니다. 단, CHM을 fastutil map으로 무조건 바꾸면 concurrency semantics가 바뀌므로 금지입니다.
3차 PR은 `CompletableFuture` chain 대신 small MPSC callback queue를 실험하되, unlock continuation ordering test 통과 전 merge 금지.

**Correctness proof sketch:**
key representation 변경은 key equality/order만 바꾸며 ownership policy는 유지합니다. unlock continuation은 기존 FIFO happens-before를 보장해야 하며, 동일 cell에 대기한 continuation의 실행 순서가 바뀌면 안 됩니다.

**예상 성능 이점:**
측정 전 가설. Lock-heavy TNT/redstone/cross-region movement에서 p99 lock wait와 allocation 감소.

**검증 방법:**
async-profiler lock + allocation, JFR custom events, jcstress, randomized split/merge stress, plugin scheduler storm.

**롤백 기준:**
deadlock, continuation reorder, owner guard false negative/positive, lock wait p99 악화.

**우선순위:** P0 측정, P1 fast path
**구현 난이도:** L
**신뢰도:** Medium

---

### PERF-003

**제목:** `RegionOwner` radius-1 isolation snapshot을 sorted packed key로 캐시

**분류:** allocation / data-structure / ownership

**코드 위치:**
`RegionOwner.java`
`cellPositionsSnapshot()`, `isolationCellPositionsSnapshot(int radius)` ([GitHub][14])

**현재 동작:**
`LongOpenHashSet` cells를 순회하며 `RegionPos` 객체 리스트를 만들고, radius-1 isolation snapshot은 주변 cell을 `LongOpenHashSet`에 추가한 뒤 `RegionPos` 리스트로 변환합니다. radius-1 snapshot은 캐시되지만 sorted packed primitive 배열은 없습니다.

**왜 비효율인가:**
tick lock path에서 이미 stable owner cells임에도 `RegionPos` 객체 리스트와 sort가 반복될 수 있습니다. `LongOpenHashSet` 자체는 좋은 선택이지만 lock 입력에는 boxed/object view가 불리합니다.

**게임 로직 부작용 위험:** 낮음.

**개선 제안:**
`RegionOwner` 내부 canonical representation은 `long` cell key로 유지하고, public compatibility method는 lazy 변환합니다. `absorbCellsFrom`, `replaceCells` 후 모든 snapshots를 invalidate합니다.

**예상 성능 이점:**
PERF-001과 결합 시 allocation 감소.

**검증 방법:**
snapshot invalidation unit test, merge/split replay, exact lock ordering property test.

**롤백 기준:**
stale snapshot, missing isolation cell, lock invariant failure.

**우선순위:** P0
**구현 난이도:** S/M
**신뢰도:** High

---

### PERF-004

**제목:** `RegionChunkIoTracker`의 다수 Atomic 필드와 false sharing 가능성 완화

**분류:** chunk / ARM / CAS / GC / diagnostics

**코드 위치:**
`RegionChunkIoTracker.java`
fields `inFlight`, `deferredRetries`, `admitted`, `completed`, `executorInFlight`, `executorWaitingTasks`, `executorDeferredRetries`, `executorOverflowInFlight`, backlog counters, drain flags; methods `tryAcquire()`, `tryAcquireExecutor()`, `deferExecutorRetry()`, `tryBeginExecutorWaiting()` ([GitHub][5])

**현재 동작:**
Per region tracker가 여러 `AtomicInteger`, `AtomicLong`, `AtomicBoolean`을 인접 object fields로 들고 있습니다. Admission마다 `incrementAndGet`, `decrementAndGet`, `compareAndSet` loop, `ConcurrentLinkedQueue.offer`가 수행됩니다.

**왜 비효율인가:**
chunk load/generation은 Astatine의 active risk입니다. Atomic object가 많으면 cache line bouncing, false sharing, allocation/object header overhead가 늘어납니다. ARM acquire/release/volatile ordering은 정확성에는 필수지만 hot counter에 과하면 throughput을 제한합니다.

**게임 로직 부작용 위험:** 낮음~중간. Counter layout 변경 자체는 낮음, admission state machine 변경은 중간.

**개선 제안:**

* `AdmissionCounters` value object를 만들고 hot counters를 grouped/padded 합니다.
* admission-critical `inFlight`, `deferredRetries`, `executorInFlight`, `executorWaitingTasks`는 유지하되 padding 또는 `@jdk.internal.vm.annotation.Contended` 실험을 추가합니다. `@Contended` 사용 시 `--add-exports`/`-XX:-RestrictContended` 필요 여부를 확인해야 하므로 production 기본값으로는 manual padding이 더 안전합니다.
* Long-lived metrics counters는 `LongAdder` 또는 region-local plain long + owner-thread publish로 분리합니다. 단, admission gate에 쓰이는 counters는 `LongAdder`로 바꾸면 정확한 cap semantics가 깨질 수 있으므로 금지입니다.
* `ChunkRequestEvent.shouldCommitSample` guard를 가장 앞쪽으로 이동해 event object/string construction을 최소화합니다.

**예상 성능 이점:**
가설. Chunk generation/load stress에서 CAS retry와 GC allocation 감소. 목표: chunk/sec 5–15% 개선 또는 p99 deferred latency 10% 감소.

**검증 방법:**
JMH admission microbench, async-profiler alloc/cpu, perf stat cache-misses/context-switches, scenario 7/8/14/15.

**롤백 기준:**
cap 초과 admission, starvation, world corruption, chunk load p99 악화.

**우선순위:** P0
**구현 난이도:** M
**신뢰도:** High for measurement, Medium for gain

---

### PERF-005

**제목:** `RegionChunkIoTracker` 단일 `EXECUTOR_BACKPRESSURE_RETRY_EXECUTOR` 병목 제거 또는 shard화

**분류:** chunk / scheduler / queue / backpressure

**코드 위치:**
`RegionChunkIoTracker.java`
`EXECUTOR_BACKPRESSURE_RETRY_EXECUTOR`, `scheduleExecutorBacklogDrain()`, `drainExecutorBacklogWaiters()`, `scheduleExecutorDeferredDrain()`, `drainExecutorDeferredWaiters()`, `scheduleExecutorBackpressureDrain()` ([GitHub][5])

**현재 동작:**
모든 region의 executor backlog/deferred/backpressure retry drain이 static single-thread scheduled executor를 공유합니다. Drain batch는 64입니다.

**왜 비효율인가:**
여러 region이 동시에 chunk generation/load stress에 빠지면, retry admission 자체가 단일 thread에 직렬화됩니다. 이는 “degraded region을 지역적으로 격리”하려는 목표와 충돌할 수 있습니다. 단일 retry executor는 context switch/futex/queue depth 병목이 될 수 있습니다.

**게임 로직 부작용 위험:** 중간. Retry order와 admission fairness가 바뀔 수 있습니다.

**개선 제안:**

* 1차: static executor queue depth, per-region queued waiters, drain latency metric 추가.
* 2차: world 또는 scheduler lane 단위 shard executor로 분할.
* 3차: region worker가 자기 tick 끝 auxiliary budget 안에서 자기 region retry drain을 수행하도록 실험. 단, core tick order에는 넣지 말고 auxiliary-only 원칙 유지.
* Backpressure semantics: task drop 금지, cap 초과 admission 금지, same-region FIFO retry 가능하면 유지.

**예상 성능 이점:**
chunk stress와 degraded saturation에서 p95/p99 retry latency 감소. 근거 수준: 코드 구조 기반 가설.

**검증 방법:**
scenario 7/8/14/15, perf context-switch/futex, JFR executor queue latency, crash recovery save/load replay.

**롤백 기준:**
retry starvation, cap violation, chunk task lost, chunk future hang.

**우선순위:** P0 measurement, P1 shard
**구현 난이도:** M/L
**신뢰도:** Medium

---

### PERF-006

**제목:** `RegionChunkExecutorLimiter.PermitTask` atomic state explosion 축소

**분류:** chunk / allocation / CAS / IO

**코드 위치:**
`RegionChunkExecutorLimiter.java`
`PermitTask` fields and retry paths; static `EMERGENCY_EXECUTOR`, `EMERGENCY_RETRY_EXECUTOR` ([GitHub][15])

**현재 동작:**
Chunk executor task wrapper가 여러 `AtomicBoolean`, `AtomicInteger`, volatile fields, `synchronized(deferredLock)`, retry runnable을 갖습니다. Emergency executor는 global `ThreadPoolExecutor`와 `ArrayBlockingQueue(8192)`를 사용합니다.

**왜 비효율인가:**
chunk work item마다 atomic object 수가 많고, retry/backpressure 상태 전환이 복잡합니다. Chunk generation throughput under QoS가 약점이면 task wrapper overhead가 직접 병목일 수 있습니다.

**게임 로직 부작용 위험:** 중간. Permit acquire/release 정확성이 world corruption 방지와 직결됩니다.

**개선 제안:**

* 여러 boolean state를 하나의 bit-packed `AtomicInteger state`로 통합합니다.
* retry runnable은 lazy single allocation 또는 static inner class로 재사용합니다.
* emergency executor는 per-world/per-lane shard를 검토하되, crash-safe save/load path에는 적용 전 soak test 필수.
* `synchronized(deferredLock)` 구간을 줄이고 owner tracker lookup을 캐시합니다.

**예상 성능 이점:**
chunk/sec 5–20% 가설. allocation 감소는 측정 가능해야 합니다.

**검증 방법:**
JMH PermitTask state transition, stress with chunk gen/load/save, duplicate permit release detector, cap invariant property test.

**롤백 기준:**
negative in-flight counter, permit leak, executor hang, chunk future not completed.

**우선순위:** P1
**구현 난이도:** L
**신뢰도:** Medium

---

### PERF-007

**제목:** `RegionMailbox` EnumMap/Atomic counter layout을 ordinal array + padded counters로 변경

**분류:** mailbox / data-structure / ARM / allocation

**코드 위치:**
`RegionMailbox.java`
fields `EnumMap<RegionTaskClass, Queue<RegionTask>> ingress`, `queuedByClass`, `offeredByClass`, `rejectedByClass`; methods `offer()`, `reserveSlot()`, `runDue()`, `depth()`, `maxClassPressure()` ([GitHub][4])

**현재 동작:**
Task class별 queue/capacity/counter를 `EnumMap`으로 조회하고, queued counters는 `AtomicInteger`, offered/rejected는 `AtomicLong`입니다. Non-critical queue는 `MpscArrayQueue`, critical/transferred는 `ConcurrentLinkedQueue`입니다.

**왜 비효율인가:**
EnumMap 조회, Atomic object indirection, class별 counter false sharing 가능성이 있습니다. Mailbox는 모든 cross-owner handoff/plugin/chunk IO/tracker broadcast의 중심 hot path입니다.

**게임 로직 부작용 위험:** 낮음~중간. Queue semantics 유지 시 낮음.

**개선 제안:**

* `RegionTaskClass.ordinal()` index 기반 arrays: `Queue<?>[] queues`, `int[] capacities`, `PaddedAtomicInt[] queued`, `PaddedAtomicLong[] offered`.
* `depth()`와 `maxClassPressure()`는 diagnostic read path이므로 write hot path counters를 방해하지 않게 snapshot 주기를 둡니다.
* Critical queue는 unbounded semantics 때문에 그대로 두되, high-water metrics와 memory pressure alarm 추가.

**Correctness proof sketch:**
class ordering은 기존 `RegionTaskClass.values()` 순서와 동일해야 합니다. `runDue()`의 class drain order와 per-class FIFO/MPSC semantics는 변경하지 않습니다. Overflow/reject policy도 그대로 유지합니다.

**예상 성능 이점:**
mailbox enqueue/dequeue CPU 2–8% 감소 가설. ARM에서 CAS cache-line bouncing 감소.

**검증 방법:**
JMH MPSC mailbox offer/drain, multi-producer stress, task class order replay, plugin storm.

**롤백 기준:**
task reorder, reject count mismatch, class capacity violation.

**우선순위:** P1
**구현 난이도:** M
**신뢰도:** Medium/High

---

### PERF-008

**제목:** Critical/transferred `ConcurrentLinkedQueue` unbounded growth에 high-water backpressure와 diagnostics 추가

**분류:** mailbox / memory / GC / backpressure

**코드 위치:**
`RegionMailbox.java`
`transferred = new ConcurrentLinkedQueue<>()`, critical class queue, `offerTransferred()`, `offerNonDropping()`, `runDue()` ([GitHub][4])

**현재 동작:**
Critical task는 non-dropping이고 CLQ 기반입니다. Transferred queue도 CLQ입니다. Drain은 transferred 32, delayed 64, class별 quanta로 제한됩니다.

**왜 비효율인가:**
정상 설계상 critical non-dropping은 필요하지만, hostile plugin/cross-region storm에서 CLQ node allocation과 unbounded memory growth가 GC pressure/OOM을 유발할 수 있습니다.

**게임 로직 부작용 위험:** 중간. Critical task drop은 금지입니다.

**개선 제안:**

* Drop 없이 high-water admission telemetry: critical depth, transferred depth, oldest task age, owner epoch mismatch count.
* Producer가 engine-owned safe context일 때만 backpressure wait/defer를 허용하고, plugin path는 fail-fast 유지.
* Critical task 중 idempotent tracker/broadcast류는 별도 task class로 분리해 coalescing 가능성 검토. PLAYER_ACTION/OWNER_HANDOFF/CHUNK_IO는 non-coalescing 유지.

**예상 성능 이점:**
정상 상태 성능보다는 tail latency/OOM 방어. plugin storm에서 allocation spike 감소.

**검증 방법:**
scenario 10/14, mailbox overflow property test, no-drop invariant.

**롤백 기준:**
critical task lost, plugin callback order change, handoff starvation.

**우선순위:** P1
**구현 난이도:** M
**신뢰도:** Medium

---

### PERF-009

**제목:** `RegionMailbox` delayed `PriorityQueue`를 tick bucket/timing wheel로 실험

**분류:** mailbox / algorithm / scheduler

**코드 위치:**
`RegionMailbox.java`
`PriorityQueue delayed`, `offerTransferred`, `offerNonDropping`, `runDue()` ([GitHub][4])

**현재 동작:**
Delayed task는 `PriorityQueue`에 들어가고, delay는 최소 1 tick으로 normalize됩니다. Consumer가 due tasks를 drain합니다.

**왜 비효율인가:**
Per-region delayed task가 많아지면 O(log n) enqueue/dequeue와 object comparator 비용이 생깁니다. Minecraft scheduler delay는 대부분 짧은 tick delay이므로 hashed timing wheel이나 readyTick bucket이 더 cache-friendly일 수 있습니다.

**게임 로직 부작용 위험:** 중간. 동일 readyTick 내 ordering이 중요할 수 있습니다.

**개선 제안:**

* `readyTick -> MPSC/SPSC bucket` ring buffer를 실험합니다.
* 동일 readyTick에서는 insertion sequence를 보존합니다.
* Long-delay task는 fallback min-heap 유지.

**예상 성능 이점:**
plugin scheduler storm에서 mailbox CPU 감소. 근거 수준: 이론/가설.

**검증 방법:**
Bukkit/Folia scheduler order replay, delayed task storm JMH, plugin callback order test.

**롤백 기준:**
same-tick ordering mismatch, delayed task early/late execution beyond existing semantics.

**우선순위:** P2
**구현 난이도:** L
**신뢰도:** Low/Medium

---

### PERF-010

**제목:** `RegionTickScheduler` `DelayQueue` global contention과 lane steal 정책 재검토

**분류:** scheduler / lock-contention / algorithm

**코드 위치:**
`RegionTickScheduler.java`
fields `DelayQueue normalQueue`, `DelayQueue degradedQueue`; worker loop, `takeNormalOrSteal()`, `enqueue()`, `RegionHandle.compareTo()` ([GitHub][2])

**현재 동작:**
Region handle이 scheduled start nanos 기준으로 `DelayQueue`에 들어갑니다. Normal/degraded lane 분리. 코드상 normal worker가 degraded queue를 steal하는 조건이 `normalWorkersMayStealDegraded = degradedThreads == 0`로 보입니다.

**왜 비효율인가:**
`DelayQueue`는 내부적으로 lock/condition 기반 global priority queue입니다. Region 수가 많을 때 enqueue/take contention이 생길 수 있습니다. 또한 degraded threads가 존재하면 normal idle worker가 degraded backlog를 전혀 돕지 못할 가능성이 있어 CPU utilization imbalance가 생길 수 있습니다.

**게임 로직 부작용 위험:** 중간/높음. Tick cadence/fairness에 영향.

**개선 제안:**

* 1차: queue wait time, wakeup latency, lane utilization, degraded backlog age metrics.
* 2차: configurable idle steal: normal worker가 normal queue에 ready work가 없고 degraded backlog age가 threshold 이상일 때만 steal.
* 3차: per-worker local deadline wheel + global admission queue 실험. `DelayQueue`를 바로 제거하지 말고 feature flag로 비교합니다.

**Correctness proof sketch:**
Region별 tick period와 no catch-up storm은 유지합니다. Steal은 “degraded lane task를 더 이르게 실행”할 수 있으므로 earliest deadline보다 빠르게 실행하지 않도록 `getDelay()` ready check를 유지해야 합니다.

**예상 성능 이점:**
degraded lane saturation에서 CPU utilization 및 far control region isolation 개선. 근거 수준: 코드 기반 가설.

**검증 방법:**
scenario 15, `perf sched`, JFR thread CPU, lane queue depth/age histograms.

**롤백 기준:**
normal lane p99 악화, degraded region이 normal region을 오염, tick interval invariant failure.

**우선순위:** P1 measurement, P2 algorithm
**구현 난이도:** L/XL
**신뢰도:** Medium

---

### PERF-011

**제목:** `RegionTickScheduler.snapshots()` stream/sort allocation을 diagnostics-only 최적화

**분류:** diagnostics / allocation

**코드 위치:**
`RegionTickScheduler.java`
`snapshots()` ([GitHub][2])

**현재 동작:**
`regions.values().stream().filter().map().sorted().toList()`로 region snapshot을 만듭니다.

**왜 비효율인가:**
`/region top`, TPS bar, frequent diagnostics에서 전체 region list allocation/sort가 반복될 수 있습니다.

**게임 로직 부작용 위험:** 없음.

**개선 제안:**

* Pre-sized `ArrayList` + explicit loop.
* Top-N command에는 full sort 대신 bounded heap/top-K.
* Diagnostics sampling interval config.

**예상 성능 이점:**
운영 중 diagnostics 사용 시 allocation 감소.

**검증 방법:**
JMH snapshots with 1k/10k regions, command spam test.

**롤백 기준:**
diagnostic output ordering mismatch.

**우선순위:** P2
**구현 난이도:** S
**신뢰도:** High

---

### PERF-012

**제목:** `ShreddedPaperAccess` loaded-read fallback stack trace sampling 비용 축소

**분류:** diagnostics / ownership / allocation

**코드 위치:**
`ShreddedPaperAccess.java`
`recordLoadedReadFallbackSample()`, `firstRelevantCaller()`, `runOrDeferToOwner()` ([GitHub][16])

**현재 동작:**
Loaded read fallback sample 기록 시 global `ArrayDeque`에 synchronized로 접근하고, caller sampling은 `Thread.currentThread().getStackTrace()`, `ArrayList`, `String.join`을 사용합니다.

**왜 비효율인가:**
정상 상태에서는 드물어야 하지만, ownership guard 실패 storm에서는 tick thread에서 stack trace allocation과 global synchronized가 증폭됩니다.

**게임 로직 부작용 위험:** 없음. Diagnostics-only.

**개선 제안:**

* fallback sampling rate와 debug flag를 추가합니다.
* ring buffer는 lock-free MPSC 또는 `AtomicInteger` index + fixed array로 변경합니다.
* Stack trace는 `StackWalker` lazy path 또는 JFR enabled 상태에서만 수집합니다.

**예상 성능 이점:**
비정상 storm에서 GC/lock pressure 감소.

**검증 방법:**
forced fallback storm, JFR allocation, command output snapshot correctness.

**롤백 기준:**
diagnostic sample missing beyond configured sampling, ownership guard behavior change.

**우선순위:** P1
**구현 난이도:** S/M
**신뢰도:** High

---

### PERF-013

**제목:** Owner handoff lambda/requeue object churn을 typed task로 축소

**분류:** ownership / mailbox / allocation

**코드 위치:**
`ShreddedPaperAccess.java`
`runOrDeferToOwner()`, `runWithWriteLockIfHeldOrDeferToOwner()`, `prefetchFullChunkThenOwner()` ([GitHub][16])

**현재 동작:**
Cross-owner work는 lambda로 scheduler에 전달되고, ownership이 바뀌면 최대 8회 requeue 후 예외를 던집니다. Prefetch path는 `CompletableFuture.handle` 뒤 owner handoff를 수행합니다.

**왜 비효율인가:**
Portal/movement/entity/chunk edge case에서 handoff lambda와 future continuation allocation이 누적될 수 있습니다. Handoff storm은 mailbox pressure를 직접 올립니다.

**게임 로직 부작용 위험:** 중간. Handoff ordering 유지 필요.

**개선 제안:**

* `OwnerHandoffTask` record/class로 affinity, ownerId/epoch, requeueCount, `RegionTaskClass`를 명시합니다.
* Same owner/epoch mismatch metrics를 추가합니다.
* Coalescing은 idempotent task만 허용하고, gameplay mutation task는 금지합니다.

**예상 성능 이점:**
cross-region movement/portal에서 allocation 감소와 mailbox pressure 감소. 근거 수준: 가설.

**검증 방법:**
portal churn, teleport/disconnect, entity movement replay, task order logging.

**롤백 기준:**
callback order mismatch, mutation on wrong owner, handoff lost.

**우선순위:** P1
**구현 난이도:** M
**신뢰도:** Medium

---

### PERF-014

**제목:** `LevelChunkRegionMap` merge/split global write lock 구간 축소

**분류:** ownership / lock-contention / algorithm

**코드 위치:**
`LevelChunkRegionMap.java`
`mergeOwnersQuiescent()`, `mergeNearbyOwnersQuiescent()`, `findNearbyMergeCandidate()`, `splitDisconnectedOwnerQuiescent()`, `connectedComponents()` ([GitHub][17])

**현재 동작:**
Owner merge/split는 `regionsLock.write` 안에서 candidate 탐색, exact lock, owner absorption/remap, state detach를 수행합니다. Split은 connected components를 계산합니다.

**왜 비효율인가:**
많은 active regions에서 merge probe가 global region map write lock을 오래 잡으면 owner lookup, scheduling, entity region update가 지연될 수 있습니다.

**게임 로직 부작용 위험:** 높음. Ownership remap correctness가 핵심입니다.

**개선 제안:**

* 1차: write lock hold time JFR event 추가.
* 2차: candidate 탐색은 read snapshot으로 수행, exact lock은 밖에서 시도, write lock 안에서는 version/epoch 재검증 후 commit하는 two-phase protocol 실험.
* Split은 cooldown/max-one-split 정책 유지.

**Correctness proof sketch:**
Commit 시점에 source/target owner id, epoch, cells snapshot version이 탐색 시점과 동일해야 합니다. 아니면 abort/retry. Remap은 global write lock 안에서 atomic하게 수행합니다.

**예상 성능 이점:**
Large spread + movement에서 lock wait p99 감소. 근거 수준: 가설.

**검증 방법:**
randomized merge/split stress, owner map invariant checker, entity/chunk ownership replay.

**롤백 기준:**
lost cell, duplicate owner mapping, stale runtime state, region tick on detached owner.

**우선순위:** P1 measurement, P2 implementation
**구현 난이도:** XL
**신뢰도:** Medium

---

### PERF-015

**제목:** `RegionOwner.absorbCellsFrom` nested synchronization lock ordering assertion

**분류:** correctness / lock-contention

**코드 위치:**
`RegionOwner.java`
`absorbCellsFrom()` ([GitHub][14])

**현재 동작:**
`this.cells`와 `source.cells`를 nested synchronized로 잠급니다. 현재 호출자가 global write lock/exact owner lock을 갖는다면 안전할 수 있으나, method 자체는 deterministic lock ordering을 강제하지 않습니다.

**왜 비효율/위험인가:**
향후 caller가 늘면 inverse ordering deadlock 위험이 생깁니다. 지금은 성능보다 correctness guard입니다.

**게임 로직 부작용 위험:** 없음.

**개선 제안:**
owner id 기준 lock ordering 또는 assert: `Thread.holdsLock`/caller guard를 테스트 전용으로 추가합니다. Production에서는 deterministic order로 synchronized 진입합니다.

**예상 성능 이점:**
직접 성능 이점은 낮음. Deadlock risk 감소.

**검증 방법:**
concurrent merge fuzz, deadlock detector.

**롤백 기준:**
merge throughput 악화, assertion false positive.

**우선순위:** P1
**구현 난이도:** S
**신뢰도:** Medium

---

### PERF-016

**제목:** `RegionOverloadController` volatile metrics write/read 분리

**분류:** diagnostics / ARM / scheduler

**코드 위치:**
`RegionOverloadController.java`
volatile fields `loadClass`, `ewmaMspt`, `ewmaScheduleLagMs`, `lastMailboxDepth`, `lastChunkIoPressure`, etc.; `recordTick()` ([GitHub][18])

**현재 동작:**
매 tick마다 여러 volatile metrics field가 갱신됩니다. `loadClass`도 volatile입니다.

**왜 비효율인가:**
Metrics field는 diagnostics read용인 경우가 많고, `loadClass`만 scheduler/chunk admission에 필요합니다. ARM에서 volatile writes는 ordering 비용이 있습니다.

**게임 로직 부작용 위험:** 낮음. 단, `loadClass` visibility는 유지해야 합니다.

**개선 제안:**

* `loadClass`는 volatile 유지.
* 나머지 metrics는 immutable `Snapshot` object를 owner tick 끝에 publish하거나, plain fields + periodic volatile snapshot으로 분리합니다.
* Diagnostics가 stale metrics를 수 ms 보는 것은 허용 가능해야 하며 config에 명시합니다.

**예상 성능 이점:**
매 tick volatile write 감소. 목표: scheduler/tick CPU 미세 감소.

**검증 방법:**
JMH recordTick, ARM perf, diagnostics freshness test.

**롤백 기준:**
load class stale로 degraded/quarantine 반응 지연, diagnostics mismatch.

**우선순위:** P2
**구현 난이도:** S/M
**신뢰도:** Medium

---

### PERF-017

**제목:** Chunk event/JFR event 문자열 action 생성 비용 축소

**분류:** diagnostics / chunk / allocation

**코드 위치:**
`RegionChunkIoTracker.java`
`tryAcquireExecutor()` uses `"executor-" + workType + "-admitted"`; `commitEvent()`, `commitExecutorEvent()` ([GitHub][5])

**현재 동작:**
Sampling guard가 일부 있긴 하지만, action string concatenation이 event path 근처에서 반복됩니다.

**왜 비효율인가:**
Chunk pressure가 높을 때 diagnostic event path가 allocation을 추가할 수 있습니다. JFR event는 강력한 도구지만 high-frequency path에서는 enabled/sample guard 이후에만 문자열을 만들어야 합니다.

**게임 로직 부작용 위험:** 없음.

**개선 제안:**

* `workType` enum 또는 interned constant action table.
* `ChunkRequestEvent.shouldCommitSample(count)`와 JFR enabled check를 string construction 앞에 배치.
* event action은 enum ordinal + JFR label로 후처리하는 구조 검토.

**예상 성능 이점:**
chunk stress diagnostics enabled 시 allocation 감소.

**검증 방법:**
alloc profiler, event count parity test.

**롤백 기준:**
JFR event action 이름 누락/변경으로 tooling break.

**우선순위:** P1
**구현 난이도:** S
**신뢰도:** High

---

### PERF-018

**제목:** `RegionRuntimeState.STATES` lifecycle leak 검사

**분류:** memory / ownership / GC

**코드 위치:**
`RegionRuntimeState.java`
static `ConcurrentHashMap STATES`, `getOrCreate()`, `removeIfIdle()` ([GitHub][19])

**현재 동작:**
Runtime state는 `(UUID worldId, ownerId)` key로 static map에 저장되고 idle일 때 제거됩니다.

**왜 비효율/위험인가:**
Mailbox/chunk tracker pending work가 남거나 owner detach/split edge case에서 idle 판정이 실패하면 state leak이 발생할 수 있습니다.

**게임 로직 부작용 위험:** 낮음.

**개선 제안:**

* owner retire/detach 시 state removal reason metric.
* `/region dump`에 orphan runtime states count.
* stress test 후 `STATES.size()`와 active owner count parity check.

**예상 성능 이점:**
장시간 운영 heap 안정성.

**검증 방법:**
teleport/disconnect churn, split/merge fuzz, heap dump dominator.

**롤백 기준:**
state prematurely removed while mailbox/chunk work pending.

**우선순위:** P1
**구현 난이도:** S
**신뢰도:** Medium

---

### PERF-019

**제목:** Scheduler lifecycle guard: independent ticking disabled 시 scheduler 시작 방지 확인

**분류:** scheduler / config / correctness

**코드 위치:**
`RegionTickScheduler.java` `get()`; milestone TODO mentions lifecycle guard; config `independent-region-ticking` ([GitHub][2])

**현재 동작:**
문서상 “independent ticking disabled never starts scheduler”가 release gate/TODO로 보입니다. `RegionTickScheduler.get()` 코드에서는 config guard가 명확히 보이지 않았습니다.

**왜 비효율/위험인가:**
비활성 모드에서도 worker threads/queues가 시작되면 compatibility mode에서 불필요한 thread/queue overhead와 semantic risk가 있습니다.

**게임 로직 부작용 위험:** 낮음. Guard 추가는 expected config behavior.

**개선 제안:**
`get()` 또는 caller에서 `independentRegionTicking` false면 scheduler 생성 금지. 이미 생성된 scheduler는 config reload 시 lifecycle을 명확히 문서화합니다.

**예상 성능 이점:**
비활성 모드 overhead 제거.

**검증 방법:**
config false boot test, thread dump, no region worker assertion.

**롤백 기준:**
independent ticking true에서 scheduler not started.

**우선순위:** P1
**구현 난이도:** S
**신뢰도:** Medium

---

### PERF-020

**제목:** Redstone/fluid/piston/explosion patch stack 전수 재감사 필요

**분류:** redstone / block / fluid / explosion / unreviewed

**코드 위치:**
`shreddedpaper-server/minecraft-patches/features`, `minecraft-patches/sources`, `paper-patches/files/src/main/java`, `purpur-patches/files/src/main/java` — 이번 세션에서 미검토.

**현재 동작:**
문서상 `ShreddedPaperAccess`가 redstone, fluids, pistons, rails 등 ownership guard에 쓰입니다. 그러나 실제 redstone/fluid/piston/explosion propagation queue는 열람하지 못했습니다. ([GitHub][7])

**왜 비효율인가:**
Minecraft에서 block update/redstone/fluid/piston/explosion은 order-sensitive hot path입니다. Astatine의 cross-region deferral과 batching 가능성은 가장 큰 성능 기회이자 correctness risk입니다.

**게임 로직 부작용 위험:** 높음.

**개선 제안:**
별도 audit pass에서 다음 grep을 전체 patch output에 실행합니다.

```bash
rg -n "neighbor|redstone|fluid|piston|explosion|TNT|ScheduledTick|BlockEvent" .
rg -n "ShreddedPaperAccess|runOrDeferToOwner|assertNoRegionWorkerSyncLoad" .
rg -n "new BlockPos|new ChunkPos|stream\\(|Optional|HashMap|ArrayList" .
```

**예상 성능 이점:**
미측정. Finding이 아니라 mandatory follow-up.

**검증 방법:**
redstone canonical contraptions, piston update-order tests, TNT blast replay, water/lava spread replay.

**롤백 기준:**
any block update order mismatch.

**우선순위:** P0 audit
**구현 난이도:** L
**신뢰도:** High as gap

---

## 7. Optimization Roadmap

이 섹션은 위의 Codex Review 판정으로 재정렬한 실행 로드맵입니다. 원본 초안에 있던 timing wheel, typed owner handoff rewrite, two-phase merge/split, scheduler idle-steal은 safe track에서 제외하지만, 아이디어 자체는 폐기하지 않고 별도 experimental track으로 이동합니다.

### Milestone 1: Diagnostic/allocation gates

**목표:** gameplay semantics를 전혀 건드리지 않고 JFR/diagnostic allocation을 줄인다.
**포함 finding:** PERF-011, PERF-012, PERF-017, PERF-018, PERF-019.
**성공 지표:** allocation MB/s 감소, no ownership invariant regression, p95 MSPT 개선 또는 동일.
**회귀 테스트:** diagnostic output parity, event count parity, config false boot, fallback diagnostics sample test.
**위험:** 낮음.

### Milestone 2: Ownership lock input cleanup

**목표:** exact-cell lock 입력의 반복 정렬/복사를 제거하되 lock map/concurrency semantics는 유지한다.
**포함 finding:** PERF-001, PERF-003, PERF-015.
**성공 지표:** lock acquisition allocation 감소, owner snapshot parity 유지, no deadlock.
**회귀 테스트:** exact-lock property test, owner split/merge replay, radius-1 isolation parity.
**위험:** 낮음~중간.

### Milestone 3: Measurement-only gates for risky areas

**목표:** high-risk rewrite 전에 lock, scheduler, chunk retry, owner handoff, merge/split 병목을 수치로 확정한다.
**포함 finding:** PERF-002, PERF-005, PERF-010, PERF-013, PERF-014, PERF-020.
**성공 지표:** disabled metrics overhead negligible, profiles identify next bottleneck, no behavior change.
**회귀 테스트:** JFR disabled overhead check, hot End region + far control profile, redstone/fluid/piston fixture inventory.
**위험:** 낮음. 단, 이 milestone은 구현 최적화가 아니라 계측입니다.

### Milestone 4: Chunk I/O low-risk cleanup

**목표:** chunk admission caps를 바꾸지 않고 event/string allocation과 counter layout 비용을 줄인다.
**포함 finding:** PERF-004, PERF-017. PERF-006은 leak detector/event guard만 허용.
**성공 지표:** chunk stress allocation 감소, cap invariant 유지, chunk future hang 없음.
**회귀 테스트:** chunk gen/load stress, admission cap invariant, crash recovery save/load replay.
**위험:** 중간.

### Milestone 5: Mailbox representation cleanup

**목표:** mailbox class lookup/counter indirection을 줄이되 queue 종류, drain order, capacity semantics는 유지한다.
**포함 finding:** PERF-007, PERF-008.
**성공 지표:** mailbox enqueue/dequeue latency 감소, reject/drop semantics 동일, no task reorder.
**회귀 테스트:** multi-producer stress, plugin scheduler order replay, class capacity invariant, critical no-drop invariant.
**위험:** 중간.

### Milestone 6: ARM Linux production validation

**목표:** ARM-specific CAS/volatile/cache/fence 비용과 GC 선택 최종 검증.
**포함:** Safe PRD 전체.
**성공 지표:** ARM server에서 x86 대비 anomaly 없음, GC pause p99 안정, CPU utilization 균형.
**도구:** JFR, async-profiler, perf, GC logs.

### Milestone 7: Feature-flagged experimental track

**목표:** safe track에서 제외된 구조적 병목 후보를 통제 실험으로 유지한다.
**포함:** PERF-005 shard retry, PERF-009 delayed bucket, PERF-010 gated idle steal, PERF-013 metadata wrapper, PERF-014 two-phase feasibility, PERF-008 conditional task classification.
**성공 지표:** feature flag off overhead negligible, golden ordering tests pass, rollback thresholds documented.
**회귀 테스트:** same-readyTick replay, scheduler due-time invariant, chunk cap/future invariant, plugin callback order replay, hot region + far control profile.
**위험:** 중간~높음. 이 milestone의 결과는 release PR이 아니라 후속 PRD/실험 branch의 입력입니다.

---

## 8. Proposed External Libraries

| Library             | Use Case                                                        |                       Expected Benefit | License                         | ARM/Linux Suitability                     | Risk                                     | Recommendation                                                                                                                                                                                        |
| ------------------- | --------------------------------------------------------------- | -------------------------------------: | ------------------------------- | ----------------------------------------- | ---------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| JCTools             | MPSC/MPMC queues, bounded mailbox                               |                       High for mailbox | Apache 2.0                      | Good; must benchmark CAS on ARM           | Ordering/backpressure misuse             | Already included; continue using, but prove queue producer/consumer shape. JCTools documents MPSC/MPMC lockless queues and `MpscArrayQueue` as multi-producer/single-consumer. ([GitHub][20])         |
| fastutil            | primitive maps/sets/lists for cell/chunk keys                   |       High for ownership/chunk indexes | Apache 2.0                      | Good; less boxing                         | API complexity                           | Already used; expand packed-long key usage. fastutil provides type-specific collections with smaller memory footprint and fast access. ([GitHub][21])                                                 |
| Agrona              | padded counters, ring buffers, low-level buffers                |                                 Medium | Apache 2.0                      | Good but benchmark needed                 | Extra dependency/shading                 | Use for metrics/counters only after JMH; do not replace gameplay queues blindly. ([GitHub][22])                                                                                                       |
| Caffeine            | bounded pure-result caches: structure/POI/path cache candidates |                                 Medium | Apache 2.0                      | Good                                      | Determinism if caching impure results    | Use only for pure deterministic caches. Caffeine uses W-TinyLFU/adaptive policies. ([GitHub][23])                                                                                                     |
| Netty utilities     | ByteBuf allocator/network buffer reuse                          | Medium for network/chunk serialization | Apache 2.0                      | Good on Linux; already ecosystem-aligned  | Recycler leaks/thread-local retention    | Use only in network/serialization paths, not region game logic. Netty is an async event-driven framework and Apache-licensed. ([GitHub][24])                                                          |
| LMAX Disruptor      | Fixed ring handoff for specialized single consumer lanes        |                       Low/experimental | Apache 2.0                      | Good if fixed topology                    | Overkill, ordering complexity            | Not recommended for general per-region mailbox; only benchmark for specialized telemetry/event pipeline. ([GitHub][25])                                                                               |
| RoaringBitmap       | sparse cell/dirty chunk/diagnostic indexes                      |                             Low/medium | Apache 2.0                      | Good                                      | Overhead for small sets                  | Use only if sets become large/sparse; not for per-tick tiny owner cells. ([GitHub][26])                                                                                                               |
| Netty io_uring      | Linux async network/file I/O experiment                         |                           Experimental | Apache 2.0                      | Linux-only; ARM support must be validated | Incubator/migration risk, crash recovery | Do not use for world storage production until soak tested. Netty io_uring incubator repo was archived and support merged into Netty 4.2 branch, but still needs production validation. ([GitHub][27]) |
| Chronicle Queue/Map | Persistent queues/maps                                          |                           Unknown here | Often complex licensing/support | Linux OK                                  | License/support/complexity               | Not recommended for this codebase without legal review.                                                                                                                                               |

---

## 9. Java 25 / JVM / GC Recommendations

1. **JDK 25 build/run alignment**
   Build files already set Java toolchain 25, `options.release = 25`, and `--enable-preview` for compile/test. Runtime scripts must also pass `--enable-preview` if preview language/API features are actually used. ([GitHub][6])

2. **JFR as first-class production profiler**
   Use JFR CPU-Time Profiling on Linux and Cooperative Sampling in JDK 25 for low-risk always-on profiling experiments. Pair with async-profiler because async-profiler can show native/kernel frames and allocation stacks without safepoint bias. ([OpenJDK][10])

3. **GC matrix**

   * G1: safest baseline. Already configured in Gradle JVM args for build.
   * ZGC: test for large heaps and low pause; watch allocation rate and CPU overhead.
   * Shenandoah: JDK 25 includes Generational Shenandoah in JEP list; test on ARM if vendor build supports it. ([OpenJDK][28])

4. **Virtual threads**
   Do not use virtual threads for region workers, mailbox consumers, or owner-affine tick loops because identity/affinity and low-latency scheduling matter. Virtual threads are reasonable for blocking admin tools or cold I/O wrappers only if ownership mutation never occurs on the virtual thread without handoff.

5. **VarHandle/acquire-release**
   Volatile operations are stronger than acquire/release in Java VarHandle semantics. Use weaker modes only after a proof that total volatile ordering is unnecessary. On ARM, acquire/release maps better to the architecture’s weaker memory model, but correctness must be proven per field. ([Oracle Docs][13])

6. **False sharing**
   For hot counters in mailbox/chunk tracker, use manual padding or carefully gated `@Contended`. Do not add `@Contended` without validating JDK flags and module export requirements in Java 25.

---

## 10. Correctness and Regression Strategy

### Tick order

* Record per-region tick start/end sequence.
* Verify no catch-up storm behavior remains.
* Compare baseline vs optimized replay.

### Ownership invariant

* Every world/block/entity/chunk mutation must prove current owner owns the cell or must defer.
* Add property tests for owner split/merge/remap:

  * no cell has two owners,
  * no active cell has zero owner,
  * retired owner cannot tick,
  * owner epoch mismatch triggers fail-closed/defer.

### Mailbox ordering

* For each `RegionTaskClass`, verify FIFO or existing queue order semantics.
* Critical task non-dropping invariant.
* Overflow/reject policy parity test.
* Same readyTick delayed task order replay.

### Chunk load/save determinism

* Generate same seed/chunk set before/after optimization.
* Byte-level or normalized NBT comparison.
* Crash during save/load replay.
* Region file integrity check.
* No sync chunk load from region worker except explicitly allowed blocking priority path.

### Redstone/fluid/piston/explosion

* Canonical redstone update order suite.
* Piston contraption replay.
* Water/lava spread replay.
* TNT/explosion propagation replay.
* Cross-region boundary tests.

### Plugin compatibility

* Bukkit scheduler compatibility.
* Folia region/entity/global/async scheduler compatibility.
* Teleport/disconnect churn.
* Unsupported plugin fail-fast isolation.

### Race/stress/chaos

* jcstress for lock/counter state.
* Thread dump blocked-thread monitor.
* Randomized cross-region movement + chunk gen + plugin storm.
* Watchdog false-positive/false-negative tests.

---

## 11. Open Questions

1. `RegionTask` ordering fields and delayed task comparator semantics need full source review.
2. `minecraft-patches/features` and `sources` must be fully patched locally and audited for redstone/fluid/piston/explosion/network/NBT paths.
3. `tools/benchmark` and `region-load-test-plugin` need execution to get real baseline.
4. `RegionChunkExecutorLimiter` needs full line-by-line review after local checkout because web view only partially exposed structure.
5. JCTools queue choice must be validated per actual producer/consumer shape:

   * region mailbox: MPSC likely correct,
   * delayed queue: consumer-only,
   * transferred queue: may be MPSC.
6. Need compare against upstream Paper/Purpur/Folia implementation for:

   * chunk IO executor,
   * region scheduler,
   * plugin scheduler fallback,
   * chunk ticket dedup.
7. Need legal review for `zero-allocation-hashing`, zstd-jni, any added library shading conflicts.

---

## 12. Appendix

### 12.1 Grep/ripgrep pass to run after successful clone

```bash
find . -type f | sort > /tmp/astatine-files.txt

rg -n "synchronized|ReentrantLock|ReadWriteLock|StampedLock|Semaphore|CountDownLatch|LockSupport|Thread\.sleep|wait\(|notify|join\(|get\(" .
rg -n "ConcurrentHashMap|CopyOnWriteArrayList|CopyOnWriteArraySet|LinkedList|PriorityQueue|TreeMap|TreeSet|HashMap|HashSet|ArrayList" .
rg -n "AtomicInteger|AtomicLong|AtomicReference|LongAdder|VarHandle|volatile|ThreadLocal" .
rg -n "CompletableFuture|ForkJoinPool|Executor|ExecutorService|VirtualThread|newThreadPerTaskExecutor" .
rg -n "stream\(|parallelStream\(|Optional|Pattern\.compile|String\.format|split\(" .
rg -n "new BlockPos|new ChunkPos|new RegionPos|UUID|Map<.*String|Map<.*UUID" .
rg -n "mailbox|handoff|RegionOwner|RegionRuntimeState|RegionTickScheduler|ShreddedPaperAccess|RegionTaskClass" .
rg -n "chunk|Chunk|ticket|Ticket|save|load|generation|compression|NBT|RegionFile" .
rg -n "redstone|fluid|piston|explosion|TNT|scheduled tick|block event" .
rg -n "JFR|watchdog|metrics|counter|LongAdder|AtomicLong" .
```

### 12.2 Profiler commands

```bash
# CPU
./profiler.sh -e cpu -d 120 -f astatine-cpu.html <pid>

# Allocation
./profiler.sh -e alloc -d 120 -f astatine-alloc.html <pid>

# Lock
./profiler.sh -e lock -d 120 -f astatine-lock.html <pid>

# perf stat
perf stat -d -d -d -p <pid> -- sleep 120
```

### 12.3 JMH skeleton

```java
@State(Scope.Thread)
public class RegionLockInputBenchmark {
    private List<RegionPos> positions;
    private long[] sortedKeys;

    @Setup
    public void setup() {
        // Generate owner cells + radius-1 isolation cells.
        // Preserve same key distribution as Astatine RegionOwner.
    }

    @Benchmark
    public Object current_sortUniqueRegionPos() {
        // call current sortedUniqueRegions equivalent
        return null;
    }

    @Benchmark
    public Object proposed_cachedLongKeys() {
        // return precomputed sorted unique long[]
        return sortedKeys;
    }
}
```

---

## GitHub Issue 목록 형식

| Issue | Title                                                                                   | Priority | Area                         | Expected Gain                            | Risk        | Validation                                  |
| ----: | --------------------------------------------------------------------------------------- | -------- | ---------------------------- | ---------------------------------------- | ----------- | ------------------------------------------- |
|     1 | Cache sorted packed owner/isolation cell snapshots and add long-key exact-lock overload | P0       | ownership/lock/allocation    | allocation -3–8%, scheduler CPU -1–3% 가설 | Low/Medium  | JMH, jcstress, owner split/merge replay     |
|     2 | Add lock wait/hold/failure JFR metrics for `ShreddedPaperRegionLocker`                  | P0       | ownership/diagnostics        | 병목 확정 가능                                 | Low         | JFR, async-profiler lock, deadlock detector |
|     3 | Gate JFR event field filling and chunk action strings behind enabled/sample checks      | P0       | diagnostics/chunk/mailbox    | alloc 감소                                 | Low         | event count parity, alloc profiler          |
|     4 | Add deterministic lock ordering to `RegionOwner.absorbCellsFrom`                       | P0       | ownership/correctness        | deadlock risk 감소                          | Low         | owner merge fuzz, parity tests              |
|     5 | Add loaded-read fallback sampling gate and fixed diagnostic ring                        | P0       | ownership/diagnostics        | fallback storm alloc 감소                   | Low         | forced fallback storm                       |
|     6 | Add runtime-state lifecycle/orphan diagnostics                                          | P1       | memory/ownership             | long-run heap risk 감소                     | Low         | split/merge + teleport churn                |
|     7 | Instrument chunk backpressure retry executor                                           | P1       | chunk/scheduler              | 병목 확정 가능                              | Low         | scenario 7/8/14/15, perf context-switch     |
|     8 | Refactor `RegionChunkIoTracker` event/counter layout without changing caps             | P1       | chunk/ARM/CAS                | chunk stress p99 개선 가능                  | Medium      | JMH admission, chunk gen/load stress        |
|     9 | Replace `RegionMailbox` EnumMap counters with ordinal arrays, preserving queues/order  | P1       | mailbox/ARM                  | enqueue/dequeue CPU -2–8% 가설             | Medium      | mailbox JMH, task order replay              |
|    10 | Full redstone/fluid/piston/explosion audit and fixture inventory                       | P0 audit | block/redstone               | unknown, potentially high                  | Audit only  | order-sensitive regression suite            |

## 지금 당장 구현할 Top 10 PR

1. **PR-001:** JFR/diagnostic allocation gates for region tick, mailbox, and chunk event paths.
2. **PR-002:** `RegionOwner` sorted `long[]` snapshots + invalidation/parity tests.
3. **PR-003:** `ShreddedPaperRegionLocker` overload accepting already sorted exact-lock input.
4. **PR-004:** Deterministic `RegionOwner.absorbCellsFrom` lock ordering.
5. **PR-005:** `ShreddedPaperAccess` fallback stack trace sampling gate and fixed-size ring buffer.
6. **PR-006:** Runtime-state lifecycle/orphan diagnostics.
7. **PR-007:** Lock, chunk retry, scheduler lane, and owner handoff measurement-only JFR events.
8. **PR-008:** `RegionChunkIoTracker` action string/event cleanup and conservative counter layout cleanup.
9. **PR-009:** `RegionMailbox` ordinal arrays for capacities/queues/counters, preserving class order.
10. **PR-010:** Local full patch checkout + redstone/fluid/piston/explosion fixture inventory, no gameplay optimization yet.

## Experimental PR 후보

이 목록은 위 Top 10처럼 바로 merge-target이 아닙니다. feature flag 기본 off와 proof gate가 먼저 있어야 합니다.

1. **EXP-001:** Sharded chunk retry executor experiment after queue-depth/drain-latency baseline.
2. **EXP-002:** Order-preserving delayed task bucket with `readyTick + sequence` golden tests.
3. **EXP-003:** Gated idle-steal scheduler experiment for already-due degraded tasks only.
4. **EXP-004:** Owner handoff metadata wrapper with no pooling and no delegate lifetime change.
5. **EXP-005:** Merge/split two-phase feasibility design with phase-level lock-hold metrics.
6. **EXP-006:** Conditional task classification for idempotent tracker/broadcast pressure only.

[1]: https://github.com/jungwuk-ryu/Astatine/tree/ver/1.21.11 "GitHub - jungwuk-ryu/Astatine: Multi-thread, single-server papermc implementation but BUGGY · GitHub"
[2]: https://raw.githubusercontent.com/jungwuk-ryu/Astatine/ver/1.21.11/shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionTickScheduler.java "raw.githubusercontent.com"
[3]: https://raw.githubusercontent.com/jungwuk-ryu/Astatine/ver/1.21.11/REGIONIZED_ENGINE_MILESTONES.md "raw.githubusercontent.com"
[4]: https://github.com/jungwuk-ryu/Astatine/raw/refs/heads/ver/1.21.11/shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionMailbox.java "raw.githubusercontent.com"
[5]: https://github.com/jungwuk-ryu/Astatine/blob/ver/1.21.11/shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionChunkIoTracker.java "Astatine/shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionChunkIoTracker.java at ver/1.21.11 · jungwuk-ryu/Astatine · GitHub"
[6]: https://raw.githubusercontent.com/jungwuk-ryu/Astatine/ver/1.21.11/build.gradle.kts "raw.githubusercontent.com"
[7]: https://raw.githubusercontent.com/jungwuk-ryu/Astatine/ver/1.21.11/HOW_IT_WORKS.md "raw.githubusercontent.com"
[8]: https://github.com/papermc/folia?utm_source=chatgpt.com "PaperMC/Folia: Fork of Paper which adds regionised ..."
[9]: https://mail.openjdk.org/pipermail/announce/2025-September/000360.html?utm_source=chatgpt.com "Java 25 / JDK 25: General Availability - Mailing Lists"
[10]: https://openjdk.org/jeps/509?utm_source=chatgpt.com "JEP 509: JFR CPU-Time Profiling (Experimental)"
[11]: https://docs.oracle.com/en/java/javase/25/docs//api/jdk.jfr/jdk/jfr/Event.html?utm_source=chatgpt.com "Event (Java SE 25 & JDK 25)"
[12]: https://raw.githubusercontent.com/jungwuk-ryu/Astatine/refs/heads/ver/1.21.11/shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperRegionLocker.java "raw.githubusercontent.com"
[13]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/invoke/VarHandle.html?utm_source=chatgpt.com "VarHandle (Java SE 25 & JDK 25)"
[14]: https://raw.githubusercontent.com/jungwuk-ryu/Astatine/refs/heads/ver/1.21.11/shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/RegionOwner.java "raw.githubusercontent.com"
[15]: https://github.com/jungwuk-ryu/Astatine/raw/refs/heads/ver/1.21.11/shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionChunkExecutorLimiter.java "raw.githubusercontent.com"
[16]: https://github.com/jungwuk-ryu/Astatine/raw/refs/heads/ver/1.21.11/shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ownership/ShreddedPaperAccess.java "raw.githubusercontent.com"
[17]: https://raw.githubusercontent.com/jungwuk-ryu/Astatine/refs/heads/ver/1.21.11/shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/LevelChunkRegionMap.java "raw.githubusercontent.com"
[18]: https://github.com/jungwuk-ryu/Astatine/blob/ver/1.21.11/shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionOverloadController.java "Astatine/shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionOverloadController.java at ver/1.21.11 · jungwuk-ryu/Astatine · GitHub"
[19]: https://github.com/jungwuk-ryu/Astatine/raw/refs/heads/ver/1.21.11/shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionRuntimeState.java "raw.githubusercontent.com"
[20]: https://github.com/JCTools/JCTools/blob/master/jctools-core/src/main/java/org/jctools/queues/MpscArrayQueue.java?utm_source=chatgpt.com "MpscArrayQueue.java"
[21]: https://github.com/vigna/fastutil?utm_source=chatgpt.com "fastutil extends the Java™ Collections Framework ..."
[22]: https://github.com/aeron-io/agrona?utm_source=chatgpt.com "aeron-io/agrona: High Performance data structures ..."
[23]: https://github.com/ben-manes/caffeine?utm_source=chatgpt.com "ben-manes/caffeine: A high performance caching library for ..."
[24]: https://github.com/netty/netty/blob/master/buffer/src/main/java/io/netty/buffer/PooledByteBufAllocator.java?utm_source=chatgpt.com "PooledByteBufAllocator.java - netty/netty"
[25]: https://github.com/LMAX-Exchange/disruptor/?utm_source=chatgpt.com "LMAX-Exchange/disruptor: High Performance Inter-Thread ..."
[26]: https://github.com/roaringbitmap/roaringbitmap?utm_source=chatgpt.com "RoaringBitmap - A better compressed bitset in Java"
[27]: https://github.com/netty/netty-incubator-transport-io_uring?utm_source=chatgpt.com "netty/netty-incubator-transport-io_uring"
[28]: https://openjdk.org/projects/jdk/25/jeps-since-jdk-21?utm_source=chatgpt.com "JEPs in JDK 25 integrated since JDK 21"
