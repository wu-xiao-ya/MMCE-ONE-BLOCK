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
- `config/mmceguiext/styles/starter_controller.json` - standalone MMCEGE style keyed by `mmceoneblock:starter_controller`.

The style file is intentionally outside `config/modularmachinery/machinery/` so
packs do not need to edit the backing MMCE machine JSON just to style a
one-block controller.

This smoke fixture is a launch-level gate. It proves that Forge loads the addon,
MMCE loads the backing machine and recipe, and MMCE One Block validates the
single-block definition against MMCE. The client launch smoke additionally proves
that the client can load the addon and standalone MMCEGE style fixture. The dev
validation smoke proves real server-side placement, structure formation, recipe
execution, addon NBT payload, chunk reload persistence, comparator formed output,
and real destroy/drop cleanup for this fixture. It does not prove real GUI
opening, shift-click mouse handling, or visual GUI style rendering.
