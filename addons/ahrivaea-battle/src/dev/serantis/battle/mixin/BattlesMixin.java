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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Battles.class)
public abstract class BattlesMixin {
    // Arenas grow for big creatures: +2 tiles per footprint step of the biggest one in the fight.
    @Unique private static final ThreadLocal<Integer> SERANTIS_BIGGEST = new ThreadLocal<>();

    @Inject(method = "start", at = @At("HEAD"))
    private static void serantis$measure(ServerPlayer player, CreatureEntity wild, CallbackInfoReturnable<Battle> cir) {
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
    }

    // Wild creatures fight with the moves saved on them.
    @Inject(method = "start", at = @At("RETURN"))
    private static void serantis$start(ServerPlayer player, CreatureEntity wild, CallbackInfoReturnable<Battle> cir) {
        SERANTIS_BIGGEST.remove();
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
        entity.snapTo(x, y, z);
    }

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
        caught = dev.serantis.battle.Villages.onRecruit(sp, caught);     // a villager starts its first deed now
        boolean added = Party.addAnywhere(player, caught);
        if (added) dev.serantis.battle.Naming.offer(sp, caught);
        if (added) dev.serantis.battle.Achievements.recruited(sp, caught.influence());
        return added;
    }

    // Attack a villager and 3-5 Town Guards come to defend it.
    @Unique private static final ThreadLocal<Boolean> SERANTIS_GUARDED = ThreadLocal.withInitial(() -> false);

    @Redirect(method = "start", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/battle/Battles;reinforcements(ILnet/minecraft/util/RandomSource;)I"))
    private static int serantis$guardCount(int level, net.minecraft.util.RandomSource random, ServerPlayer player, dev.ahrivaea.creature.CreatureEntity target) {
        boolean village = dev.serantis.battle.Villages.defendedByGuards(target);
        SERANTIS_GUARDED.set(village);
        return village ? 3 + random.nextInt(3) : dev.ahrivaea.battle.Battles.reinforcements(level, random);
    }

    @Redirect(method = "start", at = @At(value = "INVOKE", target =
            "Ldev/ahrivaea/party/RecruitRules;reinforcement(Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/Level;Lnet/minecraft/util/RandomSource;Ldev/ahrivaea/creature/Species;Ldev/ahrivaea/creature/Influence;I)Ljava/util/Optional;"))
    private static java.util.Optional<dev.ahrivaea.creature.Species.LevelledPick> serantis$guard(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome,
            net.minecraft.world.level.Level level, net.minecraft.util.RandomSource random, dev.ahrivaea.creature.Species species, dev.ahrivaea.creature.Influence influence, int lvl) {
        if (SERANTIS_GUARDED.get()) {
            var guard = dev.serantis.battle.Villages.guardPick(lvl, random);
            if (guard != null) { dev.serantis.battle.Villages.NEXT_GUARD.set(true); return java.util.Optional.of(guard); }
        }
        return dev.ahrivaea.party.RecruitRules.reinforcement(biome, level, random, species, influence, lvl);
    }

    @Inject(method = "start", at = @At("RETURN"))
    private static void serantis$guardsArrive(ServerPlayer player, dev.ahrivaea.creature.CreatureEntity entity, CallbackInfoReturnable<Battle> cir) {
        if (SERANTIS_GUARDED.get() && cir.getReturnValue() != null)
            ((BattleAccess) (Object) cir.getReturnValue()).serantis$say("Town Guards rush in to defend the village!");
        SERANTIS_GUARDED.set(false);
        dev.serantis.battle.Villages.NEXT_GUARD.set(false);
    }

    // Black Market Merchants don't fight.
    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private static void serantis$noBlackMarketBattles(ServerPlayer player, dev.ahrivaea.creature.CreatureEntity entity, CallbackInfoReturnable<Battle> cir) {
        if (dev.serantis.battle.Villages.isGuard(entity)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("I wouldn't try that, punk..."));
            cir.setReturnValue(null);
            return;
        }
        if (dev.serantis.battle.Wanderer.isWanderer(entity)) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("The Wandering Trader doesn't fight. Right-click to swap a creature."));
            cir.setReturnValue(null);
            return;
        }
        if (dev.serantis.battle.Market.isBlackMarket(entity)) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("The Black Market Merchant doesn't fight. Right-click to see what's for sale."));
            cir.setReturnValue(null);
        }
    }

    // Names chosen on the screen come back as a battle action.
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void serantis$rename(ServerPlayer player, String json, CallbackInfoReturnable<String> cir) {
        try {
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            String kind = o.has("kind") ? o.get("kind").getAsString() : "";
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
}
