package dev.serantis.battle.mixin;

import dev.serantis.battle.skills.Skills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scavenger: mobs a player with the node kills have a 5% chance to drop their loot twice. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDropsMixin {
    @Shadow public abstract Player getLastHurtByPlayer();
    @Shadow protected abstract void dropFromLootTable(ServerLevel level, DamageSource source, boolean playerKill);
    @Unique private static final ThreadLocal<Boolean> SERANTIS_AGAIN = ThreadLocal.withInitial(() -> false);

    @Inject(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V", at = @At("RETURN"))
    private void serantis$scavenger(ServerLevel level, DamageSource source, boolean playerKill, CallbackInfo ci) {
        if (SERANTIS_AGAIN.get() || !playerKill) return;
        Player killer = getLastHurtByPlayer();
        if (killer == null || !Skills.has(killer, "fortune_2") || level.getRandom().nextFloat() >= 0.05f) return;
        SERANTIS_AGAIN.set(true);
        try { dropFromLootTable(level, source, true); } finally { SERANTIS_AGAIN.set(false); }
    }

    // Black Market Merchants can't be hurt.
    @org.spongepowered.asm.mixin.injection.Inject(method = "hurtServer", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true)
    private void serantis$untouchable(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount,
                                      org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (dev.serantis.battle.Market.isBlackMarket((LivingEntity) (Object) this)
                && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) cir.setReturnValue(false);
    }
}
