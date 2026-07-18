package com.fushu.mmceoneblock.common.tile;

import com.fushu.mmceoneblock.common.config.MachineBlockDefinition;
import com.fushu.mmceoneblock.common.config.MachineComponentDefinition;
import com.fushu.mmceoneblock.common.config.MachineDefinition;
import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.util.IEnergyHandlerAsync;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class TileSingleBlockFactoryControllerRuntimeTest {
    @Test
    public void factorySharesRuntimeComponentsAndPayloadContract() {
        TestFactoryTile tile = new TestFactoryTile(factoryDefinition());

        Collection<MachineComponent<?>> first = tile.provideMachineComponents();
        assertEquals(4, first.size());
        assertSame(first, tile.provideMachineComponents());
        assertEquals(tile.getUniqueGroupID(), tile.getMachineComponentGroupId());
        assertEquals(1, tile.getItemInputSlotCount());
        assertTrue(tile.isItemInputInventorySlot(1));

        IEnergyHandlerAsync energy = energyHandler(first);
        energy.setCurrentEnergy(600L);

        NBTTagCompound output = new NBTTagCompound();
        tile.writeOneBlockPayload(output);
        assertEquals("factory_runtime_test", output.getString("definitionId"));
        assertEquals(
            600L,
            output.getCompoundTag(MachineComponentStorage.COMPONENTS_NBT_KEY)
                .getCompoundTag("energy_in").getLong("energy")
        );
        assertTrue(output.hasKey("oneBlockFluid"));
        assertFalse(output.hasKey("oneBlockGas"));
        assertTrue(output.hasKey("oneBlockEnergy"));
        assertEquals(600L, output.getLong("oneBlockEnergy"));
    }

    private static MachineDefinition factoryDefinition() {
        return new MachineDefinition(
            "factory_runtime_test",
            new ResourceLocation("modularmachinery", "factory_runtime_test"),
            true,
            "factory_runtime_test",
            new MachineBlockDefinition(
                "mmceoneblock:single_block_machine_controller",
                "mmceoneblock:blocks/factory_runtime_test"
            ),
            Arrays.asList(
                component("item_input", "items_in", "slots", 1),
                component("item_output", "items_out", "slots", 1),
                component("fluid_output", "fluid_out", "capacity", 1000),
                component("energy_input", "energy_in", "capacity", 1000)
            ),
            "mmceoneblock:factory_runtime_test",
            Paths.get("factory_runtime_test.json")
        );
    }

    private static MachineComponentDefinition component(String type,
                                                        String id,
                                                        String key,
                                                        int value) {
        JsonObject object = new JsonObject();
        object.addProperty(key, value);
        return new MachineComponentDefinition(type, id, true, id, object);
    }

    private static IEnergyHandlerAsync energyHandler(Collection<MachineComponent<?>> components) {
        for (MachineComponent<?> component : components) {
            if (component.getContainerProvider() instanceof IEnergyHandlerAsync) {
                return (IEnergyHandlerAsync) component.getContainerProvider();
            }
        }
        throw new AssertionError("expected energy component");
    }

    private static final class TestFactoryTile extends TileSingleBlockFactoryController {
        private final MachineDefinition definition;

        private TestFactoryTile(MachineDefinition definition) {
            super(null, definition.getId());
            this.definition = definition;
        }

        @Override
        public MachineDefinition getDefinition() {
            return definition;
        }

        @Override
        public void markStorageDirty() {
        }

        @Override
        public void markStorageForUpdate() {
        }
    }
}
