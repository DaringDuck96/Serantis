package dev.serantis.battle.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ahrivaea.battle.Battles;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;

/** The battles' tick, and messages from players' games about battles, going wrong is logged (see Safe) instead of crashing the server or kicking the player. */
@Mixin(Battles.class)
public abstract class SafeBattlesTickMixin {
    @WrapMethod(method = "tick")
    private static void serantis$safeTick(MinecraftServer server, Operation<Void> original) {
        try {
            original.call(server);
        } catch (Throwable t) {
            dev.serantis.battle.Safe.caught("ticking battles", t);
        }
    }

    /** A battle (or Serantis) message from a player's game that goes wrong: logged, and they're told, not kicked. */
    @WrapMethod(method = "handle")
    private static String serantis$safeHandle(net.minecraft.server.level.ServerPlayer p, String json, Operation<String> original) {
        try {
            return original.call(p, json);
        } catch (Throwable t) {
            dev.serantis.battle.Safe.caught("a battle message from " + (p == null ? "?" : p.getPlainTextName()), t);
            return "Something went wrong with that. (It's been logged.)";
        }
    }
}
