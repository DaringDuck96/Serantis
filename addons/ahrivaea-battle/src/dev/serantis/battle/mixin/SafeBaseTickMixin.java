package dev.serantis.battle.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ahrivaea.base.BaseCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** A Base Core's tick going wrong is logged (see Safe) instead of crashing the server whenever that base loads. */
@Mixin(BaseCoreBlockEntity.class)
public abstract class SafeBaseTickMixin {
    @WrapMethod(method = "serverTick")
    private static void serantis$safeTick(Level level, BlockPos pos, BlockState state, BaseCoreBlockEntity be, Operation<Void> original) {
        try {
            original.call(level, pos, state, be);
        } catch (Throwable t) {
            dev.serantis.battle.Safe.caught("ticking the Base Core at " + pos.toShortString(), t);
        }
    }
}
