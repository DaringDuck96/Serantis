package dev.serantis.battle;

/**
 * How far creatures move in battle and how fast they work at a base.
 *
 * Battle movement (tiles per turn) comes from Speed and level. A creature's place in the Speed range
 * (Level 20 Speed 44 = slowest, 103 = fastest) picks where it falls between the level's limits:
 *     Level 1:  2 (slowest) to 4 (fastest)
 *     Level 20: 7 (slowest) to 10 (fastest)
 * with the limits rising in a straight line in between. Stat changes in battle (Speed +/-, Slowed,
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

    public static int squares(int level, int speed20) {
        double lv = (BattleStats.clampLevel(level) - 1) / (double) (BattleStats.MAX_LEVEL - 1);
        double t = Math.max(0, Math.min(1, (speed20 - SLOWEST_SPEED) / (double) (FASTEST_SPEED - SLOWEST_SPEED)));
        double low = 2 + 5 * lv, high = 4 + 6 * lv;
        return (int) Math.round(low + (high - low) * t);
    }

    public static double workSpeed(int level, int charisma20) {
        double lv = (BattleStats.clampLevel(level) - 1) / (double) (BattleStats.MAX_LEVEL - 1);
        return 0.5 + lv * (charisma20 / TYPICAL_CHARISMA);
    }
}
