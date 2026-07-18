package com.fushu.mmceoneblock.common.registry;

import com.fushu.mmceoneblock.common.config.MachineBlockDefinition;
import com.fushu.mmceoneblock.common.config.MachineComponentDefinition;
import com.fushu.mmceoneblock.common.config.MachineDefinition;
import com.fushu.mmceoneblock.common.config.OneBlockRuntimeBinding;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MachineRegistryValidationTest {
    @Test
    public void filtersDefinitionsWithUnknownBackingMachines() {
        MachineDefinition known = definition("known", new ResourceLocation("modularmachinery", "known"));
        MachineDefinition missing = definition("missing", new ResourceLocation("modularmachinery", "missing"));

        List<MachineDefinition> filtered = MachineRegistry.filterKnownMachineDefinitions(
            Arrays.asList(known, missing),
            machine -> new ResourceLocation("modularmachinery", "known").equals(machine)
        );

        assertEquals(1, filtered.size());
        assertEquals("known", filtered.get(0).getId());
    }

    @Test
    public void filtersDisabledDefinitionsBeforeRegistration() {
        MachineDefinition enabled = definition("enabled", new ResourceLocation("modularmachinery", "enabled"), true);
        MachineDefinition disabled = definition("disabled", new ResourceLocation("modularmachinery", "disabled"), false);

        List<MachineDefinition> filtered =
            MachineRegistry.filterEnabledMachineDefinitions(Arrays.asList(enabled, disabled));

        assertEquals(1, filtered.size());
        assertEquals(enabled, filtered.get(0));
    }

    @Test
    public void autoDefinitionUsesFactoryBindingWhenBackingMachineIsLoaded() {
        DynamicMachine factoryMachine = new DynamicMachine("auto_factory_runtime");
        factoryMachine.setFactoryOnly(true);
        MachineDefinition definition = definition(
            "auto_factory_runtime",
            new ResourceLocation("modularmachinery", "auto_factory_runtime")
        );

        OneBlockRuntimeBinding binding =
            OneBlockRuntimeBinding.fromDefinition(definition).resolve(factoryMachine);

        assertTrue(MachineRegistry.shouldUseFactoryTile(binding));
        assertEquals(OneBlockRuntimeBinding.TileKind.FACTORY, binding.getTileKind());
    }

    private static MachineDefinition definition(String id, ResourceLocation machine) {
        return definition(id, machine, true);
    }

    private static MachineDefinition definition(String id, ResourceLocation machine, boolean enabled) {
        return new MachineDefinition(
            id,
            machine,
            enabled,
            id,
            new MachineBlockDefinition(
                "mmceoneblock:single_block_machine_controller",
                "mmceoneblock:blocks/" + id
            ),
            Collections.singletonList(
                new MachineComponentDefinition(
                    "item_input",
                    "items_in",
                    true,
                    null,
                    new com.google.gson.JsonObject()
                )
            ),
            "mmceoneblock:" + id,
            Paths.get(id + ".json")
        );
    }
}
