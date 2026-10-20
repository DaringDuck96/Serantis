package dev.serantis.battle.mixin;

import com.google.gson.JsonObject;
import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.BattleMove;
import dev.ahrivaea.battle.DamageCalc;
import dev.ahrivaea.battle.Unit;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Variant;
import dev.serantis.battle.moves.BattleAccess;
import dev.serantis.battle.moves.MoveBook;
import dev.serantis.battle.moves.MoveEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Hooks Serantis moves into the creatures mod's grid battles. */
@Mixin(Battle.class)
public abstract class BattleMixin implements BattleAccess {
    @Shadow @Final private List<Unit> enemyQueue;
    @Shadow @Final public List<Battle.Defeated> defeated;

    @Shadow private void strike(Unit attacker, Unit target, BattleMove move) { }
    @Shadow private void knockOut(Unit unit) { }
    @Shadow void say(String line) { }
    @Shadow DamageCalc.Result preview(Unit attacker, Unit target, BattleMove move) { return null; }
    @Shadow boolean checkEnd() { return false; }

    @Unique private Battle.Phase serantis$lastPhase;
    @Unique private Map<Object, String> serantis$defeatedMoves;
    @Unique private int serantis$defeatedBefore;

    // ---- BattleAccess
    @Override public void serantis$strike(Unit a, Unit t, BattleMove m) { strike(a, t, m); }
    @Override public void serantis$knockOut(Unit u) { knockOut(u); }
    @Override public void serantis$say(String line) { say(line); }
    @Override public DamageCalc.Result serantis$preview(Unit a, Unit t, BattleMove m) { return preview(a, t, m); }
    @Override public boolean serantis$checkEnd() { return checkEnd(); }
    @Override public List<Unit> serantis$enemyQueue() { return enemyQueue; }
    @Override public Battle.Phase serantis$lastPhase() { return serantis$lastPhase; }
    @Override public void serantis$setLastPhase(Battle.Phase phase) { serantis$lastPhase = phase; }
    @Override public Map<Object, String> serantis$defeatedMoves() {
        if (serantis$defeatedMoves == null) serantis$defeatedMoves = new IdentityHashMap<>();
        return serantis$defeatedMoves;
    }

    // ---- using moves
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void serantis$attack(Unit attacker, Unit target, BattleMove move, boolean counter, CallbackInfo ci) {
        if (MoveBook.of(move) == null) return;
        MoveEngine.use((Battle) (Object) this, attacker, target, move, counter);
        ci.cancel();
    }

    @Inject(method = "bestMove", at = @At("HEAD"), cancellable = true)
    private void serantis$bestMove(Unit unit, Unit target, int distance, CallbackInfoReturnable<BattleMove> cir) {
        cir.setReturnValue(MoveEngine.bestMove((Battle) (Object) this, unit, target, distance));
    }

    @Inject(method = "preview", at = @At("RETURN"), cancellable = true)
    private void serantis$preview(Unit attacker, Unit target, BattleMove move, CallbackInfoReturnable<DamageCalc.Result> cir) {
        cir.setReturnValue(MoveEngine.adjustPreview((Battle) (Object) this, attacker, target, move, cir.getReturnValue()));
    }

    // ---- movement range (server pathing and what the battle screen is told)
    @Redirect(method = {"reachable", "view"}, at = @At(value = "INVOKE", target = "Ldev/ahrivaea/battle/Unit;squares()I"))
    private int serantis$squares(Unit unit) {
        return MoveEngine.squares(unit, unit.squares());
    }

    // ---- player actions
    @Inject(method = "act", at = @At("HEAD"), cancellable = true)
    private void serantis$beforeAct(Battle.Action action, CallbackInfoReturnable<String> cir) {
        String why = MoveEngine.beforeAct((Battle) (Object) this, action);
        if (why != null) cir.setReturnValue(why);
    }

    // The creatures mod only lets you target enemies; self and ally moves are allowed through here.
    // In act() the second read of Unit.side is the target's, compared with the acting unit's.
    @Redirect(method = "act", at = @At(value = "FIELD", target = "Ldev/ahrivaea/battle/Unit;side:Ldev/ahrivaea/battle/Unit$Side;", opcode = 180, ordinal = 1))
    private Unit.Side serantis$targetSide(Unit target, Battle.Action action) {
        return MoveEngine.sideForTargetCheck((Battle) (Object) this, target, action);
    }

    // ---- turn changes
    @Inject(method = "startEnemyPhase", at = @At("RETURN"))
    private void serantis$enemyPhase(CallbackInfo ci) { MoveEngine.sync((Battle) (Object) this); }

    @Inject(method = "enemyStep", at = @At("RETURN"))
    private void serantis$afterEnemyStep(CallbackInfoReturnable<Boolean> cir) { MoveEngine.sync((Battle) (Object) this); }

    @Inject(method = "view", at = @At("HEAD"))
    private void serantis$beforeView(UUID viewer, CallbackInfoReturnable<JsonObject> cir) { MoveEngine.sync((Battle) (Object) this); }

    @Inject(method = "view", at = @At("RETURN"))
    private void serantis$afterView(UUID viewer, CallbackInfoReturnable<JsonObject> cir) { MoveEngine.decorate((Battle) (Object) this, cir.getReturnValue()); }

    // ---- move sets
    @Inject(method = "addCreature(Ldev/ahrivaea/battle/Unit$Side;Ldev/ahrivaea/creature/Species;Ldev/ahrivaea/creature/Influence;Ldev/ahrivaea/creature/Variant;IFIII)Ldev/ahrivaea/battle/Unit;",
            at = @At("RETURN"))
    private void serantis$newUnit(Unit.Side side, Species species, Influence influence, Variant variant, int level, float hp,
                                  int x, int z, int slot, CallbackInfoReturnable<Unit> cir) {
        if (cir.getReturnValue() != null) MoveEngine.assignNewUnit((Battle) (Object) this, cir.getReturnValue());
    }

    // Remember a knocked-out creature's moves so recruiting it keeps them.
    @Inject(method = "knockOut", at = @At("HEAD"))
    private void serantis$beforeKnockOut(Unit unit, CallbackInfo ci) { serantis$defeatedBefore = defeated.size(); }

    @Inject(method = "knockOut", at = @At("RETURN"))
    private void serantis$afterKnockOut(Unit unit, CallbackInfo ci) {
        if (defeated.size() > serantis$defeatedBefore) {
            String moves = MoveEngine.encodeUnit(unit);
            if (moves != null) serantis$defeatedMoves().put(defeated.get(defeated.size() - 1), moves);
        }
    }
}
