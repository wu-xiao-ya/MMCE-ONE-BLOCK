param(
    [int]$TimeoutSeconds = 240,
    [switch]$SkipPrepare
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..')).Path
$logPath = Join-Path $repoRoot 'run\logs\latest.log'
$logDir = Split-Path -Parent $logPath

if (-not $SkipPrepare) {
    & (Join-Path $scriptDir 'prepare-smoke-run.ps1') -AcceptEula
}

New-Item -ItemType Directory -Force -Path $logDir | Out-Null
if (Test-Path -LiteralPath $logPath) {
    Remove-Item -LiteralPath $logPath -Force
}

$gradle = Join-Path $repoRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Gradle wrapper not found: $gradle"
}

$args = @('--no-daemon', '--no-parallel', '--console=plain', '-PmmceOneBlockDevValidation=true', 'runServer', '--stacktrace')
$process = Start-Process -FilePath $gradle -ArgumentList $args -WorkingDirectory $repoRoot -PassThru -WindowStyle Hidden
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$finished = $false
$failed = $false

try {
    while ((Get-Date) -lt $deadline) {
        if (Test-Path -LiteralPath $logPath) {
            $log = Get-Content -LiteralPath $logPath -Raw
            if ($log.Contains('[MMCE One Block DevValidation] FAIL')) {
                $finished = $true
                $failed = $true
                break
            }
            if ($log.Contains('[MMCE One Block DevValidation] PASS id=starter_controller')) {
                $finished = $true
                break
            }
        }
        if ($process.HasExited) {
            break
        }
        Start-Sleep -Seconds 2
    }

    if (-not $finished) {
        if ($process.HasExited) {
            throw "runServer exited before dev validation evidence was observed. Exit code: $($process.ExitCode)"
        }
        throw "Timed out after $TimeoutSeconds seconds waiting for dev validation evidence."
    }
    if ($failed) {
        throw 'Dev validation reported FAIL; inspect run/logs/latest.log.'
    }
} finally {
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" |
        Where-Object {
            ($_.CommandLine -like "*$repoRoot*") -and
            ($_.CommandLine -like '*runServer*' -or $_.CommandLine -like '*GradleStartServer*')
        } |
        ForEach-Object {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        }
}

$savedLog = Join-Path $logDir 'dev-validation-latest.log'
Copy-Item -LiteralPath $logPath -Destination $savedLog -Force
& (Join-Path $scriptDir 'assert-dev-validation-log.ps1') -LogPath $savedLog
Write-Host "Saved dev validation log: $savedLog"
