# Tests

These tests keep the addon config schema, example files, and MMCEGE API contract aligned.

## Test classes

- `com.fushu.mmceoneblock.config.OneBlockMachineConfigParserTest`
- `com.fushu.mmceoneblock.api.MMCEGEApiBridgeTest`
- `com.fushu.mmceoneblock.examples.OneBlockExampleFixtureTest`
- `com.fushu.mmceoneblock.common.config.MachineConfigLoaderDirectoryTest`
- `com.fushu.mmceoneblock.common.container.ContainerSingleBlockControllerTest`
- `com.fushu.mmceoneblock.common.registry.MachineRegistryValidationTest`
- `com.fushu.mmceoneblock.common.tile.TileSingleBlockMachineControllerPayloadTest`
- `com.fushu.mmceoneblock.tile.TileSingleBlockMachineControllerTest`

## Coverage

- Parser coverage for the `config/mmce-one-block/machines/` sample JSON
- API bootstrap coverage for the MMCEGE dependency path and public bridge methods
- GUI bridge contract coverage for the MMCEGE resizable controller GUI constructor consumed by the addon
- Fixture coverage so the example files do not drift from the docs, including MMCEGE parsing of the smoke text/button/progress/dynamic-visual style
- Directory-loader coverage for invalid files, duplicate ids, disabled definitions, and default GUI style keys
- Container layout and shift-click route coverage for blueprint and configured internal item slots
- Registry validation coverage for missing backing machines and factory-only backing machines
- Addon-owned NBT payload coverage for definition id and energy save/load behavior
- Tile coverage for single-block synthetic machine components and MMCEGE provider bridging
