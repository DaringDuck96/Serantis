package dev.ahrivaea.creature;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.Ahrivaea;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public final class InfluenceRules {
   private static List<InfluenceRules.Rule> rules = List.of();

   private InfluenceRules() {
   }

   public static void reload(ResourceManager manager) {
      List<InfluenceRules.Rule> loaded = new ArrayList<>();
      manager.listResources("influence_rules", path -> path.getPath().endsWith(".json"))
         .forEach(
            (path, resource) -> {
               try (Reader reader = resource.openAsReader()) {
                  loaded.addAll(
                     (Collection<? extends InfluenceRules.Rule>)InfluenceRules.Rule.CODEC
                        .listOf()
                        .parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                        .getOrThrow()
                  );
               } catch (Exception var8) {
                  Ahrivaea.LOGGER.error("Could not load influence rules {}: {}", path, var8.getMessage());
               }
            }
         );
      rules = List.copyOf(loaded);
      Ahrivaea.LOGGER.info("Loaded {} influence rules", rules.size());
   }

   public static Optional<Influence> pick(Holder<Biome> biome, Level level, Set<Influence> available, RandomSource random) {
      long clock = level.getOverworldClockTime();
      List<InfluenceRules.Rule> matching = new ArrayList<>();
      int total = 0;

      for (InfluenceRules.Rule r : rules) {
         if (available.contains(r.influence) && r.time.matches(clock) && BiomeList.matches(r.biomes, biome)) {
            matching.add(r);
            total += Math.max(1, r.weight);
         }
      }

      if (total > 0) {
         int roll = random.nextInt(total);

         for (InfluenceRules.Rule rx : matching) {
            roll -= Math.max(1, rx.weight);
            if (roll < 0) {
               return Optional.of(rx.influence);
            }
         }
      }

      return available.contains(Influence.NONE) ? Optional.of(Influence.NONE) : Optional.empty();
   }

   public static record Rule(List<String> biomes, InfluenceRules.Time time, Influence influence, int weight) {
      public static final Codec<InfluenceRules.Rule> CODEC = RecordCodecBuilder.create(
         i -> i.group(
                  Codec.STRING.listOf().fieldOf("biomes").forGetter(InfluenceRules.Rule::biomes),
                  InfluenceRules.Time.CODEC.optionalFieldOf("time", InfluenceRules.Time.ANY).forGetter(InfluenceRules.Rule::time),
                  Influence.CODEC.fieldOf("influence").forGetter(InfluenceRules.Rule::influence),
                  Codec.INT.optionalFieldOf("weight", 1).forGetter(InfluenceRules.Rule::weight)
               )
               .apply(i, InfluenceRules.Rule::new)
      );
   }

   public static enum Time implements StringRepresentable {
      ANY,
      DAY,
      NIGHT,
      DAWN,
      DUSK;

      public static final Codec<InfluenceRules.Time> CODEC = StringRepresentable.fromEnum(InfluenceRules.Time::values);

      public String getSerializedName() {
         return this.name().toLowerCase(Locale.ROOT);
      }

      public boolean matches(long clock) {
         long t = Math.floorMod(clock, 24000L);

         return switch (this) {
            case ANY -> true;
            case DAY -> t >= 1000L && t < 12000L;
            case NIGHT -> t >= 13500L && t < 22500L;
            case DAWN -> t >= 22500L || t < 1000L;
            case DUSK -> t >= 12000L && t < 13500L;
         };
      }
   }
}
