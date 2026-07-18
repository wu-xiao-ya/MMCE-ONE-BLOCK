package com.fushu.mmceoneblock.common.container;

import com.fushu.mmceguiext.api.gui.PlayerInventoryDescriptor;
import com.fushu.mmceguiext.api.gui.SlotGroupDescriptor;
import com.fushu.mmceguiext.api.gui.SlotLayoutProvider;
import com.fushu.mmceoneblock.common.tile.TileSingleBlockFactoryController;
import hellfirepvp.modularmachinery.common.container.ContainerFactoryController;
import hellfirepvp.modularmachinery.common.item.ItemBlueprint;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.List;

public class ContainerSingleBlockFactoryController extends ContainerFactoryController implements SlotLayoutProvider {
    private final TileSingleBlockFactoryController owner;
    private final int inputInternalSlotCount;

    public ContainerSingleBlockFactoryController(TileSingleBlockFactoryController owner, EntityPlayer opening) {
        super(owner, opening);
        this.owner = owner;
        this.inputInternalSlotCount = owner.getItemInputSlotCount();
        for (Slot slot : OneBlockContainerSupport.createMachineSlots(
            owner.getInventory(),
            OneBlockContainerSupport.Layout.FACTORY,
            this.inputInternalSlotCount
        )) {
            addSlotToContainer(slot);
        }
    }

    public static int blueprintSlotIndex() {
        return OneBlockContainerSupport.blueprintSlotIndex();
    }

    public static int firstInternalSlotIndex() {
        return OneBlockContainerSupport.firstMachineSlotIndex();
    }

    @Override
    @Nonnull
    public ItemStack transferStackInSlot(@Nonnull EntityPlayer playerIn, int index) {
        if (index < 0 || index >= this.inventorySlots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = slot.getStack();
        ItemStack result = stackInSlot.copy();
        OneBlockContainerSupport.MergeRange target = OneBlockContainerSupport.transferTarget(
            index,
            stackInSlot.getItem() instanceof ItemBlueprint,
            this.inventorySlots.size(),
            this.inputInternalSlotCount
        );
        if (!target.isValid() || !this.mergeItemStack(stackInSlot, target.getStart(), target.getEnd(), target.isReverse())) {
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

    @Override
    public List<SlotGroupDescriptor> getSlotGroups() {
        return OneBlockContainerSupport.Layout.FACTORY.slotGroups(
            this.inputInternalSlotCount,
            this.owner.getInventory().getSlots()
        );
    }

    @Override
    public PlayerInventoryDescriptor getPlayerInventory() {
        return OneBlockContainerSupport.Layout.FACTORY.playerInventory();
    }
}
