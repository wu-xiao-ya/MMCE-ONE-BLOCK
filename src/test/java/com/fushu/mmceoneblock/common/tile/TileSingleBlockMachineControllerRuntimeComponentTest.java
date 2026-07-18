package com.fushu.mmceoneblock.common.tile;

import com.fushu.mmceoneblock.common.config.MachineBlockDefinition;
import com.fushu.mmceoneblock.common.config.MachineComponentDefinition;
import com.fushu.mmceoneblock.common.config.MachineDefinition;
import com.google.gson.JsonObject;
import github.kasuminova.mmce.common.util.MultiGasTank;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.util.IEnergyHandlerAsync;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public final class TileSingleBlockMachineControllerRuntimeComponentTest {
    @Test
    @Ignore("Requires the live Forge fluid registry; covered by server smoke")
    public void componentsWithDifferentIdsOwnIndependentFluidStorage() {
        TestTile tile = new TestTile(definition("independent", Arrays.asList(
            component("fluid_input", "water_in", 1000),
            component("fluid_input", "steam_in", 2000)
        )));

        List<FluidTank> tanks = fluidTanks(tile.provideMachineComponents());
        assertEquals(2, tanks.size());
        assertNotSame(tanks.get(0), tanks.get(1));
        tanks.get(0).fill(new FluidStack(FluidRegistry.WATER, 400), true);

        assertEquals(400, tanks.get(0).getFluidAmount());
        assertEquals(0, tanks.get(1).getFluidAmount());
        assertEquals(400L, tile.getCustomDataTag().getLong("oneblock.component.water_in.amount"));
        assertEquals(1000L, tile.getCustomDataTag().getLong("oneblock.component.water_in.capacity"));
        assertEquals(0L, tile.getCustomDataTag().getLong("oneblock.component.steam_in.amount"));
        assertEquals(3000L, tile.getCustomDataTag().getLong("oneblock.fluid.capacity"));

        NBTTagCompound payload = new NBTTagCompound();
        tile.writeOneBlockPayload(payload);
        assertTrue(payload.hasKey(MachineComponentStorage.COMPONENTS_NBT_KEY));
        assertTrue(payload.getCompoundTag(MachineComponentStorage.COMPONENTS_NBT_KEY).hasKey("water_in"));
        assertEquals(
            400,
            payload.getCompoundTag(MachineComponentStorage.COMPONENTS_NBT_KEY)
                .getCompoundTag("water_in").getInteger("Amount")
        );
        assertFalse(payload.hasKey("oneBlockFluid"));
    }

    @Test
    public void componentReorderingRestoresEnergyById() {
        MachineDefinition original = definition("energy_order", Arrays.asList(
            component("energy_input", "energy_a", 1000),
            component("energy_output", "energy_b", 2000)
        ));
        TestTile first = new TestTile(original);
        List<IEnergyHandlerAsync> originalHandlers = energies(first.provideMachineComponents());
        originalHandlers.get(0).setCurrentEnergy(150L);
        originalHandlers.get(1).setCurrentEnergy(900L);
        NBTTagCompound payload = new NBTTagCompound();
        first.writeOneBlockPayload(payload);

        MachineDefinition reordered = definition("energy_order", Arrays.asList(
            component("energy_output", "energy_b", 2000),
            component("energy_input", "energy_a", 1000)
        ));
        TestTile second = new TestTile(reordered);
        second.readOneBlockPayload(payload);
        List<IEnergyHandlerAsync> reorderedHandlers = energies(second.provideMachineComponents());

        assertEquals(900L, reorderedHandlers.get(0).getCurrentEnergy());
        assertEquals(150L, reorderedHandlers.get(1).getCurrentEnergy());
    }

    @Test
    public void legacyEnergyMigratesIntoFirstMatchingComponent() {
        TestTile tile = new TestTile(definition("legacy", Arrays.asList(
            component("energy_input", "primary", 1000),
            component("energy_output", "secondary", 1000)
        )));
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setString("definitionId", "legacy");
        legacy.setLong("oneBlockEnergy", 750L);

        tile.readOneBlockPayload(legacy);
        List<IEnergyHandlerAsync> handlers = energies(tile.provideMachineComponents());

        assertEquals(750L, handlers.get(0).getCurrentEnergy());
        assertEquals(0L, handlers.get(1).getCurrentEnergy());
        NBTTagCompound migrated = new NBTTagCompound();
        tile.writeOneBlockPayload(migrated);
        assertTrue(migrated.hasKey(MachineComponentStorage.COMPONENTS_NBT_KEY));
        assertFalse(migrated.hasKey("oneBlockEnergy"));
        assertEquals(
            750L,
            migrated.getCompoundTag(MachineComponentStorage.COMPONENTS_NBT_KEY)
                .getCompoundTag("primary").getLong("energy")
        );
    }

    @Test
    @Ignore("Requires the live Forge fluid registry; covered by server smoke")
    public void legacyFluidMigratesIntoFirstMatchingComponent() {
        TestTile tile = new TestTile(definition("legacy_fluid", Arrays.asList(
            component("fluid_input", "primary", 1000),
            component("fluid_output", "secondary", 1000)
        )));
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setString("definitionId", "legacy_fluid");
        FluidTank tank = new FluidTank(1000);
        tank.fill(new FluidStack(FluidRegistry.WATER, 400), true);
        legacy.setTag("oneBlockFluid", tank.writeToNBT(new NBTTagCompound()));

        tile.readOneBlockPayload(legacy);
        List<FluidTank> tanks = fluidTanks(tile.provideMachineComponents());

        assertEquals(400, tanks.get(0).getFluidAmount());
        assertEquals(0, tanks.get(1).getFluidAmount());
        NBTTagCompound migrated = new NBTTagCompound();
        tile.writeOneBlockPayload(migrated);
        assertTrue(migrated.getCompoundTag(MachineComponentStorage.COMPONENTS_NBT_KEY).hasKey("primary"));
        assertFalse(migrated.hasKey("oneBlockFluid"));
    }

    @Test
    @Ignore("Requires the live Mekanism gas registry; covered by server smoke")
    public void legacyGasMigratesIntoFirstMatchingComponent() {
        TestTile tile = new TestTile(definition("legacy_gas", Arrays.asList(
            component("gas_input", "primary", 1000),
            component("gas_output", "secondary", 1000)
        )));
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setString("definitionId", "legacy_gas");
        MultiGasTank tank = new MultiGasTank(1000, 1);
        tank.setGasInSlot(0, new GasStack(sampleGas(), 350));
        tank.writeToNBT(legacy, "oneBlockGas");

        tile.readOneBlockPayload(legacy);
        List<MultiGasTank> tanks = gasTanks(tile.provideMachineComponents());

        assertEquals(350, gasAmount(tanks.get(0)));
        assertEquals(0, gasAmount(tanks.get(1)));
        NBTTagCompound migrated = new NBTTagCompound();
        tile.writeOneBlockPayload(migrated);
        assertTrue(migrated.getCompoundTag(MachineComponentStorage.COMPONENTS_NBT_KEY).hasKey("primary"));
        assertFalse(migrated.hasKey("oneBlockGas"));
    }

    @Test
    public void typeConflictIsRetainedAsUnclaimedNbt() {
        TestTile tile = new TestTile(definition("type_change", Arrays.asList(
            component("energy_input", "shared", 1000)
        )));
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("definitionId", "type_change");
        NBTTagCompound components = new NBTTagCompound();
        NBTTagCompound oldFluid = new NBTTagCompound();
        oldFluid.setString("type", "fluid");
        components.setTag("shared", oldFluid);
        payload.setTag(MachineComponentStorage.COMPONENTS_NBT_KEY, components);

        tile.readOneBlockPayload(payload);
        NBTTagCompound output = new NBTTagCompound();
        tile.writeOneBlockPayload(output);

        assertEquals(
            "energy",
            output.getCompoundTag(MachineComponentStorage.COMPONENTS_NBT_KEY)
                .getCompoundTag("shared").getString("type")
        );
        assertEquals(
            "fluid",
            output.getCompoundTag(MachineComponentStorage.UNCLAIMED_COMPONENTS_NBT_KEY)
                .getCompoundTag("shared").getString("type")
        );
    }

    @Test
    @Ignore("Requires the live Minecraft item registry; covered by server smoke")
    public void shrinkingItemComponentPreservesOverflowItemsAsUnclaimedData() {
        TestTile first = new TestTile(definition("item_shrink", Arrays.asList(
            itemComponent("items", 2)
        )));
        IItemHandlerModifiable firstHandler = itemHandler(first.provideMachineComponents());
        firstHandler.setStackInSlot(0, new ItemStack(Blocks.STONE, 3));
        firstHandler.setStackInSlot(1, new ItemStack(Blocks.DIRT, 5));
        NBTTagCompound payload = new NBTTagCompound();
        first.writeOneBlockPayload(payload);

        TestTile second = new TestTile(definition("item_shrink", Arrays.asList(
            itemComponent("items", 1)
        )));
        second.readOneBlockPayload(payload);
        NBTTagCompound output = new NBTTagCompound();
        second.writeOneBlockPayload(output);

        assertEquals(3, itemHandler(second.provideMachineComponents()).getStackInSlot(0).getCount());
        assertEquals(
            1,
            output.getCompoundTag(MachineComponentStorage.UNCLAIMED_COMPONENTS_NBT_KEY)
                .getCompoundTag("items").getTagList("items", 10).tagCount()
        );
    }

    private static List<FluidTank> fluidTanks(Collection<MachineComponent<?>> components) {
        java.util.ArrayList<FluidTank> out = new java.util.ArrayList<FluidTank>();
        for (MachineComponent<?> component : components) {
            if (component.getContainerProvider() instanceof FluidTank) {
                out.add((FluidTank) component.getContainerProvider());
            }
        }
        return out;
    }

    private static List<MultiGasTank> gasTanks(Collection<MachineComponent<?>> components) {
        java.util.ArrayList<MultiGasTank> out = new java.util.ArrayList<MultiGasTank>();
        for (MachineComponent<?> component : components) {
            if (component.getContainerProvider() instanceof MultiGasTank) {
                out.add((MultiGasTank) component.getContainerProvider());
            }
        }
        return out;
    }

    private static List<IEnergyHandlerAsync> energies(Collection<MachineComponent<?>> components) {
        java.util.ArrayList<IEnergyHandlerAsync> out = new java.util.ArrayList<IEnergyHandlerAsync>();
        for (MachineComponent<?> component : components) {
            if (component.getContainerProvider() instanceof IEnergyHandlerAsync) {
                out.add((IEnergyHandlerAsync) component.getContainerProvider());
            }
        }
        return out;
    }

    private static IItemHandlerModifiable itemHandler(Collection<MachineComponent<?>> components) {
        for (MachineComponent<?> component : components) {
            if (component.getContainerProvider() instanceof IItemHandlerModifiable) {
                return (IItemHandlerModifiable) component.getContainerProvider();
            }
        }
        throw new AssertionError("expected item component");
    }

    private static int gasAmount(MultiGasTank tank) {
        return tank.getGasInSlot(0) == null ? 0 : tank.getGasInSlot(0).amount;
    }

    private static mekanism.api.gas.Gas sampleGas() {
        assertFalse(GasRegistry.getRegisteredGasses().isEmpty());
        return GasRegistry.getRegisteredGasses().get(0);
    }

    private static MachineComponentDefinition component(String type, String id, long capacity) {
        JsonObject raw = new JsonObject();
        raw.addProperty("capacity", capacity);
        return new MachineComponentDefinition(type, id, true, id, raw);
    }

    private static MachineComponentDefinition itemComponent(String id, int slots) {
        JsonObject raw = new JsonObject();
        raw.addProperty("slots", slots);
        return new MachineComponentDefinition("item_input", id, true, id, raw);
    }

    private static MachineDefinition definition(
        String id,
        List<MachineComponentDefinition> components
    ) {
        return new MachineDefinition(
            id,
            new ResourceLocation("modularmachinery", id),
            true,
            id,
            new MachineBlockDefinition(
                "mmceoneblock:single_block_machine_controller",
                "mmceoneblock:blocks/" + id
            ),
            components,
            "mmceoneblock:" + id,
            Paths.get(id + ".json")
        );
    }

    private static final class TestTile extends TileSingleBlockMachineController {
        private final MachineDefinition definition;

        private TestTile(MachineDefinition definition) {
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
