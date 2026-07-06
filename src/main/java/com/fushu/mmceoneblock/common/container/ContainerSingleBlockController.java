package com.fushu.mmceoneblock.common.container;

import hellfirepvp.modularmachinery.common.container.ContainerController;
import hellfirepvp.modularmachinery.common.item.ItemBlueprint;
import hellfirepvp.modularmachinery.common.tiles.TileMachineController;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nonnull;

public class ContainerSingleBlockController extends ContainerController {
    private static final int PLAYER_SLOT_COUNT = 36;
    private static final int INTERNAL_SLOT_START_X = 8;
    private static final int INTERNAL_SLOT_START_Y = 17;
    private static final int INTERNAL_SLOT_COLUMNS = 8;
    private static final int SLOT_SPACING = 18;

    public ContainerSingleBlockController(TileMachineController owner, EntityPlayer opening) {
        super(owner, opening);
        addInternalItemSlots(owner);
    }

    @Override
    @Nonnull
    public ItemStack transferStackInSlot(@Nonnull EntityPlayer playerIn, int index) {
        if (index < 0 || index >= this.inventorySlots.size()) {
            return ItemStack.EMPTY;
        }

        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);

        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = slot.getStack();
        result = stackInSlot.copy();

        MergeRange target = transferTarget(index, stackInSlot.getItem() instanceof ItemBlueprint, this.inventorySlots.size());
        if (!target.isValid() || !this.mergeItemStack(stackInSlot, target.start, target.end, target.reverse)) {
            return ItemStack.EMPTY;
        }

        if (stackInSlot.getCount() == 0) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }

        if (stackInSlot.getCount() == result.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(playerIn, stackInSlot);
        return result;
    }

    private void addInternalItemSlots(TileMachineController owner) {
        IItemHandler itemHandler = owner.getInventory().asGUIAccess();
        int totalSlots = itemHandler.getSlots();
        if (totalSlots <= 1) {
            return;
        }

        for (int slotIndex = 1; slotIndex < totalSlots; slotIndex++) {
            int offset = slotIndex - 1;
            int x = internalSlotX(offset);
            int y = internalSlotY(offset);
            addSlotToContainer(new SlotInternalItem(itemHandler, slotIndex, x, y));
        }
    }

    static int playerSlotCount() {
        return PLAYER_SLOT_COUNT;
    }

    static int controllerSlotStart() {
        return blueprintSlotIndex();
    }

    static int blueprintSlotIndex() {
        return PLAYER_SLOT_COUNT + TileMultiblockMachineController.BLUEPRINT_SLOT;
    }

    static int firstInternalSlotIndex() {
        return blueprintSlotIndex() + 1;
    }

    static boolean isBlueprintSlotIndex(int index) {
        return index == blueprintSlotIndex();
    }

    static boolean isInternalSlotIndex(int index, int slotCount) {
        return index >= firstInternalSlotIndex() && index < slotCount;
    }

    static boolean isInternalItemValid(@Nonnull ItemStack stack) {
        return isInternalItemValid(stack.getItem());
    }

    static boolean isInternalItemValid(@Nonnull Item item) {
        return !(item instanceof ItemBlueprint);
    }

    static int internalSlotX(int offset) {
        return INTERNAL_SLOT_START_X + (offset % INTERNAL_SLOT_COLUMNS) * SLOT_SPACING;
    }

    static int internalSlotY(int offset) {
        return INTERNAL_SLOT_START_Y + (offset / INTERNAL_SLOT_COLUMNS) * SLOT_SPACING;
    }

    static MergeRange transferTarget(int index, boolean blueprint, int slotCount) {
        if (index < 0 || index >= slotCount) {
            return MergeRange.none();
        }
        if (index < PLAYER_SLOT_COUNT) {
            if (blueprint) {
                return new MergeRange(blueprintSlotIndex(), firstInternalSlotIndex(), false);
            }
            return isInternalSlotIndex(firstInternalSlotIndex(), slotCount)
                ? new MergeRange(firstInternalSlotIndex(), slotCount, false)
                : MergeRange.none();
        }
        return new MergeRange(0, PLAYER_SLOT_COUNT, false);
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

        private static MergeRange none() {
            return new MergeRange(-1, -1, false);
        }

        private boolean isValid() {
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

    private static class SlotInternalItem extends SlotItemHandler {
        private SlotInternalItem(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            return isInternalItemValid(stack) && super.isItemValid(stack);
        }
    }
}
