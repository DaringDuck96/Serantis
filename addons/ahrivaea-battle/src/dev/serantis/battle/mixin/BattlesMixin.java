package dev.serantis.battle.mixin;

import dev.ahrivaea.battle.Arena;
import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.Unit;
import dev.serantis.battle.moves.Body;
import dev.serantis.battle.moves.Footprints;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Unique;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.serantis.battle.moves.BattleAccess;
import dev.serantis.battle.moves.MovesHolder;
import dev.serantis.battle.moves.MoveEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Battles.class)
public abstract class BattlesMixin {
    // Arenas grow for big creatures: +2 tiles per footprint step of the biggest one in the fight.
    @Unique private static final ThreadLocal<Integer> SERANTIS_BIGGEST = new ThreadLocal<>();

    // ---- villagers wait hidden instead of being replaced by a jobless copy (see BattleHold)
    @Redirect(method = "start", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/creature/CreatureEntity;discard()V", ordinal = 1))
    private static void serantis$holdVillager(CreatureEntity e, ServerPlayer player, CreatureEntity wild) {
        if (e == wild) dev.serantis.battle.BattleHold.source(e);       // the arena copy takes over its moves, temperament and job
        if (e == wild && dev.serantis.battle.BattleHold.wantsHold(e)) dev.serantis.battle.BattleHold.hold(e);
        else e.discard();
    }

    @Inject(method = "start", at = @At("RETURN"))
    private static void serantis$heldStarted(ServerPlayer player, CreatureEntity wild, CallbackInfoReturnable<Battle> cir) {
        dev.serantis.battle.BattleHold.started(cir.getReturnValue());
    }

    @Inject(method = "cleanUp", at = @At("TAIL"))
    private static void serantis$heldEnded(Battle b, CallbackInfo ci) { dev.serantis.battle.BattleHold.ended(b); }

    @Redirect(method = "goHome", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/Ahrivaea;spawn(Lnet/minecraft/server/level/ServerLevel;Ldev/ahrivaea/creature/Species;Ldev/ahrivaea/creature/Influence;IFDDD)Ldev/ahrivaea/creature/CreatureEntity;", ordinal = 0))
    private static CreatureEntity serantis$noLookAlike(net.minecraft.server.level.ServerLevel level, dev.ahrivaea.creature.Species sp, dev.ahrivaea.creature.Influence inf,
                                                      int lvl, float hp, double x, double y, double z, ServerPlayer p, Battle b) {
        if (dev.serantis.battle.BattleHold.keptFor(b)) return null;      // the real one is still there
        return dev.ahrivaea.Ahrivaea.spawn(level, sp, inf, lvl, hp, x, y, z);
    }

    // ---- co-op (see CoopBattles)
    // Allies and sparring rivals aren't in the creatures mod's list of battles: their actions are routed here.
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void serantis$coopRoute(ServerPlayer player, String json, CallbackInfoReturnable<String> cir) {
        String r = dev.serantis.battle.coop.CoopBattles.route(player, json);   // sets the acting player for battle actions
        if (r != dev.serantis.battle.coop.CoopBattles.PASS) {
            dev.serantis.battle.coop.CoopBattles.ACTOR.remove();
            cir.setReturnValue(r);
        }
    }

    @Inject(method = "handle", at = @At("RETURN"))
    private static void serantis$coopActorDone(ServerPlayer player, String json, CallbackInfoReturnable<String> cir) {
        dev.serantis.battle.coop.CoopBattles.ACTOR.remove();
    }

    @Inject(method = "of", at = @At("RETURN"), cancellable = true)
    private static void serantis$ofJoined(Player p, CallbackInfoReturnable<Battle> cir) {
        if (cir.getReturnValue() == null) {
            Battle joined = dev.serantis.battle.coop.CoopBattles.joined(p);
            if (joined != null) cir.setReturnValue(joined);
        }
    }

    @Inject(method = "send", at = @At("TAIL"))
    private static void serantis$sendOthers(Battle b, CallbackInfo ci) { dev.serantis.battle.coop.CoopBattles.sendOthers(b); }

    @Inject(method = "cleanUp", at = @At("TAIL"))
    private static void serantis$coopEnded(Battle b, CallbackInfo ci) { dev.serantis.battle.coop.CoopBattles.ended(b); }

    @Inject(method = "goHome", at = @At("HEAD"))
    private static void serantis$ownerLeft(ServerPlayer p, Battle b, CallbackInfo ci) {
        dev.serantis.battle.coop.CoopBattles.ownerLeft(b);             // also when the owner logged out (p is null then)
    }

    @Inject(method = "start", at = @At("HEAD"))
    private static void serantis$coopStarting(ServerPlayer player, CreatureEntity wild, CallbackInfoReturnable<Battle> cir) {
        dev.serantis.battle.coop.CoopBattles.starting(player);
    }

    @Inject(method = "start", at = @At("RETURN"))
    private static void serantis$coopStarted(ServerPlayer player, CreatureEntity wild, CallbackInfoReturnable<Battle> cir) {
        dev.serantis.battle.coop.CoopBattles.started(player, cir.getReturnValue());
    }

    @Inject(method = "start", at = @At("HEAD"))
    private static void serantis$measure(ServerPlayer player, CreatureEntity wild, CallbackInfoReturnable<Battle> cir) {
        dev.serantis.battle.Masters.SPAWN_PLAYER.set(player);           // reinforcements count as spawning near this player
        int biggest = 1;
        try {
            if (wild != null && wild.getInfluence() != null)
                biggest = Footprints.tiles(wild.getSpeciesId(), wild.getInfluence().getSerializedName());
            for (CaughtCreature c : Party.get(player))
                if (c != null && c.influence() != null) biggest = Math.max(biggest, Footprints.tiles(c.species(), c.influence().getSerializedName()));
        } catch (RuntimeException e) {
            // size unknown: no growth
        }
        SERANTIS_BIGGEST.set(biggest);
    }

    @Inject(method = "arenaField", at = @At("RETURN"), cancellable = true)
    private static void serantis$growArena(Arena.Theme theme, int a, int b, CallbackInfoReturnable<Integer> cir) {
        Integer biggest = SERANTIS_BIGGEST.get();
        if (biggest != null && biggest > 1) cir.setReturnValue(Math.min(19, cir.getReturnValue() + 2 * (biggest - 1)));
        int raid = dev.serantis.battle.GoblinRaids.extraArena();         // room for a raid's base defenders
        if (raid > 0) cir.setReturnValue(Math.min(19, cir.getReturnValue() + raid));
        int coop = dev.serantis.battle.coop.CoopBattles.extraArena(dev.serantis.battle.Masters.SPAWN_PLAYER.get() instanceof ServerPlayer sp ? sp : null);   // co-op: room for allies, or a spar's rival team
        if (coop > 0) cir.setReturnValue(Math.min(19, cir.getReturnValue() + coop));
    }

    // Wild creatures fight with the moves saved on them.
    @Inject(method = "start", at = @At("RETURN"))
    private static void serantis$start(ServerPlayer player, CreatureEntity wild, CallbackInfoReturnable<Battle> cir) {
        SERANTIS_BIGGEST.remove();
        dev.serantis.battle.Masters.SPAWN_PLAYER.remove();
        MoveEngine.assignFromWorld(cir.getReturnValue());
    }

    // A creature stands in the middle of its body, on the highest tile under it.
    @Redirect(method = "placeEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;snapTo(DDD)V"))
    private static void serantis$place(Entity entity, double x, double y, double z, Battle battle, Unit unit) {
        int n = Body.size(unit);
        if (n > 1) {
            x += (n - 1) / 2.0;
            z += (n - 1) / 2.0;
            for (int c : Body.cells(battle, unit)) y = Math.max(y, battle.grid.worldPos(c % battle.grid.size, c / battle.grid.size).getY());
        }
        if (dev.serantis.battle.BattleWalk.walk(battle, entity, x, y, z)) return;   // it walks there, tile by tile
        entity.snapTo(x, y, z);
    }

    // Pacing (see BattleWalk): players walk to their tile too, updates wait for walks, enemies take turns slowly.
    @Inject(method = "placeEntity", at = @At("HEAD"))
    private static void serantis$walkPlayer(Battle battle, Unit unit, CallbackInfo ci) {
        if (unit != null && unit.isPlayer) dev.serantis.battle.BattleWalk.walkPlayer(battle, unit);
    }

    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private static void serantis$holdForWalks(Battle battle, CallbackInfo ci) {
        if (dev.serantis.battle.BattleWalk.hold(battle)) ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private static void serantis$walkTick(net.minecraft.server.MinecraftServer server, CallbackInfo ci) {
        dev.serantis.battle.BattleWalk.tick(server);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(DDDFF)V"))
    private static void serantis$playerWalk(net.minecraft.server.network.ServerGamePacketListenerImpl conn, double x, double y, double z, float yaw, float pitch) {
        var at = dev.serantis.battle.BattleWalk.playerAt(conn.player, x, y, z);
        conn.teleport(at.x, at.y, at.z, yaw, pitch);
    }

    @org.spongepowered.asm.mixin.injection.ModifyConstant(method = "tick", constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 15))
    private static int serantis$enemyPause(int ticks) { return 22; }     // a pause between one enemy's turn and the next

    // A recruited creature keeps the moves it fought with.
    @Redirect(method = "recruit", at = @At(value = "INVOKE",
            target = "Ldev/ahrivaea/party/Party;addAnywhere(Lnet/minecraft/world/entity/player/Player;Ldev/ahrivaea/party/CaughtCreature;)Z"))
    private static boolean serantis$recruit(Player player, CaughtCreature caught, ServerPlayer sp, Battle battle, int index) {
        if (index >= 0 && index < battle.defeated.size()) {
            var job = ((BattleAccess) (Object) battle).serantis$defeatedJobs().get(battle.defeated.get(index));
            if (job != null) caught = caught.withJob(job);              // a villager keeps its profession
            String moves = ((BattleAccess) (Object) battle).serantis$defeatedMoves().get(battle.defeated.get(index));
            if (moves != null) ((MovesHolder) (Object) caught).serantis$setMoves(moves);
            String temperament = ((BattleAccess) (Object) battle).serantis$defeatedTemperaments().get(battle.defeated.get(index));
            if (temperament != null) ((MovesHolder) (Object) caught).serantis$setTemperament(temperament);
        }
        caught = dev.serantis.battle.FigureVisit.recruited(battle, caught);   // the Mysterious Figure joins at Level 10
        caught = dev.serantis.battle.Villages.onRecruit(sp, caught);     // a villager starts its first deed now
        boolean added = Party.addAnywhere(player, caught);             // false: the party was full and it went to the box
        if (added) dev.serantis.battle.Naming.offer(sp, caught);
        dev.serantis.battle.Achievements.recruited(sp, caught.influence());   // recruited either way
        dev.serantis.battle.TypeMastery.gained(sp, caught.influence());
        dev.serantis.battle.Bounties.recruited(sp, caught);
        return added;
    }

    // Attack a villager and 3-5 Town Guards come to defend it.
    @Unique private static final ThreadLocal<Boolean> SERANTIS_GUARDED = ThreadLocal.withInitial(() -> false);
    @Unique private static final ThreadLocal<String> SERANTIS_MASTER = new ThreadLocal<>();
    @Unique private static final ThreadLocal<Integer> SERANTIS_MASTER_LEVEL = ThreadLocal.withInitial(() -> 15);

    @Redirect(method = "start", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/battle/Battles;reinforcements(ILnet/minecraft/util/RandomSource;)I"))
    private static int serantis$guardCount(int level, net.minecraft.util.RandomSource random, ServerPlayer player, dev.ahrivaea.creature.CreatureEntity target) {
        if (dev.serantis.battle.coop.CoopBattles.SPAR_STARTING.get() != null) return 0;   // a spar: the rival brings their own team
        int raid = dev.serantis.battle.GoblinRaids.raidSize();
        if (raid > 0) return raid - 1;
        if (dev.serantis.battle.FigureVisit.FIGHTING.get()) return 0;  // the Mysterious Figure fights alone                                  // a goblin raid: the rest of the raiders
        String master = dev.serantis.battle.Masters.influenceOf(target);
        if (master != null) {                                           // a Creature Master brings 3-5 of its own
            SERANTIS_MASTER.set(master);
            SERANTIS_MASTER_LEVEL.set(target.getCreatureLevel());
            return 3 + random.nextInt(3);
        }
        boolean village = dev.serantis.battle.Villages.defendedByGuards(target);
        SERANTIS_GUARDED.set(village);
        return village ? 3 + random.nextInt(3) : dev.ahrivaea.battle.Battles.reinforcements(level, random);
    }

    @Redirect(method = "start", at = @At(value = "INVOKE", target =
            "Ldev/ahrivaea/party/RecruitRules;reinforcement(Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/Level;Lnet/minecraft/util/RandomSource;Ldev/ahrivaea/creature/Species;Ldev/ahrivaea/creature/Influence;I)Ljava/util/Optional;"))
    private static java.util.Optional<dev.ahrivaea.creature.Species.LevelledPick> serantis$guard(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome,
            net.minecraft.world.level.Level level, net.minecraft.util.RandomSource random, dev.ahrivaea.creature.Species species, dev.ahrivaea.creature.Influence influence, int lvl) {
        var raider = dev.serantis.battle.GoblinRaids.nextPick();
        if (raider != null) return java.util.Optional.of(raider);
        if (SERANTIS_MASTER.get() != null) {
            var pick = dev.serantis.battle.Masters.teamPick(SERANTIS_MASTER.get(), SERANTIS_MASTER_LEVEL.get(), random);
            if (pick != null) return java.util.Optional.of(pick);
        }
        if (SERANTIS_GUARDED.get()) {
            var guard = dev.serantis.battle.Villages.guardPick(lvl, random);
            if (guard != null) { dev.serantis.battle.Villages.NEXT_GUARD.set(true); return java.util.Optional.of(guard); }
        }
        return dev.ahrivaea.party.RecruitRules.reinforcement(biome, level, random, species, influence, lvl);
    }

    @Inject(method = "start", at = @At("RETURN"))
    private static void serantis$guardsArrive(ServerPlayer player, dev.ahrivaea.creature.CreatureEntity entity, CallbackInfoReturnable<Battle> cir) {
        if (cir.getReturnValue() != null && dev.serantis.battle.Harbinger.is(entity)) {   // the Harbinger taunts you
            ((BattleAccess) (Object) cir.getReturnValue()).serantis$say("Harbinger of Calamity: \"" + dev.serantis.battle.Harbinger.TAUNT + "\"");
            dev.serantis.battle.Harbinger.taunt(player);
        }
        if (SERANTIS_GUARDED.get() && cir.getReturnValue() != null)
            ((BattleAccess) (Object) cir.getReturnValue()).serantis$say("Town Guards rush in to defend the village!");
        SERANTIS_GUARDED.set(false);
        dev.serantis.battle.Villages.NEXT_GUARD.set(false);
        String master = SERANTIS_MASTER.get();
        SERANTIS_MASTER.remove();
        if (master != null && cir.getReturnValue() != null) MoveEngine.masterBattle(cir.getReturnValue(), entity, master);
    }

    // Presence: recruiting costs less XP.
    @Redirect(method = "recruit", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/battle/Battle$Defeated;cost()I"))
    private static int serantis$recruitPrice(Battle.Defeated d, ServerPlayer player, Battle battle, int index) {
        return dev.serantis.battle.skills.Skills.price(player, d.cost());
    }

    // Any weapon starts a battle, not just a sword.
    @Redirect(method = "lambda$init$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/tags/TagKey;)Z"))
    private static boolean serantis$anyWeapon(net.minecraft.world.item.ItemStack stack, net.minecraft.tags.TagKey<?> tag) {
        return dev.serantis.battle.Weapons.isWeapon(stack);
    }

    @org.spongepowered.asm.mixin.injection.ModifyConstant(method = "lambda$init$1", constant = @org.spongepowered.asm.mixin.injection.Constant(stringValue = "Hit it with a sword to battle it."))
    private static String serantis$weaponHint(String original) {
        return "Hit it with a weapon (or shoot it) to battle it.";
    }

    // Raid goblins: no ordinary battles; hitting one is a raid fight's own business (see GoblinRaids).
    @Redirect(method = "start", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/creature/Variant;roll(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;)Ldev/ahrivaea/creature/Variant;"))
    private static dev.ahrivaea.creature.Variant serantis$raidVariant(net.minecraft.server.level.ServerLevel level, net.minecraft.util.RandomSource random) {
        return dev.serantis.battle.GoblinRaids.STARTING.get() != null ? dev.ahrivaea.creature.Variant.NONE : dev.ahrivaea.creature.Variant.roll(level, random);
    }

    // Black Market Merchants don't fight.
    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private static void serantis$noBlackMarketBattles(ServerPlayer player, dev.ahrivaea.creature.CreatureEntity entity, CallbackInfoReturnable<Battle> cir) {
        if (dev.serantis.battle.coop.CoopBattles.SPAR_STARTING.get() != null) return;   // a spar's stand-in creature
        if (dev.serantis.battle.BattleHold.isHeld(entity)) { serantis$refuse(cir); return; }   // waiting out another player's battle
        if (dev.serantis.battle.Intro.isFigure(entity) && !dev.serantis.battle.FigureVisit.FIGHTING.get()) { serantis$refuse(cir); return; }   // the Mysterious Figure doesn't fight
        if (dev.serantis.battle.VillagerTalk.ask(player, entity)) { serantis$refuse(cir); return; }   // a villager talks before it fights
        if (dev.serantis.battle.GoblinRaids.isRaider(entity)) {       // a raiding goblin: bargain first
            dev.serantis.battle.GoblinRaids.open(player, entity);
            serantis$refuse(cir);
            return;
        }
        if (dev.serantis.battle.Riddles.isRiddleDragon(entity) && !dev.serantis.battle.Riddles.solved(player, entity)) {
            dev.serantis.battle.Riddles.ask(player, entity);          // an End dragon asks its riddle first
            serantis$refuse(cir);
            return;
        }
        if (dev.serantis.battle.Villages.isGuard(entity)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("I wouldn't try that, punk..."));
            serantis$refuse(cir);
            return;
        }
        if (dev.serantis.battle.Wanderer.isWanderer(entity)) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("The Wandering Trader doesn't fight. Right-click to swap a creature."));
            serantis$refuse(cir);
            return;
        }
        if (dev.serantis.battle.Market.isBlackMarket(entity)) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("The Black Market Merchant doesn't fight. Right-click to see what's for sale."));
            serantis$refuse(cir);
        }
    }

    /** Battles.start refused before it began: nothing it set up for the battle should linger. */
    @Unique
    private static void serantis$refuse(CallbackInfoReturnable<Battle> cir) {
        SERANTIS_BIGGEST.remove();
        dev.serantis.battle.Masters.SPAWN_PLAYER.remove();
        dev.serantis.battle.coop.CoopBattles.clearStart();
        cir.setReturnValue(null);
    }

    // Names chosen on the screen come back as a battle action.
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void serantis$rename(ServerPlayer player, String json, CallbackInfoReturnable<String> cir) {
        try {
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            String kind = o.has("kind") ? o.get("kind").getAsString() : "";
            if (kind.startsWith("serantis_riddle_")) {    // an End dragon's riddle
                switch (kind) {
                    case "serantis_riddle_ask" -> {
                        var e = player.level().getEntity(o.has("entity") ? o.get("entity").getAsInt() : -1);
                        if (e instanceof dev.ahrivaea.creature.CreatureEntity c && dev.serantis.battle.Riddles.isRiddleDragon(c) && player.distanceToSqr(c) < 16 * 16) {
                            if (dev.serantis.battle.Riddles.solved(player, c)) { if (dev.ahrivaea.battle.Battles.of(player) == null) dev.ahrivaea.battle.Battles.start(player, c); }
                            else dev.serantis.battle.Riddles.ask(player, c);
                        }
                    }
                    case "serantis_riddle_answer" -> dev.serantis.battle.Riddles.answer(player, o.has("index") ? o.get("index").getAsInt() : -1);
                    case "serantis_riddle_fight" -> dev.serantis.battle.Riddles.done(player, true);
                    default -> dev.serantis.battle.Riddles.done(player, false);
                }
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_linx".equals(kind)) {           // a Helm of Linx key
                dev.serantis.battle.Linx.use(player, o.has("ability") ? o.get("ability").getAsInt() : 0,
                        o.has("sneak") && o.get("sneak").getAsBoolean(),
                        o.has("shape") ? o.get("shape").getAsString() : null, o.has("size") ? o.get("size").getAsInt() : 5,
                        o.has("any") && o.get("any").getAsBoolean());
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_bounty".equals(kind)) {         // claim or hand in a bounty
                dev.serantis.battle.Bounties.act(player, o.has("index") ? o.get("index").getAsInt() : -1);
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_harbinger_watched".equals(kind)) {   // the Harbinger's entrance is over
                dev.serantis.battle.Harbinger.watched(player);
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_villager_talk".equals(kind)) {     // right-clicked a villager: it talks (and offers a fight)
                if (o.has("entity")) dev.serantis.battle.VillagerTalk.talk(player, o.get("entity").getAsInt());
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_villager_answer".equals(kind)) {   // a villager's conversation ended: fight it, or not
                dev.serantis.battle.VillagerTalk.answer(player, o.has("fight") && o.get("fight").getAsBoolean());
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_master_challenge".equals(kind)) {   // challenged a Creature Master from the conversation
                var e = player.level().getEntity(o.has("entity") ? o.get("entity").getAsInt() : -1);
                if (e instanceof dev.ahrivaea.creature.CreatureEntity c && dev.serantis.battle.Masters.isMaster(c) && c.isAlive()
                        && player.distanceToSqr(c) < 12 * 12 && dev.ahrivaea.battle.Battles.of(player) == null)
                    dev.ahrivaea.battle.Battles.start(player, c);
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_backpack".equals(kind)) {        // B: open the worn backpack (sneaking: take it off)
                if (o.has("off") && o.get("off").getAsBoolean()) dev.serantis.battle.Backpacks.takeOff(player);
                else dev.serantis.battle.Backpacks.open(player);
                cir.setReturnValue(null);
                return;
            }
            if (kind.startsWith("serantis_staff_")) {    // linking a spell to a Magic Staff
                switch (kind) {
                    case "serantis_staff_open" -> dev.serantis.battle.Staffs.open(player, net.minecraft.core.BlockPos.containing(
                            o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()));
                    case "serantis_staff_link" -> dev.serantis.battle.Staffs.link(player, o.has("index") ? o.get("index").getAsInt() : -1);
                    default -> dev.serantis.battle.Staffs.close(player);
                }
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_class_pick".equals(kind)) {
                dev.serantis.battle.PlayerClass.pick(player, o.has("class") ? o.get("class").getAsString() : "",
                        o.has("extra") ? o.get("extra").getAsString() : null);
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_core_break".equals(kind)) {
                dev.serantis.battle.CoreRemoval.remove(player, net.minecraft.core.BlockPos.containing(
                        o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()));
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_welcome_seen".equals(kind)) {
                dev.serantis.battle.Welcome.seen(player);
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_visit_done".equals(kind)) {     // the Mysterious Figure leaves (or fights)
                if (o.has("fight") && o.get("fight").getAsBoolean()) dev.serantis.battle.FigureVisit.fight(player);
                else dev.serantis.battle.FigureVisit.done(player, o.has("named") && o.get("named").getAsBoolean());
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_eat".equals(kind) || "serantis_feed".equals(kind)) {   // right-clicked food in the inventory (FeedScreen)
                int slot = o.has("slot") ? o.get("slot").getAsInt() : -1;
                if ("serantis_eat".equals(kind)) dev.serantis.battle.Feeding.eat(player, slot);
                else dev.serantis.battle.Feeding.feed(player, slot, o.has("party") ? o.get("party").getAsInt() : -1, o.has("entity") ? o.get("entity").getAsInt() : -1);
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_master_gift_done".equals(kind)) {    // the Grandmaster's End Crystal cutscene is over
                dev.serantis.battle.Masters.giftDone(player);
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_achievements_claim".equals(kind)) {   // claim waiting golden apple rewards
                dev.serantis.battle.Achievements.claim(player);
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_craft".equals(kind)) {             // a Tradesman crafts anywhere
                dev.serantis.battle.PlayerClass.openCrafting(player);
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_tower_atlas".equals(kind)) {      // the Mysterious Figure hands over the Atlas in the opening scene
                if (dev.serantis.battle.Intro.active(player)) dev.serantis.battle.TowerAtlas.give(player);
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_intro_done".equals(kind)) {     // the opening scene is over
                dev.serantis.battle.Intro.done(player);
                cir.setReturnValue(null);
                return;
            }
            if (kind.startsWith("serantis_raid_")) {      // goblins at the base
                switch (kind) {
                    case "serantis_raid_open" -> dev.serantis.battle.GoblinRaids.open(player, o.has("entity") ? o.get("entity").getAsInt() : -1);
                    case "serantis_raid_pay" -> dev.serantis.battle.GoblinRaids.pay(player);
                    case "serantis_raid_fight" -> dev.serantis.battle.GoblinRaids.refuse(player);
                    default -> dev.serantis.battle.GoblinRaids.close(player);
                }
                cir.setReturnValue(null);
                return;
            }
            if (kind.startsWith("serantis_smith_")) {     // a Smith improving a weapon
                switch (kind) {
                    case "serantis_smith_open" -> dev.serantis.battle.Honing.open(player, o.has("entity") ? o.get("entity").getAsInt() : -1);
                    case "serantis_smith_improve" -> dev.serantis.battle.Honing.improve(player);
                    default -> dev.serantis.battle.Honing.close(player);
                }
                cir.setReturnValue(null);
                return;
            }
            if (kind.startsWith("serantis_tutor_")) {     // the Move Tutor
                int n = o.has("slot") ? o.get("slot").getAsInt() : -1;
                switch (kind) {
                    case "serantis_tutor_open" -> dev.serantis.battle.Tutor.open(player, o.has("entity") ? o.get("entity").getAsInt() : -1);
                    case "serantis_tutor_pick" -> dev.serantis.battle.Tutor.pick(player, n);
                    case "serantis_tutor_teach" -> dev.serantis.battle.Tutor.teach(player, n,
                            o.has("offer") ? o.get("offer").getAsInt() : -1, o.has("replace") ? o.get("replace").getAsInt() : -1);
                    default -> dev.serantis.battle.Tutor.close(player);
                }
                cir.setReturnValue(null);
                return;
            }
            if (kind.startsWith("serantis_wander_")) {    // the Wandering Trader's swaps
                int entity = o.has("entity") ? o.get("entity").getAsInt() : -1;
                switch (kind) {
                    case "serantis_wander_open" -> dev.serantis.battle.Wanderer.open(player, entity);
                    case "serantis_wander_swap" -> dev.serantis.battle.Wanderer.swap(player, entity, o.has("slot") ? o.get("slot").getAsInt() : -1);
                    default -> dev.serantis.battle.Wanderer.close(player);
                }
                cir.setReturnValue(null);
                return;
            }
            if (kind.startsWith("serantis_market_")) {    // the Black Market Merchant's shop
                int entity = o.has("entity") ? o.get("entity").getAsInt() : -1;
                switch (kind) {
                    case "serantis_market_open" -> dev.serantis.battle.Market.open(player, entity);
                    case "serantis_market_buy" -> dev.serantis.battle.Market.buy(player, entity, o.has("index") ? o.get("index").getAsInt() : -1);
                    default -> dev.serantis.battle.Market.close(player);
                }
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_achievements".equals(kind)) {   // the Achievements page wants fresh numbers
                dev.serantis.battle.Achievements.check(player);
                dev.ahrivaea.party.PartySync.sync(player);
                cir.setReturnValue(null);
                return;
            }
            if (kind.startsWith("serantis_coop_")) {      // groups, spars, joining battles
                dev.serantis.battle.coop.Groups.handle(player, o);
                cir.setReturnValue(null);
                return;
            }
            if (kind.startsWith("serantis_trade_")) {     // creature trades between players
                dev.serantis.battle.Trades.handle(player, o);
                cir.setReturnValue(null);
                return;
            }
            if ("serantis_apple".equals(kind)) {      // a golden apple fed from the party screen
                dev.serantis.battle.Apples.feed(player, o.has("slot") ? o.get("slot").getAsInt() : -1, o.has("stat") ? o.get("stat").getAsString() : "");
                cir.setReturnValue(null);
                return;
            }
            if (!"serantis_rename".equals(kind)) return;
            cir.setReturnValue(dev.serantis.battle.Naming.rename(player, o.has("name") ? o.get("name").getAsString() : ""));
        } catch (RuntimeException e) {
            // not ours
        }
    }

    // New players' fights stay in their level window (see LevelBand): a wild creature above it (spawned near a
    // stronger friend, an old one, a villager) is brought down to the top of the window when the battle starts, and its
    // reinforcements follow it. Masters, the Harbinger, the Acolytes, raids and spars keep their levels.
    @Inject(method = "start", at = @At("HEAD"))
    private static void serantis$levelWindow(ServerPlayer player, dev.ahrivaea.creature.CreatureEntity entity, CallbackInfoReturnable<Battle> cir) {
        if (entity == null || entity.isTame() || dev.serantis.battle.coop.CoopBattles.SPAR_STARTING.get() != null || dev.serantis.battle.FigureVisit.FIGHTING.get()
                || dev.serantis.battle.GoblinRaids.raidSize() > 0 || dev.serantis.battle.Masters.isMaster(entity) || dev.serantis.battle.Harbinger.is(entity)
                || dev.serantis.battle.Intro.isFigure(entity)) return;
        int me = dev.ahrivaea.character.PlayerCharacter.get(player).level();
        if (dev.serantis.battle.LevelBand.above(me) >= 5) return;      // the window is fully open from level 8
        int cap = Math.max(1, dev.serantis.battle.LevelBand.hi(me));
        var sp = entity.getSpecies().orElse(null);
        if (sp != null && entity.getCreatureLevel() > cap) entity.setup(sp, entity.getInfluence(), cap, -1f);
    }

    // Knocked-out creatures (see Knockout) sit battles out: they're left out of the party list the battle takes
    // its creatures from (after being moved to the back, so the slots still match), and out of the ones following you.
    @Redirect(method = "start", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/party/Party;get(Lnet/minecraft/world/entity/player/Player;)Ljava/util/List;"))
    private static java.util.List<dev.ahrivaea.party.CaughtCreature> serantis$fighters(net.minecraft.world.entity.player.Player player) {
        return player instanceof ServerPlayer sp ? dev.serantis.battle.Knockout.fighters(sp) : dev.ahrivaea.party.Party.get(player);
    }

    @Redirect(method = "start", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private static <T extends net.minecraft.world.entity.Entity> java.util.List<T> serantis$outFighters(net.minecraft.server.level.ServerLevel level, Class<T> type,
            net.minecraft.world.phys.AABB box, java.util.function.Predicate<? super T> filter) {
        java.util.List<T> list = new java.util.ArrayList<>(level.getEntitiesOfClass(type, box, filter));
        list.removeIf(dev.serantis.battle.Knockout::is);
        return list;
    }
}
