package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.Drops;
import dev.ahrivaea.creature.Species;
import dev.serantis.battle.skills.SkillEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Drops.class)
public abstract class DropsMixin {
    @Redirect(method = "roll", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/creature/Species$Drop;chance()F"))
    private static float serantis$treasureSense(Species.Drop drop) {
        return SkillEffects.dropChance(drop.chance());
    }
}
