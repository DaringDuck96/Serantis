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

    // Winged Boots: a fall after a flight doesn't hurt.
    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void serantis$wingedFall(double distance, float multiplier, DamageSource source,
                                     org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (dev.serantis.battle.WingedBoots.safeFall(this)) cir.setReturnValue(false);
    }

    // Black Market Merchants can't be hurt.
    @org.spongepowered.asm.mixin.injection.Inject(method = "hurtServer", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true)
    private void serantis$untouchable(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount,
                                      org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if ((dev.serantis.battle.Market.isBlackMarket((LivingEntity) (Object) this) || dev.serantis.battle.Villages.isGuard((LivingEntity) (Object) this)
                || dev.serantis.battle.Wanderer.isWanderer((LivingEntity) (Object) this) || dev.serantis.battle.Masters.isMaster((LivingEntity) (Object) this))
                && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) cir.setReturnValue(false);
    }

    // Riding a creature: your crosshair passes through your own mount, so you can place and break blocks,
    // and hit other creatures to start a battle, instead of always clicking the creature you're sitting on.
    @org.spongepowered.asm.mixin.injection.Inject(method = "isPickable", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true)
    private void serantis$notMyMount(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof dev.ahrivaea.creature.CreatureEntity && self.level().isClientSide()
                && self.hasPassenger(e -> e instanceof net.minecraft.world.entity.player.Player p && p.isLocalPlayer()))
            cir.setReturnValue(false);
    }

    // A player's creature can't die in the world: it's left on 1 HP (and goes home if it was out). See Companions.
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void serantis$neverLost(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof dev.ahrivaea.creature.CreatureEntity c && dev.serantis.battle.Companions.spare(c)) ci.cancel();
    }
}
