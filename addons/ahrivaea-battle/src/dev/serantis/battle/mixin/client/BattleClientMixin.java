package dev.serantis.battle.mixin.client;

import dev.ahrivaea.client.battle.BattleClient;
import dev.serantis.battle.client.RenameScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Battle updates don't replace the naming screen; it returns to the latest battle screen when done. */
@Mixin(BattleClient.class)
public abstract class BattleClientMixin {
    @Redirect(method = "receive", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private static void serantis$keepNaming(Gui gui, Screen screen) {
        if (gui.screen() instanceof RenameScreen naming) { naming.setParent(screen); return; }
        if (gui.screen() instanceof dev.serantis.battle.client.EquipScreen equip && screen instanceof dev.ahrivaea.client.battle.BattleScreen) { equip.setParent(screen); return; }
        gui.setScreen(screen);
    }
}
