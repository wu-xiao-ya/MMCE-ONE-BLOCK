package com.fushu.mmceoneblock.common.tile;

import com.fushu.mmceoneblock.common.block.BlockSingleBlockMachineController;
import com.fushu.mmceoneblock.common.config.MachineDefinition;
import com.fushu.mmceoneblock.common.config.StrictResourceLocation;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.TaggedPositionBlockArray;
import hellfirepvp.modularmachinery.common.util.BlockArray;
import hellfirepvp.modularmachinery.common.util.IBlockStateDescriptor;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Collections;

final class OneBlockControllerRuntimeSupport {
    private OneBlockControllerRuntimeSupport() {
    }

    @Nullable
    static MachineDefinition restoreDefinitionFromBlock(@Nullable World world,
                                                        @Nullable BlockPos pos) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) {
            return null;
        }
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (!(block instanceof BlockSingleBlockMachineController)) {
            return null;
        }
        return ((BlockSingleBlockMachineController) block).getDefinition();
    }

    @Nullable
    static DynamicMachine resolveBackingMachine(MachineDefinition definition) {
        if (definition == null) {
            return null;
        }
        return hellfirepvp.modularmachinery.common.machine.MachineRegistry.getRegistry()
            .getMachine(definition.getMachine());
    }

    static TaggedPositionBlockArray createSyntheticPattern(@Nullable World world, @Nullable BlockPos pos) {
        TaggedPositionBlockArray pattern = new TaggedPositionBlockArray();
        if (world == null || pos == null || !world.isBlockLoaded(pos)) {
            return pattern;
        }
        IBlockState state = world.getBlockState(pos);
        BlockArray.BlockInformation info = new BlockArray.BlockInformation(
            Collections.singletonList(IBlockStateDescriptor.of(state.getBlock()))
        );
        pattern.addBlock(BlockPos.ORIGIN, info);
        pattern.flushTileBlocksCache();
        return pattern;
    }

    @Nullable
    static ResourceLocation resolveGuiStyle(@Nullable MachineDefinition definition) {
        if (definition == null) {
            return null;
        }
        return parseStyle(definition.getGuiStyle());
    }

    @Nullable
    static ResourceLocation resolveFactoryGuiStyle(@Nullable MachineDefinition definition) {
        if (definition == null) {
            return null;
        }
        ResourceLocation factoryStyle = parseStyle(definition.getFactoryGuiStyle());
        return factoryStyle != null ? factoryStyle : parseStyle(definition.getGuiStyle());
    }

    @Nullable
    private static ResourceLocation parseStyle(@Nullable String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return StrictResourceLocation.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
