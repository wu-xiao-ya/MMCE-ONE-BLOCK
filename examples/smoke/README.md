# Smoke Fixture

This folder contains a minimal runtime fixture for local `runClient` / `runServer`
checks. Copy the `config/` tree into the dev run directory before launching.

Use the helper script from the repo root:

```powershell
.\scripts\prepare-smoke-run.ps1 -AcceptEula
```

After `runServer` reaches `Done`, assert the log from the repo root:

```powershell
.\scripts\assert-smoke-log.ps1
```

For a local client launch smoke, use:

```powershell
.\scripts\run-client-smoke.ps1
```

That script starts `runClient`, waits for the client log to show that Forge,
MMCEGE, MMCE One Block, and this fixture loaded, then closes the dev client.
For the stronger headless GUI pass, use:

```powershell
.\scripts\run-client-smoke.ps1 -GuiValidation
```

That variant also launches an integrated smoke world, places
`starter_controller`, opens its controller GUI through the Forge GUI handler when
the client receives the placed tile normally, and waits until MMCEGE's resizable
controller GUI is the current client screen.

For a stronger server-side integration pass, use:

```powershell
.\scripts\run-dev-validation-smoke.ps1
```

That script starts `runServer` with the development validator enabled. The
validator places `starter_controller` in the dev world, waits for the structure
to form, inserts cobblestone, waits for the MMCE recipe to produce stone, checks
the addon NBT payload, and verifies the formed comparator level.

The fixture includes:

- `config/modularmachinery/machinery/starter_machine.json` - a tiny backing MMCE machine so MMCE One Block can resolve `machine: starter_machine`.
- `config/modularmachinery/recipes/starter_machine_cobblestone_to_stone.json` - a one-input, one-output recipe for smoke testing.
- `config/mmce-one-block/machines/starter_controller.json` - the single-block controller definition.
- `config/mmce-one-block/machines/starter_factory_controller.json` - a forced factory-controller definition for exercising the true MMCE factory container path.
- `../optional/advanced_mana_pool_controller.json` - optional advanced mana-pool sample for model-state and fluid-ratio coverage; it stays outside the default smoke set.
- `config/mmceguiext/styles/starter_controller.json` - standalone MMCEGE style keyed by `mmceoneblock:starter_controller`, with text, button, progress bar, dynamic visual, slot group, and player inventory entries.
- `config/mmceguiext/styles/factory_controller.json` - standalone MMCEGE factory style keyed by `mmceoneblock:factory_controller`, with input/output/blueprint layout, thread tooltip mode, and a subGUI entry button.
- `config/mmceguiext/subgui/factory_threads.json` - draggable modal factory-thread panel using MMCEGE's native visible thread queue.

The default smoke tree registers the starter, factory, and advanced mana-pool
definitions. The advanced sample is mirrored under `examples/optional/` so the
pack-facing copy cannot drift from the runtime fixture.

The starter style uses `smartInterfaceEditors` and a dynamic visual whose
source is `oneblock.component.energy_in.amount` with `maxSource` pointing at the
matching capacity key, so the GUI smoke covers both the virtual Smart Interface
field and the amount/capacity binding.

The style file is intentionally outside `config/modularmachinery/machinery/` so
packs do not need to edit the backing MMCE machine JSON just to style a
one-block controller.

This smoke fixture is a launch-level gate. It proves that Forge loads the addon,
MMCE loads the backing machine and recipe, and MMCE One Block validates the
single-block definition against MMCE. The client GUI smoke additionally proves
that the client can load the addon and standalone MMCEGE style fixtures without
missing models, display both MMCEGE resizable controller GUIs, and preserve the
normal/factory blueprint and internal slot coordinates. It also checks the
dynamic capacity `maxSource`, performs a virtual Smart Interface write through
the runtime GUI button, and captures screenshots for both screens.
Unit tests parse the style fixture through MMCEGE's machine-style parser so the
text, button, progress bar, dynamic visual, slot layout, player inventory,
factory tooltip, and subGUI entry cannot silently drift out of schema. The dev validation smoke proves real server-side placement, structure
formation, recipe execution, addon NBT payload, chunk reload persistence,
comparator formed output, and real destroy/drop cleanup for this fixture. It
does not prove shift-click mouse handling, pixel-level visual GUI rendering, or
the fallback mode's network GUI packet path.
