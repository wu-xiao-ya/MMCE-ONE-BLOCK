package com.fushu.mmceoneblock.common.config;

import java.util.Locale;

public enum ControllerType {
    AUTO,
    MACHINE,
    FACTORY;

    public static ControllerType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return AUTO;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        switch (trimmed) {
            case "auto":
                return AUTO;
            case "machine":
                return MACHINE;
            case "factory":
                return FACTORY;
            default:
                return AUTO;
        }
    }
}
