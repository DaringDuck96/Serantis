package dev.serantis.battle.mixin;

import com.google.gson.JsonObject;
import dev.ahrivaea.party.Dex;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The Creature Dex counts the 434 forms you can find (the retired Arcane Dragon is left out), and has one
 * extra page that doesn't count towards it: the Acolyte of the First (see DexExtras).
 */
@Mixin(Dex.class)
public abstract class DexMixin {
    @Redirect(method = "send", at = @At(value = "INVOKE", target = "Lcom/google/gson/JsonObject;toString()Ljava/lang/String;"))
    private static String serantis$extras(JsonObject data, ServerPlayer player) {
        dev.serantis.battle.DexExtras.apply(player, data);
        return data.toString();
    }
}
