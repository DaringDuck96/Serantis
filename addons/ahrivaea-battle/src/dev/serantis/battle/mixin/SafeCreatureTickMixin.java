package dev.serantis.battle.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ahrivaea.creature.CreatureEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * A creature's tick (its AI, base work, and everything hooked into them) going wrong no longer takes the server down:
 * the error is logged once (see Safe) and that creature just skips the tick. Before, one bad worker crashed the server
 * every time its base loaded.
 */
@Mixin(CreatureEntity.class)
public abstract class SafeCreatureTickMixin {
    @WrapMethod(method = "tick")
    private void serantis$safeTick(Operation<Void> original) {
        try {
            original.call();
        } catch (Throwable t) {
            CreatureEntity self = (CreatureEntity) (Object) this;
            dev.serantis.battle.Safe.caught("ticking creature " + self.getSpeciesId(), t);
        }
    }
}
