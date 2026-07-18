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
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TileSingleBlockMachineControllerPayloadTest {
    @Test
    public void oneBlockPayloadRoundTripsDefinitionAndEnergyWithoutParentBootstrap() {
        TestTile tile = new TestTile(machineDefinition(1000L));

        NBTTagCompound input = new NBTTagCompound();
        input.setString("definitionId", "runtime_test");
        input.setLong("oneBlockEnergy", 750L);

        tile.readOneBlockPayload(input);

        assertEquals("runtime_test", tile.getDefinitionId());
        assertEquals(750L, energy(tile).getCurrentEnergy());

        NBTTagCompound output = new NBTTagCompound();
        tile.writeOneBlockPayload(output);

        assertEquals("runtime_test", output.getString("definitionId"));
        assertEquals(
            750L,
            output.getCompoundTag(MachineComponentStorage.COMPONENTS_NBT_KEY)
                .getCompoundTag("energy").getLong("energy")
        );
        assertFalse(output.hasKey("oneBlockEnergy"));
    }

    @Test
    public void oneBlockPayloadClampsEnergyToConfiguredCapacity() {
        TestTile tile = new TestTile(machineDefinition(1000L));

        NBTTagCompound input = new NBTTagCompound();
        input.setString("definitionId", "runtime_test");
        input.setLong("oneBlockEnergy", 5000L);

        tile.readOneBlockPayload(input);

        assertEquals(1000L, energy(tile).getCurrentEnergy());
    }

    private static IEnergyHandlerAsync energy(TileSingleBlockMachineController tile) {
        for (MachineComponent<?> component : tile.provideMachineComponents()) {
            Object provider = component.getContainerProvider();
            if (provider instanceof IEnergyHandlerAsync) {
                return (IEnergyHandlerAsync) provider;
            }
        }
        throw new AssertionError("expected energy component");
    }

    private static MachineDefinition machineDefinition(long energyCapacity) {
        return new MachineDefinition(
            "runtime_test",
            new ResourceLocation("modularmachinery", "runtime_test"),
            true,
            "Runtime Test",
            new MachineBlockDefinition("mmceoneblock:single_block_machine_controller", "mmceoneblock:blocks/runtime_test"),
            Collections.singletonList(new MachineComponentDefinition(
                "energy_output",
                "energy",
                true,
                "Energy",
                withLong("capacity", energyCapacity)
            )),
            "mmceoneblock:runtime_test",
            Paths.get("runtime-test.json")
        );
    }

    private static JsonObject withLong(String key, long value) {
        JsonObject object = new JsonObject();
        object.addProperty(key, value);
        return object;
    }

    private static final class TestTile extends TileSingleBlockMachineController {
        private final MachineDefinition definition;

        private TestTile(MachineDefinition definition) {
            super(null, definition.getId());
            this.definition = definition;
        }

        @Override
        public MachineDefinition getDefinition() {
            return this.definition;
        }
    }
}
