package dev.serantis.battle.moves;

import com.google.gson.JsonObject;

import java.util.List;

/** One move from serantis_battle/moves.json (built from the Move Dex by tools/build_moves.py). */
public final class MoveSpec {
    public String id, name, category, influence, range, target, shape, later, text;
    public int min, max, radius, hits, power, sheetPower;
    public double accuracy;
    public boolean enabled;
    public List<JsonObject> effects;

    /** Hits enemies for damage. */
    public boolean damaging() {
        return power > 0 && ("enemy".equals(target) || "any".equals(target));
    }

    /** Only used on the user or its allies. */
    public boolean support() {
        return "self".equals(target) || "ally".equals(target) || "self_or_ally".equals(target);
    }

    public boolean magic() {
        return "magic".equals(category);
    }

    public boolean has(String type) {
        for (JsonObject e : effects) if (type.equals(str(e, "type"))) return true;
        return false;
    }

    static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }
}
