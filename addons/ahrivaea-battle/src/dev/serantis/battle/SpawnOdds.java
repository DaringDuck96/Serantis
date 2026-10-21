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
                Optional<Species.Pick> got = Species.pickFor(biome, level, random,
                        filter.and((s, inf) -> rarity.equals(RecruitRules.rarity(s, inf))));
                if (got.isPresent()) return got;
                left.remove(Integer.valueOf(tier));
            }
            return Optional.empty();
        } finally {
            INSIDE.set(false);
        }
    }
}
