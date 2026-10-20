package dev.serantis.battle.mixin.client;

import dev.ahrivaea.client.CreatureRenderState;
import dev.ahrivaea.client.CreatureRenderer;
import dev.ahrivaea.creature.CreatureEntity;
import dev.serantis.battle.fx.Fx;
import dev.serantis.battle.fx.FxHolder;
import dev.serantis.battle.fx.FxState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Plays battle effects on creatures: the attack wiggle and the coloured flashes (see Fx). */
@Mixin(CreatureRenderer.class)
public abstract class CreatureRendererMixin extends MobRenderer<CreatureEntity, CreatureRenderState, EntityModel<CreatureRenderState>> {
    private CreatureRendererMixin(EntityRendererProvider.Context context) { super(context, null, 0f); }

    @Inject(method = "extractRenderState(Ldev/ahrivaea/creature/CreatureEntity;Ldev/ahrivaea/client/CreatureRenderState;F)V", at = @At("RETURN"))
    private void serantis$fx(CreatureEntity entity, CreatureRenderState state, float partial, CallbackInfo ci) {
        FxHolder fx = (FxHolder) (Object) entity;
        float now = entity.tickCount + partial;
        float white = 0f;

        float w = now - fx.serantis$wiggleStart();
        if (w >= 0 && w < Fx.WIGGLE_TICKS) {
            float fade = 1f - w / Fx.WIGGLE_TICKS;
            state.bodyRot += (float) Math.sin(w * 1.9f) * 16f * fade;
        }

        float f = now - fx.serantis$flashStart();
        int kind = fx.serantis$flashKind();
        if (kind != 0 && f >= 0 && f < Fx.FLASH_TICKS) {
            float k = 1f - f / Fx.FLASH_TICKS;          // full colour at once, fading out
            if (kind == Fx.MISS) white = 0.85f * k;
            else state.tint = blend(state.tint, Fx.color(kind), k);
        }
        ((FxState) (Object) state).serantis$setWhite(white);
    }

    /** Multiplies the model tint toward a colour (k = 0 leaves it, k = 1 is fully that colour). */
    private static int blend(int tint, int color, float k) {
        int out = tint & 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int t = (tint >> shift) & 0xFF, c = (color >> shift) & 0xFF;
            float m = 1f - k + k * (c / 255f);
            out |= Math.min(255, Math.round(t * m)) << shift;
        }
        return out;
    }

    // The white wash vanilla uses for a creeper about to explode, used here for a miss.
    @Override
    protected float getWhiteOverlayProgress(CreatureRenderState state) {
        return ((FxState) (Object) state).serantis$white();
    }
}
