package com.fushu.mmceoneblock.api;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class MMCEGEApiBridgeTest {
    @Test
    public void publishesMmcegeComponentProviderApiBeforeLocalPackValidation() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> provider = loader.loadClass("com.fushu.mmceguiext.api.machine.IMultiMachineComponentProvider");

        Method provide = provider.getMethod("provideMachineComponents");
        Method group = provider.getMethod("getMachineComponentGroupId");

        assertEquals(Collection.class, provide.getReturnType());
        assertEquals(Long.TYPE, group.getReturnType());
    }

    @Test
    public void exposesGuiStyleRegistrationMethods() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> api = loader.loadClass("com.fushu.mmceguiext.api.gui.MachineGuiStyleApi");
        Class<?> resourceLocation = loader.loadClass("net.minecraft.util.ResourceLocation");
        Class<?> controllerStyle = loader.loadClass(
            "com.fushu.mmceguiext.client.config.MachineGuiStyleManager$ControllerStyle"
        );

        assertNotNull(api.getMethod("newControllerStyle"));
        assertNotNull(api.getMethod("resolveMachineControllerStyle", resourceLocation));
        assertNotNull(api.getMethod("resolveFactoryControllerStyle", resourceLocation));
        assertNotNull(api.getMethod("registerMachineControllerStyle", resourceLocation, controllerStyle));
        assertNotNull(api.getMethod("registerFactoryControllerStyle", resourceLocation, controllerStyle));
        assertNotNull(api.getMethod("clearExternalStyles"));
    }

    @Test
    public void keepsApiContractStableAcrossExampleUpdates() throws Exception {
        Class<?> provider = Thread.currentThread()
            .getContextClassLoader()
            .loadClass("com.fushu.mmceguiext.api.machine.IMultiMachineComponentProvider");

        assertNotNull(provider.getMethod("provideComponents"));
    }

    @Test
    public void exposesControllerGuiStyleProviderApi() throws Exception {
        Class<?> provider = Thread.currentThread()
            .getContextClassLoader()
            .loadClass("com.fushu.mmceguiext.api.gui.IMachineGuiStyleProvider");

        assertNotNull(provider.getMethod("getMachineControllerGuiStyle"));
    }
}
