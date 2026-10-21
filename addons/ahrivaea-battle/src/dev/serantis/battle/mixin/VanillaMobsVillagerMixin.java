package dev.serantis.battle.mixin;

import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.serantis.battle.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Village villagers spawn at Level 5 with their profession; and spawns note where they are (for Nobles). */
@Mixin(targets = "dev.ahrivaea.creature.VanillaMobs")
public abstract class VanillaMobsVillagerMixin {
    /** The vanilla mob being replaced right now (villager or wandering_trader). */
    private static final ThreadLocal<Object> SERANTIS_MOB = new ThreadLocal<>();

    @Redirect(method = "replace", at = @At(value = "INVOKE", ordinal = 0, target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z"))
    private static boolean serantis$whichMob(java.util.Set<?> set, Object mob) {
        SERANTIS_MOB.set(mob);
        return set.contains(mob);
    }

    @Redirect(method = "replace", at = @At(value = "INVOKE", ordinal = 0, target =
            "Ldev/ahrivaea/Ahrivaea;spawn(Lnet/minecraft/server/level/ServerLevel;Ldev/ahrivaea/creature/Species;Ldev/ahrivaea/creature/Influence;IFDDD)Ldev/ahrivaea/creature/CreatureEntity;"))
    private static CreatureEntity serantis$villager(ServerLevel level, Species species, Influence influence, int lvl, float health, double x, double y, double z) {
        boolean trader = "wandering_trader".equals(SERANTIS_MOB.get());
        // Villages get their own villagers and guards (see Towns), not the ones Minecraft puts there.
        if (!trader && dev.serantis.battle.Towns.inVillage(level, BlockPos.containing(x, y, z))) return null;
        CreatureEntity e = Ahrivaea.spawn(level, species, influence, Villages.SPAWN_LEVEL, health, x, y, z);
        if (e == null) return null;
        if (trader) {                                                  // wandering traders swap creatures
            dev.serantis.battle.Wanderer.make(level, e);
        } else {
            if (level.getRandom().nextDouble() < Villages.GUARD_SHARE) Villages.makeGuard(e);
            else Villages.assign(e, level.getRandom());
            e.setPersistenceRequired();                                // villagers never despawn
        }
        return e;
    }

    @Redirect(method = "replace", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private static Holder<Biome> serantis$spawnSpot(ServerLevel level, BlockPos pos) {
        Villages.markSpawn(level, pos);
        return level.getBiome(pos);
    }
}
