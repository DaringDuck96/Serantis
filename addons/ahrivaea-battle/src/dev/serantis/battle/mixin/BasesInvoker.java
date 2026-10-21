package dev.serantis.battle.mixin;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.base.Bases;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access to the creatures mod's package-private Bases.unassign (the base screen's Send away). */
@Mixin(Bases.class)
public interface BasesInvoker {
    @Invoker("unassign")
    static String serantis$unassign(ServerPlayer player, BaseCoreBlockEntity core, int index) {
        throw new AssertionError();
    }
}
