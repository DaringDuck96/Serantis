package dev.ahrivaea.base;

import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.battle.ArenaReturn;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Drops;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Species.Movement;
import dev.ahrivaea.creature.Species.Pick;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDeath;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDeath;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Raids {
   public static final GameRule<Boolean> ENABLED = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.MOBS).buildAndRegister(Ahrivaea.id("base_raids"));
   public static final AttachmentType<BlockPos> RAIDER_OF = AttachmentRegistry.create(Ahrivaea.id("raider_of"));
   public static final int MAX_TICKS = 6000;
   public static final int STEAL_EVERY = 200;
   private static final Map<BlockPos, Raids.Raid> ACTIVE = new HashMap<>();
   private static final Map<BlockPos, Long> LAST = new HashMap<>();
   private static final Map<BlockPos, String> RESULTS = new HashMap<>();
   private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();

   private Raids() {
   }

   public static double chance(int baseLevel) {
      return 0.25 + 0.05 * (double)baseLevel;
   }

   public static int size(int baseLevel, RandomSource r) {
      return 2 + baseLevel + r.nextInt(3);
   }

   public static String lastResult(BlockPos core) {
      return RESULTS.get(core);
   }

   public static Raids.Raid at(BlockPos core) {
      return ACTIVE.get(core);
   }

   public static boolean isRaider(Entity e) {
      return e.hasAttached(RAIDER_OF);
   }

   public static void init() {
      ServerTickEvents.END_SERVER_TICK.register(Raids::tick);
      ServerLivingEntityEvents.ALLOW_DEATH.register((AllowDeath)(entity, source, amount) -> {
         if (entity instanceof CreatureEntity c && c.hasAttached(Bases.WORKER_OF)) {
            c.setHealth(1.0F);
            if (c.brain() != null) {
               c.brain().knockOut();
            }

            return false;
         }

         return true;
      });
      ServerLivingEntityEvents.AFTER_DEATH.register((AfterDeath)(entity, source) -> {
         if (entity instanceof CreatureEntity c && isRaider(c) && entity.level() instanceof ServerLevel level) {
            Species.get(c.getSpeciesId()).ifPresent(s -> {
               for (ItemStack stack : Drops.roll(s, c.getInfluence(), c.getCreatureLevel(), level.getRandom(), level.registryAccess())) {
                  Block.popResource(level, c.blockPosition(), stack);
               }
            });
         }
      });
   }

   private static void tick(MinecraftServer server) {
      for (ServerLevel level : server.getAllLevels()) {
         if ((Boolean)level.getGameRules().get(ENABLED) && level.getGameTime() % 100L == 0L) {
            long day = level.getDefaultClockTime() / 24000L;
            long time = level.getDefaultClockTime() % 24000L;
            boolean nightfall = !level.dimensionType().hasFixedTime() && level.dimensionType().hasSkyLight()
               ? time >= 13000L && time < 13100L
               : time < 100L || level.getGameTime() % 24000L < 100L;
            if (nightfall) {
               for (BlockPos core : Bases.cores(level)) {
                  BaseCoreBlockEntity be = Bases.coreAt(level, core);
                  if (be != null && !ACTIVE.containsKey(core) && !be.workers().isEmpty() && LAST.getOrDefault(core, -1L) != day) {
                     LAST.put(core, day);
                     if (owner(level, be) != null && !(level.getRandom().nextDouble() >= chance(be.baseLevel()))) {
                        start(level, be);
                     }
                  }
               }
            }
         }
      }

      for (Raids.Raid r : List.copyOf(ACTIVE.values())) {
         step(r);
      }
   }

   private static ServerPlayer owner(ServerLevel level, BaseCoreBlockEntity be) {
      if (be.owner() == null) {
         return null;
      } else {
         ServerPlayer p = level.getServer().getPlayerList().getPlayer(be.owner());
         return p != null && p.level() == level && p.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) < 16384.0 ? p : null;
      }
   }

   public static int start(ServerLevel level, BaseCoreBlockEntity be) {
      BlockPos core = be.getBlockPos();
      if (ACTIVE.containsKey(core)) {
         return 0;
      } else {
         int count = size(be.baseLevel(), level.getRandom());
         int avgLevel = (int)be.workers().stream().mapToInt(w -> w.creature().level()).average().orElse(10.0);
         List<UUID> raiders = new ArrayList<>();
         Holder<Biome> biome = level.getBiome(core);

         for (int i = 0; i < count; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            int r = Math.min(be.radius() - 2, 30);
            Vec3 want = new Vec3(
               (double)core.getX() + 0.5 + Math.cos(angle) * (double)r, (double)core.getY(), (double)core.getZ() + 0.5 + Math.sin(angle) * (double)r
            );
            Vec3 at = ArenaReturn.safeSpot(
               level, new Vec3(want.x, (double)level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(want.x), (int)Math.floor(want.z)), want.z)
            );
            if (at != null) {
               Optional<Pick> pick = Species.pickFor(biome, level, level.getRandom(), (s, inf) -> s.movement() != Movement.SWIM);
               if (pick.isEmpty()) {
                  pick = Species.all().isEmpty()
                     ? Optional.empty()
                     : Optional.of(
                        new Pick((Species)Species.all().getFirst(), (Influence)((Species)Species.all().getFirst()).forms().keySet().iterator().next())
                     );
               }

               if (pick.isEmpty()) {
                  break;
               }

               int lvl = Math.clamp((long)(avgLevel - 3 + level.getRandom().nextInt(7)), 1, 50);
               CreatureEntity e = Ahrivaea.spawn(level, pick.get().species(), pick.get().influence(), lvl, -1.0F, at.x, at.y, at.z);
               if (e != null) {
                  e.setAttached(RAIDER_OF, core);
                  e.capSize(1.25F);
                  e.setGlowingTag(true);
                  raiders.add(e.getUUID());
               }
            }
         }

         if (raiders.isEmpty()) {
            return 0;
         } else {
            ACTIVE.put(core, new Raids.Raid(level, core, raiders, level.getGameTime(), new int[1]));
            ServerPlayer p = owner(level, be);
            if (p != null) {
               p.sendSystemMessage(
                  Component.literal(
                        "Your base is being raided by "
                           + raiders.size()
                           + " creatures! Your Defense workers will fight them; hit one with a sword to battle it yourself."
                     )
                     .withStyle(ChatFormatting.RED)
               );
               level.playSound(null, core, (SoundEvent)SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 8.0F, 1.0F);
            }

            Ahrivaea.LOGGER.info("Raid on the base at {}: {} raiders", core.toShortString(), raiders.size());
            return raiders.size();
         }
      }
   }

   private static void step(Raids.Raid r) {
      ServerLevel level = r.level();
      BaseCoreBlockEntity be = Bases.coreAt(level, r.core());
      r.raiders().removeIf(idx -> {
         if (level.getEntity(idx) instanceof CreatureEntity cx && cx.isAlive() && cx.hasAttached(RAIDER_OF)) {
            return false;
         }

         return true;
      });
      long age = level.getGameTime() - r.started();
      if (be != null && !r.raiders().isEmpty()) {
         boolean day = level.dimensionType().hasSkyLight() && !level.dimensionType().hasFixedTime() && level.getDefaultClockTime() % 24000L < 12000L;
         if (age <= 6000L && (!day || age <= 200L)) {
            if (age % 200L == 0L) {
               for (UUID id : r.raiders()) {
                  Entity var9 = level.getEntity(id);
                  if (var9 instanceof CreatureEntity) {
                     CreatureEntity c = (CreatureEntity)var9;
                     if (!(c.distanceToSqr(Vec3.atCenterOf(r.core())) > 25.0)) {
                        r.stolen()[0] += steal(level, be);
                     }
                  }
               }
            }
         } else {
            end(r, be, false);
         }
      } else {
         end(r, be, true);
      }
   }

   private static int steal(ServerLevel level, BaseCoreBlockEntity be) {
      List<BlockPos> chests = new ArrayList<>(be.containers);
      Collections.shuffle(chests, new Random(level.getRandom().nextLong()));

      for (BlockPos p : chests) {
         Container c = HopperBlockEntity.getContainerAt(level, p);
         if (c != null) {
            for (int i = 0; i < c.getContainerSize(); i++) {
               if (!c.getItem(i).isEmpty()) {
                  int n = c.removeItem(i, 16).getCount();
                  level.playSound(null, p, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 1.4F);
                  ServerPlayer owner = owner(level, be);
                  if (owner != null) {
                     owner.sendOverlayMessage(Component.literal("Raiders are stealing from your chests!").withStyle(ChatFormatting.RED));
                  }

                  return n;
               }
            }
         }
      }

      return 0;
   }

   private static void end(Raids.Raid r, BaseCoreBlockEntity be, boolean won) {
      ACTIVE.remove(r.core());
      RESULTS.put(r.core(), (won ? "defeated" : "left") + ", " + r.stolen()[0] + " stolen");
      ServerLevel level = r.level();

      for (UUID id : r.raiders()) {
         if (level.getEntity(id) instanceof CreatureEntity c) {
            c.discard();
         }
      }

      ServerPlayer p = be == null ? null : owner(level, be);
      String stolen = r.stolen()[0] > 0 ? " They stole " + r.stolen()[0] + " items." : "";
      if (p != null) {
         p.sendSystemMessage(
            Component.literal(won ? "The raid on your base is over: every raider was defeated." + stolen : "The raiders gave up and left." + stolen)
               .withStyle(won ? ChatFormatting.GREEN : ChatFormatting.YELLOW)
         );
      }

      Ahrivaea.LOGGER
         .info("Raid on {} ended: {}; {} items stolen", new Object[]{r.core().toShortString(), won ? "raiders defeated" : "raiders left", r.stolen()[0]});
   }

   static CreatureEntity nearestRaider(ServerLevel level, BlockPos core, CreatureEntity from) {
      Raids.Raid r = ACTIVE.get(core);
      if (r == null) {
         return null;
      } else {
         CreatureEntity best = null;

         for (UUID id : r.raiders()) {
            Entity var8 = level.getEntity(id);
            if (var8 instanceof CreatureEntity) {
               CreatureEntity c = (CreatureEntity)var8;
               if (c.isAlive() && (best == null || c.distanceToSqr(from) < best.distanceToSqr(from))) {
                  best = c;
               }
            }
         }

         return best;
      }
   }

   public static void raiderTick(CreatureEntity c) {
      if (c.level() instanceof ServerLevel level) {
         BlockPos core = (BlockPos)c.getAttached(RAIDER_OF);
         if (core != null && ACTIVE.containsKey(core)) {
            CreatureEntity target = null;

            for (CreatureEntity w : level.getEntitiesOfClass(
               CreatureEntity.class,
               c.getBoundingBox().inflate(10.0),
               wx -> core.equals(wx.getAttached(Bases.WORKER_OF)) && wx.brain() != null && !wx.brain().resting()
            )) {
               if (target == null || w.distanceToSqr(c) < target.distanceToSqr(c)) {
                  target = w;
               }
            }

            int cd = COOLDOWN.merge(c.getUUID(), -1, Integer::sum);
            if (target != null) {
               if ((double)c.distanceTo(target) > Combat.reach(c, target)) {
                  if (c.tickCount % 10 == 0) {
                     c.getNavigation().moveTo(target, 1.2);
                  }
               } else {
                  c.getNavigation().stop();
                  c.getLookControl().setLookAt(target);
                  if (cd <= 0) {
                     Combat.hit(level, c, target);
                     COOLDOWN.put(c.getUUID(), 35);
                  }
               }
            } else {
               if (c.distanceToSqr(Vec3.atCenterOf(core)) > 9.0) {
                  if (c.tickCount % 20 == 0) {
                     c.getNavigation().moveTo((double)core.getX() + 0.5, (double)core.getY(), (double)core.getZ() + 0.5, 1.1);
                  }
               } else {
                  c.getNavigation().stop();
               }
            }
         } else {
            c.discard();
         }
      }
   }

   public static AABB around(BlockPos core, int radius) {
      return new AABB(core).inflate((double)(radius + 8), 16.0, (double)(radius + 8));
   }

   public static record Raid(ServerLevel level, BlockPos core, List<UUID> raiders, long started, int[] stolen) {
   }
}
