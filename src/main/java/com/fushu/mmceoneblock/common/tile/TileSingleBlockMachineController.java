package com.fushu.mmceoneblock.common.tile;

import com.fushu.mmceguiext.api.gui.IMachineGuiStyleProvider;
import com.fushu.mmceguiext.api.machine.IMultiMachineComponentProvider;
import com.fushu.mmceoneblock.MMCEOneBlock;
import com.fushu.mmceoneblock.common.block.BlockSingleBlockMachineController;
import com.fushu.mmceoneblock.common.config.MachineComponentDefinition;
import com.fushu.mmceoneblock.common.config.MachineDefinition;
import com.fushu.mmceoneblock.common.registry.MachineRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import github.kasuminova.mmce.common.tile.base.MachineCombinationComponent;
import github.kasuminova.mmce.common.util.IExtendedGasHandler;
import github.kasuminova.mmce.common.util.MultiGasTank;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.crafting.ComponentType;
import hellfirepvp.modularmachinery.common.lib.ComponentTypesMM;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.machine.TaggedPositionBlockArray;
import hellfirepvp.modularmachinery.common.tiles.TileMachineController;
import hellfirepvp.modularmachinery.common.tiles.base.MachineComponentTile;
import hellfirepvp.modularmachinery.common.util.BlockArray;
import hellfirepvp.modularmachinery.common.util.IEnergyHandlerAsync;
import hellfirepvp.modularmachinery.common.util.IBlockStateDescriptor;
import hellfirepvp.modularmachinery.common.util.IOInventory;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTankInfo;
import mekanism.api.gas.IGasHandler;
import mekanism.api.gas.ITubeConnection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Optional.InterfaceList({
    @Optional.Interface(modid = "mekanism", iface = "mekanism.api.gas.IGasHandler"),
    @Optional.Interface(modid = "mekanism", iface = "mekanism.api.gas.ITubeConnection")
})
public class TileSingleBlockMachineController extends TileMachineController
    implements MachineComponentTile, MachineCombinationComponent, IMultiMachineComponentProvider, IMachineGuiStyleProvider, IGasHandler, ITubeConnection {

    private static final AtomicLong GROUP_IDS = new AtomicLong(1L);
    private static final int BLUEPRINT = BLUEPRINT_SLOT;

    private final long componentGroupId = GROUP_IDS.getAndIncrement();
    private final FluidTank fluidTank = new FluidTank(1000);
    private final MultiGasTank gasTank = new MultiGasTank(1000, 1);
    private final InternalEnergyHandler energyHandler = new InternalEnergyHandler(1000L);

    private String definitionId = "";
    private String inventorySignature = "";
    private String componentSignature = "";
    private List<MachineComponent<?>> cachedComponents = Collections.emptyList();
    private ResourceLocation lastMissingMachine = null;

    public TileSingleBlockMachineController() {
        super();
        this.gasTank.setOnSlotChanged(slot -> markNoUpdateSync());
    }

    public TileSingleBlockMachineController(@Nullable IBlockState state, @Nullable String definitionId) {
        this();
        setDefinitionId(definitionId);
        if (state != null && state.getBlock() instanceof BlockController) {
            this.controllerRotation = state.getValue(BlockController.FACING);
        }
    }

    public void setDefinitionId(@Nullable String definitionId) {
        String normalized = normalizePath(definitionId);
        if (!normalized.equals(this.definitionId)) {
            this.definitionId = normalized;
            this.inventorySignature = "";
            this.componentSignature = "";
            this.cachedComponents = Collections.emptyList();
        }
    }

    public String getDefinitionId() {
        if (!this.definitionId.isEmpty()) {
            return this.definitionId;
        }
        MachineDefinition definition = resolveDefinitionFromBlock();
        return definition == null ? "" : definition.getId();
    }

    @Nullable
    public MachineDefinition getDefinition() {
        String id = getDefinitionId();
        return id.isEmpty() ? null : MachineRegistry.getDefinition(id);
    }

    @Nullable
    private MachineDefinition resolveDefinitionFromBlock() {
        if (this.world == null || this.pos == null || !this.world.isBlockLoaded(this.pos)) {
            return null;
        }
        if (!(this.world.getBlockState(this.pos).getBlock() instanceof BlockSingleBlockMachineController)) {
            return null;
        }
        BlockSingleBlockMachineController block =
            (BlockSingleBlockMachineController) this.world.getBlockState(this.pos).getBlock();
        MachineDefinition definition = block.getDefinition();
        if (definition != null && !definition.getId().equals(this.definitionId)) {
            this.definitionId = definition.getId();
        }
        return definition;
    }

    @Override
    protected void checkRotation() {
        if (getWorld() == null || getPos() == null || !getWorld().isBlockLoaded(getPos())) {
            return;
        }
        IBlockState state = getWorld().getBlockState(getPos());
        if (state.getBlock() instanceof BlockController) {
            this.controllerRotation = state.getValue(BlockController.FACING);
        }
        if (this.controllerRotation == null) {
            this.controllerRotation = EnumFacing.NORTH;
        }
    }

    @Override
    protected boolean checkStructure() {
        if (!canCheckStructure()) {
            return true;
        }
        this.lastStructureCheckTick = this.ticksExisted;
        checkRotation();

        MachineDefinition definition = getDefinition();
        if (definition == null) {
            resetMachine(true);
            return true;
        }

        DynamicMachine machine = hellfirepvp.modularmachinery.common.machine.MachineRegistry
            .getRegistry()
            .getMachine(definition.getMachine());
        if (machine == null) {
            if (!definition.getMachine().equals(this.lastMissingMachine)) {
                MMCEOneBlock.log.warn("One-block machine '{}' points to unknown MMCE machine '{}'.",
                    definition.getId(), definition.getMachine());
                this.lastMissingMachine = definition.getMachine();
            }
            resetMachine(true);
            return true;
        }
        this.lastMissingMachine = null;

        if (machine.isFactoryOnly()) {
            MMCEOneBlock.log.warn("One-block machine '{}' points to factory-only machine '{}'; v1 only supports ordinary controllers.",
                definition.getId(), definition.getMachine());
            resetMachine(true);
            return true;
        }

        if (machine.isRequiresBlueprint() && !machine.equals(getBlueprintMachine())) {
            resetMachine(true);
            return true;
        }

        if (!getWorld().isBlockLoaded(getPos())) {
            return false;
        }

        if (isStructureFormed()
            && machine.equals(this.foundMachine)
            && this.foundPattern != null
            && this.foundPattern.matches(getWorld(), getPos(), true, this.foundReplacements)) {
            return true;
        }

        this.prevMachine = this.foundMachine;
        this.foundMachine = machine;
        this.parentMachine = machine;
        this.foundPattern = createSyntheticPattern();
        this.foundReplacements = new DynamicMachine.ModifierReplacementMap();
        this.foundDynamicPatterns.clear();
        syncDefinition(definition);
        onStructureFormed();
        return true;
    }

    private TaggedPositionBlockArray createSyntheticPattern() {
        TaggedPositionBlockArray pattern = new TaggedPositionBlockArray();
        IBlockState state = getWorld().getBlockState(getPos());
        BlockArray.BlockInformation info = new BlockArray.BlockInformation(Collections.singletonList(
            IBlockStateDescriptor.of(state.getBlock())
        ));
        pattern.addBlock(BlockPos.ORIGIN, info);
        pattern.flushTileBlocksCache();
        return pattern;
    }

    @Nullable
    @Override
    public MachineComponent<?> provideComponent() {
        Collection<MachineComponent<?>> components = provideMachineComponents();
        return components.isEmpty() ? null : components.iterator().next();
    }

    @Nonnull
    @Override
    public Collection<MachineComponent<?>> provideComponents() {
        return provideMachineComponents();
    }

    @Nonnull
    @Override
    public Collection<MachineComponent<?>> provideMachineComponents() {
        MachineDefinition definition = getDefinition();
        if (definition == null) {
            return Collections.emptyList();
        }
        syncDefinition(definition);
        return this.cachedComponents;
    }

    @Override
    public long getUniqueGroupID() {
        return this.componentGroupId;
    }

    @Override
    public long getMachineComponentGroupId() {
        return this.componentGroupId;
    }

    @Nullable
    @Override
    public ResourceLocation getMachineControllerGuiStyle() {
        MachineDefinition definition = getDefinition();
        if (definition == null || definition.getGuiStyle() == null || definition.getGuiStyle().trim().isEmpty()) {
            return null;
        }
        try {
            return new ResourceLocation(definition.getGuiStyle().trim().toLowerCase(Locale.ROOT));
        } catch (Exception ex) {
            return null;
        }
    }

    private void syncDefinition(MachineDefinition definition) {
        String signature = buildInventorySignature(definition);
        if (!signature.equals(this.inventorySignature)) {
            rebuildInventory(definition, signature);
        }
        int fluidCapacity = clampInt(findCapacity(definition, "fluid", 1000L));
        int gasCapacity = clampInt(findCapacity(definition, "gas", 1000L));
        long energyCapacity = Math.max(1L, findCapacity(definition, "energy", 1000L));
        this.fluidTank.setCapacity(fluidCapacity);
        this.gasTank.setCapacity(gasCapacity);
        this.energyHandler.setCapacity(energyCapacity);
        String componentSignature = buildComponentSignature(definition, signature, fluidCapacity, gasCapacity, energyCapacity);
        if (!componentSignature.equals(this.componentSignature)) {
            this.cachedComponents = buildComponents(definition);
            this.componentSignature = componentSignature;
        }
    }

    private void rebuildInventory(MachineDefinition definition, String signature) {
        InventorySlots slots = collectItemSlots(definition);
        IOInventory next = new IOInventory(this, slots.inputSlots, slots.outputSlots);
        next.setMiscSlots(BLUEPRINT);
        next.setStackLimit(1, BLUEPRINT);
        int copySlots = Math.min(this.inventory.getSlots(), next.getSlots());
        for (int i = 0; i < copySlots; i++) {
            ItemStack stack = this.inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                next.setStackInSlot(i, stack);
            }
        }
        next.setListener(slot -> markNoUpdateSync());
        this.inventory = next;
        this.inventorySignature = signature;
    }

    private List<MachineComponent<?>> buildComponents(MachineDefinition definition) {
        List<MachineComponent<?>> components = new ArrayList<MachineComponent<?>>();
        long baseGroupId = getUniqueGroupID();
        int index = 0;
        for (MachineComponentDefinition component : definition.getComponents()) {
            if (component == null || !component.isEnabled()) {
                continue;
            }
            String kind = componentKind(component);
            IOType ioType = componentIo(component);
            long groupId = baseGroupId + index++;
            if ("item".equals(kind)) {
                components.add(new ItemMachineComponent(ioType, groupId));
            } else if ("fluid".equals(kind)) {
                components.add(new FluidMachineComponent(ioType, groupId));
            } else if ("gas".equals(kind)) {
                components.add(new GasMachineComponent(ioType, groupId));
            } else if ("energy".equals(kind)) {
                components.add(new EnergyMachineComponent(ioType, groupId));
            }
        }
        return components.isEmpty()
            ? Collections.<MachineComponent<?>>emptyList()
            : Collections.unmodifiableList(components);
    }

    private InventorySlots collectItemSlots(MachineDefinition definition) {
        List<Integer> inputs = new ArrayList<Integer>();
        List<Integer> outputs = new ArrayList<Integer>();
        int nextSlot = 1;
        for (MachineComponentDefinition component : definition.getComponents()) {
            if (component == null || !component.isEnabled() || !"item".equals(componentKind(component))) {
                continue;
            }
            int count = Math.max(1, getInt(component.getRaw(), "slots", 1));
            IOType ioType = componentIo(component);
            for (int i = 0; i < count; i++) {
                if (ioType == IOType.OUTPUT) {
                    outputs.add(Integer.valueOf(nextSlot));
                } else {
                    inputs.add(Integer.valueOf(nextSlot));
                }
                nextSlot++;
            }
        }
        return new InventorySlots(toIntArray(inputs), toIntArray(outputs));
    }

    private String buildInventorySignature(MachineDefinition definition) {
        InventorySlots slots = collectItemSlots(definition);
        return Arrays.toString(slots.inputSlots) + "|" + Arrays.toString(slots.outputSlots);
    }

    private String buildComponentSignature(MachineDefinition definition,
                                           String inventorySignature,
                                           int fluidCapacity,
                                           int gasCapacity,
                                           long energyCapacity) {
        StringBuilder builder = new StringBuilder();
        builder.append(definition.getId())
            .append('|').append(inventorySignature)
            .append('|').append(fluidCapacity)
            .append('|').append(gasCapacity)
            .append('|').append(energyCapacity);
        for (MachineComponentDefinition component : definition.getComponents()) {
            if (component == null || !component.isEnabled()) {
                continue;
            }
            builder.append('|')
                .append(component.getType())
                .append(':')
                .append(componentIo(component));
        }
        return builder.toString();
    }

    private long findCapacity(MachineDefinition definition, String kind, long fallback) {
        long value = fallback;
        for (MachineComponentDefinition component : definition.getComponents()) {
            if (component == null || !component.isEnabled() || !kind.equals(componentKind(component))) {
                continue;
            }
            value = Math.max(value, getLong(component.getRaw(), "capacity", fallback));
        }
        return value;
    }

    private static String componentKind(MachineComponentDefinition component) {
        String type = normalize(component.getType());
        if (type.endsWith("_input")) {
            type = type.substring(0, type.length() - "_input".length());
        } else if (type.endsWith("_output")) {
            type = type.substring(0, type.length() - "_output".length());
        }
        if ("power".equals(type) || "rf".equals(type) || "fe".equals(type)) {
            return "energy";
        }
        return type;
    }

    private static IOType componentIo(MachineComponentDefinition component) {
        String type = normalize(component.getType());
        if (type.endsWith("_output")) {
            return IOType.OUTPUT;
        }
        if (type.endsWith("_input")) {
            return IOType.INPUT;
        }
        String io = normalize(getString(component.getRaw(), "io", "input"));
        return "output".equals(io) || "out".equals(io) ? IOType.OUTPUT : IOType.INPUT;
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i).intValue();
        }
        return out;
    }

    private static int getInt(@Nullable JsonObject object, String key, int fallback) {
        long value = getLong(object, key, fallback);
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private static long getLong(@Nullable JsonObject object, String key, long fallback) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        JsonElement element = object.get(key);
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
            ? element.getAsLong()
            : fallback;
    }

    private static String getString(@Nullable JsonObject object, String key, String fallback) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        JsonElement element = object.get(key);
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
            ? element.getAsString()
            : fallback;
    }

    private static int clampInt(long value) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, value));
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePath(@Nullable String value) {
        return normalize(value).replace('\\', '/');
    }

    @Override
    public void readCustomNBT(NBTTagCompound compound) {
        super.readCustomNBT(compound);
        readOneBlockPayload(compound);
    }

    void readOneBlockPayload(NBTTagCompound compound) {
        setDefinitionId(compound.getString("definitionId"));
        MachineDefinition definition = getDefinition();
        if (definition != null) {
            syncDefinition(definition);
        }
        if (compound.hasKey("oneBlockFluid")) {
            this.fluidTank.readFromNBT(compound.getCompoundTag("oneBlockFluid"));
        }
        if (compound.hasKey("oneBlockGas")) {
            this.gasTank.readFromNBT(compound, "oneBlockGas");
        }
        this.energyHandler.loadCurrentEnergy(compound.getLong("oneBlockEnergy"));
    }

    @Override
    public void writeCustomNBT(NBTTagCompound compound) {
        super.writeCustomNBT(compound);
        writeOneBlockPayload(compound);
    }

    void writeOneBlockPayload(NBTTagCompound compound) {
        compound.setString("definitionId", getDefinitionId());
        compound.setTag("oneBlockFluid", this.fluidTank.writeToNBT(new NBTTagCompound()));
        this.gasTank.writeToNBT(compound, "oneBlockGas");
        compound.setLong("oneBlockEnergy", this.energyHandler.getCurrentEnergy());
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
            || capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
            || capability == CapabilityEnergy.ENERGY
            || super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) this.inventory;
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return (T) this.fluidTank;
        }
        if (capability == CapabilityEnergy.ENERGY) {
            return (T) this.energyHandler;
        }
        return super.getCapability(capability, facing);
    }

    @Override
    @Optional.Method(modid = "mekanism")
    public boolean canTubeConnect(EnumFacing side) {
        return true;
    }

    @Override
    @Optional.Method(modid = "mekanism")
    public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
        return this.gasTank.receiveGas(side, stack, doTransfer);
    }

    @Override
    @Optional.Method(modid = "mekanism")
    public GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
        return this.gasTank.drawGas(side, amount, doTransfer);
    }

    @Override
    @Optional.Method(modid = "mekanism")
    public boolean canReceiveGas(EnumFacing side, Gas type) {
        return type != null && this.gasTank.canReceiveGas(side, type);
    }

    @Override
    @Optional.Method(modid = "mekanism")
    public boolean canDrawGas(EnumFacing side, Gas type) {
        return type != null && this.gasTank.canDrawGas(side, type);
    }

    @Nonnull
    @Override
    @Optional.Method(modid = "mekanism")
    public GasTankInfo[] getTankInfo() {
        return this.gasTank.getTankInfo();
    }

    @Override
    public void invalidate() {
        clearTransientComponentCache();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        clearTransientComponentCache();
        super.onChunkUnload();
    }

    private void clearTransientComponentCache() {
        this.componentSignature = "";
        this.cachedComponents = Collections.emptyList();
    }

    private final class ItemMachineComponent extends MachineComponent.ItemBus {
        private final long groupId;

        private ItemMachineComponent(IOType ioType, long groupId) {
            super(ioType);
            this.groupId = groupId;
        }

        @Override
        public IItemHandlerModifiable getContainerProvider() {
            MachineDefinition definition = getDefinition();
            InventorySlots slots = definition == null ? new InventorySlots(new int[0], new int[0]) : collectItemSlots(definition);
            return new SlotFilteredItemHandler(inventory, this.ioType == IOType.OUTPUT ? slots.outputSlots : slots.inputSlots);
        }

        @Override
        public long getGroupID() {
            return this.groupId;
        }
    }

    private final class FluidMachineComponent extends MachineComponent.FluidHatch {
        private final long groupId;

        private FluidMachineComponent(IOType ioType, long groupId) {
            super(ioType);
            this.groupId = groupId;
        }

        @Override
        public FluidTank getContainerProvider() {
            return fluidTank;
        }

        @Override
        public long getGroupID() {
            return this.groupId;
        }
    }

    private final class GasMachineComponent extends MachineComponent<IExtendedGasHandler> {
        private final long groupId;

        private GasMachineComponent(IOType ioType, long groupId) {
            super(ioType);
            this.groupId = groupId;
        }

        @Override
        public ComponentType getComponentType() {
            return ComponentTypesMM.COMPONENT_GAS;
        }

        @Override
        public IExtendedGasHandler getContainerProvider() {
            return gasTank;
        }

        @Override
        public long getGroupID() {
            return this.groupId;
        }
    }

    private final class EnergyMachineComponent extends MachineComponent.EnergyHatch {
        private final long groupId;

        private EnergyMachineComponent(IOType ioType, long groupId) {
            super(ioType);
            this.groupId = groupId;
        }

        @Override
        public IEnergyHandlerAsync getContainerProvider() {
            return energyHandler;
        }

        @Override
        public long getGroupID() {
            return this.groupId;
        }
    }

    private static final class SlotFilteredItemHandler implements IItemHandlerModifiable {
        private final IItemHandlerModifiable backing;
        private final int[] slots;

        private SlotFilteredItemHandler(IItemHandlerModifiable backing, int[] slots) {
            this.backing = backing;
            this.slots = slots == null ? new int[0] : Arrays.copyOf(slots, slots.length);
        }

        @Override
        public void setStackInSlot(int slot, @Nonnull net.minecraft.item.ItemStack stack) {
            int actual = map(slot);
            if (actual >= 0) {
                this.backing.setStackInSlot(actual, stack);
            }
        }

        @Override
        public int getSlots() {
            return this.slots.length;
        }

        @Nonnull
        @Override
        public net.minecraft.item.ItemStack getStackInSlot(int slot) {
            int actual = map(slot);
            return actual < 0 ? net.minecraft.item.ItemStack.EMPTY : this.backing.getStackInSlot(actual);
        }

        @Nonnull
        @Override
        public net.minecraft.item.ItemStack insertItem(int slot, @Nonnull net.minecraft.item.ItemStack stack, boolean simulate) {
            int actual = map(slot);
            return actual < 0 ? stack : this.backing.insertItem(actual, stack, simulate);
        }

        @Nonnull
        @Override
        public net.minecraft.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            int actual = map(slot);
            return actual < 0 ? net.minecraft.item.ItemStack.EMPTY : this.backing.extractItem(actual, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            int actual = map(slot);
            return actual < 0 ? 0 : this.backing.getSlotLimit(actual);
        }

        private int map(int slot) {
            return slot < 0 || slot >= this.slots.length ? -1 : this.slots[slot];
        }
    }

    private static final class InventorySlots {
        private final int[] inputSlots;
        private final int[] outputSlots;

        private InventorySlots(int[] inputSlots, int[] outputSlots) {
            this.inputSlots = inputSlots;
            this.outputSlots = outputSlots;
        }
    }

    private final class InternalEnergyHandler implements IEnergyHandlerAsync, IEnergyStorage {
        private long energy;
        private long capacity;

        private InternalEnergyHandler(long capacity) {
            this.capacity = Math.max(1L, capacity);
        }

        private void setCapacity(long capacity) {
            this.capacity = Math.max(1L, capacity);
            if (this.energy > this.capacity) {
                this.energy = this.capacity;
            }
        }

        private synchronized void loadCurrentEnergy(long energy) {
            this.energy = Math.max(0L, Math.min(this.capacity, energy));
        }

        @Override
        public synchronized long getCurrentEnergy() {
            return this.energy;
        }

        @Override
        public synchronized void setCurrentEnergy(long energy) {
            this.energy = Math.max(0L, Math.min(this.capacity, energy));
            markNoUpdateSync();
        }

        @Override
        public synchronized long getMaxEnergy() {
            return this.capacity;
        }

        @Override
        public synchronized boolean extractEnergy(long energy) {
            if (energy < 0L || this.energy < energy) {
                return false;
            }
            this.energy -= energy;
            markNoUpdateSync();
            return true;
        }

        @Override
        public synchronized boolean receiveEnergy(long energy) {
            if (energy < 0L || this.capacity - this.energy < energy) {
                return false;
            }
            this.energy += energy;
            markNoUpdateSync();
            return true;
        }

        @Override
        public synchronized int receiveEnergy(int maxReceive, boolean simulate) {
            int accepted = (int) Math.min(Math.max(0, maxReceive), Math.min(Integer.MAX_VALUE, this.capacity - this.energy));
            if (!simulate && accepted > 0) {
                this.energy += accepted;
                markNoUpdateSync();
            }
            return accepted;
        }

        @Override
        public synchronized int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = (int) Math.min(Math.max(0, maxExtract), Math.min(Integer.MAX_VALUE, this.energy));
            if (!simulate && extracted > 0) {
                this.energy -= extracted;
                markNoUpdateSync();
            }
            return extracted;
        }

        @Override
        public synchronized int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, this.energy);
        }

        @Override
        public synchronized int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, this.capacity);
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
