package com.fushu.mmceoneblock.common.validation;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DevValidationRunnerTest {
    @Test
    public void recipeTimeoutUsesTheLatestRecipeStartInsteadOfMachinePlacement() {
        assertFalse(DevValidationRunner.hasTimedOut(420, 360, 60));
        assertTrue(DevValidationRunner.hasTimedOut(421, 360, 60));
    }

    @Test
    public void structureTimeoutStillUsesTheMachinePlacementStart() {
        assertFalse(DevValidationRunner.hasTimedOut(180, 20, 160));
        assertTrue(DevValidationRunner.hasTimedOut(181, 20, 160));
    }
}
