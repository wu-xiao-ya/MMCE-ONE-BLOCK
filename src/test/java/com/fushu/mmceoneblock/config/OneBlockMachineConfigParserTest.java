package com.fushu.mmceoneblock.config;

import com.fushu.mmceoneblock.common.config.MachineConfigException;
import com.fushu.mmceoneblock.common.config.MachineConfigLoader;
import com.fushu.mmceoneblock.common.config.MachineBlockDefinition;
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
import static org.junit.Assert.assertNull;
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
    public void parsesFactoryExampleAndTypedComponentFields() {
        MachineDefinition definition = MachineConfigLoader.load(
            Paths.get("examples", "smoke", "config", "mmce-one-block", "machines", "starter_factory_controller.json")
        );

        assertNotNull(definition);
        assertEquals("mmceoneblock:starter_controller", definition.getGuiStyle());
        assertEquals("mmceoneblock:factory_controller", definition.getFactoryGuiStyle());
        assertEquals("item", definition.getComponents().get(0).getKind());
        assertEquals("input", definition.getComponents().get(0).getIo());
        assertEquals(Integer.valueOf(9), definition.getComponents().get(0).getSlots());
        assertEquals("energy", definition.getComponents().get(2).getKind());
        assertEquals("input", definition.getComponents().get(2).getIo());
        assertEquals(Long.valueOf(10000L), definition.getComponents().get(2).getCapacity());
        assertTrue(definition.getComponents().get(2).getRaw().has("capacity"));
    }

    @Test
    public void rejectsInvalidFactoryGuiStyle() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"broken_factory_style\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":[{\"type\":\"item_input\",\"id\":\"items_in\"}],"
            + "\"guiStyle\":\"mmceoneblock:broken\","
            + "\"factoryGuiStyle\":\"not a valid resource location\""
            + "}");

        try {
            MachineConfigLoader.load(file);
            fail("invalid factory gui styles should be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("factoryGuiStyle"));
        } finally {
            Files.deleteIfExists(file);
        }
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
    public void normalizesComponentIdsByTrimmingAndLowercasing() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"normalized_component_ids\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Normalized\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":["
            + "{\"type\":\"item_input\",\"id\":\"  ITEMS_IN  \",\"slots\":1},"
            + "{\"type\":\"fluid_input\",\"id\":\"  Fluid_In  \",\"capacity\":1000}"
            + "],"
            + "\"guiStyle\":\"mmceoneblock:normalized_component_ids\""
            + "}");

        try {
            MachineDefinition definition = MachineConfigLoader.load(file);

            assertNotNull(definition);
            assertEquals("items_in", definition.getComponents().get(0).getId());
            assertEquals("fluid_in", definition.getComponents().get(1).getId());
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
            + "\"components\":[{\"type\":\"item_input\",\"id\":\"items_in\"}],"
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
    public void rejectsMachineDefinitionsWithoutMachine() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"broken\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":[{\"type\":\"item_input\",\"id\":\"items_in\"}],"
            + "\"guiStyle\":\"mmceoneblock:broken\""
            + "}");

        try {
            MachineConfigLoader.load(file);
            fail("missing machine should be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("missing machine"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsMachineDefinitionsWithEmptyMachine() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"broken\","
            + "\"machine\":\" \","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":[{\"type\":\"item_input\",\"id\":\"items_in\"}],"
            + "\"guiStyle\":\"mmceoneblock:broken\""
            + "}");

        try {
            MachineConfigLoader.load(file);
            fail("empty machine should be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("missing machine"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsNamespacedMachineIds() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"mmceoneblock:broken\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":[{\"type\":\"item_input\",\"id\":\"items_in\"}],"
            + "\"guiStyle\":\"mmceoneblock:broken\""
            + "}");

        try {
            MachineConfigLoader.load(file);
            fail("namespaced ids should be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("path-only id"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsNamespacedComponentIds() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"broken_component_namespace\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":[{\"type\":\"item_input\",\"id\":\"mmceoneblock:items_in\"}],"
            + "\"guiStyle\":\"mmceoneblock:broken_component_namespace\""
            + "}");

        try {
            MachineConfigLoader.load(file);
            fail("namespaced component ids should be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("path-only component id"));
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
    public void rejectsDuplicateComponentIdsAfterNormalization() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"broken_normalized\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":["
            + "{\"type\":\"item_input\",\"id\":\"  ITEMS_IN  \"},"
            + "{\"type\":\"item_output\",\"id\":\"items_in\"}"
            + "],"
            + "\"guiStyle\":\"mmceoneblock:broken_normalized\""
            + "}");

        try {
            MachineConfigLoader.load(file);
            fail("duplicate component ids should be rejected after normalization");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("duplicate component id"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void acceptsLegacyPrimitiveComponentEntriesWithStableIds() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"primitive_component\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Legacy\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":[\"item_input\",\"item_input\"],"
            + "\"guiStyle\":\"mmceoneblock:primitive_component\""
            + "}");

        try {
            MachineDefinition definition = MachineConfigLoader.load(file);
            assertNotNull(definition);
            assertEquals(2, definition.getComponents().size());
            assertEquals("item_input", definition.getComponents().get(0).getId());
            assertEquals("item_input_2", definition.getComponents().get(1).getId());
            assertEquals("item", definition.getComponents().get(0).getKind());
            assertEquals("input", definition.getComponents().get(0).getIo());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsMissingComponentIdsIncludingDisabledComponents() throws IOException {
        Path enabled = writeTempConfig("{"
            + "\"id\":\"missing_component_id\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":[{\"type\":\"item_input\"}],"
            + "\"guiStyle\":\"mmceoneblock:broken\""
            + "}");
        Path disabled = writeTempConfig("{"
            + "\"id\":\"missing_disabled_component_id\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":["
            + "{\"type\":\"item_input\",\"id\":\"items_in\"},"
            + "{\"type\":\"item_output\",\"enabled\":false}"
            + "],"
            + "\"guiStyle\":\"mmceoneblock:broken\""
            + "}");

        try {
            MachineConfigLoader.load(enabled);
            fail("enabled components must require ids");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("missing required component id"));
        } finally {
            Files.deleteIfExists(enabled);
        }
        try {
            MachineConfigLoader.load(disabled);
            fail("disabled components must still reserve a stable id");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("missing required component id"));
        } finally {
            Files.deleteIfExists(disabled);
        }
    }

    @Test
    public void rejectsDisabledReservedComponentTypes() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"disabled_reserved_component\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\"},"
            + "\"components\":[{\"type\":\"smart_interface\",\"id\":\"smart\",\"enabled\":false}],"
            + "\"guiStyle\":\"mmceoneblock:disabled_reserved_component\""
            + "}");

        try {
            MachineConfigLoader.load(file);
            fail("disabled reserved components should still be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("unsupported 0.1.0 component type"));
            assertTrue(ex.getMessage().contains("reserved for a later MMCEGE special-component API"));
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
            + "\"components\":[{\"type\":\"item_input\",\"id\":\"items_in\",\"enabled\":false}],"
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
            + "\"components\":[{\"type\":\"smart_interface\",\"id\":\"smart\"}],"
            + "\"guiStyle\":\"mmceoneblock:broken\""
            + "}");

        try {
            MachineConfigLoader.load(file);
            fail("unsupported reserved components should be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("unsupported 0.1.0 component type"));
            assertTrue(ex.getMessage().contains("virtual Smart Interface"));
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
            + "\"components\":[{\"type\":\"item_input\",\"id\":\"items_in\",\"io\":\"output\"}],"
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
            + "\"components\":[{\"type\":\"fluid_input\",\"id\":\"fluid_in\",\"capacity\":0}],"
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
            + "\"components\":[{\"type\":\"item_input\",\"id\":\"items_in\",\"slots\":0}],"
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

    @Test
    public void parsesModelTexturesStateVariantsAndTextureLevels() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"rendered\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Rendered\","
            + "\"block\":{"
            + "\"model\":\"mmceoneblock:advanced_mana_pool_controller\","
            + "\"itemModel\":\"mmceoneblock:advanced_mana_pool_controller\","
            + "\"texture\":\"mmceoneblock:blocks/legacy_all\","
            + "\"textures\":{\"base\":\"mmceoneblock:blocks/advanced_mana_pool_base\",\"fluid\":\"mmceoneblock:blocks/advanced_mana_pool_fluid\"},"
            + "\"states\":{"
            + "\"unformed\":{\"textures\":{\"all\":\"mmceoneblock:blocks/unformed\"}},"
            + "\"working\":{\"model\":\"mmceoneblock:advanced_mana_pool_controller\",\"textures\":{\"port\":\"mmceoneblock:blocks/advanced_mana_pool_port\"}}"
            + "},"
            + "\"textureLevels\":["
            + "{\"content\":\"fluid\",\"minFillRatio\":0.25,\"textures\":{\"fluid\":\"mmceoneblock:blocks/advanced_mana_pool_fluid\"}},"
            + "{\"content\":\"energy\",\"minFillRatio\":0.75,\"model\":\"mmceoneblock:advanced_mana_pool_controller\"}"
            + "]"
            + "},"
            + "\"components\":[{\"type\":\"item_input\",\"id\":\"items_in\"}]"
            + "}");

        try {
            MachineDefinition definition = MachineConfigLoader.load(file);
            MachineBlockDefinition block = definition.getBlock();

            assertEquals("mmceoneblock:blocks/legacy_all", block.getTexture());
            assertEquals("mmceoneblock:blocks/advanced_mana_pool_base", block.getTextures().get("base"));
            assertEquals("mmceoneblock:advanced_mana_pool_controller", block.getItemModel());
            assertEquals(
                "mmceoneblock:blocks/unformed",
                block.getStateVariant(MachineBlockDefinition.RenderState.UNFORMED).getTextures().get("all")
            );
            assertNull(block.getStateVariant(MachineBlockDefinition.RenderState.IDLE));
            assertEquals(
                "mmceoneblock:blocks/advanced_mana_pool_port",
                block.getStateVariant(MachineBlockDefinition.RenderState.WORKING).getTextures().get("port")
            );
            assertEquals(2, block.getTextureLevels().size());
            assertEquals("fluid", block.getTextureLevels().get(0).getContent());
            assertEquals(0.25D, block.getTextureLevels().get(0).getMinFillRatio(), 0.0001D);
            assertEquals("energy", block.getTextureLevels().get(1).getContent());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsTextureSlotsThatAreNotObjects() throws IOException {
        Path file = writeTempConfig("{"
            + "\"id\":\"broken_textures\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{"
            + "\"model\":\"mmceoneblock:single_block_machine_controller\","
            + "\"textures\":\"mmceoneblock:blocks/starter\""
            + "},"
            + "\"components\":[{\"type\":\"item_input\",\"id\":\"items_in\"}]"
            + "}");

        try {
            MachineConfigLoader.load(file);
            fail("non-object textures should be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("invalid block.textures"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void parsesOptionalAdvancedManaPoolExampleWithTextureLevels() {
        MachineDefinition definition = MachineConfigLoader.load(
            Paths.get(
                "examples", "optional",
                "advanced_mana_pool_controller.json"
            )
        );

        assertNotNull(definition);
        assertEquals("advanced_mana_pool_controller", definition.getId());
        assertEquals("MMCE One Block Advanced Mana Pool Controller", definition.getDisplayName());
        assertEquals("mmceoneblock:starter_controller", definition.getGuiStyle());

        MachineBlockDefinition block = definition.getBlock();
        assertEquals("mmceoneblock:advanced_mana_pool_controller_empty", block.getModel());
        assertEquals("mmceoneblock:advanced_mana_pool_controller_full", block.getItemModel());
        assertEquals("mmceoneblock:blocks/advanced_mana_pool_side", block.getTexture());
        assertEquals("mmceoneblock:blocks/advanced_mana_pool_base", block.getTextures().get("base"));
        assertEquals("mmceoneblock:blocks/advanced_mana_pool_side", block.getTextures().get("side"));
        assertEquals("mmceoneblock:blocks/advanced_mana_pool_fluid", block.getTextures().get("fluid"));
        assertEquals("mmceoneblock:blocks/advanced_mana_pool_side", block.getTextures().get("all"));
        assertEquals("mmceoneblock:advanced_mana_pool_controller_empty", block.getStateVariant(MachineBlockDefinition.RenderState.UNFORMED).getModel());
        assertEquals("mmceoneblock:advanced_mana_pool_controller_empty", block.getStateVariant(MachineBlockDefinition.RenderState.IDLE).getModel());
        assertEquals("mmceoneblock:advanced_mana_pool_controller_empty", block.getStateVariant(MachineBlockDefinition.RenderState.WORKING).getModel());
        assertEquals(4, block.getTextureLevels().size());
        String[] models = {
            "mmceoneblock:advanced_mana_pool_controller_25",
            "mmceoneblock:advanced_mana_pool_controller_50",
            "mmceoneblock:advanced_mana_pool_controller_75",
            "mmceoneblock:advanced_mana_pool_controller_full"
        };
        double[] ratios = {0.25D, 0.5D, 0.75D, 1.0D};
        for (int i = 0; i < block.getTextureLevels().size(); i++) {
            assertEquals("fluid", block.getTextureLevels().get(i).getContent());
            assertEquals(ratios[i], block.getTextureLevels().get(i).getMinFillRatio(), 0.0001D);
            assertEquals(models[i], block.getTextureLevels().get(i).getModel());
        }
        assertEquals("fluid", definition.getComponents().get(0).getKind());
        assertEquals("input", definition.getComponents().get(0).getIo());
        assertEquals(Long.valueOf(1000000L), definition.getComponents().get(0).getCapacity());
    }

    @Test
    public void rejectsInvalidModelStateAndTextureLevelRatio() throws IOException {
        Path invalidState = writeTempConfig("{"
            + "\"id\":\"broken_state\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\","
            + "\"states\":{\"running\":{\"texture\":\"mmceoneblock:blocks/working\"}}},"
            + "\"components\":[{\"type\":\"item_input\",\"id\":\"items_in\"}]"
            + "}");
        Path invalidRatio = writeTempConfig("{"
            + "\"id\":\"broken_ratio\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"Broken\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/starter\","
            + "\"textureLevels\":[{\"content\":\"fluid\",\"minFillRatio\":1.5,\"texture\":\"mmceoneblock:blocks/full\"}]},"
            + "\"components\":[{\"type\":\"item_input\",\"id\":\"items_in\"}]"
            + "}");

        try {
            MachineConfigLoader.load(invalidState);
            fail("invalid render state should be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("invalid block state"));
        } finally {
            Files.deleteIfExists(invalidState);
        }
        try {
            MachineConfigLoader.load(invalidRatio);
            fail("invalid texture level ratio should be rejected");
        } catch (MachineConfigException ex) {
            assertTrue(ex.getMessage().contains("outside 0..1"));
        } finally {
            Files.deleteIfExists(invalidRatio);
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
            + "\"components\":[{\"type\":\"item_input\",\"id\":\"items_in\"}]"
            + "}";
    }
}
