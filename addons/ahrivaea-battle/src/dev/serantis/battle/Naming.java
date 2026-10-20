package dev.serantis.battle;

import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.party.PartySync;
import dev.serantis.battle.moves.MovesHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nicknames. Right after a player recruits a creature or picks their starter, their screen offers to
 * name it; the name comes back here as a battle action {"kind": "serantis_rename", "name": "..."}.
 */
public final class Naming {
    public static final int MAX_LENGTH = 20;
    private static final Map<UUID, CaughtCreature> WAITING = new ConcurrentHashMap<>();

    private Naming() {}

    /** The creature this player just got, waiting for a name. */
    public static void offer(ServerPlayer player, CaughtCreature caught) {
        if (player != null && caught != null) WAITING.put(player.getUUID(), caught);
    }

    public static String clean(String name) {
        if (name == null) return "";
        String s = name.replace("§", "").replaceAll("\\p{Cntrl}", "").trim();
        return s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH).trim() : s;
    }

    public static String rename(ServerPlayer player, String name) {
        CaughtCreature target = WAITING.remove(player.getUUID());
        if (target == null) return null;
        String clean = clean(name);
        if (clean.isEmpty()) return null;                   // kept its species name
        boolean found = false;
        for (CaughtCreature c : Party.get(player)) if (c == target) found = true;
        for (CaughtCreature c : Party.box(player)) if (c == target) found = true;
        if (!found) return null;
        ((MovesHolder) (Object) target).serantis$setNickname(clean);
        PartySync.sync(player);
        player.sendSystemMessage(Component.literal("Named it " + clean + "."));
        return null;
    }

    public static String nickname(Object holder) {
        return holder instanceof MovesHolder h ? h.serantis$nickname() : null;
    }
}
