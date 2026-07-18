package com.fushu.mmceoneblock.client;

import com.fushu.mmceoneblock.MMCEOneBlock;
import com.fushu.mmceoneblock.client.model.OneBlockBakedModel;
import com.fushu.mmceoneblock.common.registry.MachineRegistry;
import hellfirepvp.modularmachinery.common.block.BlockController;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = MMCEOneBlock.MODID, value = Side.CLIENT)
public final class SingleBlockMachineModelRegistry {
    private static final ModelResourceLocation DEFAULT_BLOCK_MODEL =
        new ModelResourceLocation(new ResourceLocation(MMCEOneBlock.MODID, "single_block_machine_controller"), "facing=north,formed=false");
    private static final ModelResourceLocation DEFAULT_ITEM_MODEL =
        new ModelResourceLocation(new ResourceLocation(MMCEOneBlock.MODID, "single_block_machine_controller"), "inventory");

    private SingleBlockMachineModelRegistry() {
    }

    @SubscribeEvent
    public static void onModelRegister(ModelRegistryEvent event) {
        for (MachineRegistry.MachineEntry entry : MachineRegistry.snapshot().values()) {
            Block block = entry.getBlock();
            Item item = entry.getItem();

            ModelLoader.setCustomStateMapper(block, new StateMapperBase() {
                @Override
                protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                    return stateModel(state);
                }
            });
            ModelLoader.setCustomModelResourceLocation(item, 0, DEFAULT_ITEM_MODEL);
        }
    }

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        wrap(event, DEFAULT_ITEM_MODEL);
        for (net.minecraft.util.EnumFacing facing : net.minecraft.util.EnumFacing.HORIZONTALS) {
            wrap(event, new ModelResourceLocation(
                new ResourceLocation(MMCEOneBlock.MODID, "single_block_machine_controller"),
                "facing=" + facing.getName() + ",formed=false"
            ));
            wrap(event, new ModelResourceLocation(
                new ResourceLocation(MMCEOneBlock.MODID, "single_block_machine_controller"),
                "facing=" + facing.getName() + ",formed=true"
            ));
        }
    }

    private static ModelResourceLocation stateModel(IBlockState state) {
        if (state == null) {
            return DEFAULT_BLOCK_MODEL;
        }
        net.minecraft.util.EnumFacing facing = state.getPropertyKeys().contains(BlockController.FACING)
            ? state.getValue(BlockController.FACING)
            : net.minecraft.util.EnumFacing.NORTH;
        boolean formed = state.getPropertyKeys().contains(BlockController.FORMED)
            && state.getValue(BlockController.FORMED);
        return new ModelResourceLocation(
            new ResourceLocation(MMCEOneBlock.MODID, "single_block_machine_controller"),
            "facing=" + facing.getName() + ",formed=" + formed
        );
    }

    private static void wrap(ModelBakeEvent event, ModelResourceLocation location) {
        IBakedModel baked = event.getModelRegistry().getObject(location);
        if (baked != null && !(baked instanceof OneBlockBakedModel)) {
            event.getModelRegistry().putObject(location, new OneBlockBakedModel(baked));
        }
    }
}
