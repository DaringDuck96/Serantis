package dev.serantis.dyedwater;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;

/** Water in a dye's colour. Everything else (flowing, swimming, putting out fire...) is water's. */
public abstract class DyedWaterFluid extends WaterFluid {
    protected final DyedWater.Kind kind;

    protected DyedWaterFluid(DyedWater.Kind kind) { this.kind = kind; }

    @Override public Fluid getFlowing() { return kind.flowing; }
    @Override public Fluid getSource() { return kind.source; }
    @Override public Item getBucket() { return kind.bucket; }

    @Override
    public BlockState createLegacyBlock(FluidState state) {
        return kind.block.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    @Override
    public boolean isSame(Fluid fluid) { return fluid == kind.source || fluid == kind.flowing; }

    public static final class Source extends DyedWaterFluid {
        public Source(DyedWater.Kind kind) { super(kind); }
        @Override public int getAmount(FluidState state) { return 8; }
        @Override public boolean isSource(FluidState state) { return true; }
    }

    public static final class Flowing extends DyedWaterFluid {
        public Flowing(DyedWater.Kind kind) { super(kind); }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override public int getAmount(FluidState state) { return state.getValue(LEVEL); }
        @Override public boolean isSource(FluidState state) { return false; }
    }
}
