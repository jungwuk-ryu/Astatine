# Developing A Plugin For ShreddedPaper

ShreddedPaper follows the same plugin principle as Folia: there is no single
"main thread" that is safe for every world object. A player, entity, block, or
chunk belongs to a region owner, and code that touches it must run on that
owner's scheduler.

Traditional Bukkit plugins can still run through ShreddedPaper's synchronous
compatibility mode, but that path exists to keep common plugins alive while the
server redirects unsafe work. New or actively maintained plugins should use the
region-aware APIs directly.

## Declare Folia Compatibility

If your plugin is safe for Paper's region scheduler model, add this to
`plugin.yml` or `paper-plugin.yml`:

```yaml
folia-supported: true
```

Do not hard-code a Folia implementation class check such as:

```java
Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
```

Instead, check for the scheduler API you need:

```java
try {
    Bukkit.class.getMethod("getRegionScheduler");
    return true;
} catch (NoSuchMethodException ex) {
    return false;
}
```

## Shared Plugin State

Plugin data structures can be accessed from several region threads at once.
Plain `HashMap`, `ArrayList`, or mutable fields are not safe unless you protect
them.

Unsafe example:

```java
final HashMap<UUID, Integer> balances = new HashMap<>();

public void addMoney(UUID playerId, int amount) {
    int current = balances.getOrDefault(playerId, 0);
    balances.put(playerId, current + amount);
}
```

Safer example:

```java
final ConcurrentHashMap<UUID, Integer> balances = new ConcurrentHashMap<>();

public void addMoney(UUID playerId, int amount) {
    balances.merge(playerId, amount, Integer::sum);
}
```

For multi-step state changes, use a lock around the specific shared state or
model the work as immutable messages sent to one owner.

## Run World Access On The Owning Region

Use Paper's region scheduler for block and chunk work:

```java
public void setBlock(JavaPlugin plugin, Location location) {
    Bukkit.getRegionScheduler().run(plugin, location, task -> {
        location.getBlock().setType(Material.DIAMOND_BLOCK);
    });
}
```

Use the entity scheduler for entity work:

```java
public void updateEntity(JavaPlugin plugin, Entity entity) {
    entity.getScheduler().run(plugin, task -> {
        entity.setGlowing(true);
    }, null);
}
```

This matters even when your command handler is already running on a server
thread. The command thread may be the wrong owner for the target entity or
block.

## Avoid Global Scheduler World Mutations

`Bukkit.getGlobalRegionScheduler()` and the classic Bukkit scheduler are not a
blanket permission to mutate arbitrary world state. In ShreddedPaper, unsupported
plugins can be routed through a synchronous compatibility path, but relying on
that path can still create stalls or ownership handoffs under load.

Prefer:

- region scheduler for block/chunk locations
- entity scheduler for entities
- async work only for pure computation or external IO
- a final scheduler handoff before touching Bukkit world state

## Teleports

Prefer asynchronous teleports:

```java
entity.teleportAsync(targetLocation);
```

Avoid synchronous teleports from plugin tasks:

```java
entity.teleport(targetLocation); // Avoid this on regionized servers.
```

Synchronous plugin teleports are guarded in this branch, but they can still make
the main thread wait for chunk/region ownership. Under load that can trigger
watchdog dumps or make unrelated diagnostics look frozen.

## Chunk Loading

Do not call blocking chunk loads from arbitrary threads before mutating the
chunk. Use async chunk APIs where possible, then hand back to the region owner:

```java
world.getChunkAtAsync(location).thenAccept(chunk -> {
    Bukkit.getRegionScheduler().run(plugin, location, task -> {
        location.getBlock().setType(Material.STONE);
    });
});
```

ShreddedPaper adds region-aware chunk IO QoS. Repeated blocking loads from a
plugin can be deferred, downgraded, backpressured, or rejected depending on the
current region pressure.

## Player Disconnects And Kicks

Do not hold your own plugin lock while kicking, teleporting, saving, or moving a
player. These operations can call into network, entity, and chunk systems. If a
plugin lock is needed, collect the data first, release the lock, and then
schedule the player operation.

Good shape:

```java
String reason;
synchronized (stateLock) {
    reason = computeKickReason(player.getUniqueId());
}
player.kick(Component.text(reason));
```

Bad shape:

```java
synchronized (stateLock) {
    player.kick(Component.text("bye"));
}
```

The server has compatibility fixes for common disconnect paths, but plugins
should still avoid lock inversions.

## Events

An event being fired on a thread does not mean every object referenced by the
event is owned by that thread. If your handler touches a different entity,
far-away block, or another world, schedule to that target.

Examples:

- A player command changing a far-away block: schedule to the block location.
- A projectile hit event changing the shooter: schedule to the shooter entity.
- A portal or RTP plugin moving a player: use `teleportAsync` or schedule the
  final move through the player/entity owner.

## Testing Checklist

Before claiming ShreddedPaper compatibility:

- run with `folia-supported: true`
- test player join, quit, kick, death, respawn, portal, and teleport flows
- test your commands from two players in different regions
- test your plugin while one unrelated region is under high entity or chunk
  generation load
- check console for `Thread failed main thread check`, `wrong thread`, or
  region mailbox rejection logs
- use `/region ownership` after stress tests to check unexpected fallback and
  handoff counters

## Supporting Bukkit, Paper, Folia, And ShreddedPaper

Use reflection or an abstraction layer only at the scheduler boundary. Keep the
world mutation code itself written as "run this on the owner of the target".

If you need a compatibility library, make sure it dispatches to Paper's region
scheduler APIs on Folia/ShreddedPaper rather than only falling back to the
classic Bukkit scheduler.
