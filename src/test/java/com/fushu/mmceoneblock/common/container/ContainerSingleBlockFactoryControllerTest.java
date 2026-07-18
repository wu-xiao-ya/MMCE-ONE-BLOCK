package com.fushu.mmceoneblock.common.container;

import com.fushu.mmceguiext.api.gui.PlayerInventoryDescriptor;
import com.fushu.mmceguiext.api.gui.SlotGroupDescriptor;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ContainerSingleBlockFactoryControllerTest {
    @Test
    public void factoryLayoutMatchesPublishedSlotDescriptors() {
        List<SlotGroupDescriptor> groups =
            OneBlockContainerSupport.Layout.FACTORY.slotGroups(9, 19);

        assertEquals(3, groups.size());
        assertSlotGroup(groups.get(0), "input", 37, 9, 112, 17, 2, 5);
        assertSlotGroup(groups.get(1), "output", 46, 9, 112, 66, 2, 5);
        assertSlotGroup(groups.get(2), "blueprint", 36, 1, 255, 8, 1, 1);

        PlayerInventoryDescriptor playerInventory =
            OneBlockContainerSupport.Layout.FACTORY.playerInventory();
        assertEquals(112, playerInventory.x);
        assertEquals(131, playerInventory.y);
        assertEquals(112, playerInventory.hotbarX);
        assertEquals(189, playerInventory.hotbarY);
        assertEquals(0, playerInventory.mainStart);
        assertEquals(27, playerInventory.hotbarStart);
        assertTrue(playerInventory.enabled);

        assertEquals(112, OneBlockContainerSupport.internalSlotX(
            OneBlockContainerSupport.Layout.FACTORY, 0, 9));
        assertEquals(184, OneBlockContainerSupport.internalSlotX(
            OneBlockContainerSupport.Layout.FACTORY, 4, 9));
        assertEquals(112, OneBlockContainerSupport.internalSlotX(
            OneBlockContainerSupport.Layout.FACTORY, 9, 9));
        assertEquals(84, OneBlockContainerSupport.internalSlotY(
            OneBlockContainerSupport.Layout.FACTORY, 17, 9));
    }

    @Test
    public void factoryShiftClickUsesTheSameSharedRanges() {
        assertRange(
            OneBlockContainerSupport.transferTarget(0, true, 55, 9),
            36, 37
        );
        assertRange(
            OneBlockContainerSupport.transferTarget(1, false, 55, 9),
            37, 46
        );
        assertRange(
            OneBlockContainerSupport.transferTarget(54, false, 55, 9),
            0, 36
        );
    }

    @Test
    public void outputOnlyLayoutDoesNotOfferPlayerInsertionTarget() {
        assertFalse(OneBlockContainerSupport.transferTarget(0, false, 46, 0).isValid());
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
