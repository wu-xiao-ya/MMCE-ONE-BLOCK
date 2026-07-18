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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
        assertTrue(output.hasKey("oneBlockEnergy"));
        assertEquals(750L, output.getLong("oneBlockEnergy"));
        assertFalse(output.hasKey("oneBlockFluid"));
        assertFalse(output.hasKey("oneBlockGas"));
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

    @Test
    public void transientCacheRebuildPreservesEnergyBeforeAndAfterPayloadRestore() {
        TestTile original = new TestTile(machineDefinition(1000L));
        energy(original).setCurrentEnergy(640L);
        original.clearTransientComponentCache();

        NBTTagCompound payload = new NBTTagCompound();
        original.writeOneBlockPayload(payload);

        assertEquals(
            640L,
            payload.getCompoundTag(MachineComponentStorage.COMPONENTS_NBT_KEY)
                .getCompoundTag("energy").getLong("energy")
        );
        assertEquals(640L, payload.getLong("oneBlockEnergy"));

        TestTile restored = new TestTile(machineDefinition(1000L));
        restored.clearTransientComponentCache();
        restored.readOneBlockPayload(payload);

        assertEquals(640L, energy(restored).getCurrentEnergy());
        assertEquals(
            640L,
            restored.getCustomDataTag().getLong("oneblock.component.energy.amount")
        );
    }

    @Test
    public void concurrentEnergyUpdatesKeepModernAndLegacyPayloadsConsistent() throws Exception {
        final TestTile tile = new TestTile(machineDefinition(1000L));
        final IEnergyHandlerAsync handler = energy(tile);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread updater = new Thread(() -> {
            await(start, failure);
            for (int i = 0; i < 500 && failure.get() == null; i++) {
                handler.setCurrentEnergy(i % 1001);
            }
        }, "one-block-energy-updater");
        Thread serializer = new Thread(() -> {
            await(start, failure);
            for (int i = 0; i < 500 && failure.get() == null; i++) {
                NBTTagCompound payload = new NBTTagCompound();
                tile.writeOneBlockPayload(payload);
                long modern = payload.getCompoundTag(MachineComponentStorage.COMPONENTS_NBT_KEY)
                    .getCompoundTag("energy").getLong("energy");
                long legacy = payload.getLong("oneBlockEnergy");
                if (modern != legacy) {
                    failure.compareAndSet(
                        null,
                        new AssertionError("modern=" + modern + ", legacy=" + legacy)
                    );
                }
            }
        }, "one-block-energy-serializer");

        updater.start();
        serializer.start();
        start.countDown();
        updater.join(TimeUnit.SECONDS.toMillis(20));
        serializer.join(TimeUnit.SECONDS.toMillis(20));

        assertFalse("energy updater did not finish", updater.isAlive());
        assertFalse("energy serializer did not finish", serializer.isAlive());
        assertNull(failure.get());
    }

    private static void await(CountDownLatch start, AtomicReference<Throwable> failure) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                failure.compareAndSet(null, new AssertionError("start latch timed out"));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            failure.compareAndSet(null, ex);
        }
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

        @Override
        public void markStorageDirty() {
        }

        @Override
        public void markStorageForUpdate() {
        }
    }
}
