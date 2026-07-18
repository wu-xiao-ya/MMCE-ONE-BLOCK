package com.fushu.mmceoneblock.common.tile;

import com.fushu.mmceguiext.api.gui.IMachineGuiStyleProvider;
import com.fushu.mmceguiext.api.machine.IMultiMachineComponentProvider;
import com.fushu.mmceoneblock.MMCEOneBlock;
import com.fushu.mmceoneblock.common.block.BlockSingleBlockMachineController;
import com.fushu.mmceoneblock.common.config.MachineDefinition;
import com.fushu.mmceoneblock.common.registry.MachineRegistry;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.crafting.ActiveMachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.machine.RecipeThread;
import hellfirepvp.modularmachinery.common.machine.TaggedPositionBlockArray;
import hellfirepvp.modularmachinery.common.tiles.TileFactoryController;
import hellfirepvp.modularmachinery.common.util.BlockArray;
import hellfirepvp.modularmachinery.common.util.IBlockStateDescriptor;
import hellfirepvp.modularmachinery.common.util.IOInventory;
import mekanism.api.gas.IGasHandler;
import mekanism.api.gas.ITubeConnection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

@Optional.InterfaceList({
    @Optional.Interface(modid = "mekanism", iface = "mekanism.api.gas.IGasHandler"),
    @Optional.Interface(modid = "mekanism", iface = "mekanism.api.gas.ITubeConnection")
})
public class TileSingleBlockFactoryController extends TileFactoryController
    implements MachineComponentStorage.Host, IMultiMachineComponentProvider,
    IMachineGuiStyleProvider, IGasHandler, ITubeConnection {

    private final OneBlockControllerRuntime runtime;
    private ResourceLocation lastMissingMachine;

    public TileSingleBlockFactoryController() {
        super();
        runtime = new OneBlockControllerRuntime(this);
    }

    public TileSingleBlockFactoryController(@Nullable IBlockState state, @Nullable String definitionId) {
        this();
        runtime.setDefinitionId(definitionId);
        if (state != null && state.getBlock() instanceof BlockController) {
            controllerRotation = state.getValue(BlockController.FACING);
        }
    }

    @Override
    public void setDefinitionId(@Nullable String definitionId) {
        runtime.setDefinitionId(definitionId);
    }

    @Override
    public String getDefinitionId() {
        String fromRuntime = runtime.getDefinitionId();
        return fromRuntime.isEmpty() ? resolveDefinitionFromBlock() : fromRuntime;
    }

    private String resolveDefinitionFromBlock() {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) {
            return "";
        }
        if (!(world.getBlockState(pos).getBlock() instanceof BlockSingleBlockMachineController)) {
            return "";
        }
        BlockSingleBlockMachineController block =
            (BlockSingleBlockMachineController) world.getBlockState(pos).getBlock();
        MachineDefinition definition = block.getDefinition();
        if (definition != null && !definition.getId().equals(runtime.getDefinitionId())) {
            setDefinitionId(definition.getId());
            return definition.getId();
        }
        return definition == null ? "" : definition.getId();
    }

    @Nullable
    @Override
    public MachineDefinition getDefinition() {
        String id = getDefinitionId();
        return id.isEmpty() ? null : MachineRegistry.getDefinition(id);
    }

    public boolean ensureOneBlockStructureReady() {
        if (world != null && !world.isRemote) {
            checkStructure();
        }
        return isStructureFormed() && getFoundMachine() != null;
    }

    @Override
    protected boolean canCheckStructure() {
        return getDefinition() != null;
    }

    @Override
    protected boolean checkStructure() {
        if (!canCheckStructure()) {
            return true;
        }
        lastStructureCheckTick = ticksExisted;
        checkRotation();

        MachineDefinition definition = getDefinition();
        if (definition == null) {
            resetMachine(true);
            return true;
        }

        DynamicMachine machine = hellfirepvp.modularmachinery.common.machine.MachineRegistry
            .getRegistry().getMachine(definition.getMachine());
        if (machine == null) {
            if (!definition.getMachine().equals(lastMissingMachine)) {
                MMCEOneBlock.log.warn("One-block factory '{}' points to unknown MMCE machine '{}'.",
                    definition.getId(), definition.getMachine());
                lastMissingMachine = definition.getMachine();
            }
            resetMachine(true);
            return true;
        }
        lastMissingMachine = null;

        if (machine.isRequiresBlueprint() && !machine.equals(getBlueprintMachine())) {
            resetMachine(true);
            return true;
        }
        if (!world.isBlockLoaded(pos)) {
            return false;
        }
        if (isStructureFormed()
            && machine.equals(foundMachine)
            && foundPattern != null
            && foundPattern.matches(world, pos, true, foundReplacements)) {
            return true;
        }

        prevMachine = foundMachine;
        foundMachine = machine;
        parentMachine = machine;
        foundPattern = createSyntheticPattern();
        foundReplacements = new DynamicMachine.ModifierReplacementMap();
        foundDynamicPatterns.clear();
        runtime.syncDefinition(definition);
        onStructureFormed();
        return true;
    }

    private TaggedPositionBlockArray createSyntheticPattern() {
        TaggedPositionBlockArray pattern = new TaggedPositionBlockArray();
        IBlockState state = world.getBlockState(pos);
        BlockArray.BlockInformation info = new BlockArray.BlockInformation(
            Collections.singletonList(IBlockStateDescriptor.of(state.getBlock())));
        pattern.addBlock(BlockPos.ORIGIN, info);
        pattern.flushTileBlocksCache();
        return pattern;
    }

    @Override
    protected void updateComponents() {
        super.updateComponents();
        runtime.updateOneBlockCustomData(true);
    }

    @Override
    public RecipeCraftingContext createContext(ActiveMachineRecipe activeRecipe) {
        return super.createContext(activeRecipe);
    }

    @Nonnull
    @Override
    public Collection<MachineComponent<?>> provideMachineComponents() {
        return runtime.provideMachineComponents();
    }

    @Nullable
    @Override
    public MachineComponent<?> provideComponent() {
        return null;
    }

    public int getItemInputSlotCount() {
        return runtime.getItemInputSlotCount();
    }

    public boolean isItemInputInventorySlot(int slot) {
        return runtime.isItemInputInventorySlot(slot);
    }

    @Override
    public long getUniqueGroupID() {
        return runtime.getGroupId();
    }

    @Override
    public long getMachineComponentGroupId() {
        return runtime.getGroupId();
    }

    @Override
    public int getMaxThreads() {
        MachineDefinition definition = getDefinition();
        if (definition == null) {
            return isStructureFormed() ? super.getMaxThreads() : 0;
        }
        DynamicMachine machine = hellfirepvp.modularmachinery.common.machine.MachineRegistry
            .getRegistry().getMachine(definition.getMachine());
        if (machine == null) {
            return isStructureFormed() ? super.getMaxThreads() : 0;
        }
        return Math.max(0, machine.getMaxThreads() + getExtraThreadCount());
    }

    @Override
    public void readCustomNBT(NBTTagCompound compound) {
        super.readCustomNBT(compound);
        runtime.readPayload(compound);
    }

    void readOneBlockPayload(NBTTagCompound compound) {
        runtime.readPayload(compound);
    }

    @Override
    public void writeCustomNBT(NBTTagCompound compound) {
        super.writeCustomNBT(compound);
        runtime.writePayload(compound);
    }

    void writeOneBlockPayload(NBTTagCompound compound) {
        runtime.writePayload(compound);
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        if (runtime.isManagedCapability(capability)) {
            return runtime.hasCapability(capability, facing);
        }
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        if (runtime.isManagedCapability(capability)) {
            return runtime.getCapability(capability, facing);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    @Optional.Method(modid = "mekanism")
    public boolean canTubeConnect(EnumFacing side) {
        return runtime.canTubeConnect(side);
    }

    @Override
    @Optional.Method(modid = "mekanism")
    public int receiveGas(EnumFacing side, mekanism.api.gas.GasStack stack, boolean doTransfer) {
        return runtime.receiveGas(side, stack, doTransfer);
    }

    @Override
    @Optional.Method(modid = "mekanism")
    public mekanism.api.gas.GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
        return runtime.drawGas(side, amount, doTransfer);
    }

    @Override
    @Optional.Method(modid = "mekanism")
    public boolean canReceiveGas(EnumFacing side, mekanism.api.gas.Gas type) {
        return runtime.canReceiveGas(side, type);
    }

    @Override
    @Optional.Method(modid = "mekanism")
    public boolean canDrawGas(EnumFacing side, mekanism.api.gas.Gas type) {
        return runtime.canDrawGas(side, type);
    }

    @Nonnull
    @Override
    @Optional.Method(modid = "mekanism")
    public mekanism.api.gas.GasTankInfo[] getTankInfo() {
        return runtime.getTankInfo();
    }

    @Nullable
    @Override
    public ResourceLocation getMachineControllerGuiStyle() {
        MachineDefinition definition = getDefinition();
        if (definition == null) {
            return null;
        }
        String factoryGuiStyle = definition.getFactoryGuiStyle();
        if (factoryGuiStyle != null && !factoryGuiStyle.trim().isEmpty()) {
            try {
                return new ResourceLocation(factoryGuiStyle.trim().toLowerCase(Locale.ROOT));
            } catch (Exception ex) {
                // fall through to the regular style
            }
        }
        if (definition.getGuiStyle() == null || definition.getGuiStyle().trim().isEmpty()) {
            return null;
        }
        try {
            return new ResourceLocation(definition.getGuiStyle().trim().toLowerCase(Locale.ROOT));
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public IOInventory getInventory() {
        return inventory;
    }

    @Override
    public void setInventory(IOInventory inv) {
        inventory = inv;
    }

    @Override
    public int getTicksExisted() {
        return ticksExisted;
    }

    @Override
    public void markStorageDirty() {
        markNoUpdateSync();
    }

    @Override
    public void markStorageForUpdate() {
        markForUpdateSync();
    }

    @Override
    public int getActiveRuntimeThreadCount() {
        return getActiveRecipeList().length;
    }

    @Override
    public int getRuntimeThreadCapacity() {
        return getMaxThreads();
    }

    @Override
    public void markDirty() {
        super.markDirty();
    }

    @Override
    public void clearTransientComponentCache() {
        runtime.clearTransientComponentCache();
    }

    @Override
    public NBTTagCompound getCustomDataTag() {
        return customData;
    }

    @Override
    public void setCustomDataTag(NBTTagCompound tag) {
        customData = tag;
    }

    @Override
    public void invalidate() {
        runtime.clearTransientComponentCache();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        runtime.clearTransientComponentCache();
        super.onChunkUnload();
    }

    @Override
    public RecipeThread[] getRecipeThreadList() {
        return super.getRecipeThreadList();
    }
}
