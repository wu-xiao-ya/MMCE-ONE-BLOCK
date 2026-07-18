package com.fushu.mmceoneblock.common.network;

import com.fushu.mmceoneblock.common.tile.TileSingleBlockFactoryController;
import com.fushu.mmceoneblock.common.tile.TileSingleBlockMachineController;
import hellfirepvp.modularmachinery.common.tiles.TileFactoryController;
import hellfirepvp.modularmachinery.common.tiles.TileMachineController;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class GuiHandlerTest {
    @Test
    public void guiIdDispatchAcceptsOnlyLocalTileImplementations() {
        assertTrue(GuiHandler.acceptsServerGuiElement(
            GuiHandler.GUI_SINGLE_BLOCK_CONTROLLER,
            new TileSingleBlockMachineController()
        ));
        assertFalse(GuiHandler.acceptsServerGuiElement(
            GuiHandler.GUI_SINGLE_BLOCK_CONTROLLER,
            new TileMachineController()
        ));
        assertFalse(GuiHandler.acceptsServerGuiElement(
            GuiHandler.GUI_SINGLE_BLOCK_CONTROLLER,
            new TileSingleBlockFactoryController()
        ));
        assertTrue(GuiHandler.acceptsServerGuiElement(
            GuiHandler.GUI_SINGLE_BLOCK_CONTROLLER,
            new DerivedMachineTile()
        ));

        assertTrue(GuiHandler.acceptsServerGuiElement(
            GuiHandler.GUI_SINGLE_BLOCK_FACTORY_CONTROLLER,
            new TileSingleBlockFactoryController()
        ));
        assertFalse(GuiHandler.acceptsServerGuiElement(
            GuiHandler.GUI_SINGLE_BLOCK_FACTORY_CONTROLLER,
            new TileFactoryController()
        ));
        assertFalse(GuiHandler.acceptsServerGuiElement(
            GuiHandler.GUI_SINGLE_BLOCK_FACTORY_CONTROLLER,
            new TileSingleBlockMachineController()
        ));
        assertTrue(GuiHandler.acceptsServerGuiElement(
            GuiHandler.GUI_SINGLE_BLOCK_FACTORY_CONTROLLER,
            new DerivedFactoryTile()
        ));
    }

    @Test
    public void clientGuiDispatchUsesTheSameTileGate() {
        assertTrue(GuiHandler.acceptsClientGuiElement(
            GuiHandler.GUI_SINGLE_BLOCK_CONTROLLER,
            new TileSingleBlockMachineController()
        ));
        assertFalse(GuiHandler.acceptsClientGuiElement(
            GuiHandler.GUI_SINGLE_BLOCK_CONTROLLER,
            new TileMachineController()
        ));
        assertTrue(GuiHandler.acceptsClientGuiElement(
            GuiHandler.GUI_SINGLE_BLOCK_FACTORY_CONTROLLER,
            new TileSingleBlockFactoryController()
        ));
        assertFalse(GuiHandler.acceptsClientGuiElement(
            GuiHandler.GUI_SINGLE_BLOCK_FACTORY_CONTROLLER,
            new TileFactoryController()
        ));
    }

    @Test(expected = StructurePreparationRequested.class)
    public void serverMachineGuiPreparesStructureBeforeCreatingContainer() {
        GuiHandler.createServerGuiElement(
            GuiHandler.GUI_SINGLE_BLOCK_CONTROLLER,
            new StructurePreparingMachineTile(),
            null
        );
    }

    @Test(expected = StructurePreparationRequested.class)
    public void serverFactoryGuiPreparesStructureBeforeCreatingContainer() {
        GuiHandler.createServerGuiElement(
            GuiHandler.GUI_SINGLE_BLOCK_FACTORY_CONTROLLER,
            new StructurePreparingFactoryTile(),
            null
        );
    }

    @Test
    public void oneBlockControllersGuardSmartInterfaceUpdatesLocally() throws Exception {
        assertSame(
            TileSingleBlockMachineController.class,
            TileSingleBlockMachineController.class
                .getMethod("updateSmartInterfaceValue", String.class, float.class)
                .getDeclaringClass()
        );
        assertSame(
            TileSingleBlockFactoryController.class,
            TileSingleBlockFactoryController.class
                .getMethod("updateSmartInterfaceValue", String.class, float.class)
                .getDeclaringClass()
        );
    }

    @Test
    public void bridgeLookupCachesResolvedClassAndMethods() {
        GuiHandler.ClientGuiBridgeLookup lookup = GuiHandler.getClientGuiBridgeLookup();

        Class<?> bridgeClass = lookup.resolveBridgeClass();
        assertNotNull(bridgeClass);
        assertSame(bridgeClass, lookup.resolveBridgeClass());

        Method machineMethod = lookup.resolveMachineControllerMethod();
        Method machineMethodAgain = lookup.resolveMachineControllerMethod();
        assertNotNull(machineMethod);
        assertSame(machineMethod, machineMethodAgain);
        assertTrue(machineMethod.getName().contains("createSingleBlockControllerGui"));

        Method factoryMethod = lookup.resolveFactoryControllerMethod();
        Method factoryMethodAgain = lookup.resolveFactoryControllerMethod();
        assertNotNull(factoryMethod);
        assertSame(factoryMethod, factoryMethodAgain);
        assertTrue(factoryMethod.getName().contains("createSingleBlockFactoryControllerGui"));
    }

    private static final class DerivedMachineTile extends TileSingleBlockMachineController {
    }

    private static final class DerivedFactoryTile extends TileSingleBlockFactoryController {
    }

    private static final class StructurePreparingMachineTile extends TileSingleBlockMachineController {
        @Override
        public boolean ensureOneBlockStructureReady() {
            throw new StructurePreparationRequested();
        }
    }

    private static final class StructurePreparingFactoryTile extends TileSingleBlockFactoryController {
        @Override
        public boolean ensureOneBlockStructureReady() {
            throw new StructurePreparationRequested();
        }
    }

    private static final class StructurePreparationRequested extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
