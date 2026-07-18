package com.fushu.mmceoneblock.common.network;

import com.fushu.mmceoneblock.common.container.ContainerSingleBlockController;
import com.fushu.mmceoneblock.common.container.ContainerSingleBlockFactoryController;
import com.fushu.mmceoneblock.common.tile.TileSingleBlockFactoryController;
import com.fushu.mmceoneblock.common.tile.TileSingleBlockMachineController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

public class GuiHandler implements IGuiHandler {
    public static final int GUI_SINGLE_BLOCK_CONTROLLER = 1;
    public static final int GUI_SINGLE_BLOCK_FACTORY_CONTROLLER = 2;
    private static final ClientGuiBridgeLookup CLIENT_GUI_BRIDGE = new ClientGuiBridgeLookup();

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (!canOpen(player, world, x, y, z)) {
            return null;
        }
        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));

        return createServerGuiElement(id, tile, player);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (!canOpen(player, world, x, y, z)) {
            return null;
        }
        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));

        return createClientGuiElement(id, tile, player);
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
    static Object createServerGuiElement(int id, @Nullable TileEntity tile, EntityPlayer player) {
        if (id == GUI_SINGLE_BLOCK_FACTORY_CONTROLLER) {
            if (!isSingleBlockFactoryControllerTile(tile)) {
                return null;
            }
            return createFactoryContainer((TileSingleBlockFactoryController) tile, player);
        }
        if (id == GUI_SINGLE_BLOCK_CONTROLLER) {
            if (!isSingleBlockMachineControllerTile(tile)) {
                return null;
            }
            return new ContainerSingleBlockController((TileSingleBlockMachineController) tile, player);
        }
        return null;
    }

    @Nullable
    static Object createClientGuiElement(int id, @Nullable TileEntity tile, EntityPlayer player) {
        if (id == GUI_SINGLE_BLOCK_FACTORY_CONTROLLER) {
            if (!isSingleBlockFactoryControllerTile(tile)) {
                return null;
            }
            ContainerSingleBlockFactoryController container = createFactoryContainer((TileSingleBlockFactoryController) tile, player);
            Object gui = createFactoryClientGui(container);
            return gui;
        }
        if (id == GUI_SINGLE_BLOCK_CONTROLLER) {
            if (!isSingleBlockMachineControllerTile(tile)) {
                return null;
            }
            ContainerSingleBlockController container = new ContainerSingleBlockController((TileSingleBlockMachineController) tile, player);
            Object gui = createClientGui(container);
            return gui;
        }
        return null;
    }

    static boolean isSingleBlockMachineControllerTile(@Nullable TileEntity tile) {
        return tile instanceof TileSingleBlockMachineController;
    }

    static boolean isSingleBlockFactoryControllerTile(@Nullable TileEntity tile) {
        return tile instanceof TileSingleBlockFactoryController;
    }

    static boolean acceptsServerGuiElement(int id, @Nullable TileEntity tile) {
        if (id == GUI_SINGLE_BLOCK_FACTORY_CONTROLLER) {
            return isSingleBlockFactoryControllerTile(tile);
        }
        if (id == GUI_SINGLE_BLOCK_CONTROLLER) {
            return isSingleBlockMachineControllerTile(tile);
        }
        return false;
    }

    static boolean acceptsClientGuiElement(int id, @Nullable TileEntity tile) {
        return acceptsServerGuiElement(id, tile);
    }

    private static ContainerSingleBlockFactoryController createFactoryContainer(TileSingleBlockFactoryController owner, EntityPlayer opening) {
        return new ContainerSingleBlockFactoryController(owner, opening);
    }

    @Nullable
    private static Object createClientGui(ContainerSingleBlockController container) {
        return CLIENT_GUI_BRIDGE.openSingleBlockControllerGui(container);
    }

    @Nullable
    private static Object createFactoryClientGui(ContainerSingleBlockFactoryController container) {
        return CLIENT_GUI_BRIDGE.openSingleBlockFactoryControllerGui(container);
    }

    static ClientGuiBridgeLookup getClientGuiBridgeLookup() {
        return CLIENT_GUI_BRIDGE;
    }

    static final class ClientGuiBridgeLookup {
        private static final String BRIDGE_CLASS_NAME = "com.fushu.mmceoneblock.client.ClientGuiBridge";

        private volatile boolean bridgeClassResolved;
        @Nullable
        private volatile Class<?> bridgeClass;
        private volatile boolean machineControllerMethodResolved;
        @Nullable
        private volatile Method machineControllerMethod;
        private volatile boolean factoryControllerMethodResolved;
        @Nullable
        private volatile Method factoryControllerMethod;

        @Nullable
        Object openSingleBlockControllerGui(ContainerSingleBlockController container) {
            Method method = resolveMachineControllerMethod();
            if (method == null) {
                return null;
            }
            try {
                return method.invoke(null, container);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        Object openSingleBlockFactoryControllerGui(ContainerSingleBlockFactoryController container) {
            Method method = resolveFactoryControllerMethod();
            if (method == null) {
                return null;
            }
            try {
                return method.invoke(null, container);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        Class<?> resolveBridgeClass() {
            if (!bridgeClassResolved) {
                synchronized (this) {
                    if (!bridgeClassResolved) {
                        bridgeClass = loadBridgeClass();
                        bridgeClassResolved = true;
                    }
                }
            }
            return bridgeClass;
        }

        @Nullable
        Method resolveMachineControllerMethod() {
            if (!machineControllerMethodResolved) {
                synchronized (this) {
                    if (!machineControllerMethodResolved) {
                        machineControllerMethod = loadMethod("createSingleBlockControllerGui", ContainerSingleBlockController.class);
                        machineControllerMethodResolved = true;
                    }
                }
            }
            return machineControllerMethod;
        }

        @Nullable
        Method resolveFactoryControllerMethod() {
            if (!factoryControllerMethodResolved) {
                synchronized (this) {
                    if (!factoryControllerMethodResolved) {
                        factoryControllerMethod = loadMethod("createSingleBlockFactoryControllerGui", ContainerSingleBlockFactoryController.class);
                        factoryControllerMethodResolved = true;
                    }
                }
            }
            return factoryControllerMethod;
        }

        @Nullable
        private Class<?> loadBridgeClass() {
            try {
                return Class.forName(BRIDGE_CLASS_NAME);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        private Method loadMethod(String name, Class<?> parameterType) {
            Class<?> bridge = resolveBridgeClass();
            if (bridge == null) {
                return null;
            }
            try {
                return bridge.getMethod(name, parameterType);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }
    }
}
