package dev.ahrivaea.creature;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.Ahrivaea;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiPredicate;
import net.minecraft.core.Holder;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public record Species(
   String name, Stats stats, Map<Influence, Species.Form> forms, float size, Species.Movement movement, Optional<Species.Spawn> spawn, String id
) {
   public static final Codec<Species> CODEC = RecordCodecBuilder.create(
      i -> i.group(
               Codec.STRING.fieldOf("name").forGetter(Species::name),
               Stats.CODEC.fieldOf("stats").forGetter(Species::stats),
               Codec.unboundedMap(Influence.CODEC, Species.Form.CODEC)
                  .optionalFieldOf("forms", Map.of(Influence.NONE, Species.Form.EMPTY))
                  .forGetter(Species::forms),
               Codec.FLOAT.optionalFieldOf("size", 1.0F).forGetter(Species::size),
               Species.Movement.CODEC.optionalFieldOf("movement", Species.Movement.WALK).forGetter(Species::movement),
               Species.Spawn.CODEC.optionalFieldOf("spawn").forGetter(Species::spawn),
               Codec.STRING.optionalFieldOf("id", "").forGetter(Species::id)
            )
            .apply(i, Species::new)
   );
   private static Map<String, Species> all = Map.of();
   public static final int WILD_LEVEL_SPREAD = 5;

   public static void reload(ResourceManager manager) {
      Map<String, Species> loaded = new LinkedHashMap<>();
      manager.listResources("creature", path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
         String id = path.getPath().substring("creature/".length(), path.getPath().length() - ".json".length());

         try (Reader reader = resource.openAsReader()) {
            Species s = (Species)CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
            s = new Species(s.name, s.stats, s.forms, s.size, s.movement, s.spawn, id);

            for (Influence inf : s.forms.keySet()) {
               int total = s.statsFor(inf).total();
               if (total < 500 || total > 600) {
                  Ahrivaea.LOGGER.warn("{} ({}) has a stat total of {}; it should be {}–{}", new Object[]{s.nameFor(inf), id, total, 500, 600});
               }
            }

            loaded.put(id, s);
         } catch (Exception var11) {
            Ahrivaea.LOGGER.error("Could not load creature {}: {}", path, var11.getMessage());
         }
      });
      all = Map.copyOf(loaded);
      Ahrivaea.LOGGER.info("Loaded {} creature species: {}", all.size(), String.join(", ", all.keySet()));
   }

   public static Optional<Species> get(String id) {
      return Optional.ofNullable(all.get(id));
   }

   public static List<Species> all() {
      return List.copyOf(all.values());
   }

   public static Optional<Species.Pick> pickFor(Holder<Biome> biome, Level level, RandomSource random) {
      return pickFor(biome, level, random, (s, inf) -> true);
   }

   public static Optional<Species.Pick> pickFor(Holder<Biome> biome, Level level, RandomSource random, BiPredicate<Species, Influence> allowed) {
      List<Species.Pick> candidates = new ArrayList<>();
      List<Integer> weights = new ArrayList<>();
      int total = 0;

      for (Species s : all.values()) {
         for (Entry<Influence, Species.Form> e : s.forms.entrySet()) {
            Optional<Species.Spawn> sp = e.getValue().spawn();
            if (!sp.isEmpty() && allowed.test(s, e.getKey()) && BiomeList.matches(sp.get().biomes(), biome)) {
               candidates.add(new Species.Pick(s, e.getKey()));
               weights.add(sp.get().weight());
               total += sp.get().weight();
            }
         }

         if (!s.spawn.isEmpty() && BiomeList.matches(s.spawn.get().biomes(), biome)) {
            Optional<Influence> inf = InfluenceRules.pick(biome, level, s.forms.keySet(), random);
            if (!inf.isEmpty() && allowed.test(s, inf.get())) {
               candidates.add(new Species.Pick(s, inf.get()));
               weights.add(s.spawn.get().weight());
               total += s.spawn.get().weight();
            }
         }
      }

      if (total <= 0) {
         return Optional.empty();
      } else {
         long[] tierTotal = new long[4];

         for (int k = 0; k < candidates.size(); k++) {
            tierTotal[candidates.get(k).influence().spawnTier()] += (long)weights.get(k).intValue();
         }

         int tier = pickTier(tierTotal, random);
         long roll = (long)(random.nextDouble() * (double)tierTotal[tier]);

         for (int k = 0; k < candidates.size(); k++) {
            if (candidates.get(k).influence().spawnTier() == tier) {
               roll -= (long)weights.get(k).intValue();
               if (roll < 0L) {
                  return Optional.of(candidates.get(k));
               }
            }
         }

         for (int kx = candidates.size() - 1; kx >= 0; kx--) {
            if (candidates.get(kx).influence().spawnTier() == tier) {
               return Optional.of(candidates.get(kx));
            }
         }

         return Optional.of(candidates.getLast());
      }
   }

   public static int pickTier(long[] tierTotal, RandomSource random) {
      int sum = 0;

      for (int t = 1; t <= 3; t++) {
         if (tierTotal[t] > 0L) {
            sum += Influence.TIER_WEIGHT[t];
         }
      }

      int roll = random.nextInt(Math.max(1, sum));

      for (int tx = 1; tx <= 3; tx++) {
         if (tierTotal[tx] > 0L) {
            roll -= Influence.TIER_WEIGHT[tx];
            if (roll < 0) {
               return tx;
            }
         }
      }

      for (int txx = 1; txx <= 3; txx++) {
         if (tierTotal[txx] > 0L) {
            return txx;
         }
      }

      return 1;
   }

   public static Optional<Species.LevelledPick> pickForLevels(
      Holder<Biome> biome, Level level, RandomSource random, int lo, int hi, BiPredicate<Species, Influence> allowed
   ) {
      Optional<Species.Pick> pick = pickFor(biome, level, random, (s, inf) -> {
         int[] r = s.levelRange(inf);
         return r[0] <= hi && r[1] >= lo && allowed.test(s, inf);
      });
      return pick.map(p -> {
         int[] r = p.species().levelRange(p.influence());
         int a = Math.max(r[0], lo);
         int b = Math.min(r[1], hi);
         return new Species.LevelledPick(p.species(), p.influence(), a + random.nextInt(Math.max(1, b - a + 1)));
      });
   }

   public Species.Form form(Influence influence) {
      return this.forms.getOrDefault(influence, Species.Form.EMPTY);
   }

   public int[] levelRange(Influence influence) {
      Optional<Species.Spawn> sp = this.form(influence).spawn().or(() -> this.spawn);
      return sp.<int[]>map(x -> new int[]{x.minLevel(), x.maxLevel()}).orElse(new int[]{2, 10});
   }

   public List<Job> jobsFor(Influence influence) {
      return this.form(influence).jobs().stream().map(Species.JobEntry::job).distinct().toList();
   }

   public Stats statsFor(Influence influence) {
      Species.Form f = this.forms.get(influence);
      return f != null && f.stats().isPresent() ? f.stats().get() : this.stats;
   }

   public String nameFor(Influence influence) {
      Species.Form f = this.forms.get(influence);
      if (f != null && f.name().isPresent()) {
         return f.name().get();
      } else {
         return influence == Influence.NONE ? this.name : influence.displayName() + " " + this.name;
      }
   }

   public Influence formOrDefault(Influence influence) {
      if (this.forms.containsKey(influence)) {
         return influence;
      } else {
         return this.forms.containsKey(Influence.NONE) ? Influence.NONE : this.forms.keySet().iterator().next();
      }
   }

   public float sizeFor(Influence influence) {
      Species.Form f = this.forms.get(influence);
      return f != null && f.size().isPresent() ? f.size().get() : this.size;
   }

   public float walkSpeed(Influence influence) {
      return 0.2F + (float)this.statsFor(influence).speed() / 1000.0F;
   }

   public static record Drop(String item, float chance, int min, int max, boolean enchant) {
      public static final Codec<Species.Drop> CODEC = RecordCodecBuilder.create(
         i -> i.group(
                  Codec.STRING.fieldOf("item").forGetter(Species.Drop::item),
                  Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(Species.Drop::chance),
                  Codec.INT.optionalFieldOf("min", 1).forGetter(Species.Drop::min),
                  Codec.INT.optionalFieldOf("max", 1).forGetter(Species.Drop::max),
                  Codec.BOOL.optionalFieldOf("enchant", false).forGetter(Species.Drop::enchant)
               )
               .apply(i, Species.Drop::new)
      );
   }

   public static record Form(
      Optional<String> name,
      Optional<Stats> stats,
      Optional<String> rarity,
      Optional<Species.Spawn> spawn,
      List<Species.Drop> drops,
      List<Species.JobEntry> jobs,
      Optional<Float> size
   ) {
      public static final Codec<Species.Form> CODEC = RecordCodecBuilder.create(
         i -> i.group(
                  Codec.STRING.optionalFieldOf("name").forGetter(Species.Form::name),
                  Stats.CODEC.optionalFieldOf("stats").forGetter(Species.Form::stats),
                  Codec.STRING.optionalFieldOf("rarity").forGetter(Species.Form::rarity),
                  Species.Spawn.CODEC.optionalFieldOf("spawn").forGetter(Species.Form::spawn),
                  Species.Drop.CODEC.listOf().optionalFieldOf("drops", List.of()).forGetter(Species.Form::drops),
                  Species.JobEntry.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(Species.Form::jobs),
                  Codec.FLOAT.optionalFieldOf("size").forGetter(Species.Form::size)
               )
               .apply(i, Species.Form::new)
      );
      public static final Species.Form EMPTY = new Species.Form(
         Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(), Optional.empty()
      );
   }

   public static record JobEntry(Job job, String text) {
      public static final Codec<Species.JobEntry> CODEC = RecordCodecBuilder.create(
         i -> i.group(Job.CODEC.fieldOf("job").forGetter(Species.JobEntry::job), Codec.STRING.optionalFieldOf("text", "").forGetter(Species.JobEntry::text))
               .apply(i, Species.JobEntry::new)
      );
   }

   public static record LevelledPick(Species species, Influence influence, int level) {
   }

   public static enum Movement implements StringRepresentable {
      WALK,
      FLY,
      SWIM;

      public static final Codec<Species.Movement> CODEC = StringRepresentable.fromEnum(Species.Movement::values);

      public String getSerializedName() {
         return this.name().toLowerCase(Locale.ROOT);
      }
   }

   public static record Pick(Species species, Influence influence) {
   }

   public static record Spawn(List<String> biomes, int minLevel, int maxLevel, int weight) {
      public static final Codec<Species.Spawn> CODEC = RecordCodecBuilder.create(
         i -> i.group(
                  Codec.STRING.listOf().fieldOf("biomes").forGetter(Species.Spawn::biomes),
                  Codec.intRange(0, 50).optionalFieldOf("min_level", 2).forGetter(Species.Spawn::minLevel),
                  Codec.intRange(0, 50).optionalFieldOf("max_level", 10).forGetter(Species.Spawn::maxLevel),
                  Codec.INT.optionalFieldOf("weight", 10).forGetter(Species.Spawn::weight)
               )
               .apply(i, Species.Spawn::new)
      );
   }
}
