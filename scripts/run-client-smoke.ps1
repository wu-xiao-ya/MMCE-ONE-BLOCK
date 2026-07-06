param(
    [int]$TimeoutSeconds = 180,
    [switch]$SkipPrepare
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..')).Path
$logPath = Join-Path $repoRoot 'run\logs\latest.log'

if (-not $SkipPrepare) {
    & (Join-Path $scriptDir 'prepare-smoke-run.ps1') -AcceptEula
}

$logDir = Split-Path -Parent $logPath
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
if (Test-Path -LiteralPath $logPath) {
    Remove-Item -LiteralPath $logPath -Force
}

$gradle = Join-Path $repoRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Gradle wrapper not found: $gradle"
}

$args = @('--no-daemon', '--no-parallel', '--console=plain', 'runClient', '--stacktrace')
$process = Start-Process -FilePath $gradle -ArgumentList $args -WorkingDirectory $repoRoot -PassThru -WindowStyle Hidden
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$loaded = $false

try {
    while ((Get-Date) -lt $deadline) {
        if ($process.HasExited) {
            break
        }
        if (Test-Path -LiteralPath $logPath) {
            $log = Get-Content -LiteralPath $logPath -Raw
            $loaded = $log.Contains('Forge Mod Loader has successfully loaded') `
                -and $log.Contains('Loaded 1 one-block machine definition(s)') `
                -and $log.Contains('Validated 1 one-block machine definition(s) against loaded MMCE machines (0 skipped)')
            if ($loaded) {
                break
            }
        }
        Start-Sleep -Seconds 2
    }

    if (-not $loaded) {
        if ($process.HasExited) {
            throw "runClient exited before client smoke evidence was observed. Exit code: $($process.ExitCode)"
        }
        throw "Timed out after $TimeoutSeconds seconds waiting for client smoke evidence."
    }
} finally {
    $escapedRoot = $repoRoot.Replace('\', '\\')
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" |
        Where-Object {
            ($_.CommandLine -like "*$repoRoot*" -or $_.CommandLine -like "*$escapedRoot*") -and
            ($_.CommandLine -like '*runClient*' -or $_.CommandLine -like '*GradleStartClient*' -or $_.CommandLine -like '*net.minecraft.client.main.Main*')
        } |
        ForEach-Object {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        }
}

& (Join-Path $scriptDir 'assert-client-smoke-log.ps1') -LogPath $logPath

$savedLog = Join-Path $logDir 'client-smoke-latest.log'
Copy-Item -LiteralPath $logPath -Destination $savedLog -Force
Write-Host "Saved client smoke log: $savedLog"
