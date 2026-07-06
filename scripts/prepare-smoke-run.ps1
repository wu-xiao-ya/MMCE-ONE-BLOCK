param(
    [string]$RunDir = "",
    [switch]$AcceptEula
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..')).Path
if ([string]::IsNullOrWhiteSpace($RunDir)) {
    $RunDir = Join-Path $repoRoot 'run'
}

$runPath = New-Item -ItemType Directory -Force -Path $RunDir
$sourceConfig = Join-Path $repoRoot 'examples\smoke\config'
$targetConfig = Join-Path $runPath.FullName 'config'

if (-not (Test-Path -LiteralPath $sourceConfig)) {
    throw "Smoke config source not found: $sourceConfig"
}

New-Item -ItemType Directory -Force -Path $targetConfig | Out-Null
Get-ChildItem -LiteralPath $sourceConfig | Copy-Item -Destination $targetConfig -Recurse -Force

if ($AcceptEula) {
    Set-Content -LiteralPath (Join-Path $runPath.FullName 'eula.txt') -Value 'eula=true' -Encoding ASCII
}

Write-Host "Prepared smoke config under $targetConfig"
Write-Host "Next: runClient or runServer from Gradle, then inspect run/logs/latest.log."
