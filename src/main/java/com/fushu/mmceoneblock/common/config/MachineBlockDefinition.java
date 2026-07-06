package com.fushu.mmceoneblock.common.config;

public final class MachineBlockDefinition {
    private final String model;
    private final String texture;

    public MachineBlockDefinition(String model, String texture) {
        this.model = model;
        this.texture = texture;
    }

    public String getModel() {
        return model;
    }

    public String getTexture() {
        return texture;
    }
}
