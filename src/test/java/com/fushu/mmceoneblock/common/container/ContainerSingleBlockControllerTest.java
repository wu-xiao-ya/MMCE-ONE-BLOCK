package com.fushu.mmceoneblock.common.container;

import com.fushu.mmceoneblock.common.network.GuiHandler;
import hellfirepvp.modularmachinery.common.item.ItemBlueprint;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;
import net.minecraft.item.Item;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ContainerSingleBlockControllerTest {
    @Test
    public void slotBoundariesMatchMmceControllerLayout() {
        assertEquals(36, ContainerSingleBlockController.playerSlotCount());
        assertEquals(
            36 + TileMultiblockMachineController.BLUEPRINT_SLOT,
            ContainerSingleBlockController.controllerSlotStart()
        );
        assertEquals(37, ContainerSingleBlockController.firstInternalSlotIndex());
    }

    @Test
    public void blueprintSlotIsPinnedBeforeInternalSlots() {
        int slotCount = ContainerSingleBlockController.firstInternalSlotIndex() + 3;

        assertEquals(36, ContainerSingleBlockController.blueprintSlotIndex());
        assertEquals(ContainerSingleBlockController.blueprintSlotIndex(), ContainerSingleBlockController.controllerSlotStart());
        assertTrue(ContainerSingleBlockController.isBlueprintSlotIndex(ContainerSingleBlockController.blueprintSlotIndex()));
        assertFalse(ContainerSingleBlockController.isBlueprintSlotIndex(ContainerSingleBlockController.blueprintSlotIndex() - 1));
        assertFalse(ContainerSingleBlockController.isBlueprintSlotIndex(ContainerSingleBlockController.firstInternalSlotIndex()));
        assertFalse(ContainerSingleBlockController.isInternalSlotIndex(ContainerSingleBlockController.blueprintSlotIndex(), slotCount));
        assertTrue(ContainerSingleBlockController.isInternalSlotIndex(ContainerSingleBlockController.firstInternalSlotIndex(), slotCount));
        assertTrue(ContainerSingleBlockController.isInternalSlotIndex(slotCount - 1, slotCount));
        assertFalse(ContainerSingleBlockController.isInternalSlotIndex(slotCount, slotCount));
    }

    @Test
    public void internalSlotsRejectBlueprintStacks() {
        assertFalse(ContainerSingleBlockController.isInternalItemValid(new ItemBlueprint()));
        assertTrue(ContainerSingleBlockController.isInternalItemValid(new Item()));
    }

    @Test
    public void internalSlotsUseStableEightColumnGrid() {
        assertEquals(8, ContainerSingleBlockController.internalSlotX(0));
        assertEquals(17, ContainerSingleBlockController.internalSlotY(0));
        assertEquals(134, ContainerSingleBlockController.internalSlotX(7));
        assertEquals(17, ContainerSingleBlockController.internalSlotY(7));
        assertEquals(8, ContainerSingleBlockController.internalSlotX(8));
        assertEquals(35, ContainerSingleBlockController.internalSlotY(8));
    }

    @Test
    public void guiIdIsStableAndBlockUsesConstant() throws IOException {
        assertEquals(1, GuiHandler.GUI_SINGLE_BLOCK_CONTROLLER);

        String blockSource = new String(
            Files.readAllBytes(Paths.get(
                "src",
                "main",
                "java",
                "com",
                "fushu",
                "mmceoneblock",
                "common",
                "block",
                "BlockSingleBlockMachineController.java"
            )),
            StandardCharsets.UTF_8
        );
        assertTrue(blockSource.contains("GuiHandler.GUI_SINGLE_BLOCK_CONTROLLER"));
        assertFalse(blockSource.contains("ordinal()"));
    }

    @Test
    public void shiftClickRoutesPlayerItemsToInternalSlots() {
        ContainerSingleBlockController.MergeRange range = ContainerSingleBlockController.transferTarget(
            0,
            false,
            ContainerSingleBlockController.firstInternalSlotIndex() + 3
        );

        assertEquals(ContainerSingleBlockController.firstInternalSlotIndex(), range.getStart());
        assertEquals(ContainerSingleBlockController.firstInternalSlotIndex() + 3, range.getEnd());
        assertFalse(range.isReverse());
    }

    @Test
    public void shiftClickRoutesPlayerBlueprintsToBlueprintSlotOnly() {
        ContainerSingleBlockController.MergeRange range = ContainerSingleBlockController.transferTarget(
            35,
            true,
            ContainerSingleBlockController.firstInternalSlotIndex() + 3
        );

        assertEquals(ContainerSingleBlockController.controllerSlotStart(), range.getStart());
        assertEquals(ContainerSingleBlockController.controllerSlotStart() + 1, range.getEnd());
        assertFalse(range.isReverse());
    }

    @Test
    public void shiftClickRoutesControllerSlotsBackToPlayerInventory() {
        ContainerSingleBlockController.MergeRange range = ContainerSingleBlockController.transferTarget(
            ContainerSingleBlockController.firstInternalSlotIndex(),
            false,
            ContainerSingleBlockController.firstInternalSlotIndex() + 3
        );

        assertEquals(0, range.getStart());
        assertEquals(ContainerSingleBlockController.playerSlotCount(), range.getEnd());
        assertFalse(range.isReverse());
    }

    @Test
    public void shiftClickRejectsPlayerItemsWhenNoInternalSlotsExist() {
        ContainerSingleBlockController.MergeRange range = ContainerSingleBlockController.transferTarget(
            0,
            false,
            ContainerSingleBlockController.firstInternalSlotIndex()
        );

        assertEquals(-1, range.getStart());
        assertEquals(-1, range.getEnd());
    }

    @Test
    public void shiftClickRejectsOutOfRangeSlotIndex() {
        ContainerSingleBlockController.MergeRange range = ContainerSingleBlockController.transferTarget(
            ContainerSingleBlockController.firstInternalSlotIndex() + 3,
            false,
            ContainerSingleBlockController.firstInternalSlotIndex() + 3
        );

        assertEquals(-1, range.getStart());
        assertEquals(-1, range.getEnd());
    }
}
