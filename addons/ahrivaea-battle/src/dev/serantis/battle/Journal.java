package dev.serantis.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.party.Dex;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The Journal (party screen > Journal, client/JournalScreen): what to do next, how far you've come, the story so
 * far (every Acolyte scene you've seen, to read again) and the lore books you've found.
 */
public final class Journal {
    private Journal() {}

    private static int maxBaseLevel(ServerPlayer p) {
        int best = 0;
        for (var level : p.level().getServer().getAllLevels())
            for (var pos : dev.ahrivaea.base.Bases.cores(level)) {
                if (!level.isLoaded(pos)) continue;
                var core = dev.ahrivaea.base.Bases.coreAt(level, pos);
                if (core != null && p.getUUID().equals(core.owner())) best = Math.max(best, core.baseLevel());
            }
        return best;
    }

    private static boolean nether(ServerPlayer p) {
        for (String b : ((Achievements.Holder) p).serantis$biomes())
            if (b.contains("nether_wastes") || b.contains("crimson_forest") || b.contains("warped_forest") || b.contains("soul_sand_valley") || b.contains("basalt_deltas"))
                return true;
        return false;
    }

    public static void describe(ServerPlayer p, JsonObject state) {
        Welcome.Holder h = (Welcome.Holder) p;
        int battles = Math.max(0, h.serantis$battlesFought()), towers = (int) Achievements.value(p, "towers"), base = maxBaseLevel(p);
        int stones = 0;
        for (String inf : Masters.INFLUENCES) stones += ((Masters.Holder) p).serantis$stones(inf);
        boolean eternita = ((Masters.Holder) p).serantis$eternita();
        int dex = Dex.count(p, Dex.CAUGHT);
        String first = FigureVisit.name(p);

        List<String> next = new ArrayList<>();
        if (battles < 10) next.add("Battle wild creatures: hit one with a weapon or a staff. Win and you can recruit them. (" + battles + "/10 battles)");
        if (base < 1) next.add("Place a Base Core and claim a home. Creatures you assign there work for you.");
        if (towers < 1) next.add("The " + first + " said: find the towers. Rebuild one and claim it.");
        if (base >= 1 && base < 2) next.add("Upgrade your base to Level 2. (Base Core > Upgrade)");
        if (!nether(p)) next.add("Serantia is more than one world. Build a Nether portal and step through.");
        if (stones < 60) next.add("Beat Creature Masters for Gym Master Stones. They wander the wild, now and then. (" + stones + "/60)");
        if (towers >= 1 && towers < 5) next.add("Find and rebuild more towers. (" + towers + "/5)");
        if (dex < Retired.FORMS) next.add("Recruit different creatures for your dex. Every 100 makes rare ones easier to find. (" + dex + "/" + Retired.FORMS + ")");
        if (eternita) next.add("Light the End portal and face what waits beyond. Every End beast is different.");
        if (eternita && dex >= Retired.FORMS) next.add("Hunt Grandmasters for enchanted books, and keep your bounties up.");
        JsonArray steps = new JsonArray();
        for (int i = 0; i < Math.min(4, next.size()); i++) steps.add(next.get(i));
        int done = 0, total = 0;
        for (var c : Bounties.today(p)) { total++; if (c.claimed()) done++; }

        JsonObject o = new JsonObject();
        o.add("next", steps);
        o.addProperty("bounties", done + "/" + total);
        o.addProperty("battles", battles);
        o.addProperty("dex", dex);
        o.addProperty("dexTotal", Retired.FORMS);
        o.addProperty("stones", stones);
        o.addProperty("eternita", eternita);
        o.addProperty("towers", towers);
        o.addProperty("base", base);
        o.addProperty("lore", h.serantis$loreTitles().size());
        o.addProperty("loreTotal", LoreDrops.count());
        o.addProperty("met", h.serantis$figureMet());
        o.addProperty("intro", PlayerClass.of(p) != null);
        o.addProperty("firstName", first);
        o.addProperty("lastName", FigureVisit.nameOf(p, "last"));
        JsonArray titles = new JsonArray();
        for (String t : h.serantis$loreTitles()) titles.add(t);
        o.add("loreTitles", titles);
        state.add("serantis_journal", o);
    }
}
