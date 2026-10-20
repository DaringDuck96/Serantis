package dev.serantis.battle;

import dev.ahrivaea.creature.Stats;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Temperaments (like natures): every creature gets one at random when it first appears, kept for good.
 * Each raises one stat by 0.5 per level and lowers another by 0.5 per level (so +10 / -10 at Level 20).
 *
 * Stats in the creatures mod are stored at Level 20 and scaled down by level; the change is added to
 * the Level 20 value in a way that comes out at exactly 0.5 per level once scaled.
 */
public final class Temperament {
    public record Kind(String name, String up, String down) {}

    public static final Map<String, Kind> ALL = new LinkedHashMap<>();
    static {
        add("Adamant", "attack", "magic");       add("Brave", "attack", "speed");
        add("Bold", "defense", "attack");        add("Relaxed", "defense", "speed");
        add("Impenetrable", "defense", "luck");  add("Hardy", "hp", "charisma");
        add("Stalwart", "hp", "speed");          add("Vital", "hp", "magic");
        add("Swift", "speed", "defense");        add("Hasty", "speed", "hp");
        add("Nimble", "speed", "attack");        add("Cunning", "luck", "defense");
        add("Fortunate", "luck", "hp");          add("Fickle", "luck", "charisma");
        add("Wise", "magic", "attack");          add("Mystic", "magic", "defense");
        add("Focused", "magic", "luck");         add("Scholarly", "magic", "speed");
        add("Charming", "charisma", "attack");   add("Regal", "charisma", "speed");
        add("Inspiring", "charisma", "luck");    add("Intimidating", "attack", "charisma");
        add("Guardian", "defense", "magic");     add("Tenacious", "hp", "luck");
        add("Trickster", "charisma", "magic");
    }

    private static void add(String name, String up, String down) { ALL.put(name, new Kind(name, up, down)); }

    private Temperament() {}

    public static String roll(RandomSource random) {
        return roll(random.nextInt(ALL.size()));
    }

    public static String roll(java.util.Random random) {
        return roll(random.nextInt(ALL.size()));
    }

    private static String roll(int i) {
        return ALL.keySet().toArray(new String[0])[i];
    }

    public static Kind of(String name) { return name == null ? null : ALL.get(name); }

    /** Level 20 stats with the temperament's change for a creature of this level. */
    public static Stats apply(Stats s, String temperament, int level) {
        Kind k = of(temperament);
        if (s == null || k == null) return s;
        int l = BattleStats.clampLevel(level);
        int delta = (int) Math.round(0.5 * l / BattleStats.levelScale(l));
        int[] v = {s.hp(), s.attack(), s.defense(), s.speed(), s.magic(), s.luck(), s.charisma()};
        v[index(k.up())] += delta;
        v[index(k.down())] = Math.max(1, v[index(k.down())] - delta);
        return new Stats(v[0], v[1], v[2], v[3], v[4], v[5], v[6]);
    }

    private static int index(String stat) {
        return switch (stat) {
            case "hp" -> 0; case "attack" -> 1; case "defense" -> 2; case "speed" -> 3;
            case "magic" -> 4; case "luck" -> 5; default -> 6;
        };
    }

    public static String label(String stat) {
        return switch (stat) {
            case "hp" -> "HP"; case "attack" -> "Attack"; case "defense" -> "Defense"; case "speed" -> "Speed";
            case "magic" -> "Magic"; case "luck" -> "Luck"; default -> "Charisma";
        };
    }
}
