package dev.serantis.battle.mixin;

import dev.serantis.battle.Villages;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Innkeepers: sleeping through the night refills your hearts if one works at any base of yours. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelInnMixin {
    @Inject(method = "wakeUpAllPlayers", at = @At("HEAD"))
    private void serantis$innkeeper(CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        for (ServerPlayer p : self.players()) {
            if (!p.isSleeping() || p.getHealth() >= p.getMaxHealth()) continue;
            if (Villages.ownsBaseWith(self.getServer(), p.getUUID(), "innkeeper")) {
                p.setHealth(p.getMaxHealth());
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal("You slept at the inn and woke up fully rested."));
            }
        }
    }
}
