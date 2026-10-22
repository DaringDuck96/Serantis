package dev.serantis.battle.mixin.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.client.DexScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The dex counter reads out of the forms you can find (434), not the number of pages (see DexExtras). */
@Mixin(DexScreen.class)
public abstract class DexScreenMixin {
    @Shadow private JsonObject data;

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lcom/google/gson/JsonArray;size()I", ordinal = 0))
    private int serantis$total(JsonArray entries) {
        return data != null && data.has("serantis_total") ? data.get("serantis_total").getAsInt() : entries.size();
    }
}
