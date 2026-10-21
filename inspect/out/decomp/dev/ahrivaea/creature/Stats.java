package dev.ahrivaea.creature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record Stats(int hp, int attack, int defense, int speed, int magic, int luck, int charisma) {
   public static final int MAX_LEVEL = 50;
   public static final int MIN_TOTAL = 500;
   public static final int MAX_TOTAL = 600;
   private static final Codec<Integer> STAT = Codec.intRange(0, 100);
   public static final Codec<Stats> CODEC = RecordCodecBuilder.create(
      i -> i.group(
               STAT.fieldOf("hp").forGetter(Stats::hp),
               STAT.fieldOf("attack").forGetter(Stats::attack),
               STAT.fieldOf("defense").forGetter(Stats::defense),
               STAT.fieldOf("speed").forGetter(Stats::speed),
               STAT.fieldOf("magic").forGetter(Stats::magic),
               STAT.fieldOf("luck").forGetter(Stats::luck),
               STAT.fieldOf("charisma").forGetter(Stats::charisma)
            )
            .apply(i, Stats::new)
   );

   public int total() {
      return this.hp + this.attack + this.defense + this.speed + this.magic + this.luck + this.charisma;
   }

   public static double levelScale(int level) {
      return 0.25 + 0.75 * (double)Math.clamp((long)level, 0, 50) / 50.0;
   }

   public double effective(int base, int level) {
      return (double)base * levelScale(level);
   }

   public int maxHealth(int level) {
      return (int)Math.round(20.0 + 2.0 * this.effective(this.hp, level));
   }

   public int squaresPerTurn() {
      return 3 + this.speed / 25;
   }
}
