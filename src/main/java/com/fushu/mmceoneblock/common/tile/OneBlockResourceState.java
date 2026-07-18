package com.fushu.mmceoneblock.common.tile;

import com.fushu.mmceoneblock.common.config.MachineComponentDefinition;
import com.fushu.mmceoneblock.common.config.MachineDefinition;
import github.kasuminova.mmce.common.util.MultiGasTank;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.tiles.base.TileEntitySynchronized;
import hellfirepvp.modularmachinery.common.util.IEnergyHandlerAsync;
import hellfirepvp.modularmachinery.common.util.IOInventory;
import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

final class OneBlockResourceState {
    private static final AtomicLong GROUP_IDS = new AtomicLong(1L);

    private final MachineComponentStorage.Host host;
    private final Map<String, Long> componentGroupIds = new HashMap<String, Long>();
    private final LinkedHashMap<String, ComponentRuntime> runtimes =
        new LinkedHashMap<String, ComponentRuntime>();
    private NBTTagCompound unclaimedComponentData = new NBTTagCompound();
    @Nullable
    private Runnable changeListener;

    OneBlockResourceState(MachineComponentStorage.Host host) {
        this.host = host;
    }

    void setChangeListener(@Nullable Runnable listener) {
        this.changeListener = listener;
    }

    void rebuild(MachineDefinition definition, boolean positionalFallback) {
        NBTTagCompound carried = mergeCompounds(unclaimedComponentData, writeCurrentComponentData());
        IOInventory previousInventory = host.getInventory();
        ItemStack previousBlueprint = previousInventory == null
            ? ItemStack.EMPTY
            : previousInventory.getStackInSlot(MachineComponentStorage.BLUEPRINT).copy();

        runtimes.clear();
        InventoryPlan inventoryPlan = buildInventoryPlan(definition);
        rebuildInventory(inventoryPlan, previousInventory, previousBlueprint, positionalFallback);
        buildRuntimes(definition, inventoryPlan);
        unclaimedComponentData = new NBTTagCompound();
        loadComponentData(carried, true);
    }

    void readPayload(NBTTagCompound compound) {
        unclaimedComponentData = compound.hasKey(MachineComponentStorage.UNCLAIMED_COMPONENTS_NBT_KEY)
            ? (NBTTagCompound) compound.getCompoundTag(MachineComponentStorage.UNCLAIMED_COMPONENTS_NBT_KEY).copy()
            : new NBTTagCompound();
        loadComponentData(unclaimedComponentData, false);
        NBTTagCompound current = compound.hasKey(MachineComponentStorage.COMPONENTS_NBT_KEY)
            ? compound.getCompoundTag(MachineComponentStorage.COMPONENTS_NBT_KEY)
            : new NBTTagCompound();
        loadComponentData(current, true);
        migrateLegacyPayload(compound, current);
    }

    void writePayload(NBTTagCompound compound) {
        compound.setTag(MachineComponentStorage.COMPONENTS_NBT_KEY, writeCurrentComponentData());
        if (unclaimedComponentData.isEmpty()) {
            compound.removeTag(MachineComponentStorage.UNCLAIMED_COMPONENTS_NBT_KEY);
        } else {
            compound.setTag(
                MachineComponentStorage.UNCLAIMED_COMPONENTS_NBT_KEY,
                unclaimedComponentData.copy()
            );
        }
        removeLegacyPayload(compound);
    }

    boolean isEmpty() {
        return runtimes.isEmpty();
    }

    List<ComponentRuntime> runtimes() {
        return new ArrayList<ComponentRuntime>(runtimes.values());
    }

    int getItemInputSlotCount() {
        return collectItemSlots(IOType.INPUT).length;
    }

    boolean isItemInputInventorySlot(int slot) {
        return containsSlot(collectItemSlots(IOType.INPUT), slot);
    }

    int[] getItemInputSlots() {
        return collectItemSlots(IOType.INPUT);
    }

    int[] getItemOutputSlots() {
        return collectItemSlots(IOType.OUTPUT);
    }

    List<ItemRuntime> itemRuntimes() {
        return runtimesOfType(ItemRuntime.class);
    }

    List<FluidRuntime> fluidRuntimes() {
        return runtimesOfType(FluidRuntime.class);
    }

    List<GasRuntime> gasRuntimes() {
        return runtimesOfType(GasRuntime.class);
    }

    List<EnergyRuntime> energyRuntimes() {
        return runtimesOfType(EnergyRuntime.class);
    }

    List<ComponentSnapshot> snapshots() {
        List<ComponentSnapshot> out = new ArrayList<ComponentSnapshot>(runtimes.size());
        for (ComponentRuntime runtime : runtimes.values()) {
            out.add(runtime.snapshot());
        }
        return out;
    }

    long runtimeFingerprint() {
        long result = 17L;
        for (ComponentSnapshot snapshot : snapshots()) {
            result = 31L * result + snapshot.id.hashCode();
            result = 31L * result + snapshot.kind.hashCode();
            result = 31L * result + snapshot.amount;
            result = 31L * result + snapshot.capacity;
            result = 31L * result + snapshot.itemCount;
            result = 31L * result + snapshot.occupiedSlots;
            result = 31L * result + snapshot.name.hashCode();
        }
        return result;
    }

    private <T extends ComponentRuntime> List<T> runtimesOfType(Class<T> type) {
        List<T> out = new ArrayList<T>();
        for (ComponentRuntime runtime : runtimes.values()) {
            if (type.isInstance(runtime)) {
                out.add(type.cast(runtime));
            }
        }
        return out;
    }

    private void notifyContentsChanged() {
        if (changeListener != null) {
            changeListener.run();
        } else {
            host.markStorageDirty();
        }
    }

    private InventoryPlan buildInventoryPlan(MachineDefinition definition) {
        LinkedHashMap<String, int[]> slotsById = new LinkedHashMap<String, int[]>();
        int nextSlot = 1;
        for (MachineComponentDefinition component : definition.getComponents()) {
            if (!isEnabledKind(component, "item")) {
                continue;
            }
            int count = component.getSlots() == null
                ? Math.max(1, getInt(component.getRaw(), "slots", 1))
                : Math.max(1, component.getSlots().intValue());
            int[] slots = new int[count];
            for (int i = 0; i < count; i++) {
                slots[i] = nextSlot++;
            }
            slotsById.put(component.getId(), slots);
        }
        return new InventoryPlan(slotsById);
    }

    private void rebuildInventory(InventoryPlan plan,
                                  @Nullable IOInventory previous,
                                  ItemStack blueprint,
                                  boolean positionalFallback) {
        int[] inputs = collectPlannedSlots(plan, IOType.INPUT);
        int[] outputs = collectPlannedSlots(plan, IOType.OUTPUT);
        IOInventory next = new IOInventory((TileEntitySynchronized) host, inputs, outputs);
        next.setMiscSlots(MachineComponentStorage.BLUEPRINT);
        next.setStackLimit(1, MachineComponentStorage.BLUEPRINT);
        if (blueprint != null && !blueprint.isEmpty()) {
            next.setStackInSlot(MachineComponentStorage.BLUEPRINT, blueprint);
        }
        if (positionalFallback && previous != null) {
            int copySlots = Math.min(previous.getSlots(), next.getSlots());
            for (int i = 1; i < copySlots; i++) {
                ItemStack stack = previous.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    next.setStackInSlot(i, stack.copy());
                }
            }
        }
        next.setListener(slot -> host.markNoUpdateSync());
        host.setInventory(next);
    }

    private int[] collectPlannedSlots(InventoryPlan plan, IOType wanted) {
        List<Integer> out = new ArrayList<Integer>();
        MachineDefinition definition = host.getDefinition();
        if (definition == null) {
            return new int[0];
        }
        for (MachineComponentDefinition component : definition.getComponents()) {
            if (!isEnabledKind(component, "item") || componentIo(component) != wanted) {
                continue;
            }
            int[] slots = plan.slotsById.get(component.getId());
            if (slots != null) {
                for (int slot : slots) {
                    out.add(Integer.valueOf(slot));
                }
            }
        }
        return toIntArray(out);
    }

    private void buildRuntimes(MachineDefinition definition, InventoryPlan plan) {
        for (MachineComponentDefinition component : definition.getComponents()) {
            if (component == null || !component.isEnabled()) {
                continue;
            }
            String id = component.getId();
            String kind = componentKind(component);
            long groupId = groupIdFor(id);
            if ("item".equals(kind)) {
                runtimes.put(id, new ItemRuntime(component, groupId, plan.slotsById.get(id)));
            } else if ("fluid".equals(kind)) {
                runtimes.put(id, new FluidRuntime(component, groupId));
            } else if ("gas".equals(kind)) {
                runtimes.put(id, new GasRuntime(component, groupId));
            } else if ("energy".equals(kind)) {
                runtimes.put(id, new EnergyRuntime(component, groupId));
            }
        }
    }

    private long groupIdFor(String id) {
        Long existing = componentGroupIds.get(id);
        if (existing != null) {
            return existing.longValue();
        }
        long allocated = GROUP_IDS.getAndIncrement();
        componentGroupIds.put(id, Long.valueOf(allocated));
        return allocated;
    }

    private NBTTagCompound writeCurrentComponentData() {
        NBTTagCompound root = new NBTTagCompound();
        for (ComponentRuntime runtime : runtimes.values()) {
            root.setTag(runtime.id, runtime.writeToTag());
        }
        return root;
    }

    private void loadComponentData(NBTTagCompound root, boolean preserveUnclaimed) {
        if (root == null || root.isEmpty()) {
            return;
        }
        for (String id : new ArrayList<String>(root.getKeySet())) {
            NBTTagCompound tag = root.getCompoundTag(id);
            ComponentRuntime runtime = runtimes.get(id);
            String storedType = normalize(tag.getString("type"));
            if (runtime == null || !runtime.kind.equals(storedType)) {
                if (preserveUnclaimed) {
                    unclaimedComponentData.setTag(id, tag.copy());
                }
                continue;
            }
            NBTTagCompound leftovers = runtime.readFromTag(tag);
            unclaimedComponentData.removeTag(id);
            if (leftovers != null && !leftovers.isEmpty()) {
                unclaimedComponentData.setTag(id, leftovers);
            }
        }
    }

    private void migrateLegacyPayload(NBTTagCompound compound, NBTTagCompound current) {
        FluidRuntime fluid = firstFluidRuntime();
        if (compound.hasKey("oneBlockFluid") && fluid != null && !current.hasKey(fluid.id)) {
            fluid.tank.readFromNBT(compound.getCompoundTag("oneBlockFluid"));
            fluid.clampContents();
        }
        GasRuntime gas = firstGasRuntime();
        if (compound.hasKey("oneBlockGas") && gas != null && !current.hasKey(gas.id)) {
            gas.tank.readFromNBT(compound, "oneBlockGas");
            gas.clampContents();
        }
        EnergyRuntime energy = firstEnergyRuntime();
        if (compound.hasKey("oneBlockEnergy") && energy != null && !current.hasKey(energy.id)) {
            energy.handler.loadCurrentEnergy(compound.getLong("oneBlockEnergy"));
        }
    }

    private static void removeLegacyPayload(NBTTagCompound compound) {
        compound.removeTag("oneBlockFluid");
        compound.removeTag("oneBlockGas");
        compound.removeTag("oneBlockEnergy");
    }

    @Nullable
    private FluidRuntime firstFluidRuntime() {
        List<FluidRuntime> values = fluidRuntimes();
        return values.isEmpty() ? null : values.get(0);
    }

    @Nullable
    private GasRuntime firstGasRuntime() {
        List<GasRuntime> values = gasRuntimes();
        return values.isEmpty() ? null : values.get(0);
    }

    @Nullable
    private EnergyRuntime firstEnergyRuntime() {
        List<EnergyRuntime> values = energyRuntimes();
        return values.isEmpty() ? null : values.get(0);
    }

    private int[] collectItemSlots(IOType wanted) {
        List<Integer> out = new ArrayList<Integer>();
        for (ItemRuntime runtime : itemRuntimes()) {
            if (runtime.ioType == wanted) {
                for (int slot : runtime.slots) {
                    out.add(Integer.valueOf(slot));
                }
            }
        }
        return toIntArray(out);
    }

    static boolean isEnabledKind(@Nullable MachineComponentDefinition component, String kind) {
        return component != null && component.isEnabled() && kind.equals(componentKind(component));
    }

    static String componentKind(MachineComponentDefinition component) {
        String configured = normalize(component.getKind());
        if (!configured.isEmpty()) {
            return configured;
        }
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

    static IOType componentIo(MachineComponentDefinition component) {
        String configured = normalize(component.getIo());
        if (!configured.isEmpty()) {
            return "output".equals(configured) || "out".equals(configured)
                ? IOType.OUTPUT
                : IOType.INPUT;
        }
        return normalize(component.getType()).endsWith("_output") ? IOType.OUTPUT : IOType.INPUT;
    }

    static int getInt(@Nullable com.google.gson.JsonObject object, String key, int fallback) {
        long value = getLong(object, key, fallback);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    static long getLong(@Nullable com.google.gson.JsonObject object, String key, long fallback) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        com.google.gson.JsonElement element = object.get(key);
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
            ? element.getAsLong()
            : fallback;
    }

    static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizePath(@Nullable String value) {
        return normalize(value).replace('\\', '/');
    }

    private static int capacity(MachineComponentDefinition component, int fallback) {
        long configured = component.getCapacity() == null
            ? getLong(component.getRaw(), "capacity", fallback)
            : component.getCapacity().longValue();
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, configured));
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i).intValue();
        }
        return out;
    }

    private static boolean containsSlot(int[] slots, int slot) {
        for (int candidate : slots) {
            if (candidate == slot) {
                return true;
            }
        }
        return false;
    }

    private static NBTTagCompound mergeCompounds(NBTTagCompound first, NBTTagCompound second) {
        NBTTagCompound merged = first == null
            ? new NBTTagCompound()
            : (NBTTagCompound) first.copy();
        if (second != null) {
            for (String key : second.getKeySet()) {
                merged.setTag(key, second.getTag(key).copy());
            }
        }
        return merged;
    }

    abstract class ComponentRuntime {
        final String id;
        final String kind;
        final IOType ioType;
        final long groupId;

        ComponentRuntime(MachineComponentDefinition definition, long groupId) {
            this.id = definition.getId();
            this.kind = componentKind(definition);
            this.ioType = componentIo(definition);
            this.groupId = groupId;
        }

        abstract NBTTagCompound writeToTag();
        @Nullable
        abstract NBTTagCompound readFromTag(NBTTagCompound tag);
        abstract ComponentSnapshot snapshot();

        NBTTagCompound baseTag() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("type", kind);
            tag.setString("io", ioType == IOType.OUTPUT ? "output" : "input");
            return tag;
        }
    }

    final class ItemRuntime extends ComponentRuntime {
        final int[] slots;

        ItemRuntime(MachineComponentDefinition definition, long groupId, @Nullable int[] slots) {
            super(definition, groupId);
            this.slots = slots == null ? new int[0] : slots.clone();
        }

        @Override
        NBTTagCompound writeToTag() {
            NBTTagCompound tag = baseTag();
            NBTTagList items = new NBTTagList();
            IOInventory inventory = host.getInventory();
            for (int i = 0; i < slots.length; i++) {
                ItemStack stack = inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(slots[i]);
                if (!stack.isEmpty()) {
                    NBTTagCompound item = new NBTTagCompound();
                    item.setInteger("slot", i);
                    stack.writeToNBT(item);
                    items.appendTag(item);
                }
            }
            tag.setTag("items", items);
            return tag;
        }

        @Nullable
        @Override
        NBTTagCompound readFromTag(NBTTagCompound tag) {
            IOInventory inventory = host.getInventory();
            if (inventory == null) {
                return tag;
            }
            for (int slot : slots) {
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
            NBTTagList items = tag.getTagList("items", 10);
            NBTTagList leftovers = new NBTTagList();
            for (int i = 0; i < items.tagCount(); i++) {
                NBTTagCompound itemTag = items.getCompoundTagAt(i);
                int localSlot = itemTag.getInteger("slot");
                if (localSlot < 0 || localSlot >= slots.length) {
                    leftovers.appendTag(itemTag.copy());
                } else {
                    inventory.setStackInSlot(slots[localSlot], new ItemStack(itemTag));
                }
            }
            if (leftovers.tagCount() <= 0) {
                return null;
            }
            NBTTagCompound out = baseTag();
            out.setTag("items", leftovers);
            return out;
        }

        @Override
        ComponentSnapshot snapshot() {
            int occupied = 0;
            long itemCount = 0L;
            IOInventory inventory = host.getInventory();
            for (int slot : slots) {
                ItemStack stack = inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    occupied++;
                    itemCount += stack.getCount();
                }
            }
            return new ComponentSnapshot(
                id, kind, ioType, itemCount, slots.length, "", "",
                slots.length, occupied, itemCount
            );
        }
    }

    final class FluidRuntime extends ComponentRuntime {
        final SyncingFluidTank tank;

        FluidRuntime(MachineComponentDefinition definition, long groupId) {
            super(definition, groupId);
            tank = new SyncingFluidTank(
                capacity(definition, 1000),
                OneBlockResourceState.this::notifyContentsChanged
            );
        }

        void clampContents() {
            FluidStack fluid = tank.getFluid();
            if (fluid != null && fluid.amount > tank.getCapacity()) {
                FluidStack clamped = fluid.copy();
                clamped.amount = tank.getCapacity();
                tank.setFluid(clamped);
            }
        }

        @Override
        NBTTagCompound writeToTag() {
            return tank.writeToNBT(baseTag());
        }

        @Nullable
        @Override
        NBTTagCompound readFromTag(NBTTagCompound tag) {
            tank.readFromNBT(tag);
            clampContents();
            return null;
        }

        @Override
        ComponentSnapshot snapshot() {
            FluidStack fluid = tank.getFluid();
            int amount = fluid == null ? 0 : Math.max(0, fluid.amount);
            String name = fluid == null || fluid.getFluid() == null ? "" : fluid.getFluid().getName();
            String localized = fluid == null || fluid.getFluid() == null ? "" : fluid.getLocalizedName();
            return new ComponentSnapshot(
                id, kind, ioType, amount, tank.getCapacity(), name, localized,
                0, 0, 0L
            );
        }
    }

    final class GasRuntime extends ComponentRuntime {
        final MultiGasTank tank;

        GasRuntime(MachineComponentDefinition definition, long groupId) {
            super(definition, groupId);
            tank = new MultiGasTank(capacity(definition, 1000), 1);
            tank.setOnSlotChanged(slot -> notifyContentsChanged());
        }

        void clampContents() {
            GasStack gas = tank.getGasInSlot(0);
            if (gas != null && gas.amount > tank.getCapacity()) {
                GasStack clamped = gas.copy();
                clamped.amount = tank.getCapacity();
                tank.setGasInSlot(0, clamped);
            }
        }

        @Override
        NBTTagCompound writeToTag() {
            NBTTagCompound tag = baseTag();
            tank.writeToNBT(tag, "gas");
            return tag;
        }

        @Nullable
        @Override
        NBTTagCompound readFromTag(NBTTagCompound tag) {
            tank.readFromNBT(tag, "gas");
            clampContents();
            return null;
        }

        @Override
        ComponentSnapshot snapshot() {
            GasStack gas = tank.getGasInSlot(0);
            int amount = gas == null ? 0 : Math.max(0, gas.amount);
            String name = gas == null || gas.getGas() == null ? "" : gas.getGas().getName();
            String localized = gas == null || gas.getGas() == null ? "" : gas.getGas().getLocalizedName();
            return new ComponentSnapshot(
                id, kind, ioType, amount, tank.getCapacity(), name, localized,
                0, 0, 0L
            );
        }
    }

    final class EnergyRuntime extends ComponentRuntime {
        final InternalEnergyHandler handler;

        EnergyRuntime(MachineComponentDefinition definition, long groupId) {
            super(definition, groupId);
            long configured = definition.getCapacity() == null
                ? getLong(definition.getRaw(), "capacity", 1000L)
                : definition.getCapacity().longValue();
            handler = new InternalEnergyHandler(Math.max(1L, configured));
        }

        @Override
        NBTTagCompound writeToTag() {
            NBTTagCompound tag = baseTag();
            tag.setLong("energy", handler.getCurrentEnergy());
            return tag;
        }

        @Nullable
        @Override
        NBTTagCompound readFromTag(NBTTagCompound tag) {
            handler.loadCurrentEnergy(tag.getLong("energy"));
            return null;
        }

        @Override
        ComponentSnapshot snapshot() {
            return new ComponentSnapshot(
                id, kind, ioType, handler.getCurrentEnergy(), handler.getMaxEnergy(),
                "", "", 0, 0, 0L
            );
        }
    }

    static final class ComponentSnapshot {
        final String id;
        final String kind;
        final IOType ioType;
        final long amount;
        final long capacity;
        final String name;
        final String localizedName;
        final int slots;
        final int occupiedSlots;
        final long itemCount;

        ComponentSnapshot(String id,
                          String kind,
                          IOType ioType,
                          long amount,
                          long capacity,
                          String name,
                          String localizedName,
                          int slots,
                          int occupiedSlots,
                          long itemCount) {
            this.id = id;
            this.kind = kind;
            this.ioType = ioType;
            this.amount = amount;
            this.capacity = capacity;
            this.name = name == null ? "" : name;
            this.localizedName = localizedName == null ? "" : localizedName;
            this.slots = slots;
            this.occupiedSlots = occupiedSlots;
            this.itemCount = itemCount;
        }

        float ratio() {
            return capacity <= 0L
                ? 0.0F
                : Math.max(0.0F, Math.min(1.0F, amount / (float) capacity));
        }
    }

    final class InternalEnergyHandler implements IEnergyHandlerAsync, IEnergyStorage {
        private long energy;
        private final long capacity;

        InternalEnergyHandler(long capacity) {
            this.capacity = Math.max(1L, capacity);
        }

        synchronized void loadCurrentEnergy(long energy) {
            this.energy = Math.max(0L, Math.min(capacity, energy));
        }

        @Override
        public synchronized long getCurrentEnergy() {
            return energy;
        }

        @Override
        public synchronized void setCurrentEnergy(long energy) {
            long updated = Math.max(0L, Math.min(capacity, energy));
            if (this.energy != updated) {
                this.energy = updated;
                notifyContentsChanged();
            }
        }

        @Override
        public synchronized long getMaxEnergy() {
            return capacity;
        }

        @Override
        public synchronized boolean extractEnergy(long amount) {
            if (amount < 0L || energy < amount) {
                return false;
            }
            energy -= amount;
            if (amount > 0L) {
                notifyContentsChanged();
            }
            return true;
        }

        @Override
        public synchronized boolean receiveEnergy(long amount) {
            if (amount < 0L || capacity - energy < amount) {
                return false;
            }
            energy += amount;
            if (amount > 0L) {
                notifyContentsChanged();
            }
            return true;
        }

        @Override
        public synchronized int receiveEnergy(int maxReceive, boolean simulate) {
            int accepted = (int) Math.min(
                Math.max(0, maxReceive),
                Math.min(Integer.MAX_VALUE, capacity - energy)
            );
            if (!simulate && accepted > 0) {
                energy += accepted;
                notifyContentsChanged();
            }
            return accepted;
        }

        @Override
        public synchronized int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = (int) Math.min(
                Math.max(0, maxExtract),
                Math.min(Integer.MAX_VALUE, energy)
            );
            if (!simulate && extracted > 0) {
                energy -= extracted;
                notifyContentsChanged();
            }
            return extracted;
        }

        @Override
        public synchronized int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, energy);
        }

        @Override
        public synchronized int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, capacity);
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

    private static final class InventoryPlan {
        private final LinkedHashMap<String, int[]> slotsById;

        private InventoryPlan(LinkedHashMap<String, int[]> slotsById) {
            this.slotsById = slotsById;
        }
    }
}
