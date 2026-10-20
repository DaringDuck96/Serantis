package dev.serantis.grinding.mixin;

import dev.serantis.grinding.Grinding;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Taking ground gravel or sand uses up only what was ground; an odd item stays in its slot. */
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
public abstract class GrindstoneResultSlotMixin {
    @Redirect(method = "onTake", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Container;setItem(ILnet/minecraft/world/item/ItemStack;)V"))
    private void serantis$keepLeftover(Container slots, int index, ItemStack stack) {
        Grinding.Leftover left = Grinding.leftover(slots);
        slots.setItem(index, left != null && left.slot() == index ? left.stack().copy() : stack);
    }
}
