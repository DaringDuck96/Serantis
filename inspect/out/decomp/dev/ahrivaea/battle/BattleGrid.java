package dev.ahrivaea.battle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class BattleGrid {
   public final int size;
   public final int originX;
   public final int originZ;
   public final int[] heights;
   public final BattleGrid.Terrain[] terrain;

   public BattleGrid(int size, int originX, int originZ, int[] heights, BattleGrid.Terrain[] terrain) {
      this.size = size;
      this.originX = originX;
      this.originZ = originZ;
      this.heights = heights;
      this.terrain = terrain;
   }

   public boolean inside(int x, int z) {
      return x >= 0 && z >= 0 && x < this.size && z < this.size;
   }

   public int y(int x, int z) {
      return this.heights[x + z * this.size];
   }

   public BattleGrid.Terrain at(int x, int z) {
      return this.terrain[x + z * this.size];
   }

   public BlockPos worldPos(int x, int z) {
      return new BlockPos(this.originX + x, this.y(x, z), this.originZ + z);
   }

   public static BattleGrid read(Level level, BlockPos center, int radius) {
      int size = radius * 2 + 1;
      int[] h = new int[size * size];
      BattleGrid.Terrain[] t = new BattleGrid.Terrain[size * size];

      for (int x = 0; x < size; x++) {
         for (int z = 0; z < size; z++) {
            int wx = center.getX() - radius + x;
            int wz = center.getZ() - radius + z;
            BattleGrid.Terrain kind = BattleGrid.Terrain.WALL;
            int y = center.getY();

            for (int yy = center.getY() + 12; yy >= center.getY() - 12; yy--) {
               BlockPos feet = new BlockPos(wx, yy, wz);
               BlockState f = level.getBlockState(feet);
               BlockState head = level.getBlockState(feet.above());
               BlockState below = level.getBlockState(feet.below());
               if (open(level, feet, f) && open(level, feet.above(), head)) {
                  if (f.getFluidState().is(FluidTags.LAVA)) {
                     kind = BattleGrid.Terrain.LAVA;
                     y = yy;
                     break;
                  }

                  if (f.getFluidState().is(FluidTags.WATER)) {
                     kind = below.getFluidState().is(FluidTags.WATER) ? BattleGrid.Terrain.DEEP_WATER : BattleGrid.Terrain.WATER;
                     y = yy;
                     break;
                  }

                  if (!below.isAir() && (below.getFluidState().isEmpty() || !below.getCollisionShape(level, feet.below()).isEmpty())) {
                     if (below.is(BlockTags.LEAVES) || below.is(BlockTags.LOGS)) {
                        kind = BattleGrid.Terrain.WALL;
                        y = yy;
                        break;
                     }

                     if (below.isFaceSturdy(level, feet.below(), Direction.UP) || !below.getCollisionShape(level, feet.below()).isEmpty()) {
                        kind = BattleGrid.Terrain.GROUND;
                        y = yy;
                        break;
                     }
                  }
               }
            }

            h[x + z * size] = y;
            t[x + z * size] = kind;
         }
      }

      return new BattleGrid(size, center.getX() - radius, center.getZ() - radius, h, t);
   }

   private static boolean open(Level level, BlockPos pos, BlockState s) {
      return s.getCollisionShape(level, pos).isEmpty() && !s.is(BlockTags.LEAVES);
   }

   public static enum Terrain {
      GROUND('G'),
      WATER('W'),
      DEEP_WATER('D'),
      LAVA('L'),
      WALL('X');

      private final char code;

      private Terrain(char code) {
         this.code = code;
      }

      public char code() {
         return this.code;
      }

      public static BattleGrid.Terrain of(char c) {
         for (BattleGrid.Terrain t : values()) {
            if (t.code == c) {
               return t;
            }
         }

         return WALL;
      }
   }
}
