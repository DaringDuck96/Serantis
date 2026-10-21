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
        /** Whether they've been given the book "Serantia: A World Forged by Dragons". */
        boolean serantis$loreBook();
        void serantis$setLoreBook(boolean given);
        /** Battles finished, won or lost (-1: not counted yet). */
        int serantis$battlesFought();
        void serantis$setBattlesFought(int n);
        /** Which Mysterious Figure scenes have played (2: the one after the 10th battle). */
        int serantis$figureMet();
        void serantis$setFigureMet(int n);
        /** A goblin raid waiting at (or being fought for) one of their bases ("": none). See GoblinRaids. */
        String serantis$raid();
        void serantis$setRaid(String saved);
        /** Titles of the lore books they've found. */
        java.util.Set<String> serantis$loreTitles();
        /** Today's bounty board contracts (see Bounties). */
        String serantis$bounty();
        void serantis$setBounty(String saved);
    }

    private Welcome() {}

    /** A few seconds after joining, a player who hasn't read the guide is sent it. */
    public static void tick(ServerPlayer p) {
        Holder h = (Holder) p;
        if (!h.serantis$loreBook() && p.tickCount >= 20) { h.serantis$setLoreBook(true); LoreBook.give(p); }
        Intro.tick(p);                                                             // the opening scene comes first
        if (h.serantis$welcomeSent() || p.tickCount < 40) return;
        if (h.serantis$welcomed() && PlayerClass.of(p) != null) return;          // nothing to show (guide read, class picked)
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
        if (!((Holder) p).serantis$welcomed() && !Intro.pending(p)) state.addProperty("serantis_welcome", true);
    }
}
