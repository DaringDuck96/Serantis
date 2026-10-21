package dev.ahrivaea.creature;

import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.character.PlayerCharacter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.Load;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

public final class VanillaMobs {
   public static final GameRule<Boolean> ALLOWED = GameRuleBuilder.forBoolean(false)
      .category(GameRuleCategory.SPAWNING)
      .buildAndRegister(Ahrivaea.id("vanilla_mobs"));
   public static final Map<String, String> MATCHES = Map.ofEntries(
      Map.entry("cow", "cow"),
      Map.entry("mooshroom", "cow"),
      Map.entry("pig", "pig"),
      Map.entry("sheep", "sheep"),
      Map.entry("chicken", "chicken"),
      Map.entry("horse", "horse"),
      Map.entry("donkey", "horse"),
      Map.entry("mule", "horse"),
      Map.entry("skeleton_horse", "horse"),
      Map.entry("zombie_horse", "horse"),
      Map.entry("camel", "camel"),
      Map.entry("cat", "cat"),
      Map.entry("ocelot", "cat"),
      Map.entry("fox", "fox"),
      Map.entry("goat", "goat"),
      Map.entry("llama", "llama"),
      Map.entry("trader_llama", "llama"),
      Map.entry("rabbit", "rabbit"),
      Map.entry("squid", "squid"),
      Map.entry("glow_squid", "squid"),
      Map.entry("dolphin", "dolphin"),
      Map.entry("wolf", "wolf"),
      Map.entry("polar_bear", "bear"),
      Map.entry("iron_golem", "golem"),
      Map.entry("snow_golem", "golem"),
      Map.entry("zombie", "zombie"),
      Map.entry("husk", "zombie"),
      Map.entry("drowned", "zombie"),
      Map.entry("zombie_villager", "zombie"),
      Map.entry("skeleton", "skeleton"),
      Map.entry("stray", "skeleton"),
      Map.entry("bogged", "skeleton"),
      Map.entry("wither_skeleton", "skeleton")
   );
   public static final int CROWD = 3;
   private static final List<VanillaMobs.Replace> QUEUE = new ArrayList<>();
   public static final Set<String> VILLAGERS = Set.of("villager", "wandering_trader");

   private VanillaMobs() {
   }

   public static void init() {
      BiomeModifications.create(Ahrivaea.id("no_vanilla_mobs"))
         .add(ModificationPhase.REMOVALS, BiomeSelectors.all(), ctx -> ctx.getMobSpawnSettings().clearSpawns());
      ServerEntityEvents.ENTITY_LOAD.register((Load)(entity, level) -> {
         if (!(entity instanceof Mob mob) || entity instanceof CreatureEntity || (Boolean)level.getGameRules().get(ALLOWED)) {
            return;
         }

         if (!WildSpawner.SKIP.contains(level.dimension())) {
            if (mob instanceof EnderDragon dragon) {
               EnderDragonFight fight = level.getDragonFight();
               if (fight != null) {
                  fight.setDragonKilled(dragon);
               }

               Ahrivaea.LOGGER.info("The Ender Dragon appeared and was defeated straight away (no vanilla mobs here)");
            } else {
               String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
               synchronized (QUEUE) {
                  QUEUE.add(new VanillaMobs.Replace(level, mob.blockPosition(), id));
               }
            }

            mob.discard();
         }
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         List<VanillaMobs.Replace> todo;
         synchronized (QUEUE) {
            if (QUEUE.isEmpty()) {
               return;
            }

            todo = new ArrayList<>(QUEUE);
            QUEUE.clear();
         }

         for (VanillaMobs.Replace r : todo) {
            replace(r);
         }
      });
   }

   private static void replace(VanillaMobs.Replace r) {
      ServerLevel level = r.level();
      if (level.isLoaded(r.pos())) {
         if (VILLAGERS.contains(r.mob())) {
            Optional<Species> sp = Species.get("villager");
            if (sp.isPresent()) {
               int[] range = sp.get().levelRange(Influence.NONE);
               int lvl = range[0] + level.getRandom().nextInt(Math.max(1, range[1] - range[0] + 1));
               Ahrivaea.spawn(level, sp.get(), Influence.NONE, lvl, -1.0F, (double)r.pos().getX() + 0.5, (double)r.pos().getY(), (double)r.pos().getZ() + 0.5);
               return;
            }
         }

         if (WildSpawner.wildAround(level, r.pos(), 8) < 3) {
            Holder<Biome> biome = level.getBiome(r.pos());
            boolean water = level.getFluidState(r.pos()).is(FluidTags.WATER);
            Species.Pick pick = null;
            String match = MATCHES.get(r.mob());
            if (match != null) {
               Optional<Species> sp = Species.get(match);
               if (sp.isPresent()) {
                  Optional<Species.Pick> here = Species.pickFor(biome, level, level.getRandom(), (s, inf) -> s == sp.get());
                  pick = here.orElse(new Species.Pick(sp.get(), sp.get().forms().keySet().iterator().next()));
               }
            }

            if (pick == null) {
               pick = Species.pickFor(biome, level, level.getRandom(), (s, inf) -> water == (s.movement() == Species.Movement.SWIM)).orElse(null);
            }

            if (pick != null) {
               int[] range = pick.species().levelRange(pick.influence());
               int lvl = range[0] + level.getRandom().nextInt(Math.max(1, range[1] - range[0] + 1));
               Player near = level.getNearestPlayer((double)r.pos().getX() + 0.5, (double)r.pos().getY(), (double)r.pos().getZ() + 0.5, 128.0, false);
               if (near != null) {
                  int me = PlayerCharacter.get(near).level();
                  int lo = me - 5;
                  int hi = me + 5;
                  if (range[0] <= hi && range[1] >= lo) {
                     lvl = Math.max(range[0], lo) + level.getRandom().nextInt(Math.max(1, Math.min(range[1], hi) - Math.max(range[0], lo) + 1));
                  } else {
                     Optional<Species.LevelledPick> other = Species.pickForLevels(
                        biome, level, level.getRandom(), lo, hi, (s, inf) -> water == (s.movement() == Species.Movement.SWIM)
                     );
                     if (other.isEmpty()) {
                        return;
                     }

                     pick = new Species.Pick(other.get().species(), other.get().influence());
                     lvl = other.get().level();
                  }
               }

               CreatureEntity e = Ahrivaea.spawn(
                  level, pick.species(), pick.influence(), lvl, -1.0F, (double)r.pos().getX() + 0.5, (double)r.pos().getY(), (double)r.pos().getZ() + 0.5
               );
               if (e != null) {
                  e.setVariant(Variant.roll(level, level.getRandom()));
               }
            }
         }
      }
   }

   private static record Replace(ServerLevel level, BlockPos pos, String mob) {
   }
}
