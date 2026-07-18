package com.fushu.mmceoneblock.common.tile;

import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;

final class OneBlockStateSynchronizer {
    private final MachineComponentStorage.Host host;
    private final MachineComponentStorage storage;
    private long lastFingerprint = Long.MIN_VALUE;
    private int restoreDepth;
    private boolean resourceChangedDuringRestore;

    OneBlockStateSynchronizer(MachineComponentStorage.Host host,
                              MachineComponentStorage storage) {
        this.host = host;
        this.storage = storage;
    }

    synchronized void reset() {
        lastFingerprint = Long.MIN_VALUE;
    }

    synchronized void beginRestore() {
        restoreDepth++;
    }

    synchronized void endRestore() {
        if (restoreDepth <= 0) {
            throw new IllegalStateException("restore transaction is not active");
        }
        restoreDepth--;
        if (restoreDepth > 0) {
            return;
        }
        if (resourceChangedDuringRestore) {
            resourceChangedDuringRestore = false;
            host.markStorageDirty();
        }
        lastFingerprint = Long.MIN_VALUE;
        updateOneBlockCustomData(false);
    }

    synchronized void onResourceChanged() {
        if (restoreDepth > 0) {
            resourceChangedDuringRestore = true;
            return;
        }
        host.markStorageDirty();
        updateOneBlockCustomData(true);
    }

    synchronized void updateOneBlockCustomData(boolean notifyClient) {
        if (restoreDepth > 0) {
            return;
        }
        NBTTagCompound tag = host.getCustomDataTag();
        if (tag == null) {
            tag = new NBTTagCompound();
            host.setCustomDataTag(tag);
        }
        for (String key : new ArrayList<String>(tag.getKeySet())) {
            if (key.startsWith("oneblock.component.")) {
                tag.removeTag(key);
            }
        }

        Aggregate fluid = new Aggregate();
        Aggregate gas = new Aggregate();
        Aggregate energy = new Aggregate();
        List<OneBlockResourceState.ComponentSnapshot> snapshots = storage.snapshots();
        for (OneBlockResourceState.ComponentSnapshot snapshot : snapshots) {
            String prefix = "oneblock.component." + snapshot.id + ".";
            tag.setLong(prefix + "amount", snapshot.amount);
            tag.setLong(prefix + "capacity", snapshot.capacity);
            tag.setFloat(prefix + "ratio", snapshot.ratio());
            if ("item".equals(snapshot.kind)) {
                tag.setInteger(prefix + "slots", snapshot.slots);
                tag.setInteger(prefix + "occupiedSlots", snapshot.occupiedSlots);
                tag.setLong(prefix + "itemCount", snapshot.itemCount);
            } else if ("fluid".equals(snapshot.kind) || "gas".equals(snapshot.kind)) {
                tag.setString(prefix + "name", snapshot.name);
                tag.setString(prefix + "localizedName", snapshot.localizedName);
            }

            if ("fluid".equals(snapshot.kind)) {
                fluid.add(snapshot);
            } else if ("gas".equals(snapshot.kind)) {
                gas.add(snapshot);
            } else if ("energy".equals(snapshot.kind)) {
                energy.add(snapshot);
            }
        }

        publishAggregate(tag, "fluid", fluid, true);
        publishAggregate(tag, "gas", gas, true);
        publishAggregate(tag, "energy", energy, false);

        boolean formed = host.isStructureFormed();
        boolean working = host.isWorking();
        int activeThreads = Math.max(0, host.getActiveRuntimeThreadCount());
        int maxThreads = Math.max(0, host.getRuntimeThreadCapacity());
        tag.setBoolean("oneblock.state.formed", formed);
        tag.setBoolean("oneblock.state.working", working);
        tag.setInteger("oneblock.threads.active", activeThreads);
        tag.setInteger("oneblock.threads.max", maxThreads);

        long fingerprint = runtimeFingerprint(snapshots);
        fingerprint = 31L * fingerprint + (formed ? 1L : 0L);
        fingerprint = 31L * fingerprint + (working ? 1L : 0L);
        fingerprint = 31L * fingerprint + activeThreads;
        fingerprint = 31L * fingerprint + maxThreads;
        boolean changed = fingerprint != lastFingerprint;
        lastFingerprint = fingerprint;
        if (notifyClient && changed) {
            host.markStorageForUpdate();
        }
    }

    private static long runtimeFingerprint(
        List<OneBlockResourceState.ComponentSnapshot> snapshots
    ) {
        long result = 17L;
        for (OneBlockResourceState.ComponentSnapshot snapshot : snapshots) {
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

    private static void publishAggregate(NBTTagCompound tag,
                                         String kind,
                                         Aggregate aggregate,
                                         boolean publishNames) {
        String prefix = "oneblock." + kind + ".";
        tag.setLong(prefix + "amount", aggregate.amount);
        tag.setLong(prefix + "capacity", aggregate.capacity);
        tag.setFloat(prefix + "ratio", aggregate.ratio());
        if (publishNames) {
            tag.setString(prefix + "name", aggregate.name);
            tag.setString(prefix + "localizedName", aggregate.localizedName);
        }
    }

    private static final class Aggregate {
        private long amount;
        private long capacity;
        private String name = "";
        private String localizedName = "";

        private void add(OneBlockResourceState.ComponentSnapshot snapshot) {
            amount += Math.max(0L, snapshot.amount);
            capacity += Math.max(0L, snapshot.capacity);
            if (name.isEmpty() && !snapshot.name.isEmpty()) {
                name = snapshot.name;
                localizedName = snapshot.localizedName;
            }
        }

        private float ratio() {
            return capacity <= 0L
                ? 0.0F
                : Math.max(0.0F, Math.min(1.0F, amount / (float) capacity));
        }
    }
}
