package dev.serantis.battle.mixin.client;

import com.google.gson.JsonObject;
import dev.ahrivaea.battle.Pathing;
import dev.ahrivaea.client.battle.BattleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the battle screen target yourself and your allies with support moves. */
@Mixin(BattleScreen.class)
public abstract class BattleScreenMixin {
    @Shadow private int pending;
    @Shadow private int destX;
    @Shadow private int destZ;
    @Shadow private int selected;

    @Shadow private static boolean mine(JsonObject unit) { return false; }
    @Shadow private static JsonObject unit(int id) { return null; }
    @Shadow private void sendAction(String kind, int target) { }

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
