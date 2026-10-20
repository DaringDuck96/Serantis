package dev.serantis.battle.mixin.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import dev.ahrivaea.battle.BattleGrid;
import dev.ahrivaea.battle.Pathing;
import dev.ahrivaea.client.battle.BattleClient;
import dev.ahrivaea.client.battle.BattleScreen;
import dev.serantis.battle.moves.Field;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
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
