package dev.serantis.battle.mixin;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bigger bases: a base reaches EXTRA more chunks out from its core's chunk than the creatures mod gives it, so a Level 1
 * base is 7 x 7 chunks (112 blocks across) and each level adds 2 chunks (32 blocks): 144, 176, 208, 240. Everything
 * that asks a base for its area (workers, chunk loading, raids, hay bales, name signs, the base screen) follows.
 */
@Mixin(BaseCoreBlockEntity.class)
public abstract class BaseAreaMixin {
    @Unique private static final int EXTRA = 2;
    @Shadow private int level;

    @Unique private int serantis$out() { return level + EXTRA; }

    @Inject(method = "chunks", at = @At("HEAD"), cancellable = true)
    private void serantis$chunks(CallbackInfoReturnable<Integer> cir) { cir.setReturnValue(serantis$out()); }

    @Inject(method = "radius", at = @At("HEAD"), cancellable = true)
    private void serantis$radius(CallbackInfoReturnable<Integer> cir) { cir.setReturnValue(serantis$out() * 16 + 8); }

    @Inject(method = "minX", at = @At("HEAD"), cancellable = true)
    private void serantis$minX(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(((((BaseCoreBlockEntity) (Object) this).getBlockPos().getX() >> 4) - serantis$out()) << 4);
    }

    @Inject(method = "maxX", at = @At("HEAD"), cancellable = true)
    private void serantis$maxX(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(((((BaseCoreBlockEntity) (Object) this).getBlockPos().getX() >> 4) + serantis$out() << 4) + 15);
    }

    @Inject(method = "minZ", at = @At("HEAD"), cancellable = true)
    private void serantis$minZ(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(((((BaseCoreBlockEntity) (Object) this).getBlockPos().getZ() >> 4) - serantis$out()) << 4);
    }

    @Inject(method = "maxZ", at = @At("HEAD"), cancellable = true)
    private void serantis$maxZ(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(((((BaseCoreBlockEntity) (Object) this).getBlockPos().getZ() >> 4) + serantis$out() << 4) + 15);
    }
}
