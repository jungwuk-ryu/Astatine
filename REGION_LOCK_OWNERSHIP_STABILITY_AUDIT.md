# Region Lock Ownership Stability Audit

작성일: 2026-05-03
대상: ShreddedPaper independent region ticking, region lock lifecycle, owner handoff, watchdog stability
주 독자: AI coding agent
운영 상황: production server may be online. Do not assume local reproduction is allowed.

## Agent Contract

이 문서는 사람이 읽기 편한 설명서가 아니라, AI Agent가 다음 루프를 반복하기 위한 작업 지시서다.

1. 하나의 audit ID만 선택한다.
2. 코드와 로그로 `Observed`와 `Hypothesis`를 검증한다.
3. 패치 전 `Patch Rules`와 `Forbidden Changes`를 확인한다.
4. 작은 패치를 작성한다.
5. 가능한 검증을 수행한다.
6. 이 문서의 `Status`, `Evidence`, `Next Action`을 갱신한다.
7. "fixed"라고 말하지 않고, `Patched`, `Built`, `Deployed`, `Verified`를 분리해서 보고한다.

### Status Values

- `Unreviewed`: 아직 코드 감사 전.
- `Suspected`: crash/log/code evidence가 있으나 패치 전.
- `Patch Candidate`: 패치 방향이 정해졌으나 코드 변경 전.
- `Patched-Unbuilt`: 소스 패치만 있음. jar 미생성.
- `Built-Undeployed`: jar 생성됨. 운영 서버 미적용.
- `Deployed-Unverified`: 운영 서버에 적용됨. 재발 확인 전.
- `Verified`: 동일 signature가 관찰 기간 동안 재발하지 않음.
- `Rejected`: hypothesis가 틀렸거나 위험 대비 가치가 낮음.

### Report Language

본문은 한국어로 쓰되, 검색 가능한 코드 심볼, 로그 문구, thread name, exception text는 원문을 유지한다.

## Production Safety Rules

### Hard Prohibitions

production server가 실행 중이면 아래 작업은 금지한다.

- 인위적인 watchdog, lag, region stall, redstone stall 재현.
- production world에서 stress command 실행.
- invariant 위반 시 `throw`, `System.exit`, `server halt`로 실패시키는 패치.
- 무거운 Gradle build 또는 paperclip jar 생성.
- `swapoff`, `kill -9`, `git reset --hard`, world data rewrite.
- server process를 중지하거나 재시작하는 명령. 사용자가 명시적으로 요청한 경우만 예외.

### Allowed Production Inspection

production server가 실행 중이어도 일반적으로 허용된다.

```bash
tail -n 200 logs/latest.log
rg -n "Watchdog|Long-running|Region locks held|stopped responding|ERROR" logs/latest.log
zgrep -n "Long-running independent region ticks" logs/*.log.gz
ps -eo pid,ppid,stat,pcpu,pmem,etime,cmd
free -h
vmstat 1 3
awk '/VmRSS|VmSwap|VmSize|Threads/ {print}' /proc/<server-pid>/status
```

`jcmd <pid> Thread.print`는 서버가 이미 watchdog/stall 상태이거나 사용자가 장애 진단을 요청한 경우에만 사용한다. 정상 운영 중 상시 실행하지 않는다.

### Build Gate

production server가 실행 중이면 Gradle build는 기본적으로 금지한다. 예외 조건은 모두 만족해야 한다.

- user가 명시적으로 "운영 중 빌드해도 된다"고 승인.
- `free -h`의 `available`이 6GiB 이상.
- `vmstat 1 3`에서 `si`와 `so`가 지속적으로 0 또는 매우 낮음.
- `ps`에서 Gradle daemon/worker가 남아 있지 않음.
- `nice -n 15 ionice -c2 -n7 ./gradlew --no-daemon --max-workers=1 ...` 형태만 사용.

조건이 깨지면 build를 중단하고 "Patched-Unbuilt" 상태로 남긴다.

## Core Invariants

### INV-LOCK-001: Region worker lock lifetime

리전 worker는 하나의 tick/task 경계 밖으로 region lock을 가져가면 안 된다.

위반 signature:

- `Current Thread: AstatineRegionNormal-*` 또는 `AstatineRegionDegraded-*`
- stack이 `DelayQueue.take`, `DelayQueue.poll`, `RegionTickScheduler.takeNormalOrSteal`, `takeDegradedOrStealNormal`
- 동시에 `Region locks held (regionSize=...)`가 출력됨

해석:

- worker가 실제 tick을 하고 있지 않는데 lock owner로 남아 있다.
- 다음 task scheduling, main thread internal task, chunk ownership operation이 해당 lock 때문에 막힐 수 있다.

### INV-LOCK-002: Region map lock scope

`LevelChunkRegionMap.regionsLock.read/write` 내부에서는 장시간 실행 가능 코드가 호출되면 안 된다.

금지 대상:

- plugin event dispatch
- blocking wait
- file/network I/O
- recursive owner handoff
- unbounded iteration
- arbitrary runnable execution

### INV-LOCK-003: Owner handoff fallback safety

`ShreddedPaper.runSync`, `ShreddedPaperRegionScheduler`, entity scheduler fallback은 owner thread가 아닌 곳에서 world/entity/player state를 mutate하면 안 된다.

fallback runnable이 필요한 경우:

- current live owner를 다시 찾아 재-handoff한다.
- owner가 retired라면 terminal cleanup만 수행한다.
- direct inline mutation은 금지한다.

### INV-LOCK-004: Cross-region deterministic locking

multi-region, multi-world 작업은 항상 deterministic order로 lock을 획득해야 한다.

감사 기준:

- region key 정렬 여부.
- world UUID 정렬 여부.
- lock1 획득 후 lock2 실패 시 lock1 release 여부.
- `onUnlock` retry가 lock을 들고 callback을 실행하지 않는지.

### INV-LOCK-005: Plugin compatibility is not ownership

`SynchronousPluginExecution`은 plugin callback serialization만 제공한다. region owner handoff를 의미하지 않는다.

plugin callback 내부에서 world/entity/player state를 mutate하려면 별도 owner validation 또는 handoff가 필요하다.

## Current Incident Record

### INC-2026-05-03-0005

Status: `Suspected`

Observed:

- watchdog shutdown occurred on `2026-05-03 00:06:28 KST`.
- server version in log: `Astatine 1.21.11-DEV-430713a`.
- long-running marker:

```text
Long-running independent region ticks:
world region=RegionPos[79, 6] elapsedMs=73352 thread=AstatineRegionNormal-6
```

- server thread stack:

```text
java.util.concurrent.locks.StampedLock.readLock
io.multipaper.shreddedpaper.util.SimpleStampedLock.read
io.multipaper.shreddedpaper.region.LevelChunkRegionMap.applyRegionForCell
io.multipaper.shreddedpaper.region.LevelChunkRegionMap.scheduleTaskNonDropping
io.multipaper.shreddedpaper.ShreddedPaper.runSync
net.minecraft.server.MinecraftServer.tickChildren
```

- region worker dump later showed worker queue wait plus held locks:

```text
Current Thread: AstatineRegionNormal-6
State: WAITING
Region locks held: [world=[[13,1] ...]]
Stack:
java.util.concurrent.DelayQueue.take
io.multipaper.shreddedpaper.threading.region.RegionTickScheduler.takeNormalOrSteal
```

Hypothesis:

- A region worker completed or abandoned tick/task execution while still owning region locks.
- watchdog long-running accounting reported the worker as active after the useful work had stopped, or a lock leaked before worker returned to scheduler wait.
- server thread later blocked while scheduling owner handoff work because region map or region lock state was inconsistent.

Current workspace patch:

- `ShreddedPaperRegionLocker.releaseCurrentThreadLocks()` tracks and releases locks owned by current thread.
- `RegionTickScheduler.runOneTickAndReleaseLeaks()` calls cleanup at worker tick boundary and logs leaked lock release.

Current patch status: `Patched-Unbuilt`

Required next action:

- Review whether cleanup is too broad for production.
- Ensure cleanup only runs at safe worker boundary.
- Prefer error log + cleanup at boundary, not throw.
- Build only when production memory gate is satisfied.

Rollback criteria:

- New logs show repeated `Released ... leaked region lock(s)` every tick.
- Entities/chunks show corruption or mass desync after deployment.
- Region lock cleanup releases locks while task is still actively mutating state.

## Audit Queue

### LCK-001: RegionLocker acquire/release lifecycle

Status: `Patch Candidate`

Files/Symbols:

- `io.multipaper.shreddedpaper.threading.ShreddedPaperRegionLocker`
- `ReadOnlyRegionLock.tryLockRegion`
- `ReadOnlyRegionLock.unlock`
- `WriteRegionLock.unlock`
- `LockedRegion.complete`
- `onUnlock`

Observed:

- watchdog dump can show `Region locks held` for a worker that is waiting in scheduler queue.

Hypothesis:

- lock object release and thread-local state can diverge from `lockedRegions`.
- partial acquisition failure, double unlock, write promotion, or callback retry can leave stale entries.

Static Checks:

```bash
rg -n "tryLockRegion|unlock\\(|lockedRegions|localLocks|readOnlyLocks|writeLocks|onUnlock" \
  shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperRegionLocker.java
```

Patch Rules:

- Track active lock objects per owner thread.
- Make unlock idempotent.
- Never complete `onUnlock` callback while still holding internal maps in a mutating lambda.
- Cleanup may run only at known safe boundaries.
- Cleanup must log enough context to identify leak source.

Forbidden Changes:

- Do not remove region locking.
- Do not allow two threads to own the same `RegionPos`.
- Do not turn failed lock acquisition into blocking wait on region workers unless bounded.

Verification Without Prod Repro:

- Compile.
- Static grep for all lock acquisition paths and verify `finally unlock`.
- Search logs after deploy for `Released .* leaked region lock`.

Next Action:

- Review current `releaseCurrentThreadLocks` patch for correctness and log rate limiting.

### LCK-002: RegionTickScheduler worker lifecycle

Status: `Patch Candidate`

Files/Symbols:

- `io.multipaper.shreddedpaper.threading.region.RegionTickScheduler`
- `workerLoop`
- `RegionHandle.runOneTick`
- `longRunningTick`
- `runningTickStartNanos`
- `runningThread`
- `retire`
- `requeueAfterOwnerLayoutChange`

Observed:

- `Long-running independent region ticks` can point to a worker that later appears in `DelayQueue.take`.
- `runningTickStartNanos` may not represent "currently executing mutable region work" in all paths if state cleanup races with watchdog sampling.

Hypothesis:

- active tick accounting and lock lifecycle are not strongly coupled.
- early returns, lock contention, layout-change requeue, exception handling, or retire paths may leave stale active state or locks.

Static Checks:

```bash
rg -n "runningTickStartNanos|runningThread|ticking|runOneTick|retire|requeueAfterOwnerLayoutChange|ownerLock" \
  shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionTickScheduler.java
```

Patch Rules:

- `runningTickStartNanos`, `runningThread`, and region locks must be cleared in the same boundary.
- `longRunningTick` should not report queue-waiting workers.
- cleanup should run even when `runOneTick` returns early.
- long-running diagnostics should include last phase: `layout-probe`, `lock-acquire`, `tick-region`, `record`, `requeue`, `retire`, `queue-wait`.

Forbidden Changes:

- Do not silence watchdog globally.
- Do not increase watchdog timeout as primary fix.
- Do not catch and discard exceptions without preserving crash evidence.

Verification Without Prod Repro:

- Compile.
- Static check all `return` paths inside `runOneTick`.
- Confirm no worker path can reach queue wait with `ticking=true`.

Next Action:

- Add phase tracking before any further behavioral change.

### LCK-003: LevelChunkRegionMap regionsLock scope

Status: `Unreviewed`

Files/Symbols:

- `LevelChunkRegionMap.applyRegionForCell`
- `acceptRegionForCell`
- `acceptRegionsForCells`
- `scheduleTask`
- `scheduleTaskNonDropping`
- `mergeOwnersQuiescent`
- `splitDisconnectedOwnerQuiescent`
- `pollMainThreadInternalTask`

Observed:

- server thread waited in `LevelChunkRegionMap.applyRegionForCell -> scheduleTaskNonDropping`.

Hypothesis:

- `regionsLock.read/write` may be held while a callback performs scheduler arming, task enqueue, region creation, merge/split, or lock acquisition.
- If a writer is pending, `StampedLock` can block new readers and amplify stalls.

Static Checks:

```bash
rg -n "regionsLock\\.(read|write)|applyRegionForCell|scheduleTaskNonDropping|mergeOwnersQuiescent|splitDisconnectedOwnerQuiescent|pollMainThreadInternalTask" \
  shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/LevelChunkRegionMap.java
```

Patch Rules:

- Keep `regionsLock` critical sections data-only.
- Do not execute arbitrary `Runnable` while `regionsLock` is held.
- If region must be created, create under write lock, then schedule outside lock.
- Avoid acquiring `ShreddedPaperRegionLocker` lock while holding `regionsLock.write`, unless proven non-blocking and bounded.

Forbidden Changes:

- Do not replace `StampedLock` with unsynchronized maps.
- Do not create regions without invalidating `regionsSnapshot`.

Verification Without Prod Repro:

- Review every lambda passed to `regionsLock.read/write`.
- Confirm no lambda calls plugin/event code.
- Confirm no lambda can block on region lock.

Next Action:

- Split scheduling methods into lookup/create phase and enqueue/arm phase if current code schedules inside `regionsLock`.

### LCK-004: ShreddedPaperRegionScheduler retry/onUnlock paths

Status: `Unreviewed`

Files/Symbols:

- `ShreddedPaperRegionScheduler.run`
- `runOnMany`
- `runAcrossLevels`
- `onUnlock`
- `submit`

Observed:

- many owner handoff paths rely on retry after unlock.

Hypothesis:

- retry callback can be registered while holding a partial lock.
- cross-level lock failure can leave first-level lock held until callback scheduling completes.
- callback future can complete exceptionally without cleanup.

Static Checks:

```bash
rg -n "runOnMany|runAcrossLevels|onUnlock|internalTryTakeExactLockNow|tryTakeLockNow|unlock\\(" \
  shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperRegionScheduler.java
```

Patch Rules:

- Always release partial locks before registering retry callback if callback path can inspect locks.
- Retry callback must not run inline while lock is still held.
- Cross-world locks must be acquired in deterministic world UUID order.
- Future completion should happen after unlock.

Forbidden Changes:

- Do not inline run cross-region mutation after failed owner handoff.
- Do not spin-wait from region worker.

Verification Without Prod Repro:

- Static path audit for every `return` between lock acquisition and unlock.
- Compile after any patch.

Next Action:

- Confirm `onUnlock` implementation never invokes retry supplier synchronously from inside unlock while lock maps are inconsistent.

### LCK-005: ShreddedPaper.runSync owner handoff wrappers

Status: `Unreviewed`

Files/Symbols:

- `ShreddedPaper.runSync(Location, Runnable)`
- `runSync(Entity, Runnable, Runnable)`
- `runSync(ServerLevel, ChunkPos, Runnable)`
- `runSync(ServerLevel, BoundingBox, Runnable)`
- `ensureSync`

Observed:

- server thread stack in incident included `ShreddedPaper.runSync`.

Hypothesis:

- wrapper names imply safety, but some overloads only schedule on a single chunk/entity and do not cover full mutation volume.
- retired callbacks can run unsafe fallback.

Static Checks:

```bash
rg -n "runSync\\(|ensureSync\\(" shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper shreddedpaper-server/minecraft-patches
```

Patch Rules:

- Each overload must define ownership scope: single entity, single chunk, bounding box, cross-level pair.
- Fallback must be terminal-safe or re-handoff-safe.
- `ensureSync` must not treat plugin sync lock as region ownership.

Forbidden Changes:

- Do not broaden all calls to global server thread.
- Do not silently drop critical cleanup without logging.

Verification Without Prod Repro:

- Generate call-site list.
- Classify each call site by ownership scope.

Next Action:

- Build a call-site table and mark high-risk overload misuse.

### LCK-006: Player lifecycle, packet, disconnect, respawn, portal

Status: `Suspected`

Files/Symbols:

- `PacketUtils.ensureRunningOnSameThread`
- `ServerGamePacketListenerImpl`
- async respawn completion
- disconnect cleanup
- portal teleport completion
- `ServerPlayer`

Observed:

- previous incidents included portal async ownership errors and login/disconnect anomalies.
- shutdown log showed `AsyncPlayerPreLoginEvent may only be triggered asynchronously` during server teardown.

Hypothesis:

- queued packet or lifecycle cleanup can target stale `ServerPlayer`.
- fallback cleanup can run off-owner.
- shutdown/restart phase can execute login events under unexpected thread assumptions.

Static Checks:

```bash
rg -n "ensureRunningOnSameThread|respawnAsync|onDisconnect|disconnect|portal|teleport|AsyncPlayerPreLoginEvent|runSync" \
  shreddedpaper-server/src/minecraft/java/net/minecraft/server/network \
  shreddedpaper-server/src/minecraft/java/net/minecraft/network/protocol \
  shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity
```

Patch Rules:

- Capture stable player at schedule time.
- Revalidate `connection.player == scheduledPlayer` before mutation.
- If stale, re-handoff to current player owner or drop non-critical packet.
- Portal completion must not sync-load chunks from independent region worker.

Forbidden Changes:

- Do not make login/prelogin synchronous to avoid one exception.
- Do not block login authenticator threads on region locks.

Verification Without Prod Repro:

- Static check all `whenComplete` and fallback lambdas.
- Confirm no fallback mutates player state inline from arbitrary thread.

Next Action:

- Re-open prior async audit findings and mark which are patched, unpatched, or superseded.

### LCK-007: SynchronousPluginExecution and unsupported plugins

Status: `Unreviewed`

Files/Symbols:

- `SynchronousPluginExecution`
- `PaperEventManager.callEvent`
- unsupported plugin sync path
- PlayerJoin/Quit/Login events

Observed:

- watchdog dumps have shown plugin stacks such as DiscordSRV during player quit.
- logs include unsupported plugin compatibility warnings.

Hypothesis:

- plugin serialization can hold region context while running slow plugin logic.
- plugin callback may perform owner handoff or scheduler calls recursively.

Static Checks:

```bash
rg -n "SynchronousPluginExecution|callEvent|run-unsupported-plugins-in-sync|allow-unsupported-plugins" \
  shreddedpaper-server/src/main/java shreddedpaper-server/src/minecraft/java
```

Patch Rules:

- Plugin sync path must not imply region ownership.
- Slow plugin callback must not block region map locks.
- Diagnostics should identify plugin name and event name when plugin callback runs under region worker.

Forbidden Changes:

- Do not disable plugins from server code.
- Do not skip Bukkit events silently.

Verification Without Prod Repro:

- Static call chain audit from region tick to event dispatch.
- Log scan for plugin stack in watchdog dumps.

Next Action:

- Add passive timing diagnostics around unsupported plugin synchronous execution if not already present.

### LCK-008: Block update owner handoff: redstone, fluid, rail, piston, navigation

Status: `Suspected`

Files/Symbols:

- `RedStoneWireBlock`
- `alternate.current.wire.WireHandler`
- `FlowingFluid`
- `BaseRailBlock`
- `PistonBaseBlock`
- `DiodeBlock`
- `ServerLevel.sendBlockUpdated`
- `collectRelevantNavigatingMobs`

Observed:

- prior watchdog showed Alternate Current redstone path in `world_nether`.
- async audit found foreign mob navigation recompute risk.

Hypothesis:

- block update systems can cross region boundaries and trigger owner handoff while locks are held.
- navigation recompute may mutate foreign mob state after read-only collection.

Static Checks:

```bash
rg -n "WireHandler|RedStoneWireBlock|FlowingFluid|BaseRailBlock|PistonBaseBlock|DiodeBlock|collectRelevantNavigatingMobs|recomputePath|runSync" \
  shreddedpaper-server/src/main/java shreddedpaper-server/src/minecraft/java shreddedpaper-server/minecraft-patches
```

Patch Rules:

- Cross-boundary block update must own full affected volume or defer per owner.
- Foreign entity navigation mutation must be mailed to mob owner.
- Redstone/pathfinding must have bounded work or defer strategy; do not watchdog-kill server for one network.

Forbidden Changes:

- Do not globally disable redstone/pathfinding as code fix.
- Do not introduce synchronous chunk load in independent region worker.

Verification Without Prod Repro:

- Static volume ownership audit.
- Compile.
- Deploy only after controlled restart.

Next Action:

- Separate redstone long-work issue from lock leak issue; do not merge them under one "lock fix".

## Patch Loop Protocol

### Step 0: Snapshot

Before any code patch:

```bash
git status --short
tail -n 120 /home/ubuntu/astatine/2b2t2/logs/latest.log
rg -n "Watchdog|Long-running|Region locks held|ERROR|stopped responding" /home/ubuntu/astatine/2b2t2/logs/latest.log | tail -120
```

Record relevant evidence in this document under the target audit ID.

### Step 1: Static Audit

Use `rg`, `sed`, `nl`, and direct file reads. Do not guess. For each target ID, list:

- all lock acquire points,
- all unlock points,
- all early returns,
- all exception paths,
- all callback/future paths,
- all owner handoff fallbacks.

### Step 2: Patch

Patch must be minimal and scoped to one audit ID.

Required commit message shape when commit is requested:

```text
Fix <audit-id> <short problem>

- <mechanical behavior change>
- <diagnostic/safety behavior>
- <verification performed>
```

Do not commit unless user asks.

### Step 3: Validate

Allowed validation priority:

1. `git diff --check`
2. targeted compile if production memory gate allows it
3. local smoke only if not production world and not production server
4. production log scan after deployment

Do not claim `Verified` without deployment and observation.

### Step 4: Document Update

After every patch, update:

- target audit ID `Status`
- `Evidence`
- `Patch Summary`
- `Verification`
- `Next Action`
- `Rollback Criteria` if changed

## Deployment and Rollback Protocol

### Deployment Preconditions

- Build completed without compile errors.
- Production server restart is explicitly allowed by user or server is already down.
- Current jar path and new jar path are recorded.
- Previous jar backup exists.

### Deployment Report Required Fields

- source commit or `git diff` state,
- built jar path,
- deployed jar path,
- server version line after boot,
- whether `Released ... leaked region lock(s)` appears,
- whether watchdog signatures reappear.

### Rollback Triggers

Rollback if any of these occur after deployment:

- repeated leaked lock cleanup logs under normal play,
- watchdog still reports queue-waiting worker with held locks,
- mass player timeout immediately after boot,
- chunk/entity corruption logs increase sharply,
- crash report indicates patch-introduced exception.

## Log Signature Index

Use these patterns to classify future incidents.

```text
The server has not responded
The server has stopped responding
Long-running independent region ticks:
Current Thread: AstatineRegion
Region locks held (regionSize=
DelayQueue.take
DelayQueue.poll
LevelChunkRegionMap.scheduleTaskNonDropping
ShreddedPaper.runSync
StampedLock.readLock
StampedLock.writeLock
Synchronous chunk load is not allowed
AsyncPlayerPreLoginEvent may only be triggered asynchronously
Released .* leaked region lock
```

Classification rules:

- `DelayQueue.*` plus `Region locks held`: lock leak or stale lock ownership.
- active redstone stack plus long-running region: bounded-work/redstone issue, not necessarily lock leak.
- server thread on `StampedLock` with no active owner dump: inspect writer/pending writer and region map critical section.
- plugin stack inside region worker: plugin compatibility path may be holding region execution hostage.
- sync chunk load exception: owner handoff or async chunk-load policy issue.

## Current Workspace State

As of this document creation:

- `start.sh` heap default is expected to remain `18G`.
- production build was intentionally stopped because Gradle increased memory pressure.
- current lock cleanup source patch is present but unbuilt.
- existing async audit remains in `ASYNC_ISOLATION_CRASH_RISK_AUDIT_2026-05-01.md`.
- do not overwrite unrelated dirty workspace changes.

## Agent Handoff Prompt

Use this prompt for the next AI Agent working on this issue:

```text
You are working on ShreddedPaper region lock/ownership stability.
Read REGION_LOCK_OWNERSHIP_STABILITY_AUDIT.md first.
Choose exactly one audit ID.
Do not run production reproduction tests.
Do not build while production server is online unless the Build Gate passes.
Before editing, collect static evidence for the chosen ID.
After editing, update the audit document status and evidence.
Never claim the issue is fixed unless the patch is built, deployed, and observed without the matching signature.
```
