package com.fushu.mmceoneblock.common.model;

import com.fushu.mmceoneblock.common.config.MachineBlockDefinition;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OneBlockModelResolver {
    private OneBlockModelResolver() {
    }

    @Nullable
    public static ResolvedModel resolveBlockModel(@Nullable MachineBlockDefinition block,
                                                  @Nullable OneBlockRenderState renderState,
                                                  @Nullable EnumFacing facing) {
        if (block == null) {
            return null;
        }

        MachineBlockDefinition.RenderState resolvedState = resolveState(renderState);
        MachineBlockDefinition.ModelVariant stateVariant = block.getStateVariant(resolvedState);
        String sourceModel = stateVariant != null && stateVariant.getModel() != null
            ? stateVariant.getModel()
            : block.getModel();
        if (sourceModel == null || sourceModel.trim().isEmpty()) {
            return null;
        }

        Map<String, String> textures = new LinkedHashMap<String, String>(block.getTextures());
        if (stateVariant != null) {
            textures.putAll(stateVariant.getTextures());
        }

        MachineBlockDefinition.TextureLevel textureLevel = selectTextureLevel(block, renderState);
        if (textureLevel != null) {
            if (textureLevel.getModel() != null) {
                sourceModel = textureLevel.getModel();
            }
            textures.putAll(textureLevel.getTextures());
        }

        return new ResolvedModel(resolvedState, stateVariant, textureLevel, sourceModel, textures, normalizeFacing(facing));
    }

    @Nullable
    public static ResolvedModel resolveItemModel(@Nullable MachineBlockDefinition block) {
        if (block == null) {
            return null;
        }
        String sourceModel = block.getItemModel() == null ? block.getModel() : block.getItemModel();
        if (sourceModel == null || sourceModel.trim().isEmpty()) {
            return null;
        }
        return new ResolvedModel(null, null, null, sourceModel, block.getTextures(), EnumFacing.NORTH);
    }

    private static MachineBlockDefinition.RenderState resolveState(@Nullable OneBlockRenderState state) {
        if (state == null || !state.formed) {
            return MachineBlockDefinition.RenderState.UNFORMED;
        }
        return state.working ? MachineBlockDefinition.RenderState.WORKING : MachineBlockDefinition.RenderState.IDLE;
    }

    @Nullable
    private static MachineBlockDefinition.TextureLevel selectTextureLevel(MachineBlockDefinition block,
                                                                          @Nullable OneBlockRenderState state) {
        if (state == null || block.getTextureLevels().isEmpty()) {
            return null;
        }
        MachineBlockDefinition.TextureLevel selected = null;
        for (MachineBlockDefinition.TextureLevel level : block.getTextureLevels()) {
            double ratio = ratioFor(level.getContent(), state);
            if (ratio < level.getMinFillRatio()) {
                continue;
            }
            if (selected == null || level.getMinFillRatio() > selected.getMinFillRatio()) {
                selected = level;
            }
        }
        return selected;
    }

    private static double ratioFor(String content, OneBlockRenderState state) {
        if ("fluid".equals(content)) {
            return state.fluidFillRatio;
        }
        if ("gas".equals(content)) {
            return state.gasFillRatio;
        }
        if ("energy".equals(content)) {
            return state.energyFillRatio;
        }
        return 0.0D;
    }

    private static EnumFacing normalizeFacing(@Nullable EnumFacing facing) {
        return facing == null ? EnumFacing.NORTH : facing;
    }

    public static final class ResolvedModel {
        @Nullable
        private final MachineBlockDefinition.RenderState resolvedState;
        @Nullable
        private final MachineBlockDefinition.ModelVariant stateVariant;
        @Nullable
        private final MachineBlockDefinition.TextureLevel textureLevel;
        private final String sourceModel;
        private final Map<String, String> textures;
        private final EnumFacing facing;

        private ResolvedModel(@Nullable MachineBlockDefinition.RenderState resolvedState,
                              @Nullable MachineBlockDefinition.ModelVariant stateVariant,
                              @Nullable MachineBlockDefinition.TextureLevel textureLevel,
                              String sourceModel,
                              Map<String, String> textures,
                              EnumFacing facing) {
            this.resolvedState = resolvedState;
            this.stateVariant = stateVariant;
            this.textureLevel = textureLevel;
            this.sourceModel = sourceModel;
            this.textures = Collections.unmodifiableMap(new LinkedHashMap<String, String>(textures));
            this.facing = facing;
        }

        @Nullable
        public MachineBlockDefinition.RenderState getResolvedState() {
            return resolvedState;
        }

        @Nullable
        public MachineBlockDefinition.ModelVariant getStateVariant() {
            return stateVariant;
        }

        @Nullable
        public MachineBlockDefinition.TextureLevel getTextureLevel() {
            return textureLevel;
        }

        public String getSourceModel() {
            return sourceModel;
        }

        public Map<String, String> getTextures() {
            return textures;
        }

        public EnumFacing getFacing() {
            return facing;
        }
    }
}
