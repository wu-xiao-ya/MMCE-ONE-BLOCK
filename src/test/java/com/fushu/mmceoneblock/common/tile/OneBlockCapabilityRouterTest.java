package com.fushu.mmceoneblock.common.tile;

import com.fushu.mmceoneblock.common.config.MachineBlockDefinition;
import com.fushu.mmceoneblock.common.config.MachineComponentDefinition;
import com.fushu.mmceoneblock.common.config.MachineDefinition;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class OneBlockCapabilityRouterTest {
    @Test
    public void routesConfiguredKindsAndEnforcesFluidEnergyDirections() {
        TestTile tile = new TestTile(fullDefinition());
        MachineComponentStorage storage = new MachineComponentStorage(tile);
        storage.provideMachineComponents();
        OneBlockCapabilityRouter router = new OneBlockCapabilityRouter(tile, storage);

        assertTrue(router.hasItemStorage());
        assertTrue(router.hasFluidStorage());
        assertTrue(router.hasEnergyStorage());

        IItemHandlerModifiable items = router.getItemHandler();
        assertNotNull(items);
        assertEquals(2, items.getSlots());

        IFluidHandler fluid = router.getFluidHandler();
        assertNotNull(fluid);
        assertFalse(fluid.getTankProperties()[0].canFill());
        assertTrue(fluid.getTankProperties()[0].canDrain());

        IEnergyStorage energy = router.getEnergyStorage();
        assertNotNull(energy);
        assertTrue(energy.canReceive());
        assertFalse(energy.canExtract());
    }

    @Test
    public void missingKindsDoNotCreateHandlers() {
        TestTile tile = new TestTile(itemOnlyDefinition());
        MachineComponentStorage storage = new MachineComponentStorage(tile);
        storage.provideMachineComponents();
        OneBlockCapabilityRouter router = new OneBlockCapabilityRouter(tile, storage);

        assertTrue(router.hasItemStorage());
        assertFalse(router.hasFluidStorage());
        assertFalse(router.hasEnergyStorage());
        assertNotNull(router.getItemHandler());
        assertNull(router.getFluidHandler());
        assertNull(router.getEnergyStorage());
    }

    private static MachineDefinition fullDefinition() {
        return definition("capability_router", Arrays.asList(
            component("item_input", "items_in", "slots", 1),
            component("item_output", "items_out", "slots", 1),
            component("fluid_output", "fluid_out", "capacity", 1000),
            component("energy_input", "energy_in", "capacity", 1000)
        ));
    }

    private static MachineDefinition itemOnlyDefinition() {
        return definition("capability_router_item", Collections.singletonList(
            component("item_input", "items_in", "slots", 1)
        ));
    }

    private static MachineComponentDefinition component(String type,
                                                        String id,
                                                        String key,
                                                        int value) {
        JsonObject raw = new JsonObject();
        raw.addProperty(key, value);
        return new MachineComponentDefinition(type, id, true, id, raw);
    }

    private static MachineDefinition definition(String id,
                                                java.util.List<MachineComponentDefinition> components) {
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
