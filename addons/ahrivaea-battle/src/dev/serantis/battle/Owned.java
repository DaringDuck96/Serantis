package dev.serantis.battle;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.base.Bases;
import dev.ahrivaea.base.Worker;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** How many of a creature (same kind and influence) a player already has: party, box, out with them, and at their bases. */
public final class Owned {
    private Owned() {}

    private static boolean same(CaughtCreature c, String species, Influence inf) {
        return c != null && species.equals(c.species()) && c.influence() == inf;
    }

    public static int count(ServerPlayer p, String species, Influence inf) {
        if (p == null || species == null) return 0;
        int n = 0;
        for (CaughtCreature c : Party.get(p)) if (same(c, species, inf)) n++;
        for (CaughtCreature c : Party.box(p)) if (same(c, species, inf)) n++;
        for (ServerLevel level : p.level().getServer().getAllLevels()) {
            for (CreatureEntity e : level.getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(128),
                    x -> x.isTame() && x.isOwnedBy(p) && x.brain() == null && species.equals(x.getSpeciesId()) && x.getInfluence() == inf))
                if (level == p.level()) n++;                               // out with them
            for (var core : Bases.cores(level)) {
                BaseCoreBlockEntity be = Bases.coreAt(level, core);
                if (be == null || !p.getUUID().equals(be.owner())) continue;
                for (Worker w : be.workers()) if (same(w.creature(), species, inf)) n++;
            }
        }
        return n;
    }

    /** The result screen's recruit list: "serantis_owned" on each entry. */
    public static void annotate(dev.ahrivaea.battle.Battle b, java.util.UUID viewer, com.google.gson.JsonObject view) {
        if (view == null || !view.has("result") || viewer == null || !viewer.equals(b.playerId) || b.level == null) return;
        var r = view.getAsJsonObject("result");
        if (!r.has("recruits")) return;
        var rec = r.getAsJsonArray("recruits");
        ServerPlayer p = b.level.getServer().getPlayerList().getPlayer(viewer);
        if (p == null || rec.size() != b.defeated.size()) return;
        for (int i = 0; i < rec.size(); i++) {
            var d = b.defeated.get(i);
            rec.get(i).getAsJsonObject().addProperty("serantis_owned", count(p, d.species(), d.influence()));
        }
    }
}
