package com.fushu.mmceoneblock.client;

import com.fushu.mmceoneblock.MMCEOneBlock;
import com.fushu.mmceoneblock.common.block.BlockSingleBlockMachineController;
import com.fushu.mmceoneblock.common.container.ContainerSingleBlockController;
import com.fushu.mmceoneblock.common.network.GuiHandler;
import com.fushu.mmceoneblock.common.registry.MachineRegistry;
import com.fushu.mmceoneblock.common.tile.TileSingleBlockMachineController;
import hellfirepvp.modularmachinery.common.block.BlockController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.UUID;

@SideOnly(Side.CLIENT)
public final class ClientGuiValidationRunner {
    private static final String ENABLE_PROPERTY = "mmceoneblock.clientGuiValidation";
    private static final String TARGET_ID = "starter_controller";
    private static final String EXPECTED_GUI = "com.fushu.mmceguiext.client.gui.GuiMachineControllerResizable";
    private static final ResourceLocation EXPECTED_STYLE = new ResourceLocation("mmceoneblock", TARGET_ID);
    private static final int CLIENT_TILE_STABLE_TICKS = 40;
    private static boolean registered = false;

    private int ticks;
    private boolean launchedWorld;
    private boolean requestedServerPlacement;
    private boolean placedBlock;
    private boolean requestedGuiOpen;
    private boolean installedClientFallback;
    private boolean directFallbackGuiOpen;
    private boolean done;
    private BlockPos pos;
    private UUID playerId;
    private int placedAt;
    private int openedAt;
    private int clientTileReadySince;
    private TileSingleBlockMachineController clientFallbackTile;
    private volatile String asyncFailureReason = null;

    private ClientGuiValidationRunner() {
    }

    public static synchronized void registerIfEnabled() {
        if (registered || !Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(new ClientGuiValidationRunner());
        MMCEOneBlock.log.info("[MMCE One Block ClientGuiValidation] Enabled by -D{}=true", ENABLE_PROPERTY);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.done) {
            return;
        }
        this.ticks++;
        try {
            tick(Minecraft.getMinecraft());
        } catch (RuntimeException ex) {
            fail("exception=" + ex.getClass().getName() + ":" + ex.getMessage());
        }
    }

    private void tick(Minecraft mc) {
        if (this.ticks > 2400) {
            fail("timeout");
            return;
        }

        if (!this.launchedWorld) {
            if (this.ticks < 20 || mc.world != null) {
                return;
            }
            WorldSettings settings = new WorldSettings(0L, GameType.CREATIVE, false, false, WorldType.FLAT)
                .enableCommands();
            mc.launchIntegratedServer(
                "MMCEOneBlockClientGuiValidation",
                "MMCE One Block Client GUI Validation",
                settings
            );
            this.launchedWorld = true;
            MMCEOneBlock.log.info("[MMCE One Block ClientGuiValidation] launched integrated smoke world");
            return;
        }

        if (mc.world == null || mc.player == null) {
            return;
        }

        if (this.asyncFailureReason != null) {
            fail(this.asyncFailureReason);
            return;
        }

        if (!this.placedBlock) {
            if (!this.requestedServerPlacement) {
                requestServerPlacement(mc);
            } else {
                observeClientTile(mc);
            }
            return;
        }

        if (!this.requestedGuiOpen) {
            requestServerGuiOpen(mc);
            return;
        }

        verifyDisplayedGui(mc);
    }

    private void requestServerPlacement(Minecraft mc) {
        BlockSingleBlockMachineController block = MachineRegistry.getBlock(TARGET_ID);
        if (block == null) {
            fail("missing_machine_block:" + TARGET_ID);
            return;
        }

        BlockPos playerPos = mc.player.getPosition();
        this.pos = new BlockPos(playerPos.getX() + 2, Math.max(4, playerPos.getY()), playerPos.getZ());
        if (!mc.world.isBlockLoaded(this.pos)) {
            return;
        }

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }

        this.requestedServerPlacement = true;
        this.playerId = mc.player.getUniqueID();
        this.placedAt = this.ticks;
        server.addScheduledTask(() -> placeSmokeControllerOnServer(server, block));
    }

    private void placeSmokeControllerOnServer(MinecraftServer server, BlockSingleBlockMachineController block) {
        try {
            EntityPlayerMP player = serverPlayer(server);
            if (player == null || this.pos == null) {
                this.asyncFailureReason = "server_world_or_player_missing";
                return;
            }
            WorldServer world = player.getServerWorld();

            world.setBlockToAir(this.pos);
            net.minecraft.block.state.IBlockState oldState = world.getBlockState(this.pos);
            net.minecraft.block.state.IBlockState state = block.getDefaultState()
                .withProperty(BlockController.FACING, EnumFacing.NORTH);
            world.setBlockState(this.pos, state, 3);
            TileEntity tile = world.getTileEntity(this.pos);
            if (!(tile instanceof TileSingleBlockMachineController)) {
                this.asyncFailureReason = "server_tile_unexpected:" + className(tile);
                return;
            }

            ((TileSingleBlockMachineController) tile).setDefinitionId(TARGET_ID);
            tile.markDirty();
            world.notifyBlockUpdate(this.pos, oldState, state, 3);
            player.connection.setPlayerLocation(
                this.pos.getX() + 0.5D,
                this.pos.getY(),
                this.pos.getZ() + 2.5D,
                player.rotationYaw,
                player.rotationPitch
            );
            player.connection.sendPacket(new SPacketBlockChange(world, this.pos));
            MMCEOneBlock.log.info("[MMCE One Block ClientGuiValidation] placed server smoke controller id={} pos={}",
                TARGET_ID, this.pos);
        } catch (RuntimeException ex) {
            this.asyncFailureReason = "server_place_exception=" + ex.getClass().getName() + ":" + ex.getMessage();
        }
    }

    private void observeClientTile(Minecraft mc) {
        TileSingleBlockMachineController tile = getValidationTile(mc);
        if (tile == null) {
            this.clientTileReadySince = 0;
            if (!this.installedClientFallback && this.ticks - this.placedAt > 80) {
                installClientFallbackTile(mc);
                tile = getValidationTile(mc);
            }
            if (tile == null && this.ticks - this.placedAt > 300) {
                fail("client_tile_missing_after_server_place");
            }
            if (tile == null) {
                return;
            }
        }
        if (!isClientTileReady(tile, "client_tile_not_ready")) {
            return;
        }

        if (this.clientTileReadySince == 0) {
            this.clientTileReadySince = this.ticks;
            return;
        }
        if (this.ticks - this.clientTileReadySince < CLIENT_TILE_STABLE_TICKS) {
            return;
        }

        this.placedBlock = true;
        this.placedAt = this.ticks;
        MMCEOneBlock.log.info("[MMCE One Block ClientGuiValidation] observed {} smoke controller id={} pos={} inventorySlots={}",
            tile == this.clientFallbackTile ? "fallback" : "client", TARGET_ID, this.pos, tile.getInventory().getSlots());
    }

    private void requestServerGuiOpen(Minecraft mc) {
        TileSingleBlockMachineController tile = getTile(mc);
        if (tile == null && this.clientFallbackTile != null) {
            requestDirectFallbackGuiOpen(mc);
            return;
        }
        if (tile == null) {
            if (this.ticks - this.placedAt > 80) {
                fail("tile_missing");
            }
            return;
        }
        if (!isClientTileReady(tile, "client_tile_not_ready_before_open")) {
            return;
        }

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }

        this.requestedGuiOpen = true;
        this.openedAt = this.ticks;
        server.addScheduledTask(() -> openSmokeControllerGuiOnServer(server));
    }

    private void requestDirectFallbackGuiOpen(Minecraft mc) {
        TileSingleBlockMachineController tile = this.clientFallbackTile;
        if (tile == null) {
            fail("fallback_tile_missing_before_open");
            return;
        }
        if (!isClientTileReady(tile, "client_fallback_tile_not_ready_before_open")) {
            return;
        }

        ContainerSingleBlockController container = new ContainerSingleBlockController(tile, mc.player);
        GuiScreen screen = ClientGuiBridge.createSingleBlockControllerGui(container);
        if (screen == null) {
            fail("fallback_gui_missing");
            return;
        }

        this.requestedGuiOpen = true;
        this.directFallbackGuiOpen = true;
        this.openedAt = this.ticks;
        mc.displayGuiScreen(screen);
        MMCEOneBlock.log.info("[MMCE One Block ClientGuiValidation] requested direct fallback GUI open id={} pos={}",
            TARGET_ID, this.pos);
    }

    private void openSmokeControllerGuiOnServer(MinecraftServer server) {
        try {
            EntityPlayerMP player = serverPlayer(server);
            if (player == null || this.pos == null) {
                this.asyncFailureReason = "server_open_world_or_player_missing";
                return;
            }
            WorldServer world = player.getServerWorld();
            TileEntity tile = world.getTileEntity(this.pos);
            if (!(tile instanceof TileSingleBlockMachineController)) {
                this.asyncFailureReason = "server_open_tile_unexpected:" + className(tile);
                return;
            }

            player.openGui(MMCEOneBlock.instance, GuiHandler.GUI_SINGLE_BLOCK_CONTROLLER,
                world, this.pos.getX(), this.pos.getY(), this.pos.getZ());
            MMCEOneBlock.log.info("[MMCE One Block ClientGuiValidation] requested server GUI open id={} pos={}",
                TARGET_ID, this.pos);
        } catch (RuntimeException ex) {
            this.asyncFailureReason = "server_open_exception=" + ex.getClass().getName() + ":" + ex.getMessage();
        }
    }

    private EntityPlayerMP serverPlayer(MinecraftServer server) {
        return this.playerId == null ? null : server.getPlayerList().getPlayerByUUID(this.playerId);
    }

    private void installClientFallbackTile(Minecraft mc) {
        BlockSingleBlockMachineController block = MachineRegistry.getBlock(TARGET_ID);
        if (block == null || mc.world == null || this.pos == null || !mc.world.isBlockLoaded(this.pos)) {
            return;
        }

        net.minecraft.block.state.IBlockState state = block.getDefaultState()
            .withProperty(BlockController.FACING, EnumFacing.NORTH);
        mc.world.setBlockState(this.pos, state, 3);

        TileEntity tile = block.createTileEntity(mc.world, state);
        if (!(tile instanceof TileSingleBlockMachineController)) {
            fail("client_fallback_tile_unexpected:" + className(tile));
            return;
        }

        tile.setWorld(mc.world);
        tile.setPos(this.pos);
        ((TileSingleBlockMachineController) tile).setDefinitionId(TARGET_ID);
        tile.validate();
        ((TileSingleBlockMachineController) tile).provideMachineComponents();
        mc.world.setTileEntity(this.pos, tile);
        if (!(mc.world.getTileEntity(this.pos) instanceof TileSingleBlockMachineController)) {
            mc.world.getChunk(this.pos).addTileEntity(tile);
        }
        this.clientFallbackTile = (TileSingleBlockMachineController) tile;
        this.installedClientFallback = true;
        MMCEOneBlock.log.info("[MMCE One Block ClientGuiValidation] installed client fallback controller id={} pos={}",
            TARGET_ID, this.pos);
    }

    private boolean isClientTileReady(TileSingleBlockMachineController tile, String reason) {
        if (tile.provideMachineComponents().isEmpty()) {
            this.clientTileReadySince = 0;
            failIfClientTileWaitExpired(reason + ":components_missing");
            return false;
        }

        int inventorySlots = tile.getInventory().getSlots();
        if (inventorySlots <= 1) {
            this.clientTileReadySince = 0;
            failIfClientTileWaitExpired(reason + ":inventory_slots=" + inventorySlots);
            return false;
        }

        ResourceLocation style = tile.getMachineControllerGuiStyle();
        if (!EXPECTED_STYLE.equals(style)) {
            fail("unexpected_style:" + style);
            return false;
        }
        return true;
    }

    private void failIfClientTileWaitExpired(String reason) {
        if (this.ticks - this.placedAt > 300) {
            fail(reason);
        }
    }

    private void verifyDisplayedGui(Minecraft mc) {
        GuiScreen screen = mc.currentScreen;
        if (screen == null) {
            if (this.ticks - this.openedAt > 160) {
                fail("screen_missing");
            }
            return;
        }

        String screenName = screen.getClass().getName();
        if (!EXPECTED_GUI.equals(screenName)) {
            if (this.ticks - this.openedAt > 160) {
                fail("unexpected_screen:" + screenName);
            }
            return;
        }
        if (!(screen instanceof GuiContainer)) {
            fail("screen_not_container:" + screenName);
            return;
        }

        Container container = ((GuiContainer) screen).inventorySlots;
        if (!(container instanceof ContainerSingleBlockController)) {
            fail("unexpected_container:" + (container == null ? "null" : container.getClass().getName()));
            return;
        }

        int slotCount = container.inventorySlots.size();
        int blueprintSlot = ContainerSingleBlockController.blueprintSlotIndex();
        int firstInternalSlot = ContainerSingleBlockController.firstInternalSlotIndex();
        if (slotCount <= firstInternalSlot) {
            fail("not_enough_slots:" + slotCount);
            return;
        }
        Slot blueprint = container.inventorySlots.get(blueprintSlot);
        Slot internal = container.inventorySlots.get(firstInternalSlot);
        if (blueprint == null || !blueprint.getClass().getName().endsWith("$SlotBlueprint")) {
            fail("blueprint_slot_unexpected:" + className(blueprint));
            return;
        }
        if (internal == null || !internal.getClass().getName().endsWith("$SlotInternalItem")) {
            fail("internal_slot_unexpected:" + className(internal));
            return;
        }

        this.done = true;
        MMCEOneBlock.log.info(
            "[MMCE One Block ClientGuiValidation] PASS id={} screen={} container={} slotCount={} blueprintSlot={} firstInternalSlot={} guiStyle={} openMode={} displayed=true",
            TARGET_ID,
            screenName,
            container.getClass().getName(),
            slotCount,
            blueprintSlot,
            firstInternalSlot,
            EXPECTED_STYLE,
            this.directFallbackGuiOpen ? "directFallback" : "serverGuiHandler"
        );
    }

    private TileSingleBlockMachineController getTile(Minecraft mc) {
        if (this.pos == null || mc.world == null || !mc.world.isBlockLoaded(this.pos)) {
            return null;
        }
        TileEntity tile = mc.world.getTileEntity(this.pos);
        return tile instanceof TileSingleBlockMachineController ? (TileSingleBlockMachineController) tile : null;
    }

    private TileSingleBlockMachineController getValidationTile(Minecraft mc) {
        TileSingleBlockMachineController tile = getTile(mc);
        return tile == null ? this.clientFallbackTile : tile;
    }

    private static String className(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private void fail(String reason) {
        this.done = true;
        MMCEOneBlock.log.error("[MMCE One Block ClientGuiValidation] FAIL reason={}", reason);
    }
}
