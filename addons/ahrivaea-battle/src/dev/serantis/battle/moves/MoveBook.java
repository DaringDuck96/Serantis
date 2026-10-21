package dev.serantis.battle.moves;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.battle.BattleMove;
import dev.ahrivaea.battle.DamageCalc;
import dev.ahrivaea.creature.Influence;
import net.minecraft.util.RandomSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The move catalog: lookup, conversion to the creatures mod's BattleMove, and random move sets. */
public final class MoveBook {
    private static Map<String, MoveSpec> byId;
    private static Map<String, MoveSpec> byName;

    private MoveBook() {}

    private static synchronized void load() {
        if (byId != null) return;
        Map<String, MoveSpec> ids = new HashMap<>(), names = new HashMap<>();
        try (InputStream in = MoveBook.class.getResourceAsStream("/serantis_battle/moves.json")) {
            if (in != null) {
                JsonArray arr = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    MoveSpec s = new MoveSpec();
                    s.id = o.get("id").getAsString();
                    s.name = o.get("name").getAsString();
                    s.category = o.get("category").getAsString();
                    s.influence = o.get("influence").getAsString();
                    s.range = o.get("range").getAsString();
                    s.target = o.get("target").getAsString();
                    s.shape = o.get("shape").getAsString();
                    s.min = o.get("min").getAsInt();
                    s.max = o.get("max").getAsInt();
                    s.radius = o.get("radius").getAsInt();
                    s.hits = Math.max(1, o.get("hits").getAsInt());
                    s.power = o.get("power").getAsInt();
                    s.sheetPower = o.get("sheet_power").getAsInt();
                    s.accuracy = o.get("accuracy").getAsDouble();
                    s.enabled = o.get("enabled").getAsBoolean();
                    s.text = MoveSpec.str(o, "text");
                    s.later = MoveSpec.str(o, "later");
                    s.effects = new ArrayList<>();
                    for (JsonElement e : o.getAsJsonArray("effects")) s.effects.add(e.getAsJsonObject());
                    ids.put(s.id, s);
                    names.put(s.name, s);
                }
            }
        } catch (Exception e) {
            // no catalog: creatures keep the creatures mod's own moves
        }
        byId = ids;
        byName = names;
    }

    public static MoveSpec byId(String id) {
        load();
        return byId.get(id);
    }

    /** Is this the name of a catalog move? (A weapon renamed to one gets a suffix, see Equipment.) */
    public static boolean isMoveName(String name) {
        load();
        return byName.containsKey(name);
    }

    /** The catalog move behind a BattleMove, or null for the creatures mod's own moves (weapons, etc.). */
    public static MoveSpec of(BattleMove m) {
        if (m == null) return null;
        load();
        MoveSpec s = byName.get(m.name());
        return s != null && s.min == m.minRange() && s.max == m.maxRange() ? s : null;
    }

    /** Every move in the catalog. */
    public static java.util.Collection<MoveSpec> all() {
        load();
        return byId.values();
    }

    public static boolean available() {
        load();
        return !byId.isEmpty();
    }

    public static BattleMove toBattleMove(MoveSpec s) {
        Influence infl = Influence.NONE;
        if (s.magic() && !"none".equals(s.influence)) {
            try { infl = Influence.valueOf(s.influence.toUpperCase()); } catch (IllegalArgumentException e) { infl = Influence.NONE; }
        }
        DamageCalc.Category cat = s.magic() ? DamageCalc.Category.MAGIC : DamageCalc.Category.PHYSICAL;
        return new BattleMove(s.name, new DamageCalc.Move(s.power, cat, infl, s.accuracy), s.min, s.max);
    }

    public static List<BattleMove> toBattleMoves(List<MoveSpec> specs) {
        List<BattleMove> out = new ArrayList<>();
        for (MoveSpec s : specs) out.add(toBattleMove(s));
        return out;
    }

    /** Moves a creature of this influence can know: universal moves plus its own influence's magic. */
    public static List<MoveSpec> pool(Influence influence) {
        load();
        String own = influence.getSerializedName();
        List<MoveSpec> out = new ArrayList<>();
        for (MoveSpec s : byId.values())
            if (s.enabled && ("universal".equals(s.influence) || own.equals(s.influence))) out.add(s);
        out.sort((a, b) -> a.id.compareTo(b.id));
        return out;
    }

    /** Three different random moves, at least one of which deals damage. */
    public static List<MoveSpec> roll(Influence influence, RandomSource random) {
        List<MoveSpec> pool = new ArrayList<>(pool(influence));
        List<MoveSpec> out = new ArrayList<>();
        if (pool.isEmpty()) return out;
        List<MoveSpec> damaging = new ArrayList<>();
        for (MoveSpec s : pool) if (s.damaging()) damaging.add(s);
        if (!damaging.isEmpty()) {
            MoveSpec first = damaging.get(random.nextInt(damaging.size()));
            out.add(first);
            pool.remove(first);
        }
        while (out.size() < 3 && !pool.isEmpty()) out.add(pool.remove(random.nextInt(pool.size())));
        Collections.sort(out, (a, b) -> Boolean.compare(b.damaging(), a.damaging()));
        return out;
    }

    /** {@code n} different random moves from every influence (at least two deal damage): the Mysterious Figure's. */
    public static List<MoveSpec> rollAny(RandomSource random, int n) {
        load();
        List<MoveSpec> pool = new ArrayList<>();
        for (MoveSpec s : byId.values()) if (s.enabled) pool.add(s);
        pool.sort((a, b) -> a.id.compareTo(b.id));
        List<MoveSpec> out = new ArrayList<>();
        List<MoveSpec> damaging = new ArrayList<>();
        for (MoveSpec s : pool) if (s.damaging()) damaging.add(s);
        for (int i = 0; i < 2 && !damaging.isEmpty(); i++) {
            MoveSpec d = damaging.remove(random.nextInt(damaging.size()));
            out.add(d);
            pool.remove(d);
        }
        while (out.size() < n && !pool.isEmpty()) out.add(pool.remove(random.nextInt(pool.size())));
        Collections.sort(out, (a, b) -> Boolean.compare(b.damaging(), a.damaging()));
        return out;
    }

    public static String encode(List<MoveSpec> specs) {
        StringBuilder sb = new StringBuilder();
        for (MoveSpec s : specs) { if (sb.length() > 0) sb.append(','); sb.append(s.id); }
        return sb.toString();
    }

    /** The saved move set, or null if it's missing or no longer valid (then a new one is rolled). */
    public static List<MoveSpec> decode(String saved) {
        if (saved == null || saved.isBlank()) return null;
        load();
        List<MoveSpec> out = new ArrayList<>();
        for (String id : saved.split(",")) {
            MoveSpec s = byId.get(id.trim());
            if (s != null && s.enabled) out.add(s);
        }
        boolean damaging = false;
        for (MoveSpec s : out) damaging |= s.damaging();
        return out.isEmpty() || !damaging ? null : out;
    }
}
