package dev.serantis.battle.mixin;

import dev.ahrivaea.base.Bases;
import dev.ahrivaea.base.WorkerBrain;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.party.CaughtCreature;
import dev.serantis.battle.Pace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The base screen shows each worker's Charisma-based work speed. */
@Mixin(Bases.class)
public abstract class BasesMixin {
    @Unique private static CaughtCreature serantis$shown;

    // The worker whose speed is about to be shown (its level is read just before).
    @Redirect(method = "view", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/party/CaughtCreature;level()I"))
    private static int serantis$level(CaughtCreature creature) {
        serantis$shown = creature;
        return creature.level();
    }

    @Redirect(method = "view", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/base/WorkerBrain;speedAt(I)D"))
    private static double serantis$speed(int level) {
        CaughtCreature c = serantis$shown;
        if (c != null && c.influence() != null) {
            var species = Species.get(c.species());
            if (species.isPresent()) return Pace.workSpeed(level, species.get().statsFor(c.influence()).charisma());
        }
        return WorkerBrain.speedAt(level);
    }
}
