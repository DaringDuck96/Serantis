package dev.serantis.battle.mixin;

import dev.ahrivaea.base.WorkerBrain;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What a worker is carrying (saved with it, so nothing is lost on a restart; see CreatureEntityMixin). */
@Mixin(WorkerBrain.class)
public interface WorkerBrainAccess {
    @Accessor("carried")
    ItemStack serantis$carried();

    @Accessor("carried")
    void serantis$setCarried(ItemStack stack);
}
