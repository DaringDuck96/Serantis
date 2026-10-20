package dev.serantis.grinding.mixin;

import dev.serantis.grinding.Grinding;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMenuMixin {
    @Shadow @Final private Container repairSlots;

    @Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
    private void serantis$grind(ItemStack top, ItemStack bottom, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack out = Grinding.result(repairSlots, top, bottom);
        if (out != null) cir.setReturnValue(out);
    }
}
