package dev.serantis.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.challenge.Challenges;
import dev.serantis.battle.mixin.ChallengesInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The land around towers you can't build on (not yet rebuilt, or someone else's): its boxes go to your game
 * ("serantis_tower_zones"), so placing a block there is stopped on your screen with a message (see
 * mixin/client/MultiPlayerGameModeMixin). Before, the block appeared and then vanished when the server refused it.
 * The server still has the final say (the towers mod's own protection).
 */
public final class TowerZones {
    private TowerZones() {}

    public static void describe(ServerPlayer p, JsonObject state) {
        if (!(p.level() instanceof ServerLevel level) || p.isCreative()) return;
        JsonArray out = new JsonArray();
        for (var e : ChallengesInvoker.serantis$states(level.getServer()).entrySet()) {
            var st = e.getValue();
            if (st.origin == null || !level.dimension().identifier().toString().equals(st.dimension)) continue;
            var def = ChallengesInvoker.serantis$defOf(e.getKey(), st).orElse(null);
            if (def == null) continue;
            BoundingBox b = ChallengesInvoker.serantis$box(level, def, st);
            if (b == null) continue;
            BlockPos probe = new BlockPos(b.minX() - 32, Math.max(level.getMinY(), b.minY() - 4), b.minZ() - 32);   // inside the protected land, away from the chest
            if (Challenges.protectedFrom(level, p, probe, false) == null) continue;   // yours (or your guild's) now: build away
            JsonObject o = new JsonObject();
            o.addProperty("name", def.name());
            o.addProperty("x0", b.minX() - 64);
            o.addProperty("y0", b.minY() - 8);
            o.addProperty("z0", b.minZ() - 64);
            o.addProperty("x1", b.maxX() + 64);
            o.addProperty("z1", b.maxZ() + 64);
            out.add(o);
        }
        if (!out.isEmpty()) state.add("serantis_tower_zones", out);
    }
}
