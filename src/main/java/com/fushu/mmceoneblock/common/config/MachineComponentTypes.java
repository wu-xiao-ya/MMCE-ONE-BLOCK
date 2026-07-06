package com.fushu.mmceoneblock.common.config;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class MachineComponentTypes {
    private static final Set<String> KNOWN = new LinkedHashSet<String>();

    static {
        register("controller");
        register("single_block_controller");
        register("machine");
        register("casing");
        register("item");
        register("item_input");
        register("item_output");
        register("fluid");
        register("fluid_input");
        register("fluid_output");
        register("gas");
        register("gas_input");
        register("gas_output");
        register("energy");
        register("energy_input");
        register("energy_output");
        register("parallel_controller");
        register("upgrade_bus");
        register("smart_interface");
    }

    private MachineComponentTypes() {
    }

    public static void register(String type) {
        if (type == null) {
            return;
        }
        String normalized = normalize(type);
        if (normalized.isEmpty()) {
            return;
        }
        KNOWN.add(normalized);
    }

    public static boolean isKnown(String type) {
        if (type == null) {
            return false;
        }
        return KNOWN.contains(normalize(type));
    }

    public static boolean isRuntimeSupported(String type) {
        String kind = kind(type);
        return "item".equals(kind)
            || "fluid".equals(kind)
            || "gas".equals(kind)
            || "energy".equals(kind);
    }

    public static String kind(String type) {
        String normalized = normalize(type);
        if (normalized.endsWith("_input")) {
            normalized = normalized.substring(0, normalized.length() - "_input".length());
        } else if (normalized.endsWith("_output")) {
            normalized = normalized.substring(0, normalized.length() - "_output".length());
        }
        if ("power".equals(normalized) || "rf".equals(normalized) || "fe".equals(normalized)) {
            return "energy";
        }
        return normalized;
    }

    public static String ioFromType(String type) {
        String normalized = normalize(type);
        if (normalized.endsWith("_input")) {
            return "input";
        }
        if (normalized.endsWith("_output")) {
            return "output";
        }
        return null;
    }

    public static Set<String> knownTypes() {
        return Collections.unmodifiableSet(KNOWN);
    }

    public static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}
