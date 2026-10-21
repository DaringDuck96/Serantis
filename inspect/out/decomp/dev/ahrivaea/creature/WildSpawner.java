package dev.ahrivaea.creature;

import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.character.PlayerCharacter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WildSpawner {
   public static int CAP = 5;
   public static final int RANGE = 80;
   public static final int MIN_DISTANCE = 24;
   public static final int MAX_DISTANCE = 56;
   public static final int EVERY_TICKS = 20;
   public static final int IDLE_SECONDS = 90;
   public static final double MAX_TRAVEL_PER_SECOND = 60.0;
   public static final GameRule<Integer> DISTANCE = GameRuleBuilder.forInteger(50)
      .minValue(1)
      .category(GameRuleCategory.SPAWNING)
      .buildAndRegister(Ahrivaea.id("wild_spawn_distance"));
   private static final Map<UUID, WildSpawner.Travel> TRAVEL = new HashMap<>();
   public static final List<BiPredicate<ServerLevel, BlockPos>> BLOCKED = new ArrayList<>();
   public static final List<ResourceKey<Level>> SKIP = new ArrayList<>();
   public static final Map<String, Integer> FAILURES = new TreeMap<>();

   private WildSpawner() {
   }

   private static CreatureEntity fail(String why) {
      FAILURES.merge(why, 1, Integer::sum);
      return null;
   }

   public static void init() {
      ServerTickEvents.END_SERVER_TICK.register(WildSpawner::tick);
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(h, server) -> TRAVEL.remove(h.getPlayer().getUUID()));
   }

   private static void tick(MinecraftServer server) {
      if (server.getTickCount() % 20 == 0) {
         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isSpectator()) {
               ServerLevel level = p.level();
               if (!SKIP.contains(level.dimension()) && (Boolean)level.getGameRules().get(GameRules.SPAWN_MOBS)) {
                  WildSpawner.Travel t = TRAVEL.get(p.getUUID());
                  double moved = 0.0;
                  if (t != null && t.dimension() == level.dimension()) {
                     double d = Math.hypot(p.getX() - t.last().x, p.getZ() - t.last().z);
                     if (d <= 60.0) {
                        moved = d;
                     }
                  }

                  double credit = (t == null ? 0.0 : t.credit())
                     + moved / (double)((Integer)level.getGameRules().get(DISTANCE)).intValue()
                     + 0.011111111111111112;
                  if (credit >= 1.0) {
                     int wild = level.getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(80.0), c -> !c.isTame()).size();
                     if (wild >= CAP) {
                        credit = 1.0;
                     } else if (trySpawnNear(level, p, level.getRandom()) != null) {
                        credit--;
                     }
                  }

                  TRAVEL.put(p.getUUID(), new WildSpawner.Travel(Math.min(credit, 2.0), p.position(), level.dimension()));
               }
            }
         }
      }
   }

   public static CreatureEntity trySpawnNear(ServerLevel level, ServerPlayer p, RandomSource rnd) {
      double angle = rnd.nextDouble() * Math.PI * 2.0;
      int dist = 24 + rnd.nextInt(32);
      int x = p.getBlockX() + (int)(Math.cos(angle) * (double)dist);
      int z = p.getBlockZ() + (int)(Math.sin(angle) * (double)dist);
      BlockPos column = new BlockPos(x, p.getBlockY(), z);
      if (!level.isLoaded(column)) {
         return fail("not loaded");
      } else {
         int surface = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
         int playerSurface = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, p.getBlockX(), p.getBlockZ());
         boolean underground = level.dimensionType().hasCeiling() || p.getBlockY() < playerSurface - 8;
         BlockPos at;
         boolean water;
         if (underground) {
            at = null;

            for (int dy = 12; dy >= -12 && at == null; dy--) {
               BlockPos q = new BlockPos(x, p.getBlockY() + dy, z);
               if (standable(level, q)) {
                  at = q;
               }
            }

            if (at == null) {
               return fail("no cave floor");
            }

            water = false;
         } else {
            at = new BlockPos(x, surface, z);
            water = level.getFluidState(at.below()).is(FluidTags.WATER);
            if (water) {
               at = at.below();
            } else if (!standable(level, at)) {
               return fail("can't stand");
            }
         }

         if (!level.getFluidState(at).is(FluidTags.LAVA)
            && level.getNearestPlayer((double)at.getX(), (double)at.getY(), (double)at.getZ(), 20.0, false) == null) {
            BlockPos spot = at;

            for (BiPredicate<ServerLevel, BlockPos> blocked : BLOCKED) {
               if (blocked.test(level, spot)) {
                  return fail("blocked");
               }
            }

            boolean inWater = water;
            int me = PlayerCharacter.get(p).level();
            Optional<Species.LevelledPick> pick = Species.pickForLevels(
               level.getBiome(spot), level, rnd, me - 5, me + 5, (s, inf) -> inWater == (s.movement() == Species.Movement.SWIM)
            );
            if (pick.isEmpty()) {
               return fail(
                  "nothing near level "
                     + me
                     + " lives in "
                     + level.getBiome(spot).unwrapKey().map(k -> k.identifier().toString()).orElse("?")
                     + (inWater ? " water" : "")
               );
            } else {
               CreatureEntity e = Ahrivaea.spawn(
                  level,
                  pick.get().species(),
                  pick.get().influence(),
                  pick.get().level(),
                  -1.0F,
                  (double)spot.getX() + 0.5,
                  (double)spot.getY(),
                  (double)spot.getZ() + 0.5
               );
               if (e == null) {
                  return fail("couldn't create");
               } else {
                  e.setVariant(Variant.roll(level, rnd));
                  FAILURES.merge("spawned", 1, Integer::sum);
                  return e;
               }
            }
         } else {
            return fail("lava or player");
         }
      }
   }

   private static boolean standable(ServerLevel level, BlockPos feet) {
      BlockState below = level.getBlockState(feet.below());
      return below.isFaceSturdy(level, feet.below(), Direction.UP)
         && below.getFluidState().isEmpty()
         && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
         && level.getFluidState(feet).isEmpty()
         && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
   }

   public static int wildAround(ServerLevel level, BlockPos pos, int range) {
      return level.getEntitiesOfClass(CreatureEntity.class, new AABB(pos).inflate((double)range), c -> !c.isTame()).size();
   }

   private static record Travel(double credit, Vec3 last, ResourceKey<Level> dimension) {
   }
}
