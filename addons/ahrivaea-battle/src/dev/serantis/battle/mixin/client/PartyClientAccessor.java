package dev.serantis.battle.mixin.client;

import com.google.gson.JsonObject;
import dev.ahrivaea.client.PartyClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The party state the server last sent (the creatures mod keeps it package-private). */
@Mixin(PartyClient.class)
public interface PartyClientAccessor {
    @Accessor("state")
    static JsonObject serantis$state() { throw new AssertionError(); }

    @Accessor("pendingStarter")
    static JsonObject serantis$pendingStarter() { throw new AssertionError(); }

    @Accessor("pendingStarter")
    static void serantis$setPendingStarter(JsonObject offer) { throw new AssertionError(); }
}
