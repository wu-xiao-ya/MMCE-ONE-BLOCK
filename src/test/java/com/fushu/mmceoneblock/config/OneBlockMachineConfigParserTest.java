package com.fushu.mmceoneblock.config;

import com.fushu.mmceoneblock.common.config.MachineConfigException;
import com.fushu.mmceoneblock.common.config.MachineConfigLoader;
import com.fushu.mmceoneblock.common.config.MachineDefinition;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class OneBlockMachineConfigParserTest {
    @Test
    public void parsesStarterMachineExample() {
        MachineDefinition definition = MachineConfigLoader.load(Paths.get("examples", "one-block-machine.json"));

        assertNotNull(definition);
        assertEquals("starter_controller", definition.getId());
        assertEquals(new ResourceLocation("modularmachinery", "starter_machine"), definition.getMachine());
        assertTrue(definition.isEnabled());
        assertEquals("MMCE One Block Starter Controller", definition.getDisplayName());
        assertEquals("mmceoneblock:starter_controller", definition.getGuiStyle());
        assertEquals("mmceoneblock:single_block_machine_controller", definition.getBlock().getModel());
        assertEquals(5, definition.getComponents().size());
        assertEquals("item_input", definition.getComponents().get(0).getType());
        assertEquals(9, definition.getComponents().get(0).getRaw().get("slots").getAsInt());
        assertEquals("fluid_input", definition.getComponents().get(1).getType());
        assertEquals(16000, definition.getComponents().get(1).getRaw().get("capacity").getAsInt());
        assertEquals("gas_input", definition.getComponents().get(3).getType());
        assertEquals(8000, definition.getComponents().get(3).getRaw().get("capacity").getAsInt());
    }

    @Test
    public void defaultsGuiStyleToOwnMachineId() throws IOException {
        Path file = writeTempConfig(validConfigWithoutGuiStyle("plain"));

        try {
            MachineDefinition definition = MachineConfigLoader.load(file);

            assertNotNull(definition);
            assertEquals("mmceoneblock:plain", definition.getGuiStyle());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsMachineDefinitionsWithoutId() throws IOException {
        Path file = writeTempConfig("{"
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":[\"item_input\"],"
            + "\"guiStyle\":\"mmceoneblock:broken\""
            + "}");

        try {
            MachineConfigLoader.load(file);
            fail("missing id should be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("missing id"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsDuplicateComponentIds() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"broken\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":["
            + "{\"type\":\"item_input\",\"id\":\"items\"},"
            + "{\"type\":\"item_output\",\"id\":\"items\"}"
            + "],"
            + "\"guiStyle\":\"mmceoneblock:broken\""
            + "}");

        try {
            MachineConfigLoader.load(file);
            fail("duplicate component ids should be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("duplicate component id"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsDefinitionsWithoutEnabledRuntimeComponents() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"broken\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":[{\"type\":\"item_input\",\"enabled\":false}],"
            + "\"guiStyle\":\"mmceoneblock:broken\""
            + "}");

        try {
            MachineConfigLoader.load(file);
            fail("definitions without enabled runtime components should be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("at least one enabled runtime"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsUnsupportedV1ComponentTypes() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"broken\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":[\"smart_interface\"],"
            + "\"guiStyle\":\"mmceoneblock:broken\""
            + "}");

        try {
            MachineConfigLoader.load(file);
            fail("unsupported v1 components should be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("unsupported v1 component type"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsConflictingIo() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"broken\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":[{\"type\":\"item_input\",\"io\":\"output\"}],"
            + "\"guiStyle\":\"mmceoneblock:broken\""
            + "}");

        try {
            MachineConfigLoader.load(file);
            fail("component type/io conflicts should be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("conflicting io"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsOutOfRangeCapacity() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"broken\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":[{\"type\":\"fluid_input\",\"capacity\":0}],"
            + "\"guiStyle\":\"mmceoneblock:broken\""
            + "}");

        try {
            MachineConfigLoader.load(file);
            fail("out of range capacities should be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("capacity out of range"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsOutOfRangeSlots() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"broken\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":[{\"type\":\"item_input\",\"slots\":0}],"
            + "\"guiStyle\":\"mmceoneblock:broken\""
            + "}");

        try {
            MachineConfigLoader.load(file);
            fail("out of range item slots should be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("slots out of range"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static Path writeTempConfig(String json) throws IOException {
        Path file = Files.createTempFile("mmce-one-block-test-", ".json");
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String validConfigWithoutGuiStyle(String id) {
        return "{"
            + "\"id\":\"" + id + "\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Plain\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":[\"item_input\"]"
            + "}";
    }
}
