package dev.serantis.battle;

import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.Map;

/**
 * In singleplayer, leaving a world and opening another keeps the same Java process, so per-player state kept in
 * static maps (pending raids, visit timers, cutscenes, battles joined...) would follow the player into the
 * other world. The first time a new server is seen, all of it is cleared; the saved player data brings back
 * what belongs to the new world (a pending raid, for example).
 */
public final class WorldScope {
    private static MinecraftServer current;

    /** class -> its static per-player collections. */
    private static final String[][] STATE = {
            {"dev.serantis.battle.GoblinRaids", "BY_OWNER", "IN_BATTLE"},
            {"dev.serantis.battle.FigureVisit", "LAST_SCENE", "ACTIVE", "LAST_BATTLE", "TRIALS", "TRIAL_SCENE", "WON"},
            {"dev.serantis.battle.Intro", "ACTIVE"},
            {"dev.serantis.battle.Riddles", "ASKING", "SOLVED"},
            {"dev.serantis.battle.Harbinger", "PENDING", "RISING", "WATCHING"},
            {"dev.serantis.battle.FalseHydra", "PENDING"},
            {"dev.serantis.battle.Honing", "TALKING"},
            {"dev.serantis.battle.Linx", "LIFTS", "UNDO"},
            {"dev.serantis.battle.Market", "VIEWING"},
            {"dev.serantis.battle.Naming", "WAITING"},
            {"dev.serantis.battle.Staffs", "LINKING"},
            {"dev.serantis.battle.TeleportBoots", "TAPS"},
            {"dev.serantis.battle.Trades", "BY_PLAYER"},
            {"dev.serantis.battle.Tutor", "TALKING"},
            {"dev.serantis.battle.VillagerTalk", "TALKING"},
            {"dev.serantis.battle.Wanderer", "VIEWING"},
            {"dev.serantis.battle.WingedBoots", "STATES"},
            {"dev.serantis.battle.coop.CoopBattles", "INFO", "JOINED", "DONE"},
    };

    private WorldScope() {}

    /** Called often (every player tick): cheap unless the server changed. */
    public static void check(MinecraftServer server) {
        if (server == null || server == current) return;
        boolean first = current == null;
        current = server;
        if (first) return;                                             // the first world of this run: nothing stale yet
        for (String[] row : STATE) {
            try {
                Class<?> c = Class.forName(row[0]);
                for (int i = 1; i < row.length; i++) {
                    var f = c.getDeclaredField(row[i]);
                    f.setAccessible(true);
                    Object v = f.get(null);
                    if (v instanceof Map<?, ?> m) synchronized (m) { m.clear(); }
                    else if (v instanceof Collection<?> col) synchronized (col) { col.clear(); }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                dev.ahrivaea.Ahrivaea.LOGGER.warn("Couldn't reset {} for the new world", row[0], e);
            }
        }
        BaseCredit.forgetAll();
    }
}
