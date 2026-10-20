package dev.serantis.battle.mixin;

import dev.serantis.battle.skills.SkillHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/** Players remember the skill tree nodes they've bought. */
@Mixin(Player.class)
public abstract class PlayerSkillsMixin implements SkillHolder {
    @Unique private final Set<String> serantis$skills = new LinkedHashSet<>();

    @Override public Set<String> serantis$skills() { return serantis$skills; }

    // XP is kept through death, so dying drops no XP orbs.
    @Inject(method = "getBaseExperienceReward", at = @At("HEAD"), cancellable = true)
    private void serantis$keepXp(net.minecraft.server.level.ServerLevel level, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(0);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void serantis$saveSkills(ValueOutput out, CallbackInfo ci) {
        out.putString("SerantisSkills", String.join(",", serantis$skills));
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void serantis$loadSkills(ValueInput in, CallbackInfo ci) {
        serantis$skills.clear();
        for (String s : in.getStringOr("SerantisSkills", "").split(",")) if (!s.isBlank()) serantis$skills.add(s.trim());
    }
}
