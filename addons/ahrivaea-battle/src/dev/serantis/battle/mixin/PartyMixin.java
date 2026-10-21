package dev.serantis.battle.mixin;

import dev.ahrivaea.party.Party;
import dev.serantis.battle.BattleStats;
import dev.serantis.battle.skills.XpBank;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Party.class)
public abstract class PartyMixin {
    // ---- One XP currency: the creatures mod's party XP is the player's vanilla XP.
    // Battles add to it; recruiting, villager trades, creature levels and the skill tree spend it.
    // Any party XP saved from before is moved into vanilla XP, one for one, the first time it's used.
    @Inject(method = "xp", at = @At("HEAD"), cancellable = true)
    private static void serantis$xp(Player player, CallbackInfoReturnable<Integer> cir) {
        if (XpBank.RAW.get() || player == null || player.level().isClientSide()) return;
        serantis$migrate(player);
        cir.setReturnValue(XpBank.points(player));
    }

    @Inject(method = "addXp", at = @At("HEAD"), cancellable = true)
    private static void serantis$addXp(Player player, int amount, CallbackInfo ci) {
        if (XpBank.RAW.get() || player == null || player.level().isClientSide()) return;
        serantis$migrate(player);
        XpBank.give(player, amount);
        ci.cancel();
    }

    // Presence: levelling a creature with XP costs less (it still gets the full amount).
    @org.spongepowered.asm.mixin.injection.Redirect(method = "feed", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/party/Party;addXp(Lnet/minecraft/world/entity/player/Player;I)V"))
    private static void serantis$feedPrice(Player player, int amount) {
        dev.ahrivaea.party.Party.addXp(player, amount < 0 ? -dev.serantis.battle.skills.Skills.price(player, -amount) : amount);
    }

    private static void serantis$migrate(Player player) {
        XpBank.RAW.set(true);
        dev.serantis.battle.Achievements.NOT_GAINED.set(true);
        try {
            int old = Party.xp(player);
            if (old > 0) {
                Party.addXp(player, -old);
                player.giveExperiencePoints(old);
            }
        } finally {
            dev.serantis.battle.Achievements.NOT_GAINED.set(false);
            XpBank.RAW.set(false);
        }
    }

    // Feeding XP to a creature stops at Level 20 (was 50).
    @ModifyConstant(method = "feed", constant = @Constant(intValue = 50))
    private static int serantis$feedCap(int original) {
        return BattleStats.MAX_LEVEL;
    }
}
