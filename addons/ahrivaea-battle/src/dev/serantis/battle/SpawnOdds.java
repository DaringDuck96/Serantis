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

    /**
     * Collecting: every 100 different creatures you've recruited (your dex's caught count, up to 400) moves
     * 3 points of the Common chance to the rarer ones: +1 Uncommon, +1 Rare, +0.5 Super Rare, +0.5 Legendary.
     * A Noble's bonus (one Noble's; more don't add up) comes on top. In tenths of a percent.
     */
    private static final int[] COLLECT_STEP = {-30, 10, 10, 5, 5};
    public static final int COLLECT_EVERY = 100, COLLECT_MAX_STEPS = 4;

    public static int collectSteps(net.minecraft.world.entity.player.Player p) {
        if (!(p instanceof net.minecraft.server.level.ServerPlayer sp)) return 0;
        return Math.min(COLLECT_MAX_STEPS, dev.ahrivaea.party.Dex.count(sp, dev.ahrivaea.party.Dex.CAUGHT) / COLLECT_EVERY);
    }

    /** The rarity odds for a spawn near this player (tenths of a percent). */
    public static int[] odds(net.minecraft.world.entity.player.Player p, boolean noble) {
        int steps = collectSteps(p);
        int[] out = new int[RARITY.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = PERCENT[i] * 10 + steps * COLLECT_STEP[i];
            if (noble) out[i] += (NOBLE[i] - PERCENT[i]) * 10;
            out[i] = Math.max(0, out[i]);
        }
        return out;
    }

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
        int[] percent = odds(Masters.SPAWN_PLAYER.get(), Villages.NOBLE_SPAWN.get());
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
