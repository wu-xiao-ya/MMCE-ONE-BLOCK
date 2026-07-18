package com.fushu.mmceoneblock.common.config;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class OneBlockRuntimeBinding {
    public enum ResolutionStatus {
        PENDING,
        RESOLVED,
        MISSING_BACKING_MACHINE,
        FACTORY_ONLY_MISMATCH
    }

    public enum TileKind {
        MACHINE,
        FACTORY
    }

    private final MachineDefinition definition;
    @Nullable
    private final DynamicMachine backingMachine;
    private final ResourceLocation guiStyle;
    @Nullable
    private final ResourceLocation factoryGuiStyle;
    private final List<ComponentLayout> componentLayout;
    private final ResolutionStatus resolutionStatus;
    private final TileKind tileKind;

    private OneBlockRuntimeBinding(MachineDefinition definition,
                                   @Nullable DynamicMachine backingMachine,
                                   ResourceLocation guiStyle,
                                   @Nullable ResourceLocation factoryGuiStyle,
                                   List<ComponentLayout> componentLayout,
                                   ResolutionStatus resolutionStatus,
                                   TileKind tileKind) {
        this.definition = definition;
        this.backingMachine = backingMachine;
        this.guiStyle = guiStyle;
        this.factoryGuiStyle = factoryGuiStyle;
        this.componentLayout = Collections.unmodifiableList(new ArrayList<ComponentLayout>(componentLayout));
        this.resolutionStatus = resolutionStatus;
        this.tileKind = tileKind;
    }

    public static OneBlockRuntimeBinding fromDefinition(MachineDefinition definition) {
        ResourceLocation guiStyle = parseStyle(definition.getGuiStyle(), definition.getSourceFile(), "guiStyle");
        ResourceLocation factoryGuiStyle = parseOptionalStyle(definition.getFactoryGuiStyle(),
            definition.getSourceFile(), "factoryGuiStyle");
        List<ComponentLayout> componentLayout = buildLayout(definition.getComponents());
        return new OneBlockRuntimeBinding(
            definition,
            null,
            guiStyle,
            factoryGuiStyle,
            componentLayout,
            ResolutionStatus.PENDING,
            resolveTileKind(definition, null)
        );
    }

    public static OneBlockRuntimeBinding fromDefinition(MachineDefinition definition,
                                                       @Nullable DynamicMachine backingMachine) {
        ResourceLocation guiStyle = parseStyle(definition.getGuiStyle(), definition.getSourceFile(), "guiStyle");
        ResourceLocation factoryGuiStyle = parseOptionalStyle(definition.getFactoryGuiStyle(),
            definition.getSourceFile(), "factoryGuiStyle");
        List<ComponentLayout> componentLayout = buildLayout(definition.getComponents());
        ResolutionStatus resolutionStatus = resolveStatus(definition, backingMachine);
        TileKind tileKind = resolveTileKind(definition, backingMachine);
        return new OneBlockRuntimeBinding(
            definition,
            backingMachine,
            guiStyle,
            factoryGuiStyle,
            componentLayout,
            resolutionStatus,
            tileKind
        );
    }

    public OneBlockRuntimeBinding resolve(@Nullable DynamicMachine backingMachine) {
        return fromDefinition(this.definition, backingMachine);
    }

    public MachineDefinition getDefinition() {
        return definition;
    }

    @Nullable
    public DynamicMachine getBackingMachine() {
        return backingMachine;
    }

    public ResourceLocation getGuiStyle() {
        return guiStyle;
    }

    @Nullable
    public ResourceLocation getFactoryGuiStyle() {
        return factoryGuiStyle;
    }

    public ResourceLocation getEffectiveFactoryGuiStyle() {
        return factoryGuiStyle == null ? guiStyle : factoryGuiStyle;
    }

    public List<ComponentLayout> getComponentLayout() {
        return componentLayout;
    }

    public ResolutionStatus getResolutionStatus() {
        return resolutionStatus;
    }

    public boolean hasBackingMachine() {
        return backingMachine != null;
    }

    public boolean isPending() {
        return resolutionStatus == ResolutionStatus.PENDING;
    }

    public boolean isResolved() {
        return resolutionStatus == ResolutionStatus.RESOLVED;
    }

    public boolean hasResolutionError() {
        return resolutionStatus == ResolutionStatus.MISSING_BACKING_MACHINE
            || resolutionStatus == ResolutionStatus.FACTORY_ONLY_MISMATCH;
    }

    public boolean usesFactoryTile() {
        return tileKind == TileKind.FACTORY;
    }

    public TileKind getTileKind() {
        return tileKind;
    }

    public String getDiagnosticMessage() {
        switch (resolutionStatus) {
            case PENDING:
                return "binding has not been validated yet";
            case RESOLVED:
                return "binding resolved successfully";
            case MISSING_BACKING_MACHINE:
                return "backing MMCE machine is missing";
            case FACTORY_ONLY_MISMATCH:
                return "backing MMCE machine is factory-only but the controller is fixed to MACHINE";
            default:
                return resolutionStatus.name();
        }
    }

    private static ResolutionStatus resolveStatus(MachineDefinition definition, @Nullable DynamicMachine backingMachine) {
        if (backingMachine == null) {
            return ResolutionStatus.MISSING_BACKING_MACHINE;
        }
        if (definition.getControllerType() == ControllerType.MACHINE && backingMachine.isFactoryOnly()) {
            return ResolutionStatus.FACTORY_ONLY_MISMATCH;
        }
        return ResolutionStatus.RESOLVED;
    }

    private static TileKind resolveTileKind(MachineDefinition definition, @Nullable DynamicMachine backingMachine) {
        ControllerType controllerType = definition.getControllerType();
        if (backingMachine != null && backingMachine.isFactoryOnly()) {
            return TileKind.FACTORY;
        }
        if (controllerType == ControllerType.FACTORY) {
            return TileKind.FACTORY;
        }
        if (controllerType == ControllerType.MACHINE) {
            return TileKind.MACHINE;
        }
        return TileKind.MACHINE;
    }

    private static List<ComponentLayout> buildLayout(List<MachineComponentDefinition> components) {
        if (components == null || components.isEmpty()) {
            return Collections.emptyList();
        }
        List<ComponentLayout> out = new ArrayList<ComponentLayout>();
        for (MachineComponentDefinition component : components) {
            if (component == null) {
                continue;
            }
            out.add(new ComponentLayout(component));
        }
        return out;
    }

    private static ResourceLocation parseStyle(@Nullable String raw, @Nullable java.nio.file.Path sourceFile, String key) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Machine definition " + sourceFile + " is missing " + key);
        }
        try {
            return StrictResourceLocation.parse(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Machine definition " + sourceFile + " has an illegal " + key + " '" + raw + "'", ex);
        }
    }

    @Nullable
    private static ResourceLocation parseOptionalStyle(@Nullable String raw, @Nullable java.nio.file.Path sourceFile, String key) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return StrictResourceLocation.parse(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Machine definition " + sourceFile + " has an illegal " + key + " '" + raw + "'", ex);
        }
    }

    public static final class ComponentLayout {
        private final MachineComponentDefinition definition;
        private final String type;
        private final String kind;
        @Nullable
        private final String io;
        @Nullable
        private final Integer slots;
        @Nullable
        private final Long capacity;
        private final JsonObject raw;

        private ComponentLayout(MachineComponentDefinition definition) {
            this.definition = definition;
            this.type = definition.getType();
            this.kind = definition.getKind() == null || definition.getKind().trim().isEmpty()
                ? MachineComponentTypes.kind(definition.getType())
                : definition.getKind().trim().toLowerCase(Locale.ROOT);
            this.io = normalizeNullable(definition.getIo());
            this.slots = definition.getSlots();
            this.capacity = definition.getCapacity();
            this.raw = definition.getRaw();
        }

        public MachineComponentDefinition getDefinition() {
            return definition;
        }

        public String getType() {
            return type;
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

        private static String normalizeNullable(@Nullable String value) {
            return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
