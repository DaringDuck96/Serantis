package dev.serantis.battle.mixin.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.client.StarterScreen;
import dev.serantis.battle.client.RenameScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After picking a starter, offer to name it. */
@Mixin(StarterScreen.class)
public abstract class StarterScreenMixin {
    @Shadow private int selected;
    @Shadow private JsonArray options() { return null; }

    @Inject(method = "confirm", at = @At("TAIL"))
    private void serantis$name(CallbackInfo ci) {
        JsonArray all = options();
        if (all == null || selected < 0 || selected >= all.size()) return;
        JsonObject o = all.get(selected).getAsJsonObject();
        RenameScreen.open(null, o.has("name") ? o.get("name").getAsString() : "starter");
    }
}
