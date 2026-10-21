package dev.serantis.battle;

import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.party.RecruitRules;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * Serantis spawn odds. Every creature spawn first rolls a rarity, then picks a creature of that rarity
 * the creatures mod's usual way (biome, spawn weight, influence rules). If the biome has nothing of the
 * rolled rarity, the roll is repeated among the rarities it does have.
 */
public final class SpawnOdds {
    public static final String[] RARITY = {"Common", "Uncommon", "Rare", "Super Rare", "Legendary"};
    public static final int[] PERCENT  = {70, 15, 10, 4, 1};
    /** Near a base with a Noble at work: Rare or rarer 35% of the time instead of 15%. */
    public static final int[] NOBLE    = {50, 15, 22, 10, 3};

    /** Common creatures of these influences spawn 5% more often (spawn weight x1.05) than other Common creatures. */
    public static final java.util.Set<Influence> COMMON_BOOST = java.util.EnumSet.of(Influence.NONE, Influence.FIRE, Influence.FROST,
            Influence.EARTH, Influence.STORM, Influence.NATURE, Influence.WATER);
    public static final double COMMON_BOOST_WEIGHT = 1.05;

    /** Weighs the Common pick: a creature of another influence is kept with chance 1/1.05, otherwise picked again. */
    private static Optional<Species.Pick> commonBoost(Optional<Species.Pick> got, Holder<Biome> biome, Level level, RandomSource random,
                                                      BiPredicate<Species, Influence> filter) {
        for (int i = 0; i < 8 && got.isPresent(); i++) {
            if (COMMON_BOOST.contains(got.get().influence()) || random.nextDouble() < 1 / COMMON_BOOST_WEIGHT) return got;
            Optional<Species.Pick> again = Species.pickFor(biome, level, random, filter);
            if (again.isEmpty()) return got;
            got = again;
        }
        return got;
    }

    /** While true, Species.pickFor runs unchanged (it is being called from here with a rarity filter). */
    public static final ThreadLocal<Boolean> INSIDE = ThreadLocal.withInitial(() -> false);

    private SpawnOdds() {}

    /** A creature of a rolled rarity, or empty to let the creatures mod pick as it normally would. */
    public static Optional<Species.Pick> pick(Holder<Biome> biome, Level level, RandomSource random,
                                              BiPredicate<Species, Influence> filter) {
        int[] percent = Villages.NOBLE_SPAWN.get() ? NOBLE : PERCENT;
        Villages.NOBLE_SPAWN.set(false);
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < RARITY.length; i++) left.add(i);
        INSIDE.set(true);
        try {
            while (!left.isEmpty()) {
                int total = 0;
                for (int i : left) total += percent[i];
                int roll = random.nextInt(total);
                int tier = left.get(left.size() - 1);
                for (int i : left) {
                    if (roll < percent[i]) { tier = i; break; }
                    roll -= percent[i];
                }
                String rarity = RARITY[tier];
                BiPredicate<Species, Influence> f = filter.and((s, inf) -> rarity.equals(RecruitRules.rarity(s, inf)));
                Optional<Species.Pick> got = Species.pickFor(biome, level, random, f);
                if (tier == 0) got = commonBoost(got, biome, level, random, f);
                if (got.isPresent()) return Masters.mastery(got, biome, level, random, f);
                left.remove(Integer.valueOf(tier));
            }
            return Optional.empty();
        } finally {
            INSIDE.set(false);
        }
    }
}
