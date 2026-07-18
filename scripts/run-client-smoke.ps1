param(
    [int]$TimeoutSeconds = 300,
    [switch]$SkipPrepare,
    [switch]$GuiValidation
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..')).Path
$logPath = Join-Path $repoRoot 'run\logs\latest.log'
$machineCount = [int](& (Join-Path $scriptDir 'Get-SmokeMachineCount.ps1'))
$expectedFixtures = @(
    'advanced_mana_pool_controller.json',
    'starter_controller.json',
    'starter_factory_controller.json'
)

if (-not $SkipPrepare) {
    & (Join-Path $scriptDir 'prepare-smoke-run.ps1') -AcceptEula
}

foreach ($fixture in $expectedFixtures) {
    if (-not (Test-Path -LiteralPath (Join-Path $repoRoot "examples\smoke\config\mmce-one-block\machines\$fixture"))) {
        throw "Smoke fixture not found: $fixture"
    }
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

$args = @('--no-daemon', '--no-parallel', '--console=plain')
if ($GuiValidation) {
    $args += '-PmmceOneBlockClientGuiValidation=true'
}
$args += @('runClient', '--stacktrace')
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
            if ($log.Contains('[MMCE One Block ClientGuiValidation] FAIL')) {
                throw "Client GUI validation failed; inspect log: $logPath"
            }
            $loaded = $log.Contains('Forge Mod Loader has successfully loaded') `
                -and $log.Contains("Loaded $machineCount one-block machine definition(s)") `
                -and $log.Contains("Validated $machineCount one-block machine definition(s) against loaded MMCE machines (0 missing, 0 mismatched)")
            if ($GuiValidation) {
                $loaded = $loaded `
                    -and $log.Contains('[MMCE One Block ClientGuiValidation] PASS id=starter_controller') `
                    -and $log.Contains('[MMCE One Block ClientGuiValidation] PASS id=factory_controller') `
                    -and $log.Contains('verified virtual Smart Interface write key=oneblock_smoke_target value=42.0') `
                    -and $log.Contains('for id=starter_controller') `
                    -and $log.Contains('for id=factory_controller')
            }
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

if ($GuiValidation) {
    & (Join-Path $scriptDir 'assert-client-smoke-log.ps1') -LogPath $logPath -RequireGuiValidation
} else {
    & (Join-Path $scriptDir 'assert-client-smoke-log.ps1') -LogPath $logPath
}

$savedLog = Join-Path $logDir 'client-smoke-latest.log'
Copy-Item -LiteralPath $logPath -Destination $savedLog -Force
Write-Host "Saved client smoke log: $savedLog"
