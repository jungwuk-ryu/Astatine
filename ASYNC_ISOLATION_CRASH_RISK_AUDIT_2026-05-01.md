# Async Isolation Crash Risk Audit

작성일: 2026-05-01
대상: ShreddedPaper independent region ticking / async ownership isolation
범위: 모 엔티티 ticking, passenger/vehicle graph, player ticking/network, command 처리, plugin/scheduler callback, chunk/ticket continuation

## 요약

이 감사는 "코드가 어느 thread에서 실행되는가"보다 "그 thread가 실제로 어떤 world cell/entity graph/player state를 소유하는가"를 기준으로 보았다. ShreddedPaper의 핵심 불변식은 region owner 또는 명시적 handoff 없이 block/entity/player/chunk 상태를 mutate하지 않는 것이다.

가장 위험한 부류는 다음과 같다.

1. player/connection 객체가 async respawn 중 교체되는데, queued packet과 respawn completion이 stable owner를 다시 검증하지 않는다.
2. vehicle/passenger graph는 root entity owner만 확인하고 재귀 passenger tick/dismount로 내려간다.
3. command wrapper가 단일 chunk 또는 단일 entity만 handoff하고, 실제 mutation volume/entity graph 전체를 소유하지 않는다.
4. plugin 호환용 synchronous lock이 region ownership handoff처럼 사용되는 곳이 있다.
5. independent ticking에서 region tick future가 실제 region tick 완료를 의미하지 않아, global continuation이 owner 없이 진행될 수 있다.

## 조사 방식

### Sub-agent 역할 분배

요청에 따라 세 명의 sub-agent를 동시에 사용했다.

- Entity/vehicle 담당: entity ticking, passenger/vehicle graph, portal/projectile/entity movement.
- Player/network 담당: player ticking, packet scheduling, respawn, disconnect, chunk send, network flush.
- Command/plugin 담당: command wrapper, Bukkit/plugin callback, scheduler/mailbox rejection.

### 프롬프트 설계 원칙

edge-case를 놓치지 않기 위해 각 agent prompt에는 다음 관찰 기준을 넣었다.

- stale owner: scheduling 시점의 owner와 실행 시점의 owner가 달라지는 경우.
- retired scheduler: entity/player scheduler가 retired/reject되었을 때 fallback runnable이 어디서 실행되는지.
- graph ownership: entity 하나가 아니라 vehicle, passenger, recursive passenger tree, projectile owner, target entity까지 포함해야 하는지.
- coordinate mismatch: 음수 좌표에서 `(int)` truncation과 `floor` 기반 block/chunk 계산이 어긋나는지.
- volume ownership: command가 single origin만 소유하고 source/destination bounding box 전체를 읽거나 쓰는지.
- plugin lock confusion: `SynchronousPluginExecution`이 serialization만 제공하고 region owner handoff를 제공하지 않는 점.
- mailbox rejection: 정상 gameplay path에서 region mailbox rejection이 crash exception으로 전파되는지.
- async completion thread: `whenComplete`, chunk-load callback, packet completion이 arbitrary executor에서 state를 만지는지.

### 로컬 스캔

- `tools/async-audit/scan-async-ownership.mjs --fail-on-critical`
  - 결과: `critical=0 high=0 medium=0`
- `tools/async-audit/scan-java-call-sites.mjs`
  - 결과: `1589` AST call-site candidates
  - 분포: `critical=28 high=1551 medium=10`
  - 자동 결과는 false positive가 많아, 아래 항목은 sub-agent 결과와 수동 line 검증으로 재분류했다.

## P0 Findings

### 1. Queued packet이 old player owner에 예약된 뒤 new player를 처리할 수 있음

파일:

- `shreddedpaper-server/src/minecraft/java/net/minecraft/network/protocol/PacketUtils.java:27`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/network/protocol/PacketUtils.java:29`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/network/protocol/PacketUtils.java:33`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:3188`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:3217`

경로:

`Netty packet -> PacketUtils.ensureRunningOnSameThread -> ShreddedPaper.runSync(gamePacketListener.player, ...) -> packet.handle(packetListener)`

문제:

`PacketUtils`는 scheduling 시점의 `gamePacketListener.player`를 기준으로 owner thread에 예약한다. 그러나 runnable 내부에서는 stable player snapshot을 쓰지 않고 mutable `packetListener`를 그대로 handle한다. async respawn은 completion에서 `this.player`를 교체하므로, queued packet이 old player owner에서 실행되면서 new player를 mutate할 수 있다.

크래시 시나리오:

1. player가 death/end portal respawn 또는 dimension change 중이다.
2. movement/use/block packet이 old player 기준으로 queued된다.
3. completion이 `this.player`를 new `ServerPlayer`로 교체한다.
4. queued packet이 old owner region에서 실행되고 `packet.handle(packetListener)`가 new player state를 만진다.
5. movement/block/entity path에서 `TickThread.ensureTickThread` 또는 region ownership assertion이 터진다.

권장 수정:

- scheduling 시점에 `final ServerPlayer scheduledPlayer = gamePacketListener.player`를 캡처한다.
- runnable 시작 시 `gamePacketListener.player == scheduledPlayer`, `scheduledPlayer.connection.player == scheduledPlayer`, `!processedDisconnect`, `scheduledPlayer.level()`/region owner를 재검증한다.
- mismatch면 packet을 drop하거나 current player owner로 재-handoff한다.

신뢰도: 높음

### 2. Async respawn completion fallback이 off-owner에서 inline 실행될 수 있음

파일:

- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:3196`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:3198`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:3199`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:3227`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:3229`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:3230`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/ShreddedPaper.java:32`

경로:

`PERFORM_RESPAWN -> PlayerList.respawnAsync(...).whenComplete(...) -> ShreddedPaper.runSync(completionPlayer, finishRespawn, finishRespawn) -> finishRespawn.run() fallback`

문제:

`finishRespawn`은 `this.player`, `chunkSender`, position, game mode, game rules를 mutate한다. 그런데 owner scheduling이 실패하면 같은 runnable을 inline으로 실행한다. entity scheduler retired/reject는 disconnect, kick, duplicate login, dimension replacement 중 충분히 발생할 수 있다.

크래시 시나리오:

1. respawn async chain이 completion thread에서 끝난다.
2. completion player scheduler가 retired되었거나 reject한다.
3. `finishRespawn.run()`이 completer thread에서 실행된다.
4. connection/player/chunk send state가 owner 없이 교체되어 다음 packet/player tick에서 crash한다.

권장 수정:

- retired callback과 direct fallback에서 mutable completion을 실행하지 않는다.
- 실패 시 connection을 disconnect-safe terminal state로 전환하거나, current live player owner를 다시 찾아 재-handoff한다.
- `finishRespawn` 시작 시 stable player, connection, level, disconnect 상태를 다시 검증한다.

신뢰도: 중상

### 3. Vehicle/passenger graph ticking이 root entity owner만 보장함

파일:

- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperEntityTicker.java:15`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperEntityTicker.java:38`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java:1487`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java:1500`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java:1511`

경로:

`ShreddedPaperEntityTicker.tickEntity(vehicle) -> ServerLevel.tickNonPassenger(vehicle) -> tickPassenger(vehicle, passenger, active)`

문제:

entity ticker는 root entity의 region ownership만 확인한다. 이후 vanilla passenger ticking은 passenger tree로 내려가며 `passengerEntity.stopRiding()`, `rideTick()`, `postTick()` 등을 실행한다. passenger가 다른 region owner에 속하는 stale graph 상태라면 foreign entity를 현재 vehicle owner worker가 tick/mutate한다.

크래시 시나리오:

1. boat/horse/minecart/player/mob passenger graph가 command, portal, plugin teleport, split/merge 타이밍으로 region boundary를 넘는다.
2. graph link는 남아 있지만 owner cell은 서로 다르다.
3. vehicle region tick이 passenger를 재귀 tick한다.
4. passenger movement, dismount, event callback, tracker update 중 wrong-thread assertion이 발생한다.

권장 수정:

- root vehicle tick 전에 recursive passenger graph의 owner set을 계산하고 모두 현재 owner/write lock 안인지 확인한다.
- cross-owner graph면 graph 전체를 하나의 owner handoff로 처리하거나, stale relation을 owner-safe mailbox로 끊는다.
- `tickPassenger` 진입 전 `TickThread.isTickThreadFor(passengerEntity)`를 강제한다.

신뢰도: 높음

### 4. Passenger tick pre-check가 foreign vehicle을 읽고 dismount할 수 있음

파일:

- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperEntityTicker.java:28`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperEntityTicker.java:30`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperEntityTicker.java:34`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperEntityTicker.java:38`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/Entity.java:3577`

경로:

`tickEntity(passenger) -> entity.getVehicle() -> vehicle.hasPassenger(entity) -> entity.stopRiding()`

문제:

현재 entity가 owned인지 확인하지만 vehicle entity가 owned인지 확인하지 않는다. `vehicle.hasPassenger(entity)`는 foreign vehicle passenger list read이고, `entity.stopRiding()`은 `vehicle.removePassenger(...)`를 통해 vehicle passenger list를 mutate한다. write-lock promotion보다 앞에서 실행되는 것도 위험하다.

권장 수정:

- vehicle relation 확인도 graph owner check 이후로 옮긴다.
- foreign vehicle이면 vehicle owner로 relation cleanup task를 보낸다.
- `stopRiding` 호출 전에 passenger와 vehicle 양쪽 owner를 모두 보장한다.

신뢰도: 중상

### 5. Block update pathfinding이 foreign mob navigation을 mutate함

파일:

- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java:1961`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java:1980`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java:1981`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/LevelChunkRegionMap.java:922`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/LevelChunkRegionMap.java:929`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/LevelChunkRegionMap.java:932`

경로:

`sendBlockUpdated -> collectRelevantNavigatingMobs(region) -> collect Mob references under read lock -> unlock -> PathNavigation.recomputePath()`

문제:

independent owner mode에서 foreign neighboring region은 read-only lock만 잠깐 잡고 navigating mob references를 수집한다. lock을 해제한 뒤 `PathNavigation.recomputePath()`를 호출하여 mob navigation state를 mutate한다. 이 mutation은 mob owner region에서 실행되지 않는다.

크래시 시나리오:

1. door/trapdoor/fence gate/fluid/block update가 region boundary 근처에서 발생한다.
2. region A가 region B의 navigating mob reference를 read lock으로 수집한다.
3. lock 해제 후 region A worker가 B mob의 navigation을 recompute한다.
4. navigation state 또는 pathfinding block access가 owner invariant를 깨뜨린다.

권장 수정:

- foreign mob은 reference만 가져와 바로 mutate하지 않는다.
- mob owner region에 recompute request를 mailbox로 보낸다.
- 또는 region-local snapshot으로 recompute하고 결과 apply만 owner에서 한다.

신뢰도: 높음

## P1 Findings

### 6. `/tp`가 음수 좌표에서 잘못된 destination chunk를 lock함

파일:

- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/TeleportCommand.java:258`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/TeleportCommand.java:269`

문제:

command는 `BlockPos.containing(x, y, z)`로 bounds를 확인하지만, 실제 handoff chunk는 `new BlockPos((int) x, (int) y, (int) z)`로 만든다. Java `(int)`는 음수 소수에서 0 방향 truncation을 하므로 `x=-0.5`는 block `0`으로 계산된다. Minecraft block/chunk 계산은 floor semantics라 실제 target block은 `-1`이다.

재현:

`/tp @s -0.5 80 0.5`

권장 수정:

- `new ChunkPos(BlockPos.containing(x, y, z))`를 사용한다.
- relative teleport의 경우 최종 absolute destination `d/d1/d2` 기준으로 owner를 계산한다.

신뢰도: 높음

### 7. `/clone`은 destination origin만 handoff하고 source/destination volume 전체를 소유하지 않음

파일:

- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/CloneCommands.java:239`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/CloneCommands.java:257`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/CloneCommands.java:288`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/CloneCommands.java:305`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/CloneCommands.java:335`

문제:

`ensureSync(serverLevel1, blockPos2, ...)`는 destination origin만 소유한다. clone body는 source bounding box를 읽고, destination bounding box를 쓰며, MOVE mode에서는 source도 쓴다. 큰 clone 또는 cross-region/cross-dimension clone은 owner 밖의 chunks를 read/write한다.

권장 수정:

- source bounding box와 destination bounding box를 모두 포함한 multi-region ownership handoff를 사용한다.
- cross-dimension clone은 source read snapshot과 destination apply 단계를 분리한다.
- `hasChunksAt`는 loaded check일 뿐 ownership check가 아님을 전제로 처리한다.

신뢰도: 높음

### 8. `/setblock` filter가 owner handoff 전에 block read/load 가능

파일:

- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/SetBlockCommand.java:107`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/SetBlockCommand.java:110`

문제:

`filter.test(new BlockInWorld(level, pos, true))`가 region owner handoff 전에 실행된다. `loadChunks=true`라 target이 unloaded거나 다른 region이면 player command thread에서 sync load 또는 foreign read가 발생할 수 있다.

권장 수정:

- filter evaluation을 `ensureSync` 내부로 옮긴다.
- filter가 실패한 경우 command result 전달 방식을 async-safe하게 정리한다.

신뢰도: 중상

### 9. Async chunk callback은 plugin lock만 잡고 region owner로 가지 않음

파일:

- `shreddedpaper-server/paper-patches/files/src/main/java/org/bukkit/craftbukkit/CraftWorld.java.patch:174`
- `shreddedpaper-server/paper-patches/files/src/main/java/org/bukkit/craftbukkit/CraftWorld.java.patch:182`
- `shreddedpaper-server/paper-patches/files/src/main/java/org/bukkit/craftbukkit/CraftWorld.java.patch:187`
- `shreddedpaper-server/paper-patches/files/src/main/java/org/bukkit/craftbukkit/CraftWorld.java.patch:196`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/SynchronousPluginExecution.java:69`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/SynchronousPluginExecution.java:92`

문제:

`SynchronousPluginExecution`은 unsupported plugin을 serialize할 뿐, callback을 owning region으로 이동시키지 않는다. `getChunkAtAsync` completion callback이 chunk completion executor에서 실행되며, callback이 Bukkit block/entity/world state를 만지면 owner invariant가 없다.

재현:

```java
world.getChunkAtAsync(x, z, chunk -> {
    chunk.getBlock(0, 64, 0).getState().update(true);
});
```

권장 수정:

- async chunk callback은 chunk owner region으로 handoff한 뒤 실행한다.
- callback API 계약상 arbitrary thread라면 Bukkit mutation을 금지하고 fail-fast diagnostics를 추가한다.

신뢰도: 높음

### 10. Independent ticking에서 region tick future가 실제 완료를 의미하지 않음

파일:

- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperChunkTicker.java:51`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperChunkTicker.java:60`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperChunkTicker.java:64`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerChunkCache.java:547`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerChunkCache.java:648`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerChunkCache.java:674`

문제:

independent mode에서 `tickChunks`는 region tick을 scheduler에 register만 하고 `completedFuture`를 반환한다. 그 뒤 `chunkMap.tick`, unload, custom spawner continuation이 `levelThread`에서 실행된다. 이 thread는 `ShreddedPaperTickThread`일 수 있지만 특정 region owner는 아니다.

권장 수정:

- independent scheduler가 해당 tick cycle의 completion future를 제공하도록 하거나, continuation을 region-owner 작업으로 분해한다.
- custom spawner/global chunk maintenance는 owner-free read-only 작업인지 재검증하고, mutation은 owner mailbox로 보낸다.

신뢰도: 중

### 11. Parallel flush는 기본값 true인데 PLAY queue를 drain하지 못함

파일:

- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperChunkTicker.java:106`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperChunkTicker.java:112`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/network/Connection.java:529`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/network/Connection.java:534`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/network/Connection.java:538`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/network/Connection.java:546`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/network/Connection.java:592`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/config/ShreddedPaperConfiguration.java:180`

문제:

`flushQueueInParallel`은 ShreddedPaper worker에서 `Connection.flushQueue()`를 호출하지만, PLAY connection은 main thread도 pending state도 아니므로 off-thread flush가 rejected된다. 반환값은 무시된다. 동시에 `Connection.tick()`은 `flushQueueInParallel=true`일 때 normal flush를 skip한다.

영향:

직접 crash라기보다는 packet backlog, keepalive timeout, disconnect churn, memory pressure, watchdog escalation으로 이어질 수 있다.

권장 수정:

- PLAY connection queue flush를 owner/player tick에서 처리하거나 Netty event loop safe queue로 이관한다.
- off-thread rejection return을 집계하고 fallback flush를 보장한다.
- P0 안정화 전까지 기본값을 false로 내리는 것을 검토한다.

신뢰도: bug 높음, crash 중

### 12. RegionScheduler initial enqueue rejection이 command/event ingress로 전파됨

파일:

- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/ShreddedPaperRegionSchedulerApiImpl.java:78`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/ShreddedPaperRegionSchedulerApiImpl.java:83`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/ShreddedPaperRegionSchedulerApiImpl.java:85`

문제:

initial plugin region task scheduling에서 mailbox가 full이면 `RejectedExecutionException`을 던진다. repeating task reschedule은 rejection을 처리하지만 initial schedule은 caller에게 예외가 전파된다. plugin storm, shutdown/retired target, 낮은 mailbox capacity에서 command/event path가 깨질 수 있다.

권장 수정:

- Bukkit scheduler API semantics에 맞게 rejected task를 cancelled state로 반환하고 diagnostics counter를 남긴다.
- command/event ingress에서는 unchecked exception으로 tick을 죽이지 않도록 containment한다.

신뢰도: 높음

## P2 Findings

### 13. Cross-cell neighbor update mailbox rejection이 직접 IllegalStateException

파일:

- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java:2026`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java:2030`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java:2034`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java:2035`

문제:

loaded chunk check와 mailbox schedule 사이에 unload/split/merge/shutdown이 발생하면 `scheduleTaskNonDropping`이 실패할 수 있고, 현재 코드는 gameplay neighbor update path에서 바로 `IllegalStateException`을 던진다.

권장 수정:

- rejection을 crash가 아닌 dropped/deferred neighbor update metric으로 처리할지 결정한다.
- target owner epoch를 포함한 retry handoff를 사용한다.

신뢰도: 중

### 14. Projectile cross-region move guard가 projectile chunk-load limit 설정에 종속됨

파일:

- `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/Projectile.java:60`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/Projectile.java:71`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/Projectile.java:74`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/Entity.java:876`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/Entity.java:878`
- `shreddedpaper-server/src/main/java/org/bxteam/divinemc/config/DivineConfig.java:60`
- `shreddedpaper-server/src/main/java/org/bxteam/divinemc/config/DivineConfig.java:61`

문제:

`maxPerTick < 0 && maxPerProjectile < 0`이면 projectile move guard가 false를 반환하고 `super.setPos`로 진행한다. 기본값은 10/10이라 즉시 위험하지는 않지만, 설정을 둘 다 음수로 두면 cross-region projectile move가 `Entity.setPos`의 async move guard에 걸릴 수 있다.

권장 수정:

- chunk-load limiting과 region ownership handoff를 분리한다.
- limit disabled 상태에서도 cross-owner projectile move handoff는 유지한다.

신뢰도: 중, config-dependent

## Audited With No High-Signal Finding

다음 경로는 살펴봤지만 이번 기준에서 high-signal crash 후보로 올리지 않았다.

- `ItemEntity` merge/pickup ownership wrappers: item/player region deferral이 비교적 잘 잡혀 있다.
- `VehicleEntity` damage ownership: vehicle, passengers, attacking entities를 포함한 ownership box를 사용한다.
- tameable/shulker/enderman random teleport: destination/owner check 또는 deferral이 있다.
- `ServerLevel.addEntity`: cross-owner add handoff가 있다.
- entity Bukkit scheduler execution: `ShreddedPaperChunkTicker`에서 entity owner tick guard가 있다.
- async pathfinding: independent region ticking에서는 config load 시 disable된다.

## 권장 수정 순서

1. `PacketUtils` queued packet stable player capture/recheck.
2. async respawn completion의 inline fallback 제거.
3. vehicle/passenger recursive graph ownership 모델 정리.
4. `TeleportCommand` coordinate 계산을 `BlockPos.containing` 및 final absolute destination 기준으로 수정.
5. `/clone` source/destination volume ownership handoff 설계.
6. `/setblock` filter를 owner handoff 내부로 이동.
7. plugin async chunk callback contract를 owner handoff 또는 explicit unsafe callback으로 정리.
8. independent tick continuation이 실제 region tick completion을 기다리도록 scheduler future 설계.
9. network parallel flush 기본값/실행 위치 재검토.
10. mailbox rejection containment 정책 정리.

## 테스트 아이디어

- Respawn race:
  - player death respawn spam 중 movement/use packet을 연속 전송.
  - respawn completion과 disconnect/kick을 동시에 유발.
  - 기대: queued packet이 stale player owner에서 실행되지 않아야 한다.

- Vehicle graph split:
  - boat/horse/minecart에 player/mob passenger를 태운 뒤 plugin teleport로 passenger 또는 vehicle만 region boundary 밖으로 이동.
  - 다음 tick에서 passenger tick/dismount가 owner-safe하게 처리되는지 확인.

- Negative coordinate teleport:
  - `/tp @s -0.5 80 0.5`
  - owner chunk가 block floor semantics와 일치하는지 확인.

- Clone volume:
  - source와 destination이 서로 다른 region 여러 개에 걸치는 `/clone`.
  - source read와 destination write가 모두 owner handoff 안에서 실행되는지 확인.

- Setblock filter:
  - player region과 다른 region/unloaded block에 filtered `/setblock`.
  - handoff 전 sync load가 발생하지 않아야 한다.

- Async chunk callback:
  - `getChunkAtAsync` callback에서 `BlockState.update(true)` 또는 entity access.
  - owner handoff 없이 실행되지 않아야 한다.

- Independent tick continuation:
  - custom spawner/chunkMap maintenance가 region tick과 동시에 owner 없이 실행되는지 instrumentation.

- Network flush:
  - `flushQueueInParallel=true`에서 `NetworkFlushDiagnostics.recordRejectedOffThread` 증가 여부와 pending queue drain 여부 확인.
