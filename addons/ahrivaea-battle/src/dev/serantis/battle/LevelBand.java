package dev.serantis.battle;

/**
 * The levels of wild creatures that appear near a player. The creatures mod uses 5 below to 5 above the player's
 * level; new players get a gentler window that widens as they grow:
 * level 0-4: 3 below to 2 above; 5: 4 below to 3 above; 6: 5 below to 4 above; 7 and up: 5 either way.
 * Reinforcements follow the creature you attacked (up to 2 levels below it), so they stay in the window too.
 */
public final class LevelBand {
    private LevelBand() {}

    public static int below(int me) { return Math.max(3, Math.min(5, me - 1)); }
    public static int above(int me) { return Math.max(2, Math.min(5, me - 2)); }

    public static int lo(int me) { return me - below(me); }
    public static int hi(int me) { return me + above(me); }
}
