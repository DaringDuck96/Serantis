package dev.serantis.battle.mixin.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lingering confusion (see client/PlayerAilments): forward is back, back is forward, left is right, right is left. */
@Mixin(KeyboardInput.class)
public abstract class ConfusedInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("RETURN"))
    private void serantis$switch(CallbackInfo ci) {
        if (!dev.serantis.battle.client.PlayerAilments.confused()) return;
        Input k = this.keyPresses;
        this.keyPresses = new Input(k.backward(), k.forward(), k.right(), k.left(), k.jump(), k.shift(), k.sprint());
        this.moveVector = new Vec2(-this.moveVector.x, -this.moveVector.y);
    }
}
