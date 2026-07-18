package com.fushu.mmceoneblock.common.container;

import com.fushu.mmceguiext.api.gui.PlayerInventoryDescriptor;
import com.fushu.mmceguiext.api.gui.SlotGroupDescriptor;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ContainerSingleBlockControllerTest {
    @Test
    public void controllerLayoutMatchesPublishedSlotDescriptors() {
        List<SlotGroupDescriptor> groups =
            OneBlockContainerSupport.Layout.CONTROLLER.slotGroups(9, 19);

        assertEquals(3, groups.size());
        assertSlotGroup(groups.get(0), "input", 37, 9, 8, 17, 2, 5);
        assertSlotGroup(groups.get(1), "output", 46, 9, 8, 66, 2, 5);
        assertSlotGroup(groups.get(2), "blueprint", 36, 1, 151, 8, 1, 1);

        PlayerInventoryDescriptor playerInventory =
            OneBlockContainerSupport.Layout.CONTROLLER.playerInventory();
        assertEquals(8, playerInventory.x);
        assertEquals(131, playerInventory.y);
        assertEquals(8, playerInventory.hotbarX);
        assertEquals(189, playerInventory.hotbarY);
        assertEquals(0, playerInventory.mainStart);
        assertEquals(27, playerInventory.hotbarStart);
        assertTrue(playerInventory.enabled);

        assertEquals(8, OneBlockContainerSupport.internalSlotX(
            OneBlockContainerSupport.Layout.CONTROLLER, 0, 9));
        assertEquals(80, OneBlockContainerSupport.internalSlotX(
            OneBlockContainerSupport.Layout.CONTROLLER, 4, 9));
        assertEquals(8, OneBlockContainerSupport.internalSlotX(
            OneBlockContainerSupport.Layout.CONTROLLER, 9, 9));
        assertEquals(17, OneBlockContainerSupport.internalSlotY(
            OneBlockContainerSupport.Layout.CONTROLLER, 0, 9));
        assertEquals(66, OneBlockContainerSupport.internalSlotY(
            OneBlockContainerSupport.Layout.CONTROLLER, 9, 9));
    }

    @Test
    public void controllerShiftClickRoutesBlueprintInputAndMachineSlots() {
        assertRange(
            OneBlockContainerSupport.transferTarget(0, true, 55, 9),
            36, 37
        );
        assertRange(
            OneBlockContainerSupport.transferTarget(1, false, 55, 9),
            37, 46
        );
        assertRange(
            OneBlockContainerSupport.transferTarget(37, false, 55, 9),
            0, 36
        );
        assertRange(
            OneBlockContainerSupport.transferTarget(46, false, 55, 9),
            0, 36
        );
    }

    @Test
    public void illegalIndexOrMissingInputProducesEmptyTransferTarget() {
        assertFalse(OneBlockContainerSupport.transferTarget(-1, false, 55, 9).isValid());
        assertFalse(OneBlockContainerSupport.transferTarget(55, false, 55, 9).isValid());
        assertFalse(OneBlockContainerSupport.transferTarget(0, false, 37, 0).isValid());
    }

    private static void assertRange(OneBlockContainerSupport.MergeRange range,
                                    int start,
                                    int end) {
        assertTrue(range.isValid());
        assertEquals(start, range.getStart());
        assertEquals(end, range.getEnd());
        assertFalse(range.isReverse());
    }

    private static void assertSlotGroup(SlotGroupDescriptor group,
                                        String id,
                                        int firstSlot,
                                        int slotCount,
                                        int x,
                                        int y,
                                        int rows,
                                        int columns) {
        assertEquals(id, group.id);
        assertEquals(firstSlot, group.firstSlot);
        assertEquals(slotCount, group.slotCount);
        assertEquals(x, group.x);
        assertEquals(y, group.y);
        assertEquals(rows, group.rows);
        assertEquals(columns, group.columns);
        assertEquals(18, group.spacingX);
        assertEquals(18, group.spacingY);
        assertEquals("playerInventory", group.shiftTarget);
        assertTrue(group.enabled);
    }
}
