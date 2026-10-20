package dev.serantis.battle.mixin;

import dev.serantis.battle.skills.SkillHolder;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skills are kept through death and returning from the End. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSkillsMixin {
    @Inject(method = "restoreFrom", at = @At("RETURN"))
    private void serantis$keepSkills(ServerPlayer old, boolean keepEverything, CallbackInfo ci) {
        var mine = ((SkillHolder) (Object) this).serantis$skills();
        mine.clear();
        mine.addAll(((SkillHolder) (Object) old).serantis$skills());
        // XP is the main currency: it's kept through death.
        ServerPlayer self = (ServerPlayer) (Object) this;
        self.experienceLevel = old.experienceLevel;
        self.experienceProgress = old.experienceProgress;
        self.totalExperience = old.totalExperience;
    }

    // Dying wears down armour and held items instead (before anything is dropped).
    @Inject(method = "die", at = @At("HEAD"))
    private void serantis$died(net.minecraft.world.damagesource.DamageSource source, CallbackInfo ci) {
        dev.serantis.battle.Wear.defeat((ServerPlayer) (Object) this, "You died.");
    }
}
