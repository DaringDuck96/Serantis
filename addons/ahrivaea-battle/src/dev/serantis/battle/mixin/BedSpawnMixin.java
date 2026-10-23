package dev.serantis.battle.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Respawning puts you by your bed whenever it still stands (see BedSpawn). */
@Mixin(PlayerList.class)
public abstract class BedSpawnMixin {
    @Inject(method = "respawn", at = @At("RETURN"))
    private void serantis$byYourBed(ServerPlayer old, boolean keepAll, Entity.RemovalReason reason, CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer p = cir.getReturnValue();
        if (p != null) dev.serantis.battle.Safe.run("respawning by a bed", () -> dev.serantis.battle.BedSpawn.respawned(old, p));
    }
}
