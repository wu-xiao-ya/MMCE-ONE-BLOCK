package com.fushu.mmceoneblock.common.container;

import com.fushu.mmceguiext.api.gui.PlayerInventoryDescriptor;
import com.fushu.mmceguiext.api.gui.SlotGroupDescriptor;
import hellfirepvp.modularmachinery.common.item.ItemBlueprint;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;
import hellfirepvp.modularmachinery.common.util.IOInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

final class OneBlockContainerSupport {
    static final int PLAYER_SLOT_COUNT = 36;
    static final int INTERNAL_SLOT_COLUMNS = 5;
    static final int SLOT_SIZE = 18;
    static final String PLAYER_INVENTORY_ID = "playerInventory";
    static final String INPUT_GROUP_ID = "input";
    static final String OUTPUT_GROUP_ID = "output";
    static final String BLUEPRINT_GROUP_ID = "blueprint";

    private OneBlockContainerSupport() {
    }

    enum Layout {
        CONTROLLER(8, 17, 66, 151, 8, 8, 131, 8, 189),
        FACTORY(112, 17, 66, 255, 8, 112, 131, 112, 189);

        private final int internalSlotStartX;
        private final int inputSlotStartY;
        private final int outputSlotStartY;
        private final int blueprintSlotX;
        private final int blueprintSlotY;
        private final int playerInventoryX;
        private final int playerInventoryY;
        private final int playerHotbarX;
        private final int playerHotbarY;

        Layout(int internalSlotStartX,
               int inputSlotStartY,
               int outputSlotStartY,
               int blueprintSlotX,
               int blueprintSlotY,
               int playerInventoryX,
               int playerInventoryY,
               int playerHotbarX,
               int playerHotbarY) {
            this.internalSlotStartX = internalSlotStartX;
            this.inputSlotStartY = inputSlotStartY;
            this.outputSlotStartY = outputSlotStartY;
            this.blueprintSlotX = blueprintSlotX;
            this.blueprintSlotY = blueprintSlotY;
            this.playerInventoryX = playerInventoryX;
            this.playerInventoryY = playerInventoryY;
            this.playerHotbarX = playerHotbarX;
            this.playerHotbarY = playerHotbarY;
        }

        List<SlotGroupDescriptor> slotGroups(int inputSlotCount, int totalMachineSlotCount) {
            List<SlotGroupDescriptor> groups = new ArrayList<SlotGroupDescriptor>();
            int machineSlotCount = Math.max(0, totalMachineSlotCount - 1);
            int inputCount = Math.max(0, Math.min(inputSlotCount, machineSlotCount));
            int outputCount = machineSlotCount - inputCount;
            if (inputCount > 0) {
                groups.add(new SlotGroupDescriptor(
                    INPUT_GROUP_ID,
                    firstMachineSlotIndex(),
                    inputCount,
                    this.internalSlotStartX,
                    this.inputSlotStartY,
                    rowsFor(inputCount),
                    INTERNAL_SLOT_COLUMNS,
                    SLOT_SIZE,
                    SLOT_SIZE,
                    PLAYER_INVENTORY_ID,
                    true
                ));
            }
            if (outputCount > 0) {
                groups.add(new SlotGroupDescriptor(
                    OUTPUT_GROUP_ID,
                    firstMachineSlotIndex() + inputCount,
                    outputCount,
                    this.internalSlotStartX,
                    this.outputSlotStartY,
                    rowsFor(outputCount),
                    INTERNAL_SLOT_COLUMNS,
                    SLOT_SIZE,
                    SLOT_SIZE,
                    PLAYER_INVENTORY_ID,
                    true
                ));
            }
            groups.add(new SlotGroupDescriptor(
                BLUEPRINT_GROUP_ID,
                blueprintSlotIndex(),
                1,
                this.blueprintSlotX,
                this.blueprintSlotY,
                1,
                1,
                SLOT_SIZE,
                SLOT_SIZE,
                PLAYER_INVENTORY_ID,
                true
            ));
            return groups;
        }

        PlayerInventoryDescriptor playerInventory() {
            return new PlayerInventoryDescriptor(
                this.playerInventoryX, this.playerInventoryY,
                this.playerHotbarX, this.playerHotbarY,
                0, 27,
                true
            );
        }
    }

    static int blueprintSlotIndex() {
        return PLAYER_SLOT_COUNT + TileMultiblockMachineController.BLUEPRINT_SLOT;
    }

    static int firstMachineSlotIndex() {
        return blueprintSlotIndex() + 1;
    }

    static boolean isBlueprintSlotIndex(int index) {
        return index == blueprintSlotIndex();
    }

    static boolean isMachineSlotIndex(int index, int slotCount) {
        return index >= firstMachineSlotIndex() && index < slotCount;
    }

    static int internalSlotX(Layout layout, int offset, int inputSlotCount) {
        int localOffset = localInternalSlotOffset(offset, inputSlotCount);
        return layout.internalSlotStartX + (localOffset % INTERNAL_SLOT_COLUMNS) * SLOT_SIZE;
    }

    static int internalSlotY(Layout layout, int offset, int inputSlotCount) {
        if (offset >= Math.max(0, inputSlotCount)) {
            int localOffset = offset - Math.max(0, inputSlotCount);
            return layout.outputSlotStartY + (localOffset / INTERNAL_SLOT_COLUMNS) * SLOT_SIZE;
        }
        return layout.inputSlotStartY + (offset / INTERNAL_SLOT_COLUMNS) * SLOT_SIZE;
    }

    static MergeRange transferTarget(int index, boolean blueprint, int slotCount, int inputInternalSlotCount) {
        if (index < 0 || index >= slotCount) {
            return MergeRange.none();
        }
        if (index < PLAYER_SLOT_COUNT) {
            if (blueprint) {
                return new MergeRange(blueprintSlotIndex(), firstMachineSlotIndex(), false);
            }
            int inputEnd = Math.min(slotCount, firstMachineSlotIndex() + Math.max(0, inputInternalSlotCount));
            return isMachineSlotIndex(firstMachineSlotIndex(), inputEnd)
                ? new MergeRange(firstMachineSlotIndex(), inputEnd, false)
                : MergeRange.none();
        }
        return new MergeRange(0, PLAYER_SLOT_COUNT, false);
    }

    static List<Slot> createMachineSlots(IOInventory inventory, Layout layout, int inputSlotCount) {
        IItemHandler itemHandler = inventory.asGUIAccess();
        IItemHandler validationHandler = inventory;
        int totalSlots = itemHandler.getSlots();
        List<Slot> slots = new ArrayList<Slot>();
        if (totalSlots <= 1) {
            return slots;
        }
        for (int slotIndex = 1; slotIndex < totalSlots; slotIndex++) {
            int offset = slotIndex - 1;
            slots.add(new MachineSlot(
                itemHandler,
                validationHandler,
                slotIndex,
                internalSlotX(layout, offset, inputSlotCount),
                internalSlotY(layout, offset, inputSlotCount),
                slotIndex <= inputSlotCount
            ));
        }
        return slots;
    }

    static boolean isInternalItemValid(@Nonnull ItemStack stack) {
        return isInternalItemValid(stack.getItem());
    }

    static boolean isInternalItemValid(@Nonnull Item item) {
        return !(item instanceof ItemBlueprint);
    }

    private static int localInternalSlotOffset(int offset, int inputSlotCount) {
        return offset >= Math.max(0, inputSlotCount)
            ? offset - Math.max(0, inputSlotCount)
            : offset;
    }

    private static int rowsFor(int slotCount) {
        return (slotCount + INTERNAL_SLOT_COLUMNS - 1) / INTERNAL_SLOT_COLUMNS;
    }

    static final class MergeRange {
        private final int start;
        private final int end;
        private final boolean reverse;

        private MergeRange(int start, int end, boolean reverse) {
            this.start = start;
            this.end = end;
            this.reverse = reverse;
        }

        static MergeRange none() {
            return new MergeRange(-1, -1, false);
        }

        boolean isValid() {
            return this.start >= 0 && this.end > this.start;
        }

        int getStart() {
            return this.start;
        }

        int getEnd() {
            return this.end;
        }

        boolean isReverse() {
            return this.reverse;
        }
    }

    private static final class MachineSlot extends SlotItemHandler {
        private final IItemHandler validationHandler;
        private final int index;
        private final boolean acceptsInput;

        private MachineSlot(IItemHandler itemHandler,
                            IItemHandler validationHandler,
                            int index,
                            int xPosition,
                            int yPosition,
                            boolean acceptsInput) {
            super(itemHandler, index, xPosition, yPosition);
            this.validationHandler = validationHandler;
            this.index = index;
            this.acceptsInput = acceptsInput;
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            if (!this.acceptsInput || stack.isEmpty() || !isInternalItemValid(stack)) {
                return false;
            }
            ItemStack remaining = this.validationHandler.insertItem(this.index, stack.copy(), true);
            return remaining.getCount() < stack.getCount();
        }
    }
}
