package dev.serantis.battle.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ahrivaea.battle.ArenaReturn;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

/** Back from the arena to where you were, not world spawn (see ReturnSpot). */
@Mixin(ArenaReturn.class)
public abstract class ArenaReturnMixin {
    @WrapMethod(method = "sendBack")
    private static void serantis$whereYouWere(ServerPlayer p, Operation<Void> original) {
        boolean inArena = p != null && p.level().dimension().equals(dev.ahrivaea.battle.Arena.DIMENSION);
        original.call(p);
        if (p != null) dev.serantis.battle.Safe.run("returning from the arena", () -> dev.serantis.battle.ReturnSpot.afterSendBack(p, inArena));
    }
}
