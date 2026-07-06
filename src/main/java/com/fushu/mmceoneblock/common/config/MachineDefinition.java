package com.fushu.mmceoneblock.common.config;

import net.minecraft.util.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MachineDefinition {
    private final String id;
    private final ResourceLocation machine;
    private final boolean enabled;
    private final String displayName;
    private final MachineBlockDefinition block;
    private final List<MachineComponentDefinition> components;
    private final String guiStyle;
    private final Path sourceFile;

    public MachineDefinition(String id,
                             ResourceLocation machine,
                             boolean enabled,
                             String displayName,
                             MachineBlockDefinition block,
                             List<MachineComponentDefinition> components,
                             String guiStyle,
                             Path sourceFile) {
        this.id = id;
        this.machine = machine;
        this.enabled = enabled;
        this.displayName = displayName;
        this.block = block;
        this.components = Collections.unmodifiableList(new ArrayList<MachineComponentDefinition>(components));
        this.guiStyle = guiStyle;
        this.sourceFile = sourceFile;
    }

    public String getId() {
        return id;
    }

    public ResourceLocation getMachine() {
        return machine;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public MachineBlockDefinition getBlock() {
        return block;
    }

    public List<MachineComponentDefinition> getComponents() {
        return components;
    }

    public String getGuiStyle() {
        return guiStyle;
    }

    public Path getSourceFile() {
        return sourceFile;
    }
}
