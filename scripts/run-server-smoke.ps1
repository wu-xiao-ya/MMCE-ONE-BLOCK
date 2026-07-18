param(
    [int]$TimeoutSeconds = 300,
    [switch]$SkipPrepare
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..')).Path
$logPath = Join-Path $repoRoot 'run\logs\latest.log'
$logDir = Split-Path -Parent $logPath
$fixtureRoot = Join-Path $repoRoot 'examples\smoke\config\mmce-one-block\machines'
$machineCount = [int](& (Join-Path $scriptDir 'Get-SmokeMachineCount.ps1'))
$expectedFixtures = @(
    'starter_controller.json',
    'starter_factory_controller.json'
)

if (-not $SkipPrepare) {
    & (Join-Path $scriptDir 'prepare-smoke-run.ps1') -AcceptEula
}

foreach ($fixture in $expectedFixtures) {
    if (-not (Test-Path -LiteralPath (Join-Path $fixtureRoot $fixture))) {
        throw "Smoke fixture not found: $fixture"
    }
}

New-Item -ItemType Directory -Force -Path $logDir | Out-Null
if (Test-Path -LiteralPath $logPath) {
    Remove-Item -LiteralPath $logPath -Force
}

$gradle = Join-Path $repoRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Gradle wrapper not found: $gradle"
}

$args = @('--no-daemon', '--no-parallel', '--console=plain', 'runServer', '--stacktrace')
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
            if ($null -eq $log) {
                $log = ''
            }
            if ($log.Contains('Forge Mod Loader has successfully loaded') `
                -and $log.Contains("Loaded $machineCount one-block machine definition(s)") `
                -and $log.Contains("Validated $machineCount one-block machine definition(s) against loaded MMCE machines (0 missing, 0 mismatched)") `
                -and $log.Contains('Done (')) {
                $loaded = $true
                break
            }
            if ($log.Contains('Exception') -or $log.Contains('ERROR') -or $log.Contains('FAIL')) {
                throw "Server smoke reported an error; inspect log: $logPath"
            }
        }
        Start-Sleep -Seconds 2
    }

    if (-not $loaded) {
        if ($process.HasExited) {
            throw "runServer exited before server smoke evidence was observed. Exit code: $($process.ExitCode)"
        }
        throw "Timed out after $TimeoutSeconds seconds waiting for server smoke evidence."
    }
} finally {
    $escapedRoot = $repoRoot.Replace('\', '\\')
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" |
        Where-Object {
            ($_.CommandLine -like "*$repoRoot*" -or $_.CommandLine -like "*$escapedRoot*") -and
            ($_.CommandLine -like '*runServer*' -or $_.CommandLine -like '*GradleStartServer*')
        } |
        ForEach-Object {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        }
}

& (Join-Path $scriptDir 'assert-smoke-log.ps1') -LogPath $logPath

$savedLog = Join-Path $logDir 'server-smoke-latest.log'
Copy-Item -LiteralPath $logPath -Destination $savedLog -Force
Write-Host "Saved server smoke log: $savedLog"
