package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.CreatureEntity;
import dev.serantis.battle.Weapons;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shooting a wild creature with an arrow (or hitting it with a thrown trident) starts a battle. */
@Mixin(Projectile.class)
public abstract class ProjectileBattleMixin {
    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void serantis$battle(HitResult hit, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        if (!(self instanceof AbstractArrow) || self.level().isClientSide()) return;
        if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof CreatureEntity target)) return;
        if (!(self.getOwner() instanceof ServerPlayer player)) return;
        if (!Weapons.shot(player, target)) return;
        self.setDeltaMovement(Vec3.ZERO);           // the arrow drops where it hit, and can be picked up
        ci.cancel();
    }
}
