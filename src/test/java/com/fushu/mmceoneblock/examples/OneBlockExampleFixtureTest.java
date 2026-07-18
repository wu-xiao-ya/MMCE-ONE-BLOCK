package com.fushu.mmceoneblock.examples;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class OneBlockExampleFixtureTest {
    @Test
    public void sampleJsonStaysStrictAndParsable() throws IOException {
        Path example = Paths.get("examples", "one-block-machine.json");
        JsonObject root;
        try (JsonReader reader = new JsonReader(new InputStreamReader(Files.newInputStream(example), StandardCharsets.UTF_8))) {
            reader.setLenient(false);
            root = new JsonParser().parse(reader).getAsJsonObject();
        }

        assertEquals("starter_controller", root.get("id").getAsString());
        assertEquals("starter_machine", root.get("machine").getAsString());
        assertEquals("mmceoneblock:starter_controller", root.get("guiStyle").getAsString());
        assertEquals(5, root.getAsJsonArray("components").size());
        assertEquals("gas_input", root.getAsJsonArray("components").get(3).getAsJsonObject().get("type").getAsString());
    }

    @Test
    public void sampleClientCfgStillMentionsExpectedKeys() throws IOException {
        String cfg = read(Paths.get("examples", "client.cfg.sample"));

        assertTrue(cfg.contains("B:enabled=true"));
        assertTrue(cfg.contains("S:smartInterfaceEditorVirtualKey=energy_target,fluid_target"));
        assertTrue(cfg.contains("S:specialThreadBackgroundColor=FF7DD3FC"));
    }

    @Test
    public void readmePointsToAddonConfigDirectory() throws IOException {
        String readme = read(Paths.get("README.md"));

        assertTrue(readme.contains("config/mmce-one-block/machines/"));
        assertTrue(readme.contains("examples/one-block-machine.json"));
        assertTrue(readme.contains("config/mmceguiext/styles/"));
    }

    @Test
    public void smokeFixtureStaysStrictAndSelfContained() throws IOException {
        Path smokeConfig = Paths.get("examples", "smoke", "config");
        Path smokeMachines = smokeConfig.resolve("mmce-one-block/machines");
        assertTrue(Files.isRegularFile(smokeConfig.resolve("modularmachinery/machinery/starter_machine.json")));
        assertTrue(Files.isRegularFile(smokeConfig.resolve("modularmachinery/recipes/starter_machine_cobblestone_to_stone.json")));
        assertTrue(Files.isRegularFile(smokeMachines.resolve("advanced_mana_pool_controller.json")));
        assertTrue(Files.isRegularFile(smokeMachines.resolve("starter_controller.json")));
        assertTrue(Files.isRegularFile(smokeMachines.resolve("starter_factory_controller.json")));
        assertTrue(Files.isRegularFile(Paths.get("examples", "optional", "advanced_mana_pool_controller.json")));
        assertTrue(Files.isRegularFile(smokeConfig.resolve("mmceguiext/styles/starter_controller.json")));
        assertTrue(Files.isRegularFile(smokeConfig.resolve("mmceguiext/styles/factory_controller.json")));
        assertTrue(Files.isRegularFile(smokeConfig.resolve("mmceguiext/subgui/factory_threads.json")));

        try (Stream<Path> stream = Files.list(smokeMachines)) {
            List<String> machineFiles = stream
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(path -> path.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
            assertEquals(
                Arrays.asList(
                    "advanced_mana_pool_controller.json",
                    "starter_controller.json",
                    "starter_factory_controller.json"
                ),
                machineFiles
            );
        }

        try (Stream<Path> stream = Files.walk(smokeConfig)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".json")).toArray(Path[]::new)) {
                try (JsonReader reader = new JsonReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
                    reader.setLenient(false);
                    new JsonParser().parse(reader).getAsJsonObject();
                }
            }
        }

        JsonObject oneBlock = parse(smokeConfig.resolve("mmce-one-block/machines/starter_controller.json"));
        JsonObject factoryOneBlock = parse(smokeConfig.resolve("mmce-one-block/machines/starter_factory_controller.json"));
        JsonObject smokeAdvanced = parse(smokeConfig.resolve(
            "mmce-one-block/machines/advanced_mana_pool_controller.json"));
        JsonObject optionalAdvanced = parse(Paths.get(
            "examples", "optional", "advanced_mana_pool_controller.json"));
        JsonObject style = parse(smokeConfig.resolve("mmceguiext/styles/starter_controller.json"));
        JsonObject factoryStyle = parse(smokeConfig.resolve("mmceguiext/styles/factory_controller.json"));
        JsonObject factoryThreads = parse(smokeConfig.resolve("mmceguiext/subgui/factory_threads.json"));

        assertEquals("starter_machine", oneBlock.get("machine").getAsString());
        assertEquals("mmceoneblock:starter_controller", oneBlock.get("guiStyle").getAsString());
        assertEquals("factory", factoryOneBlock.get("controllerType").getAsString());
        assertEquals("mmceoneblock:factory_controller", factoryOneBlock.get("factoryGuiStyle").getAsString());
        assertEquals(optionalAdvanced, smokeAdvanced);
        assertEquals(
            "mmceoneblock:advanced_mana_pool_controller_empty",
            smokeAdvanced.getAsJsonObject("block").get("model").getAsString()
        );
        assertEquals("mmceoneblock:starter_controller", style.get("registryname").getAsString());
        assertTrue(style.getAsJsonObject("mmce_gui_ext").has("machineController"));
        assertEquals("mmceoneblock:factory_controller", factoryStyle.get("registryname").getAsString());
        assertTrue(factoryStyle.getAsJsonObject("mmce_gui_ext").has("factoryController"));
        assertEquals("mmceoneblock:factory_controller", factoryThreads.get("registryname").getAsString());
        assertEquals("factory", factoryThreads.get("controller").getAsString());
        assertEquals("factory_threads", factoryThreads.get("id").getAsString());
        assertEquals("visible", factoryThreads.get("threadQueueMode").getAsString());
    }

    @Test
    public void smokeStyleFixtureParsesWithMmcegeGuiFeatures() throws Exception {
        Path styleFile = Paths.get("examples", "smoke", "config", "mmceguiext", "styles", "starter_controller.json");
        Object result = parseMmcegeMachineJson(styleFile.toString(), read(styleFile));

        assertEquals("mmceoneblock:starter_controller", field(result, "namespacedKey"));
        assertTrue(listField(result, "warnings").isEmpty());

        Object machineStyle = field(result, "machineStyle");
        assertNotNull(machineStyle);
        assertEquals(Integer.valueOf(240), field(machineStyle, "guiWidth"));
        assertEquals(Integer.valueOf(213), field(machineStyle, "guiHeight"));

        List<?> texts = listField(machineStyle, "texts");
        assertEquals(1, texts.size());
        assertEquals("smoke_title", field(texts.get(0), "id"));
        assertEquals("One Block Smoke", field(texts.get(0), "value"));

        List<?> buttons = listField(machineStyle, "buttons");
        assertEquals(2, buttons.size());
        assertEquals("smoke_cycle", field(buttons.get(0), "id"));
        assertEquals("event", field(buttons.get(0), "action"));
        assertEquals("smoke_pulse", field(buttons.get(0), "buttonId"));
        assertEquals("smoke_smart_set", field(buttons.get(1), "id"));
        assertEquals("smart_set", field(buttons.get(1), "action"));
        assertEquals("oneblock_smoke_target", field(buttons.get(1), "key"));
        assertEquals(Float.valueOf(42.0F), field(buttons.get(1), "value"));

        List<?> smartEditors = listField(machineStyle, "smartInterfaceEditors");
        assertEquals(1, smartEditors.size());
        assertEquals("smoke_smart_input", field(smartEditors.get(0), "id"));
        assertEquals("oneblock_smoke_target", field(smartEditors.get(0), "virtualKey"));

        List<?> progressBars = listField(machineStyle, "progressBars");
        assertEquals(1, progressBars.size());
        assertEquals("smoke_progress", field(progressBars.get(0), "id"));
        assertEquals("left_to_right", field(progressBars.get(0), "direction"));
        assertEquals("machine_progress", field(progressBars.get(0), "source"));

        List<?> dynamicVisuals = listField(machineStyle, "dynamicVisuals");
        assertEquals(1, dynamicVisuals.size());
        assertEquals("smoke_progress_fill", field(dynamicVisuals.get(0), "id"));
        Object dynamicSource = field(dynamicVisuals.get(0), "source");
        assertEquals("oneblock.component.energy_in.amount", field(dynamicSource, "key"));
        assertEquals(
            "oneblock.component.energy_in.capacity",
            field(field(dynamicSource, "maxSource"), "key")
        );
        assertEquals("fill", field(field(dynamicVisuals.get(0), "renderer"), "type"));
        assertEquals("right", field(field(dynamicVisuals.get(0), "renderer"), "direction"));

        List<?> slotGroups = listField(machineStyle, "slotGroups");
        assertEquals(3, slotGroups.size());
        assertEquals("input", field(slotGroups.get(0), "id"));
        assertEquals(Integer.valueOf(8), field(slotGroups.get(0), "x"));
        assertEquals(Integer.valueOf(17), field(slotGroups.get(0), "y"));
        assertEquals(Integer.valueOf(2), field(slotGroups.get(0), "rows"));
        assertEquals(Integer.valueOf(5), field(slotGroups.get(0), "columns"));
        assertEquals(Integer.valueOf(37), field(slotGroups.get(0), "firstSlot"));
        assertEquals(Integer.valueOf(9), field(slotGroups.get(0), "slotCount"));
        assertEquals("output", field(slotGroups.get(1), "id"));
        assertEquals(Integer.valueOf(8), field(slotGroups.get(1), "x"));
        assertEquals(Integer.valueOf(66), field(slotGroups.get(1), "y"));
        assertEquals("blueprint", field(slotGroups.get(2), "id"));
        assertEquals(Integer.valueOf(151), field(slotGroups.get(2), "x"));
        assertEquals(Integer.valueOf(8), field(slotGroups.get(2), "y"));
        assertEquals(Integer.valueOf(36), field(slotGroups.get(2), "firstSlot"));

        Object playerInv = field(machineStyle, "playerInventory");
        assertNotNull(playerInv);
        assertEquals(Integer.valueOf(8), field(playerInv, "x"));
        assertEquals(Integer.valueOf(131), field(playerInv, "y"));
        assertEquals(Integer.valueOf(8), field(playerInv, "hotbarX"));
        assertEquals(Integer.valueOf(189), field(playerInv, "hotbarY"));
        assertEquals(Integer.valueOf(0), field(playerInv, "mainStart"));
        assertEquals(Integer.valueOf(27), field(playerInv, "hotbarStart"));

        Path factoryStyleFile = Paths.get("examples", "smoke", "config", "mmceguiext", "styles", "factory_controller.json");
        Object factoryResult = parseMmcegeMachineJson(factoryStyleFile.toString(), read(factoryStyleFile));
        assertEquals("mmceoneblock:factory_controller", field(factoryResult, "namespacedKey"));
        assertTrue(listField(factoryResult, "warnings").isEmpty());

        Object factoryStyle = field(factoryResult, "factoryStyle");
        assertNotNull(factoryStyle);
        assertEquals("tooltip", field(factoryStyle, "threadQueueMode"));
        assertEquals(Boolean.TRUE, field(factoryStyle, "threadTooltip"));
        List<?> factorySlotGroups = listField(factoryStyle, "slotGroups");
        assertEquals(3, factorySlotGroups.size());
        assertEquals("input", field(factorySlotGroups.get(0), "id"));
        assertEquals(Integer.valueOf(37), field(factorySlotGroups.get(0), "firstSlot"));
        assertEquals("output", field(factorySlotGroups.get(1), "id"));
        assertEquals(Integer.valueOf(46), field(factorySlotGroups.get(1), "firstSlot"));
        assertEquals("blueprint", field(factorySlotGroups.get(2), "id"));
        assertEquals(Integer.valueOf(36), field(factorySlotGroups.get(2), "firstSlot"));
        List<?> factoryButtons = listField(factoryStyle, "buttons");
        assertEquals(1, factoryButtons.size());
        assertEquals("subgui", field(factoryButtons.get(0), "action"));
        assertEquals("factory_threads", field(factoryButtons.get(0), "targetSubGui"));
        Object factoryPlayerInv = field(factoryStyle, "playerInventory");
        assertNotNull(factoryPlayerInv);
        assertEquals(Integer.valueOf(112), field(factoryPlayerInv, "x"));
        assertEquals(Integer.valueOf(131), field(factoryPlayerInv, "y"));

        Path factoryThreadsFile = Paths.get(
            "examples", "smoke", "config", "mmceguiext", "subgui", "factory_threads.json"
        );
        Object factoryThreadsResult = parseMmcegeSubGuiJson(
            factoryThreadsFile.toString(),
            read(factoryThreadsFile)
        );
        assertEquals("mmceoneblock:factory_controller", field(factoryThreadsResult, "namespacedKey"));
        assertTrue(listField(factoryThreadsResult, "warnings").isEmpty());
        Object factoryThreadsStyle = field(factoryThreadsResult, "factoryStyle");
        assertNotNull(factoryThreadsStyle);
        List<?> subGuis = listField(factoryThreadsStyle, "subGuis");
        assertEquals(1, subGuis.size());
        Object subGui = subGuis.get(0);
        assertEquals("factory_threads", field(subGui, "id"));
        assertEquals("modal", field(subGui, "mode"));
        assertEquals(Boolean.TRUE, field(subGui, "draggable"));
        Object subGuiStyle = field(subGui, "style");
        assertNotNull(subGuiStyle);
        assertEquals("visible", field(subGuiStyle, "threadQueueMode"));
        assertEquals(Boolean.FALSE, field(subGuiStyle, "threadTooltip"));
        assertEquals("close_subgui", field(listField(subGuiStyle, "buttons").get(0), "action"));
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static JsonObject parse(Path file) throws IOException {
        try (JsonReader reader = new JsonReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            reader.setLenient(false);
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }

    private static Object parseMmcegeMachineJson(String sourceName, String content) throws Exception {
        Class<?> parser = Thread.currentThread()
            .getContextClassLoader()
            .loadClass("com.fushu.mmceguiext.client.config.MachineGuiStyleParser");
        Method parse = parser.getDeclaredMethod("parseMachineJson", String.class, String.class);
        parse.setAccessible(true);
        return parse.invoke(null, sourceName, content);
    }

    private static Object parseMmcegeSubGuiJson(String sourceName, String content) throws Exception {
        Class<?> parser = Thread.currentThread()
            .getContextClassLoader()
            .loadClass("com.fushu.mmceguiext.client.config.SubGuiConfigLoader");
        Method parse = parser.getDeclaredMethod("parseSubGuiJson", String.class, String.class);
        parse.setAccessible(true);
        return parse.invoke(null, sourceName, content);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @SuppressWarnings("unchecked")
    private static List<?> listField(Object target, String name) throws Exception {
        List<?> value = (List<?>) field(target, name);
        assertNotNull(value);
        return value;
    }
}
