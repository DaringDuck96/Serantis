package dev.ahrivaea.creature;

import com.mojang.serialization.Codec;
import dev.ahrivaea.Ahrivaea;
import java.util.Locale;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

public enum Variant implements StringRepresentable {
   NONE(""),
   ENRAGED("Enraged"),
   MUTATED("Mutated");

   public static final Codec<Variant> CODEC = StringRepresentable.fromEnum(Variant::values);
   public static final GameRule<Integer> ODDS = GameRuleBuilder.forInteger(10000)
      .minValue(0)
      .category(GameRuleCategory.SPAWNING)
      .buildAndRegister(Ahrivaea.id("variant_odds"));
   private final String word;

   private Variant(String word) {
      this.word = word;
   }

   public String getSerializedName() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public String prefix() {
      return this.word.isEmpty() ? "" : this.word + " ";
   }

   public static Variant byName(String name) {
      for (Variant v : values()) {
         if (v.getSerializedName().equals(name)) {
            return v;
         }
      }

      return NONE;
   }

   public static Variant roll(ServerLevel level, RandomSource random) {
      int odds = (Integer)level.getGameRules().get(ODDS);
      if (odds > 0 && random.nextInt(odds) == 0) {
         return random.nextBoolean() ? ENRAGED : MUTATED;
      } else {
         return NONE;
      }
   }

   public static void init() {
   }
}
