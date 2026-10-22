package dev.serantis.battle.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player logging out leaves any co-op battle before their data is saved, so their creatures' wounds are kept. */
@Mixin(PlayerList.class)
public abstract class PlayerListLeaveMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void serantis$leaving(ServerPlayer player, CallbackInfo ci) {
        dev.serantis.battle.Safe.run("leaving battles for " + player.getPlainTextName(), () -> dev.serantis.battle.coop.CoopBattles.disconnected(player));
        dev.serantis.battle.Safe.run("WingedBoots.forget", () -> dev.serantis.battle.WingedBoots.forget(player));
        dev.serantis.battle.Safe.run("SpawnAlerts.forget", () -> dev.serantis.battle.SpawnAlerts.forget(player));
        dev.serantis.battle.Safe.run("VersionCheck.forget", () -> dev.serantis.battle.VersionCheck.forget(player));
    }
}
