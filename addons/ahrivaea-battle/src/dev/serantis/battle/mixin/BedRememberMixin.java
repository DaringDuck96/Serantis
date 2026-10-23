package dev.serantis.battle.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every respawn point the game sets is remembered (see BedSpawn). */
@Mixin(ServerPlayer.class)
public abstract class BedRememberMixin {
    @Inject(method = "setRespawnPosition", at = @At("HEAD"))
    private void serantis$remember(ServerPlayer.RespawnConfig cfg, boolean message, CallbackInfo ci) {
        dev.serantis.battle.Safe.run("remembering a bed", () -> dev.serantis.battle.BedSpawn.set((ServerPlayer) (Object) this, cfg));
    }
}
