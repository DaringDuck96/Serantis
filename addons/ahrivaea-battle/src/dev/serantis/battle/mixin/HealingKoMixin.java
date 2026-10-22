package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.Healing;
import dev.ahrivaea.party.Party;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feeding a knocked-out creature from the party screen does nothing (and costs no food) until it's back on its feet. */
@Mixin(Healing.class)
public abstract class HealingKoMixin {
    @Inject(method = "feedParty", at = @At("HEAD"), cancellable = true)
    private static void serantis$ko(ServerPlayer p, int slot, CallbackInfoReturnable<String> cir) {
        var party = Party.get(p);
        if (slot < 0 || slot >= party.size() || !dev.serantis.battle.Knockout.is(party.get(slot))) return;
        cir.setReturnValue("That creature is knocked out: it can't be healed for another " + dev.serantis.battle.Knockout.clock(party.get(slot)) + ".");
    }
}
