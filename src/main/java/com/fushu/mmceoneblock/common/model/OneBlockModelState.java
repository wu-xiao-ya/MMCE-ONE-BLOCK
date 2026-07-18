package com.fushu.mmceoneblock.common.model;

import net.minecraftforge.common.property.IUnlistedProperty;

public final class OneBlockModelState {
    public static final IUnlistedProperty<OneBlockRenderState> RENDER_STATE = new RenderStateProperty();

    private OneBlockModelState() {
    }

    private static final class RenderStateProperty implements IUnlistedProperty<OneBlockRenderState> {
        @Override
        public String getName() {
            return "oneblock_render_state";
        }

        @Override
        public boolean isValid(OneBlockRenderState value) {
            return true;
        }

        @Override
        public Class<OneBlockRenderState> getType() {
            return OneBlockRenderState.class;
        }

        @Override
        public String valueToString(OneBlockRenderState value) {
            return value == null ? "null" : value.toString();
        }
    }
}
