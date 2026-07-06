package com.fushu.mmceoneblock.client;

import com.fushu.mmceoneblock.MMCEOneBlock;
import com.fushu.mmceoneblock.common.registry.MachineRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Locale;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = MMCEOneBlock.MODID, value = Side.CLIENT)
public final class SingleBlockMachineModelRegistry {
    private static final ModelResourceLocation DEFAULT_BLOCK_MODEL =
        new ModelResourceLocation(new ResourceLocation(MMCEOneBlock.MODID, "single_block_machine_controller"), "normal");
    private static final ModelResourceLocation DEFAULT_ITEM_MODEL =
        new ModelResourceLocation(new ResourceLocation(MMCEOneBlock.MODID, "single_block_machine_controller"), "inventory");

    private SingleBlockMachineModelRegistry() {
    }

    @SubscribeEvent
    public static void onModelRegister(ModelRegistryEvent event) {
        for (MachineRegistry.MachineEntry entry : MachineRegistry.snapshot().values()) {
            Block block = entry.getBlock();
            Item item = entry.getItem();
            final ModelResourceLocation blockModel = resolveModel(entry.getDefinition().getBlock().getModel(), "normal");
            ModelResourceLocation itemModel = resolveModel(entry.getDefinition().getBlock().getModel(), "inventory");

            ModelLoader.setCustomStateMapper(block, new StateMapperBase() {
                @Override
                protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                    return blockModel;
                }
            });
            ModelLoader.setCustomModelResourceLocation(item, 0, itemModel);
        }
    }

    private static ModelResourceLocation resolveModel(String rawModel, String variant) {
        if (rawModel == null || rawModel.trim().isEmpty()) {
            return "inventory".equals(variant) ? DEFAULT_ITEM_MODEL : DEFAULT_BLOCK_MODEL;
        }
        try {
            return new ModelResourceLocation(new ResourceLocation(rawModel.trim().toLowerCase(Locale.ROOT)), variant);
        } catch (RuntimeException ex) {
            return "inventory".equals(variant) ? DEFAULT_ITEM_MODEL : DEFAULT_BLOCK_MODEL;
        }
    }
}
