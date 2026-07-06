param(
    [string]$LogPath = ""
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..')).Path
if ([string]::IsNullOrWhiteSpace($LogPath)) {
    $LogPath = Join-Path $repoRoot 'run\logs\dev-validation-latest.log'
}

if (-not (Test-Path -LiteralPath $LogPath)) {
    throw "Dev validation log not found: $LogPath"
}

$log = Get-Content -LiteralPath $LogPath -Raw
$required = @(
    '[MMCE One Block DevValidation] Enabled',
    '[MMCE One Block DevValidation] placed id=starter_controller',
    '[MMCE One Block DevValidation] inserted minecraft:cobblestone into input slot',
    '[MMCE One Block DevValidation] saved and queued chunk reload check',
    '[MMCE One Block DevValidation] chunk unloaded',
    '[MMCE One Block DevValidation] chunk reload persisted id=starter_controller',
    '[MMCE One Block DevValidation] PASS id=starter_controller',
    'formed=true',
    'recipeFinished=true',
    'nbtPayload=true',
    'chunkReloaded=true',
    'inventoryPersisted=true',
    'energyPersisted=true',
    'comparatorAfterFormed=1'
)

foreach ($needle in $required) {
    if (-not $log.Contains($needle)) {
        throw "Dev validation log assertion failed; missing: $needle"
    }
}

if ($log.Contains('[MMCE One Block DevValidation] FAIL')) {
    throw 'Dev validation log assertion failed; validation reported FAIL.'
}

Write-Host "Dev validation log assertions passed: $LogPath"
