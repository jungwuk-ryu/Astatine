# Player Resource Reload Race on ShreddedPaper

Date: 2026-06-11 KST

Status: investigation document, no runtime patch applied by this document

Affected runtime: ShreddedPaper 1.21.11 live Eartopia runtime

Primary symptom class: long-lived player sessions can enter a broken client-visible state after runtime recipe/resource reloads. Reconnecting clears the state.

## Executive Summary

The live Eartopia server exposed a likely ShreddedPaper ownership bug around player resource reloads. The immediate player-visible symptoms were:

- No chunks were visible client-side.
- Chat and command input returned the client-side message equivalent to "Chat is disabled in client settings."
- Reconnecting fixed both the chunk visibility and chat/command behavior.
- An Eartopia Admin authentication code request returned `player_unavailable` during the same general incident window, because the bridge could not schedule work on the target player's entity scheduler at that moment.

The strongest server-side evidence is a `ConcurrentModificationException` in `PlayerAdvancements.flushDirty(...)` while MMOItems was reloading recipes. The stack shows one thread mutating or iterating player advancement state from the global/server-thread resource reload path while a region worker was ticking the same player and flushing the same advancement collections.

This is a ShreddedPaper-specific correctness issue because stock Paper's "main thread owns almost everything" assumption does not hold. In ShreddedPaper, player tick state is owned by a region worker or entity owner context. Global/server-thread code that iterates online players and mutates per-player advancement or recipe-book state can race with `ServerPlayer.tick()` on a region worker.

## Live Evidence

### Player-Visible Report

Operator report:

- After a long connection, no chunks were visible.
- Chat/command entry displayed the client settings chat-disabled warning.
- After reconnecting, chunks and chat returned to normal.
- Authentication code delivery had not yet been retested at the time of the report.

The reconnect recovery is important. It implies that the server process was still alive and that the client/server session state for that specific player had become inconsistent. Reconnect creates a fresh player connection/entity state and resends initial chunk/resource/session data.

### Relevant Live Log Pattern

The live log around 2026-06-11 KST contains repeated MMOItems reload/resource update activity followed by advancement-related concurrent modification failures.

Important examples:

```text
[22:52:06] [Server thread/INFO]: [MMOItems] MMOItems 6.10 reloaded.
[22:52:06] [Server thread/INFO]: [MMOItems] Successfully reloaded recipes.
[22:52:06] [AstatineRegionNormal-9/ERROR]: Entity threw exception at spawn:-1.677213668147345,67.0,5.858554940716605
java.util.ConcurrentModificationException
    at java.base/java.util.HashMap$HashIterator.nextNode(HashMap.java:1606)
    at java.base/java.util.HashMap$KeyIterator.next(HashMap.java:1629)
    at net.minecraft.server.PlayerAdvancements.flushDirty(PlayerAdvancements.java:279)
    at net.minecraft.server.level.ServerPlayer.tick(ServerPlayer.java:784)
    at net.minecraft.server.level.ServerLevel.tickNonPassenger(ServerLevel.java:1533)
    at net.minecraft.world.level.Level.guardEntityTick(Level.java:1500)
    at io.multipaper.shreddedpaper.threading.ShreddedPaperEntityTicker.tickEntity(ShreddedPaperEntityTicker.java:45)
    at io.multipaper.shreddedpaper.region.LevelChunkRegion.forEachTickingEntity(LevelChunkRegion.java:147)
    at io.multipaper.shreddedpaper.threading.ShreddedPaperChunkTicker._tickRegion(ShreddedPaperChunkTicker.java:194)
```

Another related stack from the server thread:

```text
[22:59:12] [Server thread/WARN]: [MMOItems] Task #200390 for MMOItems v6.10 generated an exception
java.util.ConcurrentModificationException
    at java.base/java.util.HashMap$HashIterator.nextNode(HashMap.java:1606)
    at java.base/java.util.HashMap$KeyIterator.next(HashMap.java:1629)
    at net.minecraft.server.PlayerAdvancements.flushDirty(PlayerAdvancements.java:279)
    at net.minecraft.server.players.PlayerList.reloadAdvancementData(PlayerList.java:1312)
    at net.minecraft.server.players.PlayerList.reloadResources(PlayerList.java:1300)
    at net.minecraft.world.item.crafting.RecipeManager.finalizeRecipeLoading(RecipeManager.java:103)
    at net.minecraft.world.item.crafting.RecipeManager.addRecipe(RecipeManager.java:94)
    at org.bukkit.craftbukkit.inventory.CraftShapedRecipe.addToCraftingManager(CraftShapedRecipe.java:55)
    at org.bukkit.craftbukkit.CraftServer.addRecipe(CraftServer.java:1503)
    at org.bukkit.Server.addRecipe(Server.java:1027)
    at org.bukkit.Bukkit.addRecipe(Bukkit.java:1119)
    at MythicLib-1.7.jar//io.lumine.mythic.lib.api.crafting.recipes.MythicRecipeBlueprint.deploy(MythicRecipeBlueprint.java:340)
    at MMOItems-6.10.jar//net.Indyuce.mmoitems.gui.edition.recipe.registry.RMGRR_Shaped.sendToMythicLib(RMGRR_Shaped.java:82)
    at MMOItems-6.10.jar//net.Indyuce.mmoitems.manager.RecipeManager.loadRecipes(RecipeManager.java:121)
```

The two stacks are complementary:

- The region-worker stack proves player advancement state is accessed during `ServerPlayer.tick()`.
- The server-thread stack proves the recipe/resource reload path also accesses the same player advancement state outside the player owner context.

### Reconnect Timeline

The live log also shows affected players disconnecting and reconnecting successfully:

```text
[23:08:04] [AstatineRegionNormal-13/INFO]: Hancho1577 lost connection: Disconnected
[23:08:04] [AstatineRegionNormal-13/INFO]: Hancho1577 left the game
[23:08:13] [User Authenticator #14/INFO]: UUID of player Hancho1577 is 01011cab-7f3f-4a94-b34f-87c73a220b8b
[23:08:14] [AstatineRegionNormal-11/INFO]: Hancho1577 joined the game
[23:08:14] [AstatineRegionNormal-11/INFO]: Hancho1577[IP hidden] logged in with entity id 25223 at ([spawn]-1.677213668147345, 67.0, 5.858554940716605)
```

The reconnect coincides with large batches of recipe cleanup messages:

```text
Tried to load unrecognized recipe: ResourceKey[minecraft:recipe / mmoitems:...] removed now.
```

This does not prove those recipe cleanup messages cause the client-visible broken state by themselves, but it confirms that recipe/resource state is actively changing around player login/reconnect and should be included in any final fix validation.

## Source-Level Call Graph

The critical path starts with a plugin adding or reloading recipes.

### Recipe Add Reloads Player Resources

File:

`shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/crafting/RecipeManager.java`

Current relevant lines:

```java
public void addRecipe(RecipeHolder<?> holder) {
    org.spigotmc.AsyncCatcher.catchOp("Recipe Add"); // Spigot
    this.recipes.addRecipe(holder);
    this.finalizeRecipeLoading();
}

public void finalizeRecipeLoading() {
    if (this.featureflagset != null) {
        this.finalizeRecipeLoading(this.featureflagset);

        net.minecraft.server.MinecraftServer.getServer().getPlayerList().reloadResources();
    }
}
```

Line evidence from the checked tree:

- `RecipeManager.addRecipe(...)`: lines 91-95
- `RecipeManager.finalizeRecipeLoading()`: lines 99-104

This path is still inherited from Paper/CraftBukkit assumptions. It treats player resource reload as a direct, immediate global operation.

### PlayerList Mutates Per-Player State Globally

File:

`shreddedpaper-server/src/minecraft/java/net/minecraft/server/players/PlayerList.java`

Current relevant lines:

```java
public void reloadResources() {
    // Paper start - API for updating recipes on clients
    this.reloadAdvancementData();
    this.reloadTagData();
    this.reloadRecipes();
}

public void reloadAdvancementData() {
    // CraftBukkit start
    for (ServerPlayer player : this.players) {
        player.getAdvancements().reload(this.server.getAdvancements());
        player.getAdvancements().flushDirty(player, false); // CraftBukkit - trigger immediate flush of advancements
    }
    // CraftBukkit end
}

public void reloadRecipes() {
    RecipeManager recipeManager = this.server.getRecipeManager();
    ClientboundUpdateRecipesPacket clientboundUpdateRecipesPacket = new ClientboundUpdateRecipesPacket(
        recipeManager.getSynchronizedItemProperties(), recipeManager.getSynchronizedStonecutterRecipes()
    );

    for (ServerPlayer serverPlayer : this.players) {
        serverPlayer.connection.send(clientboundUpdateRecipesPacket);
        serverPlayer.getRecipeBook().sendInitialRecipeBook(serverPlayer);
    }
}
```

Line evidence from the checked tree:

- `PlayerList.reloadResources()`: lines 1298-1303
- `PlayerList.reloadAdvancementData()`: lines 1304-1313
- `PlayerList.reloadRecipes()`: lines 1325-1335

The problem is not only `connection.send(...)`. `reloadAdvancementData()` mutates `PlayerAdvancements`, and `reloadRecipes()` reads/mutates the player's recipe book state.

### Region Worker Also Flushes Advancement State

File:

`shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerPlayer.java`

Current relevant lines:

```java
CriteriaTriggers.TICK.trigger(this);
...
this.updatePlayerAttributes();
this.advancements.flushDirty(this, true);
```

Line evidence from the checked tree:

- `ServerPlayer.tick()`: calls `this.advancements.flushDirty(this, true)` at line 788.

File:

`shreddedpaper-server/src/minecraft/java/net/minecraft/server/PlayerAdvancements.java`

Current relevant lines:

```java
public void flushDirty(ServerPlayer player, boolean showAdvancements) {
    if (this.isFirstPacket || !this.rootsToUpdate.isEmpty() || !this.progressChanged.isEmpty()) {
        Map<Identifier, AdvancementProgress> map = new HashMap<>();
        Set<AdvancementHolder> set = new java.util.TreeSet<>(java.util.Comparator.comparing(adv -> adv.id().toString()));
        Set<Identifier> set1 = new HashSet<>();

        for (AdvancementNode advancementNode : this.rootsToUpdate) {
            this.updateTreeVisibility(advancementNode, set, set1);
        }

        this.rootsToUpdate.clear();

        for (AdvancementHolder advancementHolder : this.progressChanged) {
            if (this.visible.contains(advancementHolder)) {
                map.put(advancementHolder.id(), this.progress.get(advancementHolder));
            }
        }

        this.progressChanged.clear();
```

Line evidence from the checked tree:

- `PlayerAdvancements.flushDirty(...)`: lines 267-286
- The live stack points at line 279, the `for (AdvancementHolder advancementHolder : this.progressChanged)` iteration.

This method is not thread-safe. The collections it reads and mutates are plain mutable collections. In stock Paper, this is usually safe because both tick and reload normally happen on the same main thread. In ShreddedPaper, player tick happens on a region worker, so a server-thread resource reload can race with player tick.

## Why This Is ShreddedPaper-Specific

Stock Paper often relies on these assumptions:

1. The server thread owns most gameplay state.
2. A synchronous Bukkit recipe reload on the main thread can safely touch all online players.
3. Immediate calls like `PlayerList.reloadResources()` are serialized with `ServerPlayer.tick()`.

ShreddedPaper changes the ownership model:

1. Player entity state is region/entity owned.
2. Region workers can tick players independently of the global/server thread.
3. Direct global iteration over `this.players` is not enough to own each player's mutable state.
4. Packet sends may be technically possible from multiple threads in some paths, but methods that compute packet content from player-owned mutable state are not automatically safe.

Therefore, code inherited from Paper that is safe only because it runs on the single main thread becomes unsafe when it touches:

- `ServerPlayer`
- `PlayerAdvancements`
- recipe book state
- connection/session state that depends on player-owned state
- advancement criteria/progress collections

## Suspected Failure Mechanism

The likely sequence is:

1. A plugin reloads or deploys custom recipes.
   - In the observed incident, MMOItems/MythicLib calls Bukkit recipe registration.
2. `CraftServer.addRecipe(...)` eventually calls `RecipeManager.addRecipe(...)`.
3. `RecipeManager.addRecipe(...)` calls `finalizeRecipeLoading()`.
4. `finalizeRecipeLoading()` calls `MinecraftServer.getServer().getPlayerList().reloadResources()`.
5. `PlayerList.reloadResources()` calls:
   - `reloadAdvancementData()`
   - `reloadTagData()`
   - `reloadRecipes()`
6. The server thread loops all players and directly mutates or reads per-player state.
7. At the same time, a region worker ticks one of those players.
8. The region worker calls `ServerPlayer.tick()`.
9. `ServerPlayer.tick()` calls `PlayerAdvancements.flushDirty(...)`.
10. Both threads touch `PlayerAdvancements.progressChanged`, `rootsToUpdate`, `visible`, or related state.
11. A `ConcurrentModificationException` is thrown.
12. Depending on where the exception lands, the player tick/resource pipeline may fail partway through.
13. The client can end up missing expected chunk/resource/session updates until reconnect.

The exact client "chat disabled in client settings" message may be a downstream symptom rather than a direct advancement failure. Possible downstream explanations include:

- the player's packet listener/session state was partially inconsistent after a region tick exception,
- resource/recipe/advancement packets were only partially sent,
- the client-side command/chat UI state was affected by connection configuration state not being resent cleanly,
- or the visible client warning was triggered by a failed chat-command path while the connection was already in a bad state.

The current evidence proves a data race and server-side exception in the same incident class. It does not yet prove the exact client packet that causes the chat-disabled warning.

## Why Eartopia Admin Authentication Saw `player_unavailable`

Eartopia Admin Bridge sends login codes by scheduling work onto the target player's entity scheduler. During the incident, the bridge returned:

```json
{
  "error": "player_unavailable",
  "message": "플레이어에게 인증코드를 보낼 수 없습니다."
}
```

That error means the bridge found, or attempted to find, the player but could not reliably run code delivery on the player scheduler. This is consistent with a player entity/session being in a bad or transient state.

A bridge hotfix was deployed separately to retry short-lived player scheduler unavailability instead of failing immediately. That mitigates the login code symptom but does not fix the ShreddedPaper resource reload race.

## Trigger Conditions

The race is most likely when all of the following are true:

- At least one player has been online long enough to accumulate advancement or recipe-book dirty state.
- A plugin calls Bukkit recipe registration or reloads recipe definitions at runtime.
- Online players are being ticked on region workers.
- The server/global thread calls `PlayerList.reloadResources()` or an equivalent path.
- Player resource reload and player tick overlap.

Observed trigger sources:

- MMOItems reload.
- MythicLib recipe deployment under MMOItems.
- Admin-driven resource pack or MMOItems management flows that eventually run `/mi reload`, `/resourcepacks update`, or recipe rebuilds.

Likely additional trigger sources:

- Any plugin using `Bukkit.addRecipe(...)` while players are online.
- Any plugin calling `PlayerList.reloadResources()` indirectly.
- `/reload` or datapack/resource reload paths if they route through the same player resource reload methods.
- Recipe iterator removal or recipe manager finalization paths that call reload hooks.

## Impact

Known impact:

- Server log noise and real entity tick exceptions.
- Potentially broken player sessions until reconnect.
- Client-visible missing chunks.
- Client-visible chat/command input failure.
- Admin authentication code delivery failures when the affected player scheduler cannot accept tasks.

Potential impact:

- Lost advancement update packets.
- Stale or missing recipe-book state.
- Stale client tag/recipe data.
- Partial packet delivery during resource reload.
- Region worker exception pressure if this occurs repeatedly.
- Intermittent plugin failures during high-frequency admin-driven item/recipe deployments.

This should be treated as a correctness bug, not only a log cleanup issue.

## Non-Goals

Do not treat this as solved by only catching `ConcurrentModificationException`. Catching the exception would hide corruption or partial state updates.

Do not fix this by wrapping `PlayerAdvancements` collections with synchronized collections without reviewing ownership. That may reduce one CME but still leave wrong-thread player state access and may introduce tick stalls.

Do not fix this by moving all recipe reloads to the server thread. The observed server-thread path is already part of the problem because it does not own player entity state.

Do not add blocking waits from the server thread to every player's region worker. That risks deadlocks, scheduler stalls, and severe TPS regressions.

## Correctness Requirements For A Fix

A correct fix should satisfy these rules:

1. Global recipe/tag/advancement data can be prepared in a global context.
2. Per-player mutable state must be touched from the player's owner context.
3. `PlayerAdvancements.reload(...)`, `PlayerAdvancements.flushDirty(...)`, and recipe-book resync must not race with `ServerPlayer.tick()`.
4. The fix must be non-blocking from the caller's point of view when called from plugin reload paths.
5. It must tolerate players disconnecting while reload work is queued.
6. It must tolerate player teleport/dimension transition while reload work is queued.
7. It must not synchronously load chunks.
8. It must not hold global player-list locks while scheduling region-owned work.
9. It must avoid unbounded fan-out under many online players.
10. It must preserve packet ordering requirements as much as possible.

## Candidate Fix Direction

The likely fix belongs in `PlayerList.reloadResources()`, `reloadAdvancementData()`, and `reloadRecipes()`.

The rough shape:

1. Build immutable global packets or global reload inputs once.
2. Snapshot the current online player list.
3. For each player, schedule a player-owned task.
4. In that player-owned task:
   - verify the player is still alive/connected,
   - reload advancement data for that player,
   - flush dirty advancement updates for that player,
   - send tag/recipe packets where safe,
   - resync that player's recipe book.
5. Do not block the recipe reload caller waiting for every player task.

Pseudo-code sketch:

```java
public void reloadResources() {
    this.reloadTagDataGloballyIfSafe();

    RecipeManager recipeManager = this.server.getRecipeManager();
    ClientboundUpdateRecipesPacket recipesPacket = new ClientboundUpdateRecipesPacket(
        recipeManager.getSynchronizedItemProperties(),
        recipeManager.getSynchronizedStonecutterRecipes()
    );
    ClientboundUpdateTagsPacket tagsPacket = new ClientboundUpdateTagsPacket(
        TagNetworkSerialization.serializeTagsToNetwork(this.registries)
    );

    List<ServerPlayer> snapshot = List.copyOf(this.players);
    for (ServerPlayer player : snapshot) {
        ShreddedPaper.runSync(player, () -> {
            if (player.isRemoved() || player.connection == null) {
                return;
            }
            player.getAdvancements().reload(this.server.getAdvancements());
            player.getAdvancements().flushDirty(player, false);
            player.connection.send(tagsPacket);
            player.connection.send(recipesPacket);
            player.getRecipeBook().sendInitialRecipeBook(player);
        });
    }
}
```

This sketch is intentionally incomplete. A real patch must check:

- whether `ClientboundUpdateTagsPacket` can be shared between players,
- whether recipe-book methods read or mutate only player-owned state,
- whether advancement reload reads only immutable/global advancement registry state,
- whether any packet send must stay ordered relative to other global resource reload packets,
- whether ShreddedPaper already has a preferred player scheduling helper for this exact case,
- how to handle failed scheduling when the player retires or disconnects.

## Alternative Fix Options

### Option A: Schedule Per-Player Reload Work On Entity Scheduler

Pros:

- Most aligned with ShreddedPaper ownership model.
- Avoids racing `ServerPlayer.tick()`.
- Makes player disconnect/retirement semantics explicit.

Cons:

- Resource reload becomes eventually consistent per player.
- Need careful packet ordering.
- High online counts require bounded scheduling/backpressure.

### Option B: Make PlayerAdvancements Internally Thread-Safe

Pros:

- Narrowly addresses observed CME.

Cons:

- Does not fix recipe book or other player state.
- Risks hiding wrong-thread access.
- Locks in player tick hot path can cause region stalls.
- Does not align with ShreddedPaper design.

This option is not recommended as the primary fix.

### Option C: Disable Runtime Recipe Reloads While Players Are Online

Pros:

- Operationally simple workaround.
- Avoids the dangerous path.

Cons:

- Not a runtime fix.
- Breaks admin workflows that deploy MMOItems/Fishing/Farming changes live.
- Still leaves the bug for other resource reload sources.

This can be an emergency mitigation, not a final solution.

### Option D: Queue A Full Player Reinitialization/Reconfigure Packet Flow

Pros:

- May address client-visible broken state more completely.

Cons:

- Much larger protocol risk.
- Needs client compatibility validation.
- Does not replace the need to fix owner-thread player state mutation.

This may be useful only after the data race is fixed and if client state still breaks.

## Reproduction Plan

A practical reproducer should avoid relying on long random play sessions.

Suggested steps:

1. Start ShreddedPaper with at least two online players in the same active world.
2. Enable a plugin that repeatedly registers/removes recipes or invokes MMOItems recipe reloads.
3. Keep one player active so `ServerPlayer.tick()` continues to update advancements.
4. Trigger recipe reloads repeatedly:
   - MMOItems reload,
   - MythicLib recipe deploy,
   - or a minimal test plugin calling `Bukkit.addRecipe(...)` in a loop with unique recipe keys.
5. Watch for:
   - `ConcurrentModificationException` in `PlayerAdvancements.flushDirty`,
   - `PlayerList.reloadAdvancementData` on the stack,
   - region worker entity tick exceptions,
   - players losing visible chunks,
   - chat/command client warnings,
   - reconnect restoring the state.

Better deterministic stress:

1. Create a test plugin that schedules global recipe additions at a fixed rate.
2. Simultaneously trigger advancement progress changes for a player from the owner context.
3. Keep assertions or log probes around:
   - `PlayerAdvancements.progressChanged`,
   - `rootsToUpdate`,
   - `visible`,
   - `ServerPlayer.tick`,
   - `PlayerList.reloadAdvancementData`.

The test plugin must not introduce its own wrong-thread player access. It should only trigger the same public API path plugins already use.

## Validation Plan For A Runtime Patch

Minimum validation:

- Build ShreddedPaper successfully.
- Run any existing focused tests for region ownership and player scheduler behavior.
- Add a dedicated regression test or integration stress test for recipe reload while players tick.
- Confirm no `ConcurrentModificationException` appears under repeated runtime recipe additions.
- Confirm live players receive updated recipe/tag/advancement data without reconnect.
- Confirm reconnect still works after repeated reloads.
- Confirm no synchronous chunk load guards trip.
- Confirm no owner-thread violation logs.
- Confirm no deadlocks or global thread waits.

Runtime validation on Eartopia should include:

- Run with existing MMOItems and MythicLib stack.
- Trigger `/mi reload all` or the exact admin deploy flow that currently reloads MMOItems.
- Verify players remain able to chat and run commands.
- Verify chunks remain visible without reconnect.
- Verify Eartopia Admin authentication code delivery succeeds for an online OP after reload.
- Verify server PID continuity only if testing a plugin workaround. For a ShreddedPaper runtime jar patch, a server restart will be required.

## Operational Workarounds Until Runtime Fix

Short-term mitigations:

- Avoid repeated MMOItems recipe reloads while players are online.
- Prefer batching item/recipe deployments instead of many small deploys.
- If a player sees invisible chunks or chat-disabled warnings, have them reconnect.
- Avoid diagnosing Eartopia Admin `player_unavailable` as only a bridge issue when the player also has broken chunks/chat.

Bridge-side mitigation already applied separately:

- EartopiaAdminBridge now retries short-lived player scheduler unavailability before failing login code delivery.
- This reduces false negative admin login failures.
- It does not repair the ShreddedPaper race.

## Open Questions

These need source review or targeted instrumentation:

1. Is `PlayerList.reloadTagData()` safe to broadcast globally, or should tag packet sends also be player-owner scheduled?
2. Does `ServerGamePacketListenerImpl` require player-owner context for all sends, or only methods that compute data from player-owned state?
3. Does `RecipeBook#sendInitialRecipeBook(...)` mutate player-owned collections?
4. Are advancement reloads reading immutable global advancement data, or can global advancement tree reload also race with player owner tasks?
5. Is there already a ShreddedPaper helper for fan-out player scheduling with bounded backpressure?
6. Can `RecipeManager.finalizeRecipeLoading()` be changed to enqueue reload work without breaking Bukkit API expectations?
7. Are the client chunk invisibility and chat-disabled warning caused directly by the player tick exception, or by a later resource/session packet ordering issue?

## Recommended Next Step

Implement a focused ShreddedPaper patch that routes per-player resource reload work through the player's owner context. Start with `PlayerList.reloadAdvancementData()` and `reloadRecipes()`, because those are directly present in the observed stacks and touch player-owned state.

Do not broaden the first patch into a full protocol/resource reload rewrite unless evidence requires it. The first acceptance target should be:

- no advancement CME,
- no region owner violations,
- no sync chunk loads,
- players remain connected and usable through MMOItems recipe reloads.

Once that passes, investigate whether the client-visible invisible-chunk/chat-disabled state still occurs. If it does, add packet-level instrumentation around resource reload, login/reconfigure state, chat session state, and chunk send state.
