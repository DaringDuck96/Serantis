package dev.serantis.battle.mixin.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import dev.ahrivaea.battle.BattleGrid;
import dev.ahrivaea.battle.Pathing;
import dev.ahrivaea.client.battle.BattleClient;
import dev.ahrivaea.client.battle.BattleScreen;
import dev.serantis.battle.fx.Fx;
import dev.serantis.battle.moves.Field;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lets the battle screen target yourself and your allies with support moves, and shows Stage 2 zones. */
@Mixin(BattleScreen.class)
public abstract class BattleScreenMixin {
    @Shadow private int pending;
    @Shadow private int destX;
    @Shadow private int destZ;
    @Shadow private int selected;

    @Shadow private static boolean mine(JsonObject unit) { return false; }
    @Shadow private static JsonObject unit(int id) { return null; }
    @Shadow private void sendAction(String kind, int target) { }
    @Shadow private static JsonObject state() { return null; }
    @Shadow private Pathing.Occupancy occupancy(JsonObject unit, String side) { return null; }
    @Shadow private void fillCell(GuiGraphicsExtractor g, int x, int z, int color) { }
    @Shadow public abstract double[] cellRect(int x, int z);

    // Tile effects playing now: {cell, kind, start millis}. One battle screen at a time, so static is fine.
    private static final List<long[]> serantis$playing = new ArrayList<>();
    private static long serantis$seen = 0;
    private static int serantis$battle = -1;
    private static final long SERANTIS_FLASH_MS = 650, SERANTIS_SHAKE_MS = 450;

    private static int serantis$zoneColor(String kind) {
        return switch (kind) {
            case "burning" -> 0x66FF6A00;   // orange
            case "ring" -> 0x80FF2A00;      // red-orange
            case "ice" -> 0x5590E0FF;       // pale blue
            case "sink" -> 0x60805A30;      // brown
            case "storm" -> 0x50A0A8FF;     // grey-blue
            case "horizon" -> 0x608040FF;   // violet
            case "night" -> 0x70301848;     // dark purple
            case "meteor" -> 0x80FF0000;    // red: get out
            default -> 0x40FFFFFF;
        };
    }

    // Zones are drawn under everything else the screen marks on the grid.
    @Inject(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Ldev/ahrivaea/client/battle/BattleScreen;unit(I)Lcom/google/gson/JsonObject;", ordinal = 0))
    private void serantis$drawZones(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        JsonObject st = state();
        BattleGrid grid = BattleClient.grid();
        if (st == null || grid == null || !st.has("serantis_zones")) return;
        for (JsonElement el : st.getAsJsonArray("serantis_zones")) {
            JsonObject z = el.getAsJsonObject();
            int color = serantis$zoneColor(z.get("kind").getAsString());
            for (JsonElement c : z.getAsJsonArray("cells")) {
                int i = c.getAsInt();
                fillCell(g, i % grid.size, i / grid.size, color);
            }
        }
        serantis$drawFx(g, st, grid);
    }

    // Attacks shake the attacker's tile; damage, crits, misses, heals and boosts light up the tile in colour.
    private void serantis$drawFx(GuiGraphicsExtractor g, JsonObject st, BattleGrid grid) {
        long now = System.currentTimeMillis();
        if (st.has("serantis_fx_battle") && st.get("serantis_fx_battle").getAsInt() != serantis$battle) {
            serantis$battle = st.get("serantis_fx_battle").getAsInt();
            serantis$seen = 0;
            serantis$playing.clear();
        }
        if (st.has("serantis_fx")) {
            long newest = serantis$seen;
            for (JsonElement el : st.getAsJsonArray("serantis_fx")) {
                JsonObject e = el.getAsJsonObject();
                long id = e.get("id").getAsLong();
                if (id <= serantis$seen) continue;
                serantis$playing.removeIf(p -> p[0] == e.get("cell").getAsLong() && p[1] != Fx.ATTACK && e.get("kind").getAsInt() != Fx.ATTACK);
                serantis$playing.add(new long[]{e.get("cell").getAsLong(), e.get("kind").getAsLong(), now});
                newest = Math.max(newest, id);
            }
            serantis$seen = newest;
        }
        serantis$playing.removeIf(p -> now - p[2] > Math.max(SERANTIS_FLASH_MS, SERANTIS_SHAKE_MS));
        for (long[] p : serantis$playing) {
            int cell = (int) p[0], kind = (int) p[1];
            float t = (now - p[2]) / (float) (kind == Fx.ATTACK ? SERANTIS_SHAKE_MS : SERANTIS_FLASH_MS);
            if (t >= 1f) continue;
            double[] r = cellRect(cell % grid.size, cell / grid.size);
            if (kind == Fx.ATTACK) {
                int dx = (int) Math.round(Math.sin(t * Math.PI * 6) * 4 * (1 - t));
                int alpha = (int) (110 * (1 - t));
                g.fill((int) r[0] + 1 + dx, (int) r[1] + 1, (int) r[2] + dx, (int) r[3], (alpha << 24) | Fx.color(kind));
            } else {
                int alpha = (int) (190 * (1 - t));
                g.fill((int) r[0] + 1, (int) r[1] + 1, (int) r[2], (int) r[3], (alpha << 24) | Fx.color(kind));
            }
        }
    }

    // Frozen Ground and Sinkhole tiles cost 2 movement (matches the server).
    @Inject(method = "reach", at = @At("HEAD"), cancellable = true)
    private void serantis$reach(JsonObject unit, CallbackInfoReturnable<Map<Integer, Integer>> cir) {
        JsonObject st = state();
        BattleGrid grid = BattleClient.grid();
        if (st == null || grid == null || !st.has("serantis_zones") || unit.get("flies").getAsBoolean()) return;
        String side = unit.get("side").getAsString();
        Set<Integer> slow = new HashSet<>();
        for (JsonElement el : st.getAsJsonArray("serantis_zones")) {
            JsonObject z = el.getAsJsonObject();
            String kind = z.get("kind").getAsString();
            if (!("ice".equals(kind) || "sink".equals(kind))) continue;      // slow tiles slow everyone
            for (JsonElement c : z.getAsJsonArray("cells")) slow.add(c.getAsInt());
        }
        if (slow.isEmpty()) return;
        cir.setReturnValue(Field.reach(grid, unit.get("x").getAsInt(), unit.get("z").getAsInt(), unit.get("squares").getAsInt(),
                false, unit.get("lava").getAsBoolean(), occupancy(unit, side), slow::contains));
    }

    private static String serantis$targetType(JsonObject actor, int move) {
        if (actor == null || move < 0 || !actor.has("moves") || move >= actor.getAsJsonArray("moves").size()) return null;
        JsonObject m = actor.getAsJsonArray("moves").get(move).getAsJsonObject();
        return m.has("serantis_target") ? m.get("serantis_target").getAsString() : null;
    }

    @Inject(method = "validTarget", at = @At("HEAD"), cancellable = true)
    private void serantis$validTarget(JsonObject actor, JsonObject target, CallbackInfoReturnable<Boolean> cir) {
        String type = serantis$targetType(actor, pending);
        if (type == null || "enemy".equals(type)) return;
        if (target == null || target.get("hp").getAsInt() <= 0) { cir.setReturnValue(false); return; }
        JsonObject m = actor.getAsJsonArray("moves").get(pending).getAsJsonObject();
        boolean self = target.get("id").getAsInt() == actor.get("id").getAsInt();
        int d = Pathing.distance(destX, destZ, target.get("x").getAsInt(), target.get("z").getAsInt());
        boolean inRange = d >= m.get("min").getAsInt() && d <= m.get("max").getAsInt();
        boolean ok = switch (type) {
            case "self" -> self;
            case "ally" -> !self && mine(target) && inRange;
            case "self_or_ally" -> self || (mine(target) && inRange);
            case "any" -> !self && inRange;
            default -> false;
        };
        cir.setReturnValue(ok);
    }

    // Moves that only affect the user happen as soon as they're chosen.
    @Inject(method = "chooseMove", at = @At("HEAD"), cancellable = true)
    private void serantis$chooseMove(int move, CallbackInfo ci) {
        JsonObject actor = unit(selected);
        if (!"self".equals(serantis$targetType(actor, move))) return;
        pending = move;
        sendAction("attack", actor.get("id").getAsInt());
        ci.cancel();
    }
}
