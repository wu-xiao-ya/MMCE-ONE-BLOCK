package com.fushu.mmceoneblock.client;

import com.fushu.mmceoneblock.common.container.ContainerSingleBlockController;
import com.fushu.mmceoneblock.common.container.ContainerSingleBlockFactoryController;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ClientGuiBridgeTest {
    @Test
    public void exposesInternalBridgeHelpersForTesting() throws Exception {
        assertNotNull(ClientGuiBridge.class.getDeclaredMethod(
            "createSingleBlockControllerGuiInternal",
            ContainerSingleBlockController.class
        ));
        assertNotNull(ClientGuiBridge.class.getDeclaredMethod(
            "createSingleBlockFactoryControllerGuiInternal",
            ContainerSingleBlockFactoryController.class
        ));
    }

    @Test
    public void keepsMmcegeDirectCallAndVanillaFallbackContract() throws IOException {
        String source = new String(
            Files.readAllBytes(Paths.get(
                "src",
                "main",
                "java",
                "com",
                "fushu",
                "mmceoneblock",
                "client",
                "ClientGuiBridge.java"
            )),
            StandardCharsets.UTF_8
        );

        assertTrue(source.contains("MachineGuiBridge.createMachineControllerScreen"));
        assertTrue(source.contains("MachineGuiBridge.createFactoryControllerScreen"));
        assertTrue(source.contains("LinkageError"));
        assertFalse(source.contains("return gui != null ? gui : container;"));
    }
}
