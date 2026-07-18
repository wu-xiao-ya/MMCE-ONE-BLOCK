package com.fushu.mmceoneblock.common.config;

import net.minecraft.tileentity.TileEntity;

public interface SingleBlockMachineTileFactory {
    TileEntity create(MachineDefinition definition);

    default TileEntity create(OneBlockRuntimeBinding binding) {
        return create(binding.getDefinition());
    }
}
