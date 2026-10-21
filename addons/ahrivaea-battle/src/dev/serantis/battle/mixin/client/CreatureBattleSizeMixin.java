package dev.serantis.battle.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ahrivaea.client.CreatureRenderState;
import dev.ahrivaea.client.CreatureRenderer;
import dev.ahrivaea.creature.CreatureEntity;
import dev.serantis.battle.client.BattleTiles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In battle, creatures bigger than one tile fill all of their tiles (see BattleTiles). */
@Mixin(CreatureRenderer.class)
public abstract class CreatureBattleSizeMixin {
    private static final String SHRINK = "Ldev/ahrivaea/client/CreatureRenderer;battleShrink(F)F";

    @Inject(method = "extractRenderState(Ldev/ahrivaea/creature/CreatureEntity;Ldev/ahrivaea/client/CreatureRenderState;F)V", at = @At("HEAD"))
    private void serantis$tiles(CreatureEntity e, CreatureRenderState state, float partial, CallbackInfo ci) {
        ((BattleTiles.Holder) state).serantis$setTiles(BattleTiles.tiles(e));
    }

    @Redirect(method = "extractRenderState(Ldev/ahrivaea/creature/CreatureEntity;Ldev/ahrivaea/client/CreatureRenderState;F)V",
            at = @At(value = "INVOKE", target = SHRINK))
    private float serantis$nameTag(float extent, CreatureEntity e, CreatureRenderState state, float partial) {
        return BattleTiles.shrink(extent, BattleTiles.tiles(e));
    }

    @Redirect(method = "scale(Ldev/ahrivaea/client/CreatureRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "INVOKE", target = SHRINK))
    private float serantis$scale(float extent, CreatureRenderState state, PoseStack pose) {
        return BattleTiles.shrink(extent, ((BattleTiles.Holder) state).serantis$tiles());
    }

    @Redirect(method = "getShadowRadius(Ldev/ahrivaea/client/CreatureRenderState;)F",
            at = @At(value = "INVOKE", target = SHRINK))
    private float serantis$shadow(float extent, CreatureRenderState state) {
        return BattleTiles.shrink(extent, ((BattleTiles.Holder) state).serantis$tiles());
    }
}
