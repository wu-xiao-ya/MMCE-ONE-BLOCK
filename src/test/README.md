# Tests

These tests keep the addon config schema, example files, and MMCEGE API contract aligned.

## Test classes

- `com.fushu.mmceoneblock.config.OneBlockMachineConfigParserTest`
- `com.fushu.mmceoneblock.api.MMCEGEApiBridgeTest`
- `com.fushu.mmceoneblock.examples.OneBlockExampleFixtureTest`
- `com.fushu.mmceoneblock.common.config.MachineConfigLoaderDirectoryTest`
- `com.fushu.mmceoneblock.common.container.ContainerSingleBlockControllerTest`
- `com.fushu.mmceoneblock.common.container.ContainerSingleBlockFactoryControllerTest`
- `com.fushu.mmceoneblock.common.registry.MachineRegistryValidationTest`
- `com.fushu.mmceoneblock.common.tile.TileSingleBlockMachineControllerPayloadTest`
- `com.fushu.mmceoneblock.common.tile.TileSingleBlockMachineControllerRuntimeComponentTest`
- `com.fushu.mmceoneblock.common.tile.TileSingleBlockFactoryControllerRuntimeTest`
- `com.fushu.mmceoneblock.tile.TileSingleBlockMachineControllerTest`

## Coverage

- Parser coverage for the `config/mmce-one-block/machines/` sample JSON
- API bootstrap coverage for the MMCEGE dependency path and public bridge methods
- GUI bridge contract coverage for both MMCEGE resizable controller constructors consumed by the addon
- Fixture coverage so the example files do not drift from the docs, including MMCEGE parsing of the smoke text/button/progress/dynamic-visual style, the starter/factory default smoke set, the `smartInterfaceEditors` binding, and the optional advanced mana pool example
- Directory-loader coverage for invalid files, duplicate ids, disabled definitions, primitive component rejection, path-only ids, reserved component rejection, and default GUI style keys
- Container layout and shift-click route coverage for blueprint, configured internal item slots, and factory routing
- Registry validation coverage for missing backing machines and factory-only backing machines
- Addon-owned NBT payload coverage for definition id, legacy read/write compatibility, and structured component save/load behavior
- Tile coverage for single-block synthetic machine components, component-id keyed runtime storage, capability limits, and MMCEGE provider bridging
