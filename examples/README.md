# Examples

This folder holds the pack-facing sample files for MMCE One Block.

## Files

- `one-block-machine.json` - addon machine definition for `config/mmce-one-block/machines/`
- `client.cfg.sample` - client config sample with the MMCEGE keys this pack expects
- `smoke/` - three-machine runtime fixture for local `runClient` / `runServer` checks (`advanced_mana_pool_controller`, `starter_controller`, `starter_factory_controller`)
- `optional/advanced_mana_pool_controller.json` - pack-facing mirror of the smoke advanced mana pool sample showing state models and fluid-driven `textureLevels`

## Install order

1. Build MMCEGE first so the API bridge and parser dependencies are present.
2. Keep the backing MMCE machine JSON in `config/modularmachinery/machinery/`.
3. Copy `one-block-machine.json` into `config/mmce-one-block/machines/`.
4. Keep the smoke `mmce-one-block/machines/` set aligned with the three fixture machines in `examples/smoke/`.
5. Keep the optional advanced mana pool sample in `examples/optional/` synchronized with its smoke fixture copy.
6. Copy `client.cfg.sample` into `config/mmceguiext/client.cfg` if you want the sample defaults.

The example JSON is strict JSON so CI can validate it directly.

For local runtime smoke testing, use:

```powershell
.\scripts\prepare-smoke-run.ps1 -AcceptEula
```

Then launch `runClient` or `runServer` and inspect `run/logs/latest.log`.
