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
    throw "Smoke log not found: $LogPath"
}

$log = Get-Content -LiteralPath $LogPath -Raw
$machineCount = [int](& (Join-Path $scriptDir 'Get-SmokeMachineCount.ps1'))
$required = @(
    "Loaded $machineCount one-block machine definition(s)",
    "Validated $machineCount one-block machine definition(s) against loaded MMCE machines (0 missing, 0 mismatched)",
    'Machines: [1 loaded, 0 failed]',
    'modularmachinery:starter_machine (Recipes): [1 loaded, 0 failed]',
    'Forge Mod Loader has successfully loaded',
    'Done ('
)

foreach ($needle in $required) {
    if (-not $log.Contains($needle)) {
        throw "Smoke log assertion failed; missing: $needle"
    }
}

if ($log.Contains('Skipping one-block machine')) {
    throw 'Smoke log assertion failed; a one-block machine was skipped.'
}

Write-Host "Smoke log assertions passed: $LogPath"
