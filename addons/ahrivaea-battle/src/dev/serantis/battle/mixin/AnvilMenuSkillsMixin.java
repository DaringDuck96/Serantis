package dev.serantis.battle.mixin;

import dev.serantis.battle.skills.Skills;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attuned: anvils cost 25% less XP (at least 1 level). */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuSkillsMixin {
    @Shadow @Final private DataSlot cost;

    @Inject(method = "createResult", at = @At("RETURN"))
    private void serantis$attuned(CallbackInfo ci) {
        Player p = ((ItemCombinerMenuAccessor) (Object) this).serantis$player();
        int c = cost.get();
        if (p != null && c > 1 && Skills.has(p, "arcana_6")) cost.set(Math.max(1, (int) Math.ceil(c * 0.75)));
    }
}
