package dev.serantis.battle.mixin.client;

import com.google.gson.JsonObject;
import dev.ahrivaea.client.StarterScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The starter offer a StarterScreen shows (for the opening scene, which opens it itself). */
@Mixin(StarterScreen.class)
public interface StarterAccess {
    @Accessor("offer")
    JsonObject serantis$offer();
}
