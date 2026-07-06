package com.fushu.mmceoneblock.client;

import com.fushu.mmceoneblock.common.container.ContainerSingleBlockController;
import hellfirepvp.modularmachinery.client.gui.GuiMachineController;
import hellfirepvp.modularmachinery.common.container.ContainerController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;

@SideOnly(Side.CLIENT)
public final class ClientGuiBridge {
    private static final String MMCEGE_RESIZABLE_GUI = "com.fushu.mmceguiext.client.gui.GuiMachineControllerResizable";

    private ClientGuiBridge() {
    }

    public static GuiScreen createSingleBlockControllerGui(ContainerSingleBlockController container) {
        GuiScreen gui = tryCreateMmceGeGui(container);
        if (gui != null) {
            return gui;
        }

        EntityPlayer player = Minecraft.getMinecraft().player;
        return new GuiMachineController(container.getOwner(), player);
    }

    @Nullable
    private static GuiScreen tryCreateMmceGeGui(ContainerSingleBlockController container) {
        // Keep this soft bridge isolated so servers can load without client-only MMCEGE GUI classes.
        try {
            Class<?> guiClass = Class.forName(MMCEGE_RESIZABLE_GUI);
            return tryConstructGui(guiClass, container);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static GuiScreen tryConstructGui(Class<?> guiClass, ContainerSingleBlockController container)
        throws ReflectiveOperationException {
        for (Constructor<?> ctor : guiClass.getConstructors()) {
            Class<?>[] parameterTypes = ctor.getParameterTypes();
            if (parameterTypes.length == 1 && parameterTypes[0].isAssignableFrom(container.getClass())) {
                return castGui(ctor.newInstance(container));
            }
            if (parameterTypes.length == 1 && parameterTypes[0].isAssignableFrom(ContainerController.class)) {
                return castGui(ctor.newInstance(container));
            }
            if (parameterTypes.length == 2
                && parameterTypes[0].isAssignableFrom(container.getOwner().getClass())
                && parameterTypes[1].isAssignableFrom(EntityPlayer.class)) {
                return castGui(ctor.newInstance(container.getOwner(), Minecraft.getMinecraft().player));
            }
        }
        return null;
    }

    @Nullable
    private static GuiScreen castGui(Object gui) {
        return gui instanceof GuiScreen ? (GuiScreen) gui : null;
    }
}
