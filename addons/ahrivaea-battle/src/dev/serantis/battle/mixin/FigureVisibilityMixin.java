package dev.serantis.battle.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An Acolyte in someone's cutscene is only sent to that player (see Intro.visibleTo). */
@Mixin(Entity.class)
public abstract class FigureVisibilityMixin {
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void serantis$ownCutscene(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        boolean spawning = dev.serantis.battle.Intro.SPAWNING_FOR.get() != null && self instanceof dev.ahrivaea.creature.CreatureEntity;
        if ((spawning || dev.serantis.battle.Intro.isFigure(self)) && !dev.serantis.battle.Intro.visibleTo(self, player)) cir.setReturnValue(false);
        else if (!dev.serantis.battle.DragonParties.allowed(self, player.getUUID())) cir.setReturnValue(false);   // another party's End dragon
    }
}
