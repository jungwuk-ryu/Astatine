param(
    [string]$Root = "D:\server-benchmarks",
    [string]$JavaPath = "C:\Program Files\Java\jdk-25.0.2\bin\java.exe",
    [string]$RconPassword = "codex-benchmark",
    [int]$HeapGb = 8,
    [string[]]$EngineFilter = @(),
    [string[]]$ScenarioFilter = @(),
    [switch]$SkipDownloads,
    [switch]$SkipOriginalBuild,
    [switch]$Smoke
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$RootPath = [System.IO.Path]::GetFullPath($Root)
$JarDir = Join-Path $RootPath "jars"
$SourceDir = Join-Path $RootPath "sources"
$RunDir = Join-Path $RootPath "runs"
$ResultDir = Join-Path $RootPath "results"
$PluginJar = Join-Path $RepoRoot "tools\region-load-test-plugin\build\libs\region-load-test-plugin-0.1.0-SNAPSHOT.jar"
$CustomJar = Join-Path $RepoRoot "shreddedpaper-server\build\libs\shreddedpaper-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar"

function New-Directory([string]$Path) {
    if (!(Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Assert-UnderRoot([string]$Path) {
    $full = [System.IO.Path]::GetFullPath($Path)
    if (!$full.StartsWith($RootPath, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to operate outside benchmark root: $full"
    }
}

function Write-Utf8File([string]$Path, [string]$Content) {
    $parent = Split-Path -Parent $Path
    if ($parent) {
        New-Directory $parent
    }
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Invoke-Download([string]$Url, [string]$OutFile) {
    if (Test-Path -LiteralPath $OutFile) {
        return
    }
    Write-Host "Downloading $Url"
    New-Directory (Split-Path -Parent $OutFile)
    Invoke-WebRequest -Uri $Url -OutFile $OutFile
}

function Invoke-CommandLogged([string]$FilePath, [string[]]$Arguments, [string]$WorkingDirectory, [string]$LogPath) {
    New-Directory (Split-Path -Parent $LogPath)
    $argText = ($Arguments | ForEach-Object { if ($_ -match "\s") { '"' + $_ + '"' } else { $_ } }) -join " "
    "COMMAND: $FilePath $argText" | Out-File -FilePath $LogPath -Encoding utf8
    $process = Start-Process -FilePath $FilePath -ArgumentList $Arguments -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput ($LogPath + ".out") -RedirectStandardError ($LogPath + ".err") `
        -NoNewWindow -Wait -PassThru
    Get-Content ($LogPath + ".out"), ($LogPath + ".err") -ErrorAction SilentlyContinue | Add-Content -Path $LogPath
    if ($process.ExitCode -ne 0) {
        throw "Command failed with exit code $($process.ExitCode): $FilePath $argText"
    }
}

function Build-RegionLoadTestPlugin {
    if (Test-Path -LiteralPath $PluginJar) {
        return
    }
    Invoke-CommandLogged -FilePath (Join-Path $RepoRoot "gradlew.bat") `
        -Arguments @("-p", "tools\region-load-test-plugin", "clean", "jar") `
        -WorkingDirectory $RepoRoot `
        -LogPath (Join-Path $ResultDir "build-region-load-test-plugin.log")
}

function Build-OriginalShreddedPaper([string]$OutJar) {
    if (Test-Path -LiteralPath $OutJar) {
        return
    }
    if ($SkipOriginalBuild) {
        Write-Warning "Skipping original ShreddedPaper build; missing jar: $OutJar"
        return
    }
    $source = Join-Path $SourceDir "original-shreddedpaper-1.21.11"
    if (!(Test-Path -LiteralPath $source)) {
        New-Directory $SourceDir
        Invoke-CommandLogged -FilePath "git" `
            -Arguments @("clone", "--depth", "1", "--branch", "ver/1.21.11", "https://github.com/MultiPaper/ShreddedPaper.git", $source) `
            -WorkingDirectory $SourceDir `
            -LogPath (Join-Path $ResultDir "clone-original-shreddedpaper.log")
    }
    $oldJavaHome = $env:JAVA_HOME
    try {
        $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $JavaPath)
        Invoke-CommandLogged -FilePath (Join-Path $source "gradlew.bat") `
            -Arguments @("applyAllPatches") `
            -WorkingDirectory $source `
            -LogPath (Join-Path $ResultDir "patch-original-shreddedpaper.log")
        Invoke-CommandLogged -FilePath (Join-Path $source "gradlew.bat") `
            -Arguments @(":shreddedpaper-server:createMojmapPaperclipJar") `
            -WorkingDirectory $source `
            -LogPath (Join-Path $ResultDir "build-original-shreddedpaper.log")
    } finally {
        $env:JAVA_HOME = $oldJavaHome
    }
    $built = Get-ChildItem -LiteralPath (Join-Path $source "shreddedpaper-server\build\libs") -Filter "*paperclip*mojmap*.jar" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $built) {
        throw "Original ShreddedPaper build did not produce a paperclip mojmap jar."
    }
    Copy-Item -LiteralPath $built.FullName -Destination $OutJar -Force
}

function Prepare-Jars {
    New-Directory $JarDir
    if (!(Test-Path -LiteralPath $CustomJar)) {
        throw "Current fork jar is missing. Build :shreddedpaper-server:createMojmapPaperclipJar first. Missing: $CustomJar"
    }
    Copy-Item -LiteralPath $CustomJar -Destination (Join-Path $JarDir "custom-shreddedpaper-1.21.11.jar") -Force

    if (!$SkipDownloads) {
        Invoke-Download "https://fill-data.papermc.io/v1/objects/25eb85bd8415195ce4bc188e1939e0c7cef77fb51d26d4e766407ee922561097/paper-1.21.11-130.jar" `
            (Join-Path $JarDir "paper-1.21.11-130.jar")
        Invoke-Download "https://fill-data.papermc.io/v1/objects/f52c408490a0225611e67907a3ca19f7e6da2c6bc899e715d5f46844e7103c39/folia-1.21.11-14.jar" `
            (Join-Path $JarDir "folia-1.21.11-14.jar")
        Invoke-Download "https://api.purpurmc.org/v2/purpur/1.21.11/2568/download" `
            (Join-Path $JarDir "purpur-1.21.11-2568.jar")
        Invoke-Download "https://files.bxteam.org/divinemc/versions/1.21.11/24/divinemc-1.21.11-24.jar" `
            (Join-Path $JarDir "divinemc-1.21.11-24.jar")
    }

    Build-OriginalShreddedPaper (Join-Path $JarDir "original-shreddedpaper-1.21.11.jar")
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

function Send-RconCommand([int]$Port, [string]$Command, [int]$TimeoutMs = 10000) {
    $client = [System.Net.Sockets.TcpClient]::new()
    $client.ReceiveTimeout = $TimeoutMs
    $client.SendTimeout = $TimeoutMs
    try {
        $client.Connect("127.0.0.1", $Port)
        $stream = $client.GetStream()
        $auth = New-RconPacketBytes 1 3 $RconPassword
        $stream.Write($auth, 0, $auth.Length)
        $stream.Flush()
        $authResponse = Read-RconPacket $stream
        if ($authResponse.Id -eq -1) {
            throw "RCON authentication failed on port $Port."
        }
        $packet = New-RconPacketBytes 2 2 $Command
        $stream.Write($packet, 0, $packet.Length)
        $stream.Flush()
        return (Read-RconPacket $stream).Payload
    } finally {
        $client.Close()
    }
}

function Wait-ForServerReady([string]$ServerDir, [int]$RconPort, [System.Diagnostics.Process]$Process, [int]$TimeoutSec = 240) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $log = Join-Path $ServerDir "logs\latest.log"
    while ((Get-Date) -lt $deadline) {
        if ($Process.HasExited) {
            throw "Server process exited before ready. ExitCode=$($Process.ExitCode)"
        }
        if (Test-Path -LiteralPath $log) {
            $tail = Get-Content -LiteralPath $log -Tail 40 -ErrorAction SilentlyContinue
            if (($tail -join "`n") -match "Done \(") {
                try {
                    [void](Send-RconCommand $RconPort "list" 3000)
                    return
                } catch {
                    Start-Sleep -Milliseconds 500
                }
            }
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for server readiness: $ServerDir"
}

function Stop-Server([System.Diagnostics.Process]$Process, [int]$RconPort) {
    if ($null -eq $Process -or $Process.HasExited) {
        return
    }
    try {
        [void](Send-RconCommand $RconPort "stop" 5000)
    } catch {
        Write-Warning "Could not send stop over RCON: $($_.Exception.Message)"
    }
    if (!$Process.WaitForExit(45000)) {
        Write-Warning "Server did not stop in time; killing PID $($Process.Id)."
        $Process.Kill()
        $Process.WaitForExit()
    }
}

function Get-LogSegment([string]$LogPath, [ref]$Offset) {
    if (!(Test-Path -LiteralPath $LogPath)) {
        return ""
    }
    $stream = [System.IO.File]::Open($LogPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        if ($Offset.Value -gt $stream.Length) {
            $Offset.Value = 0
        }
        $stream.Seek($Offset.Value, [System.IO.SeekOrigin]::Begin) | Out-Null
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8, $true, 4096, $true)
        $text = $reader.ReadToEnd()
        $Offset.Value = $stream.Position
        $reader.Dispose()
        return $text
    } finally {
        $stream.Dispose()
    }
}

function Write-ServerProperties([string]$ServerDir, [int]$ServerPort, [int]$RconPort) {
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
force-gamemode=false
function-permission-level=4
gamemode=survival
generate-structures=false
hardcore=false
level-name=world
level-seed=shreddedpaper-benchmark-20260426
max-players=20
max-tick-time=-1
motd=Codex benchmark
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
simulation-distance=7
spawn-animals=true
spawn-monsters=true
spawn-npcs=true
spawn-protection=0
sync-chunk-writes=false
use-native-transport=true
view-distance=12
white-list=false
"@
    Write-Utf8File (Join-Path $ServerDir "server.properties") $properties
}

function Prepare-ServerDirectory($Engine) {
    $serverDir = Join-Path $RunDir $Engine.Name
    Assert-UnderRoot $serverDir
    if (Test-Path -LiteralPath $serverDir) {
        Remove-Item -LiteralPath $serverDir -Recurse -Force
    }
    New-Directory $serverDir
    New-Directory (Join-Path $serverDir "plugins")
    Copy-Item -LiteralPath $Engine.JarPath -Destination (Join-Path $serverDir "server.jar") -Force
    Copy-Item -LiteralPath $PluginJar -Destination (Join-Path $serverDir "plugins\region-load-test-plugin.jar") -Force
    Write-Utf8File (Join-Path $serverDir "eula.txt") "eula=true`n"
    Write-ServerProperties $serverDir $Engine.ServerPort $Engine.RconPort
    return $serverDir
}

function Start-BenchmarkServer($Engine, [string]$ServerDir) {
    $stdout = Join-Path $ServerDir "console.out.log"
    $stderr = Join-Path $ServerDir "console.err.log"
    $args = @(
        "--enable-preview",
        "-Xms$($HeapGb)G",
        "-Xmx$($HeapGb)G",
        "-Duser.timezone=GMT+9",
        "-Dfile.encoding=UTF-8",
        "-XX:+UseG1GC",
        "-XX:+ParallelRefProcEnabled",
        "-XX:MaxGCPauseMillis=200",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+DisableExplicitGC",
        "-XX:+AlwaysPreTouch",
        "-XX:+UseStringDeduplication",
        "-XX:+PerfDisableSharedMem",
        "--add-modules=jdk.incubator.vector",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.net=ALL-UNNAMED",
        "-jar", "server.jar", "nogui"
    )
    $process = Start-Process -FilePath $JavaPath -ArgumentList $args -WorkingDirectory $ServerDir `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    Wait-ForServerReady $ServerDir $Engine.RconPort $process
    return $process
}

function Invoke-BenchmarkCommand($Engine, [string]$Command, [string]$RconLog) {
    $response = ""
    try {
        $response = Send-RconCommand $Engine.RconPort $Command
    } catch {
        $response = "ERROR: $($_.Exception.Message)"
    }
    Add-Content -Path $RconLog -Encoding utf8 -Value ("> $Command`n$response`n")
    return $response
}

function Initialize-World($Engine, [string]$RconLog) {
    $commands = @(
        "gamerule doDaylightCycle false",
        "gamerule doWeatherCycle false",
        "gamerule mobGriefing false",
        "gamerule doMobSpawning false",
        "time set day",
        "weather clear",
        "forceload add -64 -64 64 64",
        "forceload add 8128 -64 8256 64"
    )
    foreach ($command in $commands) {
        [void](Invoke-BenchmarkCommand $Engine $command $RconLog)
    }
}

function Get-Scenarios {
    $baselineSamples = if ($Smoke) { 120 } else { 600 }
    $entitySamples = if ($Smoke) { 160 } else { 600 }
    $chunkSamples = if ($Smoke) { 160 } else { 600 }
    $villagers = if ($Smoke) { 40 } else { 300 }
    $pathMobs = if ($Smoke) { 40 } else { 300 }
    $life = if ($Smoke) { 160 } else { 600 }
    $chunkRegions = if ($Smoke) { 1 } else { 4 }
    $chunkRadius = if ($Smoke) { 2 } else { 6 }
    $tntWidth = if ($Smoke) { 8 } else { 18 }
    $tntDepth = if ($Smoke) { 8 } else { 18 }
    $tntSamples = if ($Smoke) { 180 } else { 800 }

    return @(
        [pscustomobject]@{
            Name = "baseline"
            TimeoutSec = if ($Smoke) { 25 } else { 45 }
            Commands = @("rlt at world 0 96 0 probe $baselineSamples 1")
        },
        [pscustomobject]@{
            Name = "villagers"
            TimeoutSec = if ($Smoke) { 35 } else { 55 }
            Commands = @(
                "rlt at world 0 96 0 probe $entitySamples 1",
                "rlt at world 8192 96 0 probe $entitySamples 1",
                "rlt at world 0 96 0 villagers $villagers 32 $life"
            )
        },
        [pscustomobject]@{
            Name = "pathfinding"
            TimeoutSec = if ($Smoke) { 35 } else { 55 }
            Commands = @(
                "rlt at world 0 96 0 probe $entitySamples 1",
                "rlt at world 8192 96 0 probe $entitySamples 1",
                "rlt at world 0 96 0 path $pathMobs 48 $life"
            )
        },
        [pscustomobject]@{
            Name = "chunk-generation"
            TimeoutSec = if ($Smoke) { 45 } else { 120 }
            Commands = @(
                "rlt at world 8192 96 0 probe $chunkSamples 1",
                "rlt at world 200000 96 0 scenario gen $chunkRegions 128 $chunkRadius false $chunkSamples 1"
            )
        },
        [pscustomobject]@{
            Name = "chunk-load"
            TimeoutSec = if ($Smoke) { 45 } else { 100 }
            Commands = @(
                "rlt at world 8192 96 0 probe $chunkSamples 1",
                "rlt at world 200000 96 0 scenario load $chunkRegions 128 $chunkRadius false $chunkSamples 1"
            )
        },
        [pscustomobject]@{
            Name = "tnt-isolation"
            TimeoutSec = if ($Smoke) { 45 } else { 90 }
            Commands = @(
                "rlt at world 0 96 0 probe $tntSamples 1",
                "rlt at world 8192 96 0 probe $tntSamples 1",
                "rlt at world 0 96 0 tntsingle $tntWidth $tntDepth 2 40 8"
            )
        }
    )
}

function Get-NumberAverage([double[]]$Values) {
    if ($Values.Count -eq 0) {
        return $null
    }
    return ($Values | Measure-Object -Average).Average
}

function Get-NumberMin([double[]]$Values) {
    if ($Values.Count -eq 0) {
        return $null
    }
    return ($Values | Measure-Object -Minimum).Minimum
}

function Get-NumberMax([double[]]$Values) {
    if ($Values.Count -eq 0) {
        return $null
    }
    return ($Values | Measure-Object -Maximum).Maximum
}

function Parse-Tps([string[]]$Responses) {
    $values = @()
    foreach ($response in $Responses) {
        $clean = Remove-MinecraftFormatting $response
        if ($clean -match "TPS from last 5s,\s*1m,\s*5m,\s*15m:\s*([0-9.]+)") {
            $values += [double]$Matches[1]
        } elseif ($clean -match "TPS from last 1m,\s*5m,\s*15m:\s*([0-9.]+)") {
            $values += [double]$Matches[1]
        } elseif ($clean -match "Lowest Region TPS:\s*([0-9.]+)") {
            $values += [double]$Matches[1]
        }
    }
    return $values
}

function Parse-Mspt([string[]]$Responses) {
    $values = @()
    foreach ($response in $Responses) {
        $clean = Remove-MinecraftFormatting $response
        if ($clean -match "([0-9]+(?:\.[0-9]+)?)/([0-9]+(?:\.[0-9]+)?)/([0-9]+(?:\.[0-9]+)?)") {
            $values += [pscustomobject]@{
                Avg = [double]$Matches[1]
                Min = [double]$Matches[2]
                Max = [double]$Matches[3]
            }
        }
    }
    return $values
}

function Parse-HealthReportMspt([string[]]$Responses) {
    $values = @()
    foreach ($response in $Responses) {
        $clean = Remove-MinecraftFormatting $response
        $matches = [regex]::Matches($clean, "util at\s*([0-9]+(?:\.[0-9]+)?)\s*MSPT")
        if ($matches.Count -gt 0) {
            $sample = @($matches | ForEach-Object { [double]$_.Groups[1].Value })
            $values += [pscustomobject]@{
                Avg = Get-NumberAverage ([double[]]$sample)
                Min = Get-NumberMin ([double[]]$sample)
                Max = Get-NumberMax ([double[]]$sample)
            }
        }
    }
    return $values
}

function Remove-MinecraftFormatting([string]$Text) {
    if ($null -eq $Text) {
        return ""
    }
    return ($Text -replace "§.", "")
}

function Parse-RltLog([string]$Text) {
    $probes = @()
    $chunks = @()
    foreach ($line in ($Text -split "`r?`n")) {
        if ($line -match "(?<label>.+) finished at chunk=(?<chunkX>-?\d+),(?<chunkZ>-?\d+): samples=(?<samples>\d+) periodTicks=(?<period>\d+) avgLagMs=(?<avg>-?[0-9.]+) p95LagMs=(?<p95>-?[0-9.]+)(?: p99LagMs=(?<p99>-?[0-9.]+))? maxLagMs=(?<max>-?[0-9.]+)") {
            $probes += [pscustomobject]@{
                Label = $Matches.label.Trim()
                ChunkX = [int]$Matches.chunkX
                ChunkZ = [int]$Matches.chunkZ
                Samples = [int]$Matches.samples
                PeriodTicks = [int]$Matches.period
                AvgLagMs = [double]$Matches.avg
                P95LagMs = [double]$Matches.p95
                P99LagMs = if ($Matches.p99) { [double]$Matches.p99 } else { $null }
                MaxLagMs = [double]$Matches.max
                Raw = $line
            }
        }
        if ($line -match "(?<label>.+) batch finished: centerChunk=(?<chunkX>-?\d+),(?<chunkZ>-?\d+) total=(?<total>\d+) success=(?<success>\d+) null=(?<null>\d+) failure=(?<failure>\d+) generate=(?<generate>true|false) urgent=(?<urgent>true|false) elapsedMs=(?<elapsed>[0-9.]+)") {
            $elapsedMs = [double]$Matches.elapsed
            $success = [int]$Matches.success
            $chunks += [pscustomobject]@{
                Label = $Matches.label.Trim()
                ChunkX = [int]$Matches.chunkX
                ChunkZ = [int]$Matches.chunkZ
                Total = [int]$Matches.total
                Success = $success
                Null = [int]$Matches.null
                Failure = [int]$Matches.failure
                Generate = [bool]::Parse($Matches.generate)
                Urgent = [bool]::Parse($Matches.urgent)
                ElapsedMs = $elapsedMs
                ChunksPerSec = if ($elapsedMs -gt 0) { $success / ($elapsedMs / 1000.0) } else { $null }
                Raw = $line
            }
        }
    }
    return [pscustomobject]@{ Probes = $probes; ChunkBatches = $chunks }
}

function Invoke-Scenario($Engine, [string]$ServerDir, $Scenario, [ref]$LogOffset) {
    $scenarioDir = Join-Path (Join-Path $ResultDir $Engine.Name) $Scenario.Name
    New-Directory $scenarioDir
    $rconLog = Join-Path $scenarioDir "rcon.log"
    Write-Utf8File $rconLog ""
    Write-Host "Running $($Engine.Name) / $($Scenario.Name)"

    [void](Get-LogSegment (Join-Path $ServerDir "logs\latest.log") $LogOffset)
    [void](Invoke-BenchmarkCommand $Engine "rlt cleanup" $rconLog)
    Start-Sleep -Seconds 2

    foreach ($command in $Scenario.Commands) {
        [void](Invoke-BenchmarkCommand $Engine $command $rconLog)
        Start-Sleep -Milliseconds 500
    }

    $tpsResponses = @()
    $msptResponses = @()
    $deadline = (Get-Date).AddSeconds($Scenario.TimeoutSec)
    Start-Sleep -Seconds 5
    while ((Get-Date) -lt $deadline) {
        $tpsResponses += Invoke-BenchmarkCommand $Engine "tps" $rconLog
        $msptResponses += Invoke-BenchmarkCommand $Engine "mspt" $rconLog
        if ($Engine.HasRegionCommand) {
            [void](Invoke-BenchmarkCommand $Engine "region" $rconLog)
        }
        Start-Sleep -Seconds 10
    }

    [void](Invoke-BenchmarkCommand $Engine "rlt status" $rconLog)
    [void](Invoke-BenchmarkCommand $Engine "rlt cleanup" $rconLog)
    Start-Sleep -Seconds 2

    $segment = Get-LogSegment (Join-Path $ServerDir "logs\latest.log") $LogOffset
    Write-Utf8File (Join-Path $scenarioDir "server.log") $segment
    $parsed = Parse-RltLog $segment
    $tps = Parse-Tps $tpsResponses
    $mspt = Parse-Mspt $msptResponses
    if ($mspt.Count -eq 0 -or @(($mspt | Where-Object { $_.Avg -gt 0 -or $_.Max -gt 0 })).Count -eq 0) {
        $mspt = Parse-HealthReportMspt $tpsResponses
    }
    $msptAvgValues = @($mspt | ForEach-Object { $_.Avg })
    $msptMinValues = @($mspt | ForEach-Object { $_.Min })
    $msptMaxValues = @($mspt | ForEach-Object { $_.Max })

    $loadProbes = @($parsed.Probes | Where-Object { [Math]::Abs($_.ChunkX) -lt 256 })
    $controlProbes = @($parsed.Probes | Where-Object { [Math]::Abs($_.ChunkX) -ge 256 -and [Math]::Abs($_.ChunkX) -lt 20000 })
    $chunkBatches = @($parsed.ChunkBatches)
    $errors = @()
    if ($segment -match "(?m)\b(ERROR|Exception|Failed to handle packet|Crash report)\b") {
        $errors += "server-log-error"
    }
    foreach ($response in ($tpsResponses + $msptResponses)) {
        if ($response -match "Unknown or incomplete command|ERROR:") {
            $errors += "metric-command-error"
            break
        }
    }

    $result = [pscustomobject]@{
        Engine = $Engine.Name
        Scenario = $Scenario.Name
        Jar = Split-Path -Leaf $Engine.JarPath
        Tps5sAvg = Get-NumberAverage ([double[]]$tps)
        Tps5sMin = Get-NumberMin ([double[]]$tps)
        MsptAvg = Get-NumberAverage ([double[]]$msptAvgValues)
        MsptMin = Get-NumberMin ([double[]]$msptMinValues)
        MsptMax = Get-NumberMax ([double[]]$msptMaxValues)
        LoadProbeAvgLagMs = Get-NumberAverage ([double[]]@($loadProbes | ForEach-Object { $_.AvgLagMs }))
        LoadProbeP95LagMs = Get-NumberMax ([double[]]@($loadProbes | ForEach-Object { $_.P95LagMs }))
        LoadProbeMaxLagMs = Get-NumberMax ([double[]]@($loadProbes | ForEach-Object { $_.MaxLagMs }))
        ControlProbeAvgLagMs = Get-NumberAverage ([double[]]@($controlProbes | ForEach-Object { $_.AvgLagMs }))
        ControlProbeP95LagMs = Get-NumberMax ([double[]]@($controlProbes | ForEach-Object { $_.P95LagMs }))
        ControlProbeMaxLagMs = Get-NumberMax ([double[]]@($controlProbes | ForEach-Object { $_.MaxLagMs }))
        ChunkSuccess = [int](($chunkBatches | Measure-Object -Property Success -Sum).Sum ?? 0)
        ChunkFailure = [int](($chunkBatches | Measure-Object -Property Failure -Sum).Sum ?? 0)
        ChunkElapsedMsMax = Get-NumberMax ([double[]]@($chunkBatches | ForEach-Object { $_.ElapsedMs }))
        ChunkPerSecSum = ($chunkBatches | Measure-Object -Property ChunksPerSec -Sum).Sum
        ProbeCount = $parsed.Probes.Count
        ChunkBatchCount = $parsed.ChunkBatches.Count
        Errors = (($errors | Select-Object -Unique) -join ";")
        Raw = [pscustomobject]@{
            TpsResponses = $tpsResponses
            MsptResponses = $msptResponses
            Probes = $parsed.Probes
            ChunkBatches = $parsed.ChunkBatches
        }
    }
    Write-Utf8File (Join-Path $scenarioDir "parsed.json") ($result | ConvertTo-Json -Depth 8)
    return $result
}

function Format-Nullable($Value, [string]$Format = "0.00") {
    if ($null -eq $Value -or ($Value -is [string] -and $Value.Length -eq 0)) {
        return "n/a"
    }
    return ([double]$Value).ToString($Format, [Globalization.CultureInfo]::InvariantCulture)
}

function Write-Summary([object[]]$Results) {
    $csv = Join-Path $ResultDir "benchmark-results.csv"
    $Results | Select-Object Engine,Scenario,Jar,Tps5sAvg,Tps5sMin,MsptAvg,MsptMin,MsptMax,LoadProbeAvgLagMs,LoadProbeP95LagMs,LoadProbeMaxLagMs,ControlProbeAvgLagMs,ControlProbeP95LagMs,ControlProbeMaxLagMs,ChunkSuccess,ChunkFailure,ChunkElapsedMsMax,ChunkPerSecSum,ProbeCount,ChunkBatchCount,Errors |
        Export-Csv -Path $csv -NoTypeInformation -Encoding utf8

    $md = New-Object System.Text.StringBuilder
    [void]($md.AppendLine("# Benchmark Summary - $(Get-Date -Format s)"))
    [void]($md.AppendLine(""))
    [void]($md.AppendLine("Root: $RootPath"))
    [void]($md.AppendLine(""))
    [void]($md.AppendLine("| Engine | Scenario | TPS avg | TPS min | MSPT avg | MSPT max | Load probe avg lag | Control probe avg lag | Chunk success | Chunk/sec sum | Errors |"))
    [void]($md.AppendLine("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |"))
    foreach ($row in $Results) {
        [void]($md.AppendLine("| $($row.Engine) | $($row.Scenario) | $(Format-Nullable $row.Tps5sAvg) | $(Format-Nullable $row.Tps5sMin) | $(Format-Nullable $row.MsptAvg) | $(Format-Nullable $row.MsptMax) | $(Format-Nullable $row.LoadProbeAvgLagMs) | $(Format-Nullable $row.ControlProbeAvgLagMs) | $($row.ChunkSuccess) | $(Format-Nullable $row.ChunkPerSecSum) | $($row.Errors) |"))
    }
    Write-Utf8File (Join-Path $ResultDir "benchmark-summary.md") ($md.ToString())
}

New-Directory $RootPath
New-Directory $JarDir
New-Directory $SourceDir
New-Directory $RunDir
New-Directory $ResultDir

Build-RegionLoadTestPlugin
Prepare-Jars

$engines = @(
    [pscustomobject]@{ Name = "custom-shreddedpaper"; JarPath = Join-Path $JarDir "custom-shreddedpaper-1.21.11.jar"; ServerPort = 25600; RconPort = 35600; HasRegionCommand = $true },
    [pscustomobject]@{ Name = "original-shreddedpaper"; JarPath = Join-Path $JarDir "original-shreddedpaper-1.21.11.jar"; ServerPort = 25601; RconPort = 35601; HasRegionCommand = $false },
    [pscustomobject]@{ Name = "folia"; JarPath = Join-Path $JarDir "folia-1.21.11-14.jar"; ServerPort = 25602; RconPort = 35602; HasRegionCommand = $false },
    [pscustomobject]@{ Name = "divinemc"; JarPath = Join-Path $JarDir "divinemc-1.21.11-24.jar"; ServerPort = 25603; RconPort = 35603; HasRegionCommand = $false },
    [pscustomobject]@{ Name = "paper"; JarPath = Join-Path $JarDir "paper-1.21.11-130.jar"; ServerPort = 25604; RconPort = 35604; HasRegionCommand = $false },
    [pscustomobject]@{ Name = "purpur"; JarPath = Join-Path $JarDir "purpur-1.21.11-2568.jar"; ServerPort = 25605; RconPort = 35605; HasRegionCommand = $false }
)

$engines = @($engines | Where-Object { Test-Path -LiteralPath $_.JarPath })
if ($EngineFilter.Count -gt 0) {
    $wantedEngines = @($EngineFilter | ForEach-Object { $_.ToLowerInvariant() })
    $engines = @($engines | Where-Object { $wantedEngines -contains $_.Name.ToLowerInvariant() })
}
$scenarios = Get-Scenarios
if ($ScenarioFilter.Count -gt 0) {
    $wanted = @($ScenarioFilter | ForEach-Object { $_.ToLowerInvariant() })
    $scenarios = @($scenarios | Where-Object { $wanted -contains $_.Name.ToLowerInvariant() })
}

$allResults = @()
foreach ($engine in $engines) {
    $serverDir = Prepare-ServerDirectory $engine
    $process = $null
    try {
        $process = Start-BenchmarkServer $engine $serverDir
        $logOffset = 0L
        $rconInitLog = Join-Path (Join-Path $ResultDir $engine.Name) "init-rcon.log"
        New-Directory (Split-Path -Parent $rconInitLog)
        Write-Utf8File $rconInitLog ""
        Initialize-World $engine $rconInitLog
        Start-Sleep -Seconds 20
        [void](Get-LogSegment (Join-Path $serverDir "logs\latest.log") ([ref]$logOffset))
        foreach ($scenario in $scenarios) {
            $allResults += Invoke-Scenario $engine $serverDir $scenario ([ref]$logOffset)
            Write-Summary $allResults
        }
    } finally {
        Stop-Server $process $engine.RconPort
    }
}

Write-Summary $allResults
$summaryPath = Join-Path $ResultDir "benchmark-summary.md"
Write-Host "Benchmark complete. Results: $summaryPath"
