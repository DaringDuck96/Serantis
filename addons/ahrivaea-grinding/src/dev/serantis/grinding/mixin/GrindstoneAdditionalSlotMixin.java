package dev.serantis.grinding.mixin;

import dev.serantis.grinding.Grinding;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The grindstone's additional slot also takes cobblestone and gravel. */
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$3")
public abstract class GrindstoneAdditionalSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void serantis$grindable(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (Grinding.grindable(stack)) cir.setReturnValue(true);
    }
}
