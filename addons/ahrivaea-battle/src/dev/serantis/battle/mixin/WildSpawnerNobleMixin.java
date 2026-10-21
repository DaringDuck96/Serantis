package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.WildSpawner;
import dev.serantis.battle.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Notes where wild creatures spawn, so a Noble's base nearby can improve the odds. */
@Mixin(WildSpawner.class)
public abstract class WildSpawnerNobleMixin {
    // Now and then a wild spawn in the Overworld is a Black Market Merchant instead.
    @org.spongepowered.asm.mixin.injection.Inject(method = "trySpawnNear", at = @At("RETURN"), cancellable = true)
    private static void serantis$blackMarket(ServerLevel level, net.minecraft.server.level.ServerPlayer player, net.minecraft.util.RandomSource random,
                                             org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<dev.ahrivaea.creature.CreatureEntity> cir) {
        if (cir.getReturnValue() != null) cir.setReturnValue(dev.serantis.battle.Market.maybeBlackMarket(level, cir.getReturnValue(), random));
        if (cir.getReturnValue() != null && !dev.serantis.battle.Market.isBlackMarket(cir.getReturnValue()))
            cir.setReturnValue(dev.serantis.battle.Masters.maybeMaster(level, cir.getReturnValue(), random));
        dev.serantis.battle.Masters.SPAWN_PLAYER.remove();
    }

    // The player the creature is spawning near (their Gym Master Stones and Eternita Stone change the odds).
    @org.spongepowered.asm.mixin.injection.Inject(method = "trySpawnNear", at = @At("HEAD"))
    private static void serantis$near(ServerLevel level, net.minecraft.server.level.ServerPlayer player, net.minecraft.util.RandomSource random,
                                      org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<dev.ahrivaea.creature.CreatureEntity> cir) {
        dev.serantis.battle.Masters.SPAWN_PLAYER.set(player);
    }

    @Redirect(method = "trySpawnNear", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private static Holder<Biome> serantis$spawnSpot(ServerLevel level, BlockPos pos) {
        Villages.markSpawn(level, pos);
        return level.getBiome(pos);
    }
}
