package com.fushu.mmceoneblock;

import com.fushu.mmceoneblock.proxy.CommonProxy;
import com.fushu.mmceoneblock.common.registry.ModRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = MMCEOneBlock.MODID,
    name = MMCEOneBlock.NAME,
    version = MMCEOneBlock.VERSION,
    dependencies = "required-after:modularmachinery;required-after:mmceguiext@[1.3.0,)",
    acceptedMinecraftVersions = "[1.12.2]"
)
public class MMCEOneBlock {
    public static final String MODID = "mmceoneblock";
    public static final String NAME = "MMCE One Block";
    public static final String VERSION = Tags.VERSION;

    @Mod.Instance(MODID)
    public static MMCEOneBlock instance;

    @SidedProxy(
        clientSide = "com.fushu.mmceoneblock.proxy.ClientProxy",
        serverSide = "com.fushu.mmceoneblock.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    public static Logger log;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        log = event.getModLog();
        proxy.preInit();
        ModRegistry.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ModRegistry.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        ModRegistry.postInit();
    }
}
