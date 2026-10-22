package dev.serantis.battle.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Each item a furnace at a base finishes counts towards the base owner's Items Smelted (see BaseCredit). */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceSmeltedMixin {
    @Inject(method = "setRecipeUsed", at = @At("HEAD"))
    private void serantis$smelted(net.minecraft.world.item.crafting.RecipeHolder<?> recipe, CallbackInfo ci) {
        try {
        AbstractFurnaceBlockEntity self = (AbstractFurnaceBlockEntity) (Object) this;
        if (recipe != null && self.getLevel() instanceof net.minecraft.server.level.ServerLevel level) dev.serantis.battle.BaseCredit.smelted(level, self.getBlockPos());
            } catch (Throwable serantisT) { dev.serantis.battle.Safe.caught("serantis.smelted", serantisT); }
    }
}
