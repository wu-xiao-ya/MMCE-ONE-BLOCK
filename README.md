# MMCE One Block

MMCE One Block is a config-first companion repo for a one-block progression pack built on top of MMCEGE.
It registers configured one-block controller blocks that delegate recipes to existing MMCE machines while exposing item, fluid, gas, and energy components from one tile.

The upstream MMCE code stays read-only. The extension surface this addon needs lives in MMCEGE, and this repo only consumes that API.

## What is in this repo

- `examples/` - sample one-block machine JSON, client config templates, and the optional advanced mana pool sample
- `src/main/java/` - Forge registration, config loading, controller block, tile, container, and GUI bridge
- `src/test/` - parser, example drift, and MMCEGE API contract tests
- `.github/workflows/` - CI that builds MMCEGE first, then validates this addon

## Recommended flow

1. Check out MMCEGE at the exact SHA `394cc57269518c7ef343cf2d9a4c119c62241929` first.
2. Keep the backing MMCE machine JSON in `config/modularmachinery/machinery/`.
3. Copy `examples/one-block-machine.json` into `config/mmce-one-block/machines/`.
4. Copy `examples/client.cfg.sample` to `config/mmceguiext/client.cfg` if you want the sample client defaults.

## Local build order

The workflow uses the same order the pack should use locally:

1. Check out the requested MMCEGE exact SHA from the remote repository.
2. Check out MMCE itself only as a Gradle wrapper/reference source; do not edit it.
3. Fail the build if the requested MMCEGE SHA is missing; never silently fall back to a branch or `main`.
4. Publish MMCEGE to the local Maven cache.
5. Only then build and validate the one-block addon.

The downstream build uses these dependency hints:

- `modularMachineryCurse=curse.maven:modular-machinery-community-edition-817377:7372953`
- `mmceGuiExtMaven=com.fushu.mmce:MMCEGE:1.3.0`
- `mmcegeLocalJar=E:\mc_modding\MMCEGE\mmce-gui-ext\build\libs\MMCEGE-1.3.0.jar` when you explicitly want a local jar

CI checks out MMCEGE and MMCE separately, publishes MMCEGE to Maven local with the MMCE wrapper, and then builds this addon with `mmceGuiExtMaven`.
Local development should use the same Maven-local route, or pass `mmcegeLocalJar` explicitly. This repo does not assume a fixed `../MMCEGE` sibling path.

Local Maven build command:

```powershell
.\gradlew.bat --no-daemon --no-parallel --console=plain -PmodularMachineryCurse=curse.maven:modular-machinery-community-edition-817377:7372953 -PmmceGuiExtMaven=com.fushu.mmce:MMCEGE:1.3.0 build --stacktrace
```

Local jar build command:

```powershell
.\gradlew.bat --no-daemon --no-parallel --console=plain -PmmcegeLocalJar=E:\mc_modding\MMCEGE\mmce-gui-ext\build\libs\MMCEGE-1.3.0.jar build --stacktrace
```

The downstream API note for MMCEGE is documented in:

- `E:\mc_modding\MMCEGE\mmce-gui-ext\docs\DOWNSTREAM_API.md`

## Example files

- `examples/one-block-machine.json` - valid `config/mmce-one-block/machines/` addon definition
- `examples/client.cfg.sample` - Forge-style sample config for the MMCEGE client config
- `examples/smoke/` - minimal backing MMCE machine, recipe, starter/factory one-block configs, and standalone MMCEGE controller styles for runtime smoke
- `examples/optional/advanced_mana_pool_controller.json` - pack-facing mirror of the advanced mana pool smoke definition with state models and fluid-driven `textureLevels`

The optional `guiStyle` field is a MMCEGE machine-controller style key. Factory one-block machines can also set `factoryGuiStyle`; it is used by the factory controller GUI before falling back to `guiStyle`.
Standalone style JSON belongs under `config/mmceguiext/styles/` and uses the usual `registryname` plus either `mmce_gui_ext.machineController` or `mmce_gui_ext.factoryController`. The smoke styles intentionally include text, button, progress bar, dynamic visual, slot group, player inventory, and factory thread tooltip entries so parser coverage exercises the same feature families a pack-facing one-block controller is expected to use.
The starter smoke style also includes `smartInterfaceEditors`, and its dynamic visual binds `amount` to `maxSource` capacity so GUI validation covers the virtual Smart Interface field and a real capacity source.
`controllerType` controls the runtime tile:

- `auto` (default): uses a normal tile unless the backing MMCE machine is `factory-only`.
- `machine`: forces the normal controller path; validation rejects this when the backing machine is `factory-only`.
- `factory`: forces the factory controller path, preserving MMCE factory threads, core threads, NBT, and factory GUI dispatch.

Runtime capabilities are exposed only for enabled component kinds declared in the one-block JSON. Fluid components expose a Forge fluid handler for pipes and also accept normal fluid containers by right-clicking the controller block.
In the one-block controller GUI, configured item input slots accept manual insertion and Shift-click insertion; configured item output slots are extract-only so items cannot be placed into slots that recipes do not scan.
Every component must declare a unique non-empty `id`. Each item/fluid/gas/energy ID owns an independent runtime storage and an independent MMCE `MachineComponent`; reordering components restores contents by ID.

External capabilities combine the configured components while preserving direction:

- input item/fluid/gas/energy components accept insertion, filling, gas receive, or energy receive only;
- output components allow extraction, draining, gas draw, or energy extraction only;
- when both directions are configured, the combined capability is bidirectional through the matching independent components.

New saves use `oneBlockComponents.<id>` and retain deleted/type-changed data under `oneBlockUnclaimedComponents`. Legacy `oneBlockFluid`, `oneBlockGas`, and `oneBlockEnergy` payloads are read into the first matching component and continue to be written alongside the structured payload for old-save and downgrade compatibility.

Reserved but unsupported in `0.1.0`: `parallel_controller`, `upgrade_bus`, and physical `smart_interface`. Use factory thread limits, fixed configured capacities, and the backing MMCE machine's native virtual Smart Interface with MMCEGE `smartInterfaceEditors`.

One-block controllers publish both aggregate and per-component runtime data through MMCE controller `customData`, so MMCEGE styles can render without MMCE One Block owning GUI rendering:

- `oneblock.fluid.amount`
- `oneblock.fluid.capacity`
- `oneblock.fluid.ratio`
- `oneblock.fluid.name`
- `oneblock.fluid.localizedName`
- `oneblock.component.<id>.amount`
- `oneblock.component.<id>.capacity`
- `oneblock.component.<id>.ratio`
- item: `slots`, `occupiedSlots`, `itemCount`
- fluid/gas: `name`, `localizedName`

Minimal MMCEGE dynamic visual source for a fluid bar:

```json
{
  "id": "oneblock_fluid",
  "x": 246,
  "y": 26,
  "width": 12,
  "height": 70,
  "source": {
    "type": "customData",
    "key": "oneblock.component.fluid_in.amount",
    "default": 0,
    "min": 0,
    "max": 1,
    "maxSource": {
      "type": "customData",
      "key": "oneblock.component.fluid_in.capacity",
      "default": 1
    },
    "clamp": true
  },
  "renderer": {
    "type": "fill",
    "direction": "up",
    "backgroundColor": "33000000",
    "fillColor": "FFFF80D8",
    "borderColor": "66FFFFFF"
  }
}
```

## Container API

### ContainerSingleBlockController (SlotLayoutProvider)

`ContainerSingleBlockController` implements `SlotLayoutProvider` so that MMCEGE'"'"'s GUI framework can read slot positions without hard-coding coordinates:

- `getSlotGroups()` returns three groups: `"input"` grid (5 columns, rows based on configured input count), `"output"` grid (5 columns, below inputs), and `"blueprint"` single slot.
- `getPlayerInventory()` positions the 3x9 main inventory at (8, 131) and the hotbar at (8, 189).

Style authors can override these positions by setting `slotGroups` and `playerInventory` in the standalone style JSON under `mmce_gui_ext.machineController`.

Standalone subGUI overlays belong in `config/mmceguiext/subgui/` and are merged by the same `registryname` used by `guiStyle` or `factoryGuiStyle`. ONE BLOCK does not add a second container for these panels; MMCEGE keeps the parent container and owns modal/replace state, drag handling, hotkeys, and close actions.

### ContainerSingleBlockFactoryController

For factory-capable one-block machines (machines with `maxThreads > 1`), a factory container is available via GUI ID `2`:

- Extends MMCE `ContainerFactoryController`, so MMCEGE and vanilla MMCE factory GUIs receive the real factory container type.
- Implements `SlotLayoutProvider` for API-driven slot layout.
- Publishes configured input/output slot groups plus the MMCE factory blueprint slot; the default factory style places the blueprint at (255, 8) and the player inventory at (112, 131).
- The client GUI dispatches through MMCEGE `GuiFactoryControllerResizable` first and falls back to vanilla `GuiFactoryController`.
- Thread queue display can be moved to hover text by setting `threadQueueMode` to `"tooltip"` or `threadTooltip` to `true` in `mmce_gui_ext.factoryController`.
- A full thread panel can be supplied as a modal subGUI in `config/mmceguiext/subgui/`; the smoke fixture uses `factory_threads` and opens it from the factory main style.

### GUI bridge dispatch

`GuiHandler` now dispatches both GUI IDs:

- `GUI_SINGLE_BLOCK_CONTROLLER` (1): normal `ContainerSingleBlockController`
- `GUI_SINGLE_BLOCK_FACTORY_CONTROLLER` (2): `ContainerSingleBlockFactoryController`

`ClientGuiBridge` provides two factory methods:

- `createSingleBlockControllerGui(ContainerSingleBlockController)` - existing, unchanged
- `createSingleBlockFactoryControllerGui(ContainerSingleBlockFactoryController)` - new factory path

Each first calls the stable MMCEGE `MachineGuiBridge.create*Screen(...)` API, whose public return type is `GuiScreen`, then falls back to the vanilla MMCE GUI on a linkage mismatch.

## Slot layout style configuration

Standalone style JSON files under `config/mmceguiext/styles/` can define slot group positions for the controller GUI:

```json
{
  "machineController": {
    "slotGroups": [
      {
        "id": "input",
        "x": 8,
        "y": 17,
        "rows": 2,
        "columns": 5,
        "spacingX": 18,
        "spacingY": 18,
        "shiftTarget": "playerInventory",
        "enabled": true
      }
    ],
    "playerInventory": {
      "x": 8,
      "y": 131,
      "hotbarX": 8,
      "hotbarY": 189,
      "mainStart": 0,
      "hotbarStart": 27,
      "enabled": true
    }
  }
}
```

The provider publishes explicit container indices: player main/hotbar `0/27`, blueprint `36`, input `37`, and output immediately after input. JSON can also set `firstSlot + slotCount` or sparse `slotIndices[]`. When a style group and provider group share an ID, JSON geometry overrides field-by-field while omitted index fields inherit the provider. `slotWidth` / `slotHeight` remain aliases for `spacingX` / `spacingY`. `shiftTarget` is compatibility metadata only and does not control server-side Shift-click routing.

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

For the stronger client GUI pass used by CI, add `-GuiValidation`. This launches a headless integrated smoke world, validates the normal controller through the Forge GUI handler, then opens the factory controller through the public MMCEGE bridge. It checks both resizable screens, explicit slot coordinates, dynamic capacity bounds, a real virtual Smart Interface write, and captures screenshots for both GUIs:

```powershell
.\scripts\run-client-smoke.ps1 -GuiValidation
```

For a stronger local server-side integration pass, run the dev validation smoke. It starts `runServer` with `mmceOneBlockDevValidation=true`, places the smoke controller in a real dev world, waits for structure formation, inserts cobblestone, proves redstone power pauses recipe progress, proves blocked output slots retry after being cleared, waits for the backing MMCE recipe to output stone, checks the addon NBT payload, saves and unloads the test chunk, reloads it, verifies persisted inventory and energy payload, verifies the formed comparator level, and destroys the controller through the real world drop path to prove the block item drops and the tile is cleared:

```powershell
.\scripts\run-dev-validation-smoke.ps1
```

In GitHub Actions, push and pull-request runs launch the same fixture, assert the server log, and run a headless client smoke with GUI validation by default. Manual workflow runs can set `run_smoke=false` or `run_client_smoke=false` when a fast compile-only check is needed.

The default smoke set covers `starter_controller` and `starter_factory_controller`. The server smoke proves mod loading, backing machine loading, recipe loading, and validation of both one-block definitions. The dev validation smoke additionally proves real server-world placement, structure formation, redstone pause behavior, blocked-output retry, recipe completion, new-format addon NBT, chunk unload/reload persistence, comparator output, and destroy/drop cleanup. The client GUI smoke proves both public MMCEGE GUI bridges, normal/factory container types, blueprint/input slot coordinates, the `maxSource` capacity binding, runtime style contents, a server-observed virtual Smart Interface write, and screenshot output for both screens. Unit tests cover component ID validation, independent storage, legacy migration, unclaimed data, capability direction, normal/factory Shift-click routing, API contracts, model resources, and style fixtures.

## Test coverage

- `com.fushu.mmceoneblock.config.OneBlockMachineConfigParserTest`
- `com.fushu.mmceoneblock.api.MMCEGEApiBridgeTest`
- `com.fushu.mmceoneblock.examples.OneBlockExampleFixtureTest`
- `com.fushu.mmceoneblock.common.config.MachineConfigLoaderDirectoryTest`
- `com.fushu.mmceoneblock.common.container.ContainerSingleBlockControllerTest`
- `com.fushu.mmceoneblock.common.container.ContainerSingleBlockFactoryControllerTest`
- `com.fushu.mmceoneblock.common.registry.MachineRegistryValidationTest`
- `com.fushu.mmceoneblock.common.tile.TileSingleBlockMachineControllerPayloadTest`
- `com.fushu.mmceoneblock.tile.TileSingleBlockMachineControllerTest`
