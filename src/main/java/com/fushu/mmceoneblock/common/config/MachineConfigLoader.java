package com.fushu.mmceoneblock.common.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class MachineConfigLoader {
    public static final String MODID = "mmceoneblock";
    private static final Logger LOGGER = LogManager.getLogger(MODID);
    private static final String CONFIG_DIR = "mmce-one-block";
    private static final String MACHINES_DIR = "machines";
    private static final long MAX_CONFIG_BYTES = 1024L * 1024L;
    private static final int MAX_ITEM_SLOTS = 4096;
    private static final long MAX_COMPONENT_CAPACITY = Integer.MAX_VALUE;

    private MachineConfigLoader() {
    }

    public static List<MachineDefinition> loadAll() {
        Path machineDir = resolveMachineDir();
        return loadAll(machineDir);
    }

    static List<MachineDefinition> loadAll(Path machineDir) {
        if (!Files.isDirectory(machineDir)) {
            return Collections.emptyList();
        }

        List<Path> files = new ArrayList<Path>();
        try (Stream<Path> stream = Files.list(machineDir)) {
            files.addAll(stream
                .filter(Files::isRegularFile)
                .filter(MachineConfigLoader::isJson)
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList()));
        } catch (IOException ex) {
            throw new MachineConfigException("Failed to scan machine config directory " + machineDir, ex);
        }

        Map<String, MachineDefinition> registered = new LinkedHashMap<String, MachineDefinition>();
        for (Path file : files) {
            MachineDefinition def;
            try {
                def = load(file);
            } catch (MachineConfigException ex) {
                LOGGER.error("Skipping invalid machine definition from " + file, ex);
                continue;
            }
            if (def == null) {
                continue;
            }
            String id = normalizeId(def.getId());
            if (registered.containsKey(id)) {
                MachineDefinition previous = registered.get(id);
                LOGGER.error(
                    "Skipping duplicate machine definition '{}' from {} because it was already loaded from {}",
                    def.getId(),
                    file,
                    previous.getSourceFile()
                );
                continue;
            }
            registered.put(id, def);
        }

        LOGGER.info("Loaded {} one-block machine definition(s) from {}", registered.size(), machineDir);
        return new ArrayList<MachineDefinition>(registered.values());
    }

    @Nullable
    public static MachineDefinition load(Path file) {
        try {
            if (Files.size(file) > MAX_CONFIG_BYTES) {
                throw new MachineConfigException("Machine config " + file + " is larger than " + MAX_CONFIG_BYTES + " bytes");
            }

            JsonElement rootElement = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            if (rootElement == null || !rootElement.isJsonObject()) {
                throw new MachineConfigException("Machine config " + file + " must be a JSON object");
            }

            JsonObject root = rootElement.getAsJsonObject();
            String id = requireMachineId(root, file);
            ResourceLocation machine = parseMachine(root, file);
            boolean enabled = getBoolean(root, true, "enabled", file);
            String displayName = requireString(root, file, "displayName");
            MachineBlockDefinition block = parseBlock(root, file);
            List<MachineComponentDefinition> components = parseComponents(root, file);
            String guiStyle = parseGuiStyle(root, id, file);

            validateComponents(components, file);
            if (!enabled) {
                LOGGER.info("Skipping disabled machine definition {} from {}", id, file);
                return new MachineDefinition(id, machine, false, displayName, block, components, guiStyle, file);
            }

            return new MachineDefinition(id, machine, true, displayName, block, components, guiStyle, file);
        } catch (MachineConfigException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MachineConfigException("Failed to parse machine config " + file, ex);
        }
    }

    public static Path resolveMachineDir() {
        Path configDir = Loader.instance().getConfigDir().toPath();
        return configDir.resolve(CONFIG_DIR).resolve(MACHINES_DIR);
    }

    private static boolean isJson(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".json");
    }

    private static String requireMachineId(JsonObject root, Path file) {
        String id = requireString(root, file, "id");
        String normalized = normalizeId(id);
        if (normalized.isEmpty()) {
            throw new MachineConfigException("Machine config " + file + " has an empty id");
        }
        if (!normalized.equals(id.trim().toLowerCase(Locale.ROOT))) {
            throw new MachineConfigException("Machine config " + file + " has an illegal id '" + id + "'");
        }
        if (!normalized.equals(stripNamespace(normalized))) {
            throw new MachineConfigException("Machine config " + file + " must use a path-only id, not '" + id + "'");
        }
        new ResourceLocation(MODID, normalized);
        return normalized;
    }

    private static ResourceLocation parseMachine(JsonObject root, Path file) {
        String machineId = requireString(root, file, "machine");
        String normalized = machineId.trim().toLowerCase(Locale.ROOT);
        try {
            if (normalized.indexOf(':') >= 0) {
                return new ResourceLocation(normalized);
            }
            return new ResourceLocation("modularmachinery", normalized);
        } catch (Exception ex) {
            throw new MachineConfigException("Machine config " + file + " has an illegal machine id '" + machineId + "'", ex);
        }
    }

    private static MachineBlockDefinition parseBlock(JsonObject root, Path file) {
        JsonObject blockNode = getObject(root, "block");
        if (blockNode == null) {
            throw new MachineConfigException("Machine config " + file + " is missing block");
        }
        String model = requireString(blockNode, file, "model");
        String texture = requireString(blockNode, file, "texture");
        validateResourceLocation(model, file, "block.model");
        validateResourceLocation(texture, file, "block.texture");
        return new MachineBlockDefinition(model, texture);
    }

    private static List<MachineComponentDefinition> parseComponents(JsonObject root, Path file) {
        JsonArray array = getArray(root, "components");
        if (array == null) {
            throw new MachineConfigException("Machine config " + file + " is missing components");
        }

        List<MachineComponentDefinition> out = new ArrayList<MachineComponentDefinition>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element == null || element.isJsonNull()) {
                throw new MachineConfigException("Machine config " + file + " has a null component at index " + i);
            }

            if (element.isJsonPrimitive()) {
                String type = MachineComponentTypes.normalize(element.getAsString());
                if (type.isEmpty()) {
                    throw new MachineConfigException("Machine config " + file + " has an empty component type at index " + i);
                }
                if (!MachineComponentTypes.isKnown(type)) {
                    throw new MachineConfigException(
                        "Machine config " + file + " has an illegal component type '" + element.getAsString() + "' at index " + i
                    );
                }
                out.add(new MachineComponentDefinition(type, null, true, null, new JsonObject()));
                continue;
            }

            if (!element.isJsonObject()) {
                throw new MachineConfigException("Machine config " + file + " has an invalid component at index " + i);
            }

            JsonObject component = element.getAsJsonObject();
            String type = requireString(component, file, "type");
            String normalizedType = MachineComponentTypes.normalize(type);
            if (!MachineComponentTypes.isKnown(normalizedType)) {
                throw new MachineConfigException(
                    "Machine config " + file + " has an illegal component type '" + type + "' at index " + i
                );
            }
            boolean enabled = getBoolean(component, true, "enabled", file);
            String id = getOptionalString(component, file, "id");
            String displayName = getOptionalString(component, file, "displayName");
            if (id != null && id.trim().isEmpty()) {
                throw new MachineConfigException("Machine config " + file + " has an empty component id at index " + i);
            }
            out.add(new MachineComponentDefinition(normalizedType, id, enabled, displayName, component));
        }
        return out;
    }

    private static String parseGuiStyle(JsonObject root, String id, Path file) {
        String guiStyle = getOptionalString(root, file, "guiStyle");
        if (guiStyle == null || guiStyle.trim().isEmpty()) {
            return new ResourceLocation(MODID, id).toString();
        }
        String normalized = guiStyle.trim().toLowerCase(Locale.ROOT);
        try {
            return new ResourceLocation(normalized).toString();
        } catch (Exception ex) {
            throw new MachineConfigException("Machine config " + file + " has an illegal guiStyle '" + guiStyle + "'", ex);
        }
    }

    private static void validateComponents(List<MachineComponentDefinition> components, Path file) {
        Map<String, Integer> seen = new LinkedHashMap<String, Integer>();
        boolean hasEnabledRuntimeComponent = false;
        for (int i = 0; i < components.size(); i++) {
            MachineComponentDefinition component = components.get(i);
            if (component == null || !component.isEnabled()) {
                continue;
            }
            String id = component.getId();
            if (id == null || id.trim().isEmpty()) {
                id = null;
            } else {
                String normalized = normalizeId(id);
                Integer previousIndex = seen.get(normalized);
                if (previousIndex != null) {
                    throw new MachineConfigException(
                        "Machine config " + file + " contains duplicate component id '" + id + "' at indexes " + previousIndex + " and " + i
                    );
                }
                seen.put(normalized, i);
            }
            String kind = MachineComponentTypes.kind(component.getType());
            if (!MachineComponentTypes.isRuntimeSupported(component.getType())) {
                throw new MachineConfigException(
                    "Machine config " + file + " has unsupported v1 component type '" + component.getType() + "' at index " + i
                );
            }
            validateComponentIo(component, file, i);
            validateComponentCapacity(component, kind, file, i);
            validateComponentSlots(component, kind, file, i);
            hasEnabledRuntimeComponent = true;
        }
        if (!hasEnabledRuntimeComponent) {
            throw new MachineConfigException("Machine config " + file + " must declare at least one enabled runtime input or output component");
        }
    }

    private static void validateComponentIo(MachineComponentDefinition component, Path file, int index) {
        String suffixIo = MachineComponentTypes.ioFromType(component.getType());
        JsonObject raw = component.getRaw();
        String rawIo = getOptionalIo(raw, file, index);
        if (suffixIo != null && rawIo != null && !suffixIo.equals(rawIo)) {
            throw new MachineConfigException(
                "Machine config " + file + " has conflicting io '" + rawIo + "' for component type '" + component.getType()
                    + "' at index " + index
            );
        }
    }

    private static void validateComponentCapacity(MachineComponentDefinition component, String kind, Path file, int index) {
        if ("item".equals(kind)) {
            return;
        }
        JsonObject raw = component.getRaw();
        if (raw == null || !raw.has("capacity") || raw.get("capacity").isJsonNull()) {
            return;
        }
        JsonElement element = raw.get("capacity");
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new MachineConfigException("Machine config " + file + " has an invalid capacity at component index " + index);
        }
        long capacity;
        try {
            capacity = element.getAsLong();
        } catch (NumberFormatException ex) {
            throw new MachineConfigException("Machine config " + file + " has an invalid capacity at component index " + index, ex);
        }
        if (capacity < 1L || capacity > MAX_COMPONENT_CAPACITY) {
            throw new MachineConfigException(
                "Machine config " + file + " has capacity out of range at component index " + index
                    + ": " + capacity + " (allowed 1.." + MAX_COMPONENT_CAPACITY + ")"
            );
        }
    }

    private static void validateComponentSlots(MachineComponentDefinition component, String kind, Path file, int index) {
        JsonObject raw = component.getRaw();
        if (raw == null || !raw.has("slots") || raw.get("slots").isJsonNull()) {
            return;
        }
        if (!"item".equals(kind)) {
            throw new MachineConfigException("Machine config " + file + " may only set slots on item components at index " + index);
        }
        JsonElement element = raw.get("slots");
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new MachineConfigException("Machine config " + file + " has an invalid slots value at component index " + index);
        }
        int slots;
        try {
            slots = element.getAsInt();
        } catch (NumberFormatException ex) {
            throw new MachineConfigException("Machine config " + file + " has an invalid slots value at component index " + index, ex);
        }
        if (slots < 1 || slots > MAX_ITEM_SLOTS) {
            throw new MachineConfigException(
                "Machine config " + file + " has slots out of range at component index " + index
                    + ": " + slots + " (allowed 1.." + MAX_ITEM_SLOTS + ")"
            );
        }
    }

    @Nullable
    private static String getOptionalIo(JsonObject raw, Path file, int index) {
        String rawIo = getOptionalString(raw, file, "io");
        if (rawIo == null || rawIo.trim().isEmpty()) {
            return null;
        }
        String normalized = rawIo.trim().toLowerCase(Locale.ROOT);
        if ("in".equals(normalized)) {
            return "input";
        }
        if ("out".equals(normalized)) {
            return "output";
        }
        if ("input".equals(normalized) || "output".equals(normalized)) {
            return normalized;
        }
        throw new MachineConfigException(
            "Machine config " + file + " has an invalid io '" + rawIo + "' at component index " + index
        );
    }

    private static String requireString(JsonObject obj, Path file, String key) {
        String value = getString(obj, key);
        if (value == null || value.trim().isEmpty()) {
            throw new MachineConfigException("Machine config " + file + " is missing " + key);
        }
        return value.trim();
    }

    @Nullable
    private static String getString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement element = obj.get(key);
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
    }

    @Nullable
    private static String getOptionalString(JsonObject obj, Path file, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement element = obj.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new MachineConfigException("Machine config " + file + " has an invalid string " + key);
        }
        return element.getAsString();
    }

    @Nullable
    private static JsonObject getObject(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) {
            return null;
        }
        JsonElement element = obj.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    @Nullable
    private static JsonArray getArray(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) {
            return null;
        }
        JsonElement element = obj.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static boolean getBoolean(JsonObject obj, boolean fallback, String key, Path file) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        JsonElement element = obj.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new MachineConfigException("Machine config " + file + " has an invalid boolean " + key);
        }
        return element.getAsBoolean();
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static String stripNamespace(String id) {
        if (id == null) {
            return "";
        }
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private static void validateResourceLocation(String value, Path file, String key) {
        try {
            new ResourceLocation(value.trim().toLowerCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new MachineConfigException("Machine config " + file + " has an illegal " + key + " '" + value + "'", ex);
        }
    }
}
