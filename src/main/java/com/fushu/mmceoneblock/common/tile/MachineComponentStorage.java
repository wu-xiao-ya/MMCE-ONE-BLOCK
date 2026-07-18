package com.fushu.mmceoneblock.common.tile;

import com.fushu.mmceoneblock.common.config.MachineComponentDefinition;
import com.fushu.mmceoneblock.common.config.MachineDefinition;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.tiles.base.MachineComponentTile;
import hellfirepvp.modularmachinery.common.util.IOInventory;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;

/**
 * Coordinates configured resource state and MMCE component publication.
 */
public final class MachineComponentStorage {
    public static final String COMPONENTS_NBT_KEY = "oneBlockComponents";
    public static final String UNCLAIMED_COMPONENTS_NBT_KEY = "oneBlockUnclaimedComponents";
    public static final int BLUEPRINT = 0;

    public interface Host extends MachineComponentTile {
        MachineDefinition getDefinition();
        String getDefinitionId();
        void setDefinitionId(String id);
        long getUniqueGroupID();
        long getMachineComponentGroupId();
        void markNoUpdateSync();
        void markStorageDirty();
        void markStorageForUpdate();
        NBTTagCompound getCustomDataTag();
        void setCustomDataTag(NBTTagCompound tag);
        IOInventory getInventory();
        void setInventory(IOInventory inv);
        void clearTransientComponentCache();
        int getTicksExisted();
        boolean isStructureFormed();
        boolean isWorking();
        int getActiveRuntimeThreadCount();
        int getRuntimeThreadCapacity();
    }

    private static final AtomicLong GROUP_IDS = new AtomicLong(1L);

    private final Host host;
    private final long componentGroupId = GROUP_IDS.getAndIncrement();
    private final OneBlockResourceState resources;
    private final OneBlockRuntimeComponents runtimeComponents;
    private String definitionId = "";
    private String definitionSignature = "";
    private Collection<MachineComponent<?>> cachedComponents = Collections.emptyList();

    public MachineComponentStorage(Host host) {
        this.host = requireNonNull(host, "host");
        this.resources = new OneBlockResourceState(host);
        this.runtimeComponents = new OneBlockRuntimeComponents(host, resources);
    }

    void setChangeListener(@Nullable Runnable listener) {
        resources.setChangeListener(listener);
    }

    public void setDefinitionId(@Nullable String id) {
        String normalized = OneBlockResourceState.normalizePath(id);
        if (!normalized.equals(definitionId)) {
            definitionId = normalized;
            clearTransientComponentCache();
        }
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public long getGroupId() {
        return componentGroupId;
    }

    public void clearTransientComponentCache() {
        definitionSignature = "";
        cachedComponents = Collections.emptyList();
    }

    @Nonnull
    public Collection<MachineComponent<?>> provideMachineComponents() {
        MachineDefinition definition = host.getDefinition();
        if (definition == null) {
            return Collections.emptyList();
        }
        syncDefinition(definition);
        return cachedComponents;
    }

    void syncDefinition(MachineDefinition definition) {
        String signature = buildDefinitionSignature(definition);
        if (signature.equals(definitionSignature)) {
            return;
        }
        boolean positionalFallback = resources.isEmpty();
        resources.rebuild(definition, positionalFallback);
        cachedComponents = runtimeComponents.build();
        definitionSignature = signature;
    }

    void readPayload(NBTTagCompound compound) {
        MachineDefinition definition = host.getDefinition();
        if (definition != null) {
            syncDefinition(definition);
        }
        resources.readPayload(compound);
    }

    void writePayload(NBTTagCompound compound) {
        resources.writePayload(compound);
    }

    int getItemInputSlotCount() {
        return resources.getItemInputSlotCount();
    }

    boolean isItemInputInventorySlot(int slot) {
        return resources.isItemInputInventorySlot(slot);
    }

    int[] getItemInputSlots() {
        return resources.getItemInputSlots();
    }

    int[] getItemOutputSlots() {
        return resources.getItemOutputSlots();
    }

    List<OneBlockResourceState.FluidRuntime> fluidRuntimes() {
        return resources.fluidRuntimes();
    }

    List<OneBlockResourceState.GasRuntime> gasRuntimes() {
        return resources.gasRuntimes();
    }

    List<OneBlockResourceState.EnergyRuntime> energyRuntimes() {
        return resources.energyRuntimes();
    }

    List<OneBlockResourceState.ComponentSnapshot> snapshots() {
        return resources.snapshots();
    }

    long runtimeFingerprint() {
        return resources.runtimeFingerprint();
    }

    private String buildDefinitionSignature(MachineDefinition definition) {
        StringBuilder builder = new StringBuilder(definition.getId());
        for (MachineComponentDefinition component : definition.getComponents()) {
            if (component == null || !component.isEnabled()) {
                continue;
            }
            builder.append('|')
                .append(component.getId()).append(':')
                .append(OneBlockResourceState.componentKind(component)).append(':')
                .append(OneBlockResourceState.componentIo(component)).append(':')
                .append(component.getSlots()).append(':')
                .append(component.getCapacity());
        }
        return builder.toString();
    }
}
