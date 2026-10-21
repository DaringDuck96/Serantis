package dev.serantis.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.ahrivaea.party.Dex;
import net.minecraft.server.level.ServerPlayer;

/**
 * Changes to the Creature Dex the server sends: the retired Arcane Dragon is left out, the count is out of
 * the 433 forms you can find, and the Acolyte of the First gets a bonus page after them that doesn't count
 * towards the total (recruit him and the dex can read 434/433).
 */
public final class DexExtras {
    private DexExtras() {}

    public static void apply(ServerPlayer p, JsonObject data) {
        if (!data.has("entries")) return;
        JsonArray in = data.getAsJsonArray("entries"), out = new JsonArray();
        JsonObject villager = null;
        int max = 0;
        for (JsonElement el : in) {
            JsonObject e = el.getAsJsonObject();
            if ("dragon".equals(str(e, "species")) && "arcane".equals(str(e, "influence"))) continue;     // retired
            if ("villager".equals(str(e, "species"))) villager = e;
            if (e.has("n")) max = Math.max(max, e.get("n").getAsInt());
            out.add(e);
        }
        int met = ((Welcome.Holder) p).serantis$figureMet();
        int status = (met & FigureVisit.RECRUITED) != 0 ? Dex.CAUGHT : PlayerClass.of(p) != null ? Dex.SEEN : 0;
        JsonObject a = villager == null ? new JsonObject() : villager.deepCopy();
        a.addProperty("n", max + 1);
        a.addProperty("name", FigureVisit.REVEALED);
        a.addProperty("status", status);
        a.addProperty("rarity", "Legendary");
        a.addProperty("hint", "Only one exists. Earn the Eternita Stone, then prove yourself worthy of it.");
        a.addProperty("serantis_bonus", true);
        if (a.has("stats")) {
            var s = FigureVisit.legendStats();
            JsonObject st = new JsonObject();
            st.addProperty("hp", s.hp()); st.addProperty("attack", s.attack()); st.addProperty("defense", s.defense());
            st.addProperty("speed", s.speed()); st.addProperty("magic", s.magic()); st.addProperty("luck", s.luck());
            st.addProperty("charisma", s.charisma());
            if (a.get("stats").isJsonObject()) a.add("stats", st);
        }
        if (a.has("biomes")) a.add("biomes", new JsonArray());
        out.add(a);
        int seen = 0, caught = 0;
        for (JsonElement el : out) {
            int s = el.getAsJsonObject().has("status") ? el.getAsJsonObject().get("status").getAsInt() : 0;
            if (s >= Dex.SEEN) seen++;
            if (s >= Dex.CAUGHT) caught++;
        }
        data.add("entries", out);
        data.addProperty("seen", seen);
        data.addProperty("caught", caught);
        data.addProperty("serantis_total", Retired.FORMS);
    }

    private static String str(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null; }
}
