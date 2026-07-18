package com.fushu.mmceoneblock.common.config;

import com.google.gson.JsonObject;
import javax.annotation.Nullable;

public final class MachineComponentDefinition {
    private final String type;
    private final String id;
    private final boolean enabled;
    private final String displayName;
    private final String kind;
    @Nullable
    private final String io;
    @Nullable
    private final Integer slots;
    @Nullable
    private final Long capacity;
    private final JsonObject raw;

    public MachineComponentDefinition(String type, String id, boolean enabled, String displayName, JsonObject raw) {
        this(type, id, enabled, displayName, MachineComponentTypes.kind(type), MachineComponentTypes.ioFromType(type), null, null, raw);
    }

    public MachineComponentDefinition(String type,
                                      String id,
                                      boolean enabled,
                                      String displayName,
                                      String kind,
                                      String io,
                                      Integer slots,
                                      Long capacity,
                                      JsonObject raw) {
        this.type = type;
        this.id = id;
        this.enabled = enabled;
        this.displayName = displayName;
        this.kind = normalize(kind);
        this.io = normalize(io);
        this.slots = slots;
        this.capacity = capacity;
        this.raw = raw;
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getKind() {
        return kind;
    }

    @Nullable
    public String getIo() {
        return io;
    }

    @Nullable
    public Integer getSlots() {
        return slots;
    }

    @Nullable
    public Long getCapacity() {
        return capacity;
    }

    public JsonObject getRaw() {
        return raw;
    }

    @Nullable
    private static String normalize(@Nullable String value) {
        return value == null ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
