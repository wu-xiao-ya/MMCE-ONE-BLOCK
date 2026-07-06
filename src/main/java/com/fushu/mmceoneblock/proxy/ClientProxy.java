package com.fushu.mmceoneblock.proxy;

import com.fushu.mmceoneblock.client.ClientGuiValidationRunner;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        super.preInit();
        ClientGuiValidationRunner.registerIfEnabled();
    }
}
