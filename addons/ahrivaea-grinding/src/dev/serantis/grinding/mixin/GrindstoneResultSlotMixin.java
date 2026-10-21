package dev.serantis.grinding.mixin;

import dev.serantis.grinding.Grinding;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Taking ground gravel or sand uses up only what was taken (2 per item); the rest, and an odd item, stay in the slot. */
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
public abstract class GrindstoneResultSlotMixin {
    @Redirect(method = "onTake", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Container;setItem(ILnet/minecraft/world/item/ItemStack;)V"))
    private void serantis$keepLeftover(Container slots, int index, ItemStack stack, net.minecraft.world.entity.player.Player player, ItemStack taken) {
        Grinding.Leftover left = Grinding.leftover(slots);
        if (left == null || left.slot() != index) { slots.setItem(index, stack); return; }
        int remaining = left.input().getCount() - taken.getCount() * 2;   // only what was taken is used up (dropping one takes 2)
        slots.setItem(index, remaining > 0 ? left.input().copyWithCount(remaining) : ItemStack.EMPTY);
    }
}
