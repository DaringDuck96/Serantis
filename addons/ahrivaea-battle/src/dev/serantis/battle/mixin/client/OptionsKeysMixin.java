package dev.serantis.battle.mixin.client;

import dev.serantis.battle.client.SerantisKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/** Adds the Serantis keys to the Controls screen (and options.txt), so they can be rebound. */
@Mixin(Options.class)
public abstract class OptionsKeysMixin {
    @Shadow @Final @Mutable public KeyMapping[] keyMappings;

    @Inject(method = "load", at = @At("HEAD"))
    private void serantis$keys(CallbackInfo ci) {
        if (Arrays.asList(keyMappings).contains(SerantisKeys.BACKPACK)) return;
        KeyMapping[] all = Arrays.copyOf(keyMappings, keyMappings.length + SerantisKeys.ALL.length);
        System.arraycopy(SerantisKeys.ALL, 0, all, keyMappings.length, SerantisKeys.ALL.length);
        keyMappings = all;
    }
}
