# MMCE One Block

MMCE One Block is a config-first companion repo for a one-block progression pack built on top of MMCEGE.
It registers configured one-block controller blocks that delegate recipes to existing MMCE machines while exposing item, fluid, gas, and energy components from one tile.

The upstream MMCE code stays read-only. The extension surface this addon needs lives in MMCEGE, and this repo only consumes that API.

## What is in this repo

- `examples/` - sample one-block machine JSON and client config templates
- `src/main/java/` - Forge registration, config loading, controller block, tile, container, and GUI bridge
- `src/test/` - parser, example drift, and MMCEGE API contract tests
- `.github/workflows/` - CI that builds MMCEGE first, then validates this addon

## Recommended flow

1. Build or install the MMCEGE API branch first.
2. Keep the backing MMCE machine JSON in `config/modularmachinery/machinery/`.
3. Copy `examples/one-block-machine.json` into `config/mmce-one-block/machines/`.
4. Copy `examples/client.cfg.sample` to `config/mmceguiext/client.cfg` if you want the sample client defaults.

## Local build order

The workflow uses the same order the pack should use locally:

1. Check out MMCEGE from the remote repository.
2. Check out MMCE itself only as a Gradle wrapper/reference source; do not edit it.
3. Prefer the requested MMCEGE API branch when it exists, otherwise fall back to the configured public branch.
4. Publish MMCEGE to the local Maven cache.
5. Only then build and validate the one-block addon.

The downstream build uses these dependency hints:

- `modularMachineryCurse=curse.maven:modular-machinery-community-edition-817377:7372953`
- `mmceGuiExtMaven=com.fushu.mmce:MMCEGE:1.2.0`
- `mmcegeLocalJar=E:\mc_modding\MMCEGE\mmce-gui-ext\build\libs\MMCEGE-1.2.0-dev.jar` when you explicitly want a local jar

CI checks out MMCEGE and MMCE separately, publishes MMCEGE to Maven local with the MMCE wrapper, and then builds this addon with `mmceGuiExtMaven`.
Local development should use the same Maven-local route, or pass `mmcegeLocalJar` explicitly. This repo does not assume a fixed `../MMCEGE` sibling path.

Local Maven build command:

```powershell
..\MMCEGE\mmce-src\gradlew.bat -p . --no-daemon --no-parallel --console=plain -PmodularMachineryCurse=curse.maven:modular-machinery-community-edition-817377:7372953 -PmmceGuiExtMaven=com.fushu.mmce:MMCEGE:1.2.0 build --stacktrace
```

Local jar build command:

```powershell
..\MMCEGE\mmce-src\gradlew.bat -p . --no-daemon --no-parallel --console=plain -PmmcegeLocalJar=E:\mc_modding\MMCEGE\mmce-gui-ext\build\libs\MMCEGE-1.2.0-dev.jar build --stacktrace
```

The downstream API note for MMCEGE is documented in:

- `E:\mc_modding\MMCEGE\mmce-gui-ext\docs\DOWNSTREAM_API.md`

## Example files

- `examples/one-block-machine.json` - valid `config/mmce-one-block/machines/` addon definition
- `examples/client.cfg.sample` - Forge-style sample config for the MMCEGE client config
- `examples/smoke/` - minimal backing MMCE machine, recipe, one-block config, and standalone MMCEGE style for runtime smoke

The optional `guiStyle` field is a MMCEGE machine-controller style key. MMCE One Block exposes it to MMCEGE at GUI runtime, so `mmceoneblock:<id>` can have a style independent from the backing MMCE machine id.
Standalone style JSON belongs under `config/mmceguiext/styles/` and uses the usual `registryname` plus `mmce_gui_ext.machineController` shape.

## Runtime smoke

The server smoke fixture is a launch-level check, not a replacement for an in-game GUI pass:

```powershell
.\scripts\prepare-smoke-run.ps1 -AcceptEula
..\MMCEGE\mmce-src\gradlew.bat -p . --no-daemon --no-parallel --console=plain runServer --stacktrace
.\scripts\assert-smoke-log.ps1
```

There is also a local client launch smoke. It prepares the same fixture, starts `runClient`, waits for the log to prove that Forge, MMCEGE, MMCE One Block, and the standalone style fixture loaded, then closes the local dev client:

```powershell
.\scripts\run-client-smoke.ps1
```

For a stronger local server-side integration pass, run the dev validation smoke. It starts `runServer` with `mmceOneBlockDevValidation=true`, places the smoke controller in a real dev world, waits for structure formation, inserts cobblestone, proves redstone power pauses recipe progress, proves blocked output slots retry after being cleared, waits for the backing MMCE recipe to output stone, checks the addon NBT payload, saves and unloads the test chunk, reloads it, verifies persisted inventory and energy payload, verifies the formed comparator level, and destroys the controller through the real world drop path to prove the block item drops and the tile is cleared:

```powershell
.\scripts\run-dev-validation-smoke.ps1
```

In GitHub Actions, push and pull-request runs launch the same fixture, assert the server log, and run a headless client launch smoke by default. Manual workflow runs can set `run_smoke=false` or `run_client_smoke=false` when a fast compile-only check is needed.

The server smoke proves mod loading, backing machine loading, recipe loading, and one-block definition validation. The dev validation smoke additionally proves real server-world placement, structure formation, redstone pause behavior, blocked-output retry, recipe completion, addon NBT payload, chunk unload/reload persistence for inventory and energy payload, comparator formed output, and real destroy/drop cleanup for the smoke fixture. The client launch smoke proves the client can load the addon, MMCEGE, and the standalone style fixture locally and in CI. Unit tests cover the single-block shift-click routing rules and addon-owned NBT payload, but these checks still do not prove real GUI opening or real mouse click handling; those need a client/in-game verification pass.

## Test coverage

- `com.fushu.mmceoneblock.config.OneBlockMachineConfigParserTest`
- `com.fushu.mmceoneblock.api.MMCEGEApiBridgeTest`
- `com.fushu.mmceoneblock.examples.OneBlockExampleFixtureTest`
- `com.fushu.mmceoneblock.common.config.MachineConfigLoaderDirectoryTest`
- `com.fushu.mmceoneblock.common.container.ContainerSingleBlockControllerTest`
- `com.fushu.mmceoneblock.common.registry.MachineRegistryValidationTest`
- `com.fushu.mmceoneblock.common.tile.TileSingleBlockMachineControllerPayloadTest`
- `com.fushu.mmceoneblock.tile.TileSingleBlockMachineControllerTest`
