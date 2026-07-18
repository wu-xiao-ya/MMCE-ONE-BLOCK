package com.fushu.mmceoneblock.common.tile;

import github.kasuminova.mmce.common.util.IExtendedGasHandler;
import hellfirepvp.modularmachinery.common.crafting.ComponentType;
import hellfirepvp.modularmachinery.common.lib.ComponentTypesMM;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.util.IEnergyHandlerAsync;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class OneBlockRuntimeComponents {
    private final MachineComponentStorage.Host host;
    private final OneBlockResourceState resources;

    OneBlockRuntimeComponents(MachineComponentStorage.Host host,
                              OneBlockResourceState resources) {
        this.host = host;
        this.resources = resources;
    }

    Collection<MachineComponent<?>> build() {
        List<MachineComponent<?>> out = new ArrayList<MachineComponent<?>>();
        for (OneBlockResourceState.ComponentRuntime runtime : resources.runtimes()) {
            if (runtime instanceof OneBlockResourceState.ItemRuntime) {
                out.add(new ItemMachineComponent((OneBlockResourceState.ItemRuntime) runtime));
            } else if (runtime instanceof OneBlockResourceState.FluidRuntime) {
                out.add(new FluidMachineComponent((OneBlockResourceState.FluidRuntime) runtime));
            } else if (runtime instanceof OneBlockResourceState.GasRuntime) {
                out.add(new GasMachineComponent((OneBlockResourceState.GasRuntime) runtime));
            } else if (runtime instanceof OneBlockResourceState.EnergyRuntime) {
                out.add(new EnergyMachineComponent((OneBlockResourceState.EnergyRuntime) runtime));
            }
        }
        return out.isEmpty()
            ? Collections.<MachineComponent<?>>emptyList()
            : Collections.unmodifiableList(out);
    }

    private final class ItemMachineComponent extends MachineComponent.ItemBus {
        private final OneBlockResourceState.ItemRuntime runtime;

        private ItemMachineComponent(OneBlockResourceState.ItemRuntime runtime) {
            super(runtime.ioType);
            this.runtime = runtime;
        }

        @Override
        public IItemHandlerModifiable getContainerProvider() {
            return new SlotFilteredItemHandler(host.getInventory(), runtime.slots);
        }

        @Override
        public long getGroupID() {
            return runtime.groupId;
        }
    }

    private static final class FluidMachineComponent extends MachineComponent.FluidHatch {
        private final OneBlockResourceState.FluidRuntime runtime;

        private FluidMachineComponent(OneBlockResourceState.FluidRuntime runtime) {
            super(runtime.ioType);
            this.runtime = runtime;
        }

        @Override
        public FluidTank getContainerProvider() {
            return runtime.tank;
        }

        @Override
        public long getGroupID() {
            return runtime.groupId;
        }
    }

    private static final class GasMachineComponent extends MachineComponent<IExtendedGasHandler> {
        private final OneBlockResourceState.GasRuntime runtime;

        private GasMachineComponent(OneBlockResourceState.GasRuntime runtime) {
            super(runtime.ioType);
            this.runtime = runtime;
        }

        @Override
        public ComponentType getComponentType() {
            return ComponentTypesMM.COMPONENT_GAS;
        }

        @Override
        public IExtendedGasHandler getContainerProvider() {
            return runtime.tank;
        }

        @Override
        public long getGroupID() {
            return runtime.groupId;
        }
    }

    private static final class EnergyMachineComponent extends MachineComponent.EnergyHatch {
        private final OneBlockResourceState.EnergyRuntime runtime;

        private EnergyMachineComponent(OneBlockResourceState.EnergyRuntime runtime) {
            super(runtime.ioType);
            this.runtime = runtime;
        }

        @Override
        public IEnergyHandlerAsync getContainerProvider() {
            return runtime.handler;
        }

        @Override
        public long getGroupID() {
            return runtime.groupId;
        }
    }

    private static final class SlotFilteredItemHandler implements IItemHandlerModifiable {
        @Nullable
        private final IItemHandlerModifiable backing;
        private final int[] slots;

        private SlotFilteredItemHandler(@Nullable IItemHandlerModifiable backing, int[] slots) {
            this.backing = backing;
            this.slots = slots == null ? new int[0] : slots.clone();
        }

        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
            int actual = map(slot);
            if (actual >= 0 && backing != null) {
                backing.setStackInSlot(actual, stack);
            }
        }

        @Override
        public int getSlots() {
            return slots.length;
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
            int actual = map(slot);
            return actual < 0 || backing == null ? stack : backing.insertItem(actual, stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int actual = map(slot);
            return actual < 0 || backing == null
                ? ItemStack.EMPTY
                : backing.extractItem(actual, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            int actual = map(slot);
            return actual < 0 || backing == null ? 0 : backing.getSlotLimit(actual);
        }

        private int map(int slot) {
            return slot < 0 || slot >= slots.length ? -1 : slots[slot];
        }
    }
}
