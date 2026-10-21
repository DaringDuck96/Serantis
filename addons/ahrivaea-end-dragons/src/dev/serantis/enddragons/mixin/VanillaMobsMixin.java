package dev.serantis.enddragons.mixin;

import dev.serantis.enddragons.EndDragons;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The creatures mod removes the Ender Dragon the moment it appears and marks the fight as won
 * (dev.ahrivaea.creature.VanillaMobs, entity-load handler). When that happens in the End, queue one
 * of the End dragons; spawn it on the mod's own end-of-tick handler.
 */
@Mixin(targets = "dev.ahrivaea.creature.VanillaMobs")
public abstract class VanillaMobsMixin {

    // The creatures mod marks the fight won the moment the dragon is added to the world, but vanilla only
    // records which dragon belongs to the fight on the next line, so the "won" call was ignored and the fight
    // kept spawning dragons. Tell the fight which dragon this is first.
    @Inject(method = "lambda$init$1", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/dimension/end/EnderDragonFight;setDragonKilled(Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;)V"))
    private static void serantis$ownDragon(Entity entity, ServerLevel level, CallbackInfo ci) {
        var fight = level.getDragonFight();
        if (fight != null) ((EnderDragonFightAccess) fight).serantis$setDragonUUID(entity.getUUID());
    }

    // Runs at the mod's "The Ender Dragon appeared and was defeated straight away" log line.
    @Inject(method = "lambda$init$1", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V"))
    private static void serantis$dragonAppeared(Entity entity, ServerLevel level, CallbackInfo ci) {
        if (level.dimension() == Level.END) {
            EndDragons.queue(level, entity.getX(), entity.getZ());
        }
    }

    // The mod's end-of-server-tick handler, where it also spawns its replacement creatures.
    @Inject(method = "lambda$init$2", at = @At("HEAD"))
    private static void serantis$tick(MinecraftServer server, CallbackInfo ci) {
        EndDragons.tick(server);
    }
}
