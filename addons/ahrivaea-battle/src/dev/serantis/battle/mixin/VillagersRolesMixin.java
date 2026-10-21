package dev.serantis.battle.mixin;

import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.villager.Villagers;
import dev.serantis.battle.Villages;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A villager keeps the profession it was born with; and the Alchemist's golden apple trade. */
@Mixin(Villagers.class)
public abstract class VillagersRolesMixin {
    @Inject(method = "train", at = @At("HEAD"), cancellable = true)
    private static void serantis$noRetraining(ServerPlayer player, Villagers.Handle handle, CaughtCreature caught, String profession,
                                              CallbackInfoReturnable<String> cir) {
        String current = caught == null || caught.job() == null ? null : caught.job().profession();
        if (current != null && !current.isEmpty() && dev.ahrivaea.villager.Profession.get(current) != null && !current.equals(profession))
            cir.setReturnValue("This villager is a " + Villages.professionName(current) + " for life; it can't change professions.");
    }

    // Merchants: each sells its own random handful of things.
    @Inject(method = "view", at = @At("RETURN"))
    private static void serantis$merchantView(ServerPlayer player, Villagers.Handle handle, CaughtCreature caught, CallbackInfoReturnable<com.google.gson.JsonObject> cir) {
        dev.serantis.battle.Market.decorateView(player, caught, cir.getReturnValue());
    }

    @Inject(method = "buy", at = @At("HEAD"), cancellable = true)
    private static void serantis$merchantBuy(ServerPlayer player, CaughtCreature caught, int index, int times, CallbackInfoReturnable<String> cir) {
        if (dev.serantis.battle.Market.isMerchant(caught)) cir.setReturnValue(dev.serantis.battle.Market.buyFromMerchant(player, caught, index, times));
    }

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void serantis$alchemist(ServerPlayer player, String json, CallbackInfo ci) {
        try {
            var o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (o.has("kind") && "serantis_apple_xp".equals(o.get("kind").getAsString())) {
                Villages.appleForXp(player);
                ci.cancel();
            }
        } catch (RuntimeException ignored) {
            // not ours
        }
    }
}
