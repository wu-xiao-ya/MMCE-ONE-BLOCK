param(
    [string]$LogPath = ""
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..')).Path
if ([string]::IsNullOrWhiteSpace($LogPath)) {
    $LogPath = Join-Path $repoRoot 'run\logs\latest.log'
}

if (-not (Test-Path -LiteralPath $LogPath)) {
    throw "Client smoke log not found: $LogPath"
}

$log = Get-Content -LiteralPath $LogPath -Raw
$required = @(
    'LWJGL Version',
    'MinecraftForge v',
    'Loaded 1 one-block machine definition(s)',
    'Validated 1 one-block machine definition(s) against loaded MMCE machines (0 skipped)',
    'Forge Mod Loader has successfully loaded',
    'mmceoneblock',
    'mmceguiext'
)

foreach ($needle in $required) {
    if (-not $log.Contains($needle)) {
        throw "Client smoke log assertion failed; missing: $needle"
    }
}

$forbidden = @(
    'Skipping one-block machine',
    'Caught exception from MMCE One Block',
    'A fatal error has occurred',
    'Crash report saved to'
)

foreach ($needle in $forbidden) {
    if ($log.Contains($needle)) {
        throw "Client smoke log assertion failed; found: $needle"
    }
}

Write-Host "Client smoke log assertions passed: $LogPath"
