# Item Duplication Finding - 2026-05-17

## Scope

Excluded by request:

- `unsupported-settings.allow-piston-duplication`
- `unsupported-settings.skip-tripwire-hook-placement-validation` / tripwire dupe behavior
- `unsupported-settings.allow-unsafe-end-portal-teleportation`

Finding below is not one of those intentional compatibility/exploit toggles. It is a save/load ordering bug in the ShreddedPaper async playerdata path.

## Finding

`PlayerDataStorage.save(Player)` snapshots player NBT on the region/server thread, then writes the snapshot later on `Util.ioPool()` when `write-player-saves-async` is enabled. `PlayerDataStorage.load(NameAndId)` does not wait for any pending save for the same UUID before reading `<uuid>.dat`.

This creates a player-controlled rollback window:

1. Player moves item(s) from their inventory into persistent world state such as a chest.
2. Player disconnects. `PlayerList.remove(...)` calls `save(player)`, but the `.dat` write is queued asynchronously.
3. Player reconnects before that queued write reaches disk.
4. Login reads the old `<uuid>.dat`, restoring the pre-transfer inventory while the chest still contains the transferred item(s).
5. A later logout/save persists the restored inventory again, making the duplicate durable.

The bug is especially reachable under I/O pressure or with large player NBT, but it is not dependent on administrator privileges, commands, creative mode, piston duplication, tripwire behavior, or unsafe end portal teleportation.

## Evidence

- Save is called during disconnect after inventory/container close handling:
  - `shreddedpaper-server/src/minecraft/java/net/minecraft/server/players/PlayerList.java:444`
  - `shreddedpaper-server/src/minecraft/java/net/minecraft/server/players/PlayerList.java:472`
- The player snapshot is built synchronously, but the actual file replace is deferred to the shared IO executor:
  - `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/storage/PlayerDataStorage.java:43`
  - `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/storage/PlayerDataStorage.java:47`
  - `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/storage/PlayerDataStorage.java:72`
- The UUID lock only serializes file writes. It is not used by load and does not expose a pending-save future:
  - `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/storage/PlayerDataStorage.java:32`
  - `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/storage/PlayerDataStorage.java:50`
- Login reads playerdata directly from disk with no pending-save drain:
  - `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/storage/PlayerDataStorage.java:92`
  - `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/storage/PlayerDataStorage.java:107`
  - `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/config/PrepareSpawnTask.java:286`
  - `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/config/PrepareSpawnTask.java:301`
- The live config currently enables the vulnerable path:
  - `/home/ubuntu/astatine/2b2t2/shreddedpaper.yml:89`

## Minimal Fix Direction

Without changing gameplay behavior, make playerdata writes per UUID ordered and visible to reads:

- Track the latest pending save `CompletableFuture` per UUID.
- On `load(NameAndId)`, join or otherwise drain the pending future for that UUID before reading from disk.
- Prevent older save snapshots from overwriting newer snapshots by attaching a monotonic sequence number per UUID.
- During shutdown, wait for pending playerdata writes explicitly rather than relying only on executor shutdown.

Immediate operational mitigation without source changes:

- Set `optimizations.write-player-saves-async: false` in `shreddedpaper.yml` and restart when operationally safe.
