package dev.ahrivaea.battle;

import dev.ahrivaea.Ahrivaea;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;

public final class Arena {
   public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Ahrivaea.id("battle_arena"));
   public static final int MIN_FIELD = 7;
   public static final int MAX_FIELD = 13;
   public static final int BORDER = 6;
   public static final int GROUND = 64;
   public static final int SPACING = 1024;

   private Arena() {
   }

   public static BlockPos center(int slot) {
      return new BlockPos(slot * 1024, 64, 0);
   }

   public static BlockPos build(ServerLevel level, int slot, Arena.Theme theme, Holder<Biome> biome, long seed, int field) {
      BlockPos c = center(slot);
      RandomSource rnd = RandomSource.create(seed);
      int r = field + 6;

      for (Entity e : level.getEntities((Entity)null, new AABB(c).inflate((double)(r + 4), 40.0, (double)(r + 4)), ex -> !(ex instanceof Player))) {
         e.discard();
      }

      BlockState air = Blocks.AIR.defaultBlockState();

      for (int x = -r - 2; x <= r + 2; x++) {
         for (int z = -r - 2; z <= r + 2; z++) {
            for (int y = -14; y <= 30; y++) {
               set(level, c.offset(x, y, z), air);
            }
         }
      }

      int[][] h = new int[2 * r + 1][2 * r + 1];
      int hills = theme.hills == 0 ? 0 : 1 + rnd.nextInt(theme.hills + 1);

      for (int i = 0; i < hills; i++) {
         int hx = rnd.nextInt(2 * field + 1) - field;
         int hz = rnd.nextInt(2 * field + 1) - field;
         int height = 1 + rnd.nextInt(Math.max(1, theme.hills));
         double rad = 2.5 + rnd.nextDouble() * 3.0;

         for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
               double d = Math.sqrt((double)((x - hx) * (x - hx) + (z - hz) * (z - hz)));
               int lift = (int)Math.floor((double)height * Math.max(0.0, 1.0 - d / (rad + (double)height)) + 0.4);
               h[x + r][z + r] = Math.max(h[x + r][z + r], lift);
            }
         }
      }

      for (int x = -r; x <= r; x++) {
         for (int z = -r; z <= r; z++) {
            if (Math.abs(z) >= field - 4) {
               h[x + r][z + r] = 0;
            }
         }
      }

      for (int x = -r; x <= r; x++) {
         for (int zx = -r; zx <= r; zx++) {
            int top = h[x + r][zx + r];

            for (int y = -12; y < top - 1; y++) {
               set(level, c.offset(x, y, zx), theme.under.defaultBlockState());
            }

            set(level, c.offset(x, top - 1, zx), theme.ground.defaultBlockState());
         }
      }
      int pools = switch (theme.liquid) {
         case NONE -> 0;
         default -> 1 + rnd.nextInt(2);
         case SWAMP, SHALLOWS -> 4 + rnd.nextInt(3);
      };

      for (int i = 0; i < pools; i++) {
         int px = rnd.nextInt(2 * field - 3) - field + 2;
         int pz = rnd.nextInt(2 * field - 9) - field + 5;
         int size = theme.liquid == Arena.Liquid.SHALLOWS ? 3 + rnd.nextInt(3) : 1 + rnd.nextInt(2);

         for (int x = -size; x <= size; x++) {
            for (int zx = -size; zx <= size; zx++) {
               if (x * x + zx * zx <= size * size + 1) {
                  int ax = px + x;
                  int az = pz + zx;
                  if (Math.abs(ax) <= field && Math.abs(az) <= field - 5) {
                     int top = h[ax + r][az + r];
                     BlockPos surface = c.offset(ax, top - 1, az);

                     BlockState liquid = switch (theme.liquid) {
                        case ICE -> Blocks.ICE.defaultBlockState();
                        case LAVA -> Blocks.LAVA.defaultBlockState();
                        default -> Blocks.WATER.defaultBlockState();
                     };
                     set(level, surface, liquid);
                     if (theme.liquid != Arena.Liquid.ICE && size >= 2 && x * x + zx * zx <= 1) {
                        set(level, surface.below(), liquid);
                     }

                     if (theme.liquid == Arena.Liquid.OASIS && rnd.nextInt(4) == 0 && x * x + zx * zx == size * size) {
                        set(level, surface.above(), Blocks.SUGAR_CANE.defaultBlockState());
                     }
                  }
               }
            }
         }
      }

      int count = (6 + rnd.nextInt(6)) * field * field / 100;

      for (int i = 0; i < count; i++) {
         int ox = rnd.nextInt(2 * field + 1) - field;
         int oz = rnd.nextInt(2 * field - 9) - field + 5;
         BlockPos base = c.offset(ox, h[ox + r][oz + r], oz);
         if (level.getBlockState(base.below()).isSolid() && level.getBlockState(base).isAir()) {
            obstacle(level, base, theme.obstacle, rnd);
         }
      }

      for (int x = -field; x <= field; x++) {
         for (int zxx = -field; zxx <= field; zxx++) {
            BlockPos p = c.offset(x, h[x + r][zxx + r], zxx);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isSolid() && rnd.nextInt(5) == 0) {
               BlockState d = decor(theme.decor, rnd);
               if (d != null && d.canSurvive(level, p)) {
                  set(level, p, d);
               }
            }
         }
      }

      for (int x = -r; x <= r; x++) {
         for (int zxxx = -r; zxxx <= r; zxxx++) {
            int ring = Math.max(Math.abs(x), Math.abs(zxxx));
            if (ring > field) {
               border(level, c.offset(x, h[x + r][zxxx + r], zxxx), theme.border, ring - field, rnd);
            }
         }
      }

      if (biome != null) {
         try {
            FillBiomeCommand.fill(level, c.offset(-r - 2, -14, -r - 2), c.offset(r + 2, 30, r + 2), biome);
         } catch (RuntimeException var25) {
            Ahrivaea.LOGGER.warn("Couldn't colour the arena", var25);
         }
      }

      return c;
   }

   private static void set(ServerLevel level, BlockPos p, BlockState s) {
      level.setBlock(p, s, 18);
   }

   private static void column(ServerLevel level, BlockPos base, BlockState s, int height) {
      for (int i = 0; i < height; i++) {
         set(level, base.above(i), s);
      }
   }

   private static void obstacle(ServerLevel level, BlockPos p, Arena.Obstacle o, RandomSource rnd) {
      int tall = 1 + rnd.nextInt(2);
      switch (o) {
         case BOULDER:
            set(level, p, (rnd.nextBoolean() ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState());
            if (rnd.nextBoolean()) {
               set(level, p.above(), Blocks.COBBLESTONE.defaultBlockState());
            }
            break;
         case OAK:
            column(level, p, Blocks.OAK_LOG.defaultBlockState(), tall);
            break;
         case BIRCH:
            column(level, p, Blocks.BIRCH_LOG.defaultBlockState(), tall);
            break;
         case DARK_OAK:
            column(level, p, Blocks.DARK_OAK_LOG.defaultBlockState(), tall);
            break;
         case CHERRY:
            column(level, p, Blocks.CHERRY_LOG.defaultBlockState(), tall);
            break;
         case SPRUCE:
            column(level, p, Blocks.SPRUCE_LOG.defaultBlockState(), tall);
            break;
         case ICE_SPIKE:
            column(level, p, Blocks.PACKED_ICE.defaultBlockState(), 1 + rnd.nextInt(3));
            break;
         case CACTUS:
            if (level.getBlockState(p.below()).is(Blocks.SAND)) {
               column(level, p, Blocks.CACTUS.defaultBlockState(), 1 + rnd.nextInt(2));
            }
            break;
         case MESA:
            column(
               level, p, (rnd.nextBoolean() ? (Block)Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE) : Blocks.TERRACOTTA).defaultBlockState(), 2 + rnd.nextInt(2)
            );
            break;
         case ACACIA:
            column(level, p, Blocks.ACACIA_LOG.defaultBlockState(), tall);
            break;
         case JUNGLE:
            column(level, p, Blocks.JUNGLE_LOG.defaultBlockState(), tall);
            set(level, p.above(tall), Blocks.JUNGLE_LEAVES.defaultBlockState());
            break;
         case MANGROVE:
            column(level, p, Blocks.MANGROVE_ROOTS.defaultBlockState(), tall);
            break;
         case CORAL:
            set(level, p, Blocks.SANDSTONE.defaultBlockState());
            break;
         case MUSHROOM:
            column(level, p, Blocks.MUSHROOM_STEM.defaultBlockState(), 2);
            set(level, p.above(2), (rnd.nextBoolean() ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK).defaultBlockState());
            break;
         case DRIPSTONE:
            column(level, p, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), 1 + rnd.nextInt(3));
            break;
         case AZALEA:
            set(level, p, (rnd.nextBoolean() ? Blocks.AZALEA_LEAVES : Blocks.FLOWERING_AZALEA_LEAVES).defaultBlockState());
            break;
         case DEEPSLATE_PILLAR:
            column(level, p, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 2 + rnd.nextInt(2));
            break;
         case NETHER_PILLAR:
            column(level, p, (rnd.nextBoolean() ? Blocks.NETHER_BRICKS : Blocks.NETHERRACK).defaultBlockState(), 2 + rnd.nextInt(2));
            break;
         case BONE:
            column(level, p, Blocks.BONE_BLOCK.defaultBlockState(), 1 + rnd.nextInt(2));
            break;
         case CRIMSON:
            column(level, p, Blocks.CRIMSON_STEM.defaultBlockState(), tall);
            set(level, p.above(tall), Blocks.NETHER_WART_BLOCK.defaultBlockState());
            break;
         case WARPED:
            column(level, p, Blocks.WARPED_STEM.defaultBlockState(), tall);
            set(level, p.above(tall), Blocks.WARPED_WART_BLOCK.defaultBlockState());
            break;
         case BASALT_PILLAR:
            column(level, p, Blocks.BASALT.defaultBlockState(), 2 + rnd.nextInt(3));
            break;
         case OBSIDIAN:
            column(level, p, Blocks.OBSIDIAN.defaultBlockState(), 2 + rnd.nextInt(2));
      }
   }

   private static BlockState decor(Arena.Decor d, RandomSource rnd) {
      List<Block> pick = switch (d) {
         case NONE -> List.of();
         case MEADOW -> List.of(Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.DANDELION, Blocks.POPPY);
         case FLOWERS -> List.of(Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.SHORT_GRASS);
         case MUSHROOMS -> List.of(Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM, Blocks.SHORT_GRASS);
         case PETALS -> List.of(Blocks.PINK_PETALS, Blocks.PINK_PETALS, Blocks.SHORT_GRASS);
         case FERNS -> List.of(Blocks.FERN, Blocks.SHORT_GRASS, Blocks.SWEET_BERRY_BUSH);
         case SNOW -> List.of(Blocks.SNOW);
         case DESERT -> List.of(Blocks.DEAD_BUSH);
         case DRY_GRASS -> List.of(Blocks.SHORT_GRASS, Blocks.DEAD_BUSH);
         case JUNGLE -> List.of(Blocks.FERN, Blocks.SHORT_GRASS, Blocks.MELON);
         case SWAMP -> List.of(Blocks.BLUE_ORCHID, Blocks.SHORT_GRASS);
         case GRAVEL -> List.of();
         case BEACH -> List.of();
         case LUSH -> List.of(Blocks.MOSS_CARPET, Blocks.AZALEA);
         case NETHER -> List.of(Blocks.CRIMSON_ROOTS);
         case SOUL -> List.of();
         case CRIMSON -> List.of(Blocks.CRIMSON_ROOTS, Blocks.CRIMSON_FUNGUS);
         case WARPED -> List.of(Blocks.WARPED_ROOTS, Blocks.WARPED_FUNGUS, Blocks.NETHER_SPROUTS);
      };
      if (pick.isEmpty()) {
         return null;
      } else {
         Block b = pick.get(rnd.nextInt(pick.size()));
         return b == Blocks.MELON ? null : b.defaultBlockState();
      }
   }

   private static void border(ServerLevel level, BlockPos p, Arena.Border b, int depth, RandomSource rnd) {
      switch (b) {
         case WOODS:
         case BIRCH_WOODS:
         case DARK_WOODS:
         case CHERRY_WOODS:
         case SPRUCE_WOODS:
         case JUNGLE_WOODS:
         case ACACIA_WOODS:
         case SWAMP_WOODS:
            Block log = switch (b) {
               case BIRCH_WOODS -> Blocks.BIRCH_LOG;
               case DARK_WOODS -> Blocks.DARK_OAK_LOG;
               case CHERRY_WOODS -> Blocks.CHERRY_LOG;
               case SPRUCE_WOODS -> Blocks.SPRUCE_LOG;
               case JUNGLE_WOODS -> Blocks.JUNGLE_LOG;
               case ACACIA_WOODS -> Blocks.ACACIA_LOG;
               case SWAMP_WOODS -> Blocks.MANGROVE_LOG;
               default -> Blocks.OAK_LOG;
            };

            Block leaves = switch (b) {
               case BIRCH_WOODS -> Blocks.BIRCH_LEAVES;
               case DARK_WOODS -> Blocks.DARK_OAK_LEAVES;
               case CHERRY_WOODS -> Blocks.CHERRY_LEAVES;
               case SPRUCE_WOODS -> Blocks.SPRUCE_LEAVES;
               case JUNGLE_WOODS -> Blocks.JUNGLE_LEAVES;
               case ACACIA_WOODS -> Blocks.ACACIA_LEAVES;
               case SWAMP_WOODS -> Blocks.MANGROVE_LEAVES;
               default -> Blocks.OAK_LEAVES;
            };
            if (depth >= 2 && rnd.nextInt(3) == 0) {
               int t = 3 + rnd.nextInt(3);
               column(level, p, log.defaultBlockState(), t);

               for (int dx = -1; dx <= 1; dx++) {
                  for (int dz = -1; dz <= 1; dz++) {
                     for (int dy = t - 1; dy <= t; dy++) {
                        BlockPos q = p.offset(dx, dy, dz);
                        if (level.getBlockState(q).isAir()) {
                           set(level, q, (BlockState)leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
                        }
                     }
                  }
               }
            } else if (rnd.nextInt(2) == 0) {
               set(level, p, (BlockState)leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
            }
            break;
         case HILLS:
         case SNOW_CLIFFS:
         case DUNES:
         case MESA_CLIFFS:
         case STONE_CLIFFS:
         case CAVE_WALLS:
         case DEEPSLATE_WALLS:
         case NETHER_WALLS:
         case BASALT_WALLS:
            Block wall = switch (b) {
               case HILLS -> Blocks.GRASS_BLOCK;
               case SNOW_CLIFFS -> Blocks.SNOW_BLOCK;
               case DUNES -> Blocks.SAND;
               case MESA_CLIFFS -> depth % 2 == 0 ? (Block)Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE) : Blocks.TERRACOTTA;
               case STONE_CLIFFS -> Blocks.STONE;
               default -> Blocks.BASALT;
               case CAVE_WALLS -> Blocks.STONE;
               case DEEPSLATE_WALLS -> Blocks.DEEPSLATE;
               case NETHER_WALLS -> Blocks.NETHERRACK;
            };
            int height = (b != Arena.Border.DUNES && b != Arena.Border.HILLS ? depth * 2 : depth) + rnd.nextInt(2);
            column(level, p, (b == Arena.Border.HILLS ? Blocks.DIRT : wall).defaultBlockState(), height);
            if (b == Arena.Border.HILLS) {
               set(level, p.above(height - 1), Blocks.GRASS_BLOCK.defaultBlockState());
            }

            if ((b == Arena.Border.CAVE_WALLS || b == Arena.Border.DEEPSLATE_WALLS) && rnd.nextInt(6) == 0) {
               set(level, p.above(height), Blocks.GLOW_LICHEN.defaultBlockState());
            }
            break;
         case SEA:
            set(level, p.below(), Blocks.WATER.defaultBlockState());
            set(level, p.below(2), Blocks.WATER.defaultBlockState());
            break;
         case MUSHROOM_GROVE:
            if (depth >= 2 && rnd.nextInt(4) == 0) {
               column(level, p, Blocks.MUSHROOM_STEM.defaultBlockState(), 4);

               for (int dx = -1; dx <= 1; dx++) {
                  for (int dz = -1; dz <= 1; dz++) {
                     set(level, p.offset(dx, 4, dz), Blocks.RED_MUSHROOM_BLOCK.defaultBlockState());
                  }
               }
            }
            break;
         case END_PILLARS:
            if (depth >= 3 && rnd.nextInt(8) == 0) {
               column(level, p, Blocks.OBSIDIAN.defaultBlockState(), 6 + rnd.nextInt(5));
            }
      }
   }

   static enum Border {
      WOODS,
      BIRCH_WOODS,
      DARK_WOODS,
      CHERRY_WOODS,
      SPRUCE_WOODS,
      JUNGLE_WOODS,
      ACACIA_WOODS,
      SWAMP_WOODS,
      HILLS,
      SNOW_CLIFFS,
      DUNES,
      MESA_CLIFFS,
      STONE_CLIFFS,
      SEA,
      MUSHROOM_GROVE,
      CAVE_WALLS,
      DEEPSLATE_WALLS,
      NETHER_WALLS,
      BASALT_WALLS,
      END_PILLARS;
   }

   static enum Decor {
      NONE,
      MEADOW,
      FLOWERS,
      MUSHROOMS,
      PETALS,
      FERNS,
      SNOW,
      DESERT,
      DRY_GRASS,
      JUNGLE,
      SWAMP,
      GRAVEL,
      BEACH,
      LUSH,
      NETHER,
      SOUL,
      CRIMSON,
      WARPED;
   }

   static enum Liquid {
      NONE,
      WATER,
      OASIS,
      SWAMP,
      SHALLOWS,
      ICE,
      LAVA;
   }

   static enum Obstacle {
      BOULDER,
      OAK,
      BIRCH,
      DARK_OAK,
      CHERRY,
      SPRUCE,
      ICE_SPIKE,
      CACTUS,
      MESA,
      ACACIA,
      JUNGLE,
      MANGROVE,
      CORAL,
      MUSHROOM,
      DRIPSTONE,
      AZALEA,
      DEEPSLATE_PILLAR,
      NETHER_PILLAR,
      BONE,
      CRIMSON,
      WARPED,
      BASALT_PILLAR,
      OBSIDIAN;
   }

   public static enum Theme {
      PLAINS(Blocks.GRASS_BLOCK, Blocks.DIRT, 2, Arena.Liquid.WATER, Arena.Obstacle.BOULDER, Arena.Decor.MEADOW, Arena.Border.WOODS),
      MEADOW(Blocks.GRASS_BLOCK, Blocks.DIRT, 2, Arena.Liquid.NONE, Arena.Obstacle.BOULDER, Arena.Decor.FLOWERS, Arena.Border.HILLS),
      FOREST(Blocks.GRASS_BLOCK, Blocks.DIRT, 1, Arena.Liquid.WATER, Arena.Obstacle.OAK, Arena.Decor.MEADOW, Arena.Border.WOODS),
      BIRCH_FOREST(Blocks.GRASS_BLOCK, Blocks.DIRT, 1, Arena.Liquid.NONE, Arena.Obstacle.BIRCH, Arena.Decor.FLOWERS, Arena.Border.BIRCH_WOODS),
      DARK_FOREST(Blocks.PODZOL, Blocks.DIRT, 1, Arena.Liquid.NONE, Arena.Obstacle.DARK_OAK, Arena.Decor.MUSHROOMS, Arena.Border.DARK_WOODS),
      CHERRY(Blocks.GRASS_BLOCK, Blocks.DIRT, 2, Arena.Liquid.WATER, Arena.Obstacle.CHERRY, Arena.Decor.PETALS, Arena.Border.CHERRY_WOODS),
      TAIGA(Blocks.PODZOL, Blocks.DIRT, 2, Arena.Liquid.WATER, Arena.Obstacle.SPRUCE, Arena.Decor.FERNS, Arena.Border.SPRUCE_WOODS),
      SNOWY(Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, 2, Arena.Liquid.ICE, Arena.Obstacle.ICE_SPIKE, Arena.Decor.SNOW, Arena.Border.SNOW_CLIFFS),
      DESERT(Blocks.SAND, Blocks.SANDSTONE, 2, Arena.Liquid.OASIS, Arena.Obstacle.CACTUS, Arena.Decor.DESERT, Arena.Border.DUNES),
      BADLANDS(Blocks.RED_SAND, Blocks.TERRACOTTA, 3, Arena.Liquid.NONE, Arena.Obstacle.MESA, Arena.Decor.DESERT, Arena.Border.MESA_CLIFFS),
      SAVANNA(Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, 2, Arena.Liquid.NONE, Arena.Obstacle.ACACIA, Arena.Decor.DRY_GRASS, Arena.Border.ACACIA_WOODS),
      JUNGLE(Blocks.GRASS_BLOCK, Blocks.DIRT, 2, Arena.Liquid.WATER, Arena.Obstacle.JUNGLE, Arena.Decor.JUNGLE, Arena.Border.JUNGLE_WOODS),
      SWAMP(Blocks.MUD, Blocks.MUD, 1, Arena.Liquid.SWAMP, Arena.Obstacle.MANGROVE, Arena.Decor.SWAMP, Arena.Border.SWAMP_WOODS),
      MOUNTAIN(Blocks.STONE, Blocks.STONE, 4, Arena.Liquid.NONE, Arena.Obstacle.BOULDER, Arena.Decor.GRAVEL, Arena.Border.STONE_CLIFFS),
      OCEAN(Blocks.SAND, Blocks.SANDSTONE, 1, Arena.Liquid.SHALLOWS, Arena.Obstacle.CORAL, Arena.Decor.BEACH, Arena.Border.SEA),
      MUSHROOM(Blocks.MYCELIUM, Blocks.DIRT, 2, Arena.Liquid.NONE, Arena.Obstacle.MUSHROOM, Arena.Decor.MUSHROOMS, Arena.Border.MUSHROOM_GROVE),
      CAVE(Blocks.STONE, Blocks.DEEPSLATE, 3, Arena.Liquid.LAVA, Arena.Obstacle.DRIPSTONE, Arena.Decor.GRAVEL, Arena.Border.CAVE_WALLS),
      LUSH_CAVE(Blocks.MOSS_BLOCK, Blocks.STONE, 2, Arena.Liquid.WATER, Arena.Obstacle.AZALEA, Arena.Decor.LUSH, Arena.Border.CAVE_WALLS),
      DEEP_DARK(Blocks.DEEPSLATE, Blocks.DEEPSLATE, 2, Arena.Liquid.NONE, Arena.Obstacle.DEEPSLATE_PILLAR, Arena.Decor.NONE, Arena.Border.DEEPSLATE_WALLS),
      NETHER(Blocks.NETHERRACK, Blocks.NETHERRACK, 3, Arena.Liquid.LAVA, Arena.Obstacle.NETHER_PILLAR, Arena.Decor.NETHER, Arena.Border.NETHER_WALLS),
      SOUL_VALLEY(Blocks.SOUL_SAND, Blocks.SOUL_SOIL, 2, Arena.Liquid.LAVA, Arena.Obstacle.BONE, Arena.Decor.SOUL, Arena.Border.BASALT_WALLS),
      CRIMSON(Blocks.CRIMSON_NYLIUM, Blocks.NETHERRACK, 2, Arena.Liquid.LAVA, Arena.Obstacle.CRIMSON, Arena.Decor.CRIMSON, Arena.Border.NETHER_WALLS),
      WARPED(Blocks.WARPED_NYLIUM, Blocks.NETHERRACK, 2, Arena.Liquid.LAVA, Arena.Obstacle.WARPED, Arena.Decor.WARPED, Arena.Border.NETHER_WALLS),
      BASALT(Blocks.BASALT, Blocks.BLACKSTONE, 3, Arena.Liquid.LAVA, Arena.Obstacle.BASALT_PILLAR, Arena.Decor.NONE, Arena.Border.BASALT_WALLS),
      END(Blocks.END_STONE, Blocks.END_STONE, 2, Arena.Liquid.NONE, Arena.Obstacle.OBSIDIAN, Arena.Decor.NONE, Arena.Border.END_PILLARS);

      final Block ground;
      final Block under;
      final int hills;
      final Arena.Liquid liquid;
      final Arena.Obstacle obstacle;
      final Arena.Decor decor;
      final Arena.Border border;

      private Theme(Block ground, Block under, int hills, Arena.Liquid liquid, Arena.Obstacle obstacle, Arena.Decor decor, Arena.Border border) {
         this.ground = ground;
         this.under = under;
         this.hills = hills;
         this.liquid = liquid;
         this.obstacle = obstacle;
         this.decor = decor;
         this.border = border;
      }

      public int baseField() {
         return switch (this) {
            case PLAINS, MEADOW, SNOWY, DESERT, BADLANDS, SAVANNA, MOUNTAIN, OCEAN, BASALT, END -> 12;
            default -> 10;
            case CAVE, LUSH_CAVE, DEEP_DARK -> 8;
            case NETHER, SOUL_VALLEY, CRIMSON, WARPED -> 9;
         };
      }

      public String sampleBiome() {
         return switch (this) {
            case PLAINS -> "plains";
            case MEADOW -> "meadow";
            case FOREST -> "forest";
            case BIRCH_FOREST -> "birch_forest";
            case DARK_FOREST -> "dark_forest";
            case CHERRY -> "cherry_grove";
            case TAIGA -> "taiga";
            case SNOWY -> "snowy_plains";
            case DESERT -> "desert";
            case BADLANDS -> "badlands";
            case SAVANNA -> "savanna";
            case JUNGLE -> "jungle";
            case SWAMP -> "mangrove_swamp";
            case MOUNTAIN -> "stony_peaks";
            case OCEAN -> "beach";
            case MUSHROOM -> "mushroom_fields";
            case CAVE -> "dripstone_caves";
            case LUSH_CAVE -> "lush_caves";
            case DEEP_DARK -> "deep_dark";
            case NETHER -> "nether_wastes";
            case SOUL_VALLEY -> "soul_sand_valley";
            case CRIMSON -> "crimson_forest";
            case WARPED -> "warped_forest";
            case BASALT -> "basalt_deltas";
            case END -> "the_end";
         };
      }

      public static Arena.Theme of(ServerLevel level, BlockPos pos) {
         Holder<Biome> b = level.getBiome(pos);
         String id = b.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
         if (b.is(BiomeTags.IS_NETHER)) {
            return switch (id) {
               case "soul_sand_valley" -> SOUL_VALLEY;
               case "crimson_forest" -> CRIMSON;
               case "warped_forest" -> WARPED;
               case "basalt_deltas" -> BASALT;
               default -> NETHER;
            };
         } else if (b.is(BiomeTags.IS_END)) {
            return END;
         } else {
            switch (id) {
               case "deep_dark":
                  return DEEP_DARK;
               case "lush_caves":
                  return LUSH_CAVE;
               case "dripstone_caves":
                  return CAVE;
               default:
                  if (pos.getY() < level.getHeight(Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - 6 && pos.getY() < level.getSeaLevel() - 8) {
                     return CAVE;
                  } else if (id.contains("mushroom")) {
                     return MUSHROOM;
                  } else if (id.contains("cherry")) {
                     return CHERRY;
                  } else if (id.contains("dark_forest") || id.contains("pale_garden")) {
                     return DARK_FOREST;
                  } else if (id.contains("birch")) {
                     return BIRCH_FOREST;
                  } else if (id.contains("swamp")) {
                     return SWAMP;
                  } else if (b.is(BiomeTags.IS_JUNGLE)) {
                     return JUNGLE;
                  } else if (b.is(BiomeTags.IS_BADLANDS)) {
                     return BADLANDS;
                  } else if (id.contains("desert")) {
                     return DESERT;
                  } else if (b.is(BiomeTags.IS_SAVANNA)) {
                     return SAVANNA;
                  } else if (id.contains("snow") || id.contains("frozen") || id.contains("ice") || id.contains("grove") || id.contains("jagged")) {
                     return SNOWY;
                  } else if (b.is(BiomeTags.IS_TAIGA)) {
                     return TAIGA;
                  } else if (b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_BEACH) || b.is(BiomeTags.IS_RIVER)) {
                     return OCEAN;
                  } else if (id.contains("peaks") || id.contains("windswept") || id.contains("stony") || id.contains("slopes")) {
                     return MOUNTAIN;
                  } else if (id.contains("meadow") || id.contains("flower") || id.contains("sunflower")) {
                     return MEADOW;
                  } else {
                     return b.is(BiomeTags.IS_FOREST) ? FOREST : PLAINS;
                  }
            }
         }
      }
   }
}
