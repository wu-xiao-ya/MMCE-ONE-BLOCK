package com.fushu.mmceoneblock.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class AdvancedManaPoolModelResourceTest {
    @Test
    public void optionalConfigUsesRealFluidLevelsAndStaticFallbackModels() throws IOException {
        JsonObject config = readJson(Paths.get(
            "examples", "optional",
            "advanced_mana_pool_controller.json"
        ));
        JsonObject block = config.getAsJsonObject("block");

        assertEquals("mmceoneblock:advanced_mana_pool_controller_empty", block.get("model").getAsString());
        assertEquals("mmceoneblock:advanced_mana_pool_controller_full", block.get("itemModel").getAsString());
        assertEquals("mmceoneblock:blocks/advanced_mana_pool_side", block.get("texture").getAsString());
        assertEquals("mmceoneblock:blocks/advanced_mana_pool_base", block.getAsJsonObject("textures").get("base").getAsString());
        assertEquals(
            "mmceoneblock:advanced_mana_pool_controller_empty",
            block.getAsJsonObject("states").getAsJsonObject("idle").get("model").getAsString()
        );
        JsonArray levels = block.getAsJsonArray("textureLevels");
        assertEquals(4, levels.size());
        String[] models = {
            "mmceoneblock:advanced_mana_pool_controller_25",
            "mmceoneblock:advanced_mana_pool_controller_50",
            "mmceoneblock:advanced_mana_pool_controller_75",
            "mmceoneblock:advanced_mana_pool_controller_full"
        };
        double[] ratios = {0.25D, 0.5D, 0.75D, 1.0D};
        for (int i = 0; i < levels.size(); i++) {
            JsonObject level = levels.get(i).getAsJsonObject();
            assertEquals("fluid", level.get("content").getAsString());
            assertEquals(ratios[i], level.get("minFillRatio").getAsDouble(), 0.0001D);
            assertEquals(models[i], level.get("model").getAsString());
        }
        JsonObject fluid = config.getAsJsonArray("components").get(0).getAsJsonObject();
        assertEquals("fluid_input", fluid.get("type").getAsString());
        assertEquals(1000000L, fluid.get("capacity").getAsLong());
    }

    @Test
    public void advancedManaPoolBlockModelsStayParseableAndKeepTheirFluidSlices() throws IOException {
        assertAdvancedManaPoolModel("advanced_mana_pool_controller.json", 10, 12.75D, true);
        assertAdvancedManaPoolModel("advanced_mana_pool_controller_empty.json", 9, 0.0D, false);
        assertAdvancedManaPoolModel("advanced_mana_pool_controller_25.json", 10, 6.063D, true);
        assertAdvancedManaPoolModel("advanced_mana_pool_controller_50.json", 10, 8.25D, true);
        assertAdvancedManaPoolModel("advanced_mana_pool_controller_75.json", 10, 10.437D, true);
        assertAdvancedManaPoolModel("advanced_mana_pool_controller_full.json", 10, 12.625D, true);
    }

    private static void assertAdvancedManaPoolModel(String fileName,
                                                    int expectedElements,
                                                    double expectedFluidY,
                                                    boolean fluidSlice) throws IOException {
        JsonObject root = readJson(Paths.get("src", "main", "resources", "assets", "mmceoneblock", "models", "block", fileName));
        JsonObject textures = root.getAsJsonObject("textures");
        JsonArray elements = root.getAsJsonArray("elements");

        assertTrue("expected " + fileName + " to parse", root.entrySet().size() > 0);
        assertEquals("mmceoneblock:blocks/advanced_mana_pool_side", textures.get("particle").getAsString());
        assertEquals("mmceoneblock:blocks/advanced_mana_pool_base", textures.get("base").getAsString());
        assertEquals("mmceoneblock:blocks/advanced_mana_pool_side", textures.get("side").getAsString());
        assertEquals("mmceoneblock:blocks/advanced_mana_pool_inner", textures.get("inner").getAsString());
        assertEquals("mmceoneblock:blocks/advanced_mana_pool_fluid", textures.get("fluid").getAsString());
        assertEquals("mmceoneblock:blocks/advanced_mana_pool_port", textures.get("port").getAsString());
        assertEquals(expectedElements, elements.size());

        if (fluidSlice) {
            JsonObject slice = findFluidSlice(elements, expectedFluidY);
            JsonArray from = slice.getAsJsonArray("from");
            JsonArray to = slice.getAsJsonArray("to");
            JsonObject faces = slice.getAsJsonObject("faces");
            assertEquals(3.0D, from.get(0).getAsDouble(), 0.0001D);
            assertEquals(expectedFluidY, from.get(1).getAsDouble(), 0.0001D);
            assertEquals(3.0D, from.get(2).getAsDouble(), 0.0001D);
            assertEquals(13.0D, to.get(0).getAsDouble(), 0.0001D);
            assertEquals(expectedFluidY + 0.125D, to.get(1).getAsDouble(), 0.0001D);
            assertEquals(13.0D, to.get(2).getAsDouble(), 0.0001D);
            assertEquals(2, faces.entrySet().size());
            assertEquals("#fluid", faces.getAsJsonObject("up").get("texture").getAsString());
            assertEquals("#fluid", faces.getAsJsonObject("down").get("texture").getAsString());
        } else {
            JsonObject slice = elements.get(6).getAsJsonObject();
            JsonArray from = slice.getAsJsonArray("from");
            JsonArray to = slice.getAsJsonArray("to");
            JsonObject faces = slice.getAsJsonObject("faces");
            assertEquals(4.0D, from.get(0).getAsDouble(), 0.0001D);
            assertEquals(5.0D, from.get(1).getAsDouble(), 0.0001D);
            assertEquals(0.0D, from.get(2).getAsDouble(), 0.0001D);
            assertEquals(12.0D, to.get(0).getAsDouble(), 0.0001D);
            assertEquals(11.0D, to.get(1).getAsDouble(), 0.0001D);
            assertEquals(1.0D, to.get(2).getAsDouble(), 0.0001D);
            assertEquals(6, faces.entrySet().size());
            assertEquals("#port", faces.getAsJsonObject("north").get("texture").getAsString());
            assertEquals("#port", faces.getAsJsonObject("south").get("texture").getAsString());
            assertEquals("#side", faces.getAsJsonObject("west").get("texture").getAsString());
            assertEquals("#side", faces.getAsJsonObject("east").get("texture").getAsString());
            assertEquals("#side", faces.getAsJsonObject("up").get("texture").getAsString());
            assertEquals("#side", faces.getAsJsonObject("down").get("texture").getAsString());
        }
    }

    private static JsonObject findFluidSlice(JsonArray elements, double expectedFluidY) {
        for (JsonElement element : elements) {
            JsonObject candidate = element.getAsJsonObject();
            JsonArray from = candidate.getAsJsonArray("from");
            JsonObject faces = candidate.getAsJsonObject("faces");
            if (from == null || faces == null || Math.abs(from.get(1).getAsDouble() - expectedFluidY) > 0.0001D) {
                continue;
            }
            if (faces.has("up")
                && faces.has("down")
                && "#fluid".equals(faces.getAsJsonObject("up").get("texture").getAsString())
                && "#fluid".equals(faces.getAsJsonObject("down").get("texture").getAsString())) {
                return candidate;
            }
        }
        throw new AssertionError("missing fluid slice at y=" + expectedFluidY);
    }

    private static JsonObject readJson(Path file) throws IOException {
        try (JsonReader reader = new JsonReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            reader.setLenient(false);
            JsonElement parsed = new JsonParser().parse(reader);
            return parsed.getAsJsonObject();
        }
    }
}
