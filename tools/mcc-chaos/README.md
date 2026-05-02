# MCC Chaos Pipeline

This pipeline runs a local offline-mode ShreddedPaper server, connects multiple
Minecraft Console Client bots, drives player-like actions through MCC's
WebSocket bot, and turns failures into a compact report for Codex.

## Quick Run

```powershell
.\tools\mcc-chaos\Invoke-MccChaosLoop.ps1 -BotCount 4 -DurationSec 300 -AgentMode ReportOnly
```

macOS / Linux:

```bash
tools/mcc-chaos/Invoke-MccChaosLoop.sh --bot-count 4 --duration-sec 300 --agent-mode ReportOnly
```

Useful modes:

- `ReportOnly`: build, run, and write `failure-report.md` when a crash/signature is found.
- `Prompt`: same as `ReportOnly`, plus writes `agent-prompt.md` for manual use.
- `AutoFix`: after a failing cycle, invokes `codex exec`, rebuilds, and retests until `-MaxCycles` is exhausted.

Example automatic loop:

```powershell
.\tools\mcc-chaos\Invoke-MccChaosLoop.ps1 -BotCount 8 -DurationSec 600 -MaxCycles 5 -AgentMode AutoFix
```

macOS / Linux:

```bash
tools/mcc-chaos/Invoke-MccChaosLoop.sh --bot-count 8 --duration-sec 600 --max-cycles 5 --agent-mode AutoFix
```

## What It Does

Each cycle:

1. Builds `:shreddedpaper-server:createMojmapPaperclipJar` and the region load test plugin.
2. Downloads MCC's latest Windows release into `run/mcc-chaos/cache/mcc` if needed.
3. Starts a fresh offline-mode flat-world server with RCON enabled.
4. Starts `mccbot01`, `mccbot02`, etc. using offline login.
5. Loads MCC's external `WebSocketBot.cs` per bot on unique local ports.
6. Runs concurrent player actions: movement, boundary movement, look, sneak/sprint, block place/dig, inventory, item use, entity interaction.
7. Adds targeted hooks for teleport, fluid/leaf/redstone boundary updates, optional natural spawning, chunk generation, sync-load probes, scheduler/cross-region queues, and stale-holder style broadcast pressure.
8. Scans server logs and crash reports for async/region failure signatures.
9. Writes compact failure classification with commit, command seed, matched signature, bot positions, controller action counts, and crash snippets.

Run artifacts are under `run/mcc-chaos/results/cycle-XX`.

## Failure Buckets

`failure-report.md` classifies failures into these async bug-hunting buckets when a signature matches:

- `scheduled tick/neighbor/fluid-leaf-redstone`
- `entity movement/teleport/player tick`
- `natural spawning/entity add/structure sync load`
- `player chunk send/post-processing/stale holder broadcast`

The report also writes `failure-classification.json` beside the markdown for scripts or later triage.

## Notes

- The server is forced to `online-mode=false`, `gamemode=creative`, and `level-type=minecraft:flat`.
- On macOS/Linux the runner downloads the matching MCC release asset for the host RID (`osx-arm64`, `osx-x64`, `linux-arm64`, or `linux-x64`) unless `--mcc-path` is provided.
- `-FailOnMovementWarnings` also treats `moved too quickly` / `moved wrongly` as failures.
- Natural mob spawning is disabled by default so MCC movement gets a stable arena; add `-EnableNaturalSpawns` to enable the spawn gamerule and place a grass spawn pad near an MCC bot.
- Chunk generation and sync-load hooks require the region load test plugin jar; teleport and fluid/leaf boundary hooks run through vanilla RCON commands.
- Use `-MccPath C:\path\to\MinecraftClient.exe` to avoid downloading MCC.
- Use `-SkipBuild` only when the paperclip jar already exists.
- `AutoFix` intentionally uses Codex's non-interactive CLI. Keep it on a disposable branch/worktree when possible.
