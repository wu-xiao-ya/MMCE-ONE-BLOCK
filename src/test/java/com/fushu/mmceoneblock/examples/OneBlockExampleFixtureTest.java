package com.fushu.mmceoneblock.examples;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
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

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static JsonObject parse(Path file) throws IOException {
        try (JsonReader reader = new JsonReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            reader.setLenient(false);
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }
}
