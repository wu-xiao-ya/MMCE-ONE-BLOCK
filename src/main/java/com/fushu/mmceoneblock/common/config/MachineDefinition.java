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
    private final ControllerType controllerType;
    private final String factoryGuiStyle;

    public MachineDefinition(String id,
                             ResourceLocation machine,
                             boolean enabled,
                             String displayName,
                             MachineBlockDefinition block,
                             List<MachineComponentDefinition> components,
                             String guiStyle,
                             Path sourceFile) {
        this(id, machine, enabled, displayName, block, components, guiStyle, sourceFile,
            ControllerType.AUTO, null);
    }

    public MachineDefinition(String id,
                             ResourceLocation machine,
                             boolean enabled,
                             String displayName,
                             MachineBlockDefinition block,
                             List<MachineComponentDefinition> components,
                             String guiStyle,
                             Path sourceFile,
                             ControllerType controllerType,
                             String factoryGuiStyle) {
        this.id = id;
        this.machine = machine;
        this.enabled = enabled;
        this.displayName = displayName;
        this.block = block;
        this.components = Collections.unmodifiableList(new ArrayList<MachineComponentDefinition>(components));
        this.guiStyle = guiStyle;
        this.sourceFile = sourceFile;
        this.controllerType = controllerType == null ? ControllerType.AUTO : controllerType;
        this.factoryGuiStyle = factoryGuiStyle;
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

    public ControllerType getControllerType() {
        return controllerType;
    }

    public boolean shouldUseFactoryTile(boolean backingFactoryOnly) {
        if (controllerType == ControllerType.FACTORY) {
            return true;
        }
        if (controllerType == ControllerType.MACHINE) {
            return false;
        }
        return backingFactoryOnly;
    }

    public String getFactoryGuiStyle() {
        return factoryGuiStyle;
    }
}
