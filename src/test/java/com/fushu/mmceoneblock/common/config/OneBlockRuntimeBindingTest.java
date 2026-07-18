package com.fushu.mmceoneblock.common.config;

import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OneBlockRuntimeBindingTest {
    @Test
    public void startsPendingAndKeepsParsedRuntimeLayout() {
        MachineDefinition definition = definition("pending", "mmceoneblock:pending", null);

        OneBlockRuntimeBinding binding = OneBlockRuntimeBinding.fromDefinition(definition);

        assertTrue(binding.isPending());
        assertEquals(OneBlockRuntimeBinding.ResolutionStatus.PENDING, binding.getResolutionStatus());
        assertEquals("mmceoneblock:pending", binding.getGuiStyle().toString());
        assertEquals(1, binding.getComponentLayout().size());
        assertEquals("item", binding.getComponentLayout().get(0).getKind());
        assertEquals("input", binding.getComponentLayout().get(0).getIo());
    }

    @Test
    public void resolvesFactoryOnlyBackingsIntoFactoryTiles() {
        MachineDefinition definition = definition("factory", "mmceoneblock:factory", null);
        DynamicMachine machine = new DynamicMachine("factory");
        machine.setFactoryOnly(true);

        OneBlockRuntimeBinding binding = OneBlockRuntimeBinding.fromDefinition(definition).resolve(machine);

        assertFalse(binding.isPending());
        assertEquals(OneBlockRuntimeBinding.ResolutionStatus.RESOLVED, binding.getResolutionStatus());
        assertTrue(binding.usesFactoryTile());
        assertEquals(OneBlockRuntimeBinding.TileKind.FACTORY, binding.getTileKind());
    }

    @Test
    public void flagsMachineControllerMismatchWhenBackingIsFactoryOnly() {
        MachineDefinition definition = new MachineDefinition(
            "machine_only",
            new ResourceLocation("modularmachinery", "machine_only"),
            true,
            "Machine Only",
            new MachineBlockDefinition("mmceoneblock:single_block_machine_controller", "mmceoneblock:blocks/machine_only"),
            Collections.singletonList(new MachineComponentDefinition(
                "item_input",
                "items",
                true,
                "Items",
                "item",
                "input",
                Integer.valueOf(9),
                null,
                new com.google.gson.JsonObject()
            )),
            "mmceoneblock:machine_only",
            Paths.get("machine_only.json"),
            ControllerType.MACHINE,
            null
        );
        DynamicMachine machine = new DynamicMachine("factory");
        machine.setFactoryOnly(true);

        OneBlockRuntimeBinding binding = OneBlockRuntimeBinding.fromDefinition(definition).resolve(machine);

        assertEquals(OneBlockRuntimeBinding.ResolutionStatus.FACTORY_ONLY_MISMATCH, binding.getResolutionStatus());
        assertTrue(binding.hasResolutionError());
        assertTrue(binding.usesFactoryTile());
    }

    private static MachineDefinition definition(String id, String guiStyle, String factoryGuiStyle) {
        return new MachineDefinition(
            id,
            new ResourceLocation("modularmachinery", id),
            true,
            id,
            new MachineBlockDefinition("mmceoneblock:single_block_machine_controller", "mmceoneblock:blocks/" + id),
            Collections.singletonList(new MachineComponentDefinition(
                "item_input",
                "items",
                true,
                "Items",
                new com.google.gson.JsonObject()
            )),
            guiStyle,
            Paths.get(id + ".json"),
            ControllerType.AUTO,
            factoryGuiStyle
        );
    }
}
