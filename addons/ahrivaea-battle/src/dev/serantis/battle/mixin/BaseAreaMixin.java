package dev.serantis.battle.mixin;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bigger, round bases: a base is a circle around its core, radius 16 x (level + EXTRA) + 8, so 112 blocks across at
 * Level 1 and 32 more each level: 144, 176, 208, 240 (see BaseShape). chunks() (chunk loading, the base screen) grows
 * with it. Everything that asks a base for its area (workers, raids, hay bales, name signs, looks) follows.
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

    @Unique private int serantis$r() { return serantis$out() * 16 + 8; }

    // The square around the base's circle (see BaseShape), centred on the core itself.
    @Inject(method = "minX", at = @At("HEAD"), cancellable = true)
    private void serantis$minX(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(((BaseCoreBlockEntity) (Object) this).getBlockPos().getX() - serantis$r());
    }

    @Inject(method = "maxX", at = @At("HEAD"), cancellable = true)
    private void serantis$maxX(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(((BaseCoreBlockEntity) (Object) this).getBlockPos().getX() + serantis$r());
    }

    @Inject(method = "minZ", at = @At("HEAD"), cancellable = true)
    private void serantis$minZ(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(((BaseCoreBlockEntity) (Object) this).getBlockPos().getZ() - serantis$r());
    }

    @Inject(method = "maxZ", at = @At("HEAD"), cancellable = true)
    private void serantis$maxZ(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(((BaseCoreBlockEntity) (Object) this).getBlockPos().getZ() + serantis$r());
    }

    // Round: inside the square isn't enough, it has to be inside the circle.
    @Inject(method = "contains", at = @At("RETURN"), cancellable = true)
    private void serantis$round(net.minecraft.core.BlockPos p, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && !dev.serantis.battle.BaseShape.inside((BaseCoreBlockEntity) (Object) this, p.getX(), p.getZ())) cir.setReturnValue(false);
    }

    // The core's survey of its area skips the square's corners (and forgets anything it found there before).
    @Shadow private void survey(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos p, net.minecraft.world.level.block.state.BlockState s) { }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "scan", at = @At(value = "INVOKE",
            target = "Ldev/ahrivaea/base/BaseCoreBlockEntity;survey(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void serantis$roundSurvey(BaseCoreBlockEntity self, net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos p, net.minecraft.world.level.block.state.BlockState s) {
        boolean in = dev.serantis.battle.BaseShape.inside(self, p.getX(), p.getZ());
        survey(level, p, in ? s : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
    }
}
