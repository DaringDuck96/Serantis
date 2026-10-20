package dev.serantis.battle.mixin.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import dev.ahrivaea.battle.BattleGrid;
import dev.ahrivaea.battle.Pathing;
import dev.ahrivaea.client.battle.BattleClient;
import dev.ahrivaea.client.battle.BattleScreen;
import dev.serantis.battle.fx.Fx;
import dev.serantis.battle.moves.Body;
import dev.serantis.battle.client.MovePanel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.injection.Redirect;
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
    @Shadow private static List<JsonObject> units() { return null; }
    @Shadow private Pathing.Occupancy occupancy(JsonObject unit, String side) { return null; }
    @Shadow private void fillCell(GuiGraphicsExtractor g, int x, int z, int color) { }
    @Shadow public abstract double[] cellRect(int x, int z);
    @Shadow private static JsonObject unitAt(int x, int z) { return null; }

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
        List<JsonObject> all = units();
        if (all != null)
            for (JsonObject u : all) {
                int n = serantis$size(u);
                if (n <= 1 || u.get("hp").getAsInt() <= 0) continue;
                int color = "PLAYER".equals(u.get("side").getAsString()) ? 0x3A4C8CFF : 0x3AFF5A4C;
                int ux = u.get("x").getAsInt(), uz = u.get("z").getAsInt();
                for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) fillCell(g, ux + i, uz + j, color);
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
            serantis$pinned = -1;
        }
        if (st.has("serantis_fx")) {
            long newest = serantis$seen;
            for (JsonElement el : st.getAsJsonArray("serantis_fx")) {
                JsonObject e = el.getAsJsonObject();
                long id = e.get("id").getAsLong();
                if (id <= serantis$seen) continue;
                serantis$playing.removeIf(p -> p[0] == e.get("cell").getAsLong() && p[1] != Fx.ATTACK && e.get("kind").getAsInt() != Fx.ATTACK);
                serantis$playing.add(new long[]{e.get("cell").getAsLong(), e.get("kind").getAsLong(), now, e.has("size") ? e.get("size").getAsLong() : 1});
                newest = Math.max(newest, id);
            }
            serantis$seen = newest;
        }
        serantis$playing.removeIf(p -> now - p[2] > Math.max(SERANTIS_FLASH_MS, SERANTIS_SHAKE_MS));
        for (long[] p : serantis$playing) {
            int cell = (int) p[0], kind = (int) p[1];
            float t = (now - p[2]) / (float) (kind == Fx.ATTACK ? SERANTIS_SHAKE_MS : SERANTIS_FLASH_MS);
            if (t >= 1f) continue;
            int n = (int) p[3];
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++) {
                    int x = cell % grid.size + i, z = cell / grid.size + j;
                    if (!grid.inside(x, z)) continue;
                    double[] r = cellRect(x, z);
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
    }

    // ---- bodies bigger than one tile, and terrain costs (the same rules as the server, see Body)
    private static int serantis$size(JsonObject u) {
        return u != null && u.has("serantis_size") ? u.get("serantis_size").getAsInt() : 1;
    }

    /** Distance from the selected creature (at the tile it's moving to) to a target, body to body. */
    private int serantis$gap(JsonObject actor, JsonObject target) {
        return Body.gap(destX, destZ, serantis$size(actor), target.get("x").getAsInt(), target.get("z").getAsInt(), serantis$size(target));
    }

    private Body.Ctx serantis$ctx(JsonObject unit) {
        JsonObject st = state();
        BattleGrid grid = BattleClient.grid();
        Pathing.Occupancy occ = occupancy(unit, unit.get("side").getAsString());
        Set<Integer> slow = new HashSet<>();
        if (st != null && st.has("serantis_zones"))
            for (JsonElement el : st.getAsJsonArray("serantis_zones")) {
                JsonObject z = el.getAsJsonObject();
                String kind = z.get("kind").getAsString();
                if (!("ice".equals(kind) || "sink".equals(kind))) continue;
                for (JsonElement c : z.getAsJsonArray("cells")) slow.add(c.getAsInt());
            }
        return Body.ctx(grid, occ, slow::contains);
    }

    @Inject(method = "unitAt", at = @At("HEAD"), cancellable = true)
    private static void serantis$unitAt(int x, int z, CallbackInfoReturnable<JsonObject> cir) {
        List<JsonObject> all = units();
        if (all == null) return;
        for (JsonObject u : all)
            if (u.get("hp").getAsInt() > 0 && Body.covers(u.get("x").getAsInt(), u.get("z").getAsInt(), serantis$size(u), x, z)) {
                cir.setReturnValue(u);
                return;
            }
        cir.setReturnValue(null);
    }

    @Inject(method = "canStop", at = @At("HEAD"), cancellable = true)
    private void serantis$canStop(JsonObject unit, int x, int z, CallbackInfoReturnable<Boolean> cir) {
        if (BattleClient.grid() == null) return;
        cir.setReturnValue(Body.canStop(serantis$ctx(unit), x, z, serantis$size(unit),
                unit.get("flies").getAsBoolean(), unit.get("lava").getAsBoolean()));
    }

    @Inject(method = "reach", at = @At("HEAD"), cancellable = true)
    private void serantis$reach(JsonObject unit, CallbackInfoReturnable<Map<Integer, Integer>> cir) {
        if (BattleClient.grid() == null) return;
        boolean swims = unit.has("serantis_swims") && unit.get("serantis_swims").getAsBoolean();
        cir.setReturnValue(Body.reach(serantis$ctx(unit), unit.get("x").getAsInt(), unit.get("z").getAsInt(), serantis$size(unit),
                unit.get("squares").getAsInt(), unit.get("flies").getAsBoolean(), unit.get("lava").getAsBoolean(), swims));
    }

    @Redirect(method = "validTarget", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/battle/Pathing;distance(IIII)I"))
    private int serantis$targetDistance(int x1, int z1, int x2, int z2, JsonObject actor, JsonObject target) {
        return target == null ? Pathing.distance(x1, z1, x2, z2) : serantis$gap(actor, target);
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
        int d = serantis$gap(actor, target);
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

    // After recruiting a creature, offer to name it.
    @Inject(method = "recruit", at = @At("TAIL"))
    private void serantis$nameRecruit(int index, CallbackInfo ci) {
        JsonObject st = state();
        if (st == null || !st.has("result") || !st.get("result").isJsonObject()) return;
        JsonObject result = st.getAsJsonObject("result");
        if (!result.has("recruits")) return;
        var list = result.getAsJsonArray("recruits");
        if (index < 0 || index >= list.size()) return;
        JsonObject e = list.get(index).getAsJsonObject();
        if (e.has("why") && !e.get("why").isJsonNull() && !e.get("why").getAsString().isEmpty()) return;   // can't recruit this one
        dev.serantis.battle.client.RenameScreen.open((net.minecraft.client.gui.screens.Screen) (Object) this,
                e.has("name") ? e.get("name").getAsString() : "creature");
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

    // ---- the creature card: every move with what it actually does. Hover to see it; click a creature to pin it.
    private static int serantis$pinned = -1;
    private static boolean serantis$hoverDrawn, serantis$pinShown;
    private static int serantis$mouseX, serantis$mouseY;

    private static boolean serantis$overCard(double x, double y) {
        int[] r = MovePanel.RECT;
        return r[2] > 0 && x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    private void serantis$card(GuiGraphicsExtractor g, JsonObject unit, boolean pinned) {
        Screen s = (Screen) (Object) this;
        MovePanel.draw(g, unit, 4, s.height - 4, s.width / 2 - 10, s.height - 40,
                pinned ? "Pinned · click this card to close it" : "Click the creature to pin this card");
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void serantis$frameStart(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        serantis$mouseX = mouseX;
        serantis$mouseY = mouseY;
        serantis$hoverDrawn = false;
    }

    @Inject(method = "drawDetails", at = @At("HEAD"), cancellable = true)
    private void serantis$details(GuiGraphicsExtractor g, JsonObject unit, int x, int bottom, CallbackInfo ci) {
        ci.cancel();
        // Reading the pinned card: don't swap it for whatever creature is under the mouse.
        if (serantis$pinShown && serantis$pinned >= 0 && serantis$overCard(serantis$mouseX, serantis$mouseY)) return;
        boolean pinned = unit.has("id") && unit.get("id").getAsInt() == serantis$pinned;
        serantis$card(g, unit, pinned);
        serantis$hoverDrawn = true;
        serantis$pinShown = pinned;
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void serantis$pinnedCard(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (serantis$hoverDrawn) return;
        serantis$pinShown = false;
        JsonObject st = state();
        if (serantis$pinned < 0 || st == null || (st.has("result") && st.get("result").isJsonObject())) { MovePanel.RECT[2] = 0; return; }
        JsonObject u = unit(serantis$pinned);
        if (u == null || u.get("hp").getAsInt() <= 0) { serantis$pinned = -1; MovePanel.RECT[2] = 0; return; }
        serantis$card(g, u, true);
        serantis$pinShown = true;
    }

    @Inject(method = "clickCell", at = @At("HEAD"))
    private void serantis$pin(int x, int z, CallbackInfo ci) {
        JsonObject u = unitAt(x, z);
        if (u != null && u.has("id")) serantis$pinned = u.get("id").getAsInt();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void serantis$closeCard(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (serantis$pinShown && serantis$pinned >= 0 && serantis$overCard(event.x(), event.y())) {
            for (var child : ((Screen) (Object) this).children())
                if (child.isMouseOver(event.x(), event.y())) return;   // buttons stay clickable
            serantis$pinned = -1;
            serantis$pinShown = false;
            MovePanel.RECT[2] = 0;
            cir.setReturnValue(true);
        }
    }
}
