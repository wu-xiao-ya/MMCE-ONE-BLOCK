package com.fushu.mmceoneblock.common.config;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MachineConfigLoaderDirectoryTest {
    @Test
    public void loadAllSkipsInvalidFilesAndLaterDuplicates() throws IOException {
        Path dir = Files.createTempDirectory("mmce-one-block-load-all-");
        try {
            write(dir.resolve("01-alpha.json"), validConfig("alpha"));
            write(dir.resolve("02-broken.json"), "{"
                + "\"machine\":\"starter_machine\","
                + "\"displayName\":\"Broken\","
                + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/broken\"},"
                + "\"components\":[\"item_input\"]"
                + "}");
            write(dir.resolve("03-alpha-duplicate.json"), validConfig("alpha"));
            write(dir.resolve("04-beta.json"), validConfig("beta"));

            List<MachineDefinition> definitions = MachineConfigLoader.loadAll(dir);

            assertEquals(2, definitions.size());
            assertEquals("alpha", definitions.get(0).getId());
            assertEquals("01-alpha.json", definitions.get(0).getSourceFile().getFileName().toString());
            assertEquals("beta", definitions.get(1).getId());
        } finally {
            deleteTempDir(dir);
        }
    }

    @Test
    public void loadAllReturnsEmptyForMissingMachineDirectory() throws IOException {
        Path dir = Files.createTempDirectory("mmce-one-block-missing-parent-");
        Path missing = dir.resolve("missing");
        try {
            assertTrue(MachineConfigLoader.loadAll(missing).isEmpty());
        } finally {
            deleteTempDir(dir);
        }
    }

    private static void write(Path file, String json) throws IOException {
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));
    }

    private static String validConfig(String id) {
        return "{"
            + "\"id\":\"" + id + "\","
            + "\"machine\":\"starter_machine\","
            + "\"displayName\":\"" + id + "\","
            + "\"block\":{\"model\":\"mmceoneblock:single_block_machine_controller\",\"texture\":\"mmceoneblock:blocks/" + id + "\"},"
            + "\"components\":[\"item_input\"],"
            + "\"guiStyle\":\"mmceoneblock:" + id + "\""
            + "}";
    }

    private static void deleteTempDir(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
            for (Path file : stream.toArray(Path[]::new)) {
                Files.deleteIfExists(file);
            }
        }
        Files.deleteIfExists(dir);
    }
}
