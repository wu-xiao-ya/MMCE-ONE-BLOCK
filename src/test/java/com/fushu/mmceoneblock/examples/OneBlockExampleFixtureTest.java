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
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
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
        assertTrue(Files.isRegularFile(smokeConfig.resolve("modularmachinery/machinery/starter_machine.json")));
        assertTrue(Files.isRegularFile(smokeConfig.resolve("modularmachinery/recipes/starter_machine_cobblestone_to_stone.json")));
        assertTrue(Files.isRegularFile(smokeConfig.resolve("mmce-one-block/machines/starter_controller.json")));
        assertTrue(Files.isRegularFile(smokeConfig.resolve("mmceguiext/styles/starter_controller.json")));

        try (Stream<Path> stream = Files.walk(smokeConfig)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".json")).toArray(Path[]::new)) {
                try (JsonReader reader = new JsonReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
                    reader.setLenient(false);
                    new JsonParser().parse(reader).getAsJsonObject();
                }
            }
        }

        JsonObject oneBlock = parse(smokeConfig.resolve("mmce-one-block/machines/starter_controller.json"));
        JsonObject style = parse(smokeConfig.resolve("mmceguiext/styles/starter_controller.json"));

        assertEquals("starter_machine", oneBlock.get("machine").getAsString());
        assertEquals("mmceoneblock:starter_controller", oneBlock.get("guiStyle").getAsString());
        assertEquals("mmceoneblock:starter_controller", style.get("registryname").getAsString());
        assertTrue(style.getAsJsonObject("mmce_gui_ext").has("machineController"));
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
        assertEquals(1, buttons.size());
        assertEquals("smoke_cycle", field(buttons.get(0), "id"));
        assertEquals("event", field(buttons.get(0), "action"));
        assertEquals("smoke_pulse", field(buttons.get(0), "buttonId"));

        List<?> progressBars = listField(machineStyle, "progressBars");
        assertEquals(1, progressBars.size());
        assertEquals("smoke_progress", field(progressBars.get(0), "id"));
        assertEquals("left_to_right", field(progressBars.get(0), "direction"));
        assertEquals("machine_progress", field(progressBars.get(0), "source"));

        List<?> dynamicVisuals = listField(machineStyle, "dynamicVisuals");
        assertEquals(1, dynamicVisuals.size());
        assertEquals("smoke_progress_fill", field(dynamicVisuals.get(0), "id"));
        assertEquals("recipeProgress", field(field(dynamicVisuals.get(0), "source"), "metric"));
        assertEquals("fill", field(field(dynamicVisuals.get(0), "renderer"), "type"));
        assertEquals("right", field(field(dynamicVisuals.get(0), "renderer"), "direction"));
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
