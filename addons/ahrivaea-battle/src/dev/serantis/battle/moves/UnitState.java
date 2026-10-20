package dev.serantis.battle.moves;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Battle-only state of one unit: stat changes, statuses, Counter Stance and recent moves. */
public final class UnitState {
    public static final class Effect {
        public final String kind;   // "stat", "status" or "move"
        public final String key;    // stat name, status name, or "tiles"
        public final int amount;    // stat points, accuracy points, or tiles
        public int turns;
        public boolean fresh;       // applied during the unit's own turn: the end of that turn doesn't count
        public final int power;     // damage per turn for burning / doom

        public Effect(String kind, String key, int amount, int turns, boolean fresh, int power) {
            this.kind = kind; this.key = key; this.amount = amount; this.turns = turns; this.fresh = fresh; this.power = power;
        }

        public boolean positive() {
            return ("stat".equals(kind) || "move".equals(kind)) && amount > 0;
        }

        public boolean negative() {
            return "status".equals(kind) ? !"nobuff".equals(key) : amount < 0;
        }
    }

    public final List<Effect> effects = new ArrayList<>();
    public boolean recoil;
    public String trait;        // enemy mentality (see EnemyBrain)
    public int size = 1;        // tiles per side its body covers (see Body)
    public String temperament;  // see Temperament
    public int bonusTiles;      // extra battle movement (the player's Swiftness nodes)
    public String lastMoveId;
    public int lastMoveTurn = -99;
    public int startX = Integer.MIN_VALUE, startZ;

    public int stage(String stat) {
        int sum = 0;
        for (Effect e : effects) if ("stat".equals(e.kind) && stat.equals(e.key)) sum += e.amount;
        if ("accuracy".equals(stat)) return Math.max(-60, Math.min(60, sum));
        return Math.max(-5, Math.min(5, sum));
    }

    public int tiles() {
        int sum = 0;
        for (Effect e : effects) if ("move".equals(e.kind)) sum += e.amount;
        return sum;
    }

    public boolean has(String status) {
        for (Effect e : effects) if ("status".equals(e.kind) && status.equals(e.key)) return true;
        return false;
    }

    public boolean remove(String status) {
        return effects.removeIf(e -> "status".equals(e.kind) && status.equals(e.key));
    }

    public boolean hasBuff() {
        for (Effect e : effects) if (e.positive()) return true;
        return false;
    }

    public boolean removeFirst(boolean positive) {
        for (Iterator<Effect> it = effects.iterator(); it.hasNext(); ) {
            Effect e = it.next();
            if (positive ? e.positive() : e.negative()) { it.remove(); return true; }
        }
        return false;
    }

    /** Short labels for the battle screen, e.g. "Burn, Atk-2". */
    public String tags() {
        StringBuilder sb = new StringBuilder();
        if (temperament != null) add(sb, temperament);
        if (trait != null) add(sb, trait);
        for (String s : new String[]{"burning", "bleeding", "poison", "freeze", "stun", "sleep", "confused", "frightened", "immobilized", "slowed", "doom"}) {
            if (has(s)) add(sb, switch (s) {
                case "burning" -> "Burn"; case "bleeding" -> "Bleed"; case "poison" -> "Poison"; case "freeze" -> "Frozen";
                case "stun" -> "Stun"; case "sleep" -> "Asleep"; case "confused" -> "Confused"; case "frightened" -> "Scared";
                case "immobilized" -> "Rooted"; case "slowed" -> "Slowed"; default -> "Grasped"; });
        }
        String[][] stats = {{"attack", "Atk"}, {"defense", "Def"}, {"magic", "Mag"}, {"speed", "Spd"}, {"accuracy", "Acc"}};
        for (String[] s : stats) { int v = stage(s[0]); if (v != 0) add(sb, s[1] + (v > 0 ? "+" : "") + v); }
        int t = tiles();
        if (t != 0) add(sb, "Move" + (t > 0 ? "+" : "") + t);
        if (recoil) add(sb, "Counter");
        return sb.toString();
    }

    private static void add(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append(", ");
        sb.append(s);
    }
}
