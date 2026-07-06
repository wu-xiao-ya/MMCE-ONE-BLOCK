param(
    [string]$LogPath = "",
    [switch]$RequireGuiValidation
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
if ($RequireGuiValidation) {
    $required += '[MMCE One Block ClientGuiValidation] PASS id=starter_controller'
    $required += 'screen=com.fushu.mmceguiext.client.gui.GuiMachineControllerResizable'
    $required += 'container=com.fushu.mmceoneblock.common.container.ContainerSingleBlockController'
    $required += 'styleRuntime=true'
    $required += 'displayed=true'
}

foreach ($needle in $required) {
    if (-not $log.Contains($needle)) {
        throw "Client smoke log assertion failed; missing: $needle"
    }
}

$forbidden = @(
    'Skipping one-block machine',
    'Caught exception from MMCE One Block',
    'Exception loading model for variant modularmachinery:starter_machine_controller',
    'FileNotFoundException: modularmachinery:blockstates/starter_machine_controller.json',
    'A fatal error has occurred',
    'Crash report saved to',
    '[MMCE One Block ClientGuiValidation] FAIL'
)

foreach ($needle in $forbidden) {
    if ($log.Contains($needle)) {
        throw "Client smoke log assertion failed; found: $needle"
    }
}

Write-Host "Client smoke log assertions passed: $LogPath"
