package dev.serantis.battle.mixin.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Modpack items say what they're for (see ItemTips). */
@Mixin(ItemStack.class)
public abstract class ItemTipsMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void serantis$tip(Item.TooltipContext context, Player player, TooltipFlag flag, CallbackInfoReturnable<List<Component>> cir) {
        cir.setReturnValue(dev.serantis.battle.client.ItemTips.add((ItemStack) (Object) this, cir.getReturnValue()));
    }
}
