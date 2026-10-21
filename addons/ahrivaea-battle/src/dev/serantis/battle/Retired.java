package dev.serantis.battle;

import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;

/**
 * Creature forms in the creatures mod that Serantis doesn't use: the Arcane Dragon (replaced by the Clockwork
 * Dragon). They never spawn, and traders, merchants and Creature Masters never hand them out.
 */
public final class Retired {
    /** Creature forms a player can find: every form in the creatures mod and add-ons, less the retired ones. */
    public static final int FORMS = 433;

    private Retired() {}

    public static boolean is(Species sp, Influence inf) {
        return sp != null && "dragon".equals(sp.id()) && inf == Influence.ARCANE;
    }

    /** Creatures that only come from their own events (the 20 End dragons, the summoned bosses): never sold or traded. */
    public static boolean unbuyable(String species) {
        return Harbinger.SPECIES.equals(species) || "false_hydra".equals(species) || Riddles.riddle(species) != null;
    }
}
