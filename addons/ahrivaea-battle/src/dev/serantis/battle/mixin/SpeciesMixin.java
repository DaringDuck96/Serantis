package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.serantis.battle.BattleStats;
import dev.serantis.battle.SpawnOdds;
import net.minecraft.core.Holder;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

@Mixin(Species.class)
public abstract class SpeciesMixin {
    @Shadow private static Map<String, Species> all;

    // After the creatures load, swap in the Serantis stats and Level 1-20 spawning.
    @Inject(method = "reload", at = @At("RETURN"))
    private static void serantis$applyBattleStats(ResourceManager resources, CallbackInfo ci) {
        all = BattleStats.apply(all);
    }

    // Every spawn rolls a rarity first (70% Common, 15% Uncommon, 10% Rare, 4% Super Rare, 1% Legendary).
    @Inject(method = "pickFor(Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/Level;Lnet/minecraft/util/RandomSource;Ljava/util/function/BiPredicate;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true)
    private static void serantis$rarityFirst(Holder<Biome> biome, Level level, RandomSource random,
                                             BiPredicate<Species, Influence> filter,
                                             CallbackInfoReturnable<Optional<Species.Pick>> cir) {
        if (SpawnOdds.INSIDE.get()) return;
        BiPredicate<Species, Influence> f = dev.serantis.battle.NightOnly.wrap(level, filter);   // by day: no creatures of the night
        Optional<Species.Pick> pick = SpawnOdds.pick(biome, level, random, f);
        if (pick.isPresent()) { cir.setReturnValue(pick); return; }
        if (f != filter) {                                              // the creatures mod's own pick, with the same filter
            SpawnOdds.INSIDE.set(true);
            try { cir.setReturnValue(Species.pickFor(biome, level, random, f)); } finally { SpawnOdds.INSIDE.set(false); }
        }
    }

    // Wild spawns look for creatures within 5 levels of the player's own level, which goes up to 50.
    // Keep that window inside Level 1-20 so high-level players still find creatures.
    @ModifyVariable(method = "pickForLevels", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static int serantis$minLevel(int min) {
        return BattleStats.clampLevel(min);
    }

    @ModifyVariable(method = "pickForLevels", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static int serantis$maxLevel(int max) {
        return BattleStats.clampLevel(max);
    }
}
