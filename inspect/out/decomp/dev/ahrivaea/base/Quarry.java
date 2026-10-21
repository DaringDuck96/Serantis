package dev.ahrivaea.base;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;

public final class Quarry {
   public static final int STAIRS = 0;
   public static final int LAYERS = 1;
   public static final int DONE = 2;
   public int phase;
   public int step;
   public int layer = Integer.MIN_VALUE;
   public boolean structures;
   public boolean paused;
   public long mined;
   public boolean needsChest;
   public long lastAlert;
   public static final Codec<Quarry> CODEC = RecordCodecBuilder.create(
      i -> i.group(
               Codec.INT.optionalFieldOf("phase", 0).forGetter(q -> q.phase),
               Codec.INT.optionalFieldOf("step", 0).forGetter(q -> q.step),
               Codec.INT.optionalFieldOf("layer", Integer.MIN_VALUE).forGetter(q -> q.layer),
               Codec.BOOL.optionalFieldOf("structures", false).forGetter(q -> q.structures),
               Codec.BOOL.optionalFieldOf("paused", false).forGetter(q -> q.paused),
               Codec.LONG.optionalFieldOf("mined", 0L).forGetter(q -> q.mined)
            )
            .apply(i, (phase, step, layer, structures, paused, mined) -> {
               Quarry q = new Quarry();
               q.phase = phase;
               q.step = step;
               q.layer = layer;
               q.structures = structures;
               q.paused = paused;
               q.mined = mined;
               return q;
            })
   );
   private static final int[][] RING = ring();

   public static int top(BlockPos post) {
      return post.getY() - 1;
   }

   public static int bottom(ServerLevel level) {
      return level.getMinY() + 1;
   }

   private static int[][] ring() {
      List<int[]> out = new ArrayList<>();

      for (int x = 1; x <= 14; x++) {
         out.add(new int[]{x, 1});
      }

      for (int z = 2; z <= 14; z++) {
         out.add(new int[]{14, z});
      }

      for (int x = 13; x >= 1; x--) {
         out.add(new int[]{x, 14});
      }

      for (int z = 13; z >= 2; z--) {
         out.add(new int[]{1, z});
      }

      return out.toArray(new int[0][]);
   }

   public static BlockPos stepFloor(BlockPos post, int i) {
      int[] c = RING[i % RING.length];
      int bx = post.getX() >> 4 << 4;
      int bz = post.getZ() >> 4 << 4;
      return new BlockPos(bx + c[0], top(post) - 1 - i, bz + c[1]);
   }

   public static int steps(ServerLevel level, BlockPos post) {
      return Math.max(0, top(post) - 1 - bottom(level));
   }

   public static boolean isStep(BlockPos post, BlockPos p) {
      int i = top(post) - 1 - p.getY();
      return i >= 0 && stepFloor(post, i).equals(p);
   }

   public static boolean inChunk(BlockPos post, BlockPos p) {
      return p.getX() >> 4 == post.getX() >> 4 && p.getZ() >> 4 == post.getZ() >> 4;
   }

   public static boolean inHole(ServerLevel level, BlockPos post, BlockPos p) {
      return inChunk(post, p) && p.getY() <= top(post) + 1 && p.getY() >= level.getMinY();
   }

   public String keep(ServerLevel level, BlockPos p, BlockState s) {
      if (s.isAir()) {
         return "air";
      } else if (s.getDestroySpeed(level, p) < 0.0F || s.is(Blocks.BEDROCK)) {
         return "unbreakable";
      } else if (s.is(Blocks.SPAWNER)
         || s.is(Blocks.TRIAL_SPAWNER)
         || s.is(Blocks.VAULT)
         || s.is(Blocks.END_PORTAL_FRAME)
         || s.is(Blocks.END_PORTAL)
         || s.is(Blocks.NETHER_PORTAL)
         || s.is(Bases.BASE_CORE)
         || s.is(Bases.MINING_POST)) {
         return "kept";
      } else if (PlacedBlocks.isPlaced(level, p)) {
         return "placed by a player";
      } else {
         StructureManager sm = level.structureManager();
         if (sm.getStructureWithPieceAt(p, h -> h.is(BuiltinStructures.STRONGHOLD)).isValid()) {
            return "stronghold";
         } else {
            return !this.structures && sm.getStructureWithPieceAt(p, h -> true).isValid() ? "structure" : null;
         }
      }
   }

   public BlockPos next(ServerLevel level, BlockPos post, BlockPos near, Predicate<BlockPos> free) {
      if (!this.paused && this.phase != 2) {
         if (this.phase == 0) {
            for (int total = steps(level, post); this.step < total; this.step++) {
               BlockPos floor = stepFloor(post, this.step);
               if (!level.isLoaded(floor)) {
                  return null;
               }

               BlockState f = level.getBlockState(floor);
               if ((f.isAir() || !f.getFluidState().isEmpty() || f.getCollisionShape(level, floor).isEmpty()) && !inStructure(level, floor)) {
                  level.setBlockAndUpdate(floor, filler(floor));
               }

               for (int h = 1; h <= 3; h++) {
                  BlockPos head = floor.above(h);
                  if (head.getY() > top(post)) {
                     break;
                  }

                  BlockState s = level.getBlockState(head);
                  if (!s.getFluidState().isEmpty()) {
                     drain(level, head);
                  }

                  sealAround(level, post, head);
                  if (this.keep(level, head, level.getBlockState(head)) == null && free.test(head)) {
                     return head;
                  }

                  if (this.keep(level, head, level.getBlockState(head)) == null) {
                     return null;
                  }
               }
            }

            this.phase = 1;
            this.layer = top(post);
         }

         while (this.layer >= bottom(level)) {
            int bx = post.getX() >> 4 << 4;
            int bz = post.getZ() >> 4 << 4;

            for (int x = 0; x < 16; x++) {
               for (int z = 0; z < 16; z++) {
                  BlockPos p = new BlockPos(bx + x, this.layer, bz + z);
                  boolean wall = x == 0 || x == 15 || z == 0 || z == 15;
                  BlockState sx = level.getBlockState(p);
                  if (wall) {
                     if ((sx.isAir() || !sx.getFluidState().isEmpty() || sx.is(BlockTags.SAND) || sx.is(Blocks.GRAVEL)) && !inStructure(level, p)) {
                        level.setBlockAndUpdate(p, filler(p));
                     }
                  } else if (!sx.getFluidState().isEmpty() && !isStep(post, p) && !inStructure(level, p)) {
                     drain(level, p);
                  }
               }
            }

            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            boolean busy = false;

            for (int x = 1; x <= 14; x++) {
               for (int zx = 1; zx <= 14; zx++) {
                  BlockPos p = new BlockPos(bx + x, this.layer, bz + zx);
                  if (!isStep(post, p) && this.keep(level, p, level.getBlockState(p)) == null) {
                     if (!free.test(p)) {
                        busy = true;
                     } else {
                        double d = p.distSqr(near);
                        if (d < bestD) {
                           best = p;
                           bestD = d;
                        }
                     }
                  }
               }
            }

            if (best != null) {
               return best;
            }

            if (busy) {
               return null;
            }

            this.layer--;
         }

         this.phase = 2;
         return null;
      } else {
         return null;
      }
   }

   static boolean inStructure(ServerLevel level, BlockPos p) {
      return level.structureManager().getStructureWithPieceAt(p, h -> true).isValid();
   }

   static BlockState filler(BlockPos p) {
      return p.getY() < 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
   }

   private static void drain(ServerLevel level, BlockPos p) {
      BlockState s = level.getBlockState(p);
      if (s.hasProperty(BlockStateProperties.WATERLOGGED)) {
         level.setBlockAndUpdate(p, (BlockState)s.setValue(BlockStateProperties.WATERLOGGED, false));
      } else if (s.getBlock() instanceof LiquidBlock || s.is(Blocks.BUBBLE_COLUMN)) {
         level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
      }
   }

   private static void sealAround(ServerLevel level, BlockPos post, BlockPos p) {
      for (Direction d : Direction.values()) {
         BlockPos n = p.relative(d);
         if (!level.getBlockState(n).getFluidState().isEmpty() && (!inChunk(post, n) || isWall(post, n))) {
            level.setBlockAndUpdate(n, filler(n));
         }
      }
   }

   private static boolean isWall(BlockPos post, BlockPos p) {
      int x = p.getX() & 15;
      int z = p.getZ() & 15;
      return x == 0 || x == 15 || z == 0 || z == 15;
   }

   public static List<Container> chests(ServerLevel level, BlockPos post) {
      List<Container> out = new ArrayList<>();

      for (Direction d : Direction.values()) {
         Container c = HopperBlockEntity.getContainerAt(level, post.relative(d));
         if (c != null && !(c instanceof AbstractFurnaceBlockEntity) && !out.contains(c)) {
            out.add(c);
         }
      }

      return out;
   }

   static List<ItemStack> drops(ServerLevel level, BlockPos p, BlockState s, boolean silkTouch) {
      ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
      if (silkTouch) {
         level.registryAccess().lookup(Registries.ENCHANTMENT).flatMap(l -> l.get(Enchantments.SILK_TOUCH)).ifPresent(e -> tool.enchant(e, 1));
      }

      BlockEntity be = s.hasBlockEntity() ? level.getBlockEntity(p) : null;
      List<ItemStack> out = new ArrayList<>(Block.getDrops(s, level, p, be, null, tool));
      if (be instanceof Container c) {
         for (int i = 0; i < c.getContainerSize(); i++) {
            if (!c.getItem(i).isEmpty()) {
               out.add(c.getItem(i).copy());
            }
         }
      }

      out.removeIf(ItemStack::isEmpty);
      return out;
   }

   static boolean fits(List<Container> chests, List<ItemStack> stacks) {
      List<ItemStack[]> slots = new ArrayList<>();

      for (Container c : chests) {
         ItemStack[] copy = new ItemStack[c.getContainerSize()];

         for (int i = 0; i < copy.length; i++) {
            copy[i] = c.getItem(i).copy();
         }

         slots.add(copy);
      }

      for (ItemStack s : stacks) {
         int left = s.getCount();

         for (ItemStack[] chest : slots) {
            for (int i = 0; i < chest.length && left > 0; i++) {
               if (chest[i].isEmpty()) {
                  int n = Math.min(left, s.getMaxStackSize());
                  chest[i] = s.copyWithCount(n);
                  left -= n;
               } else if (ItemStack.isSameItemSameComponents(chest[i], s)) {
                  int n = Math.min(left, chest[i].getMaxStackSize() - chest[i].getCount());
                  chest[i].grow(n);
                  left -= n;
               }
            }
         }

         if (left > 0) {
            return false;
         }
      }

      return true;
   }

   static void store(List<Container> chests, List<ItemStack> stacks) {
      for (ItemStack s : stacks) {
         ItemStack left = s.copy();

         for (Container c : chests) {
            left = HopperBlockEntity.addItem(null, c, left, null);
            if (left.isEmpty()) {
               break;
            }
         }
      }
   }

   public int percent(ServerLevel level, BlockPos post) {
      if (this.phase == 2) {
         return 100;
      } else {
         int depth = Math.max(1, top(post) - bottom(level) + 1);
         return this.phase == 0
            ? (int)(10L * (long)this.step / (long)Math.max(1, steps(level, post)))
            : 10 + (int)(90L * (long)(top(post) - this.layer) / (long)depth);
      }
   }

   public String describe(ServerLevel level, BlockPos post) {
      if (this.paused) {
         return "Paused";
      } else if (this.needsChest) {
         return "Stopped: the chests by the post are full. Put another chest next to it.";
      } else {
         return switch (this.phase) {
            case 0 -> "Cutting the staircase: step " + this.step + " of " + steps(level, post);
            case 1 -> "Clearing layer y=" + this.layer + " (" + this.percent(level, post) + "% done)";
            default -> "Done: dug down to bedrock";
         };
      }
   }
}
