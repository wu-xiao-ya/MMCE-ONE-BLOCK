package com.fushu.mmceoneblock.api;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class MMCEGEApiBridgeTest {
    @Test
    public void requiresMmcegeVersionAndApiLevelOne() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> mod = loader.loadClass("com.fushu.mmceguiext.MMCEGuiExt");
        Class<?> api = loader.loadClass("com.fushu.mmceguiext.MMCEGuiExtApi");

        assertEquals("1.3.0", mod.getField("VERSION").get(null));
        assertEquals(1, api.getField("API_LEVEL").getInt(null));
        assertTrue(((Boolean) api.getMethod("isApiLevelAtLeast", Integer.TYPE)
            .invoke(null, Integer.valueOf(1))).booleanValue());
        assertFalse(((Boolean) api.getMethod("isApiLevelAtLeast", Integer.TYPE)
            .invoke(null, Integer.valueOf(2))).booleanValue());
    }

    @Test
    public void publishesOnlyStableMultiComponentProviderMethods() throws Exception {
        Class<?> provider = Thread.currentThread().getContextClassLoader()
            .loadClass("com.fushu.mmceguiext.api.machine.IMultiMachineComponentProvider");

        Method provide = provider.getMethod("provideMachineComponents");
        Method group = provider.getMethod("getMachineComponentGroupId");
        assertEquals(Collection.class, provide.getReturnType());
        assertEquals(Long.TYPE, group.getReturnType());
        try {
            provider.getMethod("provideComponents");
            fail("legacy provideComponents alias must not remain in API level 1");
        } catch (NoSuchMethodException expected) {
            // Expected stable API surface.
        }
    }

    @Test
    public void exposesGuiBridgeWithoutInternalGuiReturnTypes() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> bridge = loader.loadClass("com.fushu.mmceguiext.api.gui.MachineGuiBridge");
        Class<?> guiScreen = loader.loadClass("net.minecraft.client.gui.GuiScreen");
        Class<?> controller =
            loader.loadClass("hellfirepvp.modularmachinery.common.container.ContainerController");
        Class<?> factory =
            loader.loadClass("hellfirepvp.modularmachinery.common.container.ContainerFactoryController");

        assertEquals(
            guiScreen,
            bridge.getMethod("createMachineControllerScreen", controller).getReturnType()
        );
        assertEquals(
            guiScreen,
            bridge.getMethod("createFactoryControllerScreen", factory).getReturnType()
        );
    }

    @Test
    public void exposesControllerStyleKeyProviderWithoutInternalStyleApi() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> provider = loader.loadClass("com.fushu.mmceguiext.api.gui.IMachineGuiStyleProvider");
        assertNotNull(provider.getMethod("getMachineControllerGuiStyle"));
        try {
            loader.loadClass("com.fushu.mmceguiext.api.gui.MachineGuiStyleApi");
            fail("MachineGuiStyleApi must not expose internal ControllerStyle");
        } catch (ClassNotFoundException expected) {
            // JSON plus style-key providers are the stable downstream contract.
        }
    }

    @Test
    public void exposesImmutableSlotLayoutContracts() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> provider = loader.loadClass("com.fushu.mmceguiext.api.gui.SlotLayoutProvider");
        assertNotNull(provider.getMethod("getSlotGroups"));
        assertNotNull(provider.getMethod("getPlayerInventory"));

        Class<?> slotGroup = loader.loadClass("com.fushu.mmceguiext.api.gui.SlotGroupDescriptor");
        assertNotNull(slotGroup.getConstructor(
            String.class,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            String.class,
            Boolean.TYPE
        ));
        assertNotNull(slotGroup.getMethod("getSlotIndices"));
        assertFinalFields(slotGroup);

        Class<?> playerInv = loader.loadClass("com.fushu.mmceguiext.api.gui.PlayerInventoryDescriptor");
        assertNotNull(playerInv.getConstructor(
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Boolean.TYPE
        ));
        assertFinalFields(playerInv);
    }

    private static void assertFinalFields(Class<?> type) {
        for (Field field : type.getFields()) {
            assertTrue(
                type.getName() + "." + field.getName() + " should be final",
                java.lang.reflect.Modifier.isFinal(field.getModifiers())
            );
        }
    }
}
