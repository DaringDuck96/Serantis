package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.CreatureEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player's creature removed for good (recalled, released) is no longer remembered as out (see Companions). */
@Mixin(Entity.class)
public abstract class EntityRemovedMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void serantis$forget(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof CreatureEntity c && reason.shouldDestroy()) dev.serantis.battle.Companions.forget(c);
    }
}
