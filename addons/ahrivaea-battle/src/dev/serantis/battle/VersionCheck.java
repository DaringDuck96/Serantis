package dev.serantis.battle;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everyone plays with the server's mods. When a player joins, their game says which Serantis version it has
 * ("serantis_hello", sent by client/SerantisKeys); if it isn't the server's own, or nothing is said within 25 seconds (the game repeats it every few seconds until the server confirms)
 * (the Serantis mods are missing or older than this check), they're disconnected with a message saying which version
 * to get. Only dedicated servers check (single-player and LAN games share the host's mods).
 */
public final class VersionCheck {
    public static final String DOWNLOAD = "https://github.com/DaringDuck96/Serantis/releases/latest";
    private static final Set<UUID> OK = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> WAITED = new ConcurrentHashMap<>();
    private static String mine;

    private VersionCheck() {}

    /** This game's (or server's) Serantis battle add-on version, from Fabric. */
    public static synchronized String version() {
        if (mine == null) {
            try {
                Class<?> fl = Class.forName("net.fabricmc.loader.api.FabricLoader");
                Object loader = fl.getMethod("getInstance").invoke(null);
                Object opt = fl.getMethod("getModContainer", String.class).invoke(loader, "serantis_battle");
                Object container = ((java.util.Optional<?>) opt).orElse(null);
                Object meta = container.getClass().getMethod("getMetadata").invoke(container);
                Object ver = meta.getClass().getMethod("getVersion").invoke(meta);
                mine = (String) ver.getClass().getMethod("getFriendlyString").invoke(ver);
            } catch (Throwable t) {
                mine = "unknown";
            }
        }
        return mine;
    }

    private static void kick(ServerPlayer p, String why) {
        p.connection.disconnect(Component.literal(why + "\n\nThis server runs Serantis " + version() + ".\nGet the matching mods from:\n" + DOWNLOAD)
                .withStyle(ChatFormatting.GOLD));
    }

    /** The player's game said hello with its version. */
    public static void hello(ServerPlayer p, String theirs) {
        if (version().equals(theirs) || "unknown".equals(version())) {
            if (OK.add(p.getUUID())) dev.ahrivaea.party.PartySync.sync(p);   // tell their game it's settled
            return;
        }
        kick(p, "Your Serantis mods (" + theirs + ") don't match the server's.");
    }

    /** Every second, per player: no hello after 10 seconds means the mods are missing or too old. */
    public static void tick(ServerPlayer p) {
        if (OK.contains(p.getUUID())) return;
        if (!p.level().getServer().isDedicatedServer()) { OK.add(p.getUUID()); return; }   // single-player (and LAN from it): same game, same mods
        int waited = WAITED.merge(p.getUUID(), 1, Integer::sum);
        if (waited >= 25) kick(p, "Your game doesn't have the Serantis mods, or has an older version.");
    }

    /** "serantis_server_version" in the party state, so games can tell this server has Serantis. */
    public static void describe(ServerPlayer p, com.google.gson.JsonObject state) {
        state.addProperty("serantis_server_version", version());
        if (OK.contains(p.getUUID())) state.addProperty("serantis_version_ok", true);
    }

    public static void forget(ServerPlayer p) {
        OK.remove(p.getUUID());
        WAITED.remove(p.getUUID());
    }
}
