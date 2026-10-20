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
import dev.serantis.battle.moves.EnemyBrain;
import dev.serantis.battle.moves.Field;
import dev.serantis.battle.moves.Body;
import dev.ahrivaea.battle.BattleGrid;
import dev.serantis.battle.fx.Fx;
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
    @Shadow private void attack(Unit attacker, Unit target, BattleMove move, boolean counter) { }
    @Shadow private void knockOut(Unit unit) { }
    @Shadow void say(String line) { }
    @Shadow DamageCalc.Result preview(Unit attacker, Unit target, BattleMove move) { return null; }
    @Shadow boolean checkEnd() { return false; }

    @Unique private Battle.Phase serantis$lastPhase;
    @Unique private Map<Object, String> serantis$defeatedMoves;
    @Unique private int serantis$defeatedBefore;
    @Unique private Field serantis$field;
    @Unique private boolean serantis$struck, serantis$crit;

    @Override public Field serantis$field() {
        if (serantis$field == null) serantis$field = new Field();
        return serantis$field;
    }

    // ---- BattleAccess
    @Override public void serantis$strike(Unit a, Unit t, BattleMove m) { strike(a, t, m); }
    @Override public void serantis$attack(Unit a, Unit t, BattleMove m, boolean counter) { attack(a, t, m, counter); }
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
        if (MoveBook.of(move) == null) { Fx.wiggle((Battle) (Object) this, attacker); return; }
        MoveEngine.use((Battle) (Object) this, attacker, target, move, counter);
        ci.cancel();
    }

    // ---- strikes: the flyer bonus, and flashes for hits, crits and misses
    @Inject(method = "strike", at = @At("HEAD"))
    private void serantis$beforeStrike(Unit attacker, Unit target, BattleMove move, CallbackInfo ci) {
        serantis$struck = false;
        serantis$crit = false;
    }

    @Redirect(method = "strike", at = @At(value = "INVOKE",
            target = "Ldev/ahrivaea/battle/DamageCalc;calculate(Ldev/ahrivaea/battle/DamageCalc$Fighter;Ldev/ahrivaea/battle/DamageCalc$Fighter;Ldev/ahrivaea/battle/DamageCalc$Move;ZDDLdev/ahrivaea/battle/DamageCalc$Extras;)Ldev/ahrivaea/battle/DamageCalc$Result;"))
    private DamageCalc.Result serantis$calculate(DamageCalc.Fighter a, DamageCalc.Fighter d, DamageCalc.Move m, boolean crit,
                                                 double x, double y, DamageCalc.Extras extras,
                                                 Unit attacker, Unit target, BattleMove move) {
        serantis$struck = true;
        serantis$crit = crit;
        return MoveEngine.flyerBonus(target, move, DamageCalc.calculate(a, d, m, crit, x, y, extras));
    }

    @Inject(method = "strike", at = @At("RETURN"))
    private void serantis$afterStrike(Unit attacker, Unit target, BattleMove move, CallbackInfo ci) {
        Battle self = (Battle) (Object) this;
        Fx.flash(self, target, !serantis$struck ? Fx.MISS : serantis$crit ? Fx.CRIT : Fx.DAMAGE);
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
        return MoveEngine.squares((Battle) (Object) this, unit, unit.squares());
    }

    // ---- bodies bigger than one tile, and terrain that costs extra movement (see Body)
    @Inject(method = "reachable", at = @At("HEAD"), cancellable = true)
    private void serantis$reach(Unit unit, CallbackInfoReturnable<Map<Integer, Integer>> cir) {
        Battle self = (Battle) (Object) this;
        cir.setReturnValue(Body.reach(self, unit, MoveEngine.squares(self, unit, unit.squares())));
    }

    @Inject(method = "unitAt", at = @At("HEAD"), cancellable = true)
    private void serantis$unitAt(int x, int z, CallbackInfoReturnable<Unit> cir) {
        cir.setReturnValue(Body.unitAt((Battle) (Object) this, x, z));
    }

    @Inject(method = "canStop", at = @At("HEAD"), cancellable = true)
    private void serantis$canStop(Unit unit, int x, int z, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(Body.canStop((Battle) (Object) this, unit, x, z));
    }

    // Ranges, adjacency and item reach are measured between bodies, not corner tiles.
    @Redirect(method = {"act", "attack", "strike", "preview", "itemCheck", "useItem"},
            at = @At(value = "INVOKE", target = "Ldev/ahrivaea/battle/Pathing;distance(IIII)I"))
    private int serantis$distance(int x1, int z1, int x2, int z2) {
        return Body.distance((Battle) (Object) this, x1, z1, x2, z2);
    }

    // High ground: the highest tile under each body. Flyers are never on the ground, so never have high ground.
    @Redirect(method = {"strike", "preview"}, at = @At(value = "INVOKE", target = "Ldev/ahrivaea/battle/BattleGrid;y(II)I", ordinal = 0))
    private int serantis$attackerHeight(BattleGrid grid, int x, int z, Unit attacker, Unit target, BattleMove move) {
        return attacker.flies() ? -100000 : Body.height((Battle) (Object) this, attacker);
    }

    @Redirect(method = {"strike", "preview"}, at = @At(value = "INVOKE", target = "Ldev/ahrivaea/battle/BattleGrid;y(II)I", ordinal = 1))
    private int serantis$targetHeight(BattleGrid grid, int x, int z, Unit attacker, Unit target, BattleMove move) {
        return Body.height((Battle) (Object) this, target);
    }

    // ---- player actions
    @Inject(method = "act", at = @At("HEAD"), cancellable = true)
    private void serantis$beforeAct(Battle.Action action, CallbackInfoReturnable<String> cir) {
        String why = MoveEngine.beforeAct((Battle) (Object) this, action);
        if (why != null) cir.setReturnValue(why);
    }

    // After a player's creature moves: burning ground and the Inferno Ring hurt it on the way.
    @Inject(method = "act", at = @At("RETURN"))
    private void serantis$afterAct(Battle.Action action, CallbackInfoReturnable<String> cir) {
        if (cir.getReturnValue() != null) return;
        Battle self = (Battle) (Object) this;
        Unit u = self.unit(action.unit());
        if (u == null || !u.alive()) return;
        var st = MoveEngine.state(u);
        int tx = action.toX(), tz = action.toZ();
        if (st.startX == Integer.MIN_VALUE || (st.startX == tx && st.startZ == tz)) return;
        Field field = serantis$field();
        field.enter(self, u, field.walked(self, u, st.startX, st.startZ, tx, tz));
        checkEnd();
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

    // Each enemy acts according to its mentality (EnemyBrain). When the queue is empty the creatures mod
    // ends the enemy phase as usual.
    @Inject(method = "enemyStep", at = @At("HEAD"), cancellable = true)
    private void serantis$enemyStep(CallbackInfoReturnable<Boolean> cir) {
        Battle self = (Battle) (Object) this;
        while (!enemyQueue.isEmpty() && !enemyQueue.getFirst().alive()) enemyQueue.removeFirst();
        if (enemyQueue.isEmpty() || self.phase != Battle.Phase.ENEMY) return;
        Unit unit = enemyQueue.removeFirst();
        EnemyBrain.act(self, unit);
        checkEnd();
        cir.setReturnValue(self.phase == Battle.Phase.ENEMY);
    }

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
