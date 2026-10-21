package dev.ahrivaea.creature;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.StringRepresentable.EnumCodec;

public enum Influence implements StringRepresentable {
   NONE("None", 11051678),
   FIRE("Fire", 14964526),
   FROST("Frost", 10146293),
   STORM("Storm", 5995953),
   EARTH("Earth", 10254923),
   WATER("Water", 3049153),
   NATURE("Nature", 5152573),
   VOID("Void", 2825023),
   FAE("Fae", 15044568),
   DARKNESS("Darkness", 3816004),
   RADIANT("Radiant", 16044894),
   ARCANE("Arcane", 8084694);

   public static final EnumCodec<Influence> CODEC = StringRepresentable.fromEnum(Influence::values);
   private final String displayName;
   private final int color;
   public static final int[] TIER_WEIGHT = new int[]{0, 4, 3, 2};
   private static final Map<Influence, Set<Influence>> STRONG = new EnumMap<>(Influence.class);

   private Influence(String displayName, int color) {
      this.displayName = displayName;
      this.color = color;
   }

   public String getSerializedName() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public String displayName() {
      return this.displayName;
   }

   public int color() {
      return this.color;
   }

   public int spawnTier() {
      return switch (this) {
         case NONE -> 1;
         case FIRE, FROST, STORM, EARTH, WATER, NATURE -> 2;
         default -> 3;
      };
   }

   public static Influence byName(String name) {
      Influence i = (Influence)CODEC.byName(name);
      return i == null ? NONE : i;
   }

   private static void strong(Influence attacker, Influence... targets) {
      STRONG.put(attacker, EnumSet.of(targets[0], targets));
   }

   private boolean beats(Influence other) {
      return STRONG.getOrDefault(this, Set.of()).contains(other);
   }

   public double against(Influence defender) {
      if (this.beats(defender)) {
         return 2.0;
      } else {
         return !defender.beats(this) && (this != defender || this == NONE) ? 1.0 : 0.5;
      }
   }

   static {
      strong(FIRE, NATURE, FROST);
      strong(FROST, STORM, NATURE);
      strong(STORM, WATER, FROST);
      strong(EARTH, FIRE, STORM);
      strong(WATER, FIRE, EARTH);
      strong(NATURE, WATER, EARTH);
      strong(RADIANT, DARKNESS, FAE);
      strong(DARKNESS, ARCANE, VOID);
      strong(ARCANE, FAE, RADIANT);
      strong(FAE, VOID, DARKNESS);
      strong(VOID, RADIANT, ARCANE);
   }
}
