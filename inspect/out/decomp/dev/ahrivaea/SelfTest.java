package dev.ahrivaea;

import dev.ahrivaea.battle.DamageCalc;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Stats;
import java.util.Optional;
import java.util.function.Consumer;

public final class SelfTest {
   private SelfTest() {
   }

   public static boolean run(Consumer<String> out) {
      boolean ok = true;
      Consumer<String> say = msg -> {
         out.accept(msg);
         Ahrivaea.LOGGER.info("AHRIVAEA_TEST {}", msg);
      };

      for (Influence a : Influence.values()) {
         int strong = 0;
         int weak = 0;

         for (Influence d : Influence.values()) {
            if (a.against(d) == 2.0) {
               strong++;
            }

            if (d.against(a) == 2.0) {
               weak++;
            }
         }

         int want = a == Influence.NONE ? 0 : 2;
         if (strong != want || weak != want) {
            say.accept("FAIL favor chart: " + a + " strong " + strong + " weak " + weak);
            ok = false;
         }
      }

      Optional<Species> dragon = Species.get("dragon");
      if (dragon.isEmpty()) {
         say.accept("FAIL no dragon species loaded");
         return false;
      } else {
         Stats ashen = dragon.get().statsFor(Influence.FIRE);
         Stats briar = dragon.get().statsFor(Influence.NATURE);
         DamageCalc.Result r = DamageCalc.calculate(
            new DamageCalc.Fighter(ashen, 30, Influence.FIRE, false),
            new DamageCalc.Fighter(briar, 30, Influence.NATURE, false),
            new DamageCalc.Move(40, DamageCalc.Category.MAGIC, Influence.FIRE, 0.9),
            false,
            1.0,
            0.0
         );
         String line = String.format(
            "damage %d x%.1f hit %.1f%% crit %.1f%% (spreadsheet: 69, x2.0, 89.5%%, 9.4%%) %s vs %s, max HP %d",
            r.damage(),
            r.multiplier(),
            r.hitChance() * 100.0,
            r.critChance() * 100.0,
            dragon.get().nameFor(Influence.FIRE),
            dragon.get().nameFor(Influence.NATURE),
            briar.maxHealth(30)
         );
         boolean match = r.damage() == 69 && Math.abs(r.hitChance() - 0.89475) < 1.0E-6 && Math.abs(r.critChance() - 0.09375) < 1.0E-6;
         say.accept((match ? "PASS " : "FAIL ") + line);
         ok &= match;
         int dry = DamageCalc.calculate(
               new DamageCalc.Fighter(ashen, 50, Influence.WATER, false),
               new DamageCalc.Fighter(briar, 50, Influence.FIRE, false),
               new DamageCalc.Move(40, DamageCalc.Category.PHYSICAL, Influence.NONE, 1.0),
               false,
               1.0,
               0.0
            )
            .damage();
         int wet = DamageCalc.calculate(
               new DamageCalc.Fighter(ashen, 50, Influence.WATER, true),
               new DamageCalc.Fighter(briar, 50, Influence.FIRE, false),
               new DamageCalc.Move(40, DamageCalc.Category.PHYSICAL, Influence.NONE, 1.0),
               false,
               1.0,
               0.0
            )
            .damage();
         int wetMagic = DamageCalc.calculate(
               new DamageCalc.Fighter(ashen, 50, Influence.WATER, true),
               new DamageCalc.Fighter(briar, 50, Influence.FIRE, false),
               new DamageCalc.Move(40, DamageCalc.Category.MAGIC, Influence.WATER, 1.0),
               false,
               1.0,
               0.0
            )
            .damage();
         int dryMagic = DamageCalc.calculate(
               new DamageCalc.Fighter(ashen, 50, Influence.WATER, false),
               new DamageCalc.Fighter(briar, 50, Influence.FIRE, false),
               new DamageCalc.Move(40, DamageCalc.Category.MAGIC, Influence.WATER, 1.0),
               false,
               1.0,
               0.0
            )
            .damage();
         boolean water = Math.abs((double)wet - (double)dry / 2.0) <= 1.0 && wetMagic == dryMagic;
         say.accept((water ? "PASS " : "FAIL ") + "water: physical " + dry + " -> " + wet + " in water; water magic " + dryMagic + " -> " + wetMagic);
         ok &= water;
         say.accept(ok ? "selftest passed" : "selftest FAILED");
         return ok;
      }
   }
}
