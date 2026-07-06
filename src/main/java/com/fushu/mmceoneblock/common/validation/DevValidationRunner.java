package com.fushu.mmceoneblock.common.validation;

import com.fushu.mmceoneblock.MMCEOneBlock;
import com.fushu.mmceoneblock.common.block.BlockSingleBlockMachineController;
import com.fushu.mmceoneblock.common.registry.MachineRegistry;
import com.fushu.mmceoneblock.common.tile.TileSingleBlockMachineController;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.util.IEnergyHandlerAsync;
import hellfirepvp.modularmachinery.common.util.IOInventory;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Collection;

public final class DevValidationRunner {
    private static final String ENABLE_PROPERTY = "mmceoneblock.devValidation";
    private static final String TARGET_ID = "starter_controller";
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
            insertInput(tile);
            return;
        }

        if (!this.state.recipeFinished) {
            IOInventory inventory = tile.getInventory();
            if (containsStoneOutput(inventory)) {
                this.state.recipeFinished = true;
                if (!validateNbtPayload(tile)) {
                    return;
                }
                cleanup(world);
                pass();
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

        BlockPos base = world.getSpawnPoint().add(3, 0, 3);
        BlockPos top = world.getTopSolidOrLiquidBlock(base);
        BlockPos pos = top.getY() <= 0 ? new BlockPos(base.getX(), 80, base.getZ()) : top.up();
        this.state.pos = pos;
        this.state.startedAt = this.state.ticks;

        world.setBlockToAir(pos);
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

    private void insertInput(TileSingleBlockMachineController tile) {
        IOInventory inventory = tile.getInventory();
        if (inventory.getSlots() < 3) {
            fail("inventory_too_small:" + inventory.getSlots());
            return;
        }
        inventory.setStackInSlot(1, new ItemStack(Blocks.COBBLESTONE));
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

    private int comparator(WorldServer world) {
        Block block = world.getBlockState(this.state.pos).getBlock();
        return block.getComparatorInputOverride(world.getBlockState(this.state.pos), world, this.state.pos);
    }

    private boolean validateNbtPayload(TileSingleBlockMachineController tile) {
        IEnergyHandlerAsync energy = findEnergy(tile.provideMachineComponents());
        if (energy != null) {
            energy.setCurrentEnergy(123L);
        }
        NBTTagCompound tag = new NBTTagCompound();
        tile.writeToNBT(tag);
        if (!TARGET_ID.equals(tag.getString("definitionId"))) {
            fail("nbt_definition_mismatch:" + tag.getString("definitionId"));
            return false;
        }
        if (!tag.hasKey("oneBlockFluid") || !tag.hasKey("oneBlockGas") || !tag.hasKey("oneBlockEnergy")) {
            fail("nbt_payload_missing");
            return false;
        }
        this.state.nbtPayload = true;
        return true;
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

    private void cleanup(WorldServer world) {
        if (this.state.pos != null) {
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
            "[MMCE One Block DevValidation] PASS id={} formed={} recipeFinished={} nbtPayload={} comparatorAfterFormed={}",
            TARGET_ID,
            this.state.formed,
            this.state.recipeFinished,
            this.state.nbtPayload,
            this.state.comparatorAfterFormed
        );
    }

    private void fail(String reason) {
        this.state.done = true;
        MMCEOneBlock.log.error("[MMCE One Block DevValidation] FAIL reason={}", reason);
    }

    private static final class ValidationState {
        private int ticks = 0;
        private int startedAt = 0;
        private int recipeStartedAt = 0;
        private BlockPos pos = null;
        private boolean formed = false;
        private boolean recipeFinished = false;
        private boolean nbtPayload = false;
        private boolean done = false;
        private int comparatorAfterFormed = 0;
    }
}
