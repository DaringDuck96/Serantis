package dev.serantis.battle;

/**
 * How far creatures move in battle and how fast they work at a base.
 *
 * Battle movement (tiles per turn) comes from Speed and level. A creature's place in the Speed range
 * (Level 20 Speed 44 = slowest, 103 = fastest) picks where it falls between the level's limits:
 *     Level 1:  4 (slowest) to 6 (fastest)
 *     Level 20: 9 (slowest) to 12 (fastest)
 * with the limits rising in a straight line in between (EXTRA, 2 tiles, is included; players get it too). Stat changes in battle (Speed +/-, Slowed,
 * extra movement) still apply on top.
 *
 * Base work speed starts at 50% for every creature at Level 1 and climbs with level, faster the
 * higher its Charisma: work speed = 50% + (level - 1) / 19 x (Level 20 Charisma / 63). A typical
 * creature (Charisma 63) reaches 150% at Level 20, the most charming (115) about 230%, the least (51)
 * about 130%.
 */
public final class Pace {
    public static final int SLOWEST_SPEED = 44, FASTEST_SPEED = 103;
    public static final double TYPICAL_CHARISMA = 63.0;

    private Pace() {}

    /** Extra tiles of movement every turn, for everyone (creatures and players), so battles get going sooner. */
    public static final int EXTRA = 2;

    public static int squares(int level, int speed20) {
        double lv = (BattleStats.clampLevel(level) - 1) / (double) (BattleStats.MAX_LEVEL - 1);
        double t = Math.max(0, Math.min(1, (speed20 - SLOWEST_SPEED) / (double) (FASTEST_SPEED - SLOWEST_SPEED)));
        double low = 2 + 5 * lv, high = 4 + 6 * lv;
        return (int) Math.round(low + (high - low) * t) + EXTRA;
    }

    /** Work speed at a base: 35% at Level 1, rising slowly at first and faster later, to 50% + Charisma/63 at Level 20 (150% typical). */
    public static double workSpeed(int level, int charisma20) {
        double lv = (BattleStats.clampLevel(level) - 1) / (double) (BattleStats.MAX_LEVEL - 1);
        double top = 0.5 + charisma20 / TYPICAL_CHARISMA;
        return 0.35 + (top - 0.35) * Math.pow(lv, 1.4);
    }
}
