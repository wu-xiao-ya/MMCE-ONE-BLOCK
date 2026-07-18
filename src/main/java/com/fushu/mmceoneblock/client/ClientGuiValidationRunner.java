package com.fushu.mmceoneblock.client;

import com.fushu.mmceoneblock.MMCEOneBlock;
import com.fushu.mmceoneblock.common.block.BlockSingleBlockMachineController;
import com.fushu.mmceoneblock.common.container.ContainerSingleBlockController;
import com.fushu.mmceoneblock.common.container.ContainerSingleBlockFactoryController;
import com.fushu.mmceoneblock.common.network.GuiHandler;
import com.fushu.mmceoneblock.common.registry.MachineRegistry;
import com.fushu.mmceoneblock.common.tile.TileSingleBlockFactoryController;
import com.fushu.mmceoneblock.common.tile.TileSingleBlockMachineController;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.util.SmartInterfaceData;
import hellfirepvp.modularmachinery.common.util.SmartInterfaceType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ScreenShotHelper;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

@SideOnly(Side.CLIENT)
public final class ClientGuiValidationRunner {
    private static final String ENABLE_PROPERTY = "mmceoneblock.clientGuiValidation";
    private static final String TARGET_ID = "starter_controller";
    private static final String FACTORY_TARGET_ID = "factory_controller";
    private static final String EXPECTED_GUI = "com.fushu.mmceguiext.client.gui.GuiMachineControllerResizable";
    private static final String EXPECTED_FACTORY_GUI =
        "com.fushu.mmceguiext.client.gui.GuiFactoryControllerResizable";
    private static final ResourceLocation EXPECTED_STYLE = new ResourceLocation("mmceoneblock", TARGET_ID);
    private static final ResourceLocation EXPECTED_FACTORY_STYLE =
        new ResourceLocation("mmceoneblock", FACTORY_TARGET_ID);
    private static final String SMART_INTERFACE_KEY = "oneblock_smoke_target";
    private static final float SMART_INTERFACE_VALUE = 42.0F;
    private static final int CLIENT_TILE_STABLE_TICKS = 40;
    private static final int SERVER_GUI_RETRY_TICKS = 20;
    private static boolean registered = false;

    private int ticks;
    private boolean launchedWorld;
    private boolean requestedServerPlacement;
    private boolean placedBlock;
    private volatile boolean requestedGuiOpen;
    private volatile boolean serverGuiOpenCheckScheduled;
    private boolean installedClientFallback;
    private boolean directFallbackGuiOpen;
    private boolean validatingFactoryGui;
    private boolean done;
    private BlockPos pos;
    private UUID playerId;
    private int placedAt;
    private int openedAt;
    private int lastServerGuiOpenCheckAt = -SERVER_GUI_RETRY_TICKS;
    private int clientTileReadySince;
    private int smartWriteRequestedAt;
    private TileSingleBlockMachineController clientFallbackTile;
    private boolean smartWriteRequested;
    private volatile boolean smartWriteVerified;
    private volatile boolean smartWriteCheckScheduled;
    private volatile String asyncFailureReason = null;
    private Boolean previousPauseOnLostFocus;

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
            disablePauseOnLostFocus(mc);
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

        if (this.validatingFactoryGui) {
            verifyFactoryDisplayedGui(mc);
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
            if (!ensureSmokeSmartInterfaceType(block)) {
                return;
            }

            world.setBlockToAir(this.pos);
            world.setBlockState(this.pos.down(), Blocks.STONE.getDefaultState(), 3);
            net.minecraft.block.state.IBlockState oldState = world.getBlockState(this.pos);
            net.minecraft.block.state.IBlockState state = block.getDefaultState()
                .withProperty(BlockController.FACING, EnumFacing.NORTH);
            world.setBlockState(this.pos, state, 3);
            TileEntity tile = world.getTileEntity(this.pos);
            if (!(tile instanceof TileSingleBlockMachineController)) {
                this.asyncFailureReason = "server_tile_unexpected:" + className(tile);
                return;
            }

            TileSingleBlockMachineController controller = (TileSingleBlockMachineController) tile;
            controller.setDefinitionId(TARGET_ID);
            tile.markDirty();
            controller.markForUpdateSync();
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
            if (!this.installedClientFallback && this.ticks - this.placedAt > 300) {
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
        if (this.serverGuiOpenCheckScheduled
            || this.ticks - this.lastServerGuiOpenCheckAt < SERVER_GUI_RETRY_TICKS) {
            return;
        }

        this.serverGuiOpenCheckScheduled = true;
        this.lastServerGuiOpenCheckAt = this.ticks;
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
            TileSingleBlockMachineController controller = (TileSingleBlockMachineController) tile;
            if (!controller.isStructureFormed() || controller.getFoundMachine() == null) {
                MMCEOneBlock.log.info(
                    "[MMCE One Block ClientGuiValidation] waiting for server controller formation id={} formed={} machine={}",
                    TARGET_ID,
                    controller.isStructureFormed(),
                    controller.getFoundMachine() == null ? "missing" : controller.getFoundMachine().getRegistryName()
                );
                return;
            }

            player.openGui(MMCEOneBlock.instance, GuiHandler.GUI_SINGLE_BLOCK_CONTROLLER,
                world, this.pos.getX(), this.pos.getY(), this.pos.getZ());
            this.openedAt = this.ticks;
            this.requestedGuiOpen = true;
            MMCEOneBlock.log.info("[MMCE One Block ClientGuiValidation] requested server GUI open id={} pos={}",
                TARGET_ID, this.pos);
        } catch (RuntimeException ex) {
            this.asyncFailureReason = "server_open_exception=" + ex.getClass().getName() + ":" + ex.getMessage();
        } finally {
            this.serverGuiOpenCheckScheduled = false;
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
        if (tile.getCustomDataTag().getLong("oneblock.component.energy_in.capacity") != 1000L) {
            fail("unexpected_dynamic_capacity:"
                + tile.getCustomDataTag().getLong("oneblock.component.energy_in.capacity"));
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
        if (internal == null || !internal.getClass().getName().endsWith("$MachineSlot")) {
            fail("internal_slot_unexpected:" + className(internal));
            return;
        }
        if (blueprint.xPos != 151 || blueprint.yPos != 8 || internal.xPos != 8 || internal.yPos != 17) {
            fail("slot_layout_unexpected:blueprint=" + blueprint.xPos + "," + blueprint.yPos
                + ":internal=" + internal.xPos + "," + internal.yPos);
            return;
        }
        if (!verifyRuntimeStyle(screen)) {
            return;
        }
        if (!verifySmartInterfaceWrite(screen)) {
            return;
        }

        captureValidationScreenshot(TARGET_ID);
        MMCEOneBlock.log.info(
            "[MMCE One Block ClientGuiValidation] PASS id={} screen={} container={} slotCount={} blueprintSlot={} firstInternalSlot={} guiStyle={} openMode={} styleRuntime=true styleEvidence=starter_style_dynamic_capacity_and_smart_write smartKey={} smartValue={} displayed=true",
            TARGET_ID,
            screenName,
            container.getClass().getName(),
            slotCount,
            blueprintSlot,
            firstInternalSlot,
            EXPECTED_STYLE,
            this.directFallbackGuiOpen ? "directFallback" : "serverGuiHandler",
            SMART_INTERFACE_KEY,
            SMART_INTERFACE_VALUE
        );
        startFactoryGuiValidation(mc);
    }

    private boolean ensureSmokeSmartInterfaceType(BlockSingleBlockMachineController block) {
        if (block.getDefinition() == null) {
            this.asyncFailureReason = "smart_interface_definition_missing";
            return false;
        }
        DynamicMachine machine = hellfirepvp.modularmachinery.common.machine.MachineRegistry
            .getRegistry()
            .getMachine(block.getDefinition().getMachine());
        if (machine == null) {
            this.asyncFailureReason = "smart_interface_backing_machine_missing";
            return false;
        }
        if (!machine.hasSmartInterfaceType(SMART_INTERFACE_KEY)) {
            machine.addSmartInterfaceType(new SmartInterfaceType(SMART_INTERFACE_KEY, 0.0F));
        }
        return true;
    }

    private boolean verifySmartInterfaceWrite(GuiScreen screen) {
        if (!this.smartWriteRequested) {
            try {
                Object button = findStyleEntry(screen, "customButtons", "id", "smoke_smart_set");
                if (button == null) {
                    fail("runtime_smart_button_missing");
                    return false;
                }
                Method activate = findMethod(screen.getClass(), "activateCustomButton", button.getClass());
                activate.setAccessible(true);
                activate.invoke(screen, button);
                this.smartWriteRequested = true;
                this.smartWriteRequestedAt = this.ticks;
                MMCEOneBlock.log.info(
                    "[MMCE One Block ClientGuiValidation] requested virtual Smart Interface write key={} value={}",
                    SMART_INTERFACE_KEY,
                    SMART_INTERFACE_VALUE
                );
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
                fail("runtime_smart_write_exception="
                    + ex.getClass().getName() + ":" + ex.getMessage());
            }
            return false;
        }
        if (this.smartWriteVerified) {
            return true;
        }
        if (!this.smartWriteCheckScheduled && this.ticks - this.smartWriteRequestedAt >= 5) {
            requestServerSmartWriteCheck();
        }
        if (this.ticks - this.smartWriteRequestedAt > 160) {
            fail("runtime_smart_write_not_observed");
        }
        return false;
    }

    private void requestServerSmartWriteCheck() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || this.pos == null) {
            return;
        }
        this.smartWriteCheckScheduled = true;
        server.addScheduledTask(() -> {
            try {
                EntityPlayerMP player = serverPlayer(server);
                if (player == null || this.pos == null) {
                    this.smartWriteCheckScheduled = false;
                    return;
                }
                TileEntity tile = player.getServerWorld().getTileEntity(this.pos);
                if (!(tile instanceof TileSingleBlockMachineController)) {
                    this.asyncFailureReason = "smart_write_server_tile_unexpected:" + className(tile);
                    return;
                }
                SmartInterfaceData data = ((TileSingleBlockMachineController) tile)
                    .getSmartInterfaceData(SMART_INTERFACE_KEY);
                NBTTagCompound customData =
                    ((TileSingleBlockMachineController) tile).getCustomDataTag();
                boolean hasCustomValue = customData != null && customData.hasKey(SMART_INTERFACE_KEY);
                float observedValue = data != null
                    ? data.getValue()
                    : hasCustomValue ? customData.getFloat(SMART_INTERFACE_KEY) : Float.NaN;
                if (Float.isFinite(observedValue)
                    && Math.abs(observedValue - SMART_INTERFACE_VALUE) <= 0.0001F) {
                    this.smartWriteVerified = true;
                    MMCEOneBlock.log.info(
                        "[MMCE One Block ClientGuiValidation] verified virtual Smart Interface write key={} value={} source={}",
                        SMART_INTERFACE_KEY,
                        observedValue,
                        data == null ? "customData" : "smartInterface"
                    );
                } else {
                    this.smartWriteCheckScheduled = false;
                }
            } catch (RuntimeException ex) {
                this.asyncFailureReason = "smart_write_verify_exception="
                    + ex.getClass().getName() + ":" + ex.getMessage();
            }
        });
    }

    private void startFactoryGuiValidation(Minecraft mc) {
        BlockSingleBlockMachineController block = MachineRegistry.getBlock(FACTORY_TARGET_ID);
        if (block == null || mc.world == null || mc.player == null || this.pos == null) {
            fail("factory_validation_prerequisite_missing");
            return;
        }
        BlockPos factoryPos = this.pos.add(2, 0, 0);
        if (!mc.world.isBlockLoaded(factoryPos)) {
            fail("factory_validation_chunk_missing");
            return;
        }
        net.minecraft.block.state.IBlockState state = block.getDefaultState()
            .withProperty(BlockController.FACING, EnumFacing.NORTH);
        mc.world.setBlockState(factoryPos, state, 3);
        TileEntity created = block.createTileEntity(mc.world, state);
        if (!(created instanceof TileSingleBlockFactoryController)) {
            fail("factory_fallback_tile_unexpected:" + className(created));
            return;
        }

        TileSingleBlockFactoryController tile = (TileSingleBlockFactoryController) created;
        tile.setWorld(mc.world);
        tile.setPos(factoryPos);
        tile.setDefinitionId(FACTORY_TARGET_ID);
        tile.validate();
        if (tile.provideMachineComponents().isEmpty()) {
            fail("factory_components_missing");
            return;
        }
        if (!EXPECTED_FACTORY_STYLE.equals(tile.getMachineControllerGuiStyle())) {
            fail("factory_style_unexpected:" + tile.getMachineControllerGuiStyle());
            return;
        }
        if (tile.getCustomDataTag().getLong("oneblock.component.energy_in.capacity") != 10000L) {
            fail("factory_dynamic_capacity_unexpected:"
                + tile.getCustomDataTag().getLong("oneblock.component.energy_in.capacity"));
            return;
        }
        mc.world.setTileEntity(factoryPos, tile);
        if (!(mc.world.getTileEntity(factoryPos) instanceof TileSingleBlockFactoryController)) {
            mc.world.getChunk(factoryPos).addTileEntity(tile);
        }

        ContainerSingleBlockFactoryController container =
            new ContainerSingleBlockFactoryController(tile, mc.player);
        GuiScreen screen = ClientGuiBridge.createSingleBlockFactoryControllerGui(container);
        if (screen == null) {
            fail("factory_gui_missing");
            return;
        }
        this.validatingFactoryGui = true;
        this.openedAt = this.ticks;
        mc.displayGuiScreen(screen);
        MMCEOneBlock.log.info(
            "[MMCE One Block ClientGuiValidation] requested direct factory GUI open id={} pos={}",
            FACTORY_TARGET_ID,
            factoryPos
        );
    }

    private void verifyFactoryDisplayedGui(Minecraft mc) {
        GuiScreen screen = mc.currentScreen;
        if (screen == null) {
            if (this.ticks - this.openedAt > 160) {
                fail("factory_screen_missing");
            }
            return;
        }
        String screenName = screen.getClass().getName();
        if (!EXPECTED_FACTORY_GUI.equals(screenName)) {
            if (this.ticks - this.openedAt > 160) {
                fail("factory_screen_unexpected:" + screenName);
            }
            return;
        }
        if (!(screen instanceof GuiContainer)) {
            fail("factory_screen_not_container:" + screenName);
            return;
        }

        Container container = ((GuiContainer) screen).inventorySlots;
        if (!(container instanceof ContainerSingleBlockFactoryController)) {
            fail("factory_container_unexpected:" + className(container));
            return;
        }
        int slotCount = container.inventorySlots.size();
        int blueprintSlot = ContainerSingleBlockFactoryController.blueprintSlotIndex();
        int firstInternalSlot = ContainerSingleBlockFactoryController.firstInternalSlotIndex();
        if (slotCount <= firstInternalSlot) {
            fail("factory_not_enough_slots:" + slotCount);
            return;
        }
        Slot blueprint = container.inventorySlots.get(blueprintSlot);
        Slot internal = container.inventorySlots.get(firstInternalSlot);
        if (blueprint == null || !blueprint.getClass().getName().endsWith("$SlotBlueprint")
            || internal == null || !internal.getClass().getName().endsWith("$MachineSlot")) {
            fail("factory_slots_unexpected:blueprint=" + className(blueprint)
                + ":internal=" + className(internal));
            return;
        }
        if (blueprint.xPos != 255 || blueprint.yPos != 8 || internal.xPos != 112 || internal.yPos != 17) {
            fail("factory_slot_layout_unexpected:blueprint=" + blueprint.xPos + "," + blueprint.yPos
                + ":internal=" + internal.xPos + "," + internal.yPos);
            return;
        }
        if (!verifyFactoryRuntimeStyle(screen)) {
            return;
        }

        captureValidationScreenshot(FACTORY_TARGET_ID);
        this.done = true;
        restorePauseOnLostFocus(mc);
        MMCEOneBlock.log.info(
            "[MMCE One Block ClientGuiValidation] PASS id={} screen={} container={} slotCount={} blueprintSlot={} firstInternalSlot={} guiStyle={} openMode=directFallback styleRuntime=true styleEvidence=factory_style displayed=true",
            FACTORY_TARGET_ID,
            screenName,
            container.getClass().getName(),
            slotCount,
            blueprintSlot,
            firstInternalSlot,
            EXPECTED_FACTORY_STYLE
        );
    }

    private void captureValidationScreenshot(String targetId) {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            ScreenShotHelper.saveScreenshot(
                minecraft.gameDir,
                minecraft.displayWidth,
                minecraft.displayHeight,
                minecraft.getFramebuffer()
            );
            MMCEOneBlock.log.info(
                "[MMCE One Block ClientGuiValidation] screenshot saved under {} for id={}",
                new java.io.File(minecraft.gameDir, "screenshots").getAbsolutePath(),
                targetId
            );
        } catch (RuntimeException error) {
            MMCEOneBlock.log.warn(
                "[MMCE One Block ClientGuiValidation] screenshot capture failed: {}",
                error.toString()
            );
        }
    }

    private boolean verifyRuntimeStyle(GuiScreen screen) {
        try {
            Object style = readField(screen, "styleOverride");
            if (style == null) {
                fail("runtime_style_missing");
                return false;
            }

            if (findStyleEntry(style, "texts", "id", "smoke_title") == null
                || findStyleEntry(style, "texts", "value", "One Block Smoke") == null) {
                fail("runtime_style_text_missing");
                return false;
            }
            if (findStyleEntry(style, "buttons", "id", "smoke_cycle") == null
                || findStyleEntry(style, "buttons", "action", "event") == null
                || findStyleEntry(style, "buttons", "buttonId", "smoke_pulse") == null) {
                fail("runtime_style_button_missing");
                return false;
            }
            Object smartButton = findStyleEntry(style, "buttons", "id", "smoke_smart_set");
            if (smartButton == null
                || !"smart_set".equals(readField(smartButton, "action"))
                || !SMART_INTERFACE_KEY.equals(readField(smartButton, "key"))) {
                fail("runtime_style_smart_button_missing");
                return false;
            }
            Object smartEditor =
                findStyleEntry(style, "smartInterfaceEditors", "id", "smoke_smart_input");
            if (smartEditor == null
                || !SMART_INTERFACE_KEY.equals(readField(smartEditor, "virtualKey"))) {
                fail("runtime_style_smart_editor_missing");
                return false;
            }
            if (findStyleEntry(style, "progressBars", "id", "smoke_progress") == null
                || findStyleEntry(style, "progressBars", "source", "machine_progress") == null) {
                fail("runtime_style_progress_missing");
                return false;
            }

            Object dynamic = findStyleEntry(style, "dynamicVisuals", "id", "smoke_progress_fill");
            if (dynamic == null) {
                fail("runtime_style_dynamic_missing");
                return false;
            }
            Object source = readField(dynamic, "source");
            Object renderer = readField(dynamic, "renderer");
            Object maxSource = readField(source, "maxSource");
            if (!"customData".equals(readField(source, "type"))
                || !"oneblock.component.energy_in.amount".equals(readField(source, "key"))
                || maxSource == null
                || !"customData".equals(readField(maxSource, "type"))
                || !"oneblock.component.energy_in.capacity".equals(readField(maxSource, "key"))
                || !"fill".equals(readField(renderer, "type"))
                || !"right".equals(readField(renderer, "direction"))) {
                fail("runtime_style_dynamic_unexpected");
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            fail("runtime_style_exception=" + ex.getClass().getName() + ":" + ex.getMessage());
            return false;
        }
    }

    private boolean verifyFactoryRuntimeStyle(GuiScreen screen) {
        try {
            Object style = readField(screen, "styleOverride");
            if (style == null) {
                fail("factory_runtime_style_missing");
                return false;
            }
            if (findStyleEntry(style, "texts", "id", "factory_title") == null
                || findStyleEntry(style, "texts", "value", "One Block Factory") == null) {
                fail("factory_runtime_style_text_missing");
                return false;
            }
            Object button = findStyleEntry(style, "buttons", "id", "open_factory_threads");
            if (button == null
                || !"subgui".equals(readField(button, "action"))
                || !"factory_threads".equals(readField(button, "targetSubGui"))) {
                fail("factory_runtime_style_button_missing");
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            fail("factory_runtime_style_exception="
                + ex.getClass().getName() + ":" + ex.getMessage());
            return false;
        }
    }

    private static Object findStyleEntry(Object style, String listField, String fieldName, String expected)
        throws ReflectiveOperationException {
        Object value = readField(style, listField);
        if (!(value instanceof List<?>)) {
            return null;
        }

        for (Object entry : (List<?>) value) {
            if (expected.equals(readField(entry, fieldName))) {
                return entry;
            }
        }
        return null;
    }

    private static Object readField(Object target, String name) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method findMethod(Class<?> type,
                                     String name,
                                     Class<?> parameterType) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, parameterType);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
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
        restorePauseOnLostFocus(Minecraft.getMinecraft());
        MMCEOneBlock.log.error("[MMCE One Block ClientGuiValidation] FAIL reason={}", reason);
    }

    private void disablePauseOnLostFocus(Minecraft mc) {
        if (this.previousPauseOnLostFocus == null) {
            this.previousPauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
        }
        mc.gameSettings.pauseOnLostFocus = false;
        MMCEOneBlock.log.info(
            "[MMCE One Block ClientGuiValidation] disabled pauseOnLostFocus for integrated smoke world"
        );
    }

    private void restorePauseOnLostFocus(Minecraft mc) {
        if (this.previousPauseOnLostFocus == null) {
            return;
        }
        mc.gameSettings.pauseOnLostFocus = this.previousPauseOnLostFocus;
        this.previousPauseOnLostFocus = null;
    }
}
