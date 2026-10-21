package dev.serantis.battle.mixin.client;

import dev.serantis.battle.client.TradeClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opens the trade screens when a trade request arrives or is accepted. */
@Mixin(Minecraft.class)
public abstract class MinecraftTradeMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void serantis$trades(CallbackInfo ci) {
        TradeClient.tick();
        dev.serantis.battle.client.IntroScreen.autoOpen();
        dev.serantis.battle.client.FigureScreen.autoOpen();
        dev.serantis.battle.client.DialogueScreen.autoOpen();
        dev.serantis.battle.client.HarbingerScreen.autoOpen();
        dev.serantis.battle.client.WelcomeScreen.autoOpen();
        dev.serantis.battle.client.ClassScreen.autoOpen();
        dev.serantis.battle.client.SerantisKeys.tick();
        dev.serantis.battle.client.RiddleScreen.autoOpen();
        dev.serantis.battle.client.RaidScreen.autoOpen();
        dev.serantis.battle.client.CoopPromptScreen.autoOpen();
    }
}
