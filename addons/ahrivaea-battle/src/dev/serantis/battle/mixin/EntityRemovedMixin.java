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
        try {
        if ((Object) this instanceof CreatureEntity c && reason.shouldDestroy()) dev.serantis.battle.Companions.forget(c);
        if ((Object) this instanceof CreatureEntity c && reason.shouldDestroy() && c.brain() != null
                && c.level() instanceof net.minecraft.server.level.ServerLevel sl) {          // a worker sent away mid-job drops its load
            var acc = (dev.serantis.battle.mixin.WorkerBrainAccess) (Object) c.brain();
            if (!acc.serantis$carried().isEmpty()) { c.spawnAtLocation(sl, acc.serantis$carried()); acc.serantis$setCarried(net.minecraft.world.item.ItemStack.EMPTY); }
        }
        if ((Object) this instanceof net.minecraft.server.level.ServerPlayer p) {
            dev.serantis.battle.Linx.left(p);                           // carried Shape Lift blocks go back
        }
            } catch (Throwable serantisT) { dev.serantis.battle.Safe.caught("serantis.forget", serantisT); }
    }
}
