package com.fushu.mmceoneblock.common.config;

import com.google.gson.JsonObject;

public final class MachineComponentDefinition {
    private final String type;
    private final String id;
    private final boolean enabled;
    private final String displayName;
    private final JsonObject raw;

    public MachineComponentDefinition(String type, String id, boolean enabled, String displayName, JsonObject raw) {
        this.type = type;
        this.id = id;
        this.enabled = enabled;
        this.displayName = displayName;
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

    public JsonObject getRaw() {
        return raw;
    }
}
