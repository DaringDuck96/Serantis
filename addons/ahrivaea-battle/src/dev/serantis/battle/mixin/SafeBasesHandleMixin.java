package dev.serantis.battle.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ahrivaea.base.Bases;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

/** A Base Core screen message from a player's game that goes wrong is logged (see Safe), not a kick or a crash. */
@Mixin(Bases.class)
public abstract class SafeBasesHandleMixin {
    @WrapMethod(method = "handle")
    private static void serantis$safeHandle(ServerPlayer p, String json, Operation<Void> original) {
        try {
            original.call(p, json);
        } catch (Throwable t) {
            dev.serantis.battle.Safe.caught("a base message from " + (p == null ? "?" : p.getPlainTextName()), t);
        }
    }
}
