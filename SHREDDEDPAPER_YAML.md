# shreddedpaper.yml

This file documents the current `ShreddedPaperConfiguration` defaults for the
1.21.11 Astatine integration branch. Settings are shown in the same shape as
the generated `shreddedpaper.yml`.

Most operators should start with defaults and tune only after checking
`/region top` and `/region inspect`.

```yaml
# Multithreading and independent region ticking.
multithreading:
  # Number of region tick threads. -1 uses available processors minus one,
  # with a minimum of one.
  thread-count: -1

  # Fixed region cell size in chunks. Must be a power of two.
  region-size: 8

  # Run plugins without folia-supported: true through the synchronous
  # compatibility path.
  run-unsupported-plugins-in-sync: true

  # Allow unsupported plugins to mutate world state from the global scheduler
  # in single-server deployments. Disable this if a plugin stack relies on
  # stricter Folia-style behavior.
  allow-unsupported-plugins-to-modify-chunks-via-global-scheduler: true

  # Run region owners on independent deadline-based tick loops instead of one
  # global world tick barrier.
  independent-region-ticking: true

  # Workers reserved for degraded regions. -1 uses max(1, tick threads / 8).
  degraded-region-threads: -1

  # Default maximum queued tasks per bounded region task class.
  region-mailbox-capacity: 4096

  # Warning reserve threshold for non-dropping critical system work.
  critical-region-mailbox-capacity: 1024

  # Per-class mailbox capacities. Values below 0 fall back to
  # region-mailbox-capacity where supported.
  player-action-region-mailbox-capacity: 2048
  owner-handoff-region-mailbox-capacity: 2048
  chunk-io-load-region-mailbox-capacity: 1024
  chunk-io-save-region-mailbox-capacity: 512
  plugin-region-mailbox-capacity: 1024
  tracker-broadcast-region-mailbox-capacity: 1024
  explosion-physics-region-mailbox-capacity: 2048

  # Ticketed async chunk load/generation request admission per region.
  chunk-io-load-max-inflight-normal-per-region: 256
  chunk-io-load-max-inflight-degraded-per-region: 32
  chunk-io-load-deferred-retry-delay-ticks: 2
  chunk-io-load-downgrade-degraded-priority: true

  # Internal chunk worker admission and backpressure per region.
  chunk-io-executor-deferred-retry-reserve: 1024
  chunk-io-executor-max-inflight-normal-per-region: 8
  chunk-io-executor-max-inflight-degraded-per-region: 2
  chunk-io-executor-max-overflow-per-region: 64
  chunk-io-executor-max-backlog-per-region: 8192
  chunk-io-executor-backpressure-retry-reserve: 4096
  chunk-io-executor-downgrade-degraded-priority: true

  # Autosave isolation. Each producer pass scans a bounded number of chunk
  # holders and admits a bounded amount of save work per region owner.
  chunk-io-save-max-auto-saves-per-region: 2
  chunk-io-save-auto-save-scan-multiplier: 4

  # Cooperative auxiliary-work budget in milliseconds. This does not interrupt
  # core entity/chunk/player/block-entity tick phases halfway through.
  region-tick-budget-ms: 45

  # Deferred TNT explosions kept as frozen live entities per world. Overflow
  # TNT is discarded without exploding to avoid hostile-load save/entity debt.
  deferred-tnt-backlog-per-world: 1024

  # EWMA MSPT thresholds for local overload classes.
  degraded-region-mspt-threshold: 75
  quarantined-region-mspt-threshold: 5000

# Region-aware low TPS/MSPT compensation.
lag-compensation:
  enabled: true
  block-entity-acceleration: true
  block-breaking-acceleration: true
  eating-acceleration: true
  potion-effect-acceleration: true
  fluid-acceleration: true
  pickup-acceleration: true
  portal-acceleration: true
  time-acceleration: true
  random-tick-speed-acceleration: true

  # Suppress movement setbacks only when the player's own region is lagging.
  ignore-moved-too-quickly-when-lagging: true
  ignore-moved-wrongly-when-lagging: true

  # Disables moved-too-quickly and moved-wrongly enforcement entirely. Keep
  # false unless you are intentionally accepting the anticheat tradeoff.
  always-allow-weird-movement: false

  max-compensated-missed-ticks: 20
  region-lag-mspt-threshold: 55.0
  region-lag-schedule-lag-threshold-ms: 100.0

# ShreddedPaper-specific optimizations.
optimizations:
  entity-activation-check-frequency: 20
  disable-vanish-api: false
  disable-locator-bar: true
  use-lazy-execute-when-not-flushing: true
  process-track-queue-in-parallel: true
  flush-queue-in-parallel: true
  maximum-trackers-per-entity: 500
  tracker-full-update-frequency: 20
  purge-stale-tickets-frequency: 20
  write-player-saves-async: true

  # Skip per-cell scheduled-tick scans when the cell has neither block nor
  # fluid tick data.
  scheduled-tick-presence-guard: true

  chunk-packet-caching:
    enabled: true
    use-soft-references: true
    expire-after: 1200

# DivineMC/C2ME/Lithium-derived performance options exposed through
# shreddedpaper.yml.
performance:
  optimizations:
    disable-method-profiler: true
    skip-useless-secondary-poi-sensor: true
    clump-orbs: true
    enable-suffocation-optimization: true
    use-compact-bit-storage: true
    command-block-parse-results-caching: true
    sheep-optimization: true
    optimized-dragon-respawn: true
    reduce-chunk-load-and-lookup: true
    create-snapshot-on-retrieving-block-state: true
    sleeping-block-entity: true
    equipment-tracking: true

    hopper-throttle-when-full:
      enabled: true
      skip-ticks: 8

    reduce-projectile-chunk-loading:
      per-tick: 10
      per-projectile-max: 10
      reset-movement-after-reach-limit: false
      remove-from-world-after-reach-limit: false

  chunks:
    chunk-data-cache-soft-limit: 8192
    chunk-data-cache-limit: 32678
    max-view-distance: 16
    player-near-chunk-detection-range: 128
    chunk-worker-algorithm: C2ME_NEW
    use-euclidean-distance-squared: true
    end-biome-cache-enabled: true
    end-biome-cache-capacity: 2048

    worldgen-computation-cache:
      instrumentation-enabled: false
      instrumentation-sample-rate: 256

    experimental:
      enable-density-function-compiler: true
      enable-structure-layout-optimizer: true
      deduplicate-shuffled-template-pool-element-list: true

  dab:
    enabled: false
    start-distance: 12
    maximum-activation-frequency: 20
    activation-distance-mod: 8
    dont-enable-if-in-water: false
    blacked-entities:
      - villager
      - axolotl
      - hoglin
      - zombified_piglin
      - goat

region-format:
  # Valid values: MCA, LINEAR, B_LINEAR.
  type: MCA
  compression-level: 1
  linear-io-thread-count: 6
  linear-io-flush-delay-ms: 100
  linear-use-virtual-threads: true

virtual-threads:
  enabled: true
  bukkit-scheduler: true
  chat-scheduler: true
  tab-complete-scheduler: true
  async-executor: true
  command-builder-scheduler: true
  server-text-filter-pool: true

async:
  pathfinding:
    enable: true
    max-threads: 1
    keepalive: 60
    queue-size: 0
    reject-policy: CALLER_RUNS

  multithreaded-tracker:
    enable: true
    compat-mode: false
    max-threads: 1
    keepalive: 60
    queue-size: 0
```

## Tuning Notes

- `thread-count` controls region worker parallelism. More threads can improve
  isolation only if the machine has CPU headroom.
- `region-tick-budget-ms` should not be used as a blunt way to skip core game
  logic. It budgets auxiliary queues; core ticking should remain coherent.
- Lower degraded chunk IO caps protect healthy regions at the cost of slower
  progress inside overloaded regions.
- Increasing mailbox capacities can hide short spikes but also increases memory
  use and the amount of delayed work that must eventually drain.
- `always-allow-weird-movement` is intentionally disabled by default. Prefer
  the region-lag movement checks unless you are debugging movement only.
- `async.pathfinding.enable` is synchronized with the DivineMC config layer, but
  async pathfinding is disabled by the DivineMC bridge while independent region
  ticking is active because ownership handoff is safer than detached path
  resumes.

## Diagnostics For These Settings

Use `/region inspect <world> <regionX> <regionZ>` to correlate configuration
changes with runtime counters:

- `mailbox` shows task depth, class pressure, deferred work, and rejected work.
- `chunk requests` shows ticketed async chunk load/generation pressure.
- `executor`, `backlog`, `overflow`, `emergency`, and `waiters` show internal
  chunk worker QoS pressure.
- `downgrade` counters indicate work lowered in priority because a region is
  degraded.

If one overloaded region causes unrelated regions to stall, compare the
overloaded region's chunk IO and mailbox counters with the unrelated region's
schedule lag. That usually separates CPU saturation from a hidden global wait.
