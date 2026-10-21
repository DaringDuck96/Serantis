package dev.ahrivaea.battle;

import dev.ahrivaea.creature.Influence;
import java.util.List;

public record BattleMove(String name, DamageCalc.Move move, int minRange, int maxRange) {
   public boolean reaches(int distance) {
      return distance >= this.minRange && distance <= this.maxRange;
   }

   public static List<BattleMove> forCreature(Influence influence) {
      String bolt = influence == Influence.NONE ? "Force Bolt" : influence.displayName() + " Bolt";
      return List.of(
         new BattleMove("Strike", new DamageCalc.Move(40, DamageCalc.Category.PHYSICAL, Influence.NONE, 0.95), 1, 1),
         new BattleMove(bolt, new DamageCalc.Move(35, DamageCalc.Category.MAGIC, influence, 0.9), 1, 2)
      );
   }

   public static BattleMove forWeapon(String itemName, double attackDamage, boolean ranged) {
      if (ranged) {
         return new BattleMove(itemName, new DamageCalc.Move(35, DamageCalc.Category.PHYSICAL, Influence.NONE, 0.85), 2, 3);
      } else {
         int power = (int)Math.round(20.0 + 4.0 * attackDamage);
         return new BattleMove(itemName, new DamageCalc.Move(power, DamageCalc.Category.PHYSICAL, Influence.NONE, 0.95), 1, 1);
      }
   }
}
