package com.fushu.mmceoneblock.common.tile;

import com.fushu.mmceoneblock.common.config.MachineDefinition;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTankInfo;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

/**
 * Shared runtime facade for both one-block controller TileEntity variants.
 */
public final class OneBlockControllerRuntime {
    private final MachineComponentStorage.Host host;
    private final MachineComponentStorage components;
    private final OneBlockStateSynchronizer stateSync;
    private final OneBlockCapabilityRouter capabilityRouter;
    @Nullable
    private ResourceLocation lastMissingMachine;

    public OneBlockControllerRuntime(MachineComponentStorage.Host host) {
        this.host = host;
        this.components = new MachineComponentStorage(host);
        this.stateSync = new OneBlockStateSynchronizer(host, components);
        this.components.setChangeListener(stateSync::onResourceChanged);
        this.capabilityRouter = new OneBlockCapabilityRouter(host, components);
    }

    public void setDefinitionId(@Nullable String definitionId) {
        components.setDefinitionId(definitionId);
        stateSync.reset();
    }

    public String getDefinitionId() {
        return components.getDefinitionId();
    }

    public String getDefinitionId(@Nullable World world, @Nullable BlockPos pos) {
        String definitionId = getDefinitionId();
        if (!definitionId.isEmpty()) {
            return definitionId;
        }
        MachineDefinition definition = resolveDefinitionFromBlock(world, pos);
        return definition == null ? "" : definition.getId();
    }

    public long getGroupId() {
        return components.getGroupId();
    }

    public void syncDefinition(com.fushu.mmceoneblock.common.config.MachineDefinition definition) {
        stateSync.beginRestore();
        try {
            components.syncDefinition(definition);
        } finally {
            stateSync.endRestore();
        }
    }

    @Nullable
    public MachineDefinition resolveDefinitionFromBlock(@Nullable World world, @Nullable BlockPos pos) {
        MachineDefinition definition = OneBlockControllerRuntimeSupport.restoreDefinitionFromBlock(world, pos);
        if (definition != null && !definition.getId().equals(components.getDefinitionId())) {
            setDefinitionId(definition.getId());
        }
        return definition;
    }

    @Nullable
    public DynamicMachine resolveBackingMachine(MachineDefinition definition, String controllerLabel) {
        DynamicMachine machine = OneBlockControllerRuntimeSupport.resolveBackingMachine(definition);
        if (machine == null) {
            ResourceLocation missingMachine = definition.getMachine();
            if (!missingMachine.equals(lastMissingMachine)) {
                com.fushu.mmceoneblock.MMCEOneBlock.log.warn(
                    "One-block {} '{}' points to unknown MMCE machine '{}'.",
                    controllerLabel,
                    definition.getId(),
                    missingMachine
                );
                lastMissingMachine = missingMachine;
            }
            return null;
        }
        lastMissingMachine = null;
        return machine;
    }

    @Nonnull
    public hellfirepvp.modularmachinery.common.machine.TaggedPositionBlockArray createSyntheticPattern(@Nullable World world,
                                                                                                       @Nullable BlockPos pos) {
        return OneBlockControllerRuntimeSupport.createSyntheticPattern(world, pos);
    }

    @Nullable
    public ResourceLocation resolveGuiStyle(MachineDefinition definition) {
        return OneBlockControllerRuntimeSupport.resolveGuiStyle(definition);
    }

    @Nullable
    public ResourceLocation resolveFactoryGuiStyle(MachineDefinition definition) {
        return OneBlockControllerRuntimeSupport.resolveFactoryGuiStyle(definition);
    }

    @Nonnull
    public Collection<MachineComponent<?>> provideMachineComponents() {
        Collection<MachineComponent<?>> result = components.provideMachineComponents();
        stateSync.updateOneBlockCustomData(false);
        return result;
    }

    public void readPayload(NBTTagCompound compound) {
        stateSync.beginRestore();
        try {
            components.setDefinitionId(compound.getString("definitionId"));
            stateSync.reset();
            com.fushu.mmceoneblock.common.config.MachineDefinition definition = host.getDefinition();
            if (definition != null) {
                components.syncDefinition(definition);
            }
            components.readPayload(compound);
        } finally {
            stateSync.endRestore();
        }
    }

    public void writePayload(NBTTagCompound compound) {
        stateSync.updateOneBlockCustomData(false);
        compound.setString("definitionId", host.getDefinitionId());
        components.writePayload(compound);
    }

    public void updateOneBlockCustomData(boolean notifyClient) {
        stateSync.updateOneBlockCustomData(notifyClient);
    }

    public int getItemInputSlotCount() {
        return components.getItemInputSlotCount();
    }

    public boolean isItemInputInventorySlot(int slot) {
        return components.isItemInputInventorySlot(slot);
    }

    public boolean isManagedCapability(Capability<?> capability) {
        return capabilityRouter.isManagedCapability(capability);
    }

    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capabilityRouter.hasCapability(capability);
    }

    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        return capabilityRouter.getCapability(capability, facing);
    }

    public boolean canTubeConnect(EnumFacing side) {
        return capabilityRouter.canTubeConnect(side);
    }

    public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
        return capabilityRouter.receiveGas(side, stack, doTransfer);
    }

    @Nullable
    public GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
        return capabilityRouter.drawGas(side, amount, doTransfer);
    }

    public boolean canReceiveGas(EnumFacing side, Gas type) {
        return capabilityRouter.canReceiveGas(side, type);
    }

    public boolean canDrawGas(EnumFacing side, Gas type) {
        return capabilityRouter.canDrawGas(side, type);
    }

    @Nonnull
    public GasTankInfo[] getTankInfo() {
        return capabilityRouter.getTankInfo();
    }

    public void clearTransientComponentCache() {
        components.clearTransientComponentCache();
        stateSync.reset();
    }
}
