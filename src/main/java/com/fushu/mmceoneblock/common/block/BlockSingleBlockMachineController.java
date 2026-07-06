package com.fushu.mmceoneblock.common.block;

import com.fushu.mmceoneblock.MMCEOneBlock;
import com.fushu.mmceoneblock.common.config.MachineDefinition;
import com.fushu.mmceoneblock.common.config.SingleBlockMachineTileFactory;
import com.fushu.mmceoneblock.common.item.ItemBlockSingleBlockMachineController;
import com.fushu.mmceoneblock.common.network.GuiHandler;
import com.fushu.mmceoneblock.common.registry.MachineRegistry;
import com.fushu.mmceoneblock.common.tile.TileSingleBlockMachineController;
import hellfirepvp.modularmachinery.common.block.BlockController;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BlockSingleBlockMachineController extends BlockController {
    private final MachineDefinition definition;
    private final SingleBlockMachineTileFactory tileFactory;

    public BlockSingleBlockMachineController(MachineDefinition definition, SingleBlockMachineTileFactory tileFactory) {
        super();
        this.definition = definition;
        this.tileFactory = tileFactory;
        setHardness(5.0F);
        setResistance(10.0F);
        setRegistryName(new ResourceLocation(MachineRegistry.MODID, definition.getId()));
        setTranslationKey(MachineRegistry.MODID + "." + definition.getId());
    }

    public MachineDefinition getDefinition() {
        return definition;
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(@Nonnull World worldIn, int meta) {
        if (tileFactory == null) {
            return new TileSingleBlockMachineController(getStateFromMeta(meta), definition.getId());
        }
        return tileFactory.create(definition);
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        if (tileFactory == null) {
            return new TileSingleBlockMachineController(state, definition.getId());
        }
        return tileFactory.create(definition);
    }

    @Override
    public boolean onBlockActivated(World worldIn,
                                    @Nonnull BlockPos pos,
                                    @Nonnull IBlockState state,
                                    @Nonnull EntityPlayer playerIn,
                                    @Nonnull EnumHand hand,
                                    @Nonnull EnumFacing facing,
                                    float hitX,
                                    float hitY,
                                    float hitZ) {
        if (!worldIn.isRemote) {
            TileEntity te = worldIn.getTileEntity(pos);
            if (te instanceof TileSingleBlockMachineController) {
                playerIn.openGui(MMCEOneBlock.instance, GuiHandler.GUI_SINGLE_BLOCK_CONTROLLER,
                    worldIn, pos.getX(), pos.getY(), pos.getZ());
            }
        }
        return true;
    }

    @Nonnull
    @Override
    public ItemStack getItem(@Nonnull World worldIn, @Nonnull net.minecraft.util.math.BlockPos pos, @Nonnull IBlockState state) {
        Item item = Item.getItemFromBlock(this);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public boolean isOpaqueCube(@Nonnull IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(@Nonnull IBlockState state) {
        return false;
    }

    public ItemBlockSingleBlockMachineController createItemBlock() {
        return new ItemBlockSingleBlockMachineController(this, definition);
    }
}
