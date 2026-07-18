package com.fushu.mmceoneblock.common.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MachineBlockDefinition {
    private final String model;
    private final String itemModel;
    private final Map<String, String> textures;
    private final Map<RenderState, ModelVariant> stateVariants;
    private final List<TextureLevel> textureLevels;

    public MachineBlockDefinition(String model, String texture) {
        this(model, null, singleTexture(texture), Collections.<RenderState, ModelVariant>emptyMap(),
            Collections.<TextureLevel>emptyList());
    }

    public MachineBlockDefinition(String model,
                                  String itemModel,
                                  Map<String, String> textures,
                                  Map<RenderState, ModelVariant> stateVariants,
                                  List<TextureLevel> textureLevels) {
        this.model = model;
        this.itemModel = itemModel;
        this.textures = immutableMap(textures);
        this.stateVariants = immutableStateMap(stateVariants);
        this.textureLevels = immutableLevels(textureLevels);
    }

    public String getModel() {
        return model;
    }

    public String getTexture() {
        return this.textures.get("all");
    }

    public String getItemModel() {
        return itemModel;
    }

    public Map<String, String> getTextures() {
        return textures;
    }

    public ModelVariant getStateVariant(RenderState state) {
        return state == null ? null : this.stateVariants.get(state);
    }

    public Map<RenderState, ModelVariant> getStateVariants() {
        return stateVariants;
    }

    public List<TextureLevel> getTextureLevels() {
        return textureLevels;
    }

    private static Map<String, String> singleTexture(String texture) {
        Map<String, String> textures = new LinkedHashMap<String, String>();
        if (texture != null && !texture.trim().isEmpty()) {
            textures.put("all", texture);
        }
        return textures;
    }

    private static Map<String, String> immutableMap(Map<String, String> input) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (input != null) {
            out.putAll(input);
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<RenderState, ModelVariant> immutableStateMap(Map<RenderState, ModelVariant> input) {
        Map<RenderState, ModelVariant> out = new LinkedHashMap<RenderState, ModelVariant>();
        if (input != null) {
            out.putAll(input);
        }
        return Collections.unmodifiableMap(out);
    }

    private static List<TextureLevel> immutableLevels(List<TextureLevel> input) {
        return Collections.unmodifiableList(input == null
            ? new ArrayList<TextureLevel>()
            : new ArrayList<TextureLevel>(input));
    }

    public enum RenderState {
        UNFORMED,
        IDLE,
        WORKING
    }

    public static final class ModelVariant {
        private final String model;
        private final Map<String, String> textures;

        public ModelVariant(String model, Map<String, String> textures) {
            this.model = model;
            this.textures = immutableMap(textures);
        }

        public String getModel() {
            return model;
        }

        public Map<String, String> getTextures() {
            return textures;
        }
    }

    public static final class TextureLevel {
        private final String content;
        private final double minFillRatio;
        private final String model;
        private final Map<String, String> textures;

        public TextureLevel(String content, double minFillRatio, String model, Map<String, String> textures) {
            this.content = content;
            this.minFillRatio = minFillRatio;
            this.model = model;
            this.textures = immutableMap(textures);
        }

        public String getContent() {
            return content;
        }

        public double getMinFillRatio() {
            return minFillRatio;
        }

        public String getModel() {
            return model;
        }

        public Map<String, String> getTextures() {
            return textures;
        }
    }
}
