package dev.serantis.battle.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Right-clicking food or another consumable in your inventory (with nothing on the cursor) opens the eat/feed menu (FeedScreen). */
@Mixin(AbstractContainerScreen.class)
public abstract class InventoryFeedMixin {
    @Shadow protected Slot hoveredSlot;
    @Shadow @Final protected AbstractContainerMenu menu;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void serantis$feedMenu(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof InventoryScreen inv) || event.button() != 1 || !menu.getCarried().isEmpty()) return;
        Slot s = hoveredSlot;
        if (s == null || !(s.container instanceof Inventory) || !dev.serantis.battle.client.FeedScreen.usable(s.getItem())) return;
        Minecraft.getInstance().gui.setScreen(new dev.serantis.battle.client.FeedScreen(inv, s.getContainerSlot()));
        cir.setReturnValue(true);
    }
}
