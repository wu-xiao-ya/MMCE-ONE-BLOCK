package com.fushu.mmceoneblock.common.registry;

import com.fushu.mmceoneblock.MMCEOneBlock;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = MMCEOneBlock.MODID)
public final class ModRegistry {
    private ModRegistry() {
    }

    public static void preInit() {
        MachineRegistry.bootstrap();
    }

    public static void init() {
    }

    public static void postInit() {
        MachineRegistry.validateLoadedMachines();
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        MachineRegistry.registerBlocks(event.getRegistry());
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        MachineRegistry.registerItems(event.getRegistry());
    }
}
