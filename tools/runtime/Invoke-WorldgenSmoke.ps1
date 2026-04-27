param(
    [string]$ServerDir = "D:\worldgen",
    [int]$RconPort = 25575,
    [string]$RconPassword = "codex-region-test",
    [string]$PlayerName = "Hancho1577",
    [int]$ReadyTimeoutSec = 240,
    [int]$WaitForPlayerSec = 90,
    [int]$PostCommandWaitSec = 20,
    [switch]$NoStart,
    [switch]$NoStop,
    [switch]$SkipPlayerTeleport,
    [switch]$KillMinecraft
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

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

function Remove-MinecraftColors([string]$Text) {
    return $Text -replace "\u00A7.", ""
}

function Test-PlayerOnline {
    if ([string]::IsNullOrWhiteSpace($PlayerName)) {
        return $false
    }
    $plainList = Remove-MinecraftColors (Send-RconCommand "list" 5000)
    return $plainList.Contains($PlayerName)
}

function Wait-ForPlayer {
    if ($SkipPlayerTeleport) {
        return
    }
    $deadline = (Get-Date).AddSeconds($WaitForPlayerSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-PlayerOnline) {
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "Player '$PlayerName' did not join within $WaitForPlayerSec seconds. Use -SkipPlayerTeleport only for non-interactive smoke runs."
}

function Wait-ForReady([System.Diagnostics.Process]$Process) {
    $deadline = (Get-Date).AddSeconds($ReadyTimeoutSec)
    $log = Join-Path $ServerDir "logs\latest.log"
    while ((Get-Date) -lt $deadline) {
        if ($Process -ne $null -and $Process.HasExited) {
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

function Get-LogOffset([string]$Path) {
    if (!(Test-Path -LiteralPath $Path)) {
        return 0L
    }
    return (Get-Item -LiteralPath $Path).Length
}

function Get-LogSince([string]$Path, [long]$Offset) {
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

function Invoke-SmokeCommand([string]$Command, [int]$SleepMs = 1000) {
    Write-Host "RCON> $Command"
    $response = Send-RconCommand $Command 20000
    if (![string]::IsNullOrWhiteSpace($response)) {
        Write-Host $response.Trim()
    }
    Start-Sleep -Milliseconds $SleepMs
}

function Stop-Server([System.Diagnostics.Process]$Process) {
    try {
        [void](Send-RconCommand "stop" 3000)
    } catch {
        Write-Warning "Could not send stop over RCON: $($_.Exception.Message)"
    }

    if ($Process -ne $null) {
        if (!$Process.WaitForExit(60000)) {
            Write-Warning "Server did not exit after stop; killing process tree."
            Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
        }
    }
}

$serverPath = (Resolve-Path -LiteralPath $ServerDir).Path
$startScript = Join-Path $serverPath "start.bat"
if (!(Test-Path -LiteralPath $startScript)) {
    throw "Missing start.bat in $serverPath"
}

$logPath = Join-Path $serverPath "logs\latest.log"
$crashDir = Join-Path $serverPath "crash-reports"
$beforeCrashReports = @()
if (Test-Path -LiteralPath $crashDir) {
    $beforeCrashReports = @(Get-ChildItem -LiteralPath $crashDir -Filter "crash-*.txt" | ForEach-Object { $_.FullName })
}

$startedProcess = $null
if (!(Test-RconReady)) {
    if ($NoStart) {
        throw "Server is not reachable over RCON and -NoStart was specified."
    }
    Write-Host "Starting $startScript"
    $startedProcess = Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", "start.bat") -WorkingDirectory $serverPath -WindowStyle Hidden -PassThru
}

$exitCode = 0
try {
    Wait-ForReady $startedProcess
    Wait-ForPlayer
    $logOffset = Get-LogOffset $logPath

    $commands = New-Object System.Collections.Generic.List[string]
    @(
        "list",
        "tps",
        "mspt",
        "region"
    ) | ForEach-Object { $commands.Add($_) }

    if (!$SkipPlayerTeleport) {
        @(
            "execute as $PlayerName at @s run tp @s 0 ~ 0",
            "execute as $PlayerName at @s run tp @s 64 ~ 64",
            "execute as $PlayerName at @s run tp @s 0 ~ 0"
        ) | ForEach-Object { $commands.Add($_) }
    }

    @(
        "rlt at world 0 120 0 chunkload 4 false",
        "rlt at world 0 120 0 syncload 64 4",
        "rlt cleanup",
        "tps",
        "region"
    ) | ForEach-Object { $commands.Add($_) }

    foreach ($command in $commands) {
        Invoke-SmokeCommand $command
    }

    Start-Sleep -Seconds $PostCommandWaitSec

    $newLog = Get-LogSince $logPath $logOffset
    $patterns = @(
        "Encountered an unexpected exception",
        "Failed to handle packet",
        "Thread failed main thread check",
        "tried to run a task from the wrong thread",
        "Synchronous chunk load is not allowed",
        "Cannot merge non-quiescent",
        "Cannot read field `"cachedChunkPacket`"",
        "NullPointerException",
        "Chunk system crash propagated",
        "moved too quickly",
        "moved wrongly"
    )

    $matches = New-Object System.Collections.Generic.List[string]
    foreach ($pattern in $patterns) {
        if ($newLog -match [regex]::Escape($pattern)) {
            $matches.Add($pattern)
        }
    }

    $afterCrashReports = @()
    if (Test-Path -LiteralPath $crashDir) {
        $afterCrashReports = @(Get-ChildItem -LiteralPath $crashDir -Filter "crash-*.txt" | ForEach-Object { $_.FullName })
    }
    $newCrashReports = @($afterCrashReports | Where-Object { $beforeCrashReports -notcontains $_ })

    if ($matches.Count -gt 0 -or $newCrashReports.Count -gt 0) {
        Write-Host "Smoke failed."
        if ($matches.Count -gt 0) {
            Write-Host "Matched log patterns: $($matches -join ', ')"
        }
        if ($newCrashReports.Count -gt 0) {
            Write-Host "New crash reports:"
            $newCrashReports | ForEach-Object { Write-Host "  $_" }
        }
        $exitCode = 1
    } else {
        Write-Host "Smoke passed: no watched async/region crash signatures were found."
    }
} catch {
    Write-Host "Smoke failed: $($_.Exception.Message)"
    $exitCode = 1
} finally {
    if (!$NoStop) {
        Stop-Server $startedProcess
    }

    if ($KillMinecraft) {
        Get-Process | Where-Object { $_.ProcessName -match "^(javaw|Minecraft|MinecraftLauncher)$" } |
            Stop-Process -Force -ErrorAction SilentlyContinue
    }
}

exit $exitCode
