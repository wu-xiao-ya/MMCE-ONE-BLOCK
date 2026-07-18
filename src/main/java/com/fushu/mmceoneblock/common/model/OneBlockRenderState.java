package com.fushu.mmceoneblock.common.model;

import javax.annotation.Nullable;

public final class OneBlockRenderState {
    @Nullable
    public final String definitionId;
    public final boolean formed;
    public final boolean working;
    public final double fluidFillRatio;
    public final double gasFillRatio;
    public final double energyFillRatio;

    public OneBlockRenderState(@Nullable String definitionId,
                               boolean formed,
                               boolean working,
                               double fluidFillRatio,
                               double gasFillRatio,
                               double energyFillRatio) {
        this.definitionId = definitionId;
        this.formed = formed;
        this.working = working;
        this.fluidFillRatio = clamp(fluidFillRatio);
        this.gasFillRatio = clamp(gasFillRatio);
        this.energyFillRatio = clamp(energyFillRatio);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    @Override
    public String toString() {
        return (definitionId == null ? "" : definitionId)
            + "|" + formed
            + "|" + working
            + "|" + fluidFillRatio
            + "|" + gasFillRatio
            + "|" + energyFillRatio;
    }
}
