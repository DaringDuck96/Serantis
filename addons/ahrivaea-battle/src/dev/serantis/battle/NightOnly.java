package dev.serantis.battle;

import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Creatures of the night: in the Overworld these forms only appear after dark (same odds as before, but never by
 * day). Elsewhere (the Nether, the End) they're unchanged. This covers wild spawns and battle reinforcements.
 */
public final class NightOnly {
    /** species/influence. */
    public static final Set<String> FORMS = Set.of(
            "ghost/darkness",               // Common
            "nightmare_stalker/darkness",   // Common
            "night_hag/darkness",           // Uncommon
            "yurei/darkness",               // Uncommon
            "vampire/darkness",             // Rare
            "werewolf/none");               // Rare

    private NightOnly() {}

    public static boolean is(Species s, Influence inf) {
        return s != null && inf != null && FORMS.contains(s.id() + "/" + inf.getSerializedName());
    }

    /** By day in the Overworld, the filter also leaves out the creatures of the night. */
    public static BiPredicate<Species, Influence> wrap(Level level, BiPredicate<Species, Influence> filter) {
        if (level == null || level.dimension() != Level.OVERWORLD || level.isDarkOutside()) return filter;
        return (s, inf) -> !is(s, inf) && (filter == null || filter.test(s, inf));
    }
}
