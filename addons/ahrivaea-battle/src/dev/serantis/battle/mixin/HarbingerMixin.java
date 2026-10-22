package dev.serantis.battle.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Warden appearing (summoned by sculk shriekers, the vanilla way) is replaced by the Harbinger of Calamity,
 * whatever the vanilla-mob rules (see Harbinger). Hooks the creatures mod's entity-load and end-of-tick handlers.
 */
@Mixin(targets = "dev.ahrivaea.creature.VanillaMobs")
public abstract class HarbingerMixin {
    @Inject(method = "lambda$init$1", at = @At("HEAD"), cancellable = true)
    private static void serantis$warden(Entity entity, ServerLevel level, CallbackInfo ci) {
        if (!"minecraft:warden".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())) return;
        dev.serantis.battle.Harbinger.queue(level, entity.getX(), entity.getY(), entity.getZ());
        entity.discard();
        ci.cancel();
    }

    // A Wither summoned (soul sand T and three wither skeleton skulls) is replaced by the False Hydra (see FalseHydra).
    @Inject(method = "lambda$init$1", at = @At("HEAD"), cancellable = true)
    private static void serantis$wither(Entity entity, ServerLevel level, CallbackInfo ci) {
        if (!"minecraft:wither".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())) return;
        dev.serantis.battle.FalseHydra.queue(level, entity.getX(), entity.getY(), entity.getZ());
        entity.discard();
        ci.cancel();
    }

    @Inject(method = "lambda$init$2", at = @At("HEAD"))
    private static void serantis$tick(MinecraftServer server, CallbackInfo ci) {
        dev.serantis.battle.Harbinger.tick();
        dev.serantis.battle.FalseHydra.tick();
    }
}
