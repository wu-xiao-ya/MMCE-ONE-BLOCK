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
$machineCount = [int](& (Join-Path $scriptDir 'Get-SmokeMachineCount.ps1'))
$required = @(
    'LWJGL Version',
    'MinecraftForge v',
    "Loaded $machineCount one-block machine definition(s)",
    "Validated $machineCount one-block machine definition(s) against loaded MMCE machines (0 missing, 0 mismatched)",
    'Forge Mod Loader has successfully loaded',
    'mmceoneblock',
    'mmceguiext'
)
if ($RequireGuiValidation) {
    $required += '[MMCE One Block ClientGuiValidation] PASS id=starter_controller'
    $required += '[MMCE One Block ClientGuiValidation] PASS id=factory_controller'
    $required += 'screen=com.fushu.mmceguiext.client.gui.GuiMachineControllerResizable'
    $required += 'screen=com.fushu.mmceguiext.client.gui.GuiFactoryControllerResizable'
    $required += 'container=com.fushu.mmceoneblock.common.container.ContainerSingleBlockController'
    $required += 'container=com.fushu.mmceoneblock.common.container.ContainerSingleBlockFactoryController'
    $required += 'styleRuntime=true'
    $required += 'verified virtual Smart Interface write key=oneblock_smoke_target value=42.0'
    $required += 'displayed=true'
    $required += 'screenshot saved under'
    $required += 'for id=starter_controller'
    $required += 'for id=factory_controller'
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
