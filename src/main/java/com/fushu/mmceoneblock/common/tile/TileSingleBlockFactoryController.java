package com.fushu.mmceoneblock.common.tile;

import com.fushu.mmceguiext.api.gui.IMachineGuiStyleProvider;
import com.fushu.mmceguiext.api.machine.IMultiMachineComponentProvider;
import com.fushu.mmceoneblock.common.config.MachineDefinition;
import com.fushu.mmceoneblock.common.registry.MachineRegistry;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.crafting.ActiveMachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.machine.RecipeThread;
import hellfirepvp.modularmachinery.common.tiles.TileFactoryController;
import hellfirepvp.modularmachinery.common.util.IOInventory;
import mekanism.api.gas.IGasHandler;
import mekanism.api.gas.ITubeConnection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

@Optional.InterfaceList({
    @Optional.Interface(modid = "mekanism", iface = "mekanism.api.gas.IGasHandler"),
    @Optional.Interface(modid = "mekanism", iface = "mekanism.api.gas.ITubeConnection")
})
public class TileSingleBlockFactoryController extends TileFactoryController
    implements MachineComponentStorage.Host, IMultiMachineComponentProvider,
    IMachineGuiStyleProvider, IGasHandler, ITubeConnection {

    private final OneBlockControllerRuntime runtime;

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
        return runtime.getDefinitionId(world, pos);
    }

    @Nullable
    @Override
    public MachineDefinition getDefinition() {
        String id = getDefinitionId();
        return id.isEmpty() ? null : MachineRegistry.getDefinition(id);
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

        DynamicMachine machine = runtime.resolveBackingMachine(definition, "factory");
        if (machine == null) {
            resetMachine(true);
            return true;
        }

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
        foundPattern = runtime.createSyntheticPattern(world, pos);
        foundReplacements = new DynamicMachine.ModifierReplacementMap();
        foundDynamicPatterns.clear();
        runtime.syncDefinition(definition);
        onStructureFormed();
        return true;
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
        return runtime.resolveFactoryGuiStyle(getDefinition());
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
