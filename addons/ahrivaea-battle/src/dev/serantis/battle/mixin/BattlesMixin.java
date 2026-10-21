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

    // Names chosen on the screen come back as a battle action.
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void serantis$rename(ServerPlayer player, String json, CallbackInfoReturnable<String> cir) {
        try {
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            String kind = o.has("kind") ? o.get("kind").getAsString() : "";
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
