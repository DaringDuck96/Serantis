package dev.serantis.battle;

import com.google.gson.JsonObject;
import dev.ahrivaea.party.PartySync;
import net.minecraft.server.level.ServerPlayer;

/** The welcome guide every player sees the first time they join (see client/WelcomeScreen). */
public final class Welcome {
    /** Added to players: whether they've read the welcome guide. */
    public interface Holder {
        boolean serantis$welcomed();
        void serantis$setWelcomed(boolean seen);
        /** Not saved: whether the guide's been offered to this player since they joined. */
        boolean serantis$welcomeSent();
        void serantis$setWelcomeSent(boolean sent);
    }

    private Welcome() {}

    /** A few seconds after joining, a player who hasn't read the guide is sent it. */
    public static void tick(ServerPlayer p) {
        Holder h = (Holder) p;
        if (h.serantis$welcomed() || h.serantis$welcomeSent() || p.tickCount < 40) return;
        h.serantis$setWelcomeSent(true);
        PartySync.sync(p);
    }

    public static void seen(ServerPlayer p) {
        Holder h = (Holder) p;
        if (h.serantis$welcomed()) return;
        h.serantis$setWelcomed(true);
        PartySync.sync(p);
    }

    public static void describe(ServerPlayer p, JsonObject state) {
        if (!((Holder) p).serantis$welcomed()) state.addProperty("serantis_welcome", true);
    }
}
