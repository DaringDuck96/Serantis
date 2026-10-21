package dev.ahrivaea.creature;

public final class BattleSettings {
   public static final double LEVEL0_FRACTION = 0.25;
   public static final double HP_FLAT = 20.0;
   public static final double HP_MULTIPLIER = 2.0;
   public static final double STAT_OFFSET = 10.0;
   public static final double SAME_INFLUENCE_BONUS = 1.2;
   public static final double CRIT_MULTIPLIER = 1.5;
   public static final double BASE_CRIT = 0.05;
   public static final double CRIT_PER_ATTACKER_LUCK = 0.0015;
   public static final double CRIT_PER_DEFENDER_LUCK = 5.0E-4;
   public static final double HIT_PER_LUCK_DIFFERENCE = 0.0025;
   public static final double CHARISMA_SUPPORT_PER_POINT = 0.001;
   public static final double WATER_PENALTY = 0.5;
   public static final int MOVE_BASE = 3;
   public static final int SPEED_PER_SQUARE = 25;
   public static final double NIGHT_PEARL_CHANCE = 0.1;
   public static final double NETHER_BLAZE_ROD_CHANCE = 0.25;

   private BattleSettings() {
   }

   public static int xpToNext(int level) {
      return 100 + 25 * level;
   }
}
