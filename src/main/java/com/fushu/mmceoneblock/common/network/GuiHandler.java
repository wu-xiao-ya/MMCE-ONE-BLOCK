package com.fushu.mmceoneblock.common.network;

import com.fushu.mmceoneblock.common.container.ContainerSingleBlockController;
import hellfirepvp.modularmachinery.common.tiles.TileMachineController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

public class GuiHandler implements IGuiHandler {
    public static final int GUI_SINGLE_BLOCK_CONTROLLER = 1;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != GUI_SINGLE_BLOCK_CONTROLLER || !canOpen(player, world, x, y, z)) {
            return null;
        }

        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        if (!(tile instanceof TileMachineController)) {
            return null;
        }
        return new ContainerSingleBlockController((TileMachineController) tile, player);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != GUI_SINGLE_BLOCK_CONTROLLER || !canOpen(player, world, x, y, z)) {
            return null;
        }

        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        if (!(tile instanceof TileMachineController)) {
            return null;
        }

        ContainerSingleBlockController container = new ContainerSingleBlockController((TileMachineController) tile, player);
        Object gui = createClientGui(container);
        return gui;
    }

    private boolean canOpen(EntityPlayer player, World world, int x, int y, int z) {
        if (player == null || world == null) {
            return false;
        }

        BlockPos pos = new BlockPos(x, y, z);
        return world.isBlockLoaded(pos)
            && player.world == world
            && player.getDistanceSqToCenter(pos) <= 64.0D;
    }

    @Nullable
    private Object createClientGui(ContainerSingleBlockController container) {
        try {
            Class<?> bridgeClass = Class.forName("com.fushu.mmceoneblock.client.ClientGuiBridge");
            Method method = bridgeClass.getMethod("createSingleBlockControllerGui", ContainerSingleBlockController.class);
            return method.invoke(null, container);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
