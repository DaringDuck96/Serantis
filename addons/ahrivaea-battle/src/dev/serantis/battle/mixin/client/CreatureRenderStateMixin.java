package dev.serantis.battle.mixin.client;

import dev.ahrivaea.client.CreatureRenderState;
import dev.serantis.battle.fx.FxState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CreatureRenderState.class)
public abstract class CreatureRenderStateMixin implements FxState {
    @Unique private float serantis$white;

    @Override public float serantis$white() { return serantis$white; }
    @Override public void serantis$setWhite(float white) { serantis$white = white; }
}
