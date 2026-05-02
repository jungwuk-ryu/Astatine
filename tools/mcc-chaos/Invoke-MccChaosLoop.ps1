param(
    [string]$Root = "",
    [string]$JavaPath = "",
    [string]$MccPath = "",
    [int]$BotCount = 4,
    [int]$DurationSec = 300,
    [int]$MaxCycles = 1,
    [ValidateSet("ReportOnly", "Prompt", "AutoFix")]
    [string]$AgentMode = "ReportOnly",
    [string]$CodexCommand = "codex",
    [int]$HeapGb = 6,
    [int]$ServerPort = 25565,
    [int]$RconPort = 25575,
    [int]$WebSocketBasePort = 8043,
    [string]$RconPassword = "codex-mcc-chaos",
    [string]$WebSocketPassword = "",
    [string]$Seed = "",
    [switch]$SkipBuild,
    [switch]$SkipMccDownload,
    [switch]$NoStop,
    [switch]$EnableNaturalSpawns,
    [switch]$FailOnMovementWarnings
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ([string]::IsNullOrWhiteSpace($Root)) {
    $Root = Join-Path $RepoRoot "run\mcc-chaos"
}
if ([string]::IsNullOrWhiteSpace($JavaPath)) {
    if ($env:JAVA_HOME) {
        $JavaPath = Join-Path $env:JAVA_HOME "bin\java.exe"
    } else {
        $JavaPath = "java"
    }
}
if ([string]::IsNullOrWhiteSpace($WebSocketPassword)) {
    $WebSocketPassword = "mcc-chaos-" + ([Guid]::NewGuid().ToString("N"))
}
if ([string]::IsNullOrWhiteSpace($Seed)) {
    $Seed = Get-Date -Format "yyyyMMdd-HHmmss"
}

$RootPath = [System.IO.Path]::GetFullPath($Root)
$CacheDir = Join-Path $RootPath "cache"
$RunRoot = Join-Path $RootPath "runs"
$ResultRoot = Join-Path $RootPath "results"
$ServerJar = Join-Path $RepoRoot "shreddedpaper-server\build\libs\shreddedpaper-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar"
$PluginJar = Join-Path $RepoRoot "tools\region-load-test-plugin\build\libs\region-load-test-plugin-0.1.0-SNAPSHOT.jar"
$Controller = Join-Path $PSScriptRoot "mcc-chaos-controller.mjs"
$WebSocketBotUrl = "https://raw.githubusercontent.com/MCCTeam/Minecraft-Console-Client/master/MinecraftClient/config/ChatBots/WebSocketBot.cs"

function New-Directory([string]$Path) {
    if (!(Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Write-Utf8File([string]$Path, [string]$Content) {
    if ([string]::IsNullOrWhiteSpace($Path)) {
        $stack = (Get-PSCallStack | ForEach-Object { "$($_.Command)@$($_.ScriptLineNumber)" }) -join " <- "
        throw "Write-Utf8File received an empty path. Stack: $stack"
    }
    $parent = Split-Path -Parent $Path
    if ($parent) {
        New-Directory $parent
    }
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Assert-UnderRoot([string]$Path) {
    $full = [System.IO.Path]::GetFullPath($Path)
    if (!$full.StartsWith($RootPath, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to operate outside chaos root: $full"
    }
}

function Invoke-LoggedCommand([string]$FilePath, [string[]]$Arguments, [string]$WorkingDirectory, [string]$LogPath) {
    New-Directory (Split-Path -Parent $LogPath)
    $argText = ($Arguments | ForEach-Object { if ($_ -match "\s") { '"' + $_ + '"' } else { $_ } }) -join " "
    "COMMAND: $FilePath $argText" | Out-File -FilePath $LogPath -Encoding utf8
    $process = Start-Process -FilePath $FilePath -ArgumentList $Arguments -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput ($LogPath + ".out") -RedirectStandardError ($LogPath + ".err") `
        -NoNewWindow -Wait -PassThru
    Get-Content ($LogPath + ".out"), ($LogPath + ".err") -ErrorAction SilentlyContinue | Add-Content -Path $LogPath
    if ($process.ExitCode -ne 0) {
        throw "Command failed with exit code $($process.ExitCode): $FilePath $argText. See $LogPath"
    }
}

function New-RconPacketBytes([int]$Id, [int]$Type, [string]$Payload) {
    $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($Payload)
    $length = 10 + $payloadBytes.Length
    $bytes = New-Object byte[] (4 + $length)
    [System.BitConverter]::GetBytes([int]$length).CopyTo($bytes, 0)
    [System.BitConverter]::GetBytes([int]$Id).CopyTo($bytes, 4)
    [System.BitConverter]::GetBytes([int]$Type).CopyTo($bytes, 8)
    [Array]::Copy($payloadBytes, 0, $bytes, 12, $payloadBytes.Length)
    return $bytes
}

function Read-Exact([System.IO.Stream]$Stream, [int]$Length) {
    $buffer = New-Object byte[] $Length
    $offset = 0
    while ($offset -lt $Length) {
        $read = $Stream.Read($buffer, $offset, $Length - $offset)
        if ($read -le 0) {
            throw "RCON stream closed."
        }
        $offset += $read
    }
    return $buffer
}

function Read-RconPacket([System.IO.Stream]$Stream) {
    $lengthBytes = Read-Exact $Stream 4
    $length = [System.BitConverter]::ToInt32($lengthBytes, 0)
    $data = Read-Exact $Stream $length
    $id = [System.BitConverter]::ToInt32($data, 0)
    $type = [System.BitConverter]::ToInt32($data, 4)
    $payloadLength = [Math]::Max(0, $length - 10)
    $payload = [System.Text.Encoding]::UTF8.GetString($data, 8, $payloadLength)
    return [pscustomobject]@{ Id = $id; Type = $type; Payload = $payload }
}

function Send-RconCommand([string]$Command, [int]$TimeoutMs = 10000) {
    $client = [System.Net.Sockets.TcpClient]::new()
    $client.ReceiveTimeout = $TimeoutMs
    $client.SendTimeout = $TimeoutMs
    try {
        $client.Connect("127.0.0.1", $RconPort)
        $stream = $client.GetStream()
        $auth = New-RconPacketBytes 1 3 $RconPassword
        $stream.Write($auth, 0, $auth.Length)
        $stream.Flush()
        $authResponse = Read-RconPacket $stream
        if ($authResponse.Id -eq -1) {
            throw "RCON authentication failed on port $RconPort."
        }

        $packet = New-RconPacketBytes 2 2 $Command
        $stream.Write($packet, 0, $packet.Length)
        $stream.Flush()
        return (Read-RconPacket $stream).Payload
    } finally {
        $client.Close()
    }
}

function Test-RconReady {
    try {
        [void](Send-RconCommand "list" 2500)
        return $true
    } catch {
        return $false
    }
}

function Wait-ForServerReady([string]$ServerDir, [System.Diagnostics.Process]$Process, [int]$TimeoutSec = 240) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $log = Join-Path $ServerDir "logs\latest.log"
    while ((Get-Date) -lt $deadline) {
        if ($Process.HasExited) {
            throw "Server process exited before ready. ExitCode=$($Process.ExitCode)"
        }
        if (Test-Path -LiteralPath $log) {
            $tail = Get-Content -LiteralPath $log -Tail 80 -ErrorAction SilentlyContinue
            if (($tail -join "`n") -match "Done \(" -and (Test-RconReady)) {
                return
            }
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for server readiness."
}

function Get-FileOffset([string]$Path) {
    if (!(Test-Path -LiteralPath $Path)) {
        return 0L
    }
    return (Get-Item -LiteralPath $Path).Length
}

function Get-FileSince([string]$Path, [long]$Offset) {
    if (!(Test-Path -LiteralPath $Path)) {
        return ""
    }
    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        if ($Offset -gt $stream.Length) {
            $Offset = 0
        }
        $stream.Seek($Offset, [System.IO.SeekOrigin]::Begin) | Out-Null
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8, $true, 4096, $true)
        try {
            return $reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

function Wait-ForPlayers([string[]]$Names, [int]$TimeoutSec = 120) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $list = Send-RconCommand "list" 5000
        $missing = @($Names | Where-Object { $list -notmatch [regex]::Escape($_) })
        if ($missing.Count -eq 0) {
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "Timed out waiting for MCC players to join: $($Names -join ', ')"
}

function Invoke-RconCommands([string[]]$Commands, [string]$RconLog, [int]$TimeoutMs = 15000, [int]$PauseMs = 0) {
    foreach ($command in $Commands) {
        try {
            $response = Send-RconCommand $command $TimeoutMs
            Add-Content -Path $RconLog -Encoding utf8 -Value ("> $command`n$response`n")
        } catch {
            Add-Content -Path $RconLog -Encoding utf8 -Value ("> $command`nERROR: $($_.Exception.Message)`n")
        }
        if ($PauseMs -gt 0) {
            Start-Sleep -Milliseconds $PauseMs
        }
    }
}

function Stop-Server([System.Diagnostics.Process]$Process) {
    if ($null -eq $Process -or $Process.HasExited) {
        return
    }
    try {
        [void](Send-RconCommand "stop" 5000)
    } catch {
        Write-Warning "Could not send stop over RCON: $($_.Exception.Message)"
    }
    if (!$Process.WaitForExit(60000)) {
        Write-Warning "Server did not stop in time; killing PID $($Process.Id)."
        Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
    }
}

function Stop-Processes([System.Diagnostics.Process[]]$Processes) {
    foreach ($process in $Processes) {
        if ($null -ne $process -and !$process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
    }
}

function Get-WindowsAssetPattern {
    if ([Environment]::Is64BitOperatingSystem) {
        return "win-x64.exe"
    }
    return "win-x86.exe"
}

function Install-Mcc {
    if (![string]::IsNullOrWhiteSpace($MccPath)) {
        if (!(Test-Path -LiteralPath $MccPath)) {
            throw "MCC executable not found: $MccPath"
        }
        return (Resolve-Path -LiteralPath $MccPath).Path
    }

    $mccDir = Join-Path $CacheDir "mcc"
    New-Directory $mccDir
    $existing = Get-ChildItem -LiteralPath $mccDir -Filter "MinecraftClient*.exe" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -ne $existing) {
        return $existing.FullName
    }
    if ($SkipMccDownload) {
        throw "MCC executable is missing and -SkipMccDownload was specified."
    }

    $release = Invoke-RestMethod -Headers @{ "User-Agent" = "ShreddedPaper-MccChaos" } `
        -Uri "https://api.github.com/repos/MCCTeam/Minecraft-Console-Client/releases/latest"
    $pattern = Get-WindowsAssetPattern
    $asset = @($release.assets | Where-Object { $_.name.EndsWith($pattern) } | Select-Object -First 1)
    if ($asset.Count -eq 0) {
        throw "Could not find MCC Windows asset ending with $pattern in latest release."
    }
    $target = Join-Path $mccDir $asset[0].name
    Write-Host "Downloading MCC $($release.tag_name): $($asset[0].name)"
    Invoke-WebRequest -UseBasicParsing -Uri $asset[0].browser_download_url -OutFile $target
    return $target
}

function Get-WebSocketBotTemplate {
    $template = Join-Path $CacheDir "WebSocketBot.cs"
    if (!(Test-Path -LiteralPath $template)) {
        New-Directory $CacheDir
        Invoke-WebRequest -UseBasicParsing -Uri $WebSocketBotUrl -OutFile $template
    }
    return [System.IO.File]::ReadAllText($template)
}

function Write-ServerProperties([string]$ServerDir) {
    $spawnEnabled = if ($EnableNaturalSpawns) { "true" } else { "false" }
    $properties = @"
accepts-transfers=false
allow-flight=true
allow-nether=true
broadcast-console-to-ops=false
broadcast-rcon-to-ops=false
difficulty=normal
enable-command-block=false
enable-query=false
enable-rcon=true
enable-status=true
enforce-secure-profile=false
force-gamemode=true
function-permission-level=4
gamemode=creative
generate-structures=false
hardcore=false
level-name=world
level-seed=mcc-chaos-$Seed
level-type=minecraft:flat
max-players=64
max-tick-time=-1
motd=ShreddedPaper MCC chaos
network-compression-threshold=256
online-mode=false
op-permission-level=4
prevent-proxy-connections=false
pvp=true
query.port=$ServerPort
rcon.password=$RconPassword
rcon.port=$RconPort
server-ip=
server-port=$ServerPort
simulation-distance=8
spawn-animals=$spawnEnabled
spawn-monsters=$spawnEnabled
spawn-npcs=$spawnEnabled
spawn-protection=0
sync-chunk-writes=false
use-native-transport=true
view-distance=12
white-list=false
"@
    Write-Utf8File (Join-Path $ServerDir "server.properties") $properties
}

function Prepare-ServerDirectory([string]$CycleDir) {
    $serverDir = Join-Path $CycleDir "server"
    Assert-UnderRoot $serverDir
    if (Test-Path -LiteralPath $serverDir) {
        Remove-Item -LiteralPath $serverDir -Recurse -Force
    }
    New-Directory $serverDir
    New-Directory (Join-Path $serverDir "plugins")
    Copy-Item -LiteralPath $ServerJar -Destination (Join-Path $serverDir "server.jar") -Force
    if (Test-Path -LiteralPath $PluginJar) {
        Copy-Item -LiteralPath $PluginJar -Destination (Join-Path $serverDir "plugins\region-load-test-plugin.jar") -Force
    }
    Write-Utf8File (Join-Path $serverDir "eula.txt") "eula=true`n"
    Write-ServerProperties $serverDir
    return $serverDir
}

function Start-TestServer([string]$ServerDir) {
    $stdout = Join-Path $ServerDir "console.out.log"
    $stderr = Join-Path $ServerDir "console.err.log"
    $args = @(
        "--enable-preview",
        "-Xms$($HeapGb)G",
        "-Xmx$($HeapGb)G",
        "-Dfile.encoding=UTF-8",
        "-Duser.timezone=GMT+9",
        "-XX:+UseG1GC",
        "-XX:+ParallelRefProcEnabled",
        "-XX:MaxGCPauseMillis=200",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+DisableExplicitGC",
        "-XX:+UseStringDeduplication",
        "-jar", "server.jar", "nogui"
    )
    $process = Start-Process -FilePath $JavaPath -ArgumentList $args -WorkingDirectory $ServerDir `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    try {
        Wait-ForServerReady $ServerDir $process
        return $process
    } catch {
        if ($null -ne $process -and !$process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
        throw
    }
}

function Write-MccBotFiles([string]$CycleDir, [string]$MccExe, [string]$Template) {
    $botInfos = @()
    for ($i = 1; $i -le $BotCount; $i++) {
        $name = "mccbot" + $i.ToString("00")
        $port = $WebSocketBasePort + $i - 1
        $botDir = Join-Path $CycleDir $name
        New-Directory $botDir
        New-Directory (Join-Path $botDir "ChatBots")

        $script = $Template -replace 'MCC\.LoadBot\(new WebSocketBot\("127\.0\.0\.1",\s*8043,\s*"CHANGE_THIS_PASSWORD"(,\s*debugMode:\s*true)?\)\);',
            "MCC.LoadBot(new WebSocketBot(""127.0.0.1"", $port, ""$WebSocketPassword""));"
        Write-Utf8File (Join-Path $botDir "ChatBots\WebSocketBot.cs") $script

        $logFile = (Join-Path $botDir "mcc-console.log").Replace("\", "\\")
        $config = @"
[Main.General.Account]
Login = "$name"
Password = "-"

[Main.General.Server]
Host = "127.0.0.1"
Port = $ServerPort

[Main.General]
AccountType = "microsoft"
Method = "mcc"

[Main.Advanced]
Language = "en_us"
MinecraftVersion = "auto"
TerrainAndMovements = true
InventoryHandling = true
EntityHandling = true
AutoRespawn = true
ExitOnFailure = false
Timestamps = true
ResolveSrvRecords = "no"

[Logging]
DebugMessages = false
ChatMessages = true
InfoMessages = true
WarningMessages = true
ErrorMessages = true
LogToFile = true
LogFile = "$logFile"
PrependTimestamp = true
SaveColorCodes = false

[ChatBot.ScriptScheduler]
Enabled = true

[[ChatBot.ScriptScheduler.TaskList]]
Task_Name = "load-websocket"
Trigger_On_First_Login = true
Trigger_On_Login = true
Trigger_On_Times = { Enable = false, Times = [] }
Trigger_On_Interval = { Enable = false, MinTime = 1.0, MaxTime = 1.0 }
Action = "script ChatBots/WebSocketBot.cs"
"@
        Write-Utf8File (Join-Path $botDir "MinecraftClient.ini") $config
        $botInfos += [pscustomobject]@{ Name = $name; Port = $port; Dir = $botDir; Config = Join-Path $botDir "MinecraftClient.ini"; Exe = $MccExe }
    }
    return $botInfos
}

function Start-MccBots([object[]]$BotInfos) {
    $processes = @()
    foreach ($bot in $BotInfos) {
        $stdout = Join-Path $bot.Dir "stdout.log"
        $stderr = Join-Path $bot.Dir "stderr.log"
        $process = Start-Process -FilePath $bot.Exe -ArgumentList @($bot.Config) -WorkingDirectory $bot.Dir `
            -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
        $processes += $process
    }
    return $processes
}

function Invoke-SetupCommands([string[]]$BotNames, [string]$RconLog) {
    $mobSpawning = if ($EnableNaturalSpawns) { "true" } else { "false" }
    $commands = @(
        "gamerule doDaylightCycle false",
        "gamerule doWeatherCycle false",
        "gamerule mobGriefing false",
        "gamerule doMobSpawning $mobSpawning",
        "time set day",
        "weather clear",
        "setworldspawn 0 4 0",
        "forceload add -256 -256 256 256"
    )
    foreach ($name in $BotNames) {
        $commands += "gamemode creative $name"
        $commands += "effect give $name minecraft:saturation infinite 1 true"
        $commands += "give $name minecraft:stone 64"
        $commands += "give $name minecraft:oak_planks 64"
        $commands += "give $name minecraft:torch 64"
    }
    Invoke-RconCommands $commands $RconLog 15000
}

function Invoke-HostileBackgroundLoad([int]$LifeTicks, [string]$RconLog) {
    if (!(Test-Path -LiteralPath $PluginJar)) {
        return
    }
    $commands = @(
        "rlt at world 0 8 0 villagers 80 32 $LifeTicks",
        "rlt at world 96 8 0 path 80 32 $LifeTicks",
        "rlt at world -128 8 0 tracker 400 $LifeTicks 16",
        "rlt at world 0 8 128 broadcast 12 96 $LifeTicks",
        "rlt at world 0 8 0 scheduler region 128 256",
        "rlt at world 128 8 0 crossqueue 16 96 128",
        "rlt at world 0 8 0 syncload 64 4",
        "rlt at world 2048 8 0 scenario load 3 96 4 false 240 1",
        "rlt at world 200000 8 0 scenario gen 3 128 4 false 240 1"
    )
    Invoke-RconCommands $commands $RconLog 30000 500
}

function Invoke-FluidLeafBoundaryFixture([string]$RconLog) {
    $commands = @(
        "forceload add 112 -32 144 32",
        "fill 112 4 -32 144 4 32 minecraft:stone",
        "fill 112 5 -32 144 8 32 minecraft:air",
        "fill 126 5 -24 126 5 24 minecraft:water",
        "fill 127 5 -24 127 5 24 minecraft:water",
        "fill 128 5 -24 128 6 24 minecraft:oak_leaves[persistent=false]",
        "fill 129 5 -24 129 5 24 minecraft:redstone_wire",
        "setblock 130 5 0 minecraft:redstone_torch",
        "setblock 128 7 0 minecraft:oak_leaves[persistent=false]",
        "setblock 127 6 0 minecraft:air",
        "setblock 126 6 0 minecraft:lava"
    )
    Invoke-RconCommands $commands $RconLog 20000 150
}

function Invoke-TeleportHooks([string[]]$BotNames, [string]$RconLog) {
    $targets = @(
        [pscustomobject]@{ X = 0; Y = 8; Z = 0; Label = "spawn" },
        [pscustomobject]@{ X = 128; Y = 8; Z = 0; Label = "fluid-leaf-boundary" },
        [pscustomobject]@{ X = 2048; Y = 12; Z = 0; Label = "chunkload-send" },
        [pscustomobject]@{ X = -2048; Y = 12; Z = 2048; Label = "far-player-chunk-send" }
    )
    $commands = @()
    foreach ($target in $targets) {
        for ($i = 0; $i -lt $BotNames.Count; $i++) {
            $zOffset = ($i % 4) * 4
            $commands += "tp $($BotNames[$i]) $($target.X) $($target.Y) $($target.Z + $zOffset)"
        }
        $commands += "say mcc-chaos teleport-hook $($target.Label) $($target.X) $($target.Y) $($target.Z)"
    }
    for ($i = 0; $i -lt $BotNames.Count; $i++) {
        $x = if (($i % 2) -eq 0) { 124 } else { 132 }
        $z = -20 + (($i % 11) * 4)
        $commands += "tp $($BotNames[$i]) $x 8 $z"
    }
    Invoke-RconCommands $commands $RconLog 15000 250
}

function Invoke-NaturalSpawnHook([string[]]$BotNames, [string]$RconLog) {
    if (!$EnableNaturalSpawns) {
        return
    }
    $commands = @(
        "gamerule doMobSpawning true",
        "time set midnight",
        "forceload add 288 288 368 368",
        "fill 288 3 288 368 3 368 minecraft:grass_block",
        "fill 288 4 288 328 18 328 minecraft:air",
        "fill 329 4 288 368 18 328 minecraft:air",
        "fill 288 4 329 328 18 368 minecraft:air",
        "fill 329 4 329 368 18 368 minecraft:air"
    )
    if ($BotNames.Count -gt 0) {
        $commands += "tp $($BotNames[0]) 328 8 328"
    }
    Invoke-RconCommands $commands $RconLog 20000 250
}

function Invoke-ChaosScenarioHooks([string[]]$BotNames, [int]$LifeTicks, [string]$RconLog) {
    Invoke-FluidLeafBoundaryFixture $RconLog
    Invoke-TeleportHooks $BotNames $RconLog
    Invoke-NaturalSpawnHook $BotNames $RconLog
    Invoke-HostileBackgroundLoad $LifeTicks $RconLog
}

function Invoke-Controller([object[]]$BotInfos, [string]$CycleResultDir) {
    $ports = ($BotInfos | ForEach-Object { $_.Port }) -join ","
    $out = Join-Path $CycleResultDir "mcc-chaos-summary.json"
    $log = Join-Path $CycleResultDir "mcc-chaos-controller.log"
    $exitCodeFile = Join-Path $CycleResultDir "mcc-chaos-controller.exitcode"
    $args = @(
        $Controller,
        "--ports", $ports,
        "--password", $WebSocketPassword,
        "--duration-sec", "$DurationSec",
        "--seed", $Seed,
        "--out", $out
    )
    New-Directory (Split-Path -Parent $log)
    $argText = ($args | ForEach-Object { if ($_ -match "\s") { '"' + $_ + '"' } else { $_ } }) -join " "
    "COMMAND: node $argText" | Out-File -FilePath $log -Encoding utf8
    $process = Start-Process -FilePath "node" -ArgumentList $args -WorkingDirectory $RepoRoot `
        -RedirectStandardOutput ($log + ".out") -RedirectStandardError ($log + ".err") `
        -NoNewWindow -Wait -PassThru
    Get-Content ($log + ".out"), ($log + ".err") -ErrorAction SilentlyContinue | Add-Content -Path $log
    Write-Utf8File $exitCodeFile "$($process.ExitCode)`n"
    return $out
}

function Get-NewCrashReports([string]$CrashDir, [string[]]$Before) {
    if (!(Test-Path -LiteralPath $CrashDir)) {
        return @()
    }
    $after = @(Get-ChildItem -LiteralPath $CrashDir -Filter "crash-*.txt" | ForEach-Object { $_.FullName })
    return @($after | Where-Object { $Before -notcontains $_ })
}

function Get-FailurePatterns {
    $patterns = @(
        "Encountered an unexpected exception",
        "Exception ticking world",
        "Failed to handle packet",
        "Thread failed main thread check",
        "tried to run a task from the wrong thread",
        "Synchronous chunk load is not allowed",
        "Cannot merge non-quiescent",
        "ConcurrentModificationException",
        "IllegalStateException",
        "NullPointerException",
        "IndexOutOfBoundsException",
        "UnsupportedOperationException",
        "ClassCastException",
        "Crash report",
        "Watching Server",
        "This crash report has been saved",
        "AsyncCatcher",
        "wrong thread",
        "not owned",
        "already retired",
        "Chunk system crash propagated"
    )
    if ($FailOnMovementWarnings) {
        $patterns += "moved too quickly"
        $patterns += "moved wrongly"
    }
    return $patterns
}

function Get-FailureBuckets {
    return @(
        [pscustomobject]@{
            Name = "scheduled tick/neighbor/fluid-leaf-redstone"
            Patterns = @(
                "LevelTicks",
                "ScheduledTick",
                "scheduleTick",
                "tickNextTick",
                "NeighborUpdater",
                "neighbor update",
                "BlockEventData",
                "FlowingFluid",
                "LiquidBlock",
                "fluid",
                "water",
                "lava",
                "LeavesBlock",
                "leaf",
                "redstone",
                "RedStoneWireBlock"
            )
        },
        [pscustomobject]@{
            Name = "entity movement/teleport/player tick"
            Patterns = @(
                "ServerGamePacketListenerImpl",
                "handleMovePlayer",
                "PlayerList.*placeNewPlayer",
                "PlayerChunkLoader",
                "PlayerTick",
                "tickNonPassenger",
                "Entity\.move",
                "absMoveTo",
                "teleport",
                "moved too quickly",
                "moved wrongly"
            )
        },
        [pscustomobject]@{
            Name = "natural spawning/entity add/structure sync load"
            Patterns = @(
                "NaturalSpawner",
                "spawnForChunk",
                "MobSpawn",
                "addFreshEntity",
                "addEntity",
                "ServerLevel.*add",
                "Structure",
                "ChunkGeneratorStructureState",
                "StructureManager",
                "Synchronous chunk load",
                "getChunkAt"
            )
        },
        [pscustomobject]@{
            Name = "player chunk send/post-processing/stale holder broadcast"
            Patterns = @(
                "RegionizedPlayerChunkLoader",
                "PlayerChunkSender",
                "ChunkMap",
                "ChunkHolder",
                "NewChunkHolder",
                "stale holder",
                "post-processing",
                "post processing",
                "broadcast",
                "tracker",
                "ClientboundLevelChunk",
                "FullChunkStatus"
            )
        }
    )
}

function Get-TextSources([string]$ServerLogSegment, [string[]]$CrashReports) {
    $sources = @([pscustomobject]@{ Name = "server-test-segment.log"; Text = $ServerLogSegment })
    foreach ($path in $CrashReports) {
        if (Test-Path -LiteralPath $path) {
            try {
                $sources += [pscustomobject]@{
                    Name = "crash-report: $(Split-Path -Leaf $path)"
                    Text = [System.IO.File]::ReadAllText($path)
                    Path = $path
                }
            } catch {
                $sources += [pscustomobject]@{
                    Name = "crash-report: $(Split-Path -Leaf $path)"
                    Text = "Could not read crash report: $($_.Exception.Message)"
                    Path = $path
                }
            }
        }
    }
    return $sources
}

function Find-FirstPatternMatch([string[]]$Lines, [string[]]$Patterns) {
    for ($lineIndex = 0; $lineIndex -lt $Lines.Count; $lineIndex++) {
        foreach ($pattern in $Patterns) {
            if ($Lines[$lineIndex] -match $pattern) {
                return [pscustomobject]@{
                    LineIndex = $lineIndex
                    LineNumber = $lineIndex + 1
                    Signature = $pattern
                    Line = $Lines[$lineIndex]
                }
            }
        }
    }
    return $null
}

function Get-ContextSnippet([string[]]$Lines, [int]$LineIndex, [int]$ContextLines = 5) {
    if ($Lines.Count -eq 0) {
        return ""
    }
    $start = [Math]::Max(0, $LineIndex - $ContextLines)
    $end = [Math]::Min($Lines.Count - 1, $LineIndex + $ContextLines)
    $selected = @()
    for ($i = $start; $i -le $end; $i++) {
        $selected += ("{0,5}: {1}" -f ($i + 1), $Lines[$i])
    }
    return ($selected -join "`n").Trim()
}

function Find-PatternMatchNearAnchors([string[]]$Lines, [string[]]$AnchorPatterns, [string[]]$Patterns, [int]$ContextLines = 24) {
    $anchors = @()
    for ($lineIndex = 0; $lineIndex -lt $Lines.Count; $lineIndex++) {
        foreach ($anchorPattern in $AnchorPatterns) {
            if ($Lines[$lineIndex] -match $anchorPattern) {
                $anchors += $lineIndex
                break
            }
        }
    }
    foreach ($anchor in $anchors) {
        $start = [Math]::Max(0, $anchor - $ContextLines)
        $end = [Math]::Min($Lines.Count - 1, $anchor + $ContextLines)
        for ($lineIndex = $start; $lineIndex -le $end; $lineIndex++) {
            foreach ($pattern in $Patterns) {
                if ($Lines[$lineIndex] -match $pattern) {
                    return [pscustomobject]@{
                        LineIndex = $lineIndex
                        LineNumber = $lineIndex + 1
                        Signature = $pattern
                        Line = $Lines[$lineIndex]
                        AnchorLineNumber = $anchor + 1
                    }
                }
            }
        }
    }
    return $null
}

function Get-FailureClassifications([string]$ServerLogSegment, [string[]]$CrashReports, [object]$Failure) {
    $sources = Get-TextSources $ServerLogSegment $CrashReports
    $classifications = @()
    $anchorPatterns = @()
    foreach ($pattern in (@($Failure.PatternMatches) + (Get-FailurePatterns))) {
        if (![string]::IsNullOrWhiteSpace($pattern)) {
            $anchorPatterns += [regex]::Escape($pattern)
        }
    }
    $anchorPatterns += @("Description:", "Caused by:", "^\s*at ", "Exception", "Crash report")
    foreach ($bucket in Get-FailureBuckets) {
        foreach ($source in $sources) {
            $lines = @($source.Text -split "`r?`n")
            $match = Find-PatternMatchNearAnchors $lines $anchorPatterns $bucket.Patterns 24
            if ($null -eq $match -and $source.Name -like "crash-report:*") {
                $match = Find-FirstPatternMatch $lines $bucket.Patterns
            }
            if ($null -ne $match) {
                $classifications += [pscustomobject]@{
                    Bucket = $bucket.Name
                    MatchedSignature = $match.Signature
                    Source = $source.Name
                    SourcePath = $source.Path
                    LineNumber = $match.LineNumber
                    AnchorLineNumber = $match.AnchorLineNumber
                    Snippet = Get-ContextSnippet $lines $match.LineIndex 5
                }
                break
            }
        }
    }

    if ($classifications.Count -eq 0 -and $Failure.PatternMatches.Count -gt 0) {
        $genericPatterns = @($Failure.PatternMatches)
        foreach ($source in $sources) {
            $lines = @($source.Text -split "`r?`n")
            $match = Find-FirstPatternMatch $lines $genericPatterns
            if ($null -ne $match) {
                $classifications += [pscustomobject]@{
                    Bucket = "unclassified async/runtime"
                    MatchedSignature = $match.Signature
                    Source = $source.Name
                    SourcePath = $source.Path
                    LineNumber = $match.LineNumber
                    Snippet = Get-ContextSnippet $lines $match.LineIndex 5
                }
                break
            }
        }
    }
    return $classifications
}

function Format-IndentedSnippet([string]$Snippet, [int]$MaxLines = 18) {
    if ([string]::IsNullOrWhiteSpace($Snippet)) {
        return "    (no snippet)"
    }
    return (($Snippet -split "`r?`n" | Select-Object -First $MaxLines | ForEach-Object { "    $_" }) -join "`n")
}

function Format-FailureClassifications([object[]]$Classifications) {
    if ($Classifications.Count -eq 0) {
        return "No bucket-specific signature matched."
    }
    $parts = @()
    foreach ($classification in $Classifications) {
        $anchorText = ""
        if ($null -ne $classification.PSObject.Properties["AnchorLineNumber"] -and $classification.AnchorLineNumber) {
            $anchorText = " (failure anchor line $($classification.AnchorLineNumber))"
        }
        $parts += @"
- Bucket: $($classification.Bucket)
  Matched signature: $($classification.MatchedSignature)
  Source: $($classification.Source) line $($classification.LineNumber)$anchorText

$(Format-IndentedSnippet $classification.Snippet)
"@
    }
    return ($parts -join "`n")
}

function Get-CrashReportSnippets([string[]]$CrashReports) {
    if ($CrashReports.Count -eq 0) {
        return "No new crash reports."
    }
    $patterns = @("Description:", "Caused by:", "^\s*at ", "Exception", "Crash report")
    $parts = @()
    foreach ($path in ($CrashReports | Select-Object -First 4)) {
        if (!(Test-Path -LiteralPath $path)) {
            $parts += "### $(Split-Path -Leaf $path)`nPath: $path`n`n    (crash report missing)"
            continue
        }
        try {
            $text = [System.IO.File]::ReadAllText($path)
            $lines = @($text -split "`r?`n")
            $match = Find-FirstPatternMatch $lines $patterns
            if ($null -ne $match) {
                $snippet = Get-ContextSnippet $lines $match.LineIndex 10
            } else {
                $snippet = (($lines | Select-Object -First 80) -join "`n").Trim()
            }
            $parts += @"
### $(Split-Path -Leaf $path)
Path: $path

$(Format-IndentedSnippet $snippet 80)
"@
        } catch {
            $parts += "### $(Split-Path -Leaf $path)`nPath: $path`n`n    Could not read crash report: $($_.Exception.Message)"
        }
    }
    return ($parts -join "`n")
}

function Format-ObjectMap([object]$Map) {
    if ($null -eq $Map) {
        return ""
    }
    $properties = @($Map.PSObject.Properties)
    if ($properties.Count -eq 0) {
        return ""
    }
    return (($properties | Sort-Object Name | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join ", ")
}

function Format-Location([object]$Location) {
    if ($null -eq $Location -or $null -eq $Location.PSObject.Properties["x"]) {
        return "unknown"
    }
    $source = ""
    if ($null -ne $Location.PSObject.Properties["source"]) {
        $source = " source=$($Location.source)"
    }
    return ("x={0:N1}, y={1:N1}, z={2:N1}{3}" -f [double]$Location.x, [double]$Location.y, [double]$Location.z, $source)
}

function Format-ControllerDetail([string]$ControllerSummaryPath) {
    if ([string]::IsNullOrWhiteSpace($ControllerSummaryPath) -or !(Test-Path -LiteralPath $ControllerSummaryPath)) {
        return "Controller summary unavailable."
    }
    try {
        $summary = Get-Content -Raw -LiteralPath $ControllerSummaryPath | ConvertFrom-Json
        $commandSeed = $summary.seed
        if ($null -ne $summary.PSObject.Properties["commandSeed"]) {
            $commandSeed = $summary.commandSeed
        }
        $lines = @(
            "Command seed: $commandSeed",
            "Action counts: $(Format-ObjectMap $summary.actionCounts)",
            "Scenario counts: $(Format-ObjectMap $summary.scenarioCounts)"
        )
        foreach ($bot in @($summary.bots)) {
            $name = $bot.name
            $port = $bot.port
            $location = Format-Location $bot.lastKnownLocation
            $lastAction = "unknown"
            if ($null -ne $bot.lastAction -and $null -ne $bot.lastAction.PSObject.Properties["action"]) {
                $lastAction = $bot.lastAction.action
            }
            $stats = Format-ObjectMap $bot.stats
            $lines += "- $name port=$port location=$location lastAction=$lastAction stats=[$stats]"
        }
        return ($lines -join "`n")
    } catch {
        return "Could not parse controller summary: $($_.Exception.Message)"
    }
}

function Test-RunFailed([string]$ServerLogSegment, [string[]]$NewCrashReports, [string]$ControllerSummaryPath, [string]$ControllerExitCodePath) {
    $patternMatches = @()
    foreach ($pattern in Get-FailurePatterns) {
        if ($ServerLogSegment -match [regex]::Escape($pattern)) {
            $patternMatches += $pattern
        }
    }

    $controllerHardErrors = @()
    if (Test-Path -LiteralPath $ControllerSummaryPath) {
        try {
            $summary = Get-Content -Raw -LiteralPath $ControllerSummaryPath | ConvertFrom-Json
            $controllerHardErrors = @($summary.commandErrors | Where-Object {
                $_.message -match "WebSocket closed|not open|timed out"
            })
        } catch {
            $controllerHardErrors = @([pscustomobject]@{ message = "Could not parse controller summary: $($_.Exception.Message)" })
        }
    } else {
        $controllerHardErrors = @([pscustomobject]@{ message = "Missing controller summary: $ControllerSummaryPath" })
    }

    $controllerExitCode = 0
    if (Test-Path -LiteralPath $ControllerExitCodePath) {
        $rawExitCode = (Get-Content -Raw -LiteralPath $ControllerExitCodePath).Trim()
        if (![int]::TryParse($rawExitCode, [ref]$controllerExitCode)) {
            $controllerExitCode = 1
        }
    } else {
        $controllerExitCode = 1
    }

    return [pscustomobject]@{
        Failed = ($patternMatches.Count -gt 0 -or $NewCrashReports.Count -gt 0 -or $controllerHardErrors.Count -gt 0 -or $controllerExitCode -ne 0)
        PatternMatches = $patternMatches
        CrashReports = $NewCrashReports
        ControllerHardErrors = $controllerHardErrors
        ControllerExitCode = $controllerExitCode
    }
}

function New-FailureReport([string]$CycleResultDir, [string]$ServerLogSegment, [object]$Failure, [string]$ControllerSummaryPath) {
    if ([string]::IsNullOrWhiteSpace($CycleResultDir)) {
        $CycleResultDir = $ResultRoot
    }
    $reportPath = [System.IO.Path]::Combine($CycleResultDir, "failure-report.md")
    if ([string]::IsNullOrWhiteSpace($reportPath)) {
        throw "New-FailureReport produced an empty report path. CycleResultDir='$CycleResultDir'"
    }
    $tail = (($ServerLogSegment -split "`r?`n") | Select-Object -Last 240) -join "`n"
    $gitCommit = ((& git -C $RepoRoot rev-parse HEAD 2>$null) | Select-Object -First 1)
    if ([string]::IsNullOrWhiteSpace($gitCommit)) {
        $gitCommit = "unknown"
    }
    $gitStatus = (& git -C $RepoRoot status --short --branch 2>$null) -join "`n"
    $gitDiffStat = (& git -C $RepoRoot diff --stat 2>$null) -join "`n"
    $classifications = @(Get-FailureClassifications $ServerLogSegment $Failure.CrashReports $Failure)
    $classificationPath = Join-Path $CycleResultDir "failure-classification.json"
    Write-Utf8File $classificationPath ((ConvertTo-Json -InputObject $classifications -Depth 8) + "`n")
    $classificationMarkdown = Format-FailureClassifications $classifications
    $controllerDetail = Format-ControllerDetail $ControllerSummaryPath
    $crashSnippets = Get-CrashReportSnippets $Failure.CrashReports
    $content = @"
# MCC Chaos Failure Report

Generated: $(Get-Date -Format o)
Commit: $gitCommit
Seed: $Seed
Command seed: $Seed
Duration: ${DurationSec}s
Bots: $BotCount
Server port: $ServerPort
RCON port: $RconPort
Controller summary: $ControllerSummaryPath
Classification JSON: $classificationPath

## Compact Classification

$classificationMarkdown

## Controller Detail

```
$controllerDetail
```

## Failure Signals

Pattern matches:

```
$($Failure.PatternMatches -join "`n")
```

New crash reports:

```
$($Failure.CrashReports -join "`n")
```

Controller hard errors:

```
$(($Failure.ControllerHardErrors | ForEach-Object { $_.message }) -join "`n")
```

Controller exit code:

```
$($Failure.ControllerExitCode)
```

## Git Status

```
$gitStatus
```

## Git Diff Stat

```
$gitDiffStat
```

## Crash Snippets

$crashSnippets

## Server Log Tail

```log
$tail
```
"@
    $reportParent = Split-Path -Parent $reportPath
    if ($reportParent) {
        New-Directory $reportParent
    }
    [System.IO.File]::WriteAllText($reportPath, $content, [System.Text.UTF8Encoding]::new($false))
    return $reportPath
}

function New-AgentPrompt([string]$CycleResultDir, [string]$FailureReport) {
    if ([string]::IsNullOrWhiteSpace($CycleResultDir)) {
        $CycleResultDir = $ResultRoot
    }
    $promptPath = [System.IO.Path]::Combine($CycleResultDir, "agent-prompt.md")
    if ([string]::IsNullOrWhiteSpace($promptPath)) {
        throw "New-AgentPrompt produced an empty prompt path. CycleResultDir='$CycleResultDir'"
    }
    $content = @"
You are fixing ShreddedPaper async/region runtime bugs found by the MCC chaos pipeline.

Repository: $RepoRoot
Failure report: $FailureReport

Please:
1. Inspect the failure report, server log excerpts, crash reports, and relevant code.
2. Identify the smallest production fix for the root cause.
3. Preserve unrelated user changes in the dirty worktree.
4. Run a focused build, preferably `.\gradlew.bat :shreddedpaper-server:createMojmapPaperclipJar --stacktrace`, after editing.
5. Summarize the changed files and verification.

Do not modify files under `$RootPath` except transient logs if needed.
"@
    $promptParent = Split-Path -Parent $promptPath
    if ($promptParent) {
        New-Directory $promptParent
    }
    [System.IO.File]::WriteAllText($promptPath, $content, [System.Text.UTF8Encoding]::new($false))
    return $promptPath
}

function Invoke-AgentFix([string]$PromptPath, [string]$CycleResultDir) {
    $agentLog = Join-Path $CycleResultDir "codex-agent.log"
    $promptText = Get-Content -Raw -LiteralPath $PromptPath
    $promptText | & $CodexCommand exec --dangerously-bypass-approvals-and-sandbox -C $RepoRoot - 2>&1 |
        Tee-Object -FilePath $agentLog
    if ($LASTEXITCODE -ne 0) {
        throw "Codex agent failed with exit code $LASTEXITCODE. See $agentLog"
    }
}

function Build-Artifacts([string]$CycleResultDir) {
    if ($SkipBuild) {
        if (!(Test-Path -LiteralPath $ServerJar)) {
            throw "Missing server jar and -SkipBuild was specified: $ServerJar"
        }
        return
    }
    Invoke-LoggedCommand -FilePath (Join-Path $RepoRoot "gradlew.bat") `
        -Arguments @("applyAllPatches") `
        -WorkingDirectory $RepoRoot `
        -LogPath (Join-Path $CycleResultDir "gradle-applyAllPatches.log")
    Invoke-LoggedCommand -FilePath (Join-Path $RepoRoot "gradlew.bat") `
        -Arguments @(":shreddedpaper-server:createMojmapPaperclipJar", "--stacktrace") `
        -WorkingDirectory $RepoRoot `
        -LogPath (Join-Path $CycleResultDir "gradle-createMojmapPaperclipJar.log")
    Invoke-LoggedCommand -FilePath (Join-Path $RepoRoot "gradlew.bat") `
        -Arguments @("-p", "tools\region-load-test-plugin", "jar") `
        -WorkingDirectory $RepoRoot `
        -LogPath (Join-Path $CycleResultDir "gradle-region-load-test-plugin.log")
}

function Invoke-OneCycle([int]$CycleNumber, [string]$MccExe, [string]$WebSocketTemplate) {
    $cycleName = "cycle-" + $CycleNumber.ToString("00")
    $cycleRunDir = Join-Path $RunRoot $cycleName
    $cycleResultDir = Join-Path $ResultRoot $cycleName
    New-Directory $cycleRunDir
    New-Directory $cycleResultDir

    Build-Artifacts $cycleResultDir

    $serverDir = Prepare-ServerDirectory $cycleRunDir
    $crashDir = Join-Path $serverDir "crash-reports"
    $beforeCrashReports = @()
    if (Test-Path -LiteralPath $crashDir) {
        $beforeCrashReports = @(Get-ChildItem -LiteralPath $crashDir -Filter "crash-*.txt" | ForEach-Object { $_.FullName })
    }

    $serverProcess = $null
    $mccProcesses = @()
    try {
        try {
            $serverProcess = Start-TestServer $serverDir
        } catch {
            $logPath = Join-Path $serverDir "logs\latest.log"
            $serverLogSegment = if (Test-Path -LiteralPath $logPath) {
                Get-Content -Raw -LiteralPath $logPath
            } else {
                ""
            }
            $serverLogSegment += "`nStartup failure: $($_.Exception.Message)`n"
            Write-Utf8File (Join-Path $cycleResultDir "server-startup-failure.log") $serverLogSegment
            $newCrashReports = Get-NewCrashReports $crashDir $beforeCrashReports
            $failure = [pscustomobject]@{
                Failed = $true
                PatternMatches = @("startup-failure")
                CrashReports = $newCrashReports
                ControllerHardErrors = @([pscustomobject]@{ message = $_.Exception.Message })
                ControllerExitCode = 1
            }
            $failureReport = New-FailureReport $cycleResultDir $serverLogSegment $failure ""
            return [pscustomobject]@{
                Passed = $false
                Cycle = $CycleNumber
                ResultDir = $cycleResultDir
                FailureReport = $failureReport
                AgentPrompt = ""
            }
        }

        $botInfos = Write-MccBotFiles $cycleRunDir $MccExe $WebSocketTemplate
        $mccProcesses = Start-MccBots $botInfos
        Wait-ForPlayers ($botInfos | ForEach-Object { $_.Name }) 140

        $rconLog = Join-Path $cycleResultDir "rcon.log"
        Write-Utf8File $rconLog ""
        Invoke-SetupCommands ($botInfos | ForEach-Object { $_.Name }) $rconLog

        $logPath = Join-Path $serverDir "logs\latest.log"
        $logOffset = Get-FileOffset $logPath
        Invoke-ChaosScenarioHooks ($botInfos | ForEach-Object { $_.Name }) ([Math]::Max(1200, ($DurationSec + 30) * 20)) $rconLog
        $controllerSummary = Invoke-Controller $botInfos $cycleResultDir
        Start-Sleep -Seconds 5
        $serverLogSegment = Get-FileSince $logPath $logOffset
        Write-Utf8File (Join-Path $cycleResultDir "server-test-segment.log") $serverLogSegment

        $newCrashReports = Get-NewCrashReports $crashDir $beforeCrashReports
        $controllerExitCodePath = Join-Path $cycleResultDir "mcc-chaos-controller.exitcode"
        $failure = Test-RunFailed $serverLogSegment $newCrashReports $controllerSummary $controllerExitCodePath
        if ($failure.Failed) {
            $failureReport = New-FailureReport $cycleResultDir $serverLogSegment $failure $controllerSummary
            return [pscustomobject]@{
                Passed = $false
                Cycle = $CycleNumber
                ResultDir = $cycleResultDir
                FailureReport = $failureReport
                AgentPrompt = ""
            }
        }

        Write-Utf8File (Join-Path $cycleResultDir "pass.txt") "MCC chaos cycle passed at $(Get-Date -Format o).`n"
        return [pscustomobject]@{
            Passed = $true
            Cycle = $CycleNumber
            ResultDir = $cycleResultDir
            FailureReport = ""
            AgentPrompt = ""
        }
    } finally {
        Stop-Processes $mccProcesses
        if (!$NoStop) {
            Stop-Server $serverProcess
        }
    }
}

New-Directory $RootPath
New-Directory $CacheDir
New-Directory $RunRoot
New-Directory $ResultRoot

Write-Host "MCC chaos root: $RootPath"
Write-Host "Seed: $Seed"
Write-Host "AgentMode: $AgentMode"

$mccExe = Install-Mcc
$webSocketTemplate = Get-WebSocketBotTemplate
$lastResult = $null

for ($cycle = 1; $cycle -le $MaxCycles; $cycle++) {
    Write-Host "Starting MCC chaos cycle $cycle / $MaxCycles"
    $lastResult = Invoke-OneCycle $cycle $mccExe $webSocketTemplate
    if ($lastResult.Passed) {
        Write-Host "MCC chaos passed on cycle $cycle. Results: $($lastResult.ResultDir)"
        exit 0
    }

    Write-Host "MCC chaos failed on cycle $cycle. Failure report: $($lastResult.FailureReport)"
    if ($AgentMode -eq "ReportOnly") {
        Write-Host "AgentMode=ReportOnly, stopping after report generation."
        exit 1
    }

    if ([string]::IsNullOrWhiteSpace($lastResult.AgentPrompt)) {
        $lastResult.AgentPrompt = New-AgentPrompt $lastResult.ResultDir $lastResult.FailureReport
    }

    if ($AgentMode -eq "Prompt") {
        Write-Host "Agent prompt written: $($lastResult.AgentPrompt)"
        exit 1
    }

    if ($cycle -lt $MaxCycles) {
        Invoke-AgentFix $lastResult.AgentPrompt $lastResult.ResultDir
    }
}

if ($null -ne $lastResult -and !$lastResult.Passed) {
    Write-Host "MCC chaos failed after $MaxCycles cycle(s). Last report: $($lastResult.FailureReport)"
}
exit 1
