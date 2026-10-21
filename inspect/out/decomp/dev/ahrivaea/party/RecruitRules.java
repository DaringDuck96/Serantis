package dev.ahrivaea.party;

import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public final class RecruitRules {
   public static final List<String> RARITIES = List.of("Common", "Uncommon", "Rare", "Super Rare", "Legendary");
   private static final double[] COST_MULTIPLIER = new double[]{1.0, 1.5, 2.0, 3.0, 5.0};
   public static final int COST_PER_LEVEL = 100;
   public static final int REINFORCEMENT_LEVELS_BELOW = 2;

   private RecruitRules() {
   }

   public static int rank(String rarity) {
      int i = RARITIES.indexOf(rarity);
      return i < 0 ? 0 : i;
   }

   public static String rarity(Species s, Influence influence) {
      return s.form(influence).rarity().or(() -> s.forms().values().stream().map(f -> f.rarity()).flatMap(Optional::stream).findFirst()).orElse("Common");
   }

   public static boolean canReinforce(Species s, Influence influence, int level, Species wild, Influence wildInfluence, int wildLevel) {
      return rank(rarity(s, influence)) <= rank(rarity(wild, wildInfluence)) && level <= wildLevel && level >= wildLevel - 2;
   }

   public static Optional<Species.LevelledPick> reinforcement(
      Holder<Biome> biome, Level level, RandomSource random, Species wild, Influence wildInfluence, int wildLevel
   ) {
      int maxRank = rank(rarity(wild, wildInfluence));
      return Species.pickForLevels(biome, level, random, Math.max(0, wildLevel - 2), wildLevel, (sp, in) -> rank(rarity(sp, in)) <= maxRank);
   }

   public static int cost(Species s, Influence influence, int level) {
      return (int)Math.round((double)(100 * Math.max(1, level)) * COST_MULTIPLIER[rank(rarity(s, influence))]);
   }
}
