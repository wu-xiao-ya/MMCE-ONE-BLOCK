package com.fushu.mmceoneblock.client.model;

import com.fushu.mmceguiext.api.client.model.RetexturedModelCache;
import com.fushu.mmceoneblock.common.block.BlockSingleBlockMachineController;
import com.fushu.mmceoneblock.common.item.ItemBlockSingleBlockMachineController;
import com.fushu.mmceoneblock.common.model.OneBlockModelResolver;
import com.fushu.mmceoneblock.common.model.OneBlockModelState;
import com.fushu.mmceoneblock.common.model.OneBlockRenderState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OneBlockBakedModel extends BakedModelWrapper<IBakedModel> {
    private final RetexturedModelCache modelCache = new RetexturedModelCache();
    private final ItemOverrideList overrides = new OneBlockItemOverrides();

    public OneBlockBakedModel(IBakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        if (state != null && state.getBlock() instanceof BlockSingleBlockMachineController) {
            BlockSingleBlockMachineController block = (BlockSingleBlockMachineController) state.getBlock();
            if (block.getDefinition() == null) {
                return super.getQuads(state, side, rand);
            }
            OneBlockModelResolver.ResolvedModel resolved = OneBlockModelResolver.resolveBlockModel(
                block.getDefinition().getBlock(),
                extractRenderState(state),
                facing(state)
            );
            if (resolved != null) {
                IBakedModel baked = bake(resolved.getSourceModel(), resolved.getTextures(), resolved.getFacing());
                if (baked != null) {
                    return baked.getQuads(state, side, rand);
                }
            }
        }
        return super.getQuads(state, side, rand);
    }

    @Override
    public ItemOverrideList getOverrides() {
        return overrides;
    }

    @Nullable
    private IBakedModel bake(@Nullable String source,
                             Map<String, String> textures,
                             EnumFacing facing) {
        ResourceLocation sourceModel = toModelLocation(source);
        if (sourceModel == null) {
            return null;
        }
        return modelCache.resolve(sourceModel, toTextureLocations(textures), rotationFor(facing), facing.getName());
    }

    @Nullable
    private static ResourceLocation toModelLocation(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            ResourceLocation location = new ResourceLocation(raw.trim());
            String path = location.getPath();
            if (!path.startsWith("block/") && !path.startsWith("item/")) {
                path = "block/" + path;
            }
            return new ResourceLocation(location.getNamespace(), path);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Map<String, ResourceLocation> toTextureLocations(Map<String, String> textures) {
        if (textures == null || textures.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, ResourceLocation> out = new LinkedHashMap<String, ResourceLocation>();
        for (Map.Entry<String, String> entry : textures.entrySet()) {
            try {
                if (entry.getKey() != null && entry.getValue() != null) {
                    out.put(entry.getKey(), new ResourceLocation(entry.getValue()));
                }
            } catch (RuntimeException ignored) {
                // Invalid config is rejected by the loader; keep a defensive renderer fallback.
            }
        }
        return out;
    }

    private static ModelRotation rotationFor(EnumFacing facing) {
        if (facing == EnumFacing.SOUTH) {
            return ModelRotation.X0_Y180;
        }
        if (facing == EnumFacing.WEST) {
            return ModelRotation.X0_Y270;
        }
        if (facing == EnumFacing.EAST) {
            return ModelRotation.X0_Y90;
        }
        return ModelRotation.X0_Y0;
    }

    private static EnumFacing facing(@Nullable IBlockState state) {
        if (state != null && state.getPropertyKeys().contains(BlockSingleBlockMachineController.FACING)) {
            return state.getValue(BlockSingleBlockMachineController.FACING);
        }
        return EnumFacing.NORTH;
    }

    @Nullable
    private static OneBlockRenderState extractRenderState(IBlockState state) {
        if (!(state instanceof IExtendedBlockState)) {
            return null;
        }
        IExtendedBlockState extended = (IExtendedBlockState) state;
        if (!extended.getUnlistedNames().contains(OneBlockModelState.RENDER_STATE)) {
            return null;
        }
        return extended.getValue(OneBlockModelState.RENDER_STATE);
    }

    private final class OneBlockItemOverrides extends ItemOverrideList {
        private OneBlockItemOverrides() {
            super(Collections.emptyList());
        }

        @Override
        public IBakedModel handleItemState(IBakedModel originalModel,
                                           ItemStack stack,
                                           @Nullable net.minecraft.world.World world,
                                           @Nullable net.minecraft.entity.EntityLivingBase entity) {
            if (!(stack.getItem() instanceof ItemBlockSingleBlockMachineController)) {
                return OneBlockBakedModel.this;
            }
            ItemBlockSingleBlockMachineController item = (ItemBlockSingleBlockMachineController) stack.getItem();
            if (item.getBlock().getDefinition() == null) {
                return OneBlockBakedModel.this;
            }
            OneBlockModelResolver.ResolvedModel resolved = OneBlockModelResolver.resolveItemModel(item.getBlock().getDefinition().getBlock());
            if (resolved == null) {
                return OneBlockBakedModel.this;
            }
            IBakedModel baked = bake(resolved.getSourceModel(), resolved.getTextures(), resolved.getFacing());
            return baked == null ? OneBlockBakedModel.this : baked;
        }
    }
}
