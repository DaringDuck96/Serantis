package dev.serantis.battle.mixin;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.base.Raids;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The creatures mod's raids keep their timing, but goblins come instead (see GoblinRaids). */
@Mixin(Raids.class)
public abstract class RaidsMixin {
    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private static void serantis$goblins(ServerLevel level, BaseCoreBlockEntity core, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(dev.serantis.battle.GoblinRaids.begin(level, core));
    }
}
