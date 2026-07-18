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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private static final String RESOURCE_NAMESPACE_PATTERN = "[a-z0-9_.-]+";
    private static final String RESOURCE_PATH_PATTERN = "[a-z0-9/._-]+";

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
            ControllerType controllerType = parseControllerType(root, id, file);
            String factoryGuiStyle = parseOptionalStyle(root, file, "factoryGuiStyle");

            validateComponents(components, file);
            if (!enabled) {
                LOGGER.info("Skipping disabled machine definition {} from {}", id, file);
                return new MachineDefinition(id, machine, false, displayName, block, components, guiStyle, file,
                    controllerType, factoryGuiStyle);
            }

            return new MachineDefinition(id, machine, true, displayName, block, components, guiStyle, file,
                controllerType, factoryGuiStyle);
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
        validateResourceLocation(model, file, "block.model");
        Map<String, String> textures = parseTextureSlots(blockNode, file, "block");
        String legacyTexture = getOptionalString(blockNode, file, "texture");
        if (legacyTexture != null && !legacyTexture.trim().isEmpty()) {
            validateResourceLocation(legacyTexture, file, "block.texture");
            String previous = textures.put("all", legacyTexture.trim().toLowerCase(Locale.ROOT));
            if (previous != null && !previous.equalsIgnoreCase(legacyTexture.trim())) {
                throw new MachineConfigException(
                    "Machine config " + file + " has conflicting block.texture and block.textures.all"
                );
            }
        }
        if (textures.isEmpty()) {
            throw new MachineConfigException("Machine config " + file + " is missing block.texture or block.textures");
        }

        String itemModel = getOptionalString(blockNode, file, "itemModel");
        if (itemModel != null && !itemModel.trim().isEmpty()) {
            validateResourceLocation(itemModel, file, "block.itemModel");
            itemModel = itemModel.trim().toLowerCase(Locale.ROOT);
        } else {
            itemModel = null;
        }

        Map<MachineBlockDefinition.RenderState, MachineBlockDefinition.ModelVariant> states =
            parseStateVariants(blockNode, file);
        List<MachineBlockDefinition.TextureLevel> textureLevels = parseTextureLevels(blockNode, file);
        return new MachineBlockDefinition(model.trim().toLowerCase(Locale.ROOT), itemModel, textures, states, textureLevels);
    }

    private static Map<String, String> parseTextureSlots(JsonObject node, Path file, String scope) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (node == null || !node.has("textures")) {
            return out;
        }
        JsonElement textureElement = node.get("textures");
        if (!textureElement.isJsonObject()) {
            throw new MachineConfigException("Machine config " + file + " has an invalid " + scope + ".textures; expected an object");
        }
        JsonObject textureNode = textureElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : textureNode.entrySet()) {
            String slot = entry.getKey() == null ? "" : entry.getKey().trim();
            JsonElement value = entry.getValue();
            if (slot.isEmpty() || !slot.matches("[A-Za-z0-9_./-]+")) {
                throw new MachineConfigException("Machine config " + file + " has an invalid " + scope + ".textures slot '" + entry.getKey() + "'");
            }
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().trim().isEmpty()) {
                throw new MachineConfigException("Machine config " + file + " has an invalid " + scope + ".textures." + slot);
            }
            String texture = value.getAsString().trim().toLowerCase(Locale.ROOT);
            validateResourceLocation(texture, file, scope + ".textures." + slot);
            out.put(slot, texture);
        }
        return out;
    }

    private static Map<MachineBlockDefinition.RenderState, MachineBlockDefinition.ModelVariant> parseStateVariants(
        JsonObject blockNode,
        Path file
    ) {
        JsonObject stateNode = getObject(blockNode, "states");
        if (blockNode != null && blockNode.has("states") && stateNode == null) {
            throw new MachineConfigException("Machine config " + file + " has an invalid block.states; expected an object");
        }
        Map<MachineBlockDefinition.RenderState, MachineBlockDefinition.ModelVariant> out =
            new LinkedHashMap<MachineBlockDefinition.RenderState, MachineBlockDefinition.ModelVariant>();
        if (stateNode == null) {
            return out;
        }
        for (Map.Entry<String, JsonElement> entry : stateNode.entrySet()) {
            MachineBlockDefinition.RenderState state = parseRenderState(entry.getKey(), file);
            if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                throw new MachineConfigException("Machine config " + file + " has an invalid block.states." + entry.getKey());
            }
            JsonObject variantNode = entry.getValue().getAsJsonObject();
            String model = getOptionalString(variantNode, file, "model");
            if (model != null && !model.trim().isEmpty()) {
                validateResourceLocation(model, file, "block.states." + entry.getKey() + ".model");
                model = model.trim().toLowerCase(Locale.ROOT);
            } else {
                model = null;
            }
            Map<String, String> textures = parseTextureSlots(variantNode, file, "block.states." + entry.getKey());
            String legacyTexture = getOptionalString(variantNode, file, "texture");
            if (legacyTexture != null && !legacyTexture.trim().isEmpty()) {
                validateResourceLocation(legacyTexture, file, "block.states." + entry.getKey() + ".texture");
                String previous = textures.put("all", legacyTexture.trim().toLowerCase(Locale.ROOT));
                if (previous != null && !previous.equalsIgnoreCase(legacyTexture.trim())) {
                    throw new MachineConfigException(
                        "Machine config " + file + " has conflicting texture aliases in block.states." + entry.getKey()
                    );
                }
            }
            if (model == null && textures.isEmpty()) {
                throw new MachineConfigException(
                    "Machine config " + file + " block.states." + entry.getKey() + " must define model or textures"
                );
            }
            out.put(state, new MachineBlockDefinition.ModelVariant(model, textures));
        }
        return out;
    }

    private static MachineBlockDefinition.RenderState parseRenderState(String raw, Path file) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if ("unformed".equals(normalized)) {
            return MachineBlockDefinition.RenderState.UNFORMED;
        }
        if ("idle".equals(normalized)) {
            return MachineBlockDefinition.RenderState.IDLE;
        }
        if ("working".equals(normalized)) {
            return MachineBlockDefinition.RenderState.WORKING;
        }
        throw new MachineConfigException("Machine config " + file + " has an invalid block state '" + raw + "'");
    }

    private static List<MachineBlockDefinition.TextureLevel> parseTextureLevels(JsonObject blockNode, Path file) {
        JsonArray levels = getArray(blockNode, "textureLevels");
        if (blockNode != null && blockNode.has("textureLevels") && levels == null) {
            throw new MachineConfigException("Machine config " + file + " has an invalid block.textureLevels; expected an array");
        }
        if (levels == null) {
            return Collections.emptyList();
        }
        List<MachineBlockDefinition.TextureLevel> out = new ArrayList<MachineBlockDefinition.TextureLevel>();
        Set<String> seen = new HashSet<String>();
        for (int index = 0; index < levels.size(); index++) {
            JsonElement element = levels.get(index);
            if (element == null || !element.isJsonObject()) {
                throw new MachineConfigException("Machine config " + file + " has an invalid block.textureLevels entry at index " + index);
            }
            JsonObject levelNode = element.getAsJsonObject();
            String content = requireString(levelNode, file, "content").toLowerCase(Locale.ROOT);
            if (!"fluid".equals(content) && !"gas".equals(content) && !"energy".equals(content)) {
                throw new MachineConfigException("Machine config " + file + " has an invalid block.textureLevels content '" + content + "'");
            }
            JsonElement ratioNode = levelNode.get("minFillRatio");
            if (ratioNode == null || !ratioNode.isJsonPrimitive() || !ratioNode.getAsJsonPrimitive().isNumber()) {
                throw new MachineConfigException("Machine config " + file + " is missing block.textureLevels.minFillRatio at index " + index);
            }
            double minFillRatio = ratioNode.getAsDouble();
            if (Double.isNaN(minFillRatio) || Double.isInfinite(minFillRatio) || minFillRatio < 0.0D || minFillRatio > 1.0D) {
                throw new MachineConfigException("Machine config " + file + " has block.textureLevels.minFillRatio outside 0..1 at index " + index);
            }
            String model = getOptionalString(levelNode, file, "model");
            if (model != null && !model.trim().isEmpty()) {
                validateResourceLocation(model, file, "block.textureLevels.model");
                model = model.trim().toLowerCase(Locale.ROOT);
            } else {
                model = null;
            }
            Map<String, String> textures = parseTextureSlots(levelNode, file, "block.textureLevels");
            String legacyTexture = getOptionalString(levelNode, file, "texture");
            if (legacyTexture != null && !legacyTexture.trim().isEmpty()) {
                validateResourceLocation(legacyTexture, file, "block.textureLevels.texture");
                String previous = textures.put("all", legacyTexture.trim().toLowerCase(Locale.ROOT));
                if (previous != null && !previous.equalsIgnoreCase(legacyTexture.trim())) {
                    throw new MachineConfigException(
                        "Machine config " + file + " has conflicting texture aliases in block.textureLevels at index " + index
                    );
                }
            }
            if (model == null && textures.isEmpty()) {
                throw new MachineConfigException(
                    "Machine config " + file + " block.textureLevels entry " + index + " must define model or textures"
                );
            }
            String key = content + "|" + minFillRatio;
            if (!seen.add(key)) {
                throw new MachineConfigException(
                    "Machine config " + file + " contains duplicate block.textureLevels entry for " + content + " at " + minFillRatio
                );
            }
            out.add(new MachineBlockDefinition.TextureLevel(content, minFillRatio, model, textures));
        }
        return out;
    }

    private static List<MachineComponentDefinition> parseComponents(JsonObject root, Path file) {
        JsonArray array = getArray(root, "components");
        if (array == null) {
            throw new MachineConfigException("Machine config " + file + " is missing components");
        }

        List<MachineComponentDefinition> out = new ArrayList<MachineComponentDefinition>();
        Map<String, Integer> legacyTypeCounts = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element == null || element.isJsonNull()) {
                throw new MachineConfigException("Machine config " + file + " has a null component at index " + i);
            }

            if (element.isJsonPrimitive()) {
                if (!element.getAsJsonPrimitive().isString()) {
                    throw new MachineConfigException(
                        "Machine config " + file + " has an invalid legacy component at index " + i
                    );
                }
                String normalizedType = MachineComponentTypes.normalize(element.getAsString());
                if (!MachineComponentTypes.isKnown(normalizedType)) {
                    throw new MachineConfigException(
                        "Machine config " + file + " has an illegal component type '"
                            + element.getAsString() + "' at index " + i
                    );
                }
                int occurrence = legacyTypeCounts.containsKey(normalizedType)
                    ? legacyTypeCounts.get(normalizedType).intValue() + 1
                    : 1;
                legacyTypeCounts.put(normalizedType, Integer.valueOf(occurrence));
                String generatedId = occurrence == 1 ? normalizedType : normalizedType + "_" + occurrence;
                JsonObject raw = new JsonObject();
                raw.addProperty("type", normalizedType);
                out.add(new MachineComponentDefinition(
                    normalizedType,
                    generatedId,
                    true,
                    null,
                    MachineComponentTypes.kind(normalizedType),
                    MachineComponentTypes.ioFromType(normalizedType),
                    null,
                    null,
                    raw
                ));
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
            if (id == null || id.trim().isEmpty()) {
                throw new MachineConfigException(
                    "Machine config " + file + " is missing required component id at index " + i
                );
            }
            id = normalizeComponentId(id, file, i);
            String kind = MachineComponentTypes.kind(normalizedType);
            String io = parseComponentIo(component, file, i, normalizedType);
            Integer slots = parseComponentSlots(component, kind, file, i);
            Long capacity = parseComponentCapacity(component, kind, file, i);
            out.add(new MachineComponentDefinition(
                normalizedType,
                id,
                enabled,
                displayName,
                kind,
                io,
                slots,
                capacity,
                component
            ));
        }
        return out;
    }

    private static ControllerType parseControllerType(JsonObject root, String id, Path file) {
        String raw = getOptionalString(root, file, "controllerType");
        if (raw == null || raw.trim().isEmpty()) {
            return ControllerType.AUTO;
        }
        ControllerType parsed = ControllerType.fromString(raw);
        if (parsed == ControllerType.AUTO && !"auto".equals(raw.trim().toLowerCase(Locale.ROOT))) {
            throw new MachineConfigException("Machine config " + file + " has an illegal controllerType '" + raw + "'");
        }
        return parsed;
    }

    private static String parseOptionalString(JsonObject obj, Path file, String key) {
        String value = getOptionalString(obj, file, key);
        return value == null ? null : value.trim();
    }

    private static String parseGuiStyle(JsonObject root, String id, Path file) {
        return parseStyle(root, file, "guiStyle", new ResourceLocation(MODID, id).toString(), true);
    }

    private static void validateComponents(List<MachineComponentDefinition> components, Path file) {
        Map<String, Integer> seen = new LinkedHashMap<String, Integer>();
        boolean hasEnabledRuntimeComponent = false;
        for (int i = 0; i < components.size(); i++) {
            MachineComponentDefinition component = components.get(i);
            if (component == null) {
                continue;
            }
            String id = component.getId();
            if (id == null || id.trim().isEmpty()) {
                throw new MachineConfigException(
                    "Machine config " + file + " is missing required component id at index " + i
                );
            }
            String normalized = normalizeComponentId(id, file, i);
            Integer previousIndex = seen.get(normalized);
            if (previousIndex != null) {
                throw new MachineConfigException(
                    "Machine config " + file + " contains duplicate component id '" + id
                        + "' at indexes " + previousIndex + " and " + i
                );
            }
            seen.put(normalized, i);
            String kind = component.getKind() == null || component.getKind().trim().isEmpty()
                ? MachineComponentTypes.kind(component.getType())
                : component.getKind();
            if (!MachineComponentTypes.isRuntimeSupported(component.getType())) {
                String alternative = MachineComponentTypes.isReservedUnsupported(component.getType())
                    ? "; reserved for a later MMCEGE special-component API, "
                        + MachineComponentTypes.unsupportedAlternative(component.getType())
                    : "";
                throw new MachineConfigException(
                    "Machine config " + file + " has unsupported 0.1.0 component type '"
                        + component.getType() + "' at index " + i + alternative
                );
            }
            if (!component.isEnabled()) {
                continue;
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
        String rawIo = component.getIo();
        if (suffixIo != null && rawIo != null && !suffixIo.equals(rawIo)) {
            throw new MachineConfigException(
                "Machine config " + file + " has conflicting io '" + rawIo + "' for component type '" + component.getType()
                    + "' at index " + index
            );
        }
    }

    private static void validateComponentCapacity(MachineComponentDefinition component, String kind, Path file, int index) {
        if ("item".equals(kind) || component.getCapacity() == null) {
            return;
        }
        long capacity = component.getCapacity().longValue();
        if (capacity < 1L || capacity > MAX_COMPONENT_CAPACITY) {
            throw new MachineConfigException(
                "Machine config " + file + " has capacity out of range at component index " + index
                    + ": " + capacity + " (allowed 1.." + MAX_COMPONENT_CAPACITY + ")"
            );
        }
    }

    private static void validateComponentSlots(MachineComponentDefinition component, String kind, Path file, int index) {
        if (!"item".equals(kind)) {
            if (component.getSlots() != null) {
                throw new MachineConfigException("Machine config " + file + " may only set slots on item components at index " + index);
            }
            return;
        }
        Integer slotsValue = component.getSlots();
        if (slotsValue == null) {
            return;
        }
        int slots = slotsValue.intValue();
        if (slots < 1 || slots > MAX_ITEM_SLOTS) {
            throw new MachineConfigException(
                "Machine config " + file + " has slots out of range at component index " + index
                    + ": " + slots + " (allowed 1.." + MAX_ITEM_SLOTS + ")"
            );
        }
    }

    @Nullable
    private static String parseComponentIo(JsonObject raw, Path file, int index, String normalizedType) {
        String suffixIo = MachineComponentTypes.ioFromType(normalizedType);
        String explicitIo = getOptionalIo(raw, file, index);
        if (suffixIo != null && explicitIo != null && !suffixIo.equals(explicitIo)) {
            throw new MachineConfigException(
                "Machine config " + file + " has conflicting io '" + explicitIo + "' for component type '" + normalizedType
                    + "' at index " + index
            );
        }
        return explicitIo == null ? suffixIo : explicitIo;
    }

    @Nullable
    private static Integer parseComponentSlots(JsonObject raw, String kind, Path file, int index) {
        if (raw == null || !raw.has("slots") || raw.get("slots").isJsonNull()) {
            return null;
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
        return Integer.valueOf(slots);
    }

    @Nullable
    private static Long parseComponentCapacity(JsonObject raw, String kind, Path file, int index) {
        if (raw == null || !raw.has("capacity") || raw.get("capacity").isJsonNull()) {
            return null;
        }
        if ("item".equals(kind)) {
            return null;
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
        return Long.valueOf(capacity);
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

    private static String parseStyle(JsonObject root, Path file, String key, String fallback, boolean required) {
        String raw = getOptionalString(root, file, key);
        if (raw == null || raw.trim().isEmpty()) {
            if (!required) {
                return fallback;
            }
            return validateStyleValue(fallback, file, key);
        }
        return validateStyleValue(raw, file, key);
    }

    @Nullable
    private static String parseOptionalStyle(JsonObject root, Path file, String key) {
        return parseStyle(root, file, key, null, false);
    }

    private static String validateStyleValue(String raw, Path file, String key) {
        try {
            return parseStrictResourceLocation(raw).toString();
        } catch (Exception ex) {
            throw new MachineConfigException("Machine config " + file + " has an illegal " + key + " '" + raw + "'", ex);
        }
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

    private static String normalizeComponentId(String id, Path file, int index) {
        String normalized = normalizeId(id);
        if (normalized.isEmpty()
            || !normalized.equals(stripNamespace(normalized))
            || !normalized.matches(RESOURCE_PATH_PATTERN)) {
            throw new MachineConfigException(
                "Machine config " + file + " has an illegal path-only component id '"
                    + id + "' at index " + index
            );
        }
        return normalized;
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
            parseStrictResourceLocation(value);
        } catch (Exception ex) {
            throw new MachineConfigException("Machine config " + file + " has an illegal " + key + " '" + value + "'", ex);
        }
    }

    private static ResourceLocation parseStrictResourceLocation(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        ResourceLocation location = new ResourceLocation(normalized);
        if (!location.getNamespace().matches(RESOURCE_NAMESPACE_PATTERN)
            || !location.getPath().matches(RESOURCE_PATH_PATTERN)) {
            throw new IllegalArgumentException("Invalid resource location: " + raw);
        }
        return location;
    }
}
