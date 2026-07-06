package com.fushu.mmceoneblock.proxy;

import com.fushu.mmceoneblock.MMCEOneBlock;
import com.fushu.mmceoneblock.common.network.GuiHandler;
import com.fushu.mmceoneblock.common.validation.DevValidationRunner;
import net.minecraftforge.fml.common.network.NetworkRegistry;

public class CommonProxy {
    public void preInit() {
        NetworkRegistry.INSTANCE.registerGuiHandler(MMCEOneBlock.instance, new GuiHandler());
        DevValidationRunner.registerIfEnabled();
    }
}
