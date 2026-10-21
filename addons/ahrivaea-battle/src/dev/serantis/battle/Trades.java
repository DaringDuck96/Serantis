package dev.serantis.battle;

import com.google.gson.JsonObject;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.party.PartySync;
import dev.serantis.battle.moves.MovesHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creature trades between two players. Right-click another player to ask; they accept or decline;
 * then each picks one creature from their party and confirms, and the two creatures swap owners.
 *
 * The client talks to this through battle actions ({"kind": "serantis_trade_..."}) and sees the trade
 * as "serantis_trade" in its party state (see describe), which its screens follow.
 */
public final class Trades {
    public static final long INVITE_MS = 60_000;

    static final class Session {
        final int id;
        final UUID from, to;
        final long created = System.currentTimeMillis();
        boolean open;                          // accepted, now picking creatures
        int fromSlot = -1, toSlot = -1;
        CaughtCreature fromPick, toPick;
        boolean fromOk, toOk;

        Session(int id, UUID from, UUID to) { this.id = id; this.from = from; this.to = to; }

        UUID partner(UUID me) { return me.equals(from) ? to : from; }
        boolean isFrom(UUID me) { return me.equals(from); }
    }

    private static final Map<UUID, Session> BY_PLAYER = new ConcurrentHashMap<>();
    private static int nextId = 1;

    private Trades() {}

    private static MinecraftServer server(ServerPlayer p) { return ((ServerLevel) p.level()).getServer(); }

    private static ServerPlayer online(MinecraftServer server, UUID id) {
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }

    private static void tell(ServerPlayer p, String msg, ChatFormatting color) {
        if (p != null) p.sendSystemMessage(Component.literal(msg).withStyle(color));
    }

    private static String name(ServerPlayer p) { return p == null ? "?" : p.getPlainTextName(); }

    /** The player's current trade, if it's still valid (both online, invite not expired). */
    private static Session current(ServerPlayer p) {
        Session s = BY_PLAYER.get(p.getUUID());
        if (s == null) return null;
        MinecraftServer server = server(p);
        boolean gone = online(server, s.from) == null || online(server, s.to) == null;
        boolean expired = !s.open && System.currentTimeMillis() - s.created > INVITE_MS;
        if (gone || expired) {
            end(server, s, gone ? "The trade was cancelled: the other player left." : "The trade request expired.");
            return null;
        }
        return s;
    }

    private static void end(MinecraftServer server, Session s, String msg) {
        BY_PLAYER.remove(s.from, s);
        BY_PLAYER.remove(s.to, s);
        for (UUID id : new UUID[]{s.from, s.to}) {
            ServerPlayer p = online(server, id);
            if (p == null) continue;
            if (msg != null) tell(p, msg, ChatFormatting.YELLOW);
            PartySync.sync(p);
        }
    }

    private static void syncBoth(MinecraftServer server, Session s) {
        ServerPlayer a = online(server, s.from), b = online(server, s.to);
        if (a != null) PartySync.sync(a);
        if (b != null) PartySync.sync(b);
    }

    // ------------------------------------------------------------------ actions from the screens

    public static void handle(ServerPlayer p, JsonObject o) {
        String kind = o.get("kind").getAsString();
        switch (kind) {
            case "serantis_trade_request" -> request(p, o.has("target") ? o.get("target").getAsString() : "");
            case "serantis_trade_answer" -> answer(p, o.has("accept") && o.get("accept").getAsBoolean());
            case "serantis_trade_pick" -> pick(p, o.has("slot") ? o.get("slot").getAsInt() : -1);
            case "serantis_trade_confirm" -> confirm(p);
            case "serantis_trade_cancel" -> cancel(p);
            default -> { }
        }
    }

    static void request(ServerPlayer from, String targetId) {
        MinecraftServer server = server(from);
        UUID id;
        try { id = UUID.fromString(targetId); } catch (IllegalArgumentException e) { return; }
        ServerPlayer to = online(server, id);
        if (to == null || to == from) return;
        if (current(from) != null) { tell(from, "You're already in a trade.", ChatFormatting.RED); return; }
        if (current(to) != null) { tell(from, name(to) + " is busy with another trade.", ChatFormatting.RED); return; }
        if (Battles.of(from) != null || Battles.of(to) != null) { tell(from, "You can't trade during a battle.", ChatFormatting.RED); return; }
        if (Party.get(from).isEmpty()) { tell(from, "You have no creatures in your party to trade.", ChatFormatting.RED); return; }
        Session s = new Session(nextId++, from.getUUID(), to.getUUID());
        BY_PLAYER.put(from.getUUID(), s);
        BY_PLAYER.put(to.getUUID(), s);
        tell(from, "Trade request sent to " + name(to) + ".", ChatFormatting.GREEN);
        tell(to, name(from) + " wants to trade creatures with you.", ChatFormatting.GREEN);
        syncBoth(server, s);
    }

    static void answer(ServerPlayer p, boolean accept) {
        Session s = current(p);
        if (s == null || s.open || s.isFrom(p.getUUID())) return;
        MinecraftServer server = server(p);
        ServerPlayer from = online(server, s.from);
        if (!accept) { end(server, s, null); tell(from, name(p) + " declined your trade.", ChatFormatting.YELLOW); tell(p, "You declined the trade.", ChatFormatting.YELLOW); return; }
        if (Party.get(p).isEmpty()) { end(server, s, name(p) + " has no creatures in their party to trade."); return; }
        s.open = true;
        tell(from, name(p) + " accepted. Pick a creature to offer.", ChatFormatting.GREEN);
        syncBoth(server, s);
    }

    static void pick(ServerPlayer p, int slot) {
        Session s = current(p);
        if (s == null || !s.open) return;
        List<CaughtCreature> party = Party.get(p);
        if (slot < 0 || slot >= party.size()) return;
        if (s.isFrom(p.getUUID())) { s.fromSlot = slot; s.fromPick = party.get(slot); }
        else { s.toSlot = slot; s.toPick = party.get(slot); }
        s.fromOk = s.toOk = false;                    // a changed offer needs both to confirm again
        syncBoth(server(p), s);
    }

    static void confirm(ServerPlayer p) {
        Session s = current(p);
        if (s == null || !s.open || s.fromPick == null || s.toPick == null) return;
        if (s.isFrom(p.getUUID())) s.fromOk = true; else s.toOk = true;
        MinecraftServer server = server(p);
        if (s.fromOk && s.toOk) swap(server, s);
        else syncBoth(server, s);
    }

    static void cancel(ServerPlayer p) {
        Session s = BY_PLAYER.get(p.getUUID());
        if (s == null) return;
        MinecraftServer server = server(p);
        end(server, s, null);
        tell(p, "You cancelled the trade.", ChatFormatting.YELLOW);
        tell(online(server, s.partner(p.getUUID())), name(p) + " cancelled the trade.", ChatFormatting.YELLOW);
    }

    private static void swap(MinecraftServer server, Session s) {
        ServerPlayer a = online(server, s.from), b = online(server, s.to);
        if (a == null || b == null) { end(server, s, "The trade was cancelled."); return; }
        if (Battles.of(a) != null || Battles.of(b) != null) { end(server, s, "The trade was cancelled: someone is in a battle."); return; }
        List<CaughtCreature> pa = Party.get(a), pb = Party.get(b);
        // Both creatures must still be where they were picked (not sent out, boxed or released since).
        if (s.fromSlot >= pa.size() || pa.get(s.fromSlot) != s.fromPick || s.toSlot >= pb.size() || pb.get(s.toSlot) != s.toPick) {
            s.fromOk = s.toOk = false;
            s.fromPick = s.toPick = null;
            s.fromSlot = s.toSlot = -1;
            tell(a, "A creature in the trade moved. Pick again.", ChatFormatting.RED);
            tell(b, "A creature in the trade moved. Pick again.", ChatFormatting.RED);
            syncBoth(server, s);
            return;
        }
        CaughtCreature ca = s.fromPick, cb = s.toPick;
        Party.set(a, s.fromSlot, cb);
        Party.set(b, s.toSlot, ca);
        TypeMastery.gained(a, cb.influence());
        TypeMastery.gained(b, ca.influence());
        BY_PLAYER.remove(s.from, s);
        BY_PLAYER.remove(s.to, s);
        tell(a, "Trade complete! You traded " + label(ca) + " for " + label(cb) + ".", ChatFormatting.GREEN);
        tell(b, "Trade complete! You traded " + label(cb) + " for " + label(ca) + ".", ChatFormatting.GREEN);
        PartySync.sync(a);
        PartySync.sync(b);
    }

    private static String label(CaughtCreature c) {
        String nick = ((MovesHolder) (Object) c).serantis$nickname();
        String species = dev.ahrivaea.creature.Species.get(c.species()).map(sp -> sp.nameFor(c.influence())).orElse(c.species());
        return (nick != null && !nick.isEmpty() ? nick + " the " + species : species) + " (Lv " + c.level() + ")";
    }

    // ------------------------------------------------------------------ what the screens see

    /** Adds "serantis_trade" to a player's party state while they're in a trade. */
    public static void describe(ServerPlayer p, JsonObject state) {
        Session s = current(p);
        if (s == null) return;
        MinecraftServer server = server(p);
        boolean me = s.isFrom(p.getUUID());
        ServerPlayer partner = online(server, s.partner(p.getUUID()));
        JsonObject t = new JsonObject();
        t.addProperty("id", s.id);
        t.addProperty("partner", name(partner));
        t.addProperty("stage", s.open ? "open" : me ? "waiting" : "invite");
        if (!s.open) t.addProperty("secondsLeft", Math.max(0, (INVITE_MS - (System.currentTimeMillis() - s.created)) / 1000));
        t.addProperty("mySlot", me ? s.fromSlot : s.toSlot);
        t.addProperty("myOk", me ? s.fromOk : s.toOk);
        t.addProperty("theirOk", me ? s.toOk : s.fromOk);
        CaughtCreature theirs = me ? s.toPick : s.fromPick;
        if (theirs != null) t.add("theirs", PartySync.describe(theirs));
        state.add("serantis_trade", t);
    }
}
