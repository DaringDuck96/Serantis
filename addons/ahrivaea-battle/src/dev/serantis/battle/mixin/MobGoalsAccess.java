package dev.serantis.battle.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A mob's goals (for adding Leading's tempt goal to creatures). */
@Mixin(Mob.class)
public interface MobGoalsAccess {
    @Accessor("goalSelector")
    GoalSelector serantis$goals();
}
