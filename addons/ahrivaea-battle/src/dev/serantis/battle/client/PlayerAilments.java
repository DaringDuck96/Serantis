package dev.serantis.battle.client;

/**
 * Lingering confusion and fright on your own screen (see Ailments on the server): confused, your movement keys are
 * switched (forward goes back, left goes right); frightened, you see half as far. Never in battle.
 */
public final class PlayerAilments {
    private PlayerAilments() {}

    private static String current() {
        if (dev.ahrivaea.client.battle.BattleClient.active()) return null;
        var st = TradeClient.state();
        return st != null && st.has("serantis_player_ailment") ? st.get("serantis_player_ailment").getAsString() : null;
    }

    public static boolean confused() { return "confused".equals(current()); }

    public static boolean frightened() { return "frightened".equals(current()); }
}
