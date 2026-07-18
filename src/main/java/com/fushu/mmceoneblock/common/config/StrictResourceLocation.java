package com.fushu.mmceoneblock.common.config;

import net.minecraft.util.ResourceLocation;

import java.util.Locale;

public final class StrictResourceLocation {
    private static final String NAMESPACE_PATTERN = "[a-z0-9_.-]+";
    private static final String PATH_PATTERN = "[a-z0-9/._-]+";

    private StrictResourceLocation() {
    }

    public static ResourceLocation parse(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        ResourceLocation location = new ResourceLocation(normalized);
        if (!location.getNamespace().matches(NAMESPACE_PATTERN)
            || !location.getPath().matches(PATH_PATTERN)) {
            throw new IllegalArgumentException("Invalid resource location: " + raw);
        }
        return location;
    }
}
