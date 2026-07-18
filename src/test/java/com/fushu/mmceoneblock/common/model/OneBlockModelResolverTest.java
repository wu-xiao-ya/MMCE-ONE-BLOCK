package com.fushu.mmceoneblock.common.model;

import com.fushu.mmceoneblock.common.config.MachineBlockDefinition;
import net.minecraft.util.EnumFacing;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;

public final class OneBlockModelResolverTest {
    @Test
    public void resolvesUnformedIdleWorkingAndFacingAcrossAllDirections() {
        MachineBlockDefinition block = advancedManaPoolDefinition(null);
        MachineBlockDefinition.ModelVariant unformed = block.getStateVariant(MachineBlockDefinition.RenderState.UNFORMED);
        MachineBlockDefinition.ModelVariant idle = block.getStateVariant(MachineBlockDefinition.RenderState.IDLE);
        MachineBlockDefinition.ModelVariant working = block.getStateVariant(MachineBlockDefinition.RenderState.WORKING);

        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            OneBlockModelResolver.ResolvedModel unformedModel = OneBlockModelResolver.resolveBlockModel(
                block,
                new OneBlockRenderState("mmceoneblock:advanced_mana_pool_controller", false, false, 0.0D, 0.0D, 0.0D),
                facing
            );
            assertNotNull(unformedModel);
            assertEquals(MachineBlockDefinition.RenderState.UNFORMED, unformedModel.getResolvedState());
            assertSame(unformed, unformedModel.getStateVariant());
            assertEquals("mmceoneblock:advanced_mana_pool_controller_empty", unformedModel.getSourceModel());
            assertEquals(facing, unformedModel.getFacing());
            assertEquals("mmceoneblock:blocks/advanced_mana_pool_base", unformedModel.getTextures().get("all"));

            OneBlockModelResolver.ResolvedModel idleModel = OneBlockModelResolver.resolveBlockModel(
                block,
                new OneBlockRenderState("mmceoneblock:advanced_mana_pool_controller", true, false, 0.0D, 0.0D, 0.0D),
                facing
            );
            assertNotNull(idleModel);
            assertEquals(MachineBlockDefinition.RenderState.IDLE, idleModel.getResolvedState());
            assertSame(idle, idleModel.getStateVariant());
            assertEquals("mmceoneblock:advanced_mana_pool_controller", idleModel.getSourceModel());
            assertEquals(facing, idleModel.getFacing());
            assertEquals("mmceoneblock:blocks/advanced_mana_pool_port", idleModel.getTextures().get("port"));

            OneBlockModelResolver.ResolvedModel workingModel = OneBlockModelResolver.resolveBlockModel(
                block,
                new OneBlockRenderState("mmceoneblock:advanced_mana_pool_controller", true, true, 0.0D, 0.0D, 0.0D),
                facing
            );
            assertNotNull(workingModel);
            assertEquals(MachineBlockDefinition.RenderState.WORKING, workingModel.getResolvedState());
            assertSame(working, workingModel.getStateVariant());
            assertEquals("mmceoneblock:advanced_mana_pool_controller", workingModel.getSourceModel());
            assertEquals(facing, workingModel.getFacing());
            assertEquals("mmceoneblock:blocks/advanced_mana_pool_fluid", workingModel.getTextures().get("fluid"));
        }
    }

    @Test
    public void selectsFluidTextureLevelsAtAllThresholds() {
        MachineBlockDefinition block = advancedManaPoolDefinition(null);

        assertFluidLevel(block, 0.0D, "mmceoneblock:advanced_mana_pool_controller", null);
        assertFluidLevel(block, 0.25D, "mmceoneblock:advanced_mana_pool_controller_25",
            "mmceoneblock:blocks/advanced_mana_pool_fluid_25");
        assertFluidLevel(block, 0.50D, "mmceoneblock:advanced_mana_pool_controller_50",
            "mmceoneblock:blocks/advanced_mana_pool_fluid_50");
        assertFluidLevel(block, 0.75D, "mmceoneblock:advanced_mana_pool_controller_75",
            "mmceoneblock:blocks/advanced_mana_pool_fluid_75");
        assertFluidLevel(block, 1.0D, "mmceoneblock:advanced_mana_pool_controller_full",
            "mmceoneblock:blocks/advanced_mana_pool_fluid_full");
    }

    @Test
    public void selectsGasAndEnergyTextureLevelsIndependently() {
        MachineBlockDefinition block = advancedManaPoolDefinition(null);

        OneBlockModelResolver.ResolvedModel gasModel = OneBlockModelResolver.resolveBlockModel(
            block,
            new OneBlockRenderState("mmceoneblock:advanced_mana_pool_controller", true, true, 0.0D, 0.50D, 0.0D),
            EnumFacing.NORTH
        );
        assertNotNull(gasModel);
        assertEquals(MachineBlockDefinition.RenderState.WORKING, gasModel.getResolvedState());
        assertEquals("mmceoneblock:advanced_mana_pool_controller_gas", gasModel.getSourceModel());
        assertEquals("mmceoneblock:blocks/advanced_mana_pool_port_gas", gasModel.getTextures().get("port"));
        assertNotNull(gasModel.getTextureLevel());
        assertEquals("gas", gasModel.getTextureLevel().getContent());

        OneBlockModelResolver.ResolvedModel energyModel = OneBlockModelResolver.resolveBlockModel(
            block,
            new OneBlockRenderState("mmceoneblock:advanced_mana_pool_controller", true, true, 0.0D, 0.0D, 0.75D),
            EnumFacing.NORTH
        );
        assertNotNull(energyModel);
        assertEquals(MachineBlockDefinition.RenderState.WORKING, energyModel.getResolvedState());
        assertEquals("mmceoneblock:advanced_mana_pool_controller_energy", energyModel.getSourceModel());
        assertEquals("mmceoneblock:blocks/advanced_mana_pool_base_energy", energyModel.getTextures().get("all"));
        assertNotNull(energyModel.getTextureLevel());
        assertEquals("energy", energyModel.getTextureLevel().getContent());
    }

    @Test
    public void resolvesItemModelAndFallsBackToBaseModel() {
        MachineBlockDefinition fallbackBlock = advancedManaPoolDefinition(null);
        OneBlockModelResolver.ResolvedModel fallbackItem = OneBlockModelResolver.resolveItemModel(fallbackBlock);
        assertNotNull(fallbackItem);
        assertEquals("mmceoneblock:advanced_mana_pool_controller", fallbackItem.getSourceModel());
        assertEquals(EnumFacing.NORTH, fallbackItem.getFacing());
        assertEquals(6, fallbackItem.getTextures().size());

        MachineBlockDefinition explicitItemBlock = advancedManaPoolDefinition("mmceoneblock:advanced_mana_pool_controller_item");
        OneBlockModelResolver.ResolvedModel explicitItem = OneBlockModelResolver.resolveItemModel(explicitItemBlock);
        assertNotNull(explicitItem);
        assertEquals("mmceoneblock:advanced_mana_pool_controller_item", explicitItem.getSourceModel());
        assertEquals(EnumFacing.NORTH, explicitItem.getFacing());
    }

    private static void assertFluidLevel(MachineBlockDefinition block,
                                         double ratio,
                                         String expectedSource,
                                         String expectedFluidTexture) {
        OneBlockModelResolver.ResolvedModel model = OneBlockModelResolver.resolveBlockModel(
            block,
            new OneBlockRenderState("mmceoneblock:advanced_mana_pool_controller", true, true, ratio, 0.0D, 0.0D),
            EnumFacing.NORTH
        );
        assertNotNull(model);
        assertEquals(MachineBlockDefinition.RenderState.WORKING, model.getResolvedState());
        assertEquals(expectedSource, model.getSourceModel());
        if (expectedFluidTexture == null) {
            assertNull(model.getTextureLevel());
            assertEquals("mmceoneblock:blocks/advanced_mana_pool_fluid", model.getTextures().get("fluid"));
        } else {
            assertNotNull(model.getTextureLevel());
            assertEquals("fluid", model.getTextureLevel().getContent());
            assertEquals(expectedSource, model.getTextureLevel().getModel());
            assertEquals(expectedFluidTexture, model.getTextures().get("fluid"));
            assertEquals(expectedFluidTexture, model.getTextureLevel().getTextures().get("fluid"));
        }
    }

    private static MachineBlockDefinition advancedManaPoolDefinition(String itemModel) {
        Map<String, String> textures = textures(
            "particle", "mmceoneblock:blocks/advanced_mana_pool_side",
            "base", "mmceoneblock:blocks/advanced_mana_pool_base",
            "side", "mmceoneblock:blocks/advanced_mana_pool_side",
            "inner", "mmceoneblock:blocks/advanced_mana_pool_inner",
            "fluid", "mmceoneblock:blocks/advanced_mana_pool_fluid",
            "port", "mmceoneblock:blocks/advanced_mana_pool_port"
        );

        Map<MachineBlockDefinition.RenderState, MachineBlockDefinition.ModelVariant> states =
            new LinkedHashMap<MachineBlockDefinition.RenderState, MachineBlockDefinition.ModelVariant>();
        states.put(
            MachineBlockDefinition.RenderState.UNFORMED,
            new MachineBlockDefinition.ModelVariant(
                "mmceoneblock:advanced_mana_pool_controller_empty",
                textures("all", "mmceoneblock:blocks/advanced_mana_pool_base")
            )
        );
        states.put(
            MachineBlockDefinition.RenderState.IDLE,
            new MachineBlockDefinition.ModelVariant(
                "mmceoneblock:advanced_mana_pool_controller",
                textures("port", "mmceoneblock:blocks/advanced_mana_pool_port")
            )
        );
        states.put(
            MachineBlockDefinition.RenderState.WORKING,
            new MachineBlockDefinition.ModelVariant(
                "mmceoneblock:advanced_mana_pool_controller",
                textures("fluid", "mmceoneblock:blocks/advanced_mana_pool_fluid")
            )
        );

        List<MachineBlockDefinition.TextureLevel> levels = new ArrayList<MachineBlockDefinition.TextureLevel>();
        levels.add(new MachineBlockDefinition.TextureLevel(
            "fluid",
            0.25D,
            "mmceoneblock:advanced_mana_pool_controller_25",
            textures("fluid", "mmceoneblock:blocks/advanced_mana_pool_fluid_25")
        ));
        levels.add(new MachineBlockDefinition.TextureLevel(
            "fluid",
            0.50D,
            "mmceoneblock:advanced_mana_pool_controller_50",
            textures("fluid", "mmceoneblock:blocks/advanced_mana_pool_fluid_50")
        ));
        levels.add(new MachineBlockDefinition.TextureLevel(
            "fluid",
            0.75D,
            "mmceoneblock:advanced_mana_pool_controller_75",
            textures("fluid", "mmceoneblock:blocks/advanced_mana_pool_fluid_75")
        ));
        levels.add(new MachineBlockDefinition.TextureLevel(
            "fluid",
            1.0D,
            "mmceoneblock:advanced_mana_pool_controller_full",
            textures("fluid", "mmceoneblock:blocks/advanced_mana_pool_fluid_full")
        ));
        levels.add(new MachineBlockDefinition.TextureLevel(
            "gas",
            0.50D,
            "mmceoneblock:advanced_mana_pool_controller_gas",
            textures("port", "mmceoneblock:blocks/advanced_mana_pool_port_gas")
        ));
        levels.add(new MachineBlockDefinition.TextureLevel(
            "energy",
            0.75D,
            "mmceoneblock:advanced_mana_pool_controller_energy",
            textures("all", "mmceoneblock:blocks/advanced_mana_pool_base_energy")
        ));

        return new MachineBlockDefinition(
            "mmceoneblock:advanced_mana_pool_controller",
            itemModel,
            textures,
            states,
            levels
        );
    }

    private static Map<String, String> textures(String... values) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (int i = 0; i < values.length; i += 2) {
            out.put(values[i], values[i + 1]);
        }
        return out;
    }
}
