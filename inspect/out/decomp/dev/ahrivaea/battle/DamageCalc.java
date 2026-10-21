package dev.ahrivaea.battle;

import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Stats;

public final class DamageCalc {
   private DamageCalc() {
   }

   public static DamageCalc.Result calculate(
      DamageCalc.Fighter attacker, DamageCalc.Fighter defender, DamageCalc.Move move, boolean crit, double roll, double allyCharisma
   ) {
      return calculate(attacker, defender, move, crit, roll, allyCharisma, DamageCalc.Extras.NONE);
   }

   public static DamageCalc.Result calculate(
      DamageCalc.Fighter attacker, DamageCalc.Fighter defender, DamageCalc.Move move, boolean crit, double roll, double allyCharisma, DamageCalc.Extras extras
   ) {
      double base = (double)move.power() * Stats.levelScale(attacker.level());
      double ratio = move.category() == DamageCalc.Category.PHYSICAL
         ? (attacker.eff(attacker.stats().attack()) + 10.0) / (defender.eff(defender.stats().defense()) + 10.0)
         : (attacker.eff(attacker.stats().magic()) + 10.0) / (defender.eff(defender.stats().magic()) + 10.0);
      double influence = move.influence().against(defender.influence());
      double same = move.influence() == attacker.influence() && move.influence() != Influence.NONE ? 1.2 : 1.0;
      double support = 1.0 + allyCharisma * 0.001;
      boolean waterExempt = move.category() == DamageCalc.Category.MAGIC && move.influence() == Influence.WATER;
      double water = attacker.inWater() && !waterExempt ? 0.5 : 1.0;
      double noCrit = base * ratio * influence * same * support * water * roll * extras.damageMultiplier();
      int damage = Math.max(1, (int)Math.round(noCrit * (crit ? 1.5 : 1.0)));
      double luckA = attacker.eff(attacker.stats().luck());
      double luckD = defender.eff(defender.stats().luck());
      double hit = Math.clamp(move.accuracy() + (luckA - luckD) * 0.0025 + extras.hitBonus(), 0.0, 1.0);
      double critChance = Math.clamp(0.05 + luckA * 0.0015 - luckD * 5.0E-4, 0.0, 1.0);
      double expected = hit * noCrit * (1.0 + critChance * 0.5);
      return new DamageCalc.Result(damage, influence, hit, critChance, expected);
   }

   public static enum Category {
      PHYSICAL,
      MAGIC;
   }

   public static record Extras(double hitBonus, double damageMultiplier) {
      public static final DamageCalc.Extras NONE = new DamageCalc.Extras(0.0, 1.0);
   }

   public static record Fighter(Stats stats, int level, Influence influence, boolean inWater, boolean raw) {
      public Fighter(Stats stats, int level, Influence influence, boolean inWater) {
         this(stats, level, influence, inWater, false);
      }

      double eff(int base) {
         return this.raw ? (double)base : this.stats.effective(base, this.level);
      }
   }

   public static record Move(int power, DamageCalc.Category category, Influence influence, double accuracy) {
   }

   public static record Result(int damage, double multiplier, double hitChance, double critChance, double expected) {
   }
}
