# Item Duplication Finding 2 - Cross-Region Container Race

Date: 2026-05-17
Scope: ShreddedPaper / live Astatine 2b2t2 runtime
Exclusions: piston duplication, tripwire duplication, unsafe end portal teleportation, async playerdata save/load race

## Finding

A non-admin player can create an item duplication race by opening a container at a ShreddedPaper region boundary, moving into the adjacent region while the GUI remains valid, and clicking/shift-clicking the same container while a hopper or another player mutates it from the original region.

This is not a configured vanilla-anarchy duplication option. It is a concurrency bug in the player packet / container ownership path: container clicks are scheduled only to the player's current region thread, while the opened container's block entity remains owned and ticked by its own region. The container validity check only verifies distance and block-entity identity, not region-thread ownership.

## Current Live Preconditions

The live runtime has independent region ticking enabled:

- `/home/ubuntu/astatine/2b2t2/shreddedpaper.yml:62` - `independent-region-ticking: true`
- `/home/ubuntu/astatine/2b2t2/shreddedpaper.yml:68` - `region-size: 8`
- `/home/ubuntu/astatine/2b2t2/shreddedpaper.yml:71` - `thread-count: 7`

The live runtime also has normal hopper movement enabled:

- `/home/ubuntu/astatine/2b2t2/spigot.yml:141` - `hopper-transfer: 8`
- `/home/ubuntu/astatine/2b2t2/spigot.yml:142` - `hopper-check: 4`
- `/home/ubuntu/astatine/2b2t2/spigot.yml:143` - `hopper-amount: 1`

With region size 8 chunks, a region boundary occurs every 128 blocks on X/Z. A chest placed at or next to that boundary can remain within the vanilla container interaction range after the player steps into the adjacent region.

## Code Evidence

### 1. Player packets are forced onto the player's region, not the target inventory region

`PacketUtils.ensureRunningOnSameThread(...)` ignores the `ServerLevel` packet context for game packets and reroutes by `gamePacketListener.player`:

- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/network/protocol/PacketUtils.java:21`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/network/protocol/PacketUtils.java:27`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/network/protocol/PacketUtils.java:29`

### 2. Container clicks use that player-thread scheduling and then mutate the menu directly

`handleContainerClick` schedules on `this.player.level()` / player thread, checks only `containerMenu.stillValid(this.player)`, then calls `containerMenu.clicked(...)` directly:

- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:3341`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:3344`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java:3644`

There is no handoff to the owning region of the `Slot.container`.

### 3. The menu validity check does not enforce region ownership

Chest menus delegate validity to the underlying container:

- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/inventory/ChestMenu.java:121`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/inventory/ChestMenu.java:123`

Block-entity containers only check the same block entity and distance:

- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/Container.java:92`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/Container.java:95`

Distance is based on the player's eye position and interaction range:

- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/player/Player.java:2256`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/player/Player.java:2258`

So a player can keep the menu open from the neighboring region as long as the block is still nearby.

### 4. Slot/container item mutations are unsynchronized

Container slots directly call the underlying container's `getItem`, `removeItem`, and `setItem`:

- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/inventory/Slot.java:48`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/inventory/Slot.java:64`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/inventory/Slot.java:85`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/inventory/Slot.java:97`

Base container block entities mutate their `NonNullList<ItemStack>` directly:

- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/BaseContainerBlockEntity.java:120`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/BaseContainerBlockEntity.java:125`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/BaseContainerBlockEntity.java:126`

No lock, owner-thread check, or cross-region `ensureSync` appears on this mutation path.

### 5. Hoppers concurrently mutate the same source container on the container region thread

Hopper pull reads a source container slot, temporarily changes the same `ItemStack` count, inserts into the hopper, then writes the source slot back:

- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java:270`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java:272`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java:275`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java:288`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java:297`
- `/home/ubuntu/works/ShreddedPaper/shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java:298`

This is safe only if the source container is not also being mutated by another region thread.

## Player Action

One-player timing path:

1. Build a chest or shulker at a region boundary, for example near an X/Z coordinate divisible by 128.
2. Put a hopper under or next to it so the hopper can pull from the container.
3. Put the target item stack in the container.
4. Open the container from the chest's side of the boundary.
5. Keep the GUI open and step into the adjacent region while still within interaction range.
6. Repeatedly click or shift-click the same slot while the hopper transfer ticks.

Two-player timing path:

1. Two non-admin players stand on opposite sides of a region boundary, both within range of the same boundary container.
2. Both keep the same container open.
3. They click or shift-click the same slot at the same time.

In both cases, the server can process two conflicting mutations of the same container slot on two different Astatine region threads. The duplicated state is durable when one copy is accepted into the player inventory/cursor and the other is accepted into the hopper or the second player's inventory.

## Why This Is Not One Of The Excluded Dupes

- It does not use `allow-piston-duplication`.
- It does not use tripwire hook placement or tripwire update behavior.
- It does not use unsafe end portal teleportation.
- It does not depend on the previously reported async `player.dat` save/load race.
- It depends on ShreddedPaper's independent-region packet scheduling and stale container-owner assumptions.

## Mitigation Direction

Without source code changes, the practical containment is to disable independent region ticking or prevent players from keeping external block-entity containers open across region boundaries. The code-level fix would be to bind each open menu to its owning region(s) and process container click/drag/close packets on that owner, or close the menu when the player leaves the owner region while still within distance.
