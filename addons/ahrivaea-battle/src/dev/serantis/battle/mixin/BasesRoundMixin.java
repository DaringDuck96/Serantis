package dev.serantis.battle.mixin;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.base.Bases;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Round bases (see BaseShape): "is this column in a base" means inside a base's circle, not its square. */
@Mixin(Bases.class)
public abstract class BasesRoundMixin {
    @Inject(method = "inAnyBaseColumn", at = @At("HEAD"), cancellable = true)
    private static void serantis$column(ServerLevel level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(serantis$base(level, pos) != null);
    }

    @Inject(method = "baseAt", at = @At("HEAD"), cancellable = true)
    private static void serantis$baseAt(ServerLevel level, BlockPos pos, CallbackInfoReturnable<BaseCoreBlockEntity> cir) {
        cir.setReturnValue(serantis$base(level, pos));
    }

    private static BaseCoreBlockEntity serantis$base(ServerLevel level, BlockPos pos) {
        for (BlockPos c : Bases.cores(level)) {
            BaseCoreBlockEntity be = Bases.coreAt(level, c);
            if (be != null && dev.serantis.battle.BaseShape.inside(be, pos.getX(), pos.getZ())) return be;
        }
        return null;
    }
}
