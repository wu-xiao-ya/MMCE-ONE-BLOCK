package com.fushu.mmceoneblock.common.item;

import com.fushu.mmceoneblock.common.block.BlockSingleBlockMachineController;
import com.fushu.mmceoneblock.common.config.MachineDefinition;
import com.fushu.mmceoneblock.common.registry.MachineRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

public class ItemBlockSingleBlockMachineController extends ItemBlock {
    private final MachineDefinition definition;

    public ItemBlockSingleBlockMachineController(BlockSingleBlockMachineController block, MachineDefinition definition) {
        super(block);
        this.definition = definition;
        setRegistryName(block.getRegistryName());
        setTranslationKey(MachineRegistry.MODID + "." + definition.getId());
    }

    @Nonnull
    @Override
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        String displayName = definition.getDisplayName();
        return displayName == null || displayName.trim().isEmpty() ? super.getItemStackDisplayName(stack) : displayName;
    }

    @Override
    public BlockSingleBlockMachineController getBlock() {
        return (BlockSingleBlockMachineController) super.getBlock();
    }
}
