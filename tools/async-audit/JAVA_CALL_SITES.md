# JDK AST Async Ownership Call Sites

Generated: 2026-05-01T06:51:48.919281Z

This report is built from the JDK compiler API over real `.java` files. It records method invocation AST nodes, so comments and unrelated text do not count as call sites. It is syntax-aware, not full IntelliJ-grade symbol resolution yet.

## Summary

- Candidates: 1589
- Critical: 28
- High: 1551
- Medium: 10

| Category | Count |
| --- | ---: |
| entity movement/teleport/player tick | 574 |
| natural spawning/entity add/structure sync load | 148 |
| player chunk send/post-processing/stale holder broadcast | 1 |
| scheduled tick/neighbor/fluid-leaf-redstone | 166 |
| uncategorized sync-load risk | 700 |

## Candidates

### AO-AST-07A67CC6 - AST call getChunkAt in Level.java
- Severity: critical
- Category: entity movement/teleport/player tick
- Sink: bukkit-chunk-load
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/Level.java:1661`
- Context: `Level#getBlockEntity`
- Receiver: `this`
- Method: `getChunkAt`
- Evidence: `this.getChunkAt(pos)`
- Suggested fix: Guard with isChunkLoaded or route through async chunk loading before chunk access.

### AO-AST-6EEF2BDD - AST call getChunkAt in Level.java
- Severity: critical
- Category: entity movement/teleport/player tick
- Sink: bukkit-chunk-load
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/Level.java:1674`
- Context: `Level#setBlockEntity`
- Receiver: `this`
- Method: `getChunkAt`
- Evidence: `this.getChunkAt(blockPos)`
- Suggested fix: Guard with isChunkLoaded or route through async chunk loading before chunk access.

### AO-AST-1DB94248 - AST call getChunkAt in Level.java
- Severity: critical
- Category: entity movement/teleport/player tick
- Sink: bukkit-chunk-load
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/Level.java:1680`
- Context: `Level#removeBlockEntity`
- Receiver: `this`
- Method: `getChunkAt`
- Evidence: `this.getChunkAt(pos)`
- Suggested fix: Guard with isChunkLoaded or route through async chunk loading before chunk access.

### AO-AST-44934781 - AST call getChunkAt in Level.java
- Severity: critical
- Category: entity movement/teleport/player tick
- Sink: bukkit-chunk-load
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/Level.java:1907`
- Context: `Level#blockEntityChanged`
- Receiver: `this`
- Method: `getChunkAt`
- Evidence: `this.getChunkAt(pos)`
- Suggested fix: Guard with isChunkLoaded or route through async chunk loading before chunk access.

### AO-AST-426A533D - AST call getChunkAt in CraftChunk.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: bukkit-chunk-load
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftChunk.java:141`
- Context: `CraftChunk#getTileEntities`
- Receiver: `this.getWorld()`
- Method: `getChunkAt`
- Evidence: `this.getWorld().getChunkAt(this.x, this.z)`
- Suggested fix: Guard with isChunkLoaded or route through async chunk loading before chunk access.

### AO-AST-B5C3575A - AST call getChunkAt in CraftChunk.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: bukkit-chunk-load
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftChunk.java:163`
- Context: `CraftChunk#getTileEntities`
- Receiver: `this.getWorld()`
- Method: `getChunkAt`
- Evidence: `this.getWorld().getChunkAt(this.x, this.z)`
- Suggested fix: Guard with isChunkLoaded or route through async chunk loading before chunk access.

### AO-AST-D5FEC077 - AST call loadChunk in CraftChunk.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: bukkit-chunk-load
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftChunk.java:191`
- Context: `CraftChunk#load`
- Receiver: `this.getWorld()`
- Method: `loadChunk`
- Evidence: `this.getWorld().loadChunk(this.getX(), this.getZ(), true)`
- Suggested fix: Guard with isChunkLoaded or route through async chunk loading before chunk access.

### AO-AST-50DF3DDB - AST call loadChunk in CraftChunk.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: bukkit-chunk-load
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftChunk.java:196`
- Context: `CraftChunk#load`
- Receiver: `this.getWorld()`
- Method: `loadChunk`
- Evidence: `this.getWorld().loadChunk(this.getX(), this.getZ(), generate)`
- Suggested fix: Guard with isChunkLoaded or route through async chunk loading before chunk access.

### AO-AST-7B8408DE - AST call getChunk in CraftWorld.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: getchunk-load-true
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftWorld.java:387`
- Context: `CraftWorld#getChunkAt`
- Receiver: `this.world`
- Method: `getChunk`
- Evidence: `this.world.getChunk(x, z, ChunkStatus.FULL, true)`
- Suggested fix: Use getChunkIfLoadedImmediately/getChunk(..., false), prefetch asynchronously, or hand off to the owning region.

### AO-AST-5AC0DFA6 - AST call getChunkAt in CraftWorld.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: bukkit-chunk-load
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftWorld.java:395`
- Context: `CraftWorld#getChunkAt`
- Receiver: `this`
- Method: `getChunkAt`
- Evidence: `this.getChunkAt(x, z)`
- Suggested fix: Guard with isChunkLoaded or route through async chunk loading before chunk access.

### AO-AST-7591A63D - AST call getChunkAt in CraftWorld.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: bukkit-chunk-load
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftWorld.java:405`
- Context: `CraftWorld#getChunkAt`
- Receiver: `this`
- Method: `getChunkAt`
- Evidence: `this.getChunkAt(block.getX() >> 4, block.getZ() >> 4)`
- Suggested fix: Guard with isChunkLoaded or route through async chunk loading before chunk access.

### AO-AST-7F247F22 - AST call getChunk in CraftWorld.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: getchunk-load-true
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftWorld.java:552`
- Context: `CraftWorld#loadChunk`
- Receiver: `this.world.getChunkSource()`
- Method: `getChunk`
- Evidence: `this.world.getChunkSource().getChunk(x, z, generate || isChunkGenerated(x, z) ? ChunkStatus.FULL : ChunkStatus.EMPTY, true)`
- Suggested fix: Use getChunkIfLoadedImmediately/getChunk(..., false), prefetch asynchronously, or hand off to the owning region.

### AO-AST-84F70585 - AST call getChunk in CraftWorld.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: getchunk-load-true
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftWorld.java:557`
- Context: `CraftWorld#loadChunk`
- Receiver: `this.world.getChunkSource()`
- Method: `getChunk`
- Evidence: `this.world.getChunkSource().getChunk(x, z, ChunkStatus.FULL, true)`
- Suggested fix: Use getChunkIfLoadedImmediately/getChunk(..., false), prefetch asynchronously, or hand off to the owning region.

### AO-AST-3CD22A98 - AST call loadChunk in CraftWorld.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: bukkit-chunk-load
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftWorld.java:579`
- Context: `CraftWorld#loadChunk`
- Receiver: `this`
- Method: `loadChunk`
- Evidence: `this.loadChunk(chunk.getX(), chunk.getZ())`
- Suggested fix: Guard with isChunkLoaded or route through async chunk loading before chunk access.

### AO-AST-F8F872D7 - AST call getChunkAt in CraftWorld.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: bukkit-chunk-load
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftWorld.java:593`
- Context: `CraftWorld#addPluginChunkTicket`
- Receiver: `this`
- Method: `getChunkAt`
- Evidence: `this.getChunkAt(x, z)`
- Suggested fix: Guard with isChunkLoaded or route through async chunk loading before chunk access.

### AO-AST-BF78F8F0 - AST call getChunkAt in CraftWorld.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: bukkit-chunk-load
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftWorld.java:638`
- Context: `CraftWorld#getIntersectingChunks`
- Receiver: `this`
- Method: `getChunkAt`
- Evidence: `this.getChunkAt(x, z, false)`
- Suggested fix: Guard with isChunkLoaded or route through async chunk loading before chunk access.

### AO-AST-6742A58B - AST call getChunk in ChunkTaskScheduler.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: getchunk-load-true
- Location: `shreddedpaper-server/src/minecraft/java/ca/spottedleaf/moonrise/patches/chunk_system/scheduling/ChunkTaskScheduler.java:627`
- Context: `ChunkTaskScheduler#syncLoadNonFull`
- Receiver: `this.world.getChunkSource()`
- Method: `getChunk`
- Evidence: `this.world.getChunkSource().getChunk(chunkX, chunkZ, status, true)`
- Suggested fix: Use getChunkIfLoadedImmediately/getChunk(..., false), prefetch asynchronously, or hand off to the owning region.

### AO-AST-7144AB67 - AST call syncLoad in ServerChunkCache.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: sync-load-call
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerChunkCache.java:205`
- Context: `ServerChunkCache#getChunkFallback`
- Receiver: `this`
- Method: `syncLoad`
- Evidence: `this.syncLoad(chunkX, chunkZ, toStatus)`
- Suggested fix: Replace with async chunk loading and resume on the owning region, or use already-loaded chunk access when absence is acceptable.

### AO-AST-8D5FA5AC - AST call syncLoadNonFull in ServerLevel.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: sync-load-call
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java:374`
- Context: `ServerLevel#moonrise$syncLoadNonFull`
- Receiver: `this.moonrise$getChunkTaskScheduler()`
- Method: `syncLoadNonFull`
- Evidence: `this.moonrise$getChunkTaskScheduler().syncLoadNonFull(chunkX, chunkZ, status)`
- Suggested fix: Replace with async chunk loading and resume on the owning region, or use already-loaded chunk access when absence is acceptable.

### AO-AST-DEF69E45 - AST call getChunk in Level.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: getchunk-load-true
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/Level.java:367`
- Context: `Level#getChunk`
- Receiver: `((Level)(Object)this)`
- Method: `getChunk`
- Evidence: `((Level)(Object)this).getChunk(x, z, status, true)`
- Suggested fix: Use getChunkIfLoadedImmediately/getChunk(..., false), prefetch asynchronously, or hand off to the owning region.

### AO-AST-C4F8E323 - AST call getChunk in Level.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: getchunk-load-true
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/Level.java:1024`
- Context: `Level#getChunk`
- Receiver: `cps`
- Method: `getChunk`
- Evidence: `cps.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true)`
- Suggested fix: Use getChunkIfLoadedImmediately/getChunk(..., false), prefetch asynchronously, or hand off to the owning region.

### AO-AST-7DB88D61 - AST call getChunkAt in Level.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: bukkit-chunk-load
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/Level.java:1132`
- Context: `Level#setBlock`
- Receiver: `this`
- Method: `getChunkAt`
- Evidence: `this.getChunkAt(pos)`
- Suggested fix: Guard with isChunkLoaded or route through async chunk loading before chunk access.

### AO-AST-F0374E94 - AST call getChunk in Level.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: getchunk-load-true
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/Level.java:1366`
- Context: `Level#getBlockState`
- Receiver: `this`
- Method: `getChunk`
- Evidence: `this.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true)`
- Suggested fix: Use getChunkIfLoadedImmediately/getChunk(..., false), prefetch asynchronously, or hand off to the owning region.

### AO-AST-CF648DCD - AST call getChunkAt in Level.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: bukkit-chunk-load
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/Level.java:1376`
- Context: `Level#getFluidState`
- Receiver: `this`
- Method: `getChunkAt`
- Evidence: `this.getChunkAt(pos)`
- Suggested fix: Guard with isChunkLoaded or route through async chunk loading before chunk access.

### AO-AST-25B26AB7 - AST call getChunk in LevelReader.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: getchunk-load-true
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/LevelReader.java:33`
- Context: `LevelReader#moonrise$syncLoadNonFull`
- Receiver: `((LevelReader)this)`
- Method: `getChunk`
- Evidence: `((LevelReader)this).getChunk(chunkX, chunkZ, status, true)`
- Suggested fix: Use getChunkIfLoadedImmediately/getChunk(..., false), prefetch asynchronously, or hand off to the owning region.

### AO-AST-0C8C0D91 - AST call getChunk in LevelReader.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: getchunk-load-true
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/LevelReader.java:141`
- Context: `LevelReader#getChunk`
- Receiver: `this`
- Method: `getChunk`
- Evidence: `this.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true)`
- Suggested fix: Use getChunkIfLoadedImmediately/getChunk(..., false), prefetch asynchronously, or hand off to the owning region.

### AO-AST-2C2F856A - AST call getChunk in LevelReader.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: getchunk-load-true
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/LevelReader.java:145`
- Context: `LevelReader#getChunk`
- Receiver: `this`
- Method: `getChunk`
- Evidence: `this.getChunk(chunkX, chunkZ, chunkStatus, true)`
- Suggested fix: Use getChunkIfLoadedImmediately/getChunk(..., false), prefetch asynchronously, or hand off to the owning region.

### AO-AST-9AA3CB56 - AST call getChunkAt in EndDragonFight.java
- Severity: critical
- Category: uncategorized sync-load risk
- Sink: bukkit-chunk-load
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/dimension/end/EndDragonFight.java:470`
- Context: `EndDragonFight#createNewDragon`
- Receiver: `this.level`
- Method: `getChunkAt`
- Evidence: `this.level.getChunkAt(new BlockPos(this.origin.getX(), 128 + this.origin.getY(), this.origin.getZ()))`
- Suggested fix: Guard with isChunkLoaded or route through async chunk loading before chunk access.

### AO-AST-0881061E - AST call getBlockEntity in CapturedBlockState.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CapturedBlockState.java:56`
- Context: `CapturedBlockState#addBees`
- Receiver: `worldGenLevel`
- Method: `getBlockEntity`
- Evidence: `worldGenLevel.getBlockEntity(pos, BlockEntityType.BEEHIVE)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-8C1BED02 - AST call getBlockEntity in CraftBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlock.java:517`
- Context: `CraftBlock#breakNaturally`
- Receiver: `this.world`
- Method: `getBlockEntity`
- Evidence: `this.world.getBlockEntity(this.position)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-4A1B08CC - AST call getBlockEntity in CraftBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlock.java:617`
- Context: `CraftBlock#getDrops`
- Receiver: `this.world`
- Method: `getBlockEntity`
- Evidence: `this.world.getBlockEntity(this.position)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-AE3F167F - AST call getBlockEntity in CraftBlockEntityState.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlockEntityState.java:135`
- Context: `CraftBlockEntityState#getBlockEntityFromWorld`
- Receiver: `this.getWorldHandle()`
- Method: `getBlockEntity`
- Evidence: `this.getWorldHandle().getBlockEntity(this.getPosition())`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-96C4300C - AST call getBlockEntity in CraftBlockEntityState.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlockEntityState.java:194`
- Context: `CraftBlockEntityState#update`
- Receiver: `this.getWorldHandle()`
- Method: `getBlockEntity`
- Evidence: `this.getWorldHandle().getBlockEntity(this.getPosition(), this.blockEntity.getType())`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-6417C019 - AST call getBlockEntity in CraftBlockEntityState.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlockEntityState.java:207`
- Context: `CraftBlockEntityState#place`
- Receiver: `this.getWorldHandle()`
- Method: `getBlockEntity`
- Evidence: `this.getWorldHandle().getBlockEntity(this.getPosition(), this.blockEntity.getType())`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-B76D8B6D - AST call getBlockEntity in CraftBlockState.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlockState.java:381`
- Context: `CraftBlockState#getDrops`
- Receiver: `this.world.getHandle()`
- Method: `getBlockEntity`
- Evidence: `this.world.getHandle().getBlockEntity(this.position)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-CA7494E6 - AST call getBlockState in CraftEntityTypes.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftEntityTypes.java:470`
- Context: `CraftEntityTypes#(initializer)`
- Receiver: `spawnData.world()`
- Method: `getBlockState`
- Evidence: `spawnData.world().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9C649397 - AST call getBlockState in CraftEntityTypes.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftEntityTypes.java:564`
- Context: `CraftEntityTypes#createHanging`
- Receiver: `spawnData.world()`
- Method: `getBlockState`
- Evidence: `spawnData.world().getBlockState(pos.relative(CraftBlock.blockFaceToNotch(dir)))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-ABC58F5E - AST call getBlockState in CraftHumanEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftHumanEntity.java:197`
- Context: `CraftHumanEntity#sleep`
- Receiver: `this.getHandle().level()`
- Method: `getBlockState`
- Evidence: `this.getHandle().level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1A3B49C4 - AST call getBlockState in CraftVillager.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftVillager.java:138`
- Context: `CraftVillager#sleep`
- Receiver: `this.getHandle().level()`
- Method: `getBlockState`
- Evidence: `this.getHandle().level().getBlockState(position)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F52D1CD8 - AST call getBlockEntity in CraftEventFactory.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/event/CraftEventFactory.java:2366`
- Context: `CraftEventFactory#callBlockLockCheckEvent`
- Receiver: `blockEntity.getLevel()`
- Method: `getBlockEntity`
- Evidence: `blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos())`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-E57A829E - AST call setBlockEntity in CraftChunkData.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: set-block-entity
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/generator/CraftChunkData.java:178`
- Context: `CraftChunkData#setBlock`
- Receiver: `access`
- Method: `setBlockEntity`
- Evidence: `access.setBlockEntity(blockEntity)`
- Suggested fix: Ensure the target chunk is loaded and owned, or resume on the owning region before mutation.

### AO-AST-5CB1A01B - AST call setBlockEntity in CustomChunkGenerator.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: set-block-entity
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/generator/CustomChunkGenerator.java:220`
- Context: `CustomChunkGenerator#buildSurface`
- Receiver: `chunk`
- Method: `setBlockEntity`
- Evidence: `chunk.setBlockEntity(blockEntity)`
- Suggested fix: Ensure the target chunk is loaded and owned, or resume on the owning region before mutation.

### AO-AST-E2137791 - AST call getBlockEntity in CraftBlockEntityInventoryViewBuilder.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/inventory/view/builder/CraftBlockEntityInventoryViewBuilder.java:53`
- Context: `CraftBlockEntityInventoryViewBuilder#buildContainer`
- Receiver: `this.world`
- Method: `getBlockEntity`
- Evidence: `this.world.getBlockEntity(position)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-F3E36BA6 - AST call getBlockEntity in CraftEnchantmentInventoryViewBuilder.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/inventory/view/builder/CraftEnchantmentInventoryViewBuilder.java:31`
- Context: `CraftEnchantmentInventoryViewBuilder#buildContainer`
- Receiver: `this.world`
- Method: `getBlockEntity`
- Evidence: `this.world.getBlockEntity(position)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-9336DB40 - AST call getBlockEntity in BlockStateListPopulator.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/util/BlockStateListPopulator.java:51`
- Context: `BlockStateListPopulator#getBlockEntity`
- Receiver: `this.level`
- Method: `getBlockEntity`
- Evidence: `this.level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-A655501D - AST call setBlockEntity in ShreddedPaperAccess.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: set-block-entity
- Location: `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ownership/ShreddedPaperAccess.java:382`
- Context: `ShreddedPaperAccess#writeLoadedBlockEntity`
- Receiver: `chunk`
- Method: `setBlockEntity`
- Evidence: `chunk.setBlockEntity(blockEntity)`
- Suggested fix: Ensure the target chunk is loaded and owned, or resume on the owning region before mutation.

### AO-AST-FC6903A3 - AST call getBlockState in ComparatorTracking.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/main/java/net/caffeinemc/mods/lithium/common/block/entity/inventory_comparator_tracking/ComparatorTracking.java:20`
- Context: `ComparatorTracking#notifyNearbyBlockEntitiesAboutNewComparator`
- Receiver: `world`
- Method: `getBlockState`
- Evidence: `world.getBlockState(searchPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D0BBBA63 - AST call getBlockState in ComparatorTracking.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/main/java/net/caffeinemc/mods/lithium/common/block/entity/inventory_comparator_tracking/ComparatorTracking.java:37`
- Context: `ComparatorTracking#findNearbyComparators`
- Receiver: `world`
- Method: `getBlockState`
- Evidence: `world.getBlockState(searchPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-03453731 - AST call getBlockEntity in BlockPredicate.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/advancements/criterion/BlockPredicate.java:58`
- Context: `BlockPredicate#matches`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-66C5185B - AST call getBlockEntity in BlockInput.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/commands/arguments/blocks/BlockInput.java:80`
- Context: `BlockInput#place`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-BD7B3E72 - AST call getBlockEntity in DispenseItemBehavior.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/DispenseItemBehavior.java:513`
- Context: `#execute`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-17F1CFC4 - AST call getBlockEntity in ShearsDispenseItemBehavior.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/ShearsDispenseItemBehavior.java:63`
- Context: `ShearsDispenseItemBehavior#tryShearBeehive`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-E4C913B1 - AST call getBlockEntity in GameTestHelper.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/GameTestHelper.java:104`
- Context: `GameTestHelper#getBlockEntity`
- Receiver: `this.getLevel()`
- Method: `getBlockEntity`
- Evidence: `this.getLevel().getBlockEntity(this.absolutePos(pos))`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-AC6149B9 - AST call getBlockEntity in GameTestInfo.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/GameTestInfo.java:196`
- Context: `GameTestInfo#getTestInstanceBlockEntity`
- Receiver: `this.level`
- Method: `getBlockEntity`
- Evidence: `this.level.getBlockEntity(this.testBlockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-85F8E920 - AST call getBlockEntity in GameTestInfo.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/GameTestInfo.java:287`
- Context: `GameTestInfo#createTestInstanceBlock`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-586B0497 - AST call getBlockEntity in ReportGameListener.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/ReportGameListener.java:130`
- Context: `ReportGameListener#getTestInstanceBlockEntity`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos, BlockEntityType.TEST_INSTANCE_BLOCK)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-F75CBA8A - AST call getBlockEntity in StructureUtils.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/StructureUtils.java:73`
- Context: `StructureUtils#createNewEmptyTest`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-BE5C3F80 - AST call getBlockEntity in StructureUtils.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/StructureUtils.java:124`
- Context: `StructureUtils#lookedAtTestPos`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos, BlockEntityType.TEST_INSTANCE_BLOCK)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-7345C49D - AST call getBlockEntity in StructureUtils.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/StructureUtils.java:146`
- Context: `StructureUtils#doesStructureContain`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(structureBlockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-B1C3C646 - AST call getBlockEntity in TestCommand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/TestCommand.java:111`
- Context: `TestCommand#clear`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos, BlockEntityType.TEST_INSTANCE_BLOCK)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-7EFDDE8C - AST call getBlockEntity in TestCommand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/TestCommand.java:136`
- Context: `TestCommand#export`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-67AE6FD8 - AST call getBlockEntity in TestCommand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/TestCommand.java:222`
- Context: `TestCommand#locate`
- Receiver: `testFinder.source().getLevel()`
- Method: `getBlockEntity`
- Evidence: `testFinder.source().getLevel().getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-DED04162 - AST call getBlockEntity in TestCommand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/TestCommand.java:491`
- Context: `TestCommand#createGameTestInfo`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-5D11ECBB - AST call getBlockEntity in TestCommand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/TestCommand.java:538`
- Context: `TestCommand#showPos`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(optional.get())`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-B43715BE - AST call getBlockEntity in BlockDataSource.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/network/chat/contents/data/BlockDataSource.java:41`
- Context: `BlockDataSource#getData`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-6001E2D3 - AST call getBlockState in ClientboundBlockUpdatePacket.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/network/protocol/game/ClientboundBlockUpdatePacket.java:30`
- Context: `ClientboundBlockUpdatePacket#<init>`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0852CCDE - AST call getBlockEntity in CloneCommands.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/CloneCommands.java:260`
- Context: `CloneCommands#clone`
- Receiver: `serverLevel`
- Method: `getBlockEntity`
- Evidence: `serverLevel.getBlockEntity(blockPos5)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-41A0DCAF - AST call getBlockEntity in CloneCommands.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/CloneCommands.java:315`
- Context: `CloneCommands#clone`
- Receiver: `serverLevel1`
- Method: `getBlockEntity`
- Evidence: `serverLevel1.getBlockEntity(cloneBlockInfox.pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-2AF3E497 - AST call getBlockEntity in ExecuteCommand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/ExecuteCommand.java:977`
- Context: `ExecuteCommand#checkRegions`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos1)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-051D9602 - AST call getBlockEntity in ExecuteCommand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/ExecuteCommand.java:978`
- Context: `ExecuteCommand#checkRegions`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos2)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-425856B0 - AST call getBlockEntity in ItemCommands.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/ItemCommands.java:372`
- Context: `ItemCommands#getContainer`
- Receiver: `source.getLevel()`
- Method: `getBlockEntity`
- Evidence: `source.getLevel().getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-1311A9DB - AST call getBlockEntity in LootCommand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/LootCommand.java:271`
- Context: `LootCommand#getContainer`
- Receiver: `source.getLevel()`
- Method: `getBlockEntity`
- Evidence: `source.getLevel().getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-F2A0072D - AST call getBlockEntity in LootCommand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/LootCommand.java:439`
- Context: `LootCommand#dropBlockLoot`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-DD25935B - AST call getBlockEntity in BlockDataAccessor.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/data/BlockDataAccessor.java:33`
- Context: `#access`
- Receiver: `context.getSource().getLevel()`
- Method: `getBlockEntity`
- Evidence: `context.getSource().getLevel().getBlockEntity(loadedBlockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-D0A62201 - AST call getBlockState in BlockDataAccessor.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/data/BlockDataAccessor.java:58`
- Context: `BlockDataAccessor#setData`
- Receiver: `this.entity.getLevel()`
- Method: `getBlockState`
- Evidence: `this.entity.getLevel().getBlockState(this.pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7A7A6811 - AST call getBlockEntity in ChunkHolder.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ChunkHolder.java:358`
- Context: `ChunkHolder#broadcastBlockEntity`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-5DE61C72 - AST call getBlockState in ServerPlayer.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayer.java:1482`
- Context: `ServerPlayer#findRespawnAndUseSpawnBlockAsync`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F82FF21E - AST call getBlockState in ServerPlayer.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayer.java:1493`
- Context: `ServerPlayer#findRespawnAndUseSpawnBlockAsync`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-09E13184 - AST call getBlockState in ServerPlayer.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayer.java:1511`
- Context: `ServerPlayer#findRespawnAndUseSpawnBlockAsync`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-48347FCF - AST call getBlockEntity in ServerPlayer.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayer.java:1550`
- Context: `ServerPlayer#teleport`
- Receiver: `this.level()`
- Method: `getBlockEntity`
- Evidence: `this.level().getBlockEntity(this.portalProcess.getEntryPosition())`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-B175EBDE - AST call getBlockState in ServerPlayer.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayer.java:1730`
- Context: `ServerPlayer#startSleepInBed`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(bedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-596168A2 - AST call getBlockState in ServerPlayerGameMode.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayerGameMode.java:185`
- Context: `ServerPlayerGameMode#handleBlockBreakAction`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-800DFB5E - AST call getBlockState in ServerPlayerGameMode.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayerGameMode.java:192`
- Context: `ServerPlayerGameMode#handleBlockBreakAction`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-778CEDAF - AST call getBlockState in ServerPlayerGameMode.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayerGameMode.java:214`
- Context: `ServerPlayerGameMode#handleBlockBreakAction`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-295C1E09 - AST call getBlockState in ServerPlayerGameMode.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayerGameMode.java:220`
- Context: `ServerPlayerGameMode#handleBlockBreakAction`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-35240E86 - AST call getBlockState in ServerPlayerGameMode.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayerGameMode.java:227`
- Context: `ServerPlayerGameMode#handleBlockBreakAction`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-536EF796 - AST call getBlockState in ServerPlayerGameMode.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayerGameMode.java:264`
- Context: `ServerPlayerGameMode#handleBlockBreakAction`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(this.destroyPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A005A864 - AST call getBlockState in ServerPlayerGameMode.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayerGameMode.java:278`
- Context: `ServerPlayerGameMode#handleBlockBreakAction`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3A7281FE - AST call getBlockState in ServerPlayerGameMode.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayerGameMode.java:325`
- Context: `ServerPlayerGameMode#destroyAndAck`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-424FCFB6 - AST call getBlockState in ServerPlayerGameMode.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayerGameMode.java:331`
- Context: `ServerPlayerGameMode#destroyBlock`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4E308C5C - AST call getBlockState in ServerPlayerGameMode.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayerGameMode.java:344`
- Context: `ServerPlayerGameMode#destroyBlock`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A967F149 - AST call getBlockEntity in ServerPlayerGameMode.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayerGameMode.java:361`
- Context: `ServerPlayerGameMode#destroyBlock`
- Receiver: `this.level`
- Method: `getBlockEntity`
- Evidence: `this.level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-248D77E8 - AST call getBlockState in ServerPlayerGameMode.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayerGameMode.java:377`
- Context: `ServerPlayerGameMode#destroyBlock`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-42CF12E5 - AST call getBlockEntity in ServerPlayerGameMode.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayerGameMode.java:379`
- Context: `ServerPlayerGameMode#destroyBlock`
- Receiver: `this.level`
- Method: `getBlockEntity`
- Evidence: `this.level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-043FBD87 - AST call getBlockState in ServerPlayerGameMode.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayerGameMode.java:394`
- Context: `ServerPlayerGameMode#destroyBlock`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A8BDEAD4 - AST call getBlockState in ServerPlayerGameMode.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayerGameMode.java:505`
- Context: `ServerPlayerGameMode#useItemOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-83692533 - AST call setBlockEntity in WorldGenRegion.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: set-block-entity
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/WorldGenRegion.java:282`
- Context: `WorldGenRegion#getBlockEntity`
- Receiver: `chunk`
- Method: `setBlockEntity`
- Evidence: `chunk.setBlockEntity(blockEntity)`
- Suggested fix: Ensure the target chunk is loaded and owned, or resume on the owning region before mutation.

### AO-AST-2B86A775 - AST call setBlockEntity in WorldGenRegion.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: set-block-entity
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/WorldGenRegion.java:352`
- Context: `WorldGenRegion#setBlock`
- Receiver: `chunk`
- Method: `setBlockEntity`
- Evidence: `chunk.setBlockEntity(blockEntity)`
- Suggested fix: Ensure the target chunk is loaded and owned, or resume on the owning region before mutation.

### AO-AST-5CBB0FD5 - AST call getBlockEntity in ServerGamePacketListenerImpl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:1011`
- Context: `ServerGamePacketListenerImpl#handleSetCommandBlock`
- Receiver: `this.player.level()`
- Method: `getBlockEntity`
- Evidence: `this.player.level().getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-E435CC20 - AST call getBlockState in ServerGamePacketListenerImpl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:1021`
- Context: `ServerGamePacketListenerImpl#handleSetCommandBlock`
- Receiver: `this.player.level()`
- Method: `getBlockState`
- Evidence: `this.player.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5729884A - AST call getBlockState in ServerGamePacketListenerImpl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:1100`
- Context: `ServerGamePacketListenerImpl#handlePickItemFromBlock`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A9C44496 - AST call getBlockEntity in ServerGamePacketListenerImpl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:1115`
- Context: `ServerGamePacketListenerImpl#addBlockDataToItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-1BFEF981 - AST call getBlockState in ServerGamePacketListenerImpl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:1203`
- Context: `ServerGamePacketListenerImpl#handleSetStructureBlock`
- Receiver: `this.player.level()`
- Method: `getBlockState`
- Evidence: `this.player.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D60EA98A - AST call getBlockEntity in ServerGamePacketListenerImpl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:1204`
- Context: `ServerGamePacketListenerImpl#handleSetStructureBlock`
- Receiver: `this.player.level()`
- Method: `getBlockEntity`
- Evidence: `this.player.level().getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-7D7D6397 - AST call getBlockState in ServerGamePacketListenerImpl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:1256`
- Context: `ServerGamePacketListenerImpl#handleSetTestBlock`
- Receiver: `this.player.level()`
- Method: `getBlockState`
- Evidence: `this.player.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DA4917C6 - AST call getBlockEntity in ServerGamePacketListenerImpl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:1257`
- Context: `ServerGamePacketListenerImpl#handleSetTestBlock`
- Receiver: `this.player.level()`
- Method: `getBlockEntity`
- Evidence: `this.player.level().getBlockEntity(blockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-650F403A - AST call getBlockEntity in ServerGamePacketListenerImpl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:1270`
- Context: `ServerGamePacketListenerImpl#handleTestInstanceBlockAction`
- Receiver: `this.player.level()`
- Method: `getBlockEntity`
- Evidence: `this.player.level().getBlockEntity(blockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-DC614CBD - AST call getBlockState in ServerGamePacketListenerImpl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:1284`
- Context: `ServerGamePacketListenerImpl#handleTestInstanceBlockAction`
- Receiver: `this.player.level()`
- Method: `getBlockState`
- Evidence: `this.player.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-84D55418 - AST call getBlockState in ServerGamePacketListenerImpl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:1315`
- Context: `ServerGamePacketListenerImpl#handleSetJigsawBlock`
- Receiver: `this.player.level()`
- Method: `getBlockState`
- Evidence: `this.player.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-736AF1E9 - AST call getBlockEntity in ServerGamePacketListenerImpl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:1316`
- Context: `ServerGamePacketListenerImpl#handleSetJigsawBlock`
- Receiver: `this.player.level()`
- Method: `getBlockEntity`
- Evidence: `this.player.level().getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-20DFA5D5 - AST call getBlockEntity in ServerGamePacketListenerImpl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:1335`
- Context: `ServerGamePacketListenerImpl#handleJigsawGenerate`
- Receiver: `this.player.level()`
- Method: `getBlockEntity`
- Evidence: `this.player.level().getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-AC682EE3 - AST call getBlockEntity in ServerGamePacketListenerImpl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:1512`
- Context: `ServerGamePacketListenerImpl#handleBlockEntityTagQuery`
- Receiver: `this.player.level()`
- Method: `getBlockEntity`
- Evidence: `this.player.level().getBlockEntity(packet.getPos())`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-69D8D212 - AST call getBlockEntity in ServerGamePacketListenerImpl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:2170`
- Context: `ServerGamePacketListenerImpl#handlePlayerAction`
- Receiver: `this.player.level()`
- Method: `getBlockEntity`
- Evidence: `this.player.level().getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-CD78B54B - AST call getBlockEntity in ServerGamePacketListenerImpl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:3780`
- Context: `ServerGamePacketListenerImpl#updateSignText`
- Receiver: `serverLevel`
- Method: `getBlockEntity`
- Evidence: `serverLevel.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-90CABC4B - AST call getBlockState in PlayerList.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/players/PlayerList.java:673`
- Context: `PlayerList#respawnAsync`
- Receiver: `level1`
- Method: `getBlockState`
- Evidence: `level1.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1C62FBA9 - AST call getBlockEntity in Container.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/Container.java:95`
- Context: `Container#stillValidBlockEntity`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-28EEBA12 - AST call getBlockEntity in RandomizableContainer.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/RandomizableContainer.java:43`
- Context: `RandomizableContainer#setBlockEntityLootTable`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-F006DD04 - AST call getBlockState in FallLocation.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/damagesource/FallLocation.java:38`
- Context: `FallLocation#getCurrentFallLocation`
- Receiver: `entity.level()`
- Method: `getBlockState`
- Evidence: `entity.level().getBlockState(lastClimbablePos.get())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-786AC823 - AST call getFluidState in Entity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/Entity.java:2132`
- Context: `Entity#updateSwimming`
- Receiver: `this.level()`
- Method: `getFluidState`
- Evidence: `this.level().getFluidState(this.blockPosition)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-2E1BD66F - AST call getBlockEntity in Entity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/Entity.java:4232`
- Context: `Entity#teleport`
- Receiver: `this.level`
- Method: `getBlockEntity`
- Evidence: `this.level.getBlockEntity(this.portalProcess.getEntryPosition())`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-9F49EA30 - AST call getFluidState in ExperienceOrb.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ExperienceOrb.java:159`
- Context: `ExperienceOrb#tick`
- Receiver: `this.level()`
- Method: `getFluidState`
- Evidence: `this.level().getFluidState(this.blockPosition())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-8D0812CE - AST call getBlockState in ExperienceOrb.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ExperienceOrb.java:183`
- Context: `ExperienceOrb#tick`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5FB0536F - AST call getBlockState in Leashable.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/Leashable.java:209`
- Context: `Leashable#angularFriction`
- Receiver: `entity.level()`
- Method: `getBlockState`
- Evidence: `entity.level().getBlockState(entity.getBlockPosBelowThatAffectsMyMovement())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-68717419 - AST call getBlockState in LightningBolt.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/LightningBolt.java:70`
- Context: `LightningBolt#powerLightningRod`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(strikePosition)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5D3A49D8 - AST call getBlockState in LightningBolt.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/LightningBolt.java:175`
- Context: `LightningBolt#spawnFire`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(var7)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E612DCA2 - AST call getBlockState in LightningBolt.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/LightningBolt.java:185`
- Context: `LightningBolt#spawnFire`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-95960331 - AST call getBlockState in LightningBolt.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/LightningBolt.java:197`
- Context: `LightningBolt#clearCopperOnLightningStrike`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5FEBB31C - AST call getBlockState in LightningBolt.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/LightningBolt.java:203`
- Context: `LightningBolt#clearCopperOnLightningStrike`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E36902F6 - AST call getBlockState in LightningBolt.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/LightningBolt.java:235`
- Context: `LightningBolt#randomStepCleaningCopper`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8C935595 - AST call getBlockState in LivingEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/LivingEntity.java:467`
- Context: `LivingEntity#baseTick`
- Receiver: `serverLevel1`
- Method: `getBlockState`
- Evidence: `serverLevel1.getBlockState(BlockPos.containing(this.getX(), this.getEyeY(), this.getZ()))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BAA23344 - AST call getBlockState in LivingEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/LivingEntity.java:1985`
- Context: `LivingEntity#createWitherRose`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0D2DB029 - AST call getBlockState in LivingEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/LivingEntity.java:2279`
- Context: `LivingEntity#trapdoorUsableAsLadder`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8AC08939 - AST call getBlockState in LivingEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/LivingEntity.java:2352`
- Context: `LivingEntity#playBlockFallSound`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(new BlockPos(floor, floor1, floor2))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4678964B - AST call getBlockState in LivingEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/LivingEntity.java:3017`
- Context: `LivingEntity#dismountVehicle`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(vehicle.blockPosition())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0DE6F0A3 - AST call getBlockState in LivingEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/LivingEntity.java:4611`
- Context: `LivingEntity#randomTeleport`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-91340A69 - AST call getBlockState in LivingEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/LivingEntity.java:4738`
- Context: `LivingEntity#startSleeping`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-673709E6 - AST call getBlockState in LivingEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/LivingEntity.java:4755`
- Context: `LivingEntity#checkBedExists`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6BE38AD5 - AST call getBlockState in LivingEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/LivingEntity.java:4760`
- Context: `LivingEntity#stopSleeping`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B7277440 - AST call getBlockState in Mob.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/Mob.java:922`
- Context: `Mob#checkMobSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-940EFA6F - AST call getBlockState in SpawnPlacementTypes.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/SpawnPlacementTypes.java:65`
- Context: `SpawnPlacementTypes#shreddedpaper$getBlockStateIfLoaded`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5BE01F2A - AST call getBlockState in AnimalPanic.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/AnimalPanic.java:110`
- Context: `AnimalPanic#lookForWater`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-273FF560 - AST call getFluidState in AnimalPanic.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/AnimalPanic.java:115`
- Context: `AnimalPanic#lookForWater`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos2)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-8959DA26 - AST call getFluidState in AnimalPanic.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/AnimalPanic.java:117`
- Context: `AnimalPanic#lookForWater`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos1)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-D93680CE - AST call getBlockState in BehaviorUtils.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/BehaviorUtils.java:155`
- Context: `BehaviorUtils#getRandomSwimmablePos`
- Receiver: `pathfinder.level()`
- Method: `getBlockState`
- Evidence: `pathfinder.level().getBlockState(BlockPos.containing(pos))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-70595591 - AST call getBlockState in HarvestFarmland.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/HarvestFarmland.java:83`
- Context: `HarvestFarmland#validPos`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-ACF25692 - AST call getBlockState in HarvestFarmland.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/HarvestFarmland.java:85`
- Context: `HarvestFarmland#validPos`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8ABAD74F - AST call getBlockState in HarvestFarmland.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/HarvestFarmland.java:110`
- Context: `HarvestFarmland#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(this.aboveFarmlandPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-70BF2B1A - AST call getBlockState in HarvestFarmland.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/HarvestFarmland.java:112`
- Context: `HarvestFarmland#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(this.aboveFarmlandPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6E444EDB - AST call getBlockState in InteractWithDoor.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/InteractWithDoor.java:57`
- Context: `InteractWithDoor#create`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2F3B8362 - AST call getBlockState in InteractWithDoor.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/InteractWithDoor.java:74`
- Context: `InteractWithDoor#create`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B9AF7759 - AST call getBlockState in InteractWithDoor.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/InteractWithDoor.java:120`
- Context: `InteractWithDoor#closeDoorsThatIHaveOpenedOrPassedThrough`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1AF036F7 - AST call getBlockState in JumpOnBed.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/JumpOnBed.java:104`
- Context: `JumpOnBed#isBed`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-84CB17B4 - AST call getBlockState in LongJumpToPreferredBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/LongJumpToPreferredBlock.java:56`
- Context: `LongJumpToPreferredBlock#getJumpCandidate`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.setWithOffset(possibleJump.targetPos(), Direction.DOWN))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-35163EBF - AST call getBlockState in LongJumpToRandomPos.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/LongJumpToRandomPos.java:57`
- Context: `LongJumpToRandomPos#defaultAcceptableLandingSpot`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-ED29F76D - AST call getBlockState in LongJumpToRandomPos.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/LongJumpToRandomPos.java:89`
- Context: `LongJumpToRandomPos#checkExtraStartConditions`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(owner.blockPosition())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CE8D40C4 - AST call getBlockState in RamTarget.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/RamTarget.java:120`
- Context: `RamTarget#hasRammedHornBreakingBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-855F0534 - AST call getBlockState in RamTarget.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/RamTarget.java:120`
- Context: `RamTarget#hasRammedHornBreakingBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C6E5801F - AST call getFluidState in RandomStroll.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/RandomStroll.java:69`
- Context: `RandomStroll#getTargetSwimPos`
- Receiver: `mob.level()`
- Method: `getFluidState`
- Evidence: `mob.level().getFluidState(BlockPos.containing(vec31))`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-1D00305B - AST call getBlockState in RingBell.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/RingBell.java:23`
- Context: `RingBell#create`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-EF19EBD9 - AST call getBlockEntity in TransportItemsBetweenContainers.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.java:380`
- Context: `TransportItemsBetweenContainers#targetHasNotChanged`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(target.pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-18FABEA9 - AST call getBlockEntity in TransportItemsBetweenContainers.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.java:639`
- Context: `TransportItemTarget#tryCreatePossibleTarget`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-0503ABF4 - AST call getFluidState in TryFindLand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TryFindLand.java:29`
- Context: `TryFindLand#create`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(mob.blockPosition())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-9067177D - AST call getBlockState in TryFindLand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TryFindLand.java:41`
- Context: `TryFindLand#create`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2ECF1C38 - AST call getBlockState in TryFindLand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TryFindLand.java:42`
- Context: `TryFindLand#create`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.setWithOffset(blockPos1, Direction.DOWN))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E168AD50 - AST call getFluidState in TryFindLand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TryFindLand.java:44`
- Context: `TryFindLand#create`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos1)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-345C95CD - AST call getFluidState in TryFindLandNearWater.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TryFindLandNearWater.java:26`
- Context: `TryFindLandNearWater#create`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(mob.blockPosition())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-04BD3DB9 - AST call getBlockState in TryFindLandNearWater.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TryFindLandNearWater.java:39`
- Context: `TryFindLandNearWater#create`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-34C08404 - AST call getBlockState in TryFindLandNearWater.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TryFindLandNearWater.java:40`
- Context: `TryFindLandNearWater#create`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.setWithOffset(blockPos1, Direction.DOWN))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3A5A7354 - AST call getBlockState in TryFindLandNearWater.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TryFindLandNearWater.java:45`
- Context: `TryFindLandNearWater#create`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-57C43C99 - AST call getBlockState in TryFindLandNearWater.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TryFindLandNearWater.java:46`
- Context: `TryFindLandNearWater#create`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.move(Direction.DOWN))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-818808DA - AST call getFluidState in TryFindWater.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TryFindWater.java:23`
- Context: `TryFindWater#create`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(mob.blockPosition())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-F56072B3 - AST call getBlockState in TryFindWater.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TryFindWater.java:35`
- Context: `TryFindWater#create`
- Receiver: `mob.level()`
- Method: `getBlockState`
- Evidence: `mob.level().getBlockState(blockPos3.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BB976393 - AST call getBlockState in TryFindWater.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TryFindWater.java:36`
- Context: `TryFindWater#create`
- Receiver: `mob.level()`
- Method: `getBlockState`
- Evidence: `mob.level().getBlockState(blockPos3)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7513EA76 - AST call getBlockState in TryLaySpawnOnWaterNearLand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TryLaySpawnOnWaterNearLand.java:31`
- Context: `TryLaySpawnOnWaterNearLand#create`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4B82AB59 - AST call getFluidState in TryLaySpawnOnWaterNearLand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TryLaySpawnOnWaterNearLand.java:32`
- Context: `TryLaySpawnOnWaterNearLand#create`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos1)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-059F2EB3 - AST call getBlockState in TryLaySpawnOnWaterNearLand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TryLaySpawnOnWaterNearLand.java:34`
- Context: `TryLaySpawnOnWaterNearLand#create`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos2)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-70DBE958 - AST call getBlockState in UseBonemeal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/UseBonemeal.java:73`
- Context: `UseBonemeal#validPos`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DA2D4D7D - AST call getBlockState in ValidateNearbyPoi.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/ValidateNearbyPoi.java:46`
- Context: `ValidateNearbyPoi#bedIsOccupied`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-29E9B7B0 - AST call getBlockState in VillagerGoalPackages.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java:75`
- Context: `VillagerGoalPackages#validateBedPoi`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5F9294F4 - AST call getBlockState in WorkAtComposter.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/WorkAtComposter.java:28`
- Context: `WorkAtComposter#useWorkstation`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(globalPos.pos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A05EDEDB - AST call getBlockState in MoveControl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/control/MoveControl.java:113`
- Context: `MoveControl#tick`
- Receiver: `this.mob.level()`
- Method: `getBlockState`
- Evidence: `this.mob.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A4AB06CD - AST call getFluidState in BreakDoorGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/BreakDoorGoal.java:78`
- Context: `BreakDoorGoal#tick`
- Receiver: `this.mob.level()`
- Method: `getFluidState`
- Evidence: `this.mob.level().getFluidState(this.doorPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-29787329 - AST call getBlockState in BreakDoorGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/BreakDoorGoal.java:83`
- Context: `BreakDoorGoal#tick`
- Receiver: `this.mob.level()`
- Method: `getBlockState`
- Evidence: `this.mob.level().getBlockState(this.doorPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DF791176 - AST call getBlockState in BreathAirGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/BreathAirGoal.java:75`
- Context: `BreathAirGoal#givesAir`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-676372ED - AST call getFluidState in BreathAirGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/BreathAirGoal.java:76`
- Context: `BreathAirGoal#givesAir`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-C5E3F9C9 - AST call getBlockState in CatLieOnBedGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/CatLieOnBedGoal.java:55`
- Context: `CatLieOnBedGoal#isValidTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-27084A74 - AST call getBlockState in CatSitOnBlockGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/CatSitOnBlockGoal.java:50`
- Context: `CatSitOnBlockGoal#isValidTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A1CBED0F - AST call getBlockState in ClimbOnTopOfPowderSnowGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/ClimbOnTopOfPowderSnowGoal.java:27`
- Context: `ClimbOnTopOfPowderSnowGoal#canUse`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F685DFA3 - AST call getFluidState in DolphinJumpGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/DolphinJumpGoal.java:45`
- Context: `DolphinJumpGoal#waterIsClear`
- Receiver: `this.dolphin.level()`
- Method: `getFluidState`
- Evidence: `this.dolphin.level().getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-BB44754A - AST call getBlockState in DolphinJumpGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/DolphinJumpGoal.java:45`
- Context: `DolphinJumpGoal#waterIsClear`
- Receiver: `this.dolphin.level()`
- Method: `getBlockState`
- Evidence: `this.dolphin.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5EBFDD30 - AST call getBlockState in DolphinJumpGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/DolphinJumpGoal.java:49`
- Context: `DolphinJumpGoal#surfaceIsClear`
- Receiver: `this.dolphin.level()`
- Method: `getBlockState`
- Evidence: `this.dolphin.level().getBlockState(pos.offset(dx * scale, 1, dz * scale))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6C6B23E8 - AST call getBlockState in DolphinJumpGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/DolphinJumpGoal.java:50`
- Context: `DolphinJumpGoal#surfaceIsClear`
- Receiver: `this.dolphin.level()`
- Method: `getBlockState`
- Evidence: `this.dolphin.level().getBlockState(pos.offset(dx * scale, 2, dz * scale))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D30D0061 - AST call getFluidState in DolphinJumpGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/DolphinJumpGoal.java:81`
- Context: `DolphinJumpGoal#tick`
- Receiver: `this.dolphin.level()`
- Method: `getFluidState`
- Evidence: `this.dolphin.level().getFluidState(this.dolphin.blockPosition())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-16DBADBE - AST call getBlockState in DoorInteractGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/DoorInteractGoal.java:30`
- Context: `DoorInteractGoal#isOpen`
- Receiver: `this.mob.level()`
- Method: `getBlockState`
- Evidence: `this.mob.level().getBlockState(this.doorPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B2853158 - AST call getBlockState in DoorInteractGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/DoorInteractGoal.java:42`
- Context: `DoorInteractGoal#setOpen`
- Receiver: `this.mob.level()`
- Method: `getBlockState`
- Evidence: `this.mob.level().getBlockState(this.doorPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D6A5D29B - AST call getBlockState in EatBlockGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/EatBlockGoal.java:35`
- Context: `EatBlockGoal#canUse`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B7C62776 - AST call getBlockState in EatBlockGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/EatBlockGoal.java:35`
- Context: `EatBlockGoal#canUse`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-61A0F543 - AST call getBlockState in EatBlockGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/EatBlockGoal.java:65`
- Context: `EatBlockGoal#tick`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F5F64364 - AST call getBlockState in EatBlockGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/EatBlockGoal.java:74`
- Context: `EatBlockGoal#tick`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A834F9D2 - AST call getBlockState in PanicGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/PanicGoal.java:99`
- Context: `PanicGoal#lookForWater`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DB03AC4B - AST call getFluidState in PanicGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/PanicGoal.java:101`
- Context: `PanicGoal#lookForWater`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos1)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-A7A7AB99 - AST call getFluidState in TryFindWaterGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/TryFindWaterGoal.java:17`
- Context: `TryFindWaterGoal#canUse`
- Receiver: `this.mob.level()`
- Method: `getFluidState`
- Evidence: `this.mob.level().getFluidState(this.mob.blockPosition())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-27AB8514 - AST call getFluidState in TryFindWaterGoal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/TryFindWaterGoal.java:32`
- Context: `TryFindWaterGoal#start`
- Receiver: `this.mob.level()`
- Method: `getFluidState`
- Evidence: `this.mob.level().getFluidState(blockPos1)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-7898CE7D - AST call getBlockState in AmphibiousPathNavigation.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/navigation/AmphibiousPathNavigation.java:59`
- Context: `AmphibiousPathNavigation#isStableDestination`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CC9BE50F - AST call getBlockState in FlyingPathNavigation.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/navigation/FlyingPathNavigation.java:89`
- Context: `FlyingPathNavigation#isStableDestination`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3EF1BD38 - AST call getBlockState in GroundPathNavigation.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/navigation/GroundPathNavigation.java:116`
- Context: `GroundPathNavigation#getSurfaceY`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(BlockPos.containing(this.mob.getX(), blockY, this.mob.getZ()))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-836209F2 - AST call getBlockState in GroundPathNavigation.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/navigation/GroundPathNavigation.java:120`
- Context: `GroundPathNavigation#getSurfaceY`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(BlockPos.containing(this.mob.getX(), ++blockY, this.mob.getZ()))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-497C05CA - AST call getBlockState in PathNavigation.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/navigation/PathNavigation.java:444`
- Context: `PathNavigation#trimPath`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(new BlockPos(node.x, node.y, node.z))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3E735956 - AST call getBlockState in PathNavigation.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/navigation/PathNavigation.java:473`
- Context: `PathNavigation#isStableDestination`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CA5C1978 - AST call getBlockState in WaterBoundPathNavigation.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/navigation/WaterBoundPathNavigation.java:64`
- Context: `WaterBoundPathNavigation#isStableDestination`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5A8376A3 - AST call getBlockState in HoglinSpecificSensor.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/sensing/HoglinSpecificSensor.java:63`
- Context: `HoglinSpecificSensor#findNearestRepellent`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-EE2FAE8A - AST call getBlockState in PiglinSpecificSensor.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/sensing/PiglinSpecificSensor.java:123`
- Context: `PiglinSpecificSensor#isValidRepellent`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3BDE252F - AST call getBlockState in SecondaryPoiSensor.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/sensing/SecondaryPoiSensor.java:41`
- Context: `SecondaryPoiSensor#doTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-EF379CFB - AST call getFluidState in GoalUtils.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/util/GoalUtils.java:37`
- Context: `GoalUtils#isWater`
- Receiver: `mob.level()`
- Method: `getFluidState`
- Evidence: `mob.level().getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-527D05E3 - AST call getBlockState in GoalUtils.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ai/util/GoalUtils.java:45`
- Context: `GoalUtils#isSolid`
- Receiver: `mob.level()`
- Method: `getBlockState`
- Evidence: `mob.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-496D84AE - AST call getBlockState in Bat.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ambient/Bat.java:220`
- Context: `Bat#customServerAiStep`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-61EBF18E - AST call getBlockState in Bat.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ambient/Bat.java:264`
- Context: `Bat#customServerAiStep`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2E217644 - AST call getBlockState in Bat.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/ambient/Bat.java:315`
- Context: `Bat#checkBatSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-76922485 - AST call getFluidState in AgeableWaterCreature.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/AgeableWaterCreature.java:77`
- Context: `AgeableWaterCreature#checkSurfaceAgeableWaterCreatureSpawnRules`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.below())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-C1F4ED80 - AST call getBlockState in AgeableWaterCreature.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/AgeableWaterCreature.java:78`
- Context: `AgeableWaterCreature#checkSurfaceAgeableWaterCreatureSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-41710E1E - AST call getBlockState in Animal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/Animal.java:100`
- Context: `Animal#getWalkTargetValue`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F5FC455E - AST call getBlockState in Animal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/Animal.java:121`
- Context: `Animal#checkAnimalSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-392EBFAB - AST call getBlockState in Allay.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/allay/Allay.java:468`
- Context: `Allay#shouldStopDancing`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(this.jukeboxPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C1EE0BE3 - AST call getBlockState in AllayAi.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/allay/AllayAi.java:135`
- Context: `AllayAi#shouldDepositItemsAtLikedNoteblock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.pos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0664E1F4 - AST call getBlockState in Armadillo.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java:260`
- Context: `Armadillo#checkArmadilloSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E229661D - AST call getBlockState in Axolotl.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/axolotl/Axolotl.java:578`
- Context: `Axolotl#checkAxolotlSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2635413F - AST call getBlockState in Bee.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/bee/Bee.java:241`
- Context: `Bee#getWalkTargetValue`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9199E4DB - AST call getBlockEntity in Bee.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/bee/Bee.java:539`
- Context: `Bee#doesHiveHaveSpace`
- Receiver: `this.level()`
- Method: `getBlockEntity`
- Evidence: `this.level().getBlockEntity(hivePos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-7BE42475 - AST call getBlockEntity in Bee.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/bee/Bee.java:601`
- Context: `Bee#getBeehiveBlockEntity`
- Receiver: `this.level()`
- Method: `getBlockEntity`
- Evidence: `this.level().getBlockEntity(this.hivePos, BlockEntityType.BEEHIVE)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-32F349F2 - AST call getBlockState in Bee.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/bee/Bee.java:668`
- Context: `#isStableDestination`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B740C51B - AST call getBlockState in Bee.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/bee/Bee.java:912`
- Context: `BeeGoToHiveGoal#canBeeUse`
- Receiver: `Bee.this.level()`
- Method: `getBlockState`
- Evidence: `Bee.this.level().getBlockState(Bee.this.hivePos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8D2F5550 - AST call getBlockState in Bee.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/bee/Bee.java:1077`
- Context: `BeeGrowCropGoal#tick`
- Receiver: `Bee.this.level()`
- Method: `getBlockState`
- Evidence: `Bee.this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-20C442C5 - AST call getBlockState in Bee.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/bee/Bee.java:1099`
- Context: `BeeGrowCropGoal#tick`
- Receiver: `Bee.this.level()`
- Method: `getBlockState`
- Evidence: `Bee.this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9B45C9EB - AST call getBlockState in Bee.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/bee/Bee.java:1363`
- Context: `BeePollinateGoal#findNearbyFlower`
- Receiver: `Bee.this.level()`
- Method: `getBlockState`
- Evidence: `Bee.this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FBFF9B6F - AST call getBlockState in Bee.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/bee/Bee.java:1445`
- Context: `ValidateFlowerGoal#isFlower`
- Receiver: `Bee.this.level()`
- Method: `getBlockState`
- Evidence: `Bee.this.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0C0ABAA0 - AST call getBlockState in Camel.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/camel/Camel.java:151`
- Context: `Camel#checkCamelSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CDED8DB7 - AST call getBlockState in MushroomCow.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/cow/MushroomCow.java:111`
- Context: `MushroomCow#getWalkTargetValue`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A8AD4C4B - AST call getBlockState in MushroomCow.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/cow/MushroomCow.java:117`
- Context: `MushroomCow#checkMushroomSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8785F2A9 - AST call getFluidState in Dolphin.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/dolphin/Dolphin.java:549`
- Context: `DolphinSwimToTreasureGoal#tick`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-1864D8C1 - AST call getBlockState in Dolphin.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/dolphin/Dolphin.java:549`
- Context: `DolphinSwimToTreasureGoal#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BA2F0EBD - AST call getBlockState in AbstractHorse.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/equine/AbstractHorse.java:397`
- Context: `AbstractHorse#playStepSound`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6B0B72BE - AST call getBlockState in AbstractHorse.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/equine/AbstractHorse.java:600`
- Context: `AbstractHorse#aiStep`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(this.blockPosition().below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B8B0A006 - AST call getBlockState in Cat.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/feline/Cat.java:581`
- Context: `CatRelaxOnOwnerGoal#canUse`
- Receiver: `this.cat.level()`
- Method: `getBlockState`
- Evidence: `this.cat.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E598B555 - AST call getBlockState in Ocelot.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/feline/Ocelot.java:289`
- Context: `Ocelot#checkSpawnObstruction`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8FBA47BC - AST call getFluidState in TropicalFish.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/fish/TropicalFish.java:297`
- Context: `TropicalFish#checkTropicalFishSpawnRules`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.below())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-7AA8F4EF - AST call getBlockState in TropicalFish.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/fish/TropicalFish.java:298`
- Context: `TropicalFish#checkTropicalFishSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6A4FDBD1 - AST call getFluidState in WaterAnimal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/fish/WaterAnimal.java:80`
- Context: `WaterAnimal#checkSurfaceWaterAnimalSpawnRules`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.below())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-4FC72F08 - AST call getBlockState in WaterAnimal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/fish/WaterAnimal.java:81`
- Context: `WaterAnimal#checkSurfaceWaterAnimalSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9FD8472D - AST call getBlockState in Fox.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/fox/Fox.java:396`
- Context: `Fox#checkFoxSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-34E6AA14 - AST call getBlockState in Fox.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/fox/Fox.java:644`
- Context: `Fox#tick`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0056838F - AST call getBlockState in Fox.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/fox/Fox.java:851`
- Context: `Fox#isPathClear`
- Receiver: `fox.level()`
- Method: `getBlockState`
- Evidence: `fox.level().getBlockState(BlockPos.containing(fox.getX() + d4, fox.getY() + i2, fox.getZ() + d3))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-59290F11 - AST call getBlockState in Fox.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/fox/Fox.java:1066`
- Context: `FoxEatBerriesGoal#isValidTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CB874B27 - AST call getBlockState in Fox.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/fox/Fox.java:1087`
- Context: `FoxEatBerriesGoal#onReachedTarget`
- Receiver: `Fox.this.level()`
- Method: `getBlockState`
- Evidence: `Fox.this.level().getBlockState(this.blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-745578B9 - AST call getBlockState in Fox.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/fox/Fox.java:1357`
- Context: `FoxPounceGoal#tick`
- Receiver: `Fox.this.level()`
- Method: `getBlockState`
- Evidence: `Fox.this.level().getBlockState(Fox.this.blockPosition())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-66DDC20E - AST call getBlockState in Frog.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/frog/Frog.java:431`
- Context: `Frog#checkFrogSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F5EAFD80 - AST call getFluidState in FrogAi.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/frog/FrogAi.java:217`
- Context: `FrogAi#isAcceptableLandingSpot`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-0E18BDEA - AST call getFluidState in FrogAi.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/frog/FrogAi.java:217`
- Context: `FrogAi#isAcceptableLandingSpot`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-FAB89120 - AST call getFluidState in FrogAi.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/frog/FrogAi.java:217`
- Context: `FrogAi#isAcceptableLandingSpot`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.above())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-80204C31 - AST call getBlockState in FrogAi.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/frog/FrogAi.java:218`
- Context: `FrogAi#isAcceptableLandingSpot`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-93345AF7 - AST call getBlockState in FrogAi.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/frog/FrogAi.java:219`
- Context: `FrogAi#isAcceptableLandingSpot`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-241247B6 - AST call getBlockState in Goat.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/goat/Goat.java:426`
- Context: `Goat#checkGoatSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0A763384 - AST call getBlockState in CopperGolem.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/golem/CopperGolem.java:351`
- Context: `CopperGolem#canTurnToStatue`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(this.blockPosition())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7EBD8D65 - AST call getBlockEntity in CopperGolem.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/golem/CopperGolem.java:373`
- Context: `CopperGolem#turnToStatue`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-73A4C62F - AST call getBlockState in CopperGolem.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/golem/CopperGolem.java:492`
- Context: `CopperGolem#hasContainerOpen`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(this.openedChestPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-51D16617 - AST call getBlockState in IronGolem.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/golem/IronGolem.java:363`
- Context: `IronGolem#checkSpawnObstruction`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6A22A6E3 - AST call getBlockState in IronGolem.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/golem/IronGolem.java:369`
- Context: `IronGolem#checkSpawnObstruction`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos2)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9F51CE30 - AST call getBlockState in IronGolem.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/golem/IronGolem.java:376`
- Context: `IronGolem#checkSpawnObstruction`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-891E3FC6 - AST call getBlockState in SnowGolem.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/golem/SnowGolem.java:157`
- Context: `SnowGolem#aiStep`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-EF3694ED - AST call getFluidState in AbstractNautilus.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/nautilus/AbstractNautilus.java:148`
- Context: `AbstractNautilus#checkNautilusSpawnRules`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.below())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-1B6DE4BA - AST call getBlockState in AbstractNautilus.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/nautilus/AbstractNautilus.java:149`
- Context: `AbstractNautilus#checkNautilusSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B7D794A5 - AST call getBlockState in Panda.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/panda/Panda.java:895`
- Context: `PandaBreedGoal#canFindBamboo`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-87262988 - AST call getBlockState in Panda.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/panda/Panda.java:1077`
- Context: `PandaRollGoal#canUse`
- Receiver: `this.panda.level()`
- Method: `getBlockState`
- Evidence: `this.panda.level().getBlockState(this.panda.blockPosition().offset(i, -1, i1))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6131CB8C - AST call getBlockState in Parrot.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/parrot/Parrot.java:281`
- Context: `Parrot#aiStep`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(this.jukebox)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-52CEFA62 - AST call getBlockState in Parrot.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/parrot/Parrot.java:399`
- Context: `Parrot#checkParrotSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-19F559CA - AST call getBlockState in Parrot.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/parrot/Parrot.java:596`
- Context: `ParrotWanderGoal#getTreePos`
- Receiver: `this.mob.level()`
- Method: `getBlockState`
- Evidence: `this.mob.level().getBlockState(mutableBlockPos1.setWithOffset(blockPos1, Direction.DOWN))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1B5941EA - AST call getBlockState in PolarBear.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/polarbear/PolarBear.java:199`
- Context: `PolarBear#checkPolarBearSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-395135B1 - AST call getBlockState in Rabbit.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/rabbit/Rabbit.java:528`
- Context: `Rabbit#checkRabbitSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E8799DB1 - AST call getBlockState in Rabbit.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/rabbit/Rabbit.java:698`
- Context: `RaidGardenGoal#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2F61CB0D - AST call getBlockState in Rabbit.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/rabbit/Rabbit.java:723`
- Context: `RaidGardenGoal#isValidTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-77614E71 - AST call getBlockState in Rabbit.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/rabbit/Rabbit.java:725`
- Context: `RaidGardenGoal#isValidTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-876784F4 - AST call getBlockState in Sniffer.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java:302`
- Context: `Sniffer#canDig`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3CADA1BF - AST call getBlockState in Sniffer.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java:330`
- Context: `Sniffer#emitDiggingParticles`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(headBlock.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-07440BA0 - AST call getBlockState in GlowSquid.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/squid/GlowSquid.java:154`
- Context: `GlowSquid#checkGlowSquidSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1DF68D83 - AST call getBlockState in Squid.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/squid/Squid.java:327`
- Context: `SquidFleeGoal#tick`
- Receiver: `Squid.this.level()`
- Method: `getBlockState`
- Evidence: `Squid.this.level().getBlockState(BlockPos.containing(Squid.this.getX() + vec3.x, Squid.this.getY() + vec3.y, Squid.this.getZ() + vec3.z))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-461C07E1 - AST call getFluidState in Squid.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/squid/Squid.java:329`
- Context: `SquidFleeGoal#tick`
- Receiver: `Squid.this.level()`
- Method: `getFluidState`
- Evidence: `Squid.this.level().getFluidState(BlockPos.containing(Squid.this.getX() + vec3.x, Squid.this.getY() + vec3.y, Squid.this.getZ() + vec3.z))`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-DF3D790D - AST call getFluidState in Turtle.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/turtle/Turtle.java:283`
- Context: `Turtle#getWalkTargetValue`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-415A0148 - AST call getBlockState in Turtle.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/turtle/Turtle.java:296`
- Context: `Turtle#aiStep`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2EB7DBEB - AST call getBlockState in Turtle.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/turtle/Turtle.java:435`
- Context: `TurtleGoHomeGoal#tick`
- Receiver: `this.turtle.level()`
- Method: `getBlockState`
- Evidence: `this.turtle.level().getBlockState(BlockPos.containing(posTowards))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F72AE424 - AST call getBlockState in Turtle.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/turtle/Turtle.java:478`
- Context: `TurtleGoToWaterGoal#isValidTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7D4B7DB3 - AST call getBlockState in Turtle.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/turtle/Turtle.java:618`
- Context: `TurtlePathNavigation#isStableDestination`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8D724CFB - AST call getBlockState in Turtle.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/turtle/Turtle.java:619`
- Context: `TurtlePathNavigation#isStableDestination`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8790BA7F - AST call getBlockState in Wolf.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/animal/wolf/Wolf.java:821`
- Context: `Wolf#checkWolfSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-016C5992 - AST call getBlockState in EndCrystal.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/boss/enderdragon/EndCrystal.java:84`
- Context: `EndCrystal#tick`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-815CE9A7 - AST call getBlockState in EnderDragon.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/boss/enderdragon/EnderDragon.java:550`
- Context: `EnderDragon#checkWalls`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-34E9807A - AST call getBlockEntity in EnderDragon.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/boss/enderdragon/EnderDragon.java:594`
- Context: `EnderDragon#checkWalls`
- Receiver: `this.level()`
- Method: `getBlockEntity`
- Evidence: `this.level().getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-B3C184D2 - AST call getBlockState in WitherBoss.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/boss/wither/WitherBoss.java:524`
- Context: `WitherBoss#customServerAiStep`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2C30EE26 - AST call getBlockState in ArmorStand.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/decoration/ArmorStand.java:823`
- Context: `ArmorStand#updateInWaterStateAndDoWaterCurrentPushing`
- Receiver: `level()`
- Method: `getBlockState`
- Evidence: `level().getBlockState(blockPosition().below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7BF34E03 - AST call getBlockState in BlockAttachedEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/decoration/BlockAttachedEntity.java:47`
- Context: `BlockAttachedEntity#tick`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(this.blockPosition())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F804F9C4 - AST call getBlockState in HangingEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/decoration/HangingEntity.java:87`
- Context: `HangingEntity#survives`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2A1F9E3E - AST call getBlockState in ItemFrame.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/decoration/ItemFrame.java:138`
- Context: `ItemFrame#survives`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(this.pos.relative(this.getDirection().getOpposite()))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-819621D1 - AST call getBlockState in LeashFenceKnotEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/decoration/LeashFenceKnotEntity.java:120`
- Context: `LeashFenceKnotEntity#survives`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(this.pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-234F13BB - AST call getFluidState in FallingBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/item/FallingBlockEntity.java:171`
- Context: `FallingBlockEntity#tick`
- Receiver: `this.level()`
- Method: `getFluidState`
- Evidence: `this.level().getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-17C242A2 - AST call getFluidState in FallingBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/item/FallingBlockEntity.java:180`
- Context: `FallingBlockEntity#tick`
- Receiver: `this.level()`
- Method: `getFluidState`
- Evidence: `this.level().getFluidState(blockHitResult.getBlockPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-C894D2D7 - AST call getBlockState in FallingBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/item/FallingBlockEntity.java:195`
- Context: `FallingBlockEntity#tick`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-52635FEA - AST call getBlockState in FallingBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/item/FallingBlockEntity.java:202`
- Context: `FallingBlockEntity#tick`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7037B51D - AST call getFluidState in FallingBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/item/FallingBlockEntity.java:206`
- Context: `FallingBlockEntity#tick`
- Receiver: `this.level()`
- Method: `getFluidState`
- Evidence: `this.level().getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-BAB9581C - AST call getBlockState in FallingBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/item/FallingBlockEntity.java:219`
- Context: `FallingBlockEntity#tick`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BC185002 - AST call getBlockEntity in FallingBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/item/FallingBlockEntity.java:226`
- Context: `FallingBlockEntity#tick`
- Receiver: `this.level()`
- Method: `getBlockEntity`
- Evidence: `this.level().getBlockEntity(blockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-FAC8EB36 - AST call getBlockState in ItemEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/item/ItemEntity.java:190`
- Context: `ItemEntity#tick`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BC016B20 - AST call getBlockState in EnderMan.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/EnderMan.java:559`
- Context: `EndermanLeaveBlockGoal#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DA89C66B - AST call getBlockState in Ghast.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Ghast.java:355`
- Context: `GhastMoveControl#blockTraversalPossible`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A5278C77 - AST call getFluidState in Ghast.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Ghast.java:368`
- Context: `GhastMoveControl#blockTraversalPossible`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-69CE9EC6 - AST call getBlockState in Ghast.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Ghast.java:522`
- Context: `RandomFloatAroundGoal#isGoodTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2ACEEE8C - AST call getBlockState in Ghast.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Ghast.java:528`
- Context: `RandomFloatAroundGoal#isGoodTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-AD920A13 - AST call getFluidState in Guardian.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Guardian.java:223`
- Context: `Guardian#getWalkTargetValue`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-12A6FFCB - AST call getFluidState in Guardian.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Guardian.java:366`
- Context: `Guardian#checkGuardianSpawnRules`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-FEC65157 - AST call getFluidState in Guardian.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Guardian.java:367`
- Context: `Guardian#checkGuardianSpawnRules`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.below())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-35A44471 - AST call getBlockState in Monster.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Monster.java:162`
- Context: `Monster#canSpawnInBlueAndPackedIce`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B8CF1827 - AST call getBlockState in Ravager.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Ravager.java:203`
- Context: `Ravager#aiStep`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-928C784E - AST call getBlockState in Silverfish.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Silverfish.java:152`
- Context: `Silverfish#getWalkTargetValue`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6683AB41 - AST call getBlockState in Silverfish.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Silverfish.java:188`
- Context: `SilverfishMergeWithStoneGoal#canUse`
- Receiver: `this.mob.level()`
- Method: `getBlockState`
- Evidence: `this.mob.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-832D6CC9 - AST call getBlockState in Silverfish.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Silverfish.java:212`
- Context: `SilverfishMergeWithStoneGoal#start`
- Receiver: `levelAccessor`
- Method: `getBlockState`
- Evidence: `levelAccessor.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5D5DCB68 - AST call getBlockState in Silverfish.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Silverfish.java:258`
- Context: `SilverfishWakeUpFriendsGoal#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D7E2694E - AST call getBlockState in Silverfish.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Silverfish.java:262`
- Context: `SilverfishWakeUpFriendsGoal#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-972A95EE - AST call getBlockState in Silverfish.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Silverfish.java:270`
- Context: `SilverfishWakeUpFriendsGoal#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5D3C8ADE - AST call getFluidState in Strider.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Strider.java:141`
- Context: `Strider#checkStriderSpawnRules`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(mutableBlockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-D3130188 - AST call getBlockState in Strider.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Strider.java:143`
- Context: `Strider#checkStriderSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B4D5A9B1 - AST call getFluidState in Strider.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Strider.java:261`
- Context: `Strider#getDismountLocationForPassenger`
- Receiver: `this.level()`
- Method: `getFluidState`
- Evidence: `this.level().getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-5EB411BD - AST call getBlockState in Strider.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Strider.java:331`
- Context: `Strider#tick`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(this.blockPosition())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-63A25D29 - AST call getFluidState in Strider.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Strider.java:364`
- Context: `Strider#floatStrider`
- Receiver: `this.level()`
- Method: `getFluidState`
- Evidence: `this.level().getFluidState(this.blockPosition().above())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-1CB541CD - AST call getFluidState in Strider.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Strider.java:413`
- Context: `Strider#getWalkTargetValue`
- Receiver: `level.getBlockState(pos)`
- Method: `getFluidState`
- Evidence: `level.getBlockState(pos).getFluidState()`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-FA488FCA - AST call getBlockState in Strider.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Strider.java:413`
- Context: `Strider#getWalkTargetValue`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E1515040 - AST call getBlockState in Strider.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Strider.java:550`
- Context: `StriderGoToLavaGoal#isValidTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BF6B46A2 - AST call getBlockState in Strider.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Strider.java:550`
- Context: `StriderGoToLavaGoal#isValidTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-48CDE865 - AST call getBlockState in Strider.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/Strider.java:588`
- Context: `StriderPathNavigation#isStableDestination`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-AE559DEA - AST call getBlockState in LongJump.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/breeze/LongJump.java:90`
- Context: `LongJump#canRun`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1944A96C - AST call getBlockState in LongJump.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/breeze/LongJump.java:211`
- Context: `LongJump#canJumpFromCurrentPosition`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BE5C6AAA - AST call getBlockState in LongJump.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/breeze/LongJump.java:216`
- Context: `LongJump#canJumpFromCurrentPosition`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BEF0DF05 - AST call getFluidState in LongJump.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/breeze/LongJump.java:216`
- Context: `LongJump#canJumpFromCurrentPosition`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos1)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-E4058359 - AST call getBlockEntity in Creaking.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/creaking/Creaking.java:199`
- Context: `Creaking#hurtServer`
- Receiver: `this.level()`
- Method: `getBlockEntity`
- Evidence: `this.level().getBlockEntity(homePos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-CAA35EC3 - AST call getBlockEntity in Creaking.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/creaking/Creaking.java:280`
- Context: `Creaking#tick`
- Receiver: `this.level()`
- Method: `getBlockEntity`
- Evidence: `this.level().getBlockEntity(homePos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-178B6176 - AST call getBlockState in Hoglin.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java:257`
- Context: `Hoglin#checkHoglinSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D36CDEA8 - AST call getBlockState in Hoglin.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java:281`
- Context: `Hoglin#getWalkTargetValue`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-36962D8F - AST call getBlockState in Evoker.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/illager/Evoker.java:203`
- Context: `EvokerAttackSpellGoal#createSpellEntity`
- Receiver: `Evoker.this.level()`
- Method: `getBlockState`
- Evidence: `Evoker.this.level().getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E1333044 - AST call getBlockState in Evoker.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/illager/Evoker.java:206`
- Context: `EvokerAttackSpellGoal#createSpellEntity`
- Receiver: `Evoker.this.level()`
- Method: `getBlockState`
- Evidence: `Evoker.this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BD5727CD - AST call getBlockState in Piglin.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/piglin/Piglin.java:262`
- Context: `Piglin#checkPiglinSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3CA47D38 - AST call getBlockState in Stray.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/skeleton/Stray.java:78`
- Context: `Stray#checkStraySpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C6229717 - AST call getFluidState in Drowned.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/zombie/Drowned.java:213`
- Context: `Drowned#checkDrownedSpawnRules`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.below())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-CF3CCB65 - AST call getFluidState in Drowned.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/zombie/Drowned.java:219`
- Context: `Drowned#checkDrownedSpawnRules`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-506DDE57 - AST call getBlockState in Drowned.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/zombie/Drowned.java:430`
- Context: `DrownedGoToBeachGoal#isValidTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-39E41A74 - AST call getBlockState in Drowned.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/zombie/Drowned.java:495`
- Context: `DrownedGoToWaterGoal#getWaterPos`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1C73B743 - AST call getBlockState in ZombieVillager.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/zombie/ZombieVillager.java:357`
- Context: `ZombieVillager#getConversionProgress`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(mutableBlockPos.set(i2, i3, i4))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CCEE9F91 - AST call getBlockState in ZombifiedPiglin.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/monster/zombie/ZombifiedPiglin.java:259`
- Context: `ZombifiedPiglin#checkZombifiedPiglinSpawnRules`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-19ABBE44 - AST call getBlockState in Villager.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/npc/villager/Villager.java:248`
- Context: `Villager#canTravelTo`
- Receiver: `level()`
- Method: `getBlockState`
- Evidence: `level().getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5B22D79B - AST call getBlockState in Villager.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/npc/villager/Villager.java:1110`
- Context: `Villager#startSleeping`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8D99A781 - AST call getBlockState in WanderingTraderSpawner.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/npc/wanderingtrader/WanderingTraderSpawner.java:147`
- Context: `WanderingTraderSpawner#findSpawnPositionNear`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5E4463EA - AST call getBlockState in WanderingTraderSpawner.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/npc/wanderingtrader/WanderingTraderSpawner.java:150`
- Context: `WanderingTraderSpawner#findSpawnPositionNear`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-904D5566 - AST call getBlockState in WanderingTraderSpawner.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/npc/wanderingtrader/WanderingTraderSpawner.java:164`
- Context: `WanderingTraderSpawner#hasEnoughSpace`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D2D49EB4 - AST call getFluidState in Player.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/player/Player.java:1535`
- Context: `Player#travel`
- Receiver: `this.level()`
- Method: `getFluidState`
- Evidence: `this.level().getFluidState(BlockPos.containing(this.getX(), this.getY() + 1.0 - 0.1, this.getZ()))`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-13903B3E - AST call getBlockState in Player.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/player/Player.java:1566`
- Context: `Player#freeAt`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1C5EDB76 - AST call getBlockState in Player.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/player/Player.java:1643`
- Context: `Player#playStepSound`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(primaryStepSoundBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3FE0CB6F - AST call getBlockState in FireworkRocketEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/FireworkRocketEntity.java:250`
- Context: `FireworkRocketEntity#onHitBlock`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6DEB7578 - AST call getFluidState in FishingHook.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/FishingHook.java:187`
- Context: `FishingHook#tick`
- Receiver: `this.level()`
- Method: `getFluidState`
- Evidence: `this.level().getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-97EC9093 - AST call getBlockState in FishingHook.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/FishingHook.java:359`
- Context: `FishingHook#catchingFish`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(BlockPos.containing(d, d1 - 1.0, d2))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CC3D7B6C - AST call getBlockState in FishingHook.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/FishingHook.java:421`
- Context: `FishingHook#catchingFish`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(BlockPos.containing(d, d1 - 1.0, d2))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9377AE59 - AST call getBlockState in FishingHook.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/FishingHook.java:488`
- Context: `FishingHook#getOpenWaterTypeForBlock`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-473A9F21 - AST call getBlockState in Projectile.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/Projectile.java:521`
- Context: `Projectile#onHit`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F6536671 - AST call getBlockState in Projectile.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/Projectile.java:534`
- Context: `Projectile#onHitBlock`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(result.getBlockPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-037BC5BE - AST call getBlockState in ThrowableProjectile.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/ThrowableProjectile.java:100`
- Context: `ThrowableProjectile#handleFirstTickBubbleColumn`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3BEF71A6 - AST call getBlockState in AbstractArrow.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/arrow/AbstractArrow.java:180`
- Context: `AbstractArrow#tick`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0441C45B - AST call getBlockState in AbstractArrow.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/arrow/AbstractArrow.java:561`
- Context: `AbstractArrow#onHitBlock`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(result.getBlockPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FAEBEBD7 - AST call getBlockState in AbstractArrow.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/arrow/AbstractArrow.java:591`
- Context: `AbstractArrow#hitBlockEnchantmentEffects`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(hitResult.getBlockPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9DD7F65F - AST call getBlockState in ThrownTrident.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/arrow/ThrownTrident.java:181`
- Context: `ThrownTrident#hitBlockEnchantmentEffects`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(hitResult.getBlockPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8541B3A8 - AST call getBlockState in AbstractThrownPotion.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/throwableitemprojectile/AbstractThrownPotion.java:147`
- Context: `AbstractThrownPotion#dowseFire`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-17F5A038 - AST call getBlockState in Snowball.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/throwableitemprojectile/Snowball.java:69`
- Context: `Snowball#onHitBlock`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F1DC1A40 - AST call getBlockState in Snowball.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/projectile/throwableitemprojectile/Snowball.java:71`
- Context: `Snowball#onHitBlock`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(relativePos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6D59CDBF - AST call getBlockState in Raid.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/raid/Raid.java:701`
- Context: `Raid#findRandomSpawnPos`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E29CCF2B - AST call getBlockState in Raid.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/raid/Raid.java:701`
- Context: `Raid#findRandomSpawnPos`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5ADA1785 - AST call getBlockState in DismountHelper.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/DismountHelper.java:56`
- Context: `DismountHelper#nonClimbableShape`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-423F8480 - AST call getBlockState in DismountHelper.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/DismountHelper.java:80`
- Context: `DismountHelper#findSafeDismountLocation`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-AF6DCD79 - AST call getBlockState in DismountHelper.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/DismountHelper.java:86`
- Context: `DismountHelper#findSafeDismountLocation`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8F39D250 - AST call getBlockState in DismountHelper.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/DismountHelper.java:99`
- Context: `DismountHelper#findSafeDismountLocation`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E73B3796 - AST call getBlockState in DismountHelper.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/DismountHelper.java:99`
- Context: `DismountHelper#findSafeDismountLocation`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9278311B - AST call getFluidState in AbstractBoat.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/boat/AbstractBoat.java:459`
- Context: `AbstractBoat#getWaterLevelAbove`
- Receiver: `this.level()`
- Method: `getFluidState`
- Evidence: `this.level().getFluidState(mutableBlockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-E1E73C72 - AST call getBlockState in AbstractBoat.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/boat/AbstractBoat.java:499`
- Context: `AbstractBoat#getGroundFriction`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B1A76512 - AST call getFluidState in AbstractBoat.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/boat/AbstractBoat.java:532`
- Context: `AbstractBoat#checkInWater`
- Receiver: `this.level()`
- Method: `getFluidState`
- Evidence: `this.level().getFluidState(mutableBlockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-F017254D - AST call getFluidState in AbstractBoat.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/boat/AbstractBoat.java:561`
- Context: `AbstractBoat#isUnderwater`
- Receiver: `this.level()`
- Method: `getFluidState`
- Evidence: `this.level().getFluidState(mutableBlockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-C2FB3860 - AST call getFluidState in AbstractBoat.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/boat/AbstractBoat.java:766`
- Context: `AbstractBoat#checkFallDamage`
- Receiver: `this.level()`
- Method: `getFluidState`
- Evidence: `this.level().getFluidState(this.blockPosition().below())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-2029655B - AST call getBlockState in AbstractMinecart.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/AbstractMinecart.java:151`
- Context: `AbstractMinecart#createMinecart`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(currentBlockPosOrRailBelow)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8037A335 - AST call getBlockState in AbstractMinecart.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/AbstractMinecart.java:245`
- Context: `AbstractMinecart#getDismountLocationForPassenger`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3CF2D87F - AST call getBlockState in AbstractMinecart.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/AbstractMinecart.java:258`
- Context: `AbstractMinecart#getBlockSpeedFactor`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(this.blockPosition())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C7EC7D83 - AST call getBlockState in AbstractMinecart.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/AbstractMinecart.java:350`
- Context: `AbstractMinecart#getCurrentBlockPosOrRailBelow`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(BlockPos.containing(floor, d, floor2))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-11622108 - AST call getBlockState in AbstractMinecart.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/AbstractMinecart.java:353`
- Context: `AbstractMinecart#getCurrentBlockPosOrRailBelow`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(new BlockPos(floor, floor1 - 1, floor2))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-22B47698 - AST call getBlockState in AbstractMinecart.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/AbstractMinecart.java:416`
- Context: `AbstractMinecart#getControllableSpeed`
- Receiver: `level()`
- Method: `getBlockState`
- Evidence: `level().getBlockState(this.blockPosition())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-294A99AF - AST call getBlockState in AbstractMinecart.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/AbstractMinecart.java:418`
- Context: `AbstractMinecart#getControllableSpeed`
- Receiver: `level()`
- Method: `getBlockState`
- Evidence: `level().getBlockState(this.blockPosition().relative(Direction.DOWN))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F1EE752C - AST call getBlockState in MinecartTNT.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/MinecartTNT.java:197`
- Context: `MinecartTNT#getBlockExplosionResistance`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A098DD95 - AST call getBlockState in MinecartTNT.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/MinecartTNT.java:204`
- Context: `MinecartTNT#shouldBlockExplode`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C58E8CD0 - AST call getBlockState in NewMinecartBehavior.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/NewMinecartBehavior.java:50`
- Context: `NewMinecartBehavior#tick`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(var5)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-862D5A89 - AST call getBlockState in NewMinecartBehavior.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/NewMinecartBehavior.java:60`
- Context: `NewMinecartBehavior#tick`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(this.minecart.getCurrentBlockPosOrRailBelow())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-566DA133 - AST call getBlockState in NewMinecartBehavior.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/NewMinecartBehavior.java:234`
- Context: `NewMinecartBehavior#moveAlongTrack`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(currentBlockPosOrRailBelow)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7D200DAA - AST call getBlockState in NewMinecartBehavior.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/NewMinecartBehavior.java:441`
- Context: `NewMinecartBehavior#stepAlongTrack`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(BlockPos.containing(vec36))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E4BC05A2 - AST call getBlockState in OldMinecartBehavior.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior.java:60`
- Context: `OldMinecartBehavior#tick`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(var11)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-AA010F9D - AST call getBlockState in OldMinecartBehavior.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior.java:106`
- Context: `OldMinecartBehavior#moveAlongTrack`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(currentBlockPosOrRailBelow)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-08C39CF9 - AST call getBlockState in OldMinecartBehavior.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior.java:279`
- Context: `OldMinecartBehavior#getPosOffs`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(new BlockPos(floor, floor1 - 1, floor2))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-98F5F3C9 - AST call getBlockState in OldMinecartBehavior.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior.java:283`
- Context: `OldMinecartBehavior#getPosOffs`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(new BlockPos(floor, floor1, floor2))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-83AF4810 - AST call getBlockState in OldMinecartBehavior.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior.java:317`
- Context: `OldMinecartBehavior#getPos`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(new BlockPos(floor, floor1 - 1, floor2))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8769C1E7 - AST call getBlockState in OldMinecartBehavior.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior.java:321`
- Context: `OldMinecartBehavior#getPos`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(new BlockPos(floor, floor1, floor2))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-00157393 - AST call getBlockEntity in EnchantmentMenu.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/inventory/EnchantmentMenu.java:74`
- Context: `#onClose`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-9CB81D0B - AST call getBlockEntity in EnchantmentMenu.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/inventory/EnchantmentMenu.java:105`
- Context: `EnchantmentMenu#<init>`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-1339C6CA - AST call getBlockEntity in BlockItem.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BlockItem.java:138`
- Context: `BlockItem#updateBlockEntityComponents`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-292DE364 - AST call getBlockEntity in BlockItem.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BlockItem.java:149`
- Context: `BlockItem#updateCustomBlockEntityTag`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-6586AD89 - AST call getBlockEntity in BlockItem.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BlockItem.java:208`
- Context: `BlockItem#updateCustomBlockEntityTag`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-3C80E3D3 - AST call getBlockEntity in BrushItem.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BrushItem.java:82`
- Context: `BrushItem#onUseTick`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-5AA652AD - AST call setBlockEntity in ItemStack.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: set-block-entity
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/ItemStack.java:473`
- Context: `ItemStack#useOn`
- Receiver: `serverLevel`
- Method: `setBlockEntity`
- Evidence: `serverLevel.setBlockEntity(e.getValue())`
- Suggested fix: Ensure the target chunk is loaded and owned, or resume on the owning region before mutation.

### AO-AST-8593E62B - AST call getBlockEntity in ItemStack.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/ItemStack.java:500`
- Context: `ItemStack#useOn`
- Receiver: `serverLevel`
- Method: `getBlockEntity`
- Evidence: `serverLevel.getBlockEntity(bp)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-B72EA8D2 - AST call getBlockEntity in ItemStack.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/ItemStack.java:510`
- Context: `ItemStack#useOn`
- Receiver: `serverLevel`
- Method: `getBlockEntity`
- Evidence: `serverLevel.getBlockEntity(SignItem.openSignThreadLocal.get())`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-A462288C - AST call getBlockEntity in JukeboxPlayable.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/JukeboxPlayable.java:54`
- Context: `JukeboxPlayable#tryInsertIntoJukebox`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-CA85F1B0 - AST call getBlockEntity in SignItem.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/SignItem.java:29`
- Context: `SignItem#updateCustomBlockEntityTag`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-8E4A2908 - AST call getBlockState in SignItem.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/SignItem.java:30`
- Context: `SignItem#updateCustomBlockEntityTag`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D6453E43 - AST call getBlockEntity in SpawnEggItem.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/SpawnEggItem.java:61`
- Context: `SpawnEggItem#useOn`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(clickedPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-020C3761 - AST call getBlockState in SetBlockProperties.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/enchantment/effects/SetBlockProperties.java:36`
- Context: `SetBlockProperties#apply`
- Receiver: `entity.level()`
- Method: `getBlockState`
- Evidence: `entity.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1FC6DA5F - AST call getBlockEntity in CommonLevelAccessor.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/CommonLevelAccessor.java:17`
- Context: `CommonLevelAccessor#getBlockEntity`
- Receiver: `LevelReader.super`
- Method: `getBlockEntity`
- Evidence: `LevelReader.super.getBlockEntity(pos, type)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-F50F6AC8 - AST call getBlockEntity in ServerExplosion.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/ServerExplosion.java:463`
- Context: `ServerExplosion#calculateExplodedPositions`
- Receiver: `this.level`
- Method: `getBlockEntity`
- Evidence: `this.level.getBlockEntity(cachedBlock.immutablePos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-A404519A - AST call getBlockEntity in AbstractBannerBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/AbstractBannerBlock.java:36`
- Context: `AbstractBannerBlock#getCloneItemStack`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-592FFA2B - AST call getBlockEntity in AbstractFurnaceBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/AbstractFurnaceBlock.java:66`
- Context: `AbstractFurnaceBlock#getAnalogOutputSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-E536EB0D - AST call getBlockEntity in BarrelBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BarrelBlock.java:44`
- Context: `BarrelBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-C4B3BE41 - AST call getBlockEntity in BarrelBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BarrelBlock.java:59`
- Context: `BarrelBlock#tick`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-5C03429E - AST call getBlockEntity in BarrelBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BarrelBlock.java:77`
- Context: `BarrelBlock#getAnalogOutputSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-CE843E46 - AST call getBlockEntity in BaseEntityBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseEntityBlock.java:25`
- Context: `BaseEntityBlock#triggerEvent`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-386F8889 - AST call getBlockEntity in BaseEntityBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseEntityBlock.java:31`
- Context: `BaseEntityBlock#getMenuProvider`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-55D9114F - AST call getBlockEntity in BeaconBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BeaconBlock.java:48`
- Context: `BeaconBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-F6CF45A9 - AST call getBlockEntity in BeehiveBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BeehiveBlock.java:147`
- Context: `BeehiveBlock#useItemOn`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-A247D6D5 - AST call getBlockEntity in BeehiveBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BeehiveBlock.java:197`
- Context: `BeehiveBlock#hiveContainsBees`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-DDD2CEA7 - AST call getBlockEntity in BeehiveBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BeehiveBlock.java:204`
- Context: `BeehiveBlock#releaseBeesAndResetHoneyLevel`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-6BC147F4 - AST call getBlockEntity in BeehiveBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BeehiveBlock.java:291`
- Context: `BeehiveBlock#playerWillDestroy`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-18B2BA1A - AST call getBlockEntity in BeehiveBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BeehiveBlock.java:345`
- Context: `BeehiveBlock#updateShape`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-969040A9 - AST call getBlockEntity in BellBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BellBlock.java:134`
- Context: `BellBlock#attemptToRing`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-55806ADB - AST call getBlockEntity in BlastFurnaceBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BlastFurnaceBlock.java:46`
- Context: `BlastFurnaceBlock#openContainer`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-FC80A95B - AST call getBlockEntity in Blocks.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/Blocks.java:48`
- Context: `Blocks#(initializer)`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-6A20FA7C - AST call getBlockEntity in BrewingStandBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BrewingStandBlock.java:66`
- Context: `BrewingStandBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-81860565 - AST call getBlockEntity in BrewingStandBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BrewingStandBlock.java:93`
- Context: `BrewingStandBlock#getAnalogOutputSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-33FC8199 - AST call getBlockEntity in BrushableBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BrushableBlock.java:84`
- Context: `BrushableBlock#tick`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-EE5D7176 - AST call getBlockState in BubbleColumnBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BubbleColumnBlock.java:54`
- Context: `BubbleColumnBlock#entityInside`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A5152943 - AST call getBlockEntity in CampfireBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CampfireBlock.java:93`
- Context: `CampfireBlock#useItemOn`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-C65A88B2 - AST call getBlockEntity in CaveVines.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CaveVines.java:36`
- Context: `CaveVines#use`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-F30BD6AB - AST call getBlockEntity in CeilingHangingSignBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CeilingHangingSignBlock.java:71`
- Context: `CeilingHangingSignBlock#useItemOn`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-95D57062 - AST call getBlockEntity in ChestBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChestBlock.java:430`
- Context: `ChestBlock#tick`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-91A26969 - AST call getBlockEntity in ChiseledBookShelfBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChiseledBookShelfBlock.java:79`
- Context: `ChiseledBookShelfBlock#useItemOn`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-4014D147 - AST call getBlockEntity in ChiseledBookShelfBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChiseledBookShelfBlock.java:100`
- Context: `ChiseledBookShelfBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-B572C21E - AST call getBlockEntity in ChiseledBookShelfBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChiseledBookShelfBlock.java:182`
- Context: `ChiseledBookShelfBlock#getAnalogOutputSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-D30D1D52 - AST call getBlockEntity in CommandBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CommandBlock.java:97`
- Context: `CommandBlock#tick`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-A98BF96D - AST call getBlockEntity in CommandBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CommandBlock.java:137`
- Context: `CommandBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-0508F47A - AST call getBlockEntity in CommandBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CommandBlock.java:153`
- Context: `CommandBlock#getAnalogOutputSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-45D81CC5 - AST call getBlockEntity in CommandBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CommandBlock.java:159`
- Context: `CommandBlock#setPlacedBy`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-B55F9AA0 - AST call getBlockEntity in CommandBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CommandBlock.java:203`
- Context: `CommandBlock#executeChain`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(mutableBlockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-60C9C918 - AST call getBlockEntity in ComparatorBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ComparatorBlock.java:68`
- Context: `ComparatorBlock#getOutputSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-0EC10C61 - AST call getBlockEntity in ComparatorBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ComparatorBlock.java:187`
- Context: `ComparatorBlock#refreshOutputState`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-AAE053D6 - AST call getBlockEntity in ComparatorBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ComparatorBlock.java:233`
- Context: `ComparatorBlock#triggerEvent`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-ECE3795D - AST call getBlockEntity in CopperGolemStatueBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CopperGolemStatueBlock.java:154`
- Context: `CopperGolemStatueBlock#getCloneItemStack`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-1FA94C4A - AST call getBlockEntity in CrafterBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CrafterBlock.java:69`
- Context: `CrafterBlock#getAnalogOutputSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-992620BC - AST call getBlockEntity in CrafterBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CrafterBlock.java:138`
- Context: `CrafterBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-51D698DB - AST call getBlockEntity in CrafterBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CrafterBlock.java:146`
- Context: `CrafterBlock#dispenseFrom`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-4DCF990D - AST call getBlockEntity in CreakingHeartBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CreakingHeartBlock.java:162`
- Context: `CreakingHeartBlock#onExplosionHit`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-7CF3C940 - AST call getBlockEntity in CreakingHeartBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CreakingHeartBlock.java:176`
- Context: `CreakingHeartBlock#playerWillDestroy`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-4A299487 - AST call getBlockEntity in CreakingHeartBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CreakingHeartBlock.java:200`
- Context: `CreakingHeartBlock#getAnalogOutputSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-7331AC70 - AST call getBlockState in CropBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CropBlock.java:172`
- Context: `CropBlock#entityInside`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D374F237 - AST call getBlockEntity in DecoratedPotBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DecoratedPotBlock.java:99`
- Context: `DecoratedPotBlock#useItemOn`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-E01026C1 - AST call getBlockEntity in DecoratedPotBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DecoratedPotBlock.java:137`
- Context: `DecoratedPotBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-5B3E92CB - AST call getBlockEntity in DecoratedPotBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DecoratedPotBlock.java:224`
- Context: `DecoratedPotBlock#getCloneItemStack`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-2DB42364 - AST call getBlockEntity in DecoratedPotBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DecoratedPotBlock.java:239`
- Context: `DecoratedPotBlock#getAnalogOutputSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-028E1E68 - AST call getBlockEntity in DispenserBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DispenserBlock.java:74`
- Context: `DispenserBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-204E0D6D - AST call getBlockEntity in DispenserBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DispenserBlock.java:82`
- Context: `DispenserBlock#dispenseFrom`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos, BlockEntityType.DISPENSER)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-0DD78CB0 - AST call getBlockEntity in DispenserBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DispenserBlock.java:172`
- Context: `DispenserBlock#getAnalogOutputSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-A35D0BFC - AST call getBlockState in DragonEggBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DragonEggBlock.java:58`
- Context: `DragonEggBlock#teleport`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FD43E924 - AST call getBlockEntity in DropperBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DropperBlock.java:49`
- Context: `DropperBlock#dispenseFrom`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos, BlockEntityType.DROPPER)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-9DCDEF15 - AST call getBlockEntity in EnchantingTableBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/EnchantingTableBlock.java:105`
- Context: `EnchantingTableBlock#getMenuProvider`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-5E7A9D0E - AST call getBlockEntity in EndGatewayBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/EndGatewayBlock.java:57`
- Context: `EndGatewayBlock#animateTick`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-7B62ECE7 - AST call getBlockEntity in EndGatewayBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/EndGatewayBlock.java:97`
- Context: `EndGatewayBlock#entityInside`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-52E4CAE9 - AST call getBlockEntity in EndGatewayBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/EndGatewayBlock.java:110`
- Context: `EndGatewayBlock#getPortalDestinationAsync`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-14B80DF7 - AST call getBlockEntity in EnderChestBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/EnderChestBlock.java:79`
- Context: `EnderChestBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-4D738E50 - AST call getBlockEntity in EnderChestBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/EnderChestBlock.java:202`
- Context: `EnderChestBlock#tick`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-5E468351 - AST call getBlockEntity in FurnaceBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FurnaceBlock.java:46`
- Context: `FurnaceBlock#openContainer`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-FDFE650B - AST call getBlockState in HoneyBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HoneyBlock.java:100`
- Context: `HoneyBlock#maybeDoSlideAchievement`
- Receiver: `entity.level()`
- Method: `getBlockState`
- Evidence: `entity.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E3EE010A - AST call getBlockEntity in HopperBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HopperBlock.java:105`
- Context: `HopperBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-A62E2960 - AST call getBlockEntity in HopperBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HopperBlock.java:136`
- Context: `HopperBlock#getAnalogOutputSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-E95B2A8F - AST call getBlockEntity in HopperBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HopperBlock.java:157`
- Context: `HopperBlock#entityInside`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-ADF17144 - AST call getBlockEntity in JigsawBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/JigsawBlock.java:70`
- Context: `JigsawBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-12FD7354 - AST call getBlockEntity in JukeboxBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/JukeboxBlock.java:55`
- Context: `JukeboxBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-6F4E257E - AST call getBlockEntity in JukeboxBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/JukeboxBlock.java:93`
- Context: `JukeboxBlock#getSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-9480F4A5 - AST call getBlockEntity in JukeboxBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/JukeboxBlock.java:103`
- Context: `JukeboxBlock#getAnalogOutputSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-E11BDEF5 - AST call getBlockEntity in LecternBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LecternBlock.java:139`
- Context: `LecternBlock#placeBook`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-40949600 - AST call getBlockEntity in LecternBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LecternBlock.java:231`
- Context: `LecternBlock#getAnalogOutputSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-EC114B60 - AST call getBlockEntity in LecternBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LecternBlock.java:272`
- Context: `LecternBlock#openScreen`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-EE98E4AF - AST call getBlockState in NetherPortalBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NetherPortalBlock.java:281`
- Context: `NetherPortalBlock#getDimensionTransitionFromExit`
- Receiver: `entity.level()`
- Method: `getBlockState`
- Evidence: `entity.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E143295E - AST call getBlockState in NetherPortalBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NetherPortalBlock.java:287`
- Context: `NetherPortalBlock#getDimensionTransitionFromExit`
- Receiver: `entity.level()`
- Method: `getBlockState`
- Evidence: `entity.level().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0A0BA089 - AST call getBlockEntity in NoteBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NoteBlock.java:178`
- Context: `NoteBlock#getCustomSoundId`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos.above())`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-45D65DCB - AST call getBlockEntity in PumpkinBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PumpkinBlock.java:49`
- Context: `PumpkinBlock#useItemOn`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-B5B46C20 - AST call getBlockEntity in SculkSensorBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkSensorBlock.java:101`
- Context: `SculkSensorBlock#stepOn`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-906011CC - AST call getBlockEntity in SculkSensorBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkSensorBlock.java:293`
- Context: `SculkSensorBlock#getAnalogOutputSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-9E38F31F - AST call getBlockEntity in SculkShriekerBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkShriekerBlock.java:64`
- Context: `SculkShriekerBlock#stepOn`
- Receiver: `serverLevel`
- Method: `getBlockEntity`
- Evidence: `serverLevel.getBlockEntity(pos, BlockEntityType.SCULK_SHRIEKER)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-36E6A295 - AST call getBlockEntity in SculkShriekerBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkShriekerBlock.java:75`
- Context: `SculkShriekerBlock#tick`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos, BlockEntityType.SCULK_SHRIEKER)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-75D16D14 - AST call getBlockState in SculkSpreader.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkSpreader.java:288`
- Context: `ChargeCursor#update`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(validMovementPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F4330B7B - AST call getBlockState in SculkSpreader.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkSpreader.java:322`
- Context: `ChargeCursor#getValidMovementPos`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0F0C0B3F - AST call getBlockEntity in ShelfBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ShelfBlock.java:162`
- Context: `ShelfBlock#useItemOn`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-0BF84332 - AST call getBlockEntity in ShelfBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ShelfBlock.java:222`
- Context: `ShelfBlock#swapHotbar`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(allBlocksConnectedTo.get(i))`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-BB5FF295 - AST call getBlockEntity in ShelfBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ShelfBlock.java:317`
- Context: `ShelfBlock#getAnalogOutputSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-F7F4B4BB - AST call getBlockEntity in ShulkerBoxBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ShulkerBoxBlock.java:78`
- Context: `ShulkerBoxBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-9D1E3487 - AST call getBlockEntity in ShulkerBoxBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ShulkerBoxBlock.java:109`
- Context: `ShulkerBoxBlock#playerWillDestroy`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-37FE2486 - AST call getBlockEntity in ShulkerBoxBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ShulkerBoxBlock.java:160`
- Context: `ShulkerBoxBlock#getBlockSupportShape`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-12C328F1 - AST call getBlockEntity in ShulkerBoxBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ShulkerBoxBlock.java:167`
- Context: `ShulkerBoxBlock#getShape`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-07856CD8 - AST call getBlockEntity in ShulkerBoxBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ShulkerBoxBlock.java:184`
- Context: `ShulkerBoxBlock#getAnalogOutputSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-90785C96 - AST call getBlockEntity in SignBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SignBlock.java:93`
- Context: `SignBlock#useItemOn`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-67932D7A - AST call getBlockEntity in SignBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SignBlock.java:124`
- Context: `SignBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-57DAF088 - AST call getBlockEntity in SmokerBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SmokerBlock.java:45`
- Context: `SmokerBlock#openContainer`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-7851F063 - AST call getBlockEntity in SpongeBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SpongeBlock.java:122`
- Context: `SpongeBlock#removeWaterBreadthFirstSearch`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-032EA6D8 - AST call getBlockEntity in StructureBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/StructureBlock.java:44`
- Context: `StructureBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-E099F076 - AST call getBlockEntity in StructureBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/StructureBlock.java:56`
- Context: `StructureBlock#setPlacedBy`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-8B9DAF20 - AST call getBlockEntity in SweetBerryBushBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SweetBerryBushBlock.java:119`
- Context: `SweetBerryBushBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-43D7E90B - AST call getBlockEntity in TestBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TestBlock.java:63`
- Context: `TestBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-1FFE1785 - AST call getBlockEntity in TestBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TestBlock.java:104`
- Context: `TestBlock#getServerTestBlockEntity`
- Receiver: `serverLevel`
- Method: `getBlockEntity`
- Evidence: `serverLevel.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-0352A14F - AST call getBlockEntity in TestBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TestBlock.java:111`
- Context: `TestBlock#getSignal`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-340C0815 - AST call getBlockEntity in TestInstanceBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TestInstanceBlock.java:29`
- Context: `TestInstanceBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-BA4BF0C6 - AST call getBlockEntity in VaultBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/VaultBlock.java:50`
- Context: `VaultBlock#useItemOn`
- Receiver: `serverLevel`
- Method: `getBlockEntity`
- Evidence: `serverLevel.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-E8B22F92 - AST call getBlockEntity in WallHangingSignBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WallHangingSignBlock.java:64`
- Context: `WallHangingSignBlock#useItemOn`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-65BC985A - AST call getBlockEntity in WeatheringCopperChestBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WeatheringCopperChestBlock.java:45`
- Context: `WeatheringCopperChestBlock#randomTick`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-17A8483C - AST call getBlockEntity in WeatheringCopperGolemStatueBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WeatheringCopperGolemStatueBlock.java:55`
- Context: `WeatheringCopperGolemStatueBlock#useItemOn`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-BAD6EA02 - AST call getBlockEntity in WitherSkullBlock.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WitherSkullBlock.java:46`
- Context: `WitherSkullBlock#checkSpawn`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-26F3ADDC - AST call getBlockState in BeaconBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java:194`
- Context: `BeaconBlockEntity#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7AFAD704 - AST call getBlockState in BeaconBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java:279`
- Context: `BeaconBlockEntity#updateBase`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(new BlockPos(i3, i2, i4))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B971D2C1 - AST call getBlockState in BeehiveBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java:97`
- Context: `BeehiveBlockEntity#setChanged`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(this.getBlockPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-931D6793 - AST call getBlockState in BeehiveBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java:108`
- Context: `BeehiveBlockEntity#isFireNearby`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F4E80F3D - AST call getBlockState in BeehiveBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java:269`
- Context: `BeehiveBlockEntity#releaseOccupant`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-533ECB40 - AST call getBlockState in BeehiveBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java:327`
- Context: `BeehiveBlockEntity#releaseOccupant`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F2DC1B82 - AST call getBlockState in BlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/BlockEntity.java:301`
- Context: `BlockEntity#fillCrashReportCategory`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(this.worldPosition)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-81F50201 - AST call getBlockEntity in BlockEntityType.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/BlockEntityType.java:314`
- Context: `BlockEntityType#getBlockEntity`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-F9705385 - AST call scheduleTick in BrushableBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java:92`
- Context: `BrushableBlockEntity#brush`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), 2)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.
### AO-AST-0B41924C - AST call scheduleTick in BrushableBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java:198`
- Context: `BrushableBlockEntity#checkReset`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), 2)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-59FD2F1A - AST call getBlockState in ChestBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/ChestBlockEntity.java:200`
- Context: `ChestBlockEntity#getOpenCount`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CB085C6D - AST call getBlockEntity in ChestBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/ChestBlockEntity.java:202`
- Context: `ChestBlockEntity#getOpenCount`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-100D4C88 - AST call getBlockState in CommandBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/CommandBlockEntity.java:57`
- Context: `#onUpdated`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(CommandBlockEntity.this.worldPosition)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0C9553BF - AST call scheduleTick in CommandBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/CommandBlockEntity.java:125`
- Context: `CommandBlockEntity#setAutomatic`
- Receiver: `this`
- Method: `scheduleTick`
- Evidence: `this.scheduleTick()`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-4410B46E - AST call scheduleTick in CommandBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/CommandBlockEntity.java:132`
- Context: `CommandBlockEntity#onModeSwitch`
- Receiver: `this`
- Method: `scheduleTick`
- Evidence: `this.scheduleTick()`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-63161AF0 - AST call scheduleTick in CommandBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/CommandBlockEntity.java:140`
- Context: `CommandBlockEntity#scheduleTick`
- Receiver: `this.level`
- Method: `scheduleTick`
- Evidence: `this.level.scheduleTick(this.worldPosition, block, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-08C036FB - AST call getBlockState in CommandBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/CommandBlockEntity.java:151`
- Context: `CommandBlockEntity#markConditionMet`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(this.worldPosition)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CEB4EC0A - AST call getBlockState in CommandBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/CommandBlockEntity.java:152`
- Context: `CommandBlockEntity#markConditionMet`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-781FCB98 - AST call getBlockEntity in CommandBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/CommandBlockEntity.java:153`
- Context: `CommandBlockEntity#markConditionMet`
- Receiver: `this.level`
- Method: `getBlockEntity`
- Evidence: `this.level.getBlockEntity(blockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-39CD459A - AST call getBlockState in CommandBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/CommandBlockEntity.java:175`
- Context: `CommandBlockEntity#isConditional`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(this.getBlockPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-92663324 - AST call getBlockState in ConduitBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java:151`
- Context: `ConduitBlockEntity#updateShape`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FD3BF8F9 - AST call getBlockState in ContainerOpenersCounter.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/ContainerOpenersCounter.java:47`
- Context: `ContainerOpenersCounter#incrementOpeners`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2C188FFB - AST call getBlockState in ContainerOpenersCounter.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/ContainerOpenersCounter.java:72`
- Context: `ContainerOpenersCounter#decrementOpeners`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BD896F05 - AST call scheduleTick in ContainerOpenersCounter.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/ContainerOpenersCounter.java:139`
- Context: `ContainerOpenersCounter#scheduleRecheck`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, state.getBlock(), 5)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-8E7442DA - AST call getBlockState in CreakingHeartBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java:255`
- Context: `CreakingHeartBlockEntity#spreadResin`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A20B05F6 - AST call getBlockState in CreakingHeartBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java:260`
- Context: `CreakingHeartBlockEntity#spreadResin`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-73FFB828 - AST call getBlockState in CreakingHeartBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java:265`
- Context: `CreakingHeartBlockEntity#spreadResin`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-07299B36 - AST call getBlockState in HopperBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java:533`
- Context: `HopperBlockEntity#suckInItems`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2B294743 - AST call getBlockState in HopperBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java:774`
- Context: `HopperBlockEntity#getContainerAt`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-418DEE3B - AST call getBlockEntity in HopperBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java:796`
- Context: `HopperBlockEntity#getBlockContainer`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-73A965F1 - AST call getBlockState in JukeboxBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java:82`
- Context: `JukeboxBlockEntity#notifyItemChangedInJukebox`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(this.getBlockPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-54E27A2E - AST call scheduleTick in SculkCatalystBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/SculkCatalystBlockEntity.java:126`
- Context: `CatalystListener#bloom`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, state.getBlock(), 8)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-B49313AE - AST call scheduleTick in SculkShriekerBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/SculkShriekerBlockEntity.java:130`
- Context: `SculkShriekerBlockEntity#shriek`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(blockPos, blockState.getBlock(), 90)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-A7D5E28E - AST call getBlockState in SpawnerBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/SpawnerBlockEntity.java:32`
- Context: `#setNextSpawnData`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5DC8A7FE - AST call getBlockState in StructureBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/StructureBlockEntity.java:132`
- Context: `StructureBlockEntity#updateBlockState`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2B264E7C - AST call getBlockState in StructureBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/StructureBlockEntity.java:227`
- Context: `StructureBlockEntity#setMode`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(this.getBlockPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D22DAB67 - AST call getBlockState in StructureBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/StructureBlockEntity.java:286`
- Context: `StructureBlockEntity#detectSize`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8BBA3BE9 - AST call getBlockState in StructureBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/StructureBlockEntity.java:300`
- Context: `StructureBlockEntity#getRelatedCorners`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-28C9154C - AST call getBlockState in TestBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/TestBlockEntity.java:50`
- Context: `TestBlockEntity#updateBlockState`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FB5D7534 - AST call getBlockState in TestInstanceBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/TestInstanceBlockEntity.java:357`
- Context: `TestInstanceBlockEntity#encaseStructure`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-59BB2D0D - AST call getBlockState in TestInstanceBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/TestInstanceBlockEntity.java:365`
- Context: `TestInstanceBlockEntity#removeBarriers`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-33F8BC41 - AST call getBlockEntity in TheEndGatewayBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/TheEndGatewayBlockEntity.java:151`
- Context: `TheEndGatewayBlockEntity#getPortalPositionAsync`
- Receiver: `sourceLevel`
- Method: `getBlockEntity`
- Evidence: `sourceLevel.getBlockEntity(sourcePos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-2958D1AE - AST call getBlockEntity in TheEndGatewayBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/TheEndGatewayBlockEntity.java:159`
- Context: `TheEndGatewayBlockEntity#getPortalPositionAsync`
- Receiver: `sourceLevel`
- Method: `getBlockEntity`
- Evidence: `sourceLevel.getBlockEntity(sourcePos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-D4CB8303 - AST call getBlockState in TheEndGatewayBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/TheEndGatewayBlockEntity.java:370`
- Context: `TheEndGatewayBlockEntity#findTallestBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B6E6871B - AST call getBlockState in TheEndGatewayBlockEntity.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/TheEndGatewayBlockEntity.java:419`
- Context: `TheEndGatewayBlockEntity#shouldRenderFace`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(this.getBlockPos().relative(face))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-130D642E - AST call getBlockState in TrialSpawner.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java:102`
- Context: `TrialSpawner#applyOminous`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-42829265 - AST call getBlockState in TrialSpawner.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java:109`
- Context: `TrialSpawner#removeOminous`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-49265B0D - AST call getBlockState in TrialSpawnerState.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState.java:201`
- Context: `TrialSpawnerState#calculatePositionAbove`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-37239937 - AST call getBlockEntity in BlockBehaviour.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/state/BlockBehaviour.java:188`
- Context: `BlockBehaviour#onExplosionHit`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-1582DB95 - AST call getBlockEntity in BlockInWorld.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/state/pattern/BlockInWorld.java:34`
- Context: `BlockInWorld#getEntity`
- Receiver: `this.level`
- Method: `getBlockEntity`
- Evidence: `this.level.getBlockEntity(this.pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-FF813EA5 - AST call setBlockEntity in ImposterProtoChunk.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: set-block-entity
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/ImposterProtoChunk.java:119`
- Context: `ImposterProtoChunk#setBlockEntity`
- Receiver: `this.wrapped`
- Method: `setBlockEntity`
- Evidence: `this.wrapped.setBlockEntity(blockEntity)`
- Suggested fix: Ensure the target chunk is loaded and owned, or resume on the owning region before mutation.

### AO-AST-99A774BD - AST call setBlockEntity in LevelChunk.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: set-block-entity
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/LevelChunk.java:199`
- Context: `LevelChunk#<init>`
- Receiver: `this`
- Method: `setBlockEntity`
- Evidence: `this.setBlockEntity(blockEntity)`
- Suggested fix: Ensure the target chunk is loaded and owned, or resume on the owning region before mutation.

### AO-AST-53181093 - AST call getBlockEntity in LevelChunk.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/LevelChunk.java:414`
- Context: `LevelChunk#setBlockState`
- Receiver: `this.level`
- Method: `getBlockEntity`
- Evidence: `this.level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-C3F7C7AC - AST call setBlockEntity in LevelChunk.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: set-block-entity
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/LevelChunk.java:515`
- Context: `LevelChunk#addAndRegisterBlockEntity`
- Receiver: `this`
- Method: `setBlockEntity`
- Evidence: `this.setBlockEntity(blockEntity)`
- Suggested fix: Ensure the target chunk is loaded and owned, or resume on the owning region before mutation.

### AO-AST-A6F76BC0 - AST call getBlockState in LevelChunk.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/LevelChunk.java:977`
- Context: `BoundTickingBlockEntity#tick`
- Receiver: `LevelChunk.this`
- Method: `getBlockState`
- Evidence: `LevelChunk.this.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-477CC1C3 - AST call getBlockEntity in UpgradeData.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/UpgradeData.java:352`
- Context: `#updateShape`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-5DA9B7FA - AST call getBlockEntity in UpgradeData.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/UpgradeData.java:353`
- Context: `#updateShape`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(offsetPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-2AB099F1 - AST call setBlockEntity in SerializableChunkData.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: set-block-entity
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/storage/SerializableChunkData.java:688`
- Context: `SerializableChunkData#postLoadChunk`
- Receiver: `chunk`
- Method: `setBlockEntity`
- Evidence: `chunk.setBlockEntity(blockEntity)`
- Suggested fix: Ensure the target chunk is loaded and owned, or resume on the owning region before mutation.

### AO-AST-4F888969 - AST call getBlockEntity in DesertWellFeature.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/DesertWellFeature.java:111`
- Context: `DesertWellFeature#placeSusSand`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos, BlockEntityType.BRUSHABLE_BLOCK)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-53116C91 - AST call getBlockEntity in EndGatewayFeature.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/EndGatewayFeature.java:30`
- Context: `EndGatewayFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockEntity`
- Evidence: `worldGenLevel.getBlockEntity(blockPos2)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-4BA07A3B - AST call getBlockEntity in MonsterRoomFeature.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/MonsterRoomFeature.java:122`
- Context: `MonsterRoomFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockEntity`
- Evidence: `worldGenLevel.getBlockEntity(blockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-352D79EB - AST call getBlockEntity in BeehiveDecorator.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/treedecorators/BeehiveDecorator.java:59`
- Context: `BeehiveDecorator#place`
- Receiver: `context.level()`
- Method: `getBlockEntity`
- Evidence: `context.level().getBlockEntity(optional.get(), BlockEntityType.BEEHIVE)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-791E432C - AST call getBlockEntity in StructurePiece.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java:208`
- Context: `StructurePiece#placeCraftBlockEntity`
- Receiver: `levelAccessor`
- Method: `getBlockEntity`
- Evidence: `levelAccessor.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-4993D840 - AST call getBlockEntity in StructurePiece.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java:232`
- Context: `StructurePiece#setCraftLootTable`
- Receiver: `levelAccessor`
- Method: `getBlockEntity`
- Evidence: `levelAccessor.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-F571D4FC - AST call getBlockEntity in DesertPyramidStructure.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/DesertPyramidStructure.java:80`
- Context: `DesertPyramidStructure#placeSuspiciousSand`
- Receiver: `worldGenLevel`
- Method: `getBlockEntity`
- Evidence: `worldGenLevel.getBlockEntity(pos, BlockEntityType.BRUSHABLE_BLOCK)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-BF8592E5 - AST call getBlockEntity in StructureTemplate.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java:119`
- Context: `StructureTemplate#fillFromWorld`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos3)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-E0FD7CAB - AST call getBlockEntity in StructureTemplate.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java:329`
- Context: `StructureTemplate#placeInWorld`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-87172B15 - AST call getBlockEntity in StructureTemplate.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java:416`
- Context: `StructureTemplate#placeInWorld`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos4)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-CFB40D9D - AST call getBlockEntity in WaterFluid.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/WaterFluid.java:90`
- Context: `WaterFluid#beforeDestroyingBlock`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-BF7B44A6 - AST call getBlockEntity in WaterFluid.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/WaterFluid.java:97`
- Context: `WaterFluid#beforeDestroyingBlock`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-5CE31482 - AST call getBlockEntity in MapBanner.java
- Severity: high
- Category: entity movement/teleport/player tick
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/saveddata/maps/MapBanner.java:26`
- Context: `MapBanner#fromWorld`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-71B51C84 - AST call getBlockState in SpreadPlayersCommand.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/SpreadPlayersCommand.java:330`
- Context: `Position#getSpawnY`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8E220C7C - AST call getBlockState in SpreadPlayersCommand.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/SpreadPlayersCommand.java:332`
- Context: `Position#getSpawnY`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-575036F7 - AST call getBlockState in SpreadPlayersCommand.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/SpreadPlayersCommand.java:336`
- Context: `Position#getSpawnY`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1444F78B - AST call getBlockState in PlayerSpawnFinder.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/PlayerSpawnFinder.java:170`
- Context: `PlayerSpawnFinder#getOverworldRespawnPos`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A386E5B9 - AST call getBlockState in ParticleUtils.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/util/ParticleUtils.java:78`
- Context: `ParticleUtils#spawnParticleInBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DB6CBF4C - AST call getBlockState in ParticleUtils.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/util/ParticleUtils.java:96`
- Context: `ParticleUtils#spawnParticles`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(BlockPos.containing(d4, d5, d6).below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-AD06203E - AST call getBlockState in ParticleUtils.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/util/ParticleUtils.java:104`
- Context: `ParticleUtils#spawnSmashAttackParticles`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-231D02BF - AST call getBlockState in SpawnUtil.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/util/SpawnUtil.java:96`
- Context: `SpawnUtil#moveToPossibleSpawnPosition`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6AD07B85 - AST call getBlockState in SpawnUtil.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/util/SpawnUtil.java:101`
- Context: `SpawnUtil#moveToPossibleSpawnPosition`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-25C1FDEA - AST call getBlockState in WeavingMobEffect.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/effect/WeavingMobEffect.java:41`
- Context: `WeavingMobEffect#spawnCobwebsRandomlyAround`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5E339E89 - AST call getBlockState in WeavingMobEffect.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/effect/WeavingMobEffect.java:42`
- Context: `WeavingMobEffect#spawnCobwebsRandomlyAround`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-AFB9BFF7 - AST call getBlockState in AxeItem.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/AxeItem.java:139`
- Context: `AxeItem#spawnSoundAndParticle`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(connectedBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-77301533 - AST call getBlockState in SpawnEggItem.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/SpawnEggItem.java:60`
- Context: `SpawnEggItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B92C5D42 - AST call getBlockState in SpawnEggItem.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/SpawnEggItem.java:137`
- Context: `SpawnEggItem#use`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6BF501B5 - AST call getBlockState in NaturalSpawner.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/NaturalSpawner.java:450`
- Context: `NaturalSpawner#isInNetherFortressBounds`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-37047B28 - AST call getBlockState in NaturalSpawner.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/NaturalSpawner.java:559`
- Context: `NaturalSpawner#getTopNonCollidingPos`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4F298C37 - AST call getBlockState in NaturalSpawner.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/NaturalSpawner.java:563`
- Context: `NaturalSpawner#getTopNonCollidingPos`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-06943270 - AST call getBlockState in BeehiveBlock.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BeehiveBlock.java:232`
- Context: `BeehiveBlock#trySpawnDripParticles`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-05875D04 - AST call scheduleTick in FrogspawnBlock.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FrogspawnBlock.java:59`
- Context: `FrogspawnBlock#onPlace`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, getFrogspawnHatchDelay(level.getRandom()))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-B3427111 - AST call getFluidState in FrogspawnBlock.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FrogspawnBlock.java:100`
- Context: `FrogspawnBlock#mayPlaceOn`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-17E2EB8E - AST call getFluidState in FrogspawnBlock.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FrogspawnBlock.java:101`
- Context: `FrogspawnBlock#mayPlaceOn`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.above())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-D67325E0 - AST call getBlockState in PointedDripstoneBlock.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:311`
- Context: `PointedDripstoneBlock#spawnFallingStalactite`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-21813890 - AST call getFluidState in RespawnAnchorBlock.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/RespawnAnchorBlock.java:138`
- Context: `RespawnAnchorBlock#isWaterThatWouldFlow`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-2FE309B4 - AST call getFluidState in RespawnAnchorBlock.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/RespawnAnchorBlock.java:148`
- Context: `RespawnAnchorBlock#isWaterThatWouldFlow`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.below())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-4FA8DAD4 - AST call getFluidState in RespawnAnchorBlock.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/RespawnAnchorBlock.java:158`
- Context: `RespawnAnchorBlock#explode`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos2.above())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-6ECC9BF2 - AST call getBlockState in EndDragonFight.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/dimension/end/EndDragonFight.java:449`
- Context: `EndDragonFight#spawnExitPortal`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(this.portalLocation)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-EC3E4B20 - AST call getBlockState in PatrolSpawner.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/PatrolSpawner.java:105`
- Context: `PatrolSpawner#spawnPatrolMember`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-19D17717 - AST call getBlockState in PhantomSpawner.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/PhantomSpawner.java:57`
- Context: `PhantomSpawner#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-429BD9A1 - AST call getFluidState in PhantomSpawner.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/PhantomSpawner.java:58`
- Context: `PhantomSpawner#tick`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos1)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-125EF9D0 - AST call getBlockState in BambooFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/BambooFeature.java:51`
- Context: `BambooFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(mutableBlockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4424BD68 - AST call getBlockState in BlockBlobFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/BlockBlobFeature.java:25`
- Context: `BlockBlobFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5EED649E - AST call getBlockState in BlueIceFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/BlueIceFeature.java:25`
- Context: `BlueIceFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9544A989 - AST call getBlockState in BlueIceFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/BlueIceFeature.java:25`
- Context: `BlueIceFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-79B31FC2 - AST call getBlockState in BlueIceFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/BlueIceFeature.java:31`
- Context: `BlueIceFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1CD4B9A2 - AST call getBlockState in BlueIceFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/BlueIceFeature.java:53`
- Context: `BlueIceFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FDDF2E14 - AST call getBlockState in BlueIceFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/BlueIceFeature.java:56`
- Context: `BlueIceFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1.relative(direction1))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E7BE259A - AST call getBlockState in BonusChestFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/BonusChestFeature.java:39`
- Context: `BonusChestFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(heightmapPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CC4D9EA5 - AST call getBlockState in ChorusPlantFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/ChorusPlantFeature.java:21`
- Context: `ChorusPlantFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A48F98CA - AST call getBlockState in DesertWellFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/DesertWellFeature.java:39`
- Context: `DesertWellFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1E4BAB19 - AST call getBlockState in FillLayerFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/FillLayerFeature.java:27`
- Context: `FillLayerFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-519C36C3 - AST call getBlockState in GeodeFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/GeodeFeature.java:64`
- Context: `GeodeFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6E728D12 - AST call getFluidState in GeodeFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/GeodeFeature.java:118`
- Context: `GeodeFeature#place`
- Receiver: `worldGenLevel`
- Method: `getFluidState`
- Evidence: `worldGenLevel.getFluidState(blockPos4)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-4988FD9E - AST call scheduleTick in GeodeFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/GeodeFeature.java:120`
- Context: `GeodeFeature#place`
- Receiver: `worldGenLevel`
- Method: `scheduleTick`
- Evidence: `worldGenLevel.scheduleTick(blockPos4, fluidState.getType(), 0)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-49D4042F - AST call getBlockState in GeodeFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/GeodeFeature.java:156`
- Context: `GeodeFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos5)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B2A4C54F - AST call getBlockState in GlowstoneFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/GlowstoneFeature.java:26`
- Context: `GlowstoneFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-52FAE2D5 - AST call getBlockState in GlowstoneFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/GlowstoneFeature.java:36`
- Context: `GlowstoneFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3FFC5DAF - AST call getBlockState in GlowstoneFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/GlowstoneFeature.java:40`
- Context: `GlowstoneFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C539D89C - AST call getBlockState in HugeFungusFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java:32`
- Context: `HugeFungusFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4288CD16 - AST call getBlockState in IceSpikeFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/IceSpikeFeature.java:27`
- Context: `IceSpikeFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F0C3023C - AST call getBlockState in IceSpikeFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/IceSpikeFeature.java:48`
- Context: `IceSpikeFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.offset(i3, i2, i4))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4A091D92 - AST call getBlockState in IceSpikeFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/IceSpikeFeature.java:54`
- Context: `IceSpikeFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.offset(i3, -i2, i4))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D35D62B8 - AST call getBlockState in IceSpikeFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/IceSpikeFeature.java:80`
- Context: `IceSpikeFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B070EF89 - AST call getBlockState in KelpFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/KelpFeature.java:27`
- Context: `KelpFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4E2A0127 - AST call getBlockState in KelpFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/KelpFeature.java:33`
- Context: `KelpFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-AF1AC848 - AST call getBlockState in KelpFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/KelpFeature.java:34`
- Context: `KelpFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-EF3156A0 - AST call getBlockState in KelpFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/KelpFeature.java:44`
- Context: `KelpFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos2.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C8E8B200 - AST call getBlockState in LakeFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/LakeFeature.java:75`
- Context: `LakeFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.offset(i5, i7, i6))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5809EFCC - AST call getBlockState in LakeFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/LakeFeature.java:80`
- Context: `LakeFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.offset(i5, i7, i6))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-41A5FD0E - AST call getBlockState in LakeFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/LakeFeature.java:93`
- Context: `LakeFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2EE1B650 - AST call scheduleTick in LakeFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/LakeFeature.java:97`
- Context: `LakeFeature#place`
- Receiver: `worldGenLevel`
- Method: `scheduleTick`
- Evidence: `worldGenLevel.scheduleTick(blockPos1, AIR.getBlock(), 0)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-C74E815A - AST call getBlockState in LakeFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/LakeFeature.java:121`
- Context: `LakeFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.offset(i6, i8, i7xx))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D21CC2EE - AST call getBlockState in LakeFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/LakeFeature.java:139`
- Context: `LakeFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos3)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-846526FD - AST call getBlockState in MonsterRoomFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/MonsterRoomFeature.java:53`
- Context: `MonsterRoomFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4B869780 - AST call getBlockState in MonsterRoomFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/MonsterRoomFeature.java:77`
- Context: `MonsterRoomFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1x)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-517F5CF3 - AST call getBlockState in MonsterRoomFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/MonsterRoomFeature.java:79`
- Context: `MonsterRoomFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1x.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FE1FC413 - AST call getBlockState in MonsterRoomFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/MonsterRoomFeature.java:105`
- Context: `MonsterRoomFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos2.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B40966FF - AST call getBlockState in MultifaceGrowthFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/MultifaceGrowthFeature.java:25`
- Context: `MultifaceGrowthFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-98F0C260 - AST call getBlockState in MultifaceGrowthFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/MultifaceGrowthFeature.java:30`
- Context: `MultifaceGrowthFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-29C318E6 - AST call getBlockState in MultifaceGrowthFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/MultifaceGrowthFeature.java:42`
- Context: `MultifaceGrowthFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DAF75EDD - AST call getBlockState in NetherForestVegetationFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/NetherForestVegetationFeature.java:21`
- Context: `NetherForestVegetationFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3EAD4707 - AST call getBlockState in ReplaceBlobsFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/ReplaceBlobsFeature.java:42`
- Context: `ReplaceBlobsFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7CD2A713 - AST call getBlockState in ReplaceBlockFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/ReplaceBlockFeature.java:22`
- Context: `ReplaceBlockFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-83221905 - AST call getBlockState in RootSystemFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/RootSystemFeature.java:24`
- Context: `RootSystemFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2BDBD5F5 - AST call getBlockState in ScatteredOreFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/ScatteredOreFeature.java:29`
- Context: `ScatteredOreFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A0C8180C - AST call getBlockState in SculkPatchFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SculkPatchFeature.java:50`
- Context: `SculkPatchFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9F1BAF46 - AST call getBlockState in SculkPatchFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SculkPatchFeature.java:58`
- Context: `SculkPatchFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos2)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6A2F12FF - AST call getBlockState in SculkPatchFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SculkPatchFeature.java:59`
- Context: `SculkPatchFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos2.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-09A56B8B - AST call getBlockState in SeaPickleFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SeaPickleFeature.java:33`
- Context: `SeaPickleFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-ECB705E9 - AST call getBlockState in SeagrassFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SeagrassFeature.java:31`
- Context: `SeagrassFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3CD57D9C - AST call getBlockState in SeagrassFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SeagrassFeature.java:38`
- Context: `SeagrassFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos2)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D0FDC502 - AST call scheduleTick in SimpleBlockFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java:37`
- Context: `SimpleBlockFeature#place`
- Receiver: `worldGenLevel`
- Method: `scheduleTick`
- Evidence: `worldGenLevel.scheduleTick(blockPos, worldGenLevel.getBlockState(blockPos).getBlock(), 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-6053C816 - AST call getBlockState in SimpleBlockFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java:37`
- Context: `SimpleBlockFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FCCB9D70 - AST call getBlockState in SnowAndFreezeFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SnowAndFreezeFeature.java:41`
- Context: `SnowAndFreezeFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(mutableBlockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F0EDCA7C - AST call getBlockState in SpringFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SpringFeature.java:20`
- Context: `SpringFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BDF23869 - AST call getBlockState in SpringFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SpringFeature.java:22`
- Context: `SpringFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E35F049E - AST call getBlockState in SpringFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SpringFeature.java:25`
- Context: `SpringFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-63A56D20 - AST call getBlockState in SpringFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SpringFeature.java:31`
- Context: `SpringFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.west())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4B190560 - AST call getBlockState in SpringFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SpringFeature.java:35`
- Context: `SpringFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.east())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-57F7613E - AST call getBlockState in SpringFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SpringFeature.java:39`
- Context: `SpringFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.north())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DB68BA80 - AST call getBlockState in SpringFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SpringFeature.java:43`
- Context: `SpringFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.south())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2766EA02 - AST call getBlockState in SpringFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SpringFeature.java:47`
- Context: `SpringFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F6AA1823 - AST call scheduleTick in SpringFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SpringFeature.java:74`
- Context: `SpringFeature#place`
- Receiver: `worldGenLevel`
- Method: `scheduleTick`
- Evidence: `worldGenLevel.scheduleTick(blockPos, springConfiguration.state.getType(), 0)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-8B9B9939 - AST call getBlockState in WeepingVinesFeature.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/WeepingVinesFeature.java:31`
- Context: `WeepingVinesFeature#place`
- Receiver: `worldGenLevel`
- Method: `getBlockState`
- Evidence: `worldGenLevel.getBlockState(blockPos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9A0FE0DD - AST call getFluidState in StructurePiece.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java:188`
- Context: `StructurePiece#placeBlock`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(worldPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-87F56C6C - AST call scheduleTick in StructurePiece.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java:190`
- Context: `StructurePiece#placeBlock`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(worldPos, fluidState.getType(), 0)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-73B26434 - AST call getBlockState in StructurePiece.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java:248`
- Context: `StructurePiece#getBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(worldPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-29F645A4 - AST call getBlockState in StructurePiece.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java:403`
- Context: `StructurePiece#fillColumnDown`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(worldPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8F30AB7A - AST call getBlockState in StructurePiece.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java:423`
- Context: `StructurePiece#reorient`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2EFDC6CB - AST call getBlockState in StructurePiece.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java:443`
- Context: `StructurePiece#reorient`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F9FB012C - AST call getBlockState in StructurePiece.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java:448`
- Context: `StructurePiece#reorient`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8A670256 - AST call getBlockState in StructurePiece.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java:453`
- Context: `StructurePiece#reorient`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B93E9C7E - AST call getBlockState in StructurePiece.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java:465`
- Context: `StructurePiece#createChest`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DDE871E2 - AST call getBlockState in StructurePiece.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java:492`
- Context: `StructurePiece#createDispenser`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(worldPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CFDE53C3 - AST call getBlockState in BuriedTreasurePieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/BuriedTreasurePieces.java:49`
- Context: `BuriedTreasurePiece#postProcess`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2C197C91 - AST call getBlockState in BuriedTreasurePieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/BuriedTreasurePieces.java:50`
- Context: `BuriedTreasurePiece#postProcess`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2DD5AC27 - AST call getBlockState in BuriedTreasurePieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/BuriedTreasurePieces.java:60`
- Context: `BuriedTreasurePiece#postProcess`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F5259DC4 - AST call getBlockState in BuriedTreasurePieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/BuriedTreasurePieces.java:63`
- Context: `BuriedTreasurePiece#postProcess`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-19D96510 - AST call getBlockState in IglooPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/IglooPieces.java:137`
- Context: `IglooPiece#postProcess`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos3.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2589A646 - AST call getBlockState in MineshaftPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java:333`
- Context: `MineShaftCorridor#createChest`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(worldPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B1D699BE - AST call getBlockState in MineshaftPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java:333`
- Context: `MineShaftCorridor#createChest`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(worldPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2531ECB4 - AST call getBlockState in MineshaftPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java:455`
- Context: `MineShaftCorridor#fillColumnDown`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(worldPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D0F7E05B - AST call getBlockState in MineshaftPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java:459`
- Context: `MineShaftCorridor#fillColumnDown`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(worldPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-AD110E89 - AST call getBlockState in MineshaftPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java:478`
- Context: `MineShaftCorridor#fillPillarDownOrChainUp`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(worldPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E695419E - AST call getBlockState in MineshaftPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java:490`
- Context: `MineShaftCorridor#fillPillarDownOrChainUp`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(worldPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F2F77196 - AST call getBlockState in MineshaftPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java:565`
- Context: `MineShaftCorridor#hasSturdyNeighbours`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(worldPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-772CEADD - AST call getBlockState in MineshaftPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java:901`
- Context: `MineShaftPiece#isInInvalidLocation`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.set(i, max1, i1))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-109ECB35 - AST call getBlockState in MineshaftPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java:905`
- Context: `MineShaftPiece#isInInvalidLocation`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.set(i, min1, i1))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5812365B - AST call getBlockState in MineshaftPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java:913`
- Context: `MineShaftPiece#isInInvalidLocation`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.set(i, i1, max2))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-EF2EEBC7 - AST call getBlockState in MineshaftPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java:917`
- Context: `MineShaftPiece#isInInvalidLocation`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.set(i, i1, min2))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1591CD72 - AST call getBlockState in MineshaftPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java:925`
- Context: `MineShaftPiece#isInInvalidLocation`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.set(max, i1, i))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-23C0BBFD - AST call getBlockState in MineshaftPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java:929`
- Context: `MineShaftPiece#isInInvalidLocation`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.set(min, i1, i))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1B60AF32 - AST call getBlockState in MineshaftPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java:942`
- Context: `MineShaftPiece#setPlanksBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(worldPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-646CC873 - AST call scheduleTick in NetherFortressPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressPieces.java:585`
- Context: `CastleEntrance#postProcess`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(worldPos, Fluids.LAVA, 0)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-DA4652DE - AST call getBlockState in NetherFossilPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/NetherFossilPieces.java:95`
- Context: `NetherFossilPiece#placeDriedGhast`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-EFA497A5 - AST call getFluidState in OceanRuinPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/OceanRuinPieces.java:320`
- Context: `OceanRuinPiece#handleDataMarker`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-615C5A64 - AST call getBlockState in OceanRuinPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/OceanRuinPieces.java:377`
- Context: `OceanRuinPiece#getHeight`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4CDF3F62 - AST call getFluidState in OceanRuinPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/OceanRuinPieces.java:379`
- Context: `OceanRuinPiece#getHeight`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(mutableBlockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-6AC9DFB3 - AST call getFluidState in OceanRuinPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/OceanRuinPieces.java:381`
- Context: `OceanRuinPiece#getHeight`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(mutableBlockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-C61B304E - AST call getBlockState in OceanRuinPieces.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/OceanRuinPieces.java:384`
- Context: `OceanRuinPiece#getHeight`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C49F35E2 - AST call getBlockState in RuinedPortalPiece.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/RuinedPortalPiece.java:171`
- Context: `RuinedPortalPiece#maybeAddVines`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9CEF01D2 - AST call getBlockState in RuinedPortalPiece.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/RuinedPortalPiece.java:175`
- Context: `RuinedPortalPiece#maybeAddVines`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7D9FAF6B - AST call getBlockState in RuinedPortalPiece.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/RuinedPortalPiece.java:186`
- Context: `RuinedPortalPiece#maybeAddLeavesAbove`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A6FD0E0C - AST call getBlockState in RuinedPortalPiece.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/RuinedPortalPiece.java:186`
- Context: `RuinedPortalPiece#maybeAddLeavesAbove`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7FD29BFE - AST call getBlockState in RuinedPortalPiece.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/RuinedPortalPiece.java:195`
- Context: `RuinedPortalPiece#addNetherrackDripColumnsBelowPortal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-51A563C8 - AST call getBlockState in RuinedPortalPiece.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/RuinedPortalPiece.java:252`
- Context: `RuinedPortalPiece#canBlockBeReplacedByNetherrackOrMagma`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-EAB08A00 - AST call getBlockState in WoodlandMansionStructure.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionStructure.java:70`
- Context: `WoodlandMansionStructure#afterPlace`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1730250D - AST call getBlockState in LavaSubmergedBlockProcessor.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/templatesystem/LavaSubmergedBlockProcessor.java:24`
- Context: `LavaSubmergedBlockProcessor#processBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-88BE9EB2 - AST call getBlockState in ProtectedBlockProcessor.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/templatesystem/ProtectedBlockProcessor.java:31`
- Context: `ProtectedBlockProcessor#processBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(relativeBlockInfo.pos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-042302C8 - AST call getBlockState in RuleProcessor.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/templatesystem/RuleProcessor.java:34`
- Context: `RuleProcessor#processBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(relativeBlockInfo.pos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-69BF1296 - AST call getBlockState in StructureTemplate.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java:117`
- Context: `StructureTemplate#fillFromWorld`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos3)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BBD3AA81 - AST call getFluidState in StructureTemplate.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java:299`
- Context: `StructureTemplate#placeInWorld`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-786BD61C - AST call getFluidState in StructureTemplate.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java:366`
- Context: `StructureTemplate#placeInWorld`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos1)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-4E76A287 - AST call getFluidState in StructureTemplate.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java:370`
- Context: `StructureTemplate#placeInWorld`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos2)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-0BD5196C - AST call getBlockState in StructureTemplate.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java:377`
- Context: `StructureTemplate#placeInWorld`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-04C45336 - AST call getBlockState in StructureTemplate.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java:406`
- Context: `StructureTemplate#placeInWorld`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos4)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-42ADF19F - AST call getBlockState in StructureTemplate.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java:460`
- Context: `StructureTemplate#updateShapeAtEdge`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-15D40B2D - AST call getBlockState in StructureTemplate.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java:461`
- Context: `StructureTemplate#updateShapeAtEdge`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-ED9A0FB8 - AST call setType in RegionLoadTestCommand.java
- Severity: high
- Category: natural spawning/entity add/structure sync load
- Sink: bukkit-block-write
- Location: `tools/region-load-test-plugin/src/main/java/io/multipaper/regionloadtest/RegionLoadTestCommand.java:1260`
- Context: `RegionLoadTestCommand#setBlockTypeIfLoaded`
- Receiver: `world.getBlockAt(x, y, z)`
- Method: `setType`
- Evidence: `world.getBlockAt(x, y, z).setType(material, applyPhysics)`
- Suggested fix: Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region.

### AO-AST-22120BD7 - AST call getBlockState in ChunkHolder.java
- Severity: high
- Category: player chunk send/post-processing/stale holder broadcast
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ChunkHolder.java:329`
- Context: `ChunkHolder#broadcastChanges`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FD4CC389 - AST call getBlockState in WireHandler.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/alternate/current/wire/WireHandler.java:285`
- Context: `WireHandler#getOrAddNode`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D97F1922 - AST call getBlockState in WireHandler.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/alternate/current/wire/WireHandler.java:354`
- Context: `WireHandler#revalidateNode`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F789FBD2 - AST call getBlockState in WireNode.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/alternate/current/wire/WireNode.java:132`
- Context: `WireNode#setPower`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-94C9C573 - AST call getBlockState in GameTestHelper.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/GameTestHelper.java:495`
- Context: `GameTestHelper#assertRedstoneSignal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B358CB69 - AST call getBlockState in AbstractMinecart.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/AbstractMinecart.java:527`
- Context: `AbstractMinecart#getRedstoneDirection`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-98115FFB - AST call getBlockState in AbstractMinecart.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/AbstractMinecart.java:555`
- Context: `AbstractMinecart#isRedstoneConductor`
- Receiver: `this.level()`
- Method: `getBlockState`
- Evidence: `this.level().getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7DD89848 - AST call scheduleTick in ScheduledTickAccess.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/ScheduledTickAccess.java:21`
- Context: `ScheduledTickAccess#scheduleTick`
- Receiver: `serverLevel`
- Method: `scheduleTick`
- Evidence: `serverLevel.scheduleTick(pos, block, delay, priority)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-004B73CB - AST call scheduleTick in ScheduledTickAccess.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/ScheduledTickAccess.java:26`
- Context: `ScheduledTickAccess#scheduleTick`
- Receiver: `serverLevel`
- Method: `scheduleTick`
- Evidence: `serverLevel.scheduleTick(pos, block, delay)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-5E1BB76B - AST call scheduleTick in ScheduledTickAccess.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/ScheduledTickAccess.java:33`
- Context: `ScheduledTickAccess#scheduleTick`
- Receiver: `serverLevel`
- Method: `scheduleTick`
- Evidence: `serverLevel.scheduleTick(pos, fluid, delay, priority)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-DD569D1B - AST call scheduleTick in ScheduledTickAccess.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/ScheduledTickAccess.java:38`
- Context: `ScheduledTickAccess#scheduleTick`
- Receiver: `serverLevel`
- Method: `scheduleTick`
- Evidence: `serverLevel.scheduleTick(pos, fluid, delay)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-DC166F48 - AST call scheduleTick in AmethystClusterBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/AmethystClusterBlock.java:80`
- Context: `AmethystClusterBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-6D6D6659 - AST call scheduleTick in BambooStalkBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BambooStalkBlock.java:148`
- Context: `BambooStalkBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-61699558 - AST call scheduleTick in BarrierBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BarrierBlock.java:66`
- Context: `BarrierBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-8C445FAE - AST call scheduleTick in BaseCoralPlantTypeBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseCoralPlantTypeBlock.java:37`
- Context: `BaseCoralPlantTypeBlock#tryScheduleDieTick`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 60 + random.nextInt(40))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-B187FDBA - AST call scheduleTick in BaseCoralPlantTypeBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseCoralPlantTypeBlock.java:79`
- Context: `BaseCoralPlantTypeBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-FFDE7C50 - AST call scheduleTick in BaseCoralWallFanBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseCoralWallFanBlock.java:69`
- Context: `BaseCoralWallFanBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-870E1E6E - AST call scheduleTick in BaseRailBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseRailBlock.java:340`
- Context: `BaseRailBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-E6BF95A5 - AST call getBlockState in BeehiveBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BeehiveBlock.java:345`
- Context: `BeehiveBlock#updateShape`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(neighborPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-AD4E4CD6 - AST call scheduleTick in BigDripleafBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafBlock.java:159`
- Context: `BigDripleafBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-77526509 - AST call scheduleTick in BigDripleafStemBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafStemBlock.java:87`
- Context: `BigDripleafStemBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-A23D201A - AST call scheduleTick in BigDripleafStemBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafStemBlock.java:91`
- Context: `BigDripleafStemBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-4DF5E9EE - AST call scheduleTick in BrushableBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BrushableBlock.java:78`
- Context: `BrushableBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 2)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-63DF7E92 - AST call scheduleTick in BubbleColumnBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BubbleColumnBlock.java:158`
- Context: `BubbleColumnBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-5A3C2B54 - AST call scheduleTick in BubbleColumnBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BubbleColumnBlock.java:162`
- Context: `BubbleColumnBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 5)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-C56C3EFA - AST call scheduleTick in CactusBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CactusBlock.java:110`
- Context: `CactusBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-27D6164F - AST call scheduleTick in CampfireBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CampfireBlock.java:142`
- Context: `CampfireBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-404856B5 - AST call scheduleTick in CandleBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CandleBlock.java:121`
- Context: `CandleBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-05EFDA5B - AST call scheduleTick in ChainBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChainBlock.java:63`
- Context: `ChainBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-6E6398D9 - AST call scheduleTick in ChestBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChestBlock.java:203`
- Context: `ChestBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-0BB94B88 - AST call scheduleTick in ChorusFlowerBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChorusFlowerBlock.java:176`
- Context: `ChorusFlowerBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-ACDD295D - AST call scheduleTick in ChorusPlantBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChorusPlantBlock.java:74`
- Context: `ChorusPlantBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-E577A0B4 - AST call getBlockEntity in CommandBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CommandBlock.java:65`
- Context: `CommandBlock#neighborChanged`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-598440F3 - AST call getBlockEntity in ComparatorBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ComparatorBlock.java:168`
- Context: `ComparatorBlock#checkTickOnNeighbor`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-FF2E8FA9 - AST call scheduleTick in ComparatorBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ComparatorBlock.java:172`
- Context: `ComparatorBlock#checkTickOnNeighbor`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 2, tickPriority)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-2649E176 - AST call scheduleTick in ConduitBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ConduitBlock.java:78`
- Context: `ConduitBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-E7FAD26B - AST call scheduleTick in CopperGolemStatueBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CopperGolemStatueBlock.java:181`
- Context: `CopperGolemStatueBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-5D29A9D1 - AST call scheduleTick in CoralBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CoralBlock.java:61`
- Context: `CoralBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 60 + random.nextInt(40))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-809740CD - AST call scheduleTick in CoralFanBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CoralFanBlock.java:66`
- Context: `CoralFanBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-761A0096 - AST call scheduleTick in CoralPlantBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CoralPlantBlock.java:70`
- Context: `CoralPlantBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-DB77E43E - AST call scheduleTick in CoralWallFanBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CoralWallFanBlock.java:65`
- Context: `CoralWallFanBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-5827CC76 - AST call getBlockEntity in CrafterBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CrafterBlock.java:76`
- Context: `CrafterBlock#neighborChanged`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-507401E9 - AST call scheduleTick in CrafterBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CrafterBlock.java:78`
- Context: `CrafterBlock#neighborChanged`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 4)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-A6DE179E - AST call scheduleTick in CreakingHeartBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CreakingHeartBlock.java:92`
- Context: `CreakingHeartBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-C5ACE814 - AST call scheduleTick in DecoratedPotBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DecoratedPotBlock.java:80`
- Context: `DecoratedPotBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-C702A961 - AST call getBlockEntity in DiodeBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DiodeBlock.java:115`
- Context: `DiodeBlock#neighborChanged`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-6FFC8E23 - AST call scheduleTick in DiodeBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DiodeBlock.java:145`
- Context: `DiodeBlock#checkTickOnNeighbor`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, this.getDelay(state), tickPriority)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-3DF4C4EC - AST call scheduleTick in DirtPathBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DirtPathBlock.java:55`
- Context: `DirtPathBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-CB7C90DE - AST call scheduleTick in DispenserBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DispenserBlock.java:128`
- Context: `DispenserBlock#neighborChanged`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 4)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-8347427B - AST call getBlockState in DoorBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DoorBlock.java:296`
- Context: `DoorBlock#requiresRedstone`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(otherPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-90678E46 - AST call scheduleTick in DriedGhastBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DriedGhastBlock.java:73`
- Context: `DriedGhastBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-68403306 - AST call scheduleTick in EnderChestBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/EnderChestBlock.java:189`
- Context: `EnderChestBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-5DE7DD3B - AST call scheduleTick in FallingBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FallingBlock.java:44`
- Context: `FallingBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, this.getDelayAfterPlace())`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-BB4F9DB1 - AST call scheduleTick in FarmBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FarmBlock.java:59`
- Context: `FarmBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-4C9ADD88 - AST call scheduleTick in FenceBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FenceBlock.java:108`
- Context: `FenceBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-73879FA0 - AST call scheduleTick in GrowingPlantBodyBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/GrowingPlantBodyBlock.java:45`
- Context: `GrowingPlantBodyBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-0011F896 - AST call scheduleTick in GrowingPlantBodyBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/GrowingPlantBodyBlock.java:53`
- Context: `GrowingPlantBodyBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-60C4367F - AST call scheduleTick in GrowingPlantHeadBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/GrowingPlantHeadBlock.java:102`
- Context: `GrowingPlantHeadBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-A38645E5 - AST call scheduleTick in GrowingPlantHeadBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/GrowingPlantHeadBlock.java:113`
- Context: `GrowingPlantHeadBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-3E0E3E7C - AST call scheduleTick in HangingMossBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HangingMossBlock.java:82`
- Context: `HangingMossBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-85988EAD - AST call scheduleTick in HangingRootsBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HangingRootsBlock.java:85`
- Context: `HangingRootsBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-E48BDC2B - AST call scheduleTick in HeavyCoreBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HeavyCoreBlock.java:54`
- Context: `HeavyCoreBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-09D8DED1 - AST call scheduleTick in IronBarsBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/IronBarsBlock.java:69`
- Context: `IronBarsBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-358978D9 - AST call scheduleTick in LadderBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LadderBlock.java:72`
- Context: `LadderBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-911C97CE - AST call scheduleTick in LanternBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LanternBlock.java:89`
- Context: `LanternBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-03E6CF9A - AST call getBlockState in LeavesBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LeavesBlock.java:73`
- Context: `LeavesBlock#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4A09B680 - AST call scheduleTick in LeavesBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LeavesBlock.java:108`
- Context: `LeavesBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-70DD08D6 - AST call scheduleTick in LeavesBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LeavesBlock.java:113`
- Context: `LeavesBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-A1205481 - AST call getBlockState in LeavesBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LeavesBlock.java:125`
- Context: `LeavesBlock#updateDistance`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6083EB49 - AST call getBlockState in LeavesBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LeavesBlock.java:155`
- Context: `LeavesBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2F9A9B13 - AST call getFluidState in LeavesBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LeavesBlock.java:187`
- Context: `LeavesBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-F120B795 - AST call scheduleTick in LightBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LightBlock.java:102`
- Context: `LightBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-F7CBD11D - AST call scheduleTick in LightningRodBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LightningRodBlock.java:63`
- Context: `LightningRodBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-07109AB2 - AST call scheduleTick in LiquidBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LiquidBlock.java:179`
- Context: `LiquidBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, state.getFluidState().getType(), this.fluid.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-03D38D47 - AST call scheduleTick in LiquidBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LiquidBlock.java:188`
- Context: `LiquidBlock#neighborChanged`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, state.getFluidState().getType(), this.getFlowSpeed(level, pos))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-808EFD58 - AST call scheduleTick in MagmaBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MagmaBlock.java:55`
- Context: `MagmaBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 20)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-51E21003 - AST call getBlockState in MangroveLeavesBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MangroveLeavesBlock.java:34`
- Context: `MangroveLeavesBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BA06F877 - AST call scheduleTick in MangrovePropaguleBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MangrovePropaguleBlock.java:89`
- Context: `MangrovePropaguleBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-DBBF2777 - AST call scheduleTick in MangroveRootsBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MangroveRootsBlock.java:57`
- Context: `MangroveRootsBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-FF00A07C - AST call scheduleTick in MultifaceBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MultifaceBlock.java:135`
- Context: `MultifaceBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-DEBF944E - AST call getBlockState in NoteBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NoteBlock.java:96`
- Context: `NoteBlock#neighborChanged`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6E9A67AB - AST call scheduleTick in ObserverBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ObserverBlock.java:93`
- Context: `ObserverBlock#startSignal`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 2)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-9B84F27F - AST call scheduleTick in PointedDripstoneBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:113`
- Context: `PointedDripstoneBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-2B495806 - AST call scheduleTick in PointedDripstoneBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:124`
- Context: `PointedDripstoneBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 2)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-B4ACB138 - AST call scheduleTick in PointedDripstoneBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:126`
- Context: `PointedDripstoneBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-182995F6 - AST call getBlockState in RedStoneOreBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/RedStoneOreBlock.java:51`
- Context: `RedStoneOreBlock#stepOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3427930A - AST call getBlockState in RedStoneOreBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/RedStoneOreBlock.java:57`
- Context: `RedStoneOreBlock#stepOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5B4B8DA6 - AST call getBlockState in RedStoneOreBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/RedStoneOreBlock.java:139`
- Context: `RedStoneOreBlock#spawnParticles`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DFBE3643 - AST call scheduleTick in RedstoneLampBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/RedstoneLampBlock.java:41`
- Context: `RedstoneLampBlock#neighborChanged`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 4)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-CA2003BC - AST call scheduleTick in RedstoneTorchBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/RedstoneTorchBlock.java:107`
- Context: `RedstoneTorchBlock#tick`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 160)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-71682749 - AST call getBlockState in RedstoneTorchBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/RedstoneTorchBlock.java:107`
- Context: `RedstoneTorchBlock#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A9EDDDB1 - AST call scheduleTick in RedstoneTorchBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/RedstoneTorchBlock.java:127`
- Context: `RedstoneTorchBlock#neighborChanged`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 2)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-083A8E6B - AST call scheduleTick in ScaffoldingBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ScaffoldingBlock.java:106`
- Context: `ScaffoldingBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-13DDAAC3 - AST call scheduleTick in ScaffoldingBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ScaffoldingBlock.java:110`
- Context: `ScaffoldingBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-425A469C - AST call scheduleTick in SculkSensorBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkSensorBlock.java:150`
- Context: `SculkSensorBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-BDFB2B8D - AST call scheduleTick in SculkShriekerBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkShriekerBlock.java:111`
- Context: `SculkShriekerBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-48E789D0 - AST call scheduleTick in SeaPickleBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SeaPickleBlock.java:89`
- Context: `SeaPickleBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-290C25CE - AST call scheduleTick in SeagrassBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SeagrassBlock.java:68`
- Context: `SeagrassBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-CC490963 - AST call scheduleTick in ShelfBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ShelfBlock.java:300`
- Context: `ShelfBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-B3228583 - AST call getBlockState in SideChainPartBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SideChainPartBlock.java:149`
- Context: `Neighbors#createNewNeighbor`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-12526C3B - AST call scheduleTick in SignBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SignBlock.java:68`
- Context: `SignBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-2BAEAA6D - AST call scheduleTick in SlabBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SlabBlock.java:127`
- Context: `SlabBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-79273D0B - AST call scheduleTick in SmallDripleafBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SmallDripleafBlock.java:105`
- Context: `SmallDripleafBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-5BC5A4A7 - AST call scheduleTick in SoulSandBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SoulSandBlock.java:65`
- Context: `SoulSandBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 20)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-B40DDB59 - AST call scheduleTick in StairBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/StairBlock.java:126`
- Context: `StairBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-C32D79F6 - AST call getBlockEntity in StructureBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/StructureBlock.java:72`
- Context: `StructureBlock#neighborChanged`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-1ADBFDBD - AST call scheduleTick in SugarCaneBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SugarCaneBlock.java:83`
- Context: `SugarCaneBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-9586F9C5 - AST call getBlockState in TrapDoorBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TrapDoorBlock.java:149`
- Context: `TrapDoorBlock#neighborChanged`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(abovePos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-949D39C1 - AST call getBlockState in TrapDoorBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TrapDoorBlock.java:154`
- Context: `TrapDoorBlock#neighborChanged`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D2421558 - AST call scheduleTick in TrapDoorBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TrapDoorBlock.java:168`
- Context: `TrapDoorBlock#neighborChanged`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-8169A71D - AST call scheduleTick in TrapDoorBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TrapDoorBlock.java:216`
- Context: `TrapDoorBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-C7CC4632 - AST call getBlockState in TripWireBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TripWireBlock.java:80`
- Context: `TripWireBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.north())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E67208D7 - AST call getBlockState in TripWireBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TripWireBlock.java:81`
- Context: `TripWireBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.east())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-90480392 - AST call getBlockState in TripWireBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TripWireBlock.java:82`
- Context: `TripWireBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.south())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A7681F0A - AST call getBlockState in TripWireBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TripWireBlock.java:83`
- Context: `TripWireBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.west())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D763221D - AST call getBlockState in TripWireBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TripWireBlock.java:135`
- Context: `TripWireBlock#updateSource`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E7018928 - AST call getBlockState in TripWireBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TripWireBlock.java:169`
- Context: `TripWireBlock#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D804642A - AST call getBlockState in TripWireBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TripWireBlock.java:175`
- Context: `TripWireBlock#checkPressed`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B7ADEAB4 - AST call getBlockState in TripWireBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TripWireBlock.java:181`
- Context: `TripWireBlock#checkPressed`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6A2B89F2 - AST call scheduleTick in TripWireBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TripWireBlock.java:233`
- Context: `TripWireBlock#checkPressed`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(new BlockPos(pos), this, 10)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-E68DE7F0 - AST call getBlockState in TripWireHookBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TripWireHookBlock.java:63`
- Context: `TripWireHookBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3E108D65 - AST call getBlockState in TripWireHookBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TripWireHookBlock.java:124`
- Context: `TripWireHookBlock#calculateState`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-772DD94A - AST call scheduleTick in TripWireHookBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TripWireHookBlock.java:145`
- Context: `TripWireHookBlock#calculateState`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, block, 10)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-626CA3C8 - AST call getBlockState in TripWireHookBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TripWireHookBlock.java:189`
- Context: `TripWireHookBlock#calculateState`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-02BA5754 - AST call getBlockState in TripWireHookBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TripWireHookBlock.java:202`
- Context: `TripWireHookBlock#calculateState`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0D18F5EB - AST call getBlockState in VineBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/VineBlock.java:117`
- Context: `VineBlock#isAcceptableNeighbour`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(neighborPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2D004F1D - AST call scheduleTick in WallBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WallBlock.java:146`
- Context: `WallBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-F525180F - AST call scheduleTick in WaterloggedTransparentBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WaterloggedTransparentBlock.java:51`
- Context: `WaterloggedTransparentBlock#updateShape`
- Receiver: `scheduledTickAccess`
- Method: `scheduleTick`
- Evidence: `scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-8DD4C41F - AST call getBlockState in MovingPistonBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/MovingPistonBlock.java:71`
- Context: `MovingPistonBlock#destroy`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4DFADAD7 - AST call getBlockEntity in MovingPistonBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/MovingPistonBlock.java:79`
- Context: `MovingPistonBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-D5A72BE8 - AST call getBlockEntity in MovingPistonBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/MovingPistonBlock.java:105`
- Context: `MovingPistonBlock#getBlockEntity`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-A2C804C2 - AST call getBlockEntity in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:98`
- Context: `PistonBaseBlock#onPlace`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-28C3D347 - AST call getBlockState in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:121`
- Context: `PistonBaseBlock#checkIfExtend`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-84A35B44 - AST call getBlockEntity in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:125`
- Context: `PistonBaseBlock#checkIfExtend`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-D1846CED - AST call getBlockEntity in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:266`
- Context: `PistonBaseBlock#triggerEvent`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(pos.relative(direction))`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-1A673714 - AST call setBlockEntity in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: set-block-entity
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:283`
- Context: `PistonBaseBlock#triggerEvent`
- Receiver: `level`
- Method: `setBlockEntity`
- Evidence: `level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(pos, blockState1, this.defaultBlockState().setValue(FACING, Direction.from3DDataValue(param & 7)), direction, false, true))`
- Suggested fix: Ensure the target chunk is loaded and owned, or resume on the owning region before mutation.

### AO-AST-A231F52C - AST call getBlockState in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:292`
- Context: `PistonBaseBlock#triggerEvent`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8175D36A - AST call getBlockEntity in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:295`
- Context: `PistonBaseBlock#triggerEvent`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-FF3ADB24 - AST call getBlockState in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:324`
- Context: `PistonBaseBlock#triggerEvent`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(headPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E5FF31FA - AST call getBlockState in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:377`
- Context: `PistonBaseBlock#moveBlocks`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-43B3323D - AST call getBlockState in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:393`
- Context: `PistonBaseBlock#moveBlocks`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-12EE5777 - AST call getBlockState in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:434`
- Context: `PistonBaseBlock#moveBlocks`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(brokenPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-361FB712 - AST call getBlockState in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:437`
- Context: `PistonBaseBlock#moveBlocks`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(movedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B2F43D8D - AST call getBlockState in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:439`
- Context: `PistonBaseBlock#moveBlocks`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(movedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B0A434B1 - AST call getBlockState in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:447`
- Context: `PistonBaseBlock#moveBlocks`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos2)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1E7F9598 - AST call getBlockEntity in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: block-entity-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:448`
- Context: `PistonBaseBlock#moveBlocks`
- Receiver: `level`
- Method: `getBlockEntity`
- Evidence: `level.getBlockEntity(blockPos2)`
- Suggested fix: Prefer non-loading access and owner validation before block entity reads.

### AO-AST-30DB19D0 - AST call getBlockState in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:464`
- Context: `PistonBaseBlock#moveBlocks`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(oldPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-800748EF - AST call getBlockState in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:472`
- Context: `PistonBaseBlock#moveBlocks`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(oldPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5CF46999 - AST call setBlockEntity in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: set-block-entity
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:475`
- Context: `PistonBaseBlock#moveBlocks`
- Receiver: `level`
- Method: `setBlockEntity`
- Evidence: `level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(blockPos2, blockState2, allowDesync ? list.get(i1) : blockState1, facing, extending, false))`
- Suggested fix: Ensure the target chunk is loaded and owned, or resume on the owning region before mutation.

### AO-AST-61DF5C28 - AST call setBlockEntity in PistonBaseBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: set-block-entity
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java:495`
- Context: `PistonBaseBlock#moveBlocks`
- Receiver: `level`
- Method: `setBlockEntity`
- Evidence: `level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(blockPos, blockState1, blockState3, facing, true, true))`
- Suggested fix: Ensure the target chunk is loaded and owned, or resume on the owning region before mutation.

### AO-AST-3F728A56 - AST call getBlockState in PistonHeadBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonHeadBlock.java:73`
- Context: `PistonHeadBlock#playerWillDestroy`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8A49DC63 - AST call getBlockState in PistonHeadBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonHeadBlock.java:84`
- Context: `PistonHeadBlock#affectNeighborsAfterRemoval`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FE6560AB - AST call getBlockState in PistonHeadBlock.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonHeadBlock.java:107`
- Context: `PistonHeadBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(state.getValue(FACING).getOpposite()))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1745570F - AST call getBlockState in PistonMovingBlockEntity.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonMovingBlockEntity.java:278`
- Context: `PistonMovingBlockEntity#finalTick`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(this.worldPosition)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8F03287E - AST call getBlockState in PistonMovingBlockEntity.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonMovingBlockEntity.java:313`
- Context: `PistonMovingBlockEntity#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2E8718B2 - AST call getFluidState in FlowingFluid.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/FlowingFluid.java:109`
- Context: `FlowingFluid#getFlow`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(mutableBlockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-D03EC349 - AST call getBlockState in FlowingFluid.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/FlowingFluid.java:114`
- Context: `FlowingFluid#getFlow`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3EAC188D - AST call getFluidState in FlowingFluid.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/FlowingFluid.java:116`
- Context: `FlowingFluid#getFlow`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-FF5866AC - AST call getBlockState in FlowingFluid.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/FlowingFluid.java:154`
- Context: `FlowingFluid#isSolidFace`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(neighborPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-EA1A8751 - AST call getFluidState in FlowingFluid.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/FlowingFluid.java:155`
- Context: `FlowingFluid#isSolidFace`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(neighborPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-71E6B08B - AST call getBlockState in FlowingFluid.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/FlowingFluid.java:203`
- Context: `FlowingFluid#spread`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-34ACAE05 - AST call getBlockState in FlowingFluid.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/FlowingFluid.java:590`
- Context: `FlowingFluid#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5ED261F5 - AST call scheduleTick in FlowingFluid.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/FlowingFluid.java:614`
- Context: `FlowingFluid#tick`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, newLiquid.getType(), spreadDelay)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-88B76B5E - AST call getFluidState in FlowingFluid.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/FlowingFluid.java:626`
- Context: `FlowingFluid#hasSameAbove`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.above())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-DE1C132F - AST call getBlockState in FlowingFluid.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/FlowingFluid.java:688`
- Context: `SpreadContext#getBlockState`
- Receiver: ``
- Method: `getBlockState`
- Evidence: `getBlockState(pos, cacheKey, true)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-58608C73 - AST call getBlockState in FlowingFluid.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/FlowingFluid.java:694`
- Context: `SpreadContext#getBlockState`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D6EB2756 - AST call getBlockState in InstantNeighborUpdater.java
- Severity: high
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/redstone/InstantNeighborUpdater.java:24`
- Context: `InstantNeighborUpdater#neighborChanged`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4E92C14C - AST call getBlockState in CraftWorld.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftWorld.java:776`
- Context: `CraftWorld#generateTree`
- Receiver: `this.world`
- Method: `getBlockState`
- Evidence: `this.world.getBlockState(position)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5966DC5D - AST call getBlockState in CraftWorld.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftWorld.java:779`
- Context: `CraftWorld#generateTree`
- Receiver: `this.world`
- Method: `getBlockState`
- Evidence: `this.world.getBlockState(position)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C0FC80E4 - AST call getBlockState in CraftBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlock.java:79`
- Context: `CraftBlock#getNMS`
- Receiver: `this.world`
- Method: `getBlockState`
- Evidence: `this.world.getBlockState(this.position)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-24B9B900 - AST call getFluidState in CraftBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlock.java:83`
- Context: `CraftBlock#getNMSFluid`
- Receiver: `this.world`
- Method: `getFluidState`
- Evidence: `this.world.getFluidState(this.position)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-AD2F5793 - AST call getBlockState in CraftBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlock.java:164`
- Context: `CraftBlock#getData`
- Receiver: `this.world`
- Method: `getBlockState`
- Evidence: `this.world.getBlockState(this.position)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-69ADC0ED - AST call setType in CraftBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: bukkit-block-write
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlock.java:175`
- Context: `CraftBlock#setType`
- Receiver: `this`
- Method: `setType`
- Evidence: `this.setType(type, true)`
- Suggested fix: Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region.

### AO-AST-BF13A4A7 - AST call getBlockState in CraftBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlock.java:231`
- Context: `CraftBlock#getType`
- Receiver: `this.world`
- Method: `getBlockState`
- Evidence: `this.world.getBlockState(this.position)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-966BC87E - AST call getBlockState in CraftBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlock.java:424`
- Context: `CraftBlock#getBlockPower`
- Receiver: `world`
- Method: `getBlockState`
- Evidence: `world.getBlockState(new BlockPos(x, y - 1, z))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C75A2B07 - AST call getBlockState in CraftBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlock.java:425`
- Context: `CraftBlock#getBlockPower`
- Receiver: `world`
- Method: `getBlockState`
- Evidence: `world.getBlockState(new BlockPos(x, y + 1, z))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2E0C49A5 - AST call getBlockState in CraftBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlock.java:426`
- Context: `CraftBlock#getBlockPower`
- Receiver: `world`
- Method: `getBlockState`
- Evidence: `world.getBlockState(new BlockPos(x + 1, y, z))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9C4AB9E2 - AST call getBlockState in CraftBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlock.java:427`
- Context: `CraftBlock#getBlockPower`
- Receiver: `world`
- Method: `getBlockState`
- Evidence: `world.getBlockState(new BlockPos(x - 1, y, z))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-603AA661 - AST call getBlockState in CraftBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlock.java:428`
- Context: `CraftBlock#getBlockPower`
- Receiver: `world`
- Method: `getBlockState`
- Evidence: `world.getBlockState(new BlockPos(x, y, z - 1))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-34DDD2AA - AST call getBlockState in CraftBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlock.java:429`
- Context: `CraftBlock#getBlockPower`
- Receiver: `world`
- Method: `getBlockState`
- Evidence: `world.getBlockState(new BlockPos(x, y, z + 1))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-28738215 - AST call getBlockState in CraftEnderChest.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftEnderChest.java:67`
- Context: `CraftEnderChest#isBlocked`
- Receiver: `this.getWorldHandle()`
- Method: `getBlockState`
- Evidence: `this.getWorldHandle().getBlockState(abovePos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F140DA0D - AST call setType in CraftItemStack.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: bukkit-block-write
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/inventory/CraftItemStack.java:181`
- Context: `CraftItemStack#<init>`
- Receiver: `this`
- Method: `setType`
- Evidence: `this.setType(type)`
- Suggested fix: Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region.

### AO-AST-A7AED05D - AST call getBlockState in CraftAccessLocationInventoryViewBuilder.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/inventory/view/builder/CraftAccessLocationInventoryViewBuilder.java:31`
- Context: `CraftAccessLocationInventoryViewBuilder#buildContainer`
- Receiver: `super.world`
- Method: `getBlockState`
- Evidence: `super.world.getBlockState(position)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B1B88784 - AST call getBlockState in CraftDoubleChestInventoryViewBuilder.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/inventory/view/builder/CraftDoubleChestInventoryViewBuilder.java:30`
- Context: `CraftDoubleChestInventoryViewBuilder#buildContainer`
- Receiver: `super.world`
- Method: `getBlockState`
- Evidence: `super.world.getBlockState(super.position)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5B9AED96 - AST call setType in CraftEvil.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: bukkit-block-write
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/legacy/CraftEvil.java:44`
- Context: `CraftEvil#setDurability`
- Receiver: `itemStack`
- Method: `setType`
- Evidence: `itemStack.setType(CraftLegacy.fromLegacy(new MaterialData(materialData.getItemType(), (byte)itemStack.getDurability()), true))`
- Suggested fix: Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region.

### AO-AST-F7F0CBCC - AST call setType in CraftEvil.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: bukkit-block-write
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/legacy/CraftEvil.java:61`
- Context: `CraftEvil#setTypeId`
- Receiver: `block`
- Method: `setType`
- Evidence: `block.setType(CraftEvil.getMaterial(type))`
- Suggested fix: Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region.

### AO-AST-A4D787DD - AST call setType in CraftEvil.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: bukkit-block-write
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/legacy/CraftEvil.java:66`
- Context: `CraftEvil#setTypeId`
- Receiver: `block`
- Method: `setType`
- Evidence: `block.setType(CraftEvil.getMaterial(type), applyPhysics)`
- Suggested fix: Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region.

### AO-AST-C0DDCF94 - AST call setType in CraftEvil.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: bukkit-block-write
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/legacy/CraftEvil.java:71`
- Context: `CraftEvil#setTypeIdAndData`
- Receiver: `block`
- Method: `setType`
- Evidence: `block.setType(CraftEvil.getMaterial(type), applyPhysics)`
- Suggested fix: Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region.

### AO-AST-6BB8370C - AST call setType in CraftEvil.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: bukkit-block-write
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/legacy/CraftEvil.java:89`
- Context: `CraftEvil#setTypeId`
- Receiver: `state`
- Method: `setType`
- Evidence: `state.setType(CraftEvil.getMaterial(type))`
- Suggested fix: Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region.

### AO-AST-699E2D12 - AST call setType in CraftEvil.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: bukkit-block-write
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/legacy/CraftEvil.java:98`
- Context: `CraftEvil#setTypeId`
- Receiver: `stack`
- Method: `setType`
- Evidence: `stack.setType(CraftEvil.getMaterial(type))`
- Suggested fix: Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region.

### AO-AST-67D12701 - AST call setType in MaterialRerouting.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: bukkit-block-write
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/legacy/MaterialRerouting.java:131`
- Context: `MaterialRerouting#setType`
- Receiver: `block`
- Method: `setType`
- Evidence: `block.setType(MaterialRerouting.transformToBlockType(type))`
- Suggested fix: Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region.

### AO-AST-5912431C - AST call setType in MaterialRerouting.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: bukkit-block-write
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/legacy/MaterialRerouting.java:135`
- Context: `MaterialRerouting#setType`
- Receiver: `block`
- Method: `setType`
- Evidence: `block.setType(MaterialRerouting.transformToBlockType(type), applyPhysics)`
- Suggested fix: Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region.

### AO-AST-BB2F58A5 - AST call setType in MaterialRerouting.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: bukkit-block-write
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/legacy/MaterialRerouting.java:143`
- Context: `MaterialRerouting#setType`
- Receiver: `blockState`
- Method: `setType`
- Evidence: `blockState.setType(MaterialRerouting.transformToBlockType(type))`
- Suggested fix: Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region.

### AO-AST-2A0BA2B8 - AST call setType in MaterialRerouting.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: bukkit-block-write
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/legacy/MaterialRerouting.java:368`
- Context: `MaterialRerouting#setType`
- Receiver: `itemStack`
- Method: `setType`
- Evidence: `itemStack.setType(MaterialRerouting.transformToItemType(material))`
- Suggested fix: Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region.

### AO-AST-F1467496 - AST call setType in MaterialRerouting.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: bukkit-block-write
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/legacy/MaterialRerouting.java:530`
- Context: `MaterialRerouting#setType`
- Receiver: `regionAccessor`
- Method: `setType`
- Evidence: `regionAccessor.setType(location, MaterialRerouting.transformToBlockType(material))`
- Suggested fix: Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region.

### AO-AST-A0B094D7 - AST call setType in MaterialRerouting.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: bukkit-block-write
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/legacy/MaterialRerouting.java:534`
- Context: `MaterialRerouting#setType`
- Receiver: `regionAccessor`
- Method: `setType`
- Evidence: `regionAccessor.setType(x, y, z, MaterialRerouting.transformToBlockType(material))`
- Suggested fix: Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region.

### AO-AST-2AA48CE5 - AST call getBlockState in BlockStateListPopulator.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/util/BlockStateListPopulator.java:39`
- Context: `BlockStateListPopulator#getBlockState`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6A4C37E5 - AST call getFluidState in BlockStateListPopulator.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/util/BlockStateListPopulator.java:45`
- Context: `BlockStateListPopulator#getFluidState`
- Receiver: `this.level`
- Method: `getFluidState`
- Evidence: `this.level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-AA292FCF - AST call getBlockState in CombinedHeightmapUpdate.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/main/java/net/caffeinemc/mods/lithium/common/world/chunk/heightmap/CombinedHeightmapUpdate.java:103`
- Context: `CombinedHeightmapUpdate#updateHeightmaps`
- Receiver: `worldChunk`
- Method: `getBlockState`
- Evidence: `worldChunk.getBlockState(mutable)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-14C2922F - AST call getBlockState in AnyBlockInteractionTrigger.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/advancements/criterion/AnyBlockInteractionTrigger.java:24`
- Context: `AnyBlockInteractionTrigger#trigger`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-169A0095 - AST call getBlockState in BlockPredicate.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/advancements/criterion/BlockPredicate.java:54`
- Context: `BlockPredicate#matches`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-45DA5A33 - AST call getBlockState in DefaultBlockInteractionTrigger.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/advancements/criterion/DefaultBlockInteractionTrigger.java:23`
- Context: `DefaultBlockInteractionTrigger#trigger`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-84C91BE2 - AST call getFluidState in FluidPredicate.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/advancements/criterion/FluidPredicate.java:27`
- Context: `FluidPredicate#matches`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-0CD92BBF - AST call getBlockState in ItemUsedOnLocationTrigger.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/advancements/criterion/ItemUsedOnLocationTrigger.java:34`
- Context: `ItemUsedOnLocationTrigger#trigger`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-23934B01 - AST call getFluidState in CauldronInteraction.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/cauldron/CauldronInteraction.java:391`
- Context: `CauldronInteraction#isUnderWater`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.above())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-ED20D1ED - AST call getFluidState in BoatDispenseItemBehavior.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/BoatDispenseItemBehavior.java:34`
- Context: `BoatDispenseItemBehavior#execute`
- Receiver: `serverLevel`
- Method: `getFluidState`
- Evidence: `serverLevel.getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-EBD8E2EC - AST call getBlockState in BoatDispenseItemBehavior.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/BoatDispenseItemBehavior.java:37`
- Context: `BoatDispenseItemBehavior#execute`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E4EA258A - AST call getFluidState in BoatDispenseItemBehavior.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/BoatDispenseItemBehavior.java:37`
- Context: `BoatDispenseItemBehavior#execute`
- Receiver: `serverLevel`
- Method: `getFluidState`
- Evidence: `serverLevel.getFluidState(blockPos.below())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-4C67BD1F - AST call getBlockState in DispenseItemBehavior.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/DispenseItemBehavior.java:275`
- Context: `#execute`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1D97618E - AST call getBlockState in DispenseItemBehavior.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/DispenseItemBehavior.java:333`
- Context: `#execute`
- Receiver: `levelAccessor`
- Method: `getBlockState`
- Evidence: `levelAccessor.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-60C846EB - AST call getBlockState in DispenseItemBehavior.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/DispenseItemBehavior.java:369`
- Context: `#execute`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A9B8B57C - AST call getBlockState in DispenseItemBehavior.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/DispenseItemBehavior.java:575`
- Context: `#execute`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A644FD7D - AST call getFluidState in DispenseItemBehavior.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/DispenseItemBehavior.java:591`
- Context: `#execute`
- Receiver: `serverLevel`
- Method: `getFluidState`
- Evidence: `serverLevel.getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-902D0251 - AST call getBlockState in DispenseItemBehavior.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/DispenseItemBehavior.java:606`
- Context: `#execute`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-33B0A19E - AST call getBlockState in DispenseItemBehavior.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/DispenseItemBehavior.java:679`
- Context: `#execute`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1240A36B - AST call getBlockState in DispenseItemBehavior.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/DispenseItemBehavior.java:713`
- Context: `#execute`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CDFC5190 - AST call getBlockState in DispenseItemBehavior.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/DispenseItemBehavior.java:761`
- Context: `#execute`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C2084364 - AST call getBlockState in MinecartDispenseItemBehavior.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/MinecartDispenseItemBehavior.java:35`
- Context: `MinecartDispenseItemBehavior#execute`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6A6D4CAF - AST call getBlockState in MinecartDispenseItemBehavior.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/MinecartDispenseItemBehavior.java:48`
- Context: `MinecartDispenseItemBehavior#execute`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3719470B - AST call getBlockState in ShearsDispenseItemBehavior.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/core/dispenser/ShearsDispenseItemBehavior.java:56`
- Context: `ShearsDispenseItemBehavior#tryShearBeehive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BBD8C329 - AST call getBlockState in GameTestHelper.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/GameTestHelper.java:100`
- Context: `GameTestHelper#getBlockState`
- Receiver: `this.getLevel()`
- Method: `getBlockState`
- Evidence: `this.getLevel().getBlockState(this.absolutePos(pos))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-00BFD164 - AST call getBlockState in GameTestHelper.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/GameTestHelper.java:279`
- Context: `GameTestHelper#pressButton`
- Receiver: `this.getLevel()`
- Method: `getBlockState`
- Evidence: `this.getLevel().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-50F656B6 - AST call getBlockState in GameTestHelper.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/GameTestHelper.java:295`
- Context: `GameTestHelper#useBlock`
- Receiver: `this.getLevel()`
- Method: `getBlockState`
- Evidence: `this.getLevel().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3F3F21C7 - AST call getBlockState in GameTestHelper.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/GameTestHelper.java:360`
- Context: `GameTestHelper#pullLever`
- Receiver: `this.getLevel()`
- Method: `getBlockState`
- Evidence: `this.getLevel().getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6BEA5E67 - AST call getBlockState in GameTestHelper.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/GameTestHelper.java:855`
- Context: `GameTestHelper#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7DC52CF6 - AST call getBlockState in GameTestHelper.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/gametest/framework/GameTestHelper.java:861`
- Context: `GameTestHelper#tickBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CFBF8AD0 - AST call getBlockState in CloneCommands.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/CloneCommands.java:270`
- Context: `CloneCommands#clone`
- Receiver: `serverLevel1`
- Method: `getBlockState`
- Evidence: `serverLevel1.getBlockState(blockPos6)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8AAE89F8 - AST call getBlockState in CloneCommands.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/CloneCommands.java:274`
- Context: `CloneCommands#clone`
- Receiver: `serverLevel1`
- Method: `getBlockState`
- Evidence: `serverLevel1.getBlockState(blockPos6)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A13E467F - AST call getBlockState in CloneCommands.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/CloneCommands.java:277`
- Context: `CloneCommands#clone`
- Receiver: `serverLevel1`
- Method: `getBlockState`
- Evidence: `serverLevel1.getBlockState(blockPos6)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CBDEFAAD - AST call getBlockState in ExecuteCommand.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/ExecuteCommand.java:971`
- Context: `ExecuteCommand#checkRegions`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-94FA1EC6 - AST call getBlockState in ExecuteCommand.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/ExecuteCommand.java:973`
- Context: `ExecuteCommand#checkRegions`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos2)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-AF5F188B - AST call getBlockState in FillCommand.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/FillCommand.java:197`
- Context: `FillCommand#fillBlocks`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B6688F63 - AST call getBlockState in LootCommand.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/LootCommand.java:438`
- Context: `LootCommand#dropBlockLoot`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-42537003 - AST call getBlockState in SetBlockCommand.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/SetBlockCommand.java:114`
- Context: `SetBlockCommand#setBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6242A7A0 - AST call getBlockState in SetBlockCommand.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/SetBlockCommand.java:119`
- Context: `SetBlockCommand#setBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2C2EE353 - AST call getBlockState in SpreadPlayersCommand.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/server/commands/SpreadPlayersCommand.java:350`
- Context: `Position#isSafe`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F29CE3F7 - AST call getBlockState in BlockUtil.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/util/BlockUtil.java:129`
- Context: `BlockUtil#getTopConnectedBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-08465EC8 - AST call getBlockState in AbstractContainerMenu.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/inventory/AbstractContainerMenu.java:124`
- Context: `AbstractContainerMenu#stillValid`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DAC1E042 - AST call getBlockState in AnvilMenu.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/inventory/AnvilMenu.java:124`
- Context: `AnvilMenu#onTake`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E0F1D9D2 - AST call getBlockState in ItemCombinerMenu.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/inventory/ItemCombinerMenu.java:113`
- Context: `ItemCombinerMenu#stillValid`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-55A0DCF8 - AST call getBlockState in AxeItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/AxeItem.java:68`
- Context: `AxeItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-925F787E - AST call getBlockState in AxeItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/AxeItem.java:73`
- Context: `AxeItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1DD297F7 - AST call getBlockState in BlockItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BlockItem.java:77`
- Context: `BlockItem#place`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A6384F07 - AST call getBlockState in BoneMealItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BoneMealItem.java:55`
- Context: `BoneMealItem#applyBonemeal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-96305AE8 - AST call getBlockState in BoneMealItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BoneMealItem.java:71`
- Context: `BoneMealItem#growCrop`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4465525C - AST call getBlockState in BoneMealItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BoneMealItem.java:88`
- Context: `BoneMealItem#growWaterPlant`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7BE85729 - AST call getFluidState in BoneMealItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BoneMealItem.java:88`
- Context: `BoneMealItem#growWaterPlant`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-F6DF9895 - AST call getBlockState in BoneMealItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BoneMealItem.java:101`
- Context: `BoneMealItem#growWaterPlant`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A1E2746D - AST call getBlockState in BoneMealItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BoneMealItem.java:131`
- Context: `BoneMealItem#growWaterPlant`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0CE0493C - AST call getFluidState in BoneMealItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BoneMealItem.java:132`
- Context: `BoneMealItem#growWaterPlant`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-AE227713 - AST call getBlockState in BoneMealItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BoneMealItem.java:151`
- Context: `BoneMealItem#addGrowthParticles`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5611E8EF - AST call getFluidState in BottleItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BottleItem.java:58`
- Context: `BottleItem#use`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-FF328F77 - AST call getBlockState in BrushItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BrushItem.java:66`
- Context: `BrushItem#onUseTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DD4C551A - AST call getBlockState in BucketItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BucketItem.java:59`
- Context: `BucketItem#use`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-831EA254 - AST call getBlockState in BucketItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BucketItem.java:87`
- Context: `BucketItem#use`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5F4D6F18 - AST call getBlockState in BucketItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/BucketItem.java:131`
- Context: `BucketItem#emptyContents`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-104C19C0 - AST call getBlockState in CompassItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/CompassItem.java:48`
- Context: `CompassItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CE638A36 - AST call getBlockState in DebugStickItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/DebugStickItem.java:43`
- Context: `DebugStickItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4183EE9F - AST call getBlockState in EndCrystalItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/EndCrystalItem.java:26`
- Context: `EndCrystalItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-67AD5927 - AST call getBlockState in EnderEyeItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/EnderEyeItem.java:40`
- Context: `EnderEyeItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E34BA953 - AST call getBlockState in EnderEyeItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/EnderEyeItem.java:105`
- Context: `EnderEyeItem#use`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(playerPovHitResult.getBlockPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FCAB9A8E - AST call getBlockState in FireChargeItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/FireChargeItem.java:34`
- Context: `FireChargeItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-09B332B3 - AST call getBlockState in FlintAndSteelItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/FlintAndSteelItem.java:31`
- Context: `FlintAndSteelItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F462B0B9 - AST call getBlockState in HoeItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/HoeItem.java:48`
- Context: `HoeItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D1F3EAF3 - AST call getBlockState in HoeItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/HoeItem.java:96`
- Context: `HoeItem#onlyIfAirAbove`
- Receiver: `context.getLevel()`
- Method: `getBlockState`
- Evidence: `context.getLevel().getBlockState(context.getClickedPos().above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2A026F9D - AST call getBlockState in HoneycombItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/HoneycombItem.java:145`
- Context: `HoneycombItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4592E129 - AST call getBlockState in HoneycombItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/HoneycombItem.java:167`
- Context: `HoneycombItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(connectedBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7908B74E - AST call getBlockState in ItemStack.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/ItemStack.java:480`
- Context: `ItemStack#useOn`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(newPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-83B08B09 - AST call getBlockState in ItemStack.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/ItemStack.java:487`
- Context: `ItemStack#useOn`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(newPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8E5160B1 - AST call getBlockState in ItemStack.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/ItemStack.java:492`
- Context: `ItemStack#useOn`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BF28BE4E - AST call getBlockState in ItemStack.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/ItemStack.java:493`
- Context: `ItemStack#useOn`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FB37675C - AST call getBlockState in ItemStack.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/ItemStack.java:511`
- Context: `ItemStack#useOn`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(SignItem.openSignThreadLocal.get())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3F90E639 - AST call getBlockState in ItemStack.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/ItemStack.java:523`
- Context: `ItemStack#useOn`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C5699737 - AST call getBlockState in ItemStack.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/ItemStack.java:534`
- Context: `ItemStack#useOn`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(success.paperSuccessContext().placedBlockPosition())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-EF90A194 - AST call getBlockState in JukeboxPlayable.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/JukeboxPlayable.java:50`
- Context: `JukeboxPlayable#tryInsertIntoJukebox`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-23E38F77 - AST call getBlockState in LeadItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/LeadItem.java:25`
- Context: `LeadItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A3FB94D3 - AST call getBlockState in MapItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/MapItem.java:323`
- Context: `MapItem#useOn`
- Receiver: `context.getLevel()`
- Method: `getBlockState`
- Evidence: `context.getLevel().getBlockState(context.getClickedPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1A0423F6 - AST call getBlockState in MinecartItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/MinecartItem.java:31`
- Context: `MinecartItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5DDBCA19 - AST call getBlockState in MinecartItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/MinecartItem.java:69`
- Context: `MinecartItem#useOn`
- Receiver: `serverLevel`
- Method: `getBlockState`
- Evidence: `serverLevel.getBlockState(clickedPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A7C1ED31 - AST call getBlockState in PotionItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/PotionItem.java:41`
- Context: `PotionItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4455AA1B - AST call getBlockState in ScaffoldingBlockItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/ScaffoldingBlockItem.java:25`
- Context: `ScaffoldingBlockItem#updatePlacementContext`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-59E9593A - AST call getBlockState in ScaffoldingBlockItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/ScaffoldingBlockItem.java:50`
- Context: `ScaffoldingBlockItem#updatePlacementContext`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F609BC6E - AST call getBlockState in ShearsItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/ShearsItem.java:65`
- Context: `ShearsItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-593855AC - AST call getBlockState in ShovelItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/ShovelItem.java:41`
- Context: `ShovelItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1C29DF3F - AST call getBlockState in ShovelItem.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/ShovelItem.java:50`
- Context: `ShovelItem#useOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D7005538 - AST call getBlockState in BlockPlaceContext.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/context/BlockPlaceContext.java:28`
- Context: `BlockPlaceContext#<init>`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(hitResult.getBlockPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2DE648BC - AST call getBlockState in BlockPlaceContext.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/context/BlockPlaceContext.java:54`
- Context: `BlockPlaceContext#canPlace`
- Receiver: `this.getLevel()`
- Method: `getBlockState`
- Evidence: `this.getLevel().getBlockState(this.getClickedPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-EACD9EC6 - AST call getBlockState in DirectionalPlaceContext.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/context/DirectionalPlaceContext.java:26`
- Context: `DirectionalPlaceContext#canPlace`
- Receiver: `this.getLevel()`
- Method: `getBlockState`
- Evidence: `this.getLevel().getBlockState(this.getHitResult().getBlockPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6D3C5F06 - AST call getBlockState in Level.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/Level.java:1114`
- Context: `Level#setBlock`
- Receiver: ``
- Method: `getBlockState`
- Evidence: `getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-03F40EB1 - AST call getBlockState in ServerExplosion.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/ServerExplosion.java:606`
- Context: `ServerExplosion#interactWithBlocks`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BEEC08D1 - AST call getBlockState in ServerExplosion.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/ServerExplosion.java:618`
- Context: `ServerExplosion#interactWithBlocks`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DCC77B40 - AST call getBlockState in ServerExplosion.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/ServerExplosion.java:630`
- Context: `ServerExplosion#createFire`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-26782724 - AST call getBlockState in ServerExplosion.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/ServerExplosion.java:630`
- Context: `ServerExplosion#createFire`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-90B261FB - AST call getBlockState in Biome.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/biome/Biome.java:138`
- Context: `Biome#shouldFreeze`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(water)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9BC00304 - AST call getFluidState in Biome.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/biome/Biome.java:139`
- Context: `Biome#shouldFreeze`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(water)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-235289D8 - AST call getBlockState in Biome.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/biome/Biome.java:176`
- Context: `Biome#shouldSnow`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0C28ACB3 - AST call getBlockState in AmethystClusterBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/AmethystClusterBlock.java:65`
- Context: `AmethystClusterBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-10B2DE1E - AST call getFluidState in AmethystClusterBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/AmethystClusterBlock.java:93`
- Context: `AmethystClusterBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(clickedPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-233A1146 - AST call getFluidState in AzaleaBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/AzaleaBlock.java:43`
- Context: `AzaleaBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.above())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-BC4E0A08 - AST call getBlockState in BambooSaplingBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BambooSaplingBlock.java:48`
- Context: `BambooSaplingBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-309F6CC4 - AST call getBlockState in BambooSaplingBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BambooSaplingBlock.java:78`
- Context: `BambooSaplingBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0CF5037D - AST call getFluidState in BambooStalkBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BambooStalkBlock.java:84`
- Context: `BambooStalkBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-506E25DA - AST call getBlockState in BambooStalkBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BambooStalkBlock.java:88`
- Context: `BambooStalkBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getBlockState`
- Evidence: `context.getLevel().getBlockState(context.getClickedPos().below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-54672F6E - AST call getBlockState in BambooStalkBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BambooStalkBlock.java:96`
- Context: `BambooStalkBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getBlockState`
- Evidence: `context.getLevel().getBlockState(context.getClickedPos().above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F2EC897F - AST call getBlockState in BambooStalkBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BambooStalkBlock.java:133`
- Context: `BambooStalkBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B5B36F9F - AST call getBlockState in BambooStalkBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BambooStalkBlock.java:160`
- Context: `BambooStalkBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above(heightAboveUpToMax))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CB34A923 - AST call getBlockState in BambooStalkBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BambooStalkBlock.java:177`
- Context: `BambooStalkBlock#performBonemeal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-54DE9DF8 - AST call getBlockState in BambooStalkBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BambooStalkBlock.java:189`
- Context: `BambooStalkBlock#growBamboo`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DE945112 - AST call getBlockState in BambooStalkBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BambooStalkBlock.java:191`
- Context: `BambooStalkBlock#growBamboo`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-72A06AEF - AST call getBlockState in BambooStalkBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BambooStalkBlock.java:224`
- Context: `BambooStalkBlock#getHeightAboveUpToMax`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above(i + 1))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-771D19FA - AST call getBlockState in BambooStalkBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BambooStalkBlock.java:234`
- Context: `BambooStalkBlock#getHeightBelowUpToMax`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below(i + 1))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-713AD250 - AST call getBlockState in BannerBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BannerBlock.java:46`
- Context: `BannerBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-89EACB1C - AST call getFluidState in BarrierBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BarrierBlock.java:79`
- Context: `BarrierBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-59D311DF - AST call getFluidState in BaseCoralPlantTypeBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseCoralPlantTypeBlock.java:47`
- Context: `BaseCoralPlantTypeBlock#scanForWater`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.relative(direction))`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-B95B4DFD - AST call getFluidState in BaseCoralPlantTypeBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseCoralPlantTypeBlock.java:58`
- Context: `BaseCoralPlantTypeBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-41C428E9 - AST call getBlockState in BaseCoralPlantTypeBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseCoralPlantTypeBlock.java:90`
- Context: `BaseCoralPlantTypeBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BBA55BBF - AST call getBlockState in BaseCoralWallFanBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseCoralWallFanBlock.java:79`
- Context: `BaseCoralWallFanBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C5938A3B - AST call getBlockState in BaseFireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseFireBlock.java:47`
- Context: `BaseFireBlock#getState`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0D90A348 - AST call getBlockState in BaseFireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseFireBlock.java:72`
- Context: `BaseFireBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-54991007 - AST call getBlockState in BaseFireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseFireBlock.java:74`
- Context: `BaseFireBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.west())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-70967F44 - AST call getBlockState in BaseFireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseFireBlock.java:83`
- Context: `BaseFireBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.east())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A03CDEEF - AST call getBlockState in BaseFireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseFireBlock.java:92`
- Context: `BaseFireBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.north())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FF1C427B - AST call getBlockState in BaseFireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseFireBlock.java:101`
- Context: `BaseFireBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.south())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-43317CAD - AST call getBlockState in BaseFireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseFireBlock.java:110`
- Context: `BaseFireBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-74B90BF2 - AST call getBlockState in BaseFireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseFireBlock.java:199`
- Context: `BaseFireBlock#canBePlacedAt`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-513C391E - AST call getBlockState in BaseFireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseFireBlock.java:211`
- Context: `BaseFireBlock#isPortal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.set(pos).move(direction1))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-35606113 - AST call scheduleTick in BasePressurePlateBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BasePressurePlateBlock.java:124`
- Context: `BasePressurePlateBlock#checkPressed`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(new BlockPos(pos), this, this.getPressedTime())`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-2628DD33 - AST call getFluidState in BaseRailBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BaseRailBlock.java:186`
- Context: `BaseRailBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-E3B5E1B3 - AST call getBlockState in BedBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BedBlock.java:76`
- Context: `BedBlock#getBedOrientation`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BC034B1B - AST call getBlockState in BedBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BedBlock.java:87`
- Context: `BedBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8CC15184 - AST call getBlockState in BedBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BedBlock.java:98`
- Context: `BedBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7DA1000D - AST call getBlockState in BedBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BedBlock.java:151`
- Context: `BedBlock#explodeBed`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3553D01E - AST call getBlockState in BedBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BedBlock.java:223`
- Context: `BedBlock#playerWillDestroy`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0ABD5F58 - AST call getBlockState in BedBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BedBlock.java:240`
- Context: `BedBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3AD56C7D - AST call getBlockState in BedBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BedBlock.java:261`
- Context: `BedBlock#isBunkBed`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6E94EF82 - AST call getBlockState in BellBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BellBlock.java:137`
- Context: `BellBlock#attemptToRing`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5A331525 - AST call getBlockState in BellBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BellBlock.java:190`
- Context: `BellBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.west())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-ED822A9F - AST call getBlockState in BellBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BellBlock.java:191`
- Context: `BellBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.east())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-86000157 - AST call getBlockState in BellBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BellBlock.java:193`
- Context: `BellBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.north())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C7E1B249 - AST call getBlockState in BellBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BellBlock.java:194`
- Context: `BellBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.south())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F8CC885A - AST call getBlockState in BellBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BellBlock.java:202`
- Context: `BellBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6F6F1117 - AST call getBlockState in BigDripleafBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafBlock.java:95`
- Context: `BigDripleafBlock#placeWithRandomHeight`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6FB0DF91 - AST call getFluidState in BigDripleafBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafBlock.java:104`
- Context: `BigDripleafBlock#placeWithRandomHeight`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(mutableBlockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-A406D6F5 - AST call getFluidState in BigDripleafBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafBlock.java:108`
- Context: `BigDripleafBlock#placeWithRandomHeight`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(mutableBlockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-6A3EFDAB - AST call getBlockState in BigDripleafBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafBlock.java:140`
- Context: `BigDripleafBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3BA60DC4 - AST call getBlockState in BigDripleafBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafBlock.java:170`
- Context: `BigDripleafBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2F4C40D6 - AST call getBlockState in BigDripleafBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafBlock.java:182`
- Context: `BigDripleafBlock#performBonemeal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FB55AB20 - AST call scheduleTick in BigDripleafBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafBlock.java:256`
- Context: `BigDripleafBlock#setTiltAndScheduleTick`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, _int)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-2EAD1DCD - AST call getBlockState in BigDripleafBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafBlock.java:295`
- Context: `BigDripleafBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getBlockState`
- Evidence: `context.getLevel().getBlockState(context.getClickedPos().below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-ABAA70AE - AST call getFluidState in BigDripleafBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafBlock.java:296`
- Context: `BigDripleafBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-E1DB5EBF - AST call getBlockState in BigDripleafStemBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafStemBlock.java:62`
- Context: `BigDripleafStemBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E45F5BED - AST call getBlockState in BigDripleafStemBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafStemBlock.java:63`
- Context: `BigDripleafStemBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-77077B73 - AST call getBlockState in BigDripleafStemBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafStemBlock.java:111`
- Context: `BigDripleafStemBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FA48689E - AST call getFluidState in BigDripleafStemBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafStemBlock.java:128`
- Context: `BigDripleafStemBlock#performBonemeal`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-73D8CA06 - AST call getFluidState in BigDripleafStemBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BigDripleafStemBlock.java:129`
- Context: `BigDripleafStemBlock#performBonemeal`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos1)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-8B68148A - AST call getBlockState in Block.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/Block.java:232`
- Context: `Block#updateFromNeighbourShapes`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5C1028FC - AST call getBlockState in Block.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/Block.java:347`
- Context: `Block#canSupportRigidBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-199158CF - AST call getBlockState in Block.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/Block.java:351`
- Context: `Block#canSupportCenter`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6B8124D1 - AST call getBlockState in BonemealableFeaturePlacerBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BonemealableFeaturePlacerBlock.java:35`
- Context: `BonemealableFeaturePlacerBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7A862B60 - AST call scheduleTick in BrushableBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BrushableBlock.java:64`
- Context: `BrushableBlock#onPlace`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 2)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-02A59CD8 - AST call getBlockState in BrushableBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BrushableBlock.java:88`
- Context: `BrushableBlock#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-869262F4 - AST call getBlockState in BrushableBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BrushableBlock.java:105`
- Context: `BrushableBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2B30BE92 - AST call getBlockState in BubbleColumnBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BubbleColumnBlock.java:66`
- Context: `BubbleColumnBlock#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-186F5DE1 - AST call getBlockState in BubbleColumnBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BubbleColumnBlock.java:75`
- Context: `BubbleColumnBlock#updateColumn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A8E41936 - AST call getBlockState in BubbleColumnBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BubbleColumnBlock.java:84`
- Context: `BubbleColumnBlock#updateColumn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-36F703CD - AST call getBlockState in BubbleColumnBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BubbleColumnBlock.java:170`
- Context: `BubbleColumnBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-06B01797 - AST call getBlockState in BuddingAmethystBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/BuddingAmethystBlock.java:31`
- Context: `BuddingAmethystBlock#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-99C96B9F - AST call scheduleTick in ButtonBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ButtonBlock.java:122`
- Context: `ButtonBlock#press`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, this.ticksToStayPressed)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-68906163 - AST call scheduleTick in ButtonBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ButtonBlock.java:210`
- Context: `ButtonBlock#checkPressed`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(new BlockPos(pos), this, this.ticksToStayPressed)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-3D298FC9 - AST call getBlockState in CactusBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CactusBlock.java:60`
- Context: `CactusBlock#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below(i))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BA81F9C4 - AST call getBlockState in CactusBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CactusBlock.java:119`
- Context: `CactusBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-934BC4DC - AST call getFluidState in CactusBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CactusBlock.java:120`
- Context: `CactusBlock#canSurvive`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.relative(direction))`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-66024EDE - AST call getBlockState in CactusBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CactusBlock.java:125`
- Context: `CactusBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-45B7B6CD - AST call getBlockState in CactusBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CactusBlock.java:126`
- Context: `CactusBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-668E3AC4 - AST call getBlockState in CactusBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CactusBlock.java:151`
- Context: `CactusBlock#isValidBonemealTarget`
- Receiver: `world`
- Method: `getBlockState`
- Evidence: `world.getBlockState(pos.below(cactusHeight))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-43B2D0EA - AST call getBlockState in CactusBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CactusBlock.java:166`
- Context: `CactusBlock#performBonemeal`
- Receiver: `world`
- Method: `getBlockState`
- Evidence: `world.getBlockState(pos.below(cactusHeight))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E2542F12 - AST call getBlockState in CactusFlowerBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CactusFlowerBlock.java:32`
- Context: `CactusFlowerBlock#mayPlaceOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8C8A8555 - AST call getFluidState in CakeBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CakeBlock.java:98`
- Context: `CakeBlock#eat`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-C32FF56E - AST call getBlockState in CakeBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CakeBlock.java:149`
- Context: `CakeBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4AB2517E - AST call getFluidState in CampfireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CampfireBlock.java:122`
- Context: `CampfireBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(clickedPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-23F97266 - AST call getBlockState in CampfireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CampfireBlock.java:125`
- Context: `CampfireBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2C017F7D - AST call scheduleTick in CampfireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CampfireBlock.java:208`
- Context: `CampfireBlock#placeLiquid`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-5661CB3E - AST call getBlockState in CampfireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CampfireBlock.java:261`
- Context: `CampfireBlock#isSmokeyPos`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-79338FEE - AST call getBlockState in CampfireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CampfireBlock.java:268`
- Context: `CampfireBlock#isSmokeyPos`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FB7B7060 - AST call getBlockState in CandleBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CandleBlock.java:99`
- Context: `CandleBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getBlockState`
- Evidence: `context.getLevel().getBlockState(context.getClickedPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A15F486A - AST call getFluidState in CandleBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CandleBlock.java:103`
- Context: `CandleBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-EB9C7C8A - AST call scheduleTick in CandleBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CandleBlock.java:152`
- Context: `CandleBlock#placeLiquid`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-6FF56A2C - AST call getBlockState in CandleCakeBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CandleCakeBlock.java:128`
- Context: `CandleCakeBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B0AE85AA - AST call getBlockState in CeilingHangingSignBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CeilingHangingSignBlock.java:85`
- Context: `CeilingHangingSignBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3C7BAE4B - AST call getFluidState in CeilingHangingSignBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CeilingHangingSignBlock.java:91`
- Context: `CeilingHangingSignBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-306A2A31 - AST call getBlockState in CeilingHangingSignBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CeilingHangingSignBlock.java:93`
- Context: `CeilingHangingSignBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A74C04BF - AST call getFluidState in ChainBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChainBlock.java:46`
- Context: `ChainBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-D7183D08 - AST call getBlockState in ChangeOverTimeBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChangeOverTimeBlock.java:36`
- Context: `ChangeOverTimeBlock#getNextState`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B498CCB4 - AST call getFluidState in ChestBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChestBlock.java:247`
- Context: `ChestBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-F3B1B86A - AST call getBlockState in ChestBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChestBlock.java:279`
- Context: `ChestBlock#candidatePartnerFacing`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CBE1C6B7 - AST call getBlockState in ChestBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChestBlock.java:375`
- Context: `ChestBlock#isBlockedChestByBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DA362147 - AST call getBlockState in ChorusFlowerBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChorusFlowerBlock.java:73`
- Context: `ChorusFlowerBlock#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-20A3FA1B - AST call getBlockState in ChorusFlowerBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChorusFlowerBlock.java:80`
- Context: `ChorusFlowerBlock#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below(i + 1))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-866C94A0 - AST call getBlockState in ChorusFlowerBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChorusFlowerBlock.java:184`
- Context: `ChorusFlowerBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-90BCC0B2 - AST call getBlockState in ChorusFlowerBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChorusFlowerBlock.java:192`
- Context: `ChorusFlowerBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7827782A - AST call getBlockState in ChorusPlantBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChorusPlantBlock.java:46`
- Context: `ChorusPlantBlock#getStateWithConnections`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-14EE06C6 - AST call getBlockState in ChorusPlantBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChorusPlantBlock.java:47`
- Context: `ChorusPlantBlock#getStateWithConnections`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CD544930 - AST call getBlockState in ChorusPlantBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChorusPlantBlock.java:48`
- Context: `ChorusPlantBlock#getStateWithConnections`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.north())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-26947DAD - AST call getBlockState in ChorusPlantBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChorusPlantBlock.java:49`
- Context: `ChorusPlantBlock#getStateWithConnections`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.east())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D94DB387 - AST call getBlockState in ChorusPlantBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChorusPlantBlock.java:50`
- Context: `ChorusPlantBlock#getStateWithConnections`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.south())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-25704427 - AST call getBlockState in ChorusPlantBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChorusPlantBlock.java:51`
- Context: `ChorusPlantBlock#getStateWithConnections`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.west())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-601228AE - AST call getBlockState in ChorusPlantBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChorusPlantBlock.java:95`
- Context: `ChorusPlantBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FF25416A - AST call getBlockState in ChorusPlantBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChorusPlantBlock.java:96`
- Context: `ChorusPlantBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A46F184C - AST call getBlockState in ChorusPlantBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChorusPlantBlock.java:100`
- Context: `ChorusPlantBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B61E004B - AST call getBlockState in ChorusPlantBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ChorusPlantBlock.java:106`
- Context: `ChorusPlantBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-534F7078 - AST call getBlockState in CocoaBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CocoaBlock.java:63`
- Context: `CocoaBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(state.getValue(FACING)))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-79BD7E1F - AST call scheduleTick in CommandBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CommandBlock.java:90`
- Context: `CommandBlock#setPoweredAndUpdate`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-7CFFA624 - AST call scheduleTick in CommandBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CommandBlock.java:111`
- Context: `CommandBlock#tick`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-601D2E81 - AST call getBlockState in CommandBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CommandBlock.java:200`
- Context: `CommandBlock#executeChain`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6979C269 - AST call getBlockState in ComposterBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ComposterBlock.java:201`
- Context: `ComposterBlock#handleFill`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-07833F74 - AST call scheduleTick in ComposterBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ComposterBlock.java:243`
- Context: `ComposterBlock#onPlace`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, state.getBlock(), 20)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-75E74250 - AST call scheduleTick in ComposterBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ComposterBlock.java:398`
- Context: `ComposterBlock#addItem`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, state.getBlock(), 20)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-DCA55820 - AST call getBlockState in ConcretePowderBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ConcretePowderBlock.java:49`
- Context: `ConcretePowderBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-238E5551 - AST call getBlockState in ConcretePowderBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ConcretePowderBlock.java:79`
- Context: `ConcretePowderBlock#touchesLiquid`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9D057A2E - AST call getBlockState in ConcretePowderBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ConcretePowderBlock.java:82`
- Context: `ConcretePowderBlock#touchesLiquid`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6BF8284E - AST call getFluidState in ConduitBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ConduitBlock.java:91`
- Context: `ConduitBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-7AC91A49 - AST call getBlockState in CopperBulbBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CopperBulbBlock.java:72`
- Context: `CopperBulbBlock#getAnalogOutputSignal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4C0B8B47 - AST call getBlockState in CopperChestBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CopperChestBlock.java:76`
- Context: `CopperChestBlock#getLeastOxidizedChestOfConnectedBlocks`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(getConnectedDirection(state)))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0AF1C4A2 - AST call getFluidState in CopperGolemStatueBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CopperGolemStatueBlock.java:78`
- Context: `CopperGolemStatueBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-A777F42C - AST call getFluidState in CoralBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CoralBlock.java:70`
- Context: `CoralBlock#scanForWater`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.relative(direction))`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-98CD71FC - AST call scheduleTick in CoralBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CoralBlock.java:82`
- Context: `CoralBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `scheduleTick`
- Evidence: `context.getLevel().scheduleTick(context.getClickedPos(), this, 60 + context.getLevel().getRandom().nextInt(40))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-65C9DEB6 - AST call scheduleTick in CrafterBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CrafterBlock.java:127`
- Context: `CrafterBlock#setPlacedBy`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 4)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-D9560ABF - AST call getBlockState in CreakingHeartBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CreakingHeartBlock.java:119`
- Context: `CreakingHeartBlock#hasRequiredLogs`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-17981AC4 - AST call getBlockState in CreakingHeartBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CreakingHeartBlock.java:131`
- Context: `CreakingHeartBlock#isSurroundedByLogs`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5A2EB7A5 - AST call getBlockState in CropBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CropBlock.java:123`
- Context: `CropBlock#getGrowthSpeed`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos.offset(i, 0, i1))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1820A1EB - AST call getBlockState in CropBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CropBlock.java:143`
- Context: `CropBlock#getGrowthSpeed`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos3)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0EF11AEC - AST call getBlockState in CropBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CropBlock.java:143`
- Context: `CropBlock#getGrowthSpeed`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos4)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1E84D650 - AST call getBlockState in CropBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CropBlock.java:144`
- Context: `CropBlock#getGrowthSpeed`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3604B25A - AST call getBlockState in CropBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CropBlock.java:144`
- Context: `CropBlock#getGrowthSpeed`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos2)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F1677560 - AST call getBlockState in CropBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CropBlock.java:148`
- Context: `CropBlock#getGrowthSpeed`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos3.north())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C85D1512 - AST call getBlockState in CropBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CropBlock.java:149`
- Context: `CropBlock#getGrowthSpeed`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos4.north())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9D016C76 - AST call getBlockState in CropBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CropBlock.java:150`
- Context: `CropBlock#getGrowthSpeed`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos4.south())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CE1761CB - AST call getBlockState in CropBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CropBlock.java:151`
- Context: `CropBlock#getGrowthSpeed`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos3.south())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E60C74D6 - AST call getBlockState in CryingObsidianBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/CryingObsidianBlock.java:30`
- Context: `CryingObsidianBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-76FDBB7A - AST call getFluidState in DecoratedPotBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DecoratedPotBlock.java:88`
- Context: `DecoratedPotBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-C629D993 - AST call scheduleTick in DetectorRailBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DetectorRailBlock.java:120`
- Context: `DetectorRailBlock#checkPressed`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 20)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-8AD40114 - AST call scheduleTick in DiodeBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DiodeBlock.java:82`
- Context: `DiodeBlock#tick`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, this.getDelay(state), TickPriority.VERY_HIGH)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-E76099B8 - AST call scheduleTick in DiodeBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DiodeBlock.java:206`
- Context: `DiodeBlock#setPlacedBy`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-CAB2ECBC - AST call getBlockState in DirtPathBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DirtPathBlock.java:73`
- Context: `DirtPathBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3B4E027B - AST call getBlockState in DoorBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DoorBlock.java:140`
- Context: `DoorBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DE656E27 - AST call getBlockState in DoorBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DoorBlock.java:165`
- Context: `DoorBlock#getHinge`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4DDCE51A - AST call getBlockState in DoorBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DoorBlock.java:167`
- Context: `DoorBlock#getHinge`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos2)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-67D7B522 - AST call getBlockState in DoorBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DoorBlock.java:170`
- Context: `DoorBlock#getHinge`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos3)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F40CE8C7 - AST call getBlockState in DoorBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DoorBlock.java:172`
- Context: `DoorBlock#getHinge`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos4)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4F636F8A - AST call getBlockState in DoorBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DoorBlock.java:253`
- Context: `DoorBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CF137139 - AST call getBlockState in DoorBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DoorBlock.java:284`
- Context: `DoorBlock#isWoodenDoor`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1D9B14F9 - AST call getBlockState in DoublePlantBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DoublePlantBlock.java:67`
- Context: `DoublePlantBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7C6F57C6 - AST call getBlockState in DoublePlantBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DoublePlantBlock.java:83`
- Context: `DoublePlantBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-14DE4FB2 - AST call getBlockState in DoublePlantBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DoublePlantBlock.java:125`
- Context: `DoublePlantBlock#preventDropFromBottomPart`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-07D5E42C - AST call getBlockState in DriedGhastBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DriedGhastBlock.java:145`
- Context: `DriedGhastBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-18FA555E - AST call scheduleTick in DriedGhastBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DriedGhastBlock.java:174`
- Context: `DriedGhastBlock#randomTick`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 5000)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-980DD7C9 - AST call getFluidState in DriedGhastBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DriedGhastBlock.java:180`
- Context: `DriedGhastBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-1D0AEFA5 - AST call scheduleTick in DriedGhastBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DriedGhastBlock.java:195`
- Context: `DriedGhastBlock#placeLiquid`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-F55EA335 - AST call getBlockState in DropperBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/DropperBlock.java:61`
- Context: `DropperBlock#dispenseFrom`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-50DCD2CE - AST call getBlockState in EnchantingTableBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/EnchantingTableBlock.java:48`
- Context: `EnchantingTableBlock#isValidBookShelf`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(enchantingTablePos.offset(bookshelfPos))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6C039DE2 - AST call getBlockState in EnchantingTableBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/EnchantingTableBlock.java:49`
- Context: `EnchantingTableBlock#isValidBookShelf`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(enchantingTablePos.offset(bookshelfPos.getX() / 2, bookshelfPos.getY(), bookshelfPos.getZ() / 2))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6F808884 - AST call getBlockState in EndRodBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/EndRodBlock.java:30`
- Context: `EndRodBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getBlockState`
- Evidence: `context.getLevel().getBlockState(context.getClickedPos().relative(clickedFace.getOpposite()))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DA53AA75 - AST call getFluidState in EnderChestBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/EnderChestBlock.java:70`
- Context: `EnderChestBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-734E2CCA - AST call getBlockState in EnderChestBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/EnderChestBlock.java:81`
- Context: `EnderChestBlock#useWithoutItem`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-905B1AD4 - AST call getBlockState in EyeblossomBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/EyeblossomBlock.java:55`
- Context: `EyeblossomBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-233FC1C1 - AST call getBlockState in EyeblossomBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/EyeblossomBlock.java:90`
- Context: `EyeblossomBlock#tryChangingState`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(checkPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E5218BF1 - AST call scheduleTick in EyeblossomBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/EyeblossomBlock.java:94`
- Context: `EyeblossomBlock#tryChangingState`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(checkPos, state.getBlock(), randomInt)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-3F558211 - AST call getBlockState in FaceAttachedHorizontalDirectionalBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FaceAttachedHorizontalDirectionalBlock.java:34`
- Context: `FaceAttachedHorizontalDirectionalBlock#canAttach`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-29553477 - AST call scheduleTick in FallingBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FallingBlock.java:30`
- Context: `FallingBlock#onPlace`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, this.getDelayAfterPlace())`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-6C32E42C - AST call getBlockState in FallingBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FallingBlock.java:50`
- Context: `FallingBlock#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-005CB7B0 - AST call getBlockState in FallingBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FallingBlock.java:71`
- Context: `FallingBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CDB7E3AF - AST call getBlockState in FarmBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FarmBlock.java:67`
- Context: `FarmBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3B844E5A - AST call getBlockState in FarmBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FarmBlock.java:137`
- Context: `FarmBlock#fallOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-090F1F5E - AST call getBlockState in FarmBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FarmBlock.java:179`
- Context: `FarmBlock#shouldMaintainFarmland`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B9922588 - AST call getFluidState in FarmBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FarmBlock.java:202`
- Context: `FarmBlock#isNearWater`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.relative(Direction.DOWN))`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-BCAD057B - AST call getFluidState in FenceBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FenceBlock.java:79`
- Context: `FenceBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-1ACF7CAA - AST call getBlockState in FenceBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FenceBlock.java:84`
- Context: `FenceBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9C6BEB3F - AST call getBlockState in FenceBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FenceBlock.java:85`
- Context: `FenceBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3E12EFCA - AST call getBlockState in FenceBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FenceBlock.java:86`
- Context: `FenceBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos2)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FBBE145F - AST call getBlockState in FenceBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FenceBlock.java:87`
- Context: `FenceBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos3)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7D7BC87F - AST call getBlockState in FenceGateBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FenceGateBlock.java:94`
- Context: `FenceGateBlock#updateShape`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(direction.getOpposite()))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F18FCE49 - AST call getBlockState in FenceGateBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FenceGateBlock.java:138`
- Context: `FenceGateBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.west())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-099D743E - AST call getBlockState in FenceGateBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FenceGateBlock.java:138`
- Context: `FenceGateBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.east())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A2C92934 - AST call getBlockState in FenceGateBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FenceGateBlock.java:139`
- Context: `FenceGateBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.north())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-40759B00 - AST call getBlockState in FenceGateBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FenceGateBlock.java:139`
- Context: `FenceGateBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.south())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B2FB2A91 - AST call getBlockState in FireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FireBlock.java:135`
- Context: `FireBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8F648805 - AST call getBlockState in FireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FireBlock.java:142`
- Context: `FireBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-741D1F4B - AST call getBlockState in FireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FireBlock.java:155`
- Context: `FireBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4F81DE5F - AST call scheduleTick in FireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FireBlock.java:160`
- Context: `FireBlock#tick`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, getFireTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-0AEC43F9 - AST call getBlockState in FireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FireBlock.java:166`
- Context: `FireBlock#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9D583608 - AST call getBlockState in FireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FireBlock.java:181`
- Context: `FireBlock#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0ED847F3 - AST call getBlockState in FireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FireBlock.java:188`
- Context: `FireBlock#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-AC39E7CD - AST call getBlockState in FireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FireBlock.java:226`
- Context: `FireBlock#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FCF59E2C - AST call getBlockState in FireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FireBlock.java:265`
- Context: `FireBlock#checkBurnOut`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-93B4115D - AST call getBlockState in FireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FireBlock.java:267`
- Context: `FireBlock#checkBurnOut`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-085FA9EB - AST call getBlockState in FireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FireBlock.java:310`
- Context: `FireBlock#isValidFireLocation`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A68ACEEA - AST call getBlockState in FireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FireBlock.java:325`
- Context: `FireBlock#getIgniteOdds`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-456BC071 - AST call scheduleTick in FireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FireBlock.java:342`
- Context: `FireBlock#onPlace`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, FireBlock.getFireTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-1E7FD338 - AST call scheduleTick in FrostedIceBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FrostedIceBlock.java:41`
- Context: `FrostedIceBlock#onPlace`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, Mth.nextInt(level.getRandom(), 60, 120))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-7E2E4AEC - AST call getBlockState in FrostedIceBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FrostedIceBlock.java:54`
- Context: `FrostedIceBlock#tick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-441110FA - AST call scheduleTick in FrostedIceBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FrostedIceBlock.java:56`
- Context: `FrostedIceBlock#tick`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(mutableBlockPos, this, Mth.nextInt(random, level.paperConfig().environment.frostedIce.delay.min, level.paperConfig().environment.frostedIce.delay.max))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-6C9DBB6A - AST call scheduleTick in FrostedIceBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FrostedIceBlock.java:64`
- Context: `FrostedIceBlock#tick`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, Mth.nextInt(random, level.paperConfig().environment.frostedIce.delay.min, level.paperConfig().environment.frostedIce.delay.max))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-2BA48D62 - AST call getBlockState in FrostedIceBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FrostedIceBlock.java:93`
- Context: `FrostedIceBlock#fewerNeigboursThan`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-40B42FDB - AST call getBlockState in FungusBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/FungusBlock.java:64`
- Context: `FungusBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-35CEE712 - AST call getBlockState in GrassBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/GrassBlock.java:34`
- Context: `GrassBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-564CDA88 - AST call getBlockState in GrassBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/GrassBlock.java:56`
- Context: `GrassBlock#performBonemeal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-84C920B9 - AST call getBlockState in GrassBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/GrassBlock.java:56`
- Context: `GrassBlock#performBonemeal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D9A4A59D - AST call getBlockState in GrassBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/GrassBlock.java:61`
- Context: `GrassBlock#performBonemeal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E4878428 - AST call getBlockState in GrowingPlantBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/GrowingPlantBlock.java:34`
- Context: `GrowingPlantBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getBlockState`
- Evidence: `context.getLevel().getBlockState(context.getClickedPos().relative(this.growthDirection))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7A9CF90C - AST call getBlockState in GrowingPlantBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/GrowingPlantBlock.java:47`
- Context: `GrowingPlantBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FC0988D9 - AST call getBlockState in GrowingPlantBodyBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/GrowingPlantBodyBlock.java:68`
- Context: `GrowingPlantBodyBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(headPos.get().relative(this.growthDirection))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-682FE256 - AST call getBlockState in GrowingPlantBodyBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/GrowingPlantBodyBlock.java:80`
- Context: `GrowingPlantBodyBlock#performBonemeal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(headPos.get())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7AA1BF33 - AST call getBlockState in GrowingPlantHeadBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/GrowingPlantHeadBlock.java:61`
- Context: `GrowingPlantHeadBlock#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BCB28480 - AST call getBlockState in GrowingPlantHeadBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/GrowingPlantHeadBlock.java:104`
- Context: `GrowingPlantHeadBlock#updateShape`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(this.growthDirection))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E37931A6 - AST call getBlockState in GrowingPlantHeadBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/GrowingPlantHeadBlock.java:129`
- Context: `GrowingPlantHeadBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(this.growthDirection))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3EA0CE08 - AST call getBlockState in GrowingPlantHeadBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/GrowingPlantHeadBlock.java:143`
- Context: `GrowingPlantHeadBlock#performBonemeal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DE99C4A6 - AST call getBlockState in HangingMossBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HangingMossBlock.java:47`
- Context: `HangingMossBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-44CC52C5 - AST call getBlockState in HangingMossBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HangingMossBlock.java:66`
- Context: `HangingMossBlock#canStayAtPosition`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6ACA0C29 - AST call getBlockState in HangingMossBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HangingMossBlock.java:85`
- Context: `HangingMossBlock#updateShape`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F9FEB04C - AST call getBlockState in HangingMossBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HangingMossBlock.java:102`
- Context: `HangingMossBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(this.getTip(level, pos).below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-582898A8 - AST call getBlockState in HangingMossBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HangingMossBlock.java:115`
- Context: `HangingMossBlock#getTip`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7D787ED8 - AST call getBlockState in HangingMossBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HangingMossBlock.java:129`
- Context: `HangingMossBlock#performBonemeal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-891C4BE3 - AST call getFluidState in HangingRootsBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HangingRootsBlock.java:51`
- Context: `HangingRootsBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-8D8B1E01 - AST call getBlockState in HangingRootsBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HangingRootsBlock.java:61`
- Context: `HangingRootsBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-445997D1 - AST call getFluidState in HeavyCoreBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HeavyCoreBlock.java:67`
- Context: `HeavyCoreBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-8B9B08D3 - AST call getBlockState in HugeMushroomBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HugeMushroomBlock.java:52`
- Context: `HugeMushroomBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A2AAFFDC - AST call getBlockState in HugeMushroomBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HugeMushroomBlock.java:53`
- Context: `HugeMushroomBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6FB44A88 - AST call getBlockState in HugeMushroomBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HugeMushroomBlock.java:54`
- Context: `HugeMushroomBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.north())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F69C117D - AST call getBlockState in HugeMushroomBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HugeMushroomBlock.java:55`
- Context: `HugeMushroomBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.east())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B3EE8D5A - AST call getBlockState in HugeMushroomBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HugeMushroomBlock.java:56`
- Context: `HugeMushroomBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.south())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8FB42A29 - AST call getBlockState in HugeMushroomBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/HugeMushroomBlock.java:57`
- Context: `HugeMushroomBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.west())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A205EB4A - AST call getBlockState in IceBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/IceBlock.java:49`
- Context: `IceBlock#afterDestroy`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8CAA0D8C - AST call getFluidState in IronBarsBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/IronBarsBlock.java:40`
- Context: `IronBarsBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-71DF1D25 - AST call getBlockState in IronBarsBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/IronBarsBlock.java:45`
- Context: `IronBarsBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1FFA34D0 - AST call getBlockState in IronBarsBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/IronBarsBlock.java:46`
- Context: `IronBarsBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-15A6BD65 - AST call getBlockState in IronBarsBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/IronBarsBlock.java:47`
- Context: `IronBarsBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos2)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CE9AA8B7 - AST call getBlockState in IronBarsBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/IronBarsBlock.java:48`
- Context: `IronBarsBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos3)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BB99F4C8 - AST call getFluidState in KelpBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/KelpBlock.java:66`
- Context: `KelpBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-7B00F0BE - AST call getBlockState in LadderBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LadderBlock.java:47`
- Context: `LadderBlock#canAttachTo`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B5FF9D0F - AST call getBlockState in LadderBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LadderBlock.java:82`
- Context: `LadderBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getBlockState`
- Evidence: `context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9FC81BAF - AST call getFluidState in LadderBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LadderBlock.java:91`
- Context: `LadderBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-EDDEA7AC - AST call getFluidState in LanternBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LanternBlock.java:43`
- Context: `LanternBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-39CEEF20 - AST call getBlockState in LeafLitterBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LeafLitterBlock.java:56`
- Context: `LeafLitterBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1AA40BEA - AST call scheduleTick in LecternBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LecternBlock.java:172`
- Context: `LecternBlock#signalPageChange`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, state.getBlock(), 2)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-185840B5 - AST call getFluidState in LightningRodBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LightningRodBlock.java:46`
- Context: `LightningRodBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-1D308D5F - AST call scheduleTick in LightningRodBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LightningRodBlock.java:99`
- Context: `LightningRodBlock#onLightningStrike`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 8)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-0707A55C - AST call scheduleTick in LightningRodBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LightningRodBlock.java:134`
- Context: `LightningRodBlock#onPlace`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 8)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-3DF9071C - AST call getFluidState in LiquidBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LiquidBlock.java:87`
- Context: `LiquidBlock#getCollisionShape`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.above())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-92715007 - AST call scheduleTick in LiquidBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LiquidBlock.java:142`
- Context: `LiquidBlock#onPlace`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, state.getFluidState().getType(), this.getFlowSpeed(level, pos))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-AA17CA36 - AST call getBlockState in LiquidBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LiquidBlock.java:194`
- Context: `LiquidBlock#shouldSpreadLiquid`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7E9450D1 - AST call getFluidState in LiquidBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LiquidBlock.java:198`
- Context: `LiquidBlock#shouldSpreadLiquid`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-40BA1773 - AST call getFluidState in LiquidBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LiquidBlock.java:199`
- Context: `LiquidBlock#shouldSpreadLiquid`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-AA8A8839 - AST call getBlockState in LiquidBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/LiquidBlock.java:208`
- Context: `LiquidBlock#shouldSpreadLiquid`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9516C692 - AST call scheduleTick in MagmaBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MagmaBlock.java:63`
- Context: `MagmaBlock#onPlace`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 20)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-93B50C7E - AST call getFluidState in MangrovePropaguleBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MangrovePropaguleBlock.java:61`
- Context: `MangrovePropaguleBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-7BF4B1DB - AST call getBlockState in MangrovePropaguleBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MangrovePropaguleBlock.java:74`
- Context: `MangrovePropaguleBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1F2F8383 - AST call getFluidState in MangroveRootsBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MangroveRootsBlock.java:40`
- Context: `MangroveRootsBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-DE3F0119 - AST call getBlockState in MossyCarpetBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MossyCarpetBlock.java:105`
- Context: `MossyCarpetBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B927F4E7 - AST call getBlockState in MossyCarpetBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MossyCarpetBlock.java:137`
- Context: `MossyCarpetBlock#getUpdatedState`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8643EC31 - AST call getBlockState in MossyCarpetBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MossyCarpetBlock.java:146`
- Context: `MossyCarpetBlock#getUpdatedState`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0E5F9155 - AST call getBlockState in MossyCarpetBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MossyCarpetBlock.java:191`
- Context: `MossyCarpetBlock#createTopperWithSideChance`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8376F3EB - AST call getBlockState in MultifaceBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MultifaceBlock.java:183`
- Context: `MultifaceBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-707BDB01 - AST call getBlockState in MultifaceBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MultifaceBlock.java:194`
- Context: `MultifaceBlock#isValidStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-869CBF15 - AST call getBlockState in MultifaceBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MultifaceBlock.java:250`
- Context: `MultifaceBlock#canAttachTo`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2E8CAE9D - AST call getBlockState in MultifaceSpreader.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MultifaceSpreader.java:99`
- Context: `MultifaceSpreader#spreadToFace`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.pos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-241A2F9D - AST call getBlockState in MultifaceSpreader.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MultifaceSpreader.java:121`
- Context: `DefaultSpreaderConfig#canSpreadInto`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(spreadPos.pos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0404FE24 - AST call getBlockState in MushroomBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MushroomBlock.java:54`
- Context: `MushroomBlock#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-AD6CC15D - AST call getBlockState in MushroomBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/MushroomBlock.java:86`
- Context: `MushroomBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A53204ED - AST call getBlockState in NetherPortalBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NetherPortalBlock.java:77`
- Context: `NetherPortalBlock#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-70BD490D - AST call getBlockState in NetherPortalBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NetherPortalBlock.java:81`
- Context: `NetherPortalBlock#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-18F3609D - AST call getBlockState in NetherPortalBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NetherPortalBlock.java:212`
- Context: `NetherPortalBlock#getExitPortalAsync`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0633FB01 - AST call getBlockState in NetherPortalBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NetherPortalBlock.java:219`
- Context: `NetherPortalBlock#getExitPortalAsync`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-02216DF9 - AST call getBlockState in NetherPortalBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NetherPortalBlock.java:307`
- Context: `NetherPortalBlock#createDimensionTransition`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-AA740088 - AST call getBlockState in NetherPortalBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NetherPortalBlock.java:350`
- Context: `NetherPortalBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.west())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9C6F5D7C - AST call getBlockState in NetherPortalBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NetherPortalBlock.java:350`
- Context: `NetherPortalBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.east())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DCEFF269 - AST call getBlockState in NetherrackBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NetherrackBlock.java:27`
- Context: `NetherrackBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0E6A9142 - AST call getBlockState in NetherrackBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NetherrackBlock.java:31`
- Context: `NetherrackBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FF2C527D - AST call getBlockState in NetherrackBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NetherrackBlock.java:51`
- Context: `NetherrackBlock#performBonemeal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-23293D21 - AST call getBlockState in NoteBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NoteBlock.java:55`
- Context: `NoteBlock#setInstrument`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-50961DAB - AST call getBlockState in NoteBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NoteBlock.java:59`
- Context: `NoteBlock#setInstrument`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1EE27A32 - AST call getBlockState in NoteBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NoteBlock.java:104`
- Context: `NoteBlock#playNote`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DA9F1C4A - AST call getBlockState in NyliumBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NyliumBlock.java:34`
- Context: `NyliumBlock#canBeNylium`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-397FFAE1 - AST call getBlockState in NyliumBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NyliumBlock.java:53`
- Context: `NyliumBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E275CA5C - AST call getBlockState in NyliumBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/NyliumBlock.java:63`
- Context: `NyliumBlock#performBonemeal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-31B95C0B - AST call scheduleTick in ObserverBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ObserverBlock.java:66`
- Context: `ObserverBlock#tick`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 2)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-5FA184B3 - AST call getBlockState in PitcherCropBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PitcherCropBlock.java:161`
- Context: `PitcherCropBlock#canGrowInto`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-46E69963 - AST call getBlockState in PitcherCropBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PitcherCropBlock.java:190`
- Context: `PitcherCropBlock#getLowerHalf`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B5411EA5 - AST call getBlockState in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:229`
- Context: `PointedDripstoneBlock#maybeTransferFluid`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1A7FD8A4 - AST call scheduleTick in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:230`
- Context: `PointedDripstoneBlock#maybeTransferFluid`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(blockPos1, blockState1.getBlock(), i1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-3D6651F6 - AST call getFluidState in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:254`
- Context: `PointedDripstoneBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(clickedPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-12BE05F5 - AST call getBlockState in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:317`
- Context: `PointedDripstoneBlock#growStalactiteOrStalagmiteIfPossible`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above(1))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C0A16F8B - AST call getBlockState in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:318`
- Context: `PointedDripstoneBlock#growStalactiteOrStalagmiteIfPossible`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above(2))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-11A2B7E5 - AST call getBlockState in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:322`
- Context: `PointedDripstoneBlock#growStalactiteOrStalagmiteIfPossible`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C2588598 - AST call getBlockState in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:339`
- Context: `PointedDripstoneBlock#growStalagmiteBelow`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3C65ED13 - AST call getFluidState in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:375`
- Context: `PointedDripstoneBlock#createDripstone`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-994658B5 - AST call getBlockState in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:437`
- Context: `PointedDripstoneBlock#calculateDripstoneThickness`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(dir))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F512A63F - AST call getBlockState in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:445`
- Context: `PointedDripstoneBlock#calculateDripstoneThickness`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(opposite))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9A162217 - AST call getBlockState in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:460`
- Context: `PointedDripstoneBlock#canTipGrow`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-211477E5 - AST call getBlockState in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:475`
- Context: `PointedDripstoneBlock#isValidPointedDripstonePlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7C926AB0 - AST call getBlockState in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:501`
- Context: `PointedDripstoneBlock#isStalactiteStartPos`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F358E71A - AST call getBlockState in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:526`
- Context: `PointedDripstoneBlock#getCauldronFillFluidType`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0C2F885A - AST call getBlockState in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:535`
- Context: `PointedDripstoneBlock#getFluidAboveStalactite`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-01AE0C4B - AST call getFluidState in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:540`
- Context: `PointedDripstoneBlock#getFluidAboveStalactite`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-CAEC3AF5 - AST call getBlockState in PointedDripstoneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PointedDripstoneBlock.java:576`
- Context: `PointedDripstoneBlock#findBlockVertical`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D83FBDCC - AST call getBlockState in PressurePlateBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/PressurePlateBlock.java:51`
- Context: `PressurePlateBlock#getSignalStrength`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B387B08C - AST call getBlockState in RootedDirtBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/RootedDirtBlock.java:26`
- Context: `RootedDirtBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9928F692 - AST call getFluidState in ScaffoldingBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ScaffoldingBlock.java:82`
- Context: `ScaffoldingBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(clickedPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-B49D8E68 - AST call scheduleTick in ScaffoldingBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ScaffoldingBlock.java:90`
- Context: `ScaffoldingBlock#onPlace`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 1)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-5486E3C5 - AST call getBlockState in ScaffoldingBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ScaffoldingBlock.java:155`
- Context: `ScaffoldingBlock#isBottom`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-268B0CEF - AST call getBlockState in ScaffoldingBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ScaffoldingBlock.java:160`
- Context: `ScaffoldingBlock#getDistance`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BA92B628 - AST call getBlockState in ScaffoldingBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ScaffoldingBlock.java:169`
- Context: `ScaffoldingBlock#getDistance`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.setWithOffset(pos, direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2AE64FE1 - AST call getBlockState in SculkBehaviour.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkBehaviour.java:19`
- Context: `#attemptSpreadVein`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-686147C2 - AST call getFluidState in SculkBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkBlock.java:70`
- Context: `SculkBlock#getRandomGrowthState`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-BB7DDC1C - AST call getBlockState in SculkBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkBlock.java:76`
- Context: `SculkBlock#canPlaceGrowth`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-41AC4D1A - AST call getBlockState in SculkBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkBlock.java:81`
- Context: `SculkBlock#canPlaceGrowth`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-BA945ABB - AST call getFluidState in SculkSensorBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkSensorBlock.java:73`
- Context: `SculkSensorBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(clickedPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-82E67925 - AST call scheduleTick in SculkSensorBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkSensorBlock.java:217`
- Context: `SculkSensorBlock#deactivate`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, state.getBlock(), 10)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-69BC06B2 - AST call scheduleTick in SculkSensorBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkSensorBlock.java:237`
- Context: `SculkSensorBlock#activate`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, state.getBlock(), this.getActiveTicks())`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-205441FF - AST call getBlockState in SculkSensorBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkSensorBlock.java:258`
- Context: `SculkSensorBlock#tryResonateVibration`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-AEB553A3 - AST call getFluidState in SculkShriekerBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkShriekerBlock.java:119`
- Context: `SculkShriekerBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-435AFC85 - AST call getBlockState in SculkSpreader.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkSpreader.java:264`
- Context: `ChargeCursor#update`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(this.pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9E0AED89 - AST call getBlockState in SculkSpreader.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkSpreader.java:268`
- Context: `ChargeCursor#update`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(this.pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-41810A2E - AST call getBlockState in SculkSpreader.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkSpreader.java:360`
- Context: `ChargeCursor#isUnobstructed`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3962E809 - AST call getBlockState in SculkVeinBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkVeinBlock.java:74`
- Context: `SculkVeinBlock#onDischarged`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-692FC8FF - AST call getFluidState in SculkVeinBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkVeinBlock.java:80`
- Context: `SculkVeinBlock#onDischarged`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-534D9525 - AST call getBlockState in SculkVeinBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkVeinBlock.java:101`
- Context: `SculkVeinBlock#attemptPlaceSculk`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5E214094 - AST call getBlockState in SculkVeinBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkVeinBlock.java:107`
- Context: `SculkVeinBlock#attemptPlaceSculk`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-494F081B - AST call getBlockState in SculkVeinBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkVeinBlock.java:123`
- Context: `SculkVeinBlock#attemptPlaceSculk`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D1F2DCBD - AST call getBlockState in SculkVeinBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkVeinBlock.java:143`
- Context: `SculkVeinBlock#hasSubstrateAccess`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F38E5DF7 - AST call getBlockState in SculkVeinBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkVeinBlock.java:162`
- Context: `SculkVeinSpreaderConfig#stateCanBeReplaced`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(spreadPos.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0D1CC257 - AST call getBlockState in SculkVeinBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SculkVeinBlock.java:166`
- Context: `SculkVeinSpreaderConfig#stateCanBeReplaced`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D32C8C54 - AST call getBlockState in SeaPickleBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SeaPickleBlock.java:49`
- Context: `SeaPickleBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getBlockState`
- Evidence: `context.getLevel().getBlockState(context.getClickedPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8C1CB85D - AST call getFluidState in SeaPickleBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SeaPickleBlock.java:53`
- Context: `SeaPickleBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-1E11AA48 - AST call getBlockState in SeaPickleBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SeaPickleBlock.java:71`
- Context: `SeaPickleBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-54DB04C8 - AST call getBlockState in SeaPickleBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SeaPickleBlock.java:124`
- Context: `SeaPickleBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9669EB3E - AST call getBlockState in SeaPickleBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SeaPickleBlock.java:147`
- Context: `SeaPickleBlock#performBonemeal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DB87C0EB - AST call getBlockState in SeaPickleBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SeaPickleBlock.java:148`
- Context: `SeaPickleBlock#performBonemeal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6242AEBF - AST call getFluidState in SeagrassBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SeagrassBlock.java:51`
- Context: `SeagrassBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-6B90C62B - AST call getBlockState in SeagrassBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SeagrassBlock.java:76`
- Context: `SeagrassBlock#isValidBonemealTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8868833B - AST call getBlockState in SegmentableBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SegmentableBlock.java:48`
- Context: `SegmentableBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getBlockState`
- Evidence: `context.getLevel().getBlockState(context.getClickedPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4363F7D0 - AST call getFluidState in ShelfBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/ShelfBlock.java:131`
- Context: `ShelfBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-12DB373D - AST call getBlockState in SideChainPartBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SideChainPartBlock.java:27`
- Context: `SideChainPartBlock#getAllBlocksConnectedTo`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D0F3806E - AST call getBlockState in SideChainPartBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SideChainPartBlock.java:98`
- Context: `SideChainPartBlock#setPart`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-00D7BD6C - AST call scheduleTick in SimpleWaterloggedBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java:29`
- Context: `SimpleWaterloggedBlock#placeLiquid`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-3A4E17B1 - AST call getBlockState in SlabBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SlabBlock.java:70`
- Context: `SlabBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getBlockState`
- Evidence: `context.getLevel().getBlockState(clickedPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-185A99E5 - AST call getFluidState in SlabBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SlabBlock.java:74`
- Context: `SlabBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(clickedPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-F949A482 - AST call getFluidState in SmallDripleafBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SmallDripleafBlock.java:55`
- Context: `SmallDripleafBlock#mayPlaceOn`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.above())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-E81B95E1 - AST call getBlockState in SmallDripleafBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SmallDripleafBlock.java:88`
- Context: `SmallDripleafBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A463D077 - AST call getFluidState in SmallDripleafBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SmallDripleafBlock.java:130`
- Context: `SmallDripleafBlock#performBonemeal`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-0A5D22EC - AST call getBlockState in SmallDripleafBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SmallDripleafBlock.java:134`
- Context: `SmallDripleafBlock#performBonemeal`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-420903E3 - AST call scheduleTick in SnifferEggBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SnifferEggBlock.java:67`
- Context: `SnifferEggBlock#rescheduleTick`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, (baseDelay / 3) + level.random.nextInt(RANDOM_HATCH_OFFSET_TICKS))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-F90B2677 - AST call scheduleTick in SnifferEggBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SnifferEggBlock.java:111`
- Context: `SnifferEggBlock#onPlace`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, i1 + level.random.nextInt(300))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-B4BA889C - AST call getBlockState in SnifferEggBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SnifferEggBlock.java:120`
- Context: `SnifferEggBlock#hatchBoost`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B53A214A - AST call getBlockState in SnowLayerBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SnowLayerBlock.java:78`
- Context: `SnowLayerBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FB77FF3A - AST call getBlockState in SnowLayerBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SnowLayerBlock.java:127`
- Context: `SnowLayerBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getBlockState`
- Evidence: `context.getLevel().getBlockState(context.getClickedPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D938438D - AST call getBlockState in SnowyDirtBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SnowyDirtBlock.java:49`
- Context: `SnowyDirtBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getBlockState`
- Evidence: `context.getLevel().getBlockState(context.getClickedPos().above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8C975D17 - AST call getBlockState in SoulFireBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SoulFireBlock.java:41`
- Context: `SoulFireBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CC3543DD - AST call scheduleTick in SoulSandBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SoulSandBlock.java:73`
- Context: `SoulSandBlock#onPlace`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, this, 20)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-ADC2CA94 - AST call getBlockState in SpongeBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SpongeBlock.java:115`
- Context: `SpongeBlock#removeWaterBreadthFirstSearch`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B575B90A - AST call getFluidState in SpongeBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SpongeBlock.java:116`
- Context: `SpongeBlock#removeWaterBreadthFirstSearch`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-9FD103EC - AST call getBlockState in SporeBlossomBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SporeBlossomBlock.java:68`
- Context: `SporeBlossomBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-85F3D46B - AST call getFluidState in StairBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/StairBlock.java:101`
- Context: `StairBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(clickedPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-6AC91DC4 - AST call getBlockState in StairBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/StairBlock.java:136`
- Context: `StairBlock#getStairsShape`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5F89AF14 - AST call getBlockState in StairBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/StairBlock.java:148`
- Context: `StairBlock#getStairsShape`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(direction.getOpposite()))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D90197D0 - AST call getBlockState in StairBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/StairBlock.java:164`
- Context: `StairBlock#canTakeShape`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(face))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F82F9A6F - AST call getBlockState in StandingSignBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/StandingSignBlock.java:39`
- Context: `StandingSignBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-74919F97 - AST call getFluidState in StandingSignBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/StandingSignBlock.java:44`
- Context: `StandingSignBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-1DED0775 - AST call getBlockState in StemBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/StemBlock.java:81`
- Context: `StemBlock#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-030813E1 - AST call getBlockState in StemBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/StemBlock.java:82`
- Context: `StemBlock#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0F96397E - AST call getBlockState in SugarCaneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SugarCaneBlock.java:54`
- Context: `SugarCaneBlock#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below(i))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-871AC03D - AST call getBlockState in SugarCaneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SugarCaneBlock.java:91`
- Context: `SugarCaneBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-249C7AD1 - AST call getBlockState in SugarCaneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SugarCaneBlock.java:99`
- Context: `SugarCaneBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FE56C5E2 - AST call getFluidState in SugarCaneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SugarCaneBlock.java:100`
- Context: `SugarCaneBlock#canSurvive`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos.relative(direction))`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-5D11C8A8 - AST call getBlockState in SugarCaneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SugarCaneBlock.java:122`
- Context: `SugarCaneBlock#isValidBonemealTarget`
- Receiver: `world`
- Method: `getBlockState`
- Evidence: `world.getBlockState(pos.below(reedHeight))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E0E2D050 - AST call getBlockState in SugarCaneBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/SugarCaneBlock.java:137`
- Context: `SugarCaneBlock#performBonemeal`
- Receiver: `world`
- Method: `getBlockState`
- Evidence: `world.getBlockState(pos.below(reedHeight))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-55666A88 - AST call getFluidState in TallSeagrassBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TallSeagrassBlock.java:57`
- Context: `TallSeagrassBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos().above())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-A0563CAC - AST call getBlockState in TallSeagrassBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TallSeagrassBlock.java:69`
- Context: `TallSeagrassBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F011EE06 - AST call getFluidState in TallSeagrassBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TallSeagrassBlock.java:72`
- Context: `TallSeagrassBlock#canSurvive`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-041ABBE0 - AST call scheduleTick in TargetBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TargetBlock.java:105`
- Context: `TargetBlock#setOutputPower`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, state.getBlock(), waitTime)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-E61FE32C - AST call scheduleTick in TrapDoorBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TrapDoorBlock.java:113`
- Context: `TrapDoorBlock#toggle`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-D241D569 - AST call getFluidState in TrapDoorBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TrapDoorBlock.java:177`
- Context: `TrapDoorBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-2BB97CC8 - AST call getBlockState in TurtleEggBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TurtleEggBlock.java:148`
- Context: `TurtleEggBlock#isSand`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-034A6274 - AST call getBlockState in TurtleEggBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/TurtleEggBlock.java:177`
- Context: `TurtleEggBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getBlockState`
- Evidence: `context.getLevel().getBlockState(context.getClickedPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-033E1B01 - AST call getBlockState in VegetationBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/VegetationBlock.java:52`
- Context: `VegetationBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-575EB325 - AST call getBlockState in VineBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/VineBlock.java:110`
- Context: `VineBlock#canSupportAtFace`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C300EF21 - AST call getBlockState in VineBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/VineBlock.java:134`
- Context: `VineBlock#getUpdatedState`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DD79B3FC - AST call getBlockState in VineBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/VineBlock.java:175`
- Context: `VineBlock#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8CE43172 - AST call getBlockState in VineBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/VineBlock.java:234`
- Context: `VineBlock#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-42650380 - AST call getBlockState in VineBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/VineBlock.java:271`
- Context: `VineBlock#canSpread`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C7829C54 - AST call getBlockState in VineBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/VineBlock.java:283`
- Context: `VineBlock#canBeReplaced`
- Receiver: `useContext.getLevel()`
- Method: `getBlockState`
- Evidence: `useContext.getLevel().getBlockState(useContext.getClickedPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7452DD9B - AST call getBlockState in VineBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/VineBlock.java:289`
- Context: `VineBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getBlockState`
- Evidence: `context.getLevel().getBlockState(context.getClickedPos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-60EFBFFE - AST call getBlockState in WallBannerBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WallBannerBlock.java:42`
- Context: `WallBannerBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(state.getValue(FACING).getOpposite()))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FD8A9EF3 - AST call getFluidState in WallBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WallBlock.java:115`
- Context: `WallBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-119D1F74 - AST call getBlockState in WallBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WallBlock.java:121`
- Context: `WallBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-82440D67 - AST call getBlockState in WallBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WallBlock.java:122`
- Context: `WallBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E2268A11 - AST call getBlockState in WallBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WallBlock.java:123`
- Context: `WallBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos2)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-37920238 - AST call getBlockState in WallBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WallBlock.java:124`
- Context: `WallBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos3)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-58786A31 - AST call getBlockState in WallBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WallBlock.java:125`
- Context: `WallBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos4)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E62DCC06 - AST call getBlockState in WallBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WallBlock.java:189`
- Context: `WallBlock#sideUpdate`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5B56FA46 - AST call getBlockState in WallHangingSignBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WallHangingSignBlock.java:103`
- Context: `WallHangingSignBlock#canAttachTo`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-39CD6A99 - AST call getFluidState in WallHangingSignBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WallHangingSignBlock.java:112`
- Context: `WallHangingSignBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-F9E03417 - AST call getBlockState in WallSignBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WallSignBlock.java:50`
- Context: `WallSignBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(state.getValue(FACING).getOpposite()))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DF238199 - AST call getFluidState in WallSignBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WallSignBlock.java:56`
- Context: `WallSignBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-C418495B - AST call getBlockState in WallSkullBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WallSkullBlock.java:52`
- Context: `WallSkullBlock#getStateForPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(clickedPos.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-66DB1F7E - AST call getBlockState in WallTorchBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WallTorchBlock.java:59`
- Context: `WallTorchBlock#canSurvive`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-818E74DD - AST call getFluidState in WaterlilyBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WaterlilyBlock.java:52`
- Context: `WaterlilyBlock#mayPlaceOn`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-B5A3954A - AST call getFluidState in WaterlilyBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WaterlilyBlock.java:53`
- Context: `WaterlilyBlock#mayPlaceOn`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos.above())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-0BFBEE3D - AST call getFluidState in WaterloggedTransparentBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WaterloggedTransparentBlock.java:35`
- Context: `WaterloggedTransparentBlock#getStateForPlacement`
- Receiver: `context.getLevel()`
- Method: `getFluidState`
- Evidence: `context.getLevel().getFluidState(context.getClickedPos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-0B318A0F - AST call getFluidState in WeatheringCopperGolemStatueBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WeatheringCopperGolemStatueBlock.java:68`
- Context: `WeatheringCopperGolemStatueBlock#useItemOn`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-17D0C45D - AST call getBlockState in WetSpongeBlock.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/WetSpongeBlock.java:41`
- Context: `WetSpongeBlock#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4A71F2CE - AST call getFluidState in TreeGrower.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/grower/TreeGrower.java:168`
- Context: `TreeGrower#growTree`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-81CB1EBB - AST call getBlockState in TreeGrower.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/grower/TreeGrower.java:171`
- Context: `TreeGrower#growTree`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E6383079 - AST call getBlockState in TreeGrower.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/grower/TreeGrower.java:186`
- Context: `TreeGrower#isTwoByTwoSapling`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.offset(xOffset, 0, yOffset))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-678453B8 - AST call getBlockState in TreeGrower.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/grower/TreeGrower.java:187`
- Context: `TreeGrower#isTwoByTwoSapling`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.offset(xOffset + 1, 0, yOffset))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6FDDB753 - AST call getBlockState in TreeGrower.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/grower/TreeGrower.java:188`
- Context: `TreeGrower#isTwoByTwoSapling`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.offset(xOffset, 0, yOffset + 1))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F6AF3A06 - AST call getBlockState in TreeGrower.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/grower/TreeGrower.java:189`
- Context: `TreeGrower#isTwoByTwoSapling`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.offset(xOffset + 1, 0, yOffset + 1))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5DFED1AF - AST call getBlockState in TreeGrower.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/grower/TreeGrower.java:194`
- Context: `TreeGrower#hasFlowers`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B20AB452 - AST call getBlockState in AmbientDesertBlockSoundsPlayer.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/sounds/AmbientDesertBlockSoundsPlayer.java:25`
- Context: `AmbientDesertBlockSoundsPlayer#playAmbientSandSounds`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B2A4647E - AST call getBlockState in AmbientDesertBlockSoundsPlayer.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/sounds/AmbientDesertBlockSoundsPlayer.java:40`
- Context: `AmbientDesertBlockSoundsPlayer#playAmbientDeadBushSounds`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-1301752D - AST call getBlockState in AmbientDesertBlockSoundsPlayer.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/sounds/AmbientDesertBlockSoundsPlayer.java:52`
- Context: `AmbientDesertBlockSoundsPlayer#shouldPlayDesertDryVegetationBlockSounds`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7649222D - AST call getBlockState in AmbientDesertBlockSoundsPlayer.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/sounds/AmbientDesertBlockSoundsPlayer.java:53`
- Context: `AmbientDesertBlockSoundsPlayer#shouldPlayDesertDryVegetationBlockSounds`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FD6A60FD - AST call getBlockState in AmbientDesertBlockSoundsPlayer.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/sounds/AmbientDesertBlockSoundsPlayer.java:83`
- Context: `AmbientDesertBlockSoundsPlayer#columnContainsTriggeringBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5C1ABA53 - AST call getBlockState in AmbientDesertBlockSoundsPlayer.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/sounds/AmbientDesertBlockSoundsPlayer.java:87`
- Context: `AmbientDesertBlockSoundsPlayer#columnContainsTriggeringBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4FFB0607 - AST call getBlockState in AmbientDesertBlockSoundsPlayer.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/sounds/AmbientDesertBlockSoundsPlayer.java:98`
- Context: `AmbientDesertBlockSoundsPlayer#columnContainsTriggeringBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.setY(i + 1))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A8716435 - AST call getBlockState in AmbientDesertBlockSoundsPlayer.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/sounds/AmbientDesertBlockSoundsPlayer.java:99`
- Context: `AmbientDesertBlockSoundsPlayer#columnContainsTriggeringBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.setY(i))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-EA043BDD - AST call getBlockState in BlockInWorld.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/state/pattern/BlockInWorld.java:26`
- Context: `BlockInWorld#getState`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(this.pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-75CA8E87 - AST call getBlockState in LevelChunk.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/LevelChunk.java:319`
- Context: `LevelChunk#getBlockState`
- Receiver: `levelChunkSection`
- Method: `getBlockState`
- Evidence: `levelChunkSection.getBlockState(x & 15, y & 15, z & 15)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-916E2F87 - AST call getFluidState in LevelChunk.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/LevelChunk.java:356`
- Context: `LevelChunk#getFluidState`
- Receiver: `levelChunkSection.states.get((y & 15) << 8 | (z & 15) << 4 | x & 15)`
- Method: `getFluidState`
- Evidence: `levelChunkSection.states.get((y & 15) << 8 | (z & 15) << 4 | x & 15).getFluidState()`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-1DB6471A - AST call getBlockState in UpgradeData.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/UpgradeData.java:122`
- Context: `UpgradeData#upgrade`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(savedTick.pos())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FF65BA76 - AST call getFluidState in UpgradeData.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/UpgradeData.java:126`
- Context: `UpgradeData#upgrade`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(savedTick.pos())`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-9387A0A1 - AST call getBlockState in UpgradeData.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/UpgradeData.java:153`
- Context: `UpgradeData#upgradeSides`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0C24FD21 - AST call getBlockState in UpgradeData.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/UpgradeData.java:168`
- Context: `UpgradeData#updateState`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(offsetPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-21C17049 - AST call getBlockState in UpgradeData.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/UpgradeData.java:335`
- Context: `#updateShape`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(offsetPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-ECAB66FE - AST call getBlockState in UpgradeData.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/UpgradeData.java:381`
- Context: `#updateShape`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(offsetPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-50A0E51E - AST call getBlockState in UpgradeData.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/UpgradeData.java:408`
- Context: `#processChunk`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-398B71B2 - AST call getBlockState in UpgradeData.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/UpgradeData.java:414`
- Context: `#processChunk`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-288B14D5 - AST call getBlockState in HasSturdyFacePredicate.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/blockpredicates/HasSturdyFacePredicate.java:29`
- Context: `HasSturdyFacePredicate#test`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F87A58CC - AST call getBlockState in StateTestingPredicate.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/blockpredicates/StateTestingPredicate.java:24`
- Context: `StateTestingPredicate#test`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.offset(this.offset))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7A7487FD - AST call getBlockState in AbstractHugeMushroomFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/AbstractHugeMushroomFeature.java:28`
- Context: `AbstractHugeMushroomFeature#placeMushroomBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutablePos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F695246F - AST call getBlockState in AbstractHugeMushroomFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/AbstractHugeMushroomFeature.java:48`
- Context: `AbstractHugeMushroomFeature#isValidPosition`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F0B18734 - AST call getBlockState in AbstractHugeMushroomFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/AbstractHugeMushroomFeature.java:57`
- Context: `AbstractHugeMushroomFeature#isValidPosition`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutablePos.setWithOffset(pos, i1, i, i2))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-924E1B27 - AST call getBlockState in BasaltColumnsFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/BasaltColumnsFeature.java:84`
- Context: `BasaltColumnsFeature#placeColumn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-D76B4871 - AST call getBlockState in BasaltColumnsFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/BasaltColumnsFeature.java:114`
- Context: `BasaltColumnsFeature#canPlaceAt`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.move(Direction.DOWN))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C71D3CAD - AST call getBlockState in BasaltColumnsFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/BasaltColumnsFeature.java:123`
- Context: `BasaltColumnsFeature#findAir`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CA117C63 - AST call getBlockState in BasaltColumnsFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/BasaltColumnsFeature.java:139`
- Context: `BasaltColumnsFeature#isAirOrLavaOcean`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3C38E973 - AST call getBlockState in BlockPileFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/BlockPileFeature.java:47`
- Context: `BlockPileFeature#mayPlaceOn`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F2C1E8DD - AST call getBlockState in CoralFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/CoralFeature.java:38`
- Context: `CoralFeature#placeCoralBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-55152788 - AST call getBlockState in CoralFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/CoralFeature.java:39`
- Context: `CoralFeature#placeCoralBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FE1F91BF - AST call getBlockState in CoralFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/CoralFeature.java:53`
- Context: `CoralFeature#placeCoralBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CE2C7064 - AST call getBlockState in DeltaFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/DeltaFeature.java:64`
- Context: `DeltaFeature#isClear`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F59FE441 - AST call getBlockState in DeltaFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/DeltaFeature.java:71`
- Context: `DeltaFeature#isClear`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5DAD81AB - AST call getBlockState in DripstoneClusterFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/DripstoneClusterFeature.java:148`
- Context: `DripstoneClusterFeature#isLava`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-87108485 - AST call getBlockState in DripstoneClusterFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/DripstoneClusterFeature.java:162`
- Context: `DripstoneClusterFeature#canPlacePool`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-706B4BC3 - AST call getFluidState in DripstoneClusterFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/DripstoneClusterFeature.java:164`
- Context: `DripstoneClusterFeature#canPlacePool`
- Receiver: `level.getBlockState(pos.above())`
- Method: `getFluidState`
- Evidence: `level.getBlockState(pos.above()).getFluidState()`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-DB8B2320 - AST call getBlockState in DripstoneClusterFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/DripstoneClusterFeature.java:164`
- Context: `DripstoneClusterFeature#canPlacePool`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7680AA9E - AST call getBlockState in DripstoneClusterFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/DripstoneClusterFeature.java:181`
- Context: `DripstoneClusterFeature#canBeAdjacentToWater`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0D51B7C3 - AST call getBlockState in DripstoneUtils.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/DripstoneUtils.java:78`
- Context: `DripstoneUtils#growPointedDripstone`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.relative(direction.getOpposite()))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5007F78E - AST call getBlockState in DripstoneUtils.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/DripstoneUtils.java:92`
- Context: `DripstoneUtils#placeDripstoneBlockIfPossible`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E44D6FE9 - AST call getBlockState in EndPodiumFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/EndPodiumFeature.java:79`
- Context: `EndPodiumFeature#dropPreviousAndSetBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B9FDA7B7 - AST call getBlockState in FallenTreeFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/FallenTreeFeature.java:106`
- Context: `FallenTreeFeature#isOverSolidGround`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-36BAC280 - AST call getBlockState in Feature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/Feature.java:184`
- Context: `Feature#safeSetBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-25D66321 - AST call getBlockState in Feature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/Feature.java:229`
- Context: `Feature#markAboveForPostProcessing`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-8C72D828 - AST call getBlockState in FossilFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/FossilFeature.java:80`
- Context: `FossilFeature#countEmptyCorners`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B9346367 - AST call getBlockState in HugeFungusFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java:77`
- Context: `HugeFungusFeature#placeStem`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C2ABB892 - AST call getBlockState in HugeFungusFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java:120`
- Context: `HugeFungusFeature#placeHat`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C74C0F4D - AST call getBlockState in HugeFungusFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java:161`
- Context: `HugeFungusFeature#placeHatDropBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FA6E76C7 - AST call getBlockState in IcebergFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/IcebergFeature.java:126`
- Context: `IcebergFeature#carve`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F6F7422C - AST call getBlockState in IcebergFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/IcebergFeature.java:141`
- Context: `IcebergFeature#removeFloatingSnowLayer`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-9781B75A - AST call getBlockState in IcebergFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/IcebergFeature.java:179`
- Context: `IcebergFeature#setIcebergBlock`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-15003786 - AST call getBlockState in IcebergFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/IcebergFeature.java:239`
- Context: `IcebergFeature#belowIsAir`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-16608DAE - AST call getBlockState in IcebergFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/IcebergFeature.java:249`
- Context: `IcebergFeature#smooth`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-33D51D80 - AST call getBlockState in IcebergFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/IcebergFeature.java:256`
- Context: `IcebergFeature#smooth`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos.west())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-7057E2D5 - AST call getBlockState in IcebergFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/IcebergFeature.java:257`
- Context: `IcebergFeature#smooth`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos.east())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2FDF5FB0 - AST call getBlockState in IcebergFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/IcebergFeature.java:258`
- Context: `IcebergFeature#smooth`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos.north())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-47DE6C2B - AST call getBlockState in IcebergFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/IcebergFeature.java:259`
- Context: `IcebergFeature#smooth`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos.south())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F5552EB6 - AST call getBlockState in LargeDripstoneFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/LargeDripstoneFeature.java:110`
- Context: `LargeDripstoneFeature#placeDebugMarkers`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5C15600D - AST call getBlockState in LargeDripstoneFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/LargeDripstoneFeature.java:149`
- Context: `LargeDripstone#moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-332EEAB5 - AST call getBlockState in LargeDripstoneFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/LargeDripstoneFeature.java:194`
- Context: `LargeDripstone#placeBlocks`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-475A39AF - AST call getBlockState in MultifaceGrowthFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/MultifaceGrowthFeature.java:66`
- Context: `MultifaceGrowthFeature#placeGrowthIfPossible`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.setWithOffset(pos, direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-FC3E4AF0 - AST call getBlockState in PointedDripstoneFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/PointedDripstoneFeature.java:29`
- Context: `PointedDripstoneFeature#place`
- Receiver: `levelAccessor`
- Method: `getBlockState`
- Evidence: `levelAccessor.getBlockState(blockPos.relative(tipDirection.get()))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A88E39EA - AST call getBlockState in PointedDripstoneFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/PointedDripstoneFeature.java:38`
- Context: `PointedDripstoneFeature#getTipDirection`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6C782E14 - AST call getBlockState in PointedDripstoneFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/PointedDripstoneFeature.java:39`
- Context: `PointedDripstoneFeature#getTipDirection`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E047F18C - AST call getBlockState in ReplaceBlobsFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/ReplaceBlobsFeature.java:55`
- Context: `ReplaceBlobsFeature#findTarget`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(topPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-46528C51 - AST call getBlockState in RootSystemFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/RootSystemFeature.java:44`
- Context: `RootSystemFeature#spaceForTree`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-504BC726 - AST call getFluidState in RootSystemFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/RootSystemFeature.java:74`
- Context: `RootSystemFeature#placeDirtAndTree`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-8EA7E81E - AST call getBlockState in RootSystemFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/RootSystemFeature.java:74`
- Context: `RootSystemFeature#placeDirtAndTree`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-CD1354DE - AST call getBlockState in RootSystemFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/RootSystemFeature.java:104`
- Context: `RootSystemFeature#placeRootedDirt`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0CB1BA96 - AST call getBlockState in RootSystemFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/RootSystemFeature.java:125`
- Context: `RootSystemFeature#placeRoots`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutablePos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6597395F - AST call getBlockState in SculkPatchFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SculkPatchFeature.java:69`
- Context: `SculkPatchFeature#canSpreadFrom`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E69D1823 - AST call getBlockState in SculkPatchFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SculkPatchFeature.java:72`
- Context: `SculkPatchFeature#canSpreadFrom`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2F2E57AE - AST call getBlockState in TreeFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java:202`
- Context: `TreeFeature#updateLeaves`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-864FC104 - AST call getBlockState in TreeFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java:215`
- Context: `TreeFeature#updateLeaves`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DD7B6901 - AST call getBlockState in TwistingVinesFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/TwistingVinesFeature.java:68`
- Context: `TwistingVinesFeature#findFirstAirBlockAboveGround`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-A885519F - AST call getBlockState in TwistingVinesFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/TwistingVinesFeature.java:97`
- Context: `TwistingVinesFeature#isInvalidPlacementLocation`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-AC1C49F1 - AST call getBlockState in UnderwaterMagmaFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/UnderwaterMagmaFeature.java:64`
- Context: `UnderwaterMagmaFeature#isValidPlacement`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-6C63B33E - AST call getBlockState in UnderwaterMagmaFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/UnderwaterMagmaFeature.java:82`
- Context: `UnderwaterMagmaFeature#isVisibleFromOutside`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5A7755CD - AST call getBlockState in VegetationPatchFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/VegetationPatchFeature.java:65`
- Context: `VegetationPatchFeature#placeGroundPatch`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C6341BCE - AST call getBlockState in VegetationPatchFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/VegetationPatchFeature.java:114`
- Context: `VegetationPatchFeature#placeGround`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutablePos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4CDACA91 - AST call getBlockState in WaterloggedVegetationPatchFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/WaterloggedVegetationPatchFeature.java:54`
- Context: `WaterloggedVegetationPatchFeature#isExposedDirection`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutablePos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-812B8C44 - AST call getBlockState in WaterloggedVegetationPatchFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/WaterloggedVegetationPatchFeature.java:62`
- Context: `WaterloggedVegetationPatchFeature#placeVegetation`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B2EFD456 - AST call getBlockState in WeepingVinesFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/WeepingVinesFeature.java:55`
- Context: `WeepingVinesFeature#placeRoofNetherWart`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos1.setWithOffset(mutableBlockPos, direction))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B09CF074 - AST call getBlockState in WeepingVinesFeature.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/WeepingVinesFeature.java:80`
- Context: `WeepingVinesFeature#placeRoofWeepingVines`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.above())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2747B3D1 - AST call getBlockState in PlacementContext.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/placement/PlacementContext.java:35`
- Context: `PlacementContext#getBlockState`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-83948BBB - AST call getBlockState in ChunkSkyLightSources.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/lighting/ChunkSkyLightSources.java:85`
- Context: `ChunkSkyLightSources#update`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-2AD25D38 - AST call getBlockState in ChunkSkyLightSources.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/lighting/ChunkSkyLightSources.java:87`
- Context: `ChunkSkyLightSources#update`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-F32D52CE - AST call getBlockState in ChunkSkyLightSources.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/lighting/ChunkSkyLightSources.java:92`
- Context: `ChunkSkyLightSources#update`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos2)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-03658A38 - AST call getBlockState in ChunkSkyLightSources.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/lighting/ChunkSkyLightSources.java:119`
- Context: `ChunkSkyLightSources#findLowestSourceBelow`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos1)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-11B45319 - AST call getFluidState in FluidState.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/FluidState.java:89`
- Context: `FluidState#shouldRenderBackwardUpFace`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(blockPos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-D20896F9 - AST call getBlockState in FluidState.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/FluidState.java:90`
- Context: `FluidState#shouldRenderBackwardUpFace`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4130949C - AST call getBlockState in LavaFluid.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/LavaFluid.java:55`
- Context: `LavaFluid#animateTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-E332B961 - AST call getBlockState in LavaFluid.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/LavaFluid.java:94`
- Context: `LavaFluid#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-4D237FA4 - AST call getBlockState in LavaFluid.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/LavaFluid.java:98`
- Context: `LavaFluid#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-33003B94 - AST call getBlockState in LavaFluid.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/LavaFluid.java:121`
- Context: `LavaFluid#randomTick`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(up)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-47AB1413 - AST call getBlockState in LavaFluid.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/LavaFluid.java:153`
- Context: `LavaFluid#isFlammable`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C03FDB2A - AST call getFluidState in LavaFluid.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/LavaFluid.java:231`
- Context: `LavaFluid#spreadTo`
- Receiver: `level`
- Method: `getFluidState`
- Evidence: `level.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-CABA3AC0 - AST call getBlockState in PathfindingContext.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/pathfinder/PathfindingContext.java:33`
- Context: `PathfindingContext#getBlockState`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-C2840EA5 - AST call getFluidState in SwimNodeEvaluator.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/pathfinder/SwimNodeEvaluator.java:94`
- Context: `SwimNodeEvaluator#findAcceptedNode`
- Receiver: `this.currentContext.level()`
- Method: `getFluidState`
- Evidence: `this.currentContext.level().getFluidState(new BlockPos(x, y, z))`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-72772CB5 - AST call getFluidState in WalkNodeEvaluator.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: fluidstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/pathfinder/WalkNodeEvaluator.java:206`
- Context: `WalkNodeEvaluator#getFloorLevel`
- Receiver: `blockGetter`
- Method: `getFluidState`
- Evidence: `blockGetter.getFluidState(pos)`
- Suggested fix: Use getFluidIfLoaded or derive fluid state from a loaded BlockState.

### AO-AST-0787EDDE - AST call getBlockState in WalkNodeEvaluator.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/pathfinder/WalkNodeEvaluator.java:213`
- Context: `WalkNodeEvaluator#getFloorLevel`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(blockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-57908940 - AST call getBlockState in PortalForcer.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/portal/PortalForcer.java:209`
- Context: `PortalForcer#canPortalReplaceBlock`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(pos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-62E1BFA8 - AST call getBlockState in PortalForcer.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/portal/PortalForcer.java:226`
- Context: `PortalForcer#canHostFrame`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(offsetPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5766E950 - AST call getBlockState in PortalForcer.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/portal/PortalForcer.java:231`
- Context: `PortalForcer#canHostFrame`
- Receiver: `this.level`
- Method: `getBlockState`
- Evidence: `this.level.getBlockState(offsetPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-963DEB7B - AST call getBlockState in PortalShape.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/portal/PortalShape.java:90`
- Context: `PortalShape#calculateBottomLeft`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(pos.below())`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-178D2715 - AST call getBlockState in PortalShape.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/portal/PortalShape.java:109`
- Context: `PortalShape#getDistanceUntilEdgeAboveFrame`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5D7CA1D4 - AST call getBlockState in PortalShape.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/portal/PortalShape.java:118`
- Context: `PortalShape#getDistanceUntilEdgeAboveFrame`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos.move(Direction.DOWN))`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-680D4A50 - AST call getBlockState in PortalShape.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/portal/PortalShape.java:139`
- Context: `PortalShape#hasTopFrame`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-B8D8D48A - AST call getBlockState in PortalShape.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/portal/PortalShape.java:142`
- Context: `PortalShape#hasTopFrame`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(mutableBlockPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3A04D781 - AST call getBlockState in PortalShape.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/portal/PortalShape.java:153`
- Context: `PortalShape#getDistanceUntilTop`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(checkPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-73415593 - AST call getBlockState in PortalShape.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/portal/PortalShape.java:158`
- Context: `PortalShape#getDistanceUntilTop`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(checkPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-5410A283 - AST call getBlockState in PortalShape.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/portal/PortalShape.java:164`
- Context: `PortalShape#getDistanceUntilTop`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(checkPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-DDE98D99 - AST call getBlockState in PortalShape.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/portal/PortalShape.java:174`
- Context: `PortalShape#getDistanceUntilTop`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(checkPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-3B93AA37 - AST call getBlockState in PortalShape.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/portal/PortalShape.java:175`
- Context: `PortalShape#getDistanceUntilTop`
- Receiver: `level`
- Method: `getBlockState`
- Evidence: `level.getBlockState(checkPos)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-20C96666 - AST call getBlockState in MinecartCollisionContext.java
- Severity: high
- Category: uncategorized sync-load risk
- Sink: blockstate-read
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/phys/shapes/MinecartCollisionContext.java:22`
- Context: `MinecartCollisionContext#setupContext`
- Receiver: `minecart.level()`
- Method: `getBlockState`
- Evidence: `minecart.level().getBlockState(currentBlockPosOrRailBelow)`
- Suggested fix: Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.

### AO-AST-0CDCC3DD - AST call scheduleTick in CraftLimitedRegion.java
- Severity: medium
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/generator/CraftLimitedRegion.java:295`
- Context: `CraftLimitedRegion#scheduleBlockUpdate`
- Receiver: `getHandle()`
- Method: `scheduleTick`
- Evidence: `getHandle().scheduleTick(position, getHandle().getBlockState(position).getBlock(), 0)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-B017A10C - AST call scheduleTick in CraftLimitedRegion.java
- Severity: medium
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/generator/CraftLimitedRegion.java:301`
- Context: `CraftLimitedRegion#scheduleFluidUpdate`
- Receiver: `getHandle()`
- Method: `scheduleTick`
- Evidence: `getHandle().scheduleTick(position, getHandle().getFluidState(position).getType(), 0)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-478736BB - AST call scheduleTick in DelegatedGeneratorAccess.java
- Severity: medium
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/util/DelegatedGeneratorAccess.java:111`
- Context: `DelegatedGeneratorAccess#scheduleTick`
- Receiver: `this.delegate`
- Method: `scheduleTick`
- Evidence: `this.delegate.scheduleTick(pos, block, delay, priority)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-26AF5298 - AST call scheduleTick in DelegatedGeneratorAccess.java
- Severity: medium
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/util/DelegatedGeneratorAccess.java:116`
- Context: `DelegatedGeneratorAccess#scheduleTick`
- Receiver: `this.delegate`
- Method: `scheduleTick`
- Evidence: `this.delegate.scheduleTick(pos, block, delay)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-33B0459B - AST call scheduleTick in DelegatedGeneratorAccess.java
- Severity: medium
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/util/DelegatedGeneratorAccess.java:126`
- Context: `DelegatedGeneratorAccess#scheduleTick`
- Receiver: `this.delegate`
- Method: `scheduleTick`
- Evidence: `this.delegate.scheduleTick(pos, fluid, delay, priority)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-61AEFE90 - AST call scheduleTick in DelegatedGeneratorAccess.java
- Severity: medium
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/util/DelegatedGeneratorAccess.java:131`
- Context: `DelegatedGeneratorAccess#scheduleTick`
- Receiver: `this.delegate`
- Method: `scheduleTick`
- Evidence: `this.delegate.scheduleTick(pos, fluid, delay)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-6FE8A395 - AST call scheduleTick in TransformerGeneratorAccess.java
- Severity: medium
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `paper-server/src/main/java/org/bukkit/craftbukkit/util/TransformerGeneratorAccess.java:62`
- Context: `TransformerGeneratorAccess#setCraftBlock`
- Receiver: `this`
- Method: `scheduleTick`
- Evidence: `this.scheduleTick(position, fluidState.getType(), 0)`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-5D55AAFE - AST call scheduleTick in UpgradeData.java
- Severity: medium
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/UpgradeData.java:123`
- Context: `UpgradeData#upgrade`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(savedTick.pos(), block, savedTick.delay(), savedTick.priority())`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-6C46BAF0 - AST call scheduleTick in UpgradeData.java
- Severity: medium
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/UpgradeData.java:127`
- Context: `UpgradeData#upgrade`
- Receiver: `level`
- Method: `scheduleTick`
- Evidence: `level.scheduleTick(savedTick.pos(), fluid, savedTick.delay(), savedTick.priority())`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.

### AO-AST-782A520F - AST call scheduleTick in SimpleBlockFeature.java
- Severity: medium
- Category: uncategorized sync-load risk
- Sink: schedule-tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java:36`
- Context: `SimpleBlockFeature#place`
- Receiver: `simpleBlockConfiguration`
- Method: `scheduleTick`
- Evidence: `simpleBlockConfiguration.scheduleTick()`
- Suggested fix: Use the scheduled tick owner handoff path for cross-cell future ticks.
