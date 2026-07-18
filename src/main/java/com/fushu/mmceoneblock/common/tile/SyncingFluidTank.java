package com.fushu.mmceoneblock.common.tile;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

final class SyncingFluidTank extends FluidTank {
    private final Runnable changeListener;

    SyncingFluidTank(int capacity, Runnable changeListener) {
        super(capacity);
        this.changeListener = changeListener;
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        int filled = super.fill(resource, doFill);
        if (doFill && filled > 0) {
            onTankContentsChanged();
        }
        return filled;
    }

    @Override
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        FluidStack drained = super.drain(resource, doDrain);
        if (doDrain && drained != null && drained.amount > 0) {
            onTankContentsChanged();
        }
        return drained;
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        FluidStack drained = super.drain(maxDrain, doDrain);
        if (doDrain && drained != null && drained.amount > 0) {
            onTankContentsChanged();
        }
        return drained;
    }

    private void onTankContentsChanged() {
        if (this.changeListener != null) {
            this.changeListener.run();
        }
    }
}
