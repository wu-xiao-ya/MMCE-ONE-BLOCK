package com.fushu.mmceoneblock.common.tile;

import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.util.IOInventory;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTankInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class OneBlockCapabilityRouter {
    private final MachineComponentStorage.Host host;
    private final MachineComponentStorage storage;

    OneBlockCapabilityRouter(MachineComponentStorage.Host host,
                             MachineComponentStorage storage) {
        this.host = host;
        this.storage = storage;
    }

    boolean isManagedCapability(Capability<?> capability) {
        return capability != null
            && (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
            || capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
            || capability == CapabilityEnergy.ENERGY);
    }

    boolean hasCapability(Capability<?> capability) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return hasItemStorage();
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return hasFluidStorage();
        }
        if (capability == CapabilityEnergy.ENERGY) {
            return hasEnergyStorage();
        }
        return false;
    }

    boolean hasItemStorage() {
        return storage.getItemInputSlots().length > 0
            || storage.getItemOutputSlots().length > 0;
    }

    boolean hasFluidStorage() {
        return !storage.fluidRuntimes().isEmpty();
    }

    boolean hasEnergyStorage() {
        return !storage.energyRuntimes().isEmpty();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (!hasCapability(capability)) {
            return null;
        }
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) getItemHandler();
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return (T) getFluidHandler();
        }
        if (capability == CapabilityEnergy.ENERGY) {
            return (T) getEnergyStorage();
        }
        return null;
    }

    @Nullable
    IItemHandlerModifiable getItemHandler() {
        return hasItemStorage()
            ? new DirectionalItemHandler(
                host.getInventory(),
                storage.getItemInputSlots(),
                storage.getItemOutputSlots()
            )
            : null;
    }

    @Nullable
    IFluidHandler getFluidHandler() {
        return hasFluidStorage() ? new CombinedFluidHandler(storage.fluidRuntimes()) : null;
    }

    @Nullable
    IEnergyStorage getEnergyStorage() {
        return hasEnergyStorage() ? new CombinedEnergyStorage(storage.energyRuntimes()) : null;
    }

    boolean canTubeConnect(EnumFacing side) {
        return !storage.gasRuntimes().isEmpty();
    }

    int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
        if (stack == null || stack.amount <= 0) {
            return 0;
        }
        int accepted = 0;
        for (OneBlockResourceState.GasRuntime runtime : storage.gasRuntimes()) {
            if (runtime.ioType != IOType.INPUT || accepted >= stack.amount) {
                continue;
            }
            GasStack remaining = stack.copy();
            remaining.amount = stack.amount - accepted;
            accepted += runtime.tank.receiveGas(side, remaining, doTransfer);
        }
        return accepted;
    }

    @Nullable
    GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
        if (amount <= 0) {
            return null;
        }
        for (OneBlockResourceState.GasRuntime runtime : storage.gasRuntimes()) {
            if (runtime.ioType != IOType.OUTPUT) {
                continue;
            }
            GasStack drawn = runtime.tank.drawGas(side, amount, doTransfer);
            if (drawn != null && drawn.amount > 0) {
                return drawn;
            }
        }
        return null;
    }

    boolean canReceiveGas(EnumFacing side, Gas type) {
        if (type == null) {
            return false;
        }
        for (OneBlockResourceState.GasRuntime runtime : storage.gasRuntimes()) {
            if (runtime.ioType == IOType.INPUT && runtime.tank.canReceiveGas(side, type)) {
                return true;
            }
        }
        return false;
    }

    boolean canDrawGas(EnumFacing side, Gas type) {
        if (type == null) {
            return false;
        }
        for (OneBlockResourceState.GasRuntime runtime : storage.gasRuntimes()) {
            if (runtime.ioType == IOType.OUTPUT && runtime.tank.canDrawGas(side, type)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    GasTankInfo[] getTankInfo() {
        List<GasTankInfo> out = new ArrayList<GasTankInfo>();
        for (OneBlockResourceState.GasRuntime runtime : storage.gasRuntimes()) {
            out.addAll(Arrays.asList(runtime.tank.getTankInfo()));
        }
        return out.toArray(new GasTankInfo[out.size()]);
    }

    private static final class DirectionalItemHandler implements IItemHandlerModifiable {
        @Nullable private final IItemHandlerModifiable backing;
        private final int[] inputs;
        private final int[] outputs;
        private final int[] exposed;

        private DirectionalItemHandler(@Nullable IOInventory backing, int[] inputs, int[] outputs) {
            this.backing = backing;
            this.inputs = inputs == null ? new int[0] : inputs.clone();
            this.outputs = outputs == null ? new int[0] : outputs.clone();
            this.exposed = new int[this.inputs.length + this.outputs.length];
            System.arraycopy(this.inputs, 0, exposed, 0, this.inputs.length);
            System.arraycopy(this.outputs, 0, exposed, this.inputs.length, this.outputs.length);
        }

        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
            // External automation must use insert/extract so component direction is enforced.
        }

        @Override
        public int getSlots() {
            return exposed.length;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            int actual = map(slot);
            return actual < 0 || backing == null ? ItemStack.EMPTY : backing.getStackInSlot(actual);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= inputs.length) {
                return stack;
            }
            int actual = map(slot);
            return actual < 0 || backing == null ? stack : backing.insertItem(actual, stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < inputs.length || slot >= exposed.length) {
                return ItemStack.EMPTY;
            }
            int actual = map(slot);
            return actual < 0 || backing == null
                ? ItemStack.EMPTY : backing.extractItem(actual, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            int actual = map(slot);
            return actual < 0 || backing == null ? 0 : backing.getSlotLimit(actual);
        }

        private int map(int slot) {
            return slot < 0 || slot >= exposed.length ? -1 : exposed[slot];
        }
    }

    private static final class CombinedFluidHandler implements IFluidHandler {
        private final List<OneBlockResourceState.FluidRuntime> runtimes;

        private CombinedFluidHandler(List<OneBlockResourceState.FluidRuntime> runtimes) {
            this.runtimes = new ArrayList<OneBlockResourceState.FluidRuntime>(runtimes);
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (resource == null || resource.amount <= 0) {
                return 0;
            }
            int filled = 0;
            for (OneBlockResourceState.FluidRuntime runtime : runtimes) {
                if (runtime.ioType != IOType.INPUT || filled >= resource.amount) {
                    continue;
                }
                FluidStack remaining = resource.copy();
                remaining.amount = resource.amount - filled;
                filled += runtime.tank.fill(remaining, doFill);
            }
            return filled;
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || resource.amount <= 0) {
                return null;
            }
            for (OneBlockResourceState.FluidRuntime runtime : runtimes) {
                if (runtime.ioType != IOType.OUTPUT) {
                    continue;
                }
                FluidStack drained = runtime.tank.drain(resource, doDrain);
                if (drained != null && drained.amount > 0) {
                    return drained;
                }
            }
            return null;
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            if (maxDrain <= 0) {
                return null;
            }
            for (OneBlockResourceState.FluidRuntime runtime : runtimes) {
                if (runtime.ioType != IOType.OUTPUT) {
                    continue;
                }
                FluidStack drained = runtime.tank.drain(maxDrain, doDrain);
                if (drained != null && drained.amount > 0) {
                    return drained;
                }
            }
            return null;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            List<IFluidTankProperties> out = new ArrayList<IFluidTankProperties>();
            for (OneBlockResourceState.FluidRuntime runtime : runtimes) {
                for (IFluidTankProperties property : runtime.tank.getTankProperties()) {
                    out.add(new DirectionalFluidTankProperties(
                        property,
                        runtime.ioType == IOType.INPUT,
                        runtime.ioType == IOType.OUTPUT
                    ));
                }
            }
            return out.toArray(new IFluidTankProperties[out.size()]);
        }
    }

    private static final class DirectionalFluidTankProperties implements IFluidTankProperties {
        private final IFluidTankProperties backing;
        private final boolean canFill;
        private final boolean canDrain;

        private DirectionalFluidTankProperties(IFluidTankProperties backing,
                                               boolean canFill,
                                               boolean canDrain) {
            this.backing = backing;
            this.canFill = canFill;
            this.canDrain = canDrain;
        }

        @Nullable
        @Override
        public FluidStack getContents() {
            return backing.getContents();
        }

        @Override
        public int getCapacity() {
            return backing.getCapacity();
        }

        @Override
        public boolean canFill() {
            return canFill;
        }

        @Override
        public boolean canDrain() {
            return canDrain;
        }

        @Override
        public boolean canFillFluidType(FluidStack fluidStack) {
            return canFill && backing.canFillFluidType(fluidStack);
        }

        @Override
        public boolean canDrainFluidType(FluidStack fluidStack) {
            return canDrain && backing.canDrainFluidType(fluidStack);
        }
    }

    private static final class CombinedEnergyStorage implements IEnergyStorage {
        private final List<OneBlockResourceState.EnergyRuntime> runtimes;

        private CombinedEnergyStorage(List<OneBlockResourceState.EnergyRuntime> runtimes) {
            this.runtimes = new ArrayList<OneBlockResourceState.EnergyRuntime>(runtimes);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int accepted = 0;
            for (OneBlockResourceState.EnergyRuntime runtime : runtimes) {
                if (runtime.ioType != IOType.INPUT || accepted >= maxReceive) {
                    continue;
                }
                accepted += runtime.handler.receiveEnergy(maxReceive - accepted, simulate);
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = 0;
            for (OneBlockResourceState.EnergyRuntime runtime : runtimes) {
                if (runtime.ioType != IOType.OUTPUT || extracted >= maxExtract) {
                    continue;
                }
                extracted += runtime.handler.extractEnergy(maxExtract - extracted, simulate);
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            long total = 0L;
            for (OneBlockResourceState.EnergyRuntime runtime : runtimes) {
                total += runtime.handler.getCurrentEnergy();
            }
            return (int) Math.min(Integer.MAX_VALUE, total);
        }

        @Override
        public int getMaxEnergyStored() {
            long total = 0L;
            for (OneBlockResourceState.EnergyRuntime runtime : runtimes) {
                total += runtime.handler.getMaxEnergy();
            }
            return (int) Math.min(Integer.MAX_VALUE, total);
        }

        @Override
        public boolean canExtract() {
            for (OneBlockResourceState.EnergyRuntime runtime : runtimes) {
                if (runtime.ioType == IOType.OUTPUT) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canReceive() {
            for (OneBlockResourceState.EnergyRuntime runtime : runtimes) {
                if (runtime.ioType == IOType.INPUT) {
                    return true;
                }
            }
            return false;
        }
    }
}
