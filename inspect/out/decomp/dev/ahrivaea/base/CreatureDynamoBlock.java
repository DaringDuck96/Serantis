package dev.ahrivaea.base;

import dev.ahrivaea.creature.CreatureEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

public class CreatureDynamoBlock extends Block {
   public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

   public CreatureDynamoBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(POWERED, false));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{POWERED});
   }

   protected boolean isSignalSource(BlockState state) {
      return true;
   }

   protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
      return state.getValue(POWERED) ? 15 : 0;
   }

   public static void power(ServerLevel level, BlockPos pos) {
      BlockState s = level.getBlockState(pos);
      if (s.getBlock() instanceof CreatureDynamoBlock) {
         if (!(Boolean)s.getValue(POWERED)) {
            level.setBlock(pos, (BlockState)s.setValue(POWERED, true), 3);
         }

         level.scheduleTick(pos, s.getBlock(), 40);
      }
   }

   protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if ((Boolean)state.getValue(POWERED)) {
         boolean worked = !level.getEntitiesOfClass(
               CreatureEntity.class, new AABB(pos).inflate(3.0), c -> c.brain() != null && pos.equals(c.brain().powering())
            )
            .isEmpty();
         if (worked) {
            level.scheduleTick(pos, this, 40);
         } else {
            level.setBlock(pos, (BlockState)state.setValue(POWERED, false), 3);
         }
      }
   }
}
