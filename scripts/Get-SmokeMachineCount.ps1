param(
    [string]$FixtureRoot = ""
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..')).Path
if ([string]::IsNullOrWhiteSpace($FixtureRoot)) {
    $FixtureRoot = Join-Path $repoRoot 'examples\smoke\config\mmce-one-block\machines'
}

if (-not (Test-Path -LiteralPath $FixtureRoot)) {
    throw "Smoke machine fixture directory not found: $FixtureRoot"
}

$expected = @(
    'advanced_mana_pool_controller.json',
    'starter_controller.json',
    'starter_factory_controller.json'
)

$actual = @(Get-ChildItem -LiteralPath $FixtureRoot -Filter '*.json' -File |
    Sort-Object Name |
    Select-Object -ExpandProperty Name)

if ($actual.Count -ne $expected.Count -or (($actual -join '|') -ne ($expected -join '|'))) {
    throw "Smoke machine fixture set must stay exactly at $($expected -join ', '); found: $($actual -join ', ')"
}

$count = $expected.Count
Write-Output $count
