package com.fushu.mmceoneblock.tile;

import com.fushu.mmceoneblock.common.config.MachineBlockDefinition;
import com.fushu.mmceoneblock.common.config.MachineComponentDefinition;
import com.fushu.mmceoneblock.common.config.MachineDefinition;
import com.fushu.mmceoneblock.common.tile.TileSingleBlockMachineController;
import com.fushu.mmceoneblock.common.tile.MachineComponentStorage;
import com.google.gson.JsonObject;
import github.kasuminova.mmce.common.util.MultiGasTank;
import hellfirepvp.modularmachinery.common.lib.ComponentTypesMM;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.util.IEnergyHandlerAsync;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class TileSingleBlockMachineControllerTest {
    @Test
    public void exposesConfiguredRuntimeComponents() {
        MachineDefinition definition = machineDefinition();
        TileSingleBlockMachineController tile = new TestTile(definition);

        Collection<MachineComponent<?>> components = tile.provideMachineComponents();

        assertEquals(5, components.size());
        assertSame(components, tile.provideMachineComponents());
        assertEquals(tile.getUniqueGroupID(), tile.getMachineComponentGroupId());
        assertEquals(new ResourceLocation("mmceoneblock", "runtime_test"), tile.getMachineControllerGuiStyle());

        Set<Long> groupIds = new HashSet<Long>();
        boolean sawItemInput = false;
        boolean sawFluidInput = false;
        boolean sawGasInput = false;
        boolean sawEnergyInput = false;
        boolean sawEnergyOutput = false;

        for (MachineComponent<?> component : components) {
            assertTrue("component group ids should be stable and non-negative", component.getGroupID() >= tile.getUniqueGroupID());
            assertTrue("component group ids should be unique", groupIds.add(Long.valueOf(component.getGroupID())));

            Object provider = component.getContainerProvider();
            if (provider instanceof IItemHandlerModifiable) {
                sawItemInput = true;
                assertEquals(ComponentTypesMM.COMPONENT_ITEM, component.getComponentType());
                assertEquals(IOType.INPUT, component.getIOType());
                IItemHandlerModifiable handler = (IItemHandlerModifiable) provider;
                assertEquals(2, handler.getSlots());
            } else if (provider instanceof FluidTank) {
                sawFluidInput = true;
                assertEquals(ComponentTypesMM.COMPONENT_FLUID, component.getComponentType());
                assertEquals(IOType.INPUT, component.getIOType());
                assertEquals(16000, ((FluidTank) provider).getCapacity());
            } else if (provider instanceof MultiGasTank) {
                sawGasInput = true;
                assertEquals(ComponentTypesMM.COMPONENT_GAS, component.getComponentType());
                assertEquals(IOType.INPUT, component.getIOType());
                assertEquals(8000, ((MultiGasTank) provider).getCapacity());
            } else if (provider instanceof IEnergyHandlerAsync) {
                assertEquals(ComponentTypesMM.COMPONENT_ENERGY, component.getComponentType());
                assertTrue(
                    "energy components should expose input or output io",
                    component.getIOType() == IOType.INPUT || component.getIOType() == IOType.OUTPUT
                );
                assertEquals(100000L, ((IEnergyHandlerAsync) provider).getMaxEnergy());
                if (component.getIOType() == IOType.INPUT) {
                    sawEnergyInput = true;
                } else {
                    sawEnergyOutput = true;
                }
            }
        }

        assertTrue(sawItemInput);
        assertTrue(sawFluidInput);
        assertTrue(sawGasInput);
        assertTrue(sawEnergyInput);
        assertTrue(sawEnergyOutput);
        assertFalse(groupIds.contains(Long.valueOf(-1L)));
    }

    @Test
    public void publishesFluidRuntimeValuesToCustomData() {
        TileSingleBlockMachineController tile = new TestTile(fluidMachineDefinition());
        FluidTank tank = null;
        for (MachineComponent<?> component : tile.provideMachineComponents()) {
            if (component.getContainerProvider() instanceof FluidTank) {
                tank = (FluidTank) component.getContainerProvider();
                break;
            }
        }

        assertTrue("fluid component should expose a tank", tank != null);

        NBTTagCompound customData = tile.getCustomDataTag();
        assertEquals(0, customData.getInteger("oneblock.fluid.amount"));
        assertEquals(1000, customData.getInteger("oneblock.fluid.capacity"));
        assertEquals(0.0F, customData.getFloat("oneblock.fluid.ratio"), 0.0001F);
        assertEquals("", customData.getString("oneblock.fluid.name"));
        assertEquals("", customData.getString("oneblock.fluid.localizedName"));
    }

    @Test
    public void fluidDefinitionDoesNotBuildEnergyComponent() {
        TileSingleBlockMachineController tile = new TestTile(fluidMachineDefinition());

        for (MachineComponent<?> component : tile.provideMachineComponents()) {
            assertFalse(component.getContainerProvider() instanceof IEnergyHandlerAsync);
        }
    }

    @Test
    public void publishesGasEnergyAndThreadRuntimeValuesToCustomData() {
        TestTile tile = new TestTile(machineDefinition());
        FluidTank fluid = null;
        MultiGasTank gas = null;
        IEnergyHandlerAsync energy = null;
        for (MachineComponent<?> component : tile.provideMachineComponents()) {
            Object provider = component.getContainerProvider();
            if (provider instanceof FluidTank) {
                fluid = (FluidTank) provider;
            } else if (provider instanceof MultiGasTank) {
                gas = (MultiGasTank) provider;
            } else if (provider instanceof IEnergyHandlerAsync
                && component.getIOType() == IOType.INPUT) {
                energy = (IEnergyHandlerAsync) provider;
            }
        }

        assertTrue("fluid component should be available", fluid != null);
        assertTrue("gas component should be available", gas != null);
        assertTrue("energy component should be available", energy != null);
        NBTTagCompound initial = tile.getCustomDataTag();
        assertEquals(0, initial.getInteger("oneblock.fluid.amount"));
        assertEquals(16000, initial.getInteger("oneblock.fluid.capacity"));
        assertEquals(0.0F, initial.getFloat("oneblock.fluid.ratio"), 0.0001F);
        assertEquals(0, initial.getInteger("oneblock.gas.amount"));
        assertEquals(8000, initial.getInteger("oneblock.gas.capacity"));
        assertEquals(0.0F, initial.getFloat("oneblock.gas.ratio"), 0.0001F);
        assertEquals(200000L, initial.getLong("oneblock.energy.capacity"));
        assertEquals(0L, initial.getLong("oneblock.energy.amount"));
        assertFalse(initial.getBoolean("oneblock.state.formed"));
        assertFalse(initial.getBoolean("oneblock.state.working"));
        assertEquals(0, initial.getInteger("oneblock.threads.active"));
        assertEquals(1, initial.getInteger("oneblock.threads.max"));

        energy.setCurrentEnergy(25000L);

        NBTTagCompound updated = tile.getCustomDataTag();
        assertEquals(25000L, updated.getLong("oneblock.energy.amount"));
        assertEquals(0.125F, updated.getFloat("oneblock.energy.ratio"), 0.0001F);
        assertEquals(25000L, updated.getLong("oneblock.component.energy_in.amount"));
        assertEquals(100000L, updated.getLong("oneblock.component.energy_in.capacity"));
        assertEquals(1, tile.storageUpdateRequests);

        NBTTagCompound payload = new NBTTagCompound();
        tile.writeCustomNBT(payload);
        assertTrue(payload.hasKey(MachineComponentStorage.COMPONENTS_NBT_KEY));
        assertFalse(payload.hasKey("oneBlockFluid"));
        assertFalse(payload.hasKey("oneBlockGas"));
        assertFalse(payload.hasKey("oneBlockEnergy"));
    }

    private static MachineDefinition machineDefinition() {
        List<MachineComponentDefinition> components = Arrays.asList(
            new MachineComponentDefinition("item_input", "items_in", true, "Items In", withInt("slots", 2)),
            new MachineComponentDefinition("fluid_input", "fluid_in", true, "Fluid In", withInt("capacity", 16000)),
            new MachineComponentDefinition("gas_input", "gas_in", true, "Gas In", withInt("capacity", 8000)),
            new MachineComponentDefinition("energy_input", "energy_in", true, "Energy In", withInt("capacity", 100000)),
            new MachineComponentDefinition("energy_output", "energy_out", true, "Energy Out", withInt("capacity", 100000))
        );
        return new MachineDefinition(
            "runtime_test",
            new ResourceLocation("modularmachinery", "runtime_test"),
            true,
            "Runtime Test",
            new MachineBlockDefinition("mmceoneblock:single_block_machine_controller", "mmceoneblock:blocks/runtime_test"),
            components,
            "mmceoneblock:runtime_test",
            Paths.get("runtime-test.json")
        );
    }

    private static MachineDefinition fluidMachineDefinition() {
        List<MachineComponentDefinition> components = Arrays.asList(
            new MachineComponentDefinition("item_input", "items_in", true, "Items In", withInt("slots", 2)),
            new MachineComponentDefinition("fluid_input", "fluid_in", true, "Fluid In", withInt("capacity", 1000))
        );
        return new MachineDefinition(
            "fluid_runtime_test",
            new ResourceLocation("modularmachinery", "fluid_runtime_test"),
            true,
            "Fluid Runtime Test",
            new MachineBlockDefinition("mmceoneblock:single_block_machine_controller", "mmceoneblock:blocks/fluid_runtime_test"),
            components,
            "mmceoneblock:fluid_runtime_test",
            Paths.get("fluid-runtime-test.json")
        );
    }

    private static JsonObject withInt(String key, int value) {
        JsonObject object = new JsonObject();
        object.addProperty(key, value);
        return object;
    }

    private static final class TestTile extends TileSingleBlockMachineController {
        private final MachineDefinition definition;
        private int storageUpdateRequests;

        private TestTile(MachineDefinition definition) {
            super(null, definition.getId());
            this.definition = definition;
        }

        @Override
        public MachineDefinition getDefinition() {
            return this.definition;
        }

        @Override
        public void markStorageForUpdate() {
            this.storageUpdateRequests++;
        }

        @Override
        public void markStorageDirty() {
        }
    }
}
