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
    @Unique private double serantis$mult = 1;
    @Unique private int serantis$hpBefore;

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
    @Unique private Map<Object, String> serantis$defeatedTemperaments;
    @Override public Map<Object, String> serantis$defeatedTemperaments() {
        if (serantis$defeatedTemperaments == null) serantis$defeatedTemperaments = new IdentityHashMap<>();
        return serantis$defeatedTemperaments;
    }
    @Unique private Map<Object, dev.ahrivaea.villager.VillagerJob> serantis$defeatedJobs;
    @Unique private java.util.Set<Object> serantis$defeatedGuards;
    @Override public Map<Object, dev.ahrivaea.villager.VillagerJob> serantis$defeatedJobs() {
        if (serantis$defeatedJobs == null) serantis$defeatedJobs = new IdentityHashMap<>();
        return serantis$defeatedJobs;
    }
    @Override public java.util.Set<Object> serantis$defeatedGuards() {
        if (serantis$defeatedGuards == null) serantis$defeatedGuards = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        return serantis$defeatedGuards;
    }

    // Town Guards can't be recruited, nor can a Creature Master or its team.
    @Inject(method = "whyNotRecruit", at = @At("HEAD"), cancellable = true)
    private void serantis$noGuards(Battle.Defeated d, CallbackInfoReturnable<String> cir) {
        if (serantis$master != null) cir.setReturnValue("A Creature Master's team can't be recruited.");
        else if (serantis$defeatedGuards != null && serantis$defeatedGuards.contains(d)) cir.setReturnValue("Town Guards can't be recruited.");
    }

    // ---- Creature Masters: double XP and a Gym Master Stone for beating one
    @Unique private String serantis$master;
    @Override public String serantis$master() { return serantis$master; }
    @Override public void serantis$setMaster(String influence) { serantis$master = influence; }

    // Performers rally twice as hard.
    @Redirect(method = "act", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/battle/Unit;charisma()D"))
    private double serantis$performerRally(Unit u) {
        double c = u.charisma();
        return u.isPlayer && dev.serantis.battle.PlayerClass.is(dev.serantis.battle.coop.CoopBattles.playerOf((Battle) (Object) this, u), "performer") ? c * dev.serantis.battle.PlayerClass.PERFORMER_RALLY : c;
    }

    // ---- co-op (see CoopBattles): only your own units; the battle goes on while any player stands; spars are friendly
    @Inject(method = "checkEnd", at = @At("HEAD"), cancellable = true)
    private void serantis$coopEnd(CallbackInfoReturnable<Boolean> cir) {
        Boolean end = dev.serantis.battle.coop.CoopBattles.checkEnd((Battle) (Object) this);
        if (end != null) cir.setReturnValue(end);
    }

    @Inject(method = "finish", at = @At("HEAD"))
    private void serantis$sparFinish(Battle.Outcome outcome, CallbackInfo ci) {
        if (dev.serantis.battle.coop.CoopBattles.isSpar((Battle) (Object) this)) defeated.clear();   // no loot or recruits from a spar
    }

    // A spar win isn't a battle won (no Battles Won progress): its only prize is Bragging Rights.
    @Redirect(method = "finish", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/villager/Villagers;award(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/resources/Identifier;)V"))
    private void serantis$noSparCredit(net.minecraft.server.level.ServerPlayer p, net.minecraft.resources.Identifier stat) {
        if (!dev.serantis.battle.coop.CoopBattles.isSpar((Battle) (Object) this)) dev.ahrivaea.villager.Villagers.award(p, stat);
    }

    // ...and no night-time ender pearl either.
    @Redirect(method = "finish", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isDarkOutside()Z"))
    private boolean serantis$noSparPearl(net.minecraft.world.level.Level level) {
        return !dev.serantis.battle.coop.CoopBattles.isSpar((Battle) (Object) this) && level.isDarkOutside();
    }

    // Creatures that fell are knocked out for 5 minutes (see Knockout).
    @Inject(method = "saveParty", at = @At("RETURN"))
    private void serantis$knockouts(net.minecraft.server.level.ServerPlayer p, CallbackInfo ci) {
        dev.serantis.battle.Knockout.afterBattle((Battle) (Object) this, p);
        dev.serantis.battle.Ailments.afterBattle((Battle) (Object) this, p);   // statuses linger a while
    }

    @Inject(method = "saveParty", at = @At("HEAD"), cancellable = true)
    private void serantis$sparParty(net.minecraft.server.level.ServerPlayer p, CallbackInfo ci) {
        if (dev.serantis.battle.coop.CoopBattles.skipSaveParty((Battle) (Object) this)) ci.cancel();
    }

    // A bucket of milk cures statuses in battle (see Ailments): the Item action on a unit with a status uses milk if
    // you have some (the bucket is used up), otherwise a healing potion as before.
    // The battle screen's Milk action is the Item action with move 1 (Potion is move 0).
    @Inject(method = "act", at = @At("HEAD"))
    private void serantis$whichItem(Battle.Action action, CallbackInfoReturnable<String> cir) {
        dev.serantis.battle.Ailments.WANT_MILK.set("item".equals(action.kind()) && action.move() == 1);
    }

    @Inject(method = "act", at = @At("RETURN"))
    private void serantis$whichItemDone(Battle.Action action, CallbackInfoReturnable<String> cir) {
        dev.serantis.battle.Ailments.WANT_MILK.set(false);
    }

    @Inject(method = "itemCheck", at = @At("HEAD"), cancellable = true)
    private void serantis$milkCheck(Unit u, Unit target, CallbackInfoReturnable<String> cir) {
        if (!dev.serantis.battle.Ailments.WANT_MILK.get()) return;      // a potion, as before
        cir.setReturnValue(dev.serantis.battle.Ailments.milkProblem((Battle) (Object) this, u, target));
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void serantis$milk(Unit u, Unit target, CallbackInfo ci) {
        if (dev.serantis.battle.Ailments.WANT_MILK.get() && dev.serantis.battle.Ailments.useMilk((Battle) (Object) this, u, target)) ci.cancel();
    }

    // Potions come from whoever is acting (an ally uses their own).
    @Redirect(method = {"itemCheck", "useItem"}, at = @At(value = "INVOKE", target = "Ldev/ahrivaea/battle/Battle;player()Lnet/minecraft/server/level/ServerPlayer;"))
    private net.minecraft.server.level.ServerPlayer serantis$itemUser(Battle self) {
        return dev.serantis.battle.coop.CoopBattles.itemUser(self);
    }

    @Redirect(method = "finish", at = @At(value = "FIELD", target = "Ldev/ahrivaea/battle/Battle;poolXp:I", opcode = 181))
    private void serantis$masterXp(Battle self, int xp) {
        // 10 for the win, plus for each creature beaten 3 x its level (3 XP at Level 1, 60 at Level 20), x1.5 if Super Rare or Legendary; then x1.5 overall.
        int sum = 10;
        for (Battle.Defeated d : self.defeated) {
            String r = dev.serantis.battle.Apples.rarity(d.species(), d.influence());
            double f = "Legendary".equals(r) || "Super Rare".equals(r) ? 1.5 : 1.0;
            sum += (int) Math.round(3 * Math.max(1, d.level()) * f);
        }
        sum = (int) Math.round(sum * 1.5);                              // battle XP is 1.5x (all of the above)
        if (!self.defeated.isEmpty()) { xp = sum; self.characterXp = sum; }
        self.poolXp = serantis$master != null ? xp * 2 : xp;
        if (dev.serantis.battle.coop.CoopBattles.isSpar(self)) { self.poolXp = 0; self.characterXp = 0; }
    }

    @Inject(method = "finish", at = @At("HEAD"))
    private void serantis$masterBeaten(Battle.Outcome outcome, CallbackInfo ci) {
        Battle self = (Battle) (Object) this;
        if (serantis$master != null && outcome == Battle.Outcome.WON && self.player() != null)
            dev.serantis.battle.Masters.reward(self.player(), serantis$master);
        if (serantis$master != null && outcome == Battle.Outcome.WON)          // a beaten Master moves on
            for (Unit u : self.units)
                if (u.entity != null && self.level.getEntity(u.entity) instanceof dev.ahrivaea.creature.CreatureEntity c && dev.serantis.battle.Masters.isMaster(c))
                    ((dev.serantis.battle.Market.Holder) c).serantis$setLeaveAt(self.level.getGameTime()
                            + (dev.serantis.battle.Masters.giftPending(self.player()) ? 20 * 45 : 0));   // its End Crystal (first win): it stays to explain
    }

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
        serantis$mult = 1;
        serantis$hpBefore = target.hp;
    }

    @Redirect(method = "strike", at = @At(value = "INVOKE",
            target = "Ldev/ahrivaea/battle/DamageCalc;calculate(Ldev/ahrivaea/battle/DamageCalc$Fighter;Ldev/ahrivaea/battle/DamageCalc$Fighter;Ldev/ahrivaea/battle/DamageCalc$Move;ZDDLdev/ahrivaea/battle/DamageCalc$Extras;)Ldev/ahrivaea/battle/DamageCalc$Result;"))
    private DamageCalc.Result serantis$calculate(DamageCalc.Fighter a, DamageCalc.Fighter d, DamageCalc.Move m, boolean crit,
                                                 double x, double y, DamageCalc.Extras extras,
                                                 Unit attacker, Unit target, BattleMove move) {
        serantis$struck = true;
        serantis$crit = crit;
        DamageCalc.Result r = MoveEngine.flyerBonus(target, move, DamageCalc.calculate(a, d, m, crit, x, y, extras));
        serantis$mult = r.multiplier();
        return dev.serantis.battle.skills.SkillEffects.battle((Battle) (Object) this, attacker, target, move, crit, r);
    }

    @Inject(method = "strike", at = @At("RETURN"))
    private void serantis$afterStrike(Unit attacker, Unit target, BattleMove move, CallbackInfo ci) {
        Battle self = (Battle) (Object) this;
        Fx.flash(self, target, !serantis$struck ? Fx.MISS : serantis$crit ? Fx.CRIT : Fx.DAMAGE);
        if (serantis$struck && serantis$mult > 1.0) Fx.flash(self, target, Fx.SUPER);
        else if (serantis$struck && serantis$mult < 1.0) Fx.flash(self, target, Fx.WEAK);
        Fx.hitSound(self, target, move.move().influence(), serantis$struck, serantis$crit);
        if (serantis$struck && MoveBook.of(move) == null && target.hp < serantis$hpBefore)   // weapon hits (catalog moves do this themselves)
            MoveEngine.afterDamage(self, attacker, target, serantis$hpBefore - target.hp);
    }

    // Losing a battle wears down the player's armour and held items (XP is never lost).
    @Inject(method = "finish", at = @At("HEAD"))
    private void serantis$defeat(Battle.Outcome outcome, CallbackInfo ci) {
        Battle self = (Battle) (Object) this;
        if (dev.serantis.battle.coop.CoopBattles.isSpar(self)) return;   // a friendly spar counts for nothing
        if (outcome == Battle.Outcome.LOST && self.player() != null) dev.serantis.battle.Wear.defeat(self.player(), "You were defeated.");
        dev.serantis.battle.GoblinRaids.finished(self, outcome);
        dev.serantis.battle.FigureVisit.finished(self, outcome);
        if (outcome == Battle.Outcome.WON) dev.serantis.battle.Bounties.battleWon(self.player(), self.defeated, serantis$master != null);
        if (outcome != Battle.Outcome.FLED) dev.serantis.battle.FigureVisit.battleOver(self.player());
    }

    // The player's Swiftness nodes add battle movement.
    @Inject(method = "addPlayer", at = @At("RETURN"))
    private void serantis$playerTiles(net.minecraft.server.level.ServerPlayer player, int x, int z, CallbackInfoReturnable<Unit> cir) {
        if (cir.getReturnValue() != null) MoveEngine.state(cir.getReturnValue()).bonusTiles = dev.serantis.battle.skills.Skills.tiles(player);
        if (cir.getReturnValue() != null && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(dev.serantis.battle.TeleportBoots.BOOTS))
            MoveEngine.state(cir.getReturnValue()).flight = true;        // Teleporting Boots: Flight in battle
    }

    // Fortune: creatures beaten in battle may drop twice, and rare drops come up more often.
    @Redirect(method = "finish", at = @At(value = "INVOKE",
            target = "Ldev/ahrivaea/creature/Drops;roll(Ldev/ahrivaea/creature/Species;Ldev/ahrivaea/creature/Influence;ILnet/minecraft/util/RandomSource;Lnet/minecraft/core/RegistryAccess;)Ljava/util/List;"))
    private List<net.minecraft.world.item.ItemStack> serantis$loot(dev.ahrivaea.creature.Species species, Influence influence, int level,
                                                                  net.minecraft.util.RandomSource random, net.minecraft.core.RegistryAccess access) {
        return dev.serantis.battle.skills.SkillEffects.creatureLoot(((Battle) (Object) this).player(), species, influence, level, random, access);
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
    // Co-op: you command only your own units, and ending the turn waits for everyone (see CoopBattles).
    @Inject(method = "act", at = @At("HEAD"), cancellable = true)
    private void serantis$coopAct(Battle.Action action, CallbackInfoReturnable<String> cir) {
        String why = dev.serantis.battle.coop.CoopBattles.checkAct((Battle) (Object) this, action);
        if (why == dev.serantis.battle.coop.CoopBattles.HANDLED) cir.setReturnValue(null);
        else if (why != null) cir.setReturnValue(why);
    }

    // Changing gear takes the place of an attack: the player moves (if they chose to), then equips. See Equipment.
    @Inject(method = "act", at = @At("HEAD"), cancellable = true)
    private void serantis$equip(Battle.Action action, CallbackInfoReturnable<String> cir) {
        if (!"equip".equals(action.kind())) return;
        Battle self = (Battle) (Object) this;
        Unit u = self.unit(action.unit());
        net.minecraft.server.level.ServerPlayer p = dev.serantis.battle.coop.CoopBattles.playerOf(self, u);
        if (p == null || u == null || !u.isPlayer) { cir.setReturnValue("Only you can change your gear."); return; }
        if (u.acted) { cir.setReturnValue("You've already acted this turn."); return; }
        int slot = action.move(), hand = action.target() == 1 ? 1 : 0;
        // check first, so a refusal doesn't use up the turn
        net.minecraft.world.item.ItemStack held = p.getItemInHand(hand == 1 ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND);
        if (slot < 0 && held.isEmpty()) { cir.setReturnValue("That hand is already empty."); return; }
        if (slot >= 0) {
            var pick = slot < 36 ? p.getInventory().getItem(slot) : net.minecraft.world.item.ItemStack.EMPTY;
            if (!dev.serantis.battle.Equipment.battleGear(pick)) { cir.setReturnValue("That's not a weapon, shield or staff."); return; }
            if (hand == 0 && dev.serantis.battle.Equipment.isShield(pick)) { cir.setReturnValue("Shields go in your off hand."); return; }
        }
        String moved = self.act(new Battle.Action("wait", action.unit(), action.toX(), action.toZ(), -1, -1));
        if (moved != null) { cir.setReturnValue(moved); return; }
        String why = dev.serantis.battle.Equipment.equip(p, slot, hand);
        if (why != null) { say(why); cir.setReturnValue(null); return; }
        ((dev.serantis.battle.moves.UnitAccess) (Object) u).serantis$setMoves(dev.serantis.battle.Equipment.playerMoves(p));
        ((dev.serantis.battle.moves.UnitAccess) (Object) u).serantis$replaceStats(dev.ahrivaea.character.PlayerCharacter.get(p).battleStats(p));
        say(p.getPlainTextName() + (slot < 0 ? " puts away their " + (hand == 1 ? "off-hand gear." : "weapon.")
                : " takes up " + dev.serantis.battle.Equipment.handName(p.getItemInHand(hand == 1
                        ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND)) + "."));
        cir.setReturnValue(null);
    }

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
    private void serantis$enemyPhase(CallbackInfo ci) {
        MoveEngine.sync((Battle) (Object) this);
        dev.serantis.battle.coop.CoopBattles.enemyPhaseStarted((Battle) (Object) this);
    }

    // Each enemy acts according to its mentality (EnemyBrain). When the queue is empty the creatures mod
    // ends the enemy phase as usual.
    @Inject(method = "enemyStep", at = @At("HEAD"), cancellable = true)
    private void serantis$enemyStep(CallbackInfoReturnable<Boolean> cir) {
        Battle self = (Battle) (Object) this;
        if (dev.serantis.battle.BattleWalk.busy(self)) { cir.setReturnValue(true); return; }   // the last one is still walking
        if (dev.serantis.battle.coop.CoopBattles.holdEnemyStep(self)) { cir.setReturnValue(true); return; }   // a spar: the rival plays this phase
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
    private void serantis$afterView(UUID viewer, CallbackInfoReturnable<JsonObject> cir) {
        MoveEngine.decorate((Battle) (Object) this, cir.getReturnValue());
        dev.serantis.battle.coop.CoopBattles.view((Battle) (Object) this, viewer, cir.getReturnValue());
        Battle self = (Battle) (Object) this;                          // a player's very first battle: the screen walks them through it
        var vp = viewer == null || self.level == null ? null : self.level.getServer().getPlayerList().getPlayer(viewer);
        if (vp != null && ((dev.serantis.battle.Welcome.Holder) vp).serantis$battlesFought() == 0 && !dev.serantis.battle.coop.CoopBattles.isSpar(self))
            cir.getReturnValue().addProperty("serantis_tutorial", true);
    }

    // Villagers (Creature Masters and Town Guards included) are never Enraged or Mutated.
    @Redirect(method = "addCreature(Ldev/ahrivaea/battle/Unit$Side;Ldev/ahrivaea/creature/Species;Ldev/ahrivaea/creature/Influence;Ldev/ahrivaea/creature/Variant;IFIII)Ldev/ahrivaea/battle/Unit;",
            at = @At(value = "INVOKE", target = "Ldev/ahrivaea/creature/Variant;prefix()Ljava/lang/String;"))
    private String serantis$plainVillagerName(Variant variant, Unit.Side side, Species species, Influence influence, Variant v,
                                             int level, float hp, int x, int z, int slot) {
        return dev.ahrivaea.villager.Villagers.SPECIES.equals(species.id()) ? "" : variant.prefix();
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/creature/CreatureEntity;getVariant()Ldev/ahrivaea/creature/Variant;"), require = 0)
    private Variant serantis$plainVillagerDefeated(dev.ahrivaea.creature.CreatureEntity e) {
        return dev.ahrivaea.villager.Villagers.SPECIES.equals(e.getSpeciesId()) ? Variant.NONE : e.getVariant();
    }

    // ---- move sets
    @Inject(method = "addCreature(Ldev/ahrivaea/battle/Unit$Side;Ldev/ahrivaea/creature/Species;Ldev/ahrivaea/creature/Influence;Ldev/ahrivaea/creature/Variant;IFIII)Ldev/ahrivaea/battle/Unit;",
            at = @At("RETURN"))
    private void serantis$newUnit(Unit.Side side, Species species, Influence influence, Variant variant, int level, float hp,
                                  int x, int z, int slot, CallbackInfoReturnable<Unit> cir) {
        if (cir.getReturnValue() != null) MoveEngine.assignNewUnit((Battle) (Object) this, cir.getReturnValue());
    }

    // Presence: the recruit list shows the discounted price.
    @Redirect(method = "view", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/battle/Battle$Defeated;cost()I"))
    private int serantis$recruitPrice(Battle.Defeated d) {
        return dev.serantis.battle.skills.Skills.price(((Battle) (Object) this).player(), d.cost());
    }

    // Remember a knocked-out creature's moves so recruiting it keeps them.
    @Inject(method = "knockOut", at = @At("HEAD"))
    private void serantis$beforeKnockOut(Unit unit, CallbackInfo ci) {
        serantis$defeatedBefore = defeated.size();
        Fx.flash((Battle) (Object) this, unit, Fx.KO);
        Fx.koSound((Battle) (Object) this, unit);
    }

    @Inject(method = "knockOut", at = @At("RETURN"))
    private void serantis$afterKnockOut(Unit unit, CallbackInfo ci) {
        if (defeated.size() > serantis$defeatedBefore) {
            String moves = MoveEngine.encodeUnit(unit);
            if (moves != null) serantis$defeatedMoves().put(defeated.get(defeated.size() - 1), moves);
            String temperament = MoveEngine.state(unit).temperament;
            if (temperament != null) serantis$defeatedTemperaments().put(defeated.get(defeated.size() - 1), temperament);
            if (MoveEngine.state(unit).guard) serantis$defeatedGuards().add(defeated.get(defeated.size() - 1));
            Battle self = (Battle) (Object) this;
            if (unit.entity != null && self.level != null
                    && self.level.getEntity(unit.entity) instanceof dev.ahrivaea.creature.CreatureEntity ce) {
                var job = dev.serantis.battle.Villages.jobOf(ce);
                if (job != null) serantis$defeatedJobs().put(defeated.get(defeated.size() - 1), job);
            }
        }
    }

    // A linked Magic Staff in either hand adds its spell to the player's moves (see Staffs).
    @Redirect(method = "addPlayer", at = @At(value = "INVOKE", target = "Ljava/util/List;of(Ljava/lang/Object;)Ljava/util/List;"))
    private List<Object> serantis$staffSpells(Object weapon, net.minecraft.server.level.ServerPlayer player, int x, int z) {
        // the weapon attack is rebuilt by Equipment.playerMoves so Sharpness counts too
        return new java.util.ArrayList<>(dev.serantis.battle.Equipment.playerMoves(player));
    }

    // Your health and your battle HP are one stat: battle HP is your max health x 5 (10 hearts = 100 HP),
    // so anything that raises your hearts raises your HP in battle too.
    @Redirect(method = "addPlayer", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/character/PlayerCharacter;maxHealth()I"))
    private int serantis$playerHp(dev.ahrivaea.character.PlayerCharacter character, net.minecraft.server.level.ServerPlayer player, int x, int z) {
        return dev.serantis.battle.skills.Skills.battleHp(player.getMaxHealth());
    }
}
