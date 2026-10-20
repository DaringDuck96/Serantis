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
 * Base work speed comes from Charisma at the creature's level (Level 1 = 50% of its Level 20 Charisma):
 * work speed = Charisma / 63, so a typical Level 20 creature (Charisma 63) works at 100%. Between 25%
 * and 200%.
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
        double charisma = charisma20 * BattleStats.levelScale(level);
        return Math.max(0.25, Math.min(2.0, charisma / TYPICAL_CHARISMA));
    }
}
