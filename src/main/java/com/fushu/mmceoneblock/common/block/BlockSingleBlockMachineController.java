package com.fushu.mmceoneblock.common.block;

import com.fushu.mmceoneblock.MMCEOneBlock;
import com.fushu.mmceoneblock.common.config.OneBlockRuntimeBinding;
import com.fushu.mmceoneblock.common.config.MachineDefinition;
import com.fushu.mmceoneblock.common.config.SingleBlockMachineTileFactory;
import com.fushu.mmceoneblock.common.item.ItemBlockSingleBlockMachineController;
import com.fushu.mmceoneblock.common.model.OneBlockModelState;
import com.fushu.mmceoneblock.common.model.OneBlockRenderState;
import com.fushu.mmceoneblock.common.network.GuiHandler;
import com.fushu.mmceoneblock.common.registry.MachineRegistry;
import com.fushu.mmceoneblock.common.tile.TileSingleBlockFactoryController;
import com.fushu.mmceoneblock.common.tile.TileSingleBlockMachineController;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BlockSingleBlockMachineController extends BlockController {
    private final String machineId;
    private final OneBlockRuntimeBinding initialBinding;
    private final SingleBlockMachineTileFactory tileFactory;

    public BlockSingleBlockMachineController(OneBlockRuntimeBinding binding, SingleBlockMachineTileFactory tileFactory) {
        super();
        this.initialBinding = binding;
        this.machineId = binding.getDefinition().getId();
        this.tileFactory = tileFactory;
        setHardness(5.0F);
        setResistance(10.0F);
        setRegistryName(new ResourceLocation(MachineRegistry.MODID, machineId));
        setTranslationKey(MachineRegistry.MODID + "." + machineId);
    }

    public MachineDefinition getDefinition() {
        return getBinding().getDefinition();
    }

    @Nullable
    public OneBlockRuntimeBinding getBinding() {
        OneBlockRuntimeBinding binding = MachineRegistry.getBinding(machineId);
        return binding == null ? initialBinding : binding;
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(@Nonnull World worldIn, int meta) {
        OneBlockRuntimeBinding binding = getBinding();
        if (tileFactory == null) {
            return MachineRegistry.createDefaultTile(binding, getStateFromMeta(meta));
        }
        return tileFactory.create(binding);
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        OneBlockRuntimeBinding binding = getBinding();
        if (tileFactory == null) {
            return MachineRegistry.createDefaultTile(binding, state);
        }
        return tileFactory.create(binding);
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
            if (te instanceof TileSingleBlockFactoryController) {
                if (tryInteractWithFluidContainer(te, playerIn, hand, facing)) {
                    return true;
                }
                playerIn.openGui(MMCEOneBlock.instance, GuiHandler.GUI_SINGLE_BLOCK_FACTORY_CONTROLLER,
                    worldIn, pos.getX(), pos.getY(), pos.getZ());
            } else if (te instanceof TileSingleBlockMachineController) {
                if (tryInteractWithFluidContainer(te, playerIn, hand, facing)) {
                    return true;
                }
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
        return new ItemBlockSingleBlockMachineController(this, getBinding().getDefinition());
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(
            this,
            new IProperty<?>[] { FACING, FORMED },
            new IUnlistedProperty<?>[] { OneBlockModelState.RENDER_STATE }
        );
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState)) {
            return state;
        }

        TileEntity tile = world.getTileEntity(pos);
        boolean formed = tile instanceof TileMultiblockMachineController
            && ((TileMultiblockMachineController) tile).isStructureFormed();
        boolean working = isWorking(tile);
        NBTTagCompound customData = customData(tile);

        return ((IExtendedBlockState) state).withProperty(
            OneBlockModelState.RENDER_STATE,
            new OneBlockRenderState(
                machineId,
                formed,
                working,
                customData.getFloat("oneblock.fluid.ratio"),
                customData.getFloat("oneblock.gas.ratio"),
                customData.getFloat("oneblock.energy.ratio")
            )
        );
    }

    private static boolean tryInteractWithFluidContainer(TileEntity tile,
                                                         EntityPlayer player,
                                                         EnumHand hand,
                                                         EnumFacing facing) {
        if (player.getHeldItem(hand).isEmpty()) {
            return false;
        }
        IFluidHandler handler = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, facing);
        return handler != null && FluidUtil.interactWithFluidHandler(player, hand, handler);
    }

    private static boolean isWorking(@Nullable TileEntity tile) {
        if (tile instanceof TileSingleBlockMachineController) {
            return ((TileSingleBlockMachineController) tile).isWorking();
        }
        if (tile instanceof TileSingleBlockFactoryController) {
            return ((TileSingleBlockFactoryController) tile).isWorking();
        }
        return false;
    }

    @Nonnull
    private static NBTTagCompound customData(@Nullable TileEntity tile) {
        if (tile instanceof TileSingleBlockMachineController) {
            return ((TileSingleBlockMachineController) tile).getCustomDataTag();
        }
        if (tile instanceof TileSingleBlockFactoryController) {
            return ((TileSingleBlockFactoryController) tile).getCustomDataTag();
        }
        return new NBTTagCompound();
    }
}
