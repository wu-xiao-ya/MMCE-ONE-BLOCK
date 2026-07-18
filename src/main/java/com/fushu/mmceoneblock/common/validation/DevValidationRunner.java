package com.fushu.mmceoneblock.common.validation;

import com.fushu.mmceoneblock.MMCEOneBlock;
import com.fushu.mmceoneblock.common.block.BlockSingleBlockMachineController;
import com.fushu.mmceoneblock.common.registry.MachineRegistry;
import com.fushu.mmceoneblock.common.tile.MachineComponentStorage;
import com.fushu.mmceoneblock.common.tile.TileSingleBlockFactoryController;
import com.fushu.mmceoneblock.common.tile.TileSingleBlockMachineController;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.util.IEnergyHandlerAsync;
import hellfirepvp.modularmachinery.common.util.IOInventory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLever;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Collection;
import java.util.List;

public final class DevValidationRunner {
    private static final String ENABLE_PROPERTY = "mmceoneblock.devValidation";
    private static final String TARGET_ID = "starter_controller";
    private static final long EXPECTED_ENERGY = 123L;
    private static final int INPUT_SLOT = 1;
    private static final int FIRST_OUTPUT_SLOT = 10;
    private static final int REDSTONE_PAUSE_TICKS = 60;
    private static final int OUTPUT_BLOCK_TICKS = 60;
    private static boolean registered = false;

    private final ValidationState state = new ValidationState();

    private DevValidationRunner() {
    }

    public static synchronized void registerIfEnabled() {
        if (registered || !Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(new DevValidationRunner());
        MinecraftForge.EVENT_BUS.register(new FactoryValidationRunner());
        MMCEOneBlock.log.info("[MMCE One Block DevValidation] Enabled by -D{}=true", ENABLE_PROPERTY);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.state.done) {
            return;
        }

        try {
            tick();
        } catch (RuntimeException ex) {
            fail("exception=" + ex.getClass().getName() + ":" + ex.getMessage());
        }
    }

    private void tick() {
        this.state.ticks++;
        if (this.state.ticks < 20) {
            return;
        }

        WorldServer world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
        if (world == null) {
            return;
        }

        if (this.state.pos == null) {
            placeMachine(world);
            return;
        }

        if (this.state.persistencePrepared && !this.state.chunkUnloaded) {
            waitForChunkUnload(world);
            return;
        }

        if (this.state.chunkUnloaded && !this.state.chunkReloaded) {
            reloadChunkAndValidate(world);
            return;
        }

        TileSingleBlockMachineController tile = getTile(world);
        if (tile == null) {
            fail("tile_missing");
            return;
        }

        if (!this.state.formed) {
            if (!tile.isStructureFormed() || tile.getFoundMachine() == null) {
                timeoutAfter(160, "structure_not_formed");
                return;
            }
            this.state.formed = true;
            this.state.comparatorAfterFormed = comparator(world);
            if (this.state.comparatorAfterFormed < 1) {
                fail("comparator_not_formed:" + this.state.comparatorAfterFormed);
                return;
            }
            if (tile.provideMachineComponents().isEmpty()) {
                fail("components_missing");
                return;
            }
            startRedstonePause(world, tile);
            return;
        }

        if (this.state.redstonePauseStarted && !this.state.redstonePaused) {
            if (containsStoneOutput(tile.getInventory())) {
                fail("redstone_pause_produced_output");
                return;
            }
            if (this.state.ticks - this.state.redstonePauseStartedAt >= REDSTONE_PAUSE_TICKS) {
                if (!containsCobblestoneInput(tile.getInventory())) {
                    fail("redstone_pause_consumed_input");
                    return;
                }
                this.state.redstonePaused = true;
                fillOutputSlots(tile);
                removeRedstonePause(world, tile);
                this.state.outputBlocked = true;
                this.state.outputBlockStartedAt = this.state.ticks;
                MMCEOneBlock.log.info("[MMCE One Block DevValidation] redstone pause held; filled output slots to test retry");
            }
            return;
        }

        if (this.state.outputBlocked && !this.state.outputRecovered) {
            if (containsStoneOutput(tile.getInventory())) {
                fail("blocked_output_produced_output");
                return;
            }
            if (this.state.ticks - this.state.outputBlockStartedAt >= OUTPUT_BLOCK_TICKS) {
                clearOutputSlots(tile);
                this.state.outputRecovered = true;
                this.state.recipeStartedAt = this.state.ticks;
                MMCEOneBlock.log.info("[MMCE One Block DevValidation] cleared blocked output slots for retry");
            }
            return;
        }

        if (!this.state.recipeFinished) {
            IOInventory inventory = tile.getInventory();
            if (containsStoneOutput(inventory)) {
                this.state.recipeFinished = true;
                if (!validateNbtPayload(tile)) {
                    return;
                }
                prepareChunkReloadValidation(world, tile);
                return;
            }
            timeoutAfter(360, "recipe_not_finished");
        }
    }

    private void placeMachine(WorldServer world) {
        MachineRegistry.MachineEntry entry = MachineRegistry.snapshot().get(TARGET_ID);
        if (entry == null) {
            fail("missing_machine_entry:" + TARGET_ID);
            return;
        }

        BlockPos spawn = world.getSpawnPoint();
        BlockPos pos = new BlockPos(spawn.getX() + 1024, Math.max(80, spawn.getY() + 1), spawn.getZ() + 1024);
        this.state.pos = pos;
        this.state.startedAt = this.state.ticks;

        world.setBlockToAir(pos);
        clearRedstonePause(world, pos);
        world.setBlockState(pos.down(), Blocks.STONE.getDefaultState(), 3);
        BlockSingleBlockMachineController block = entry.getBlock();
        world.setBlockState(pos, block.getDefaultState().withProperty(BlockController.FACING, EnumFacing.NORTH), 3);
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileSingleBlockMachineController) {
            ((TileSingleBlockMachineController) tile).setDefinitionId(TARGET_ID);
        }
        MMCEOneBlock.log.info("[MMCE One Block DevValidation] placed id={} pos={}", TARGET_ID, pos);
    }

    private TileSingleBlockMachineController getTile(WorldServer world) {
        TileEntity tile = world.getTileEntity(this.state.pos);
        return tile instanceof TileSingleBlockMachineController ? (TileSingleBlockMachineController) tile : null;
    }

    private void startRedstonePause(WorldServer world, TileSingleBlockMachineController tile) {
        for (BlockPos redstonePos : redstonePositions(this.state.pos)) {
            world.setBlockState(redstonePos, Blocks.LEVER.getDefaultState()
                .withProperty(BlockLever.FACING, BlockLever.EnumOrientation.EAST)
                .withProperty(BlockLever.POWERED, Boolean.TRUE), 3);
        }
        tile.onNeighborChange();
        insertInput(tile);
        this.state.redstonePauseStarted = true;
        this.state.redstonePauseStartedAt = this.state.ticks;
        MMCEOneBlock.log.info(
            "[MMCE One Block DevValidation] applied redstone pause beside controller strongPower={}",
            world.getStrongPower(this.state.pos)
        );
    }

    private void removeRedstonePause(WorldServer world, TileSingleBlockMachineController tile) {
        clearRedstonePause(world, this.state.pos);
        tile.onNeighborChange();
    }

    private void clearRedstonePause(WorldServer world, BlockPos pos) {
        for (BlockPos redstonePos : redstonePositions(pos)) {
            world.setBlockToAir(redstonePos);
        }
    }

    private BlockPos[] redstonePositions(BlockPos pos) {
        return new BlockPos[] {
            pos.east()
        };
    }

    private void insertInput(TileSingleBlockMachineController tile) {
        IOInventory inventory = tile.getInventory();
        if (inventory.getSlots() < 3) {
            fail("inventory_too_small:" + inventory.getSlots());
            return;
        }
        inventory.setStackInSlot(INPUT_SLOT, new ItemStack(Blocks.COBBLESTONE));
        this.state.recipeStartedAt = this.state.ticks;
        MMCEOneBlock.log.info("[MMCE One Block DevValidation] inserted minecraft:cobblestone into input slot");
    }

    private boolean containsStoneOutput(IOInventory inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.STONE)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsCobblestoneInput(IOInventory inventory) {
        if (inventory.getSlots() <= INPUT_SLOT) {
            return false;
        }
        ItemStack stack = inventory.getStackInSlot(INPUT_SLOT);
        return !stack.isEmpty() && stack.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.COBBLESTONE);
    }

    private void fillOutputSlots(TileSingleBlockMachineController tile) {
        IOInventory inventory = tile.getInventory();
        for (int slot = FIRST_OUTPUT_SLOT; slot < inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, new ItemStack(Blocks.DIRT));
        }
        tile.markDirty();
    }

    private void clearOutputSlots(TileSingleBlockMachineController tile) {
        IOInventory inventory = tile.getInventory();
        for (int slot = FIRST_OUTPUT_SLOT; slot < inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        tile.markDirty();
    }

    private int comparator(WorldServer world) {
        Block block = world.getBlockState(this.state.pos).getBlock();
        return block.getComparatorInputOverride(world.getBlockState(this.state.pos), world, this.state.pos);
    }

    private boolean validateNbtPayload(TileSingleBlockMachineController tile) {
        IEnergyHandlerAsync energy = findEnergy(tile.provideMachineComponents());
        if (energy != null) {
            energy.setCurrentEnergy(EXPECTED_ENERGY);
            this.state.expectedEnergy = EXPECTED_ENERGY;
            this.state.energySeeded = true;
        }
        NBTTagCompound tag = new NBTTagCompound();
        tile.writeToNBT(tag);
        if (!TARGET_ID.equals(tag.getString("definitionId"))) {
            fail("nbt_definition_mismatch:" + tag.getString("definitionId"));
            return false;
        }
        if (!tag.hasKey(MachineComponentStorage.COMPONENTS_NBT_KEY)
            || !tag.getCompoundTag(MachineComponentStorage.COMPONENTS_NBT_KEY).hasKey("energy_in")) {
            fail("nbt_payload_missing");
            return false;
        }
        this.state.nbtPayload = true;
        return true;
    }

    private void prepareChunkReloadValidation(WorldServer world, TileSingleBlockMachineController tile) {
        if (!containsStoneOutput(tile.getInventory())) {
            fail("persistence_output_missing_before_save");
            return;
        }
        tile.markDirty();
        ChunkProviderServer provider = world.getChunkProvider();
        Chunk chunk = findLoadedChunk(provider, this.state.pos.getX() >> 4, this.state.pos.getZ() >> 4);
        if (chunk == null) {
            fail("persistence_chunk_missing_before_save");
            return;
        }
        chunk.markDirty();
        try {
            provider.saveChunks(true);
        } catch (Exception ex) {
            fail("persistence_save_failed:" + ex.getClass().getName() + ":" + ex.getMessage());
            return;
        }
        this.state.persistencePrepared = true;
        this.state.persistenceStartedAt = this.state.ticks;
        MMCEOneBlock.log.info("[MMCE One Block DevValidation] saved and queued chunk reload check pos={}", this.state.pos);
        if (requestChunkUnload(provider)) {
            this.state.chunkUnloaded = true;
            MMCEOneBlock.log.info("[MMCE One Block DevValidation] chunk unloaded pos={}", this.state.pos);
        }
    }

    private void waitForChunkUnload(WorldServer world) {
        ChunkProviderServer provider = world.getChunkProvider();
        if (requestChunkUnload(provider)) {
            this.state.chunkUnloaded = true;
            MMCEOneBlock.log.info("[MMCE One Block DevValidation] chunk unloaded pos={}", this.state.pos);
            return;
        }
        if (this.state.ticks - this.state.persistenceStartedAt > 120) {
            fail("chunk_not_unloaded");
        }
    }

    private void reloadChunkAndValidate(WorldServer world) {
        ChunkProviderServer provider = world.getChunkProvider();
        provider.loadChunk(this.state.pos.getX() >> 4, this.state.pos.getZ() >> 4);
        TileSingleBlockMachineController tile = getTile(world);
        if (tile == null) {
            fail("tile_missing_after_chunk_reload");
            return;
        }
        if (!TARGET_ID.equals(tile.getDefinitionId())) {
            fail("definition_mismatch_after_chunk_reload:" + tile.getDefinitionId());
            return;
        }
        if (tile.provideMachineComponents().isEmpty()) {
            fail("components_missing_after_chunk_reload");
            return;
        }
        if (!containsStoneOutput(tile.getInventory())) {
            fail("inventory_missing_after_chunk_reload");
            return;
        }
        this.state.inventoryPersisted = true;

        IEnergyHandlerAsync energy = findEnergy(tile.provideMachineComponents());
        if (this.state.energySeeded && (energy == null || energy.getCurrentEnergy() != this.state.expectedEnergy)) {
            fail("energy_mismatch_after_chunk_reload:" + (energy == null ? "missing" : Long.toString(energy.getCurrentEnergy())));
            return;
        }
        this.state.energyPersisted = !this.state.energySeeded || energy != null;
        this.state.chunkReloaded = true;
        MMCEOneBlock.log.info(
            "[MMCE One Block DevValidation] chunk reload persisted id={} inventoryPersisted={} energyPersisted={}",
            TARGET_ID,
            this.state.inventoryPersisted,
            this.state.energyPersisted
        );
        if (!validateDestroyDrop(world)) {
            return;
        }
        pass();
    }

    private boolean validateDestroyDrop(WorldServer world) {
        TileEntity tile = world.getTileEntity(this.state.pos);
        if (!(tile instanceof TileSingleBlockMachineController)) {
            fail("drop_tile_missing_before_destroy");
            return false;
        }

        Block block = world.getBlockState(this.state.pos).getBlock();
        if (!(block instanceof BlockSingleBlockMachineController)) {
            fail("drop_wrong_block_before_destroy:" + block.getRegistryName());
            return false;
        }

        MachineRegistry.MachineEntry entry = MachineRegistry.snapshot().get(TARGET_ID);
        if (entry == null) {
            fail("drop_missing_machine_entry:" + TARGET_ID);
            return false;
        }

        int before = countControllerDrops(world, entry);
        if (!world.destroyBlock(this.state.pos, true)) {
            fail("destroy_block_returned_false");
            return false;
        }

        int after = countControllerDrops(world, entry);
        if (after <= before) {
            fail("controller_drop_missing:" + before + "->" + after);
            return false;
        }
        if (!world.isAirBlock(this.state.pos)) {
            fail("block_not_air_after_destroy:" + world.getBlockState(this.state.pos).getBlock().getRegistryName());
            return false;
        }
        if (world.getTileEntity(this.state.pos) != null) {
            fail("tile_not_cleared_after_destroy");
            return false;
        }

        this.state.blockDropped = true;
        this.state.tileCleared = true;
        cleanupDropEntities(world);
        MMCEOneBlock.log.info(
            "[MMCE One Block DevValidation] destroy drop verified id={} blockDropped={} tileCleared={}",
            TARGET_ID,
            this.state.blockDropped,
            this.state.tileCleared
        );
        return true;
    }

    private boolean requestChunkUnload(ChunkProviderServer provider) {
        Chunk chunk = findLoadedChunk(provider, this.state.pos.getX() >> 4, this.state.pos.getZ() >> 4);
        if (chunk == null) {
            return true;
        }
        provider.queueUnload(chunk);
        provider.tick();
        return findLoadedChunk(provider, this.state.pos.getX() >> 4, this.state.pos.getZ() >> 4) == null;
    }

    private Chunk findLoadedChunk(ChunkProviderServer provider, int chunkX, int chunkZ) {
        for (Chunk chunk : provider.getLoadedChunks()) {
            if (chunk.x == chunkX && chunk.z == chunkZ) {
                return chunk;
            }
        }
        return null;
    }

    private IEnergyHandlerAsync findEnergy(Collection<MachineComponent<?>> components) {
        for (MachineComponent<?> component : components) {
            Object provider = component.getContainerProvider();
            if (provider instanceof IEnergyHandlerAsync) {
                return (IEnergyHandlerAsync) provider;
            }
        }
        return null;
    }

    private int countControllerDrops(WorldServer world, MachineRegistry.MachineEntry entry) {
        int count = 0;
        for (EntityItem entity : findNearbyDrops(world)) {
            ItemStack stack = entity.getItem();
            if (!entity.isDead && !stack.isEmpty() && stack.getItem() == entry.getItem()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void cleanupDropEntities(WorldServer world) {
        for (EntityItem entity : findNearbyDrops(world)) {
            entity.setDead();
        }
    }

    private List<EntityItem> findNearbyDrops(WorldServer world) {
        double x = this.state.pos.getX();
        double y = this.state.pos.getY();
        double z = this.state.pos.getZ();
        AxisAlignedBB box = new AxisAlignedBB(x - 2.0D, y - 2.0D, z - 2.0D, x + 3.0D, y + 3.0D, z + 3.0D);
        return world.getEntitiesWithinAABB(EntityItem.class, box);
    }

    @SuppressWarnings("unused")
    private void cleanup(WorldServer world) {
        if (this.state.pos != null) {
            clearRedstonePause(world, this.state.pos);
            world.setBlockToAir(this.state.pos);
        }
    }

    private void timeoutAfter(int maxTicks, String reason) {
        if (this.state.ticks - this.state.startedAt > maxTicks) {
            fail(reason);
        }
    }

    private void pass() {
        this.state.done = true;
        MMCEOneBlock.log.info(
            "[MMCE One Block DevValidation] PASS id={} formed={} redstonePaused={} outputBlocked={} outputRecovered={} recipeFinished={} nbtPayload={} chunkReloaded={} inventoryPersisted={} energyPersisted={} comparatorAfterFormed={} blockDropped={} tileCleared={}",
            TARGET_ID,
            this.state.formed,
            this.state.redstonePaused,
            this.state.outputBlocked,
            this.state.outputRecovered,
            this.state.recipeFinished,
            this.state.nbtPayload,
            this.state.chunkReloaded,
            this.state.inventoryPersisted,
            this.state.energyPersisted,
            this.state.comparatorAfterFormed,
            this.state.blockDropped,
            this.state.tileCleared
        );
        MMCEOneBlock.log.info(
            "[MMCE One Block DevValidation] PASS_DROP id={} blockDropped={} tileCleared={}",
            TARGET_ID,
            this.state.blockDropped,
            this.state.tileCleared
        );
    }

    private void fail(String reason) {
        this.state.done = true;
        MMCEOneBlock.log.error("[MMCE One Block DevValidation] FAIL reason={}", reason);
    }

    private static final class FactoryValidationRunner {
        private static final String FACTORY_TARGET_ID = "factory_controller";

        private int ticks;
        private int startedAt;
        private BlockPos pos;
        private boolean done;

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || done) {
                return;
            }
            try {
                tick();
            } catch (RuntimeException ex) {
                fail("exception=" + ex.getClass().getName() + ":" + ex.getMessage());
            }
        }

        private void tick() {
            ticks++;
            if (ticks < 20) {
                return;
            }

            WorldServer world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
            if (world == null) {
                return;
            }
            if (pos == null) {
                placeFactory(world);
                return;
            }

            TileEntity rawTile = world.getTileEntity(pos);
            if (!(rawTile instanceof TileSingleBlockFactoryController)) {
                fail("wrong_tile=" + (rawTile == null ? "null" : rawTile.getClass().getName()));
                return;
            }

            TileSingleBlockFactoryController tile = (TileSingleBlockFactoryController) rawTile;
            if (!tile.isStructureFormed() || tile.getFoundMachine() == null) {
                if (ticks - startedAt > 160) {
                    fail("structure_not_formed");
                }
                return;
            }

            Collection<MachineComponent<?>> components = tile.provideMachineComponents();
            if (components.isEmpty()) {
                fail("components_missing");
                return;
            }

            NBTTagCompound tag = new NBTTagCompound();
            tile.writeToNBT(tag);
            if (!FACTORY_TARGET_ID.equals(tag.getString("definitionId"))) {
                fail("nbt_definition_mismatch:" + tag.getString("definitionId"));
                return;
            }
            if (!tag.hasKey(MachineComponentStorage.COMPONENTS_NBT_KEY)
                || !tag.getCompoundTag(MachineComponentStorage.COMPONENTS_NBT_KEY).hasKey("energy_in")) {
                fail("nbt_payload_missing");
                return;
            }

            world.destroyBlock(pos, false);
            done = true;
            MMCEOneBlock.log.info(
                "[MMCE One Block DevValidation] FACTORY_PASS id={} factoryFormed=true factoryComponents=true factoryNbtPayload=true",
                FACTORY_TARGET_ID
            );
        }

        private void placeFactory(WorldServer world) {
            MachineRegistry.MachineEntry entry = MachineRegistry.snapshot().get(FACTORY_TARGET_ID);
            if (entry == null) {
                fail("missing_machine_entry:" + FACTORY_TARGET_ID);
                return;
            }

            BlockPos spawn = world.getSpawnPoint();
            pos = new BlockPos(spawn.getX() + 1040, Math.max(80, spawn.getY() + 1), spawn.getZ() + 1024);
            startedAt = ticks;
            world.setBlockToAir(pos);
            world.setBlockState(pos.down(), Blocks.STONE.getDefaultState(), 3);
            BlockSingleBlockMachineController block = entry.getBlock();
            world.setBlockState(pos, block.getDefaultState().withProperty(BlockController.FACING, EnumFacing.NORTH), 3);
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileSingleBlockFactoryController) {
                ((TileSingleBlockFactoryController) tile).setDefinitionId(FACTORY_TARGET_ID);
            }
            MMCEOneBlock.log.info(
                "[MMCE One Block DevValidation] factory placed id={} pos={}",
                FACTORY_TARGET_ID,
                pos
            );
        }

        private void fail(String reason) {
            done = true;
            MMCEOneBlock.log.error(
                "[MMCE One Block DevValidation] FACTORY_FAIL id={} reason={}",
                FACTORY_TARGET_ID,
                reason
            );
        }
    }

    private static final class ValidationState {
        private int ticks = 0;
        private int startedAt = 0;
        private int recipeStartedAt = 0;
        private int redstonePauseStartedAt = 0;
        private int outputBlockStartedAt = 0;
        private int persistenceStartedAt = 0;
        private BlockPos pos = null;
        private boolean formed = false;
        private boolean redstonePauseStarted = false;
        private boolean redstonePaused = false;
        private boolean outputBlocked = false;
        private boolean outputRecovered = false;
        private boolean recipeFinished = false;
        private boolean nbtPayload = false;
        private boolean persistencePrepared = false;
        private boolean chunkUnloaded = false;
        private boolean chunkReloaded = false;
        private boolean inventoryPersisted = false;
        private boolean energySeeded = false;
        private boolean energyPersisted = false;
        private boolean blockDropped = false;
        private boolean tileCleared = false;
        private boolean done = false;
        private int comparatorAfterFormed = 0;
        private long expectedEnergy = 0L;
    }
}
