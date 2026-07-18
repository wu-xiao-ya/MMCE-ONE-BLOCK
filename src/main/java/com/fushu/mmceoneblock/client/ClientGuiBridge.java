package com.fushu.mmceoneblock.client;

import com.fushu.mmceguiext.api.gui.MachineGuiBridge;
import com.fushu.mmceoneblock.common.container.ContainerSingleBlockController;
import com.fushu.mmceoneblock.common.container.ContainerSingleBlockFactoryController;
import hellfirepvp.modularmachinery.client.gui.GuiFactoryController;
import hellfirepvp.modularmachinery.client.gui.GuiMachineController;
import hellfirepvp.modularmachinery.common.tiles.TileFactoryController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientGuiBridge {
    private ClientGuiBridge() {
    }

    public static GuiScreen createSingleBlockControllerGui(ContainerSingleBlockController container) {
        return createSingleBlockControllerGuiInternal(container);
    }

    static GuiScreen createSingleBlockControllerGuiInternal(ContainerSingleBlockController container) {
        try {
            return MachineGuiBridge.createMachineControllerScreen(container);
        } catch (LinkageError ignored) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            return new GuiMachineController(container.getOwner(), player);
        }
    }

    public static GuiScreen createSingleBlockFactoryControllerGui(ContainerSingleBlockFactoryController container) {
        return createSingleBlockFactoryControllerGuiInternal(container);
    }

    static GuiScreen createSingleBlockFactoryControllerGuiInternal(ContainerSingleBlockFactoryController container) {
        try {
            return MachineGuiBridge.createFactoryControllerScreen(container);
        } catch (LinkageError ignored) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            TileFactoryController owner = container.getOwner();
            return new GuiFactoryController(owner, player);
        }
    }
}
