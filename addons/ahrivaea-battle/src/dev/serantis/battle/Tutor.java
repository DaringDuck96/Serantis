package dev.serantis.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.party.PartySync;
import dev.ahrivaea.villager.VillagerJob;
import dev.serantis.battle.moves.MoveBook;
import dev.serantis.battle.moves.MoveExplain;
import dev.serantis.battle.moves.MoveSpec;
import dev.serantis.battle.moves.MovesHolder;
import dev.serantis.battle.skills.Skills;
import dev.serantis.battle.skills.XpBank;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Move Tutor: a villager profession. Working at your base, it can be talked to (right-click it).
 * Pick a creature in your party and the Tutor offers 3 other moves it could learn, at random. Teaching
 * one (it replaces a move of your choice) costs 25 XP. The offer stays the same until the creature has
 * been taught one of them; talk to the Tutor again after that for 3 new ones.
 */
public final class Tutor {
    public static final String ROLE = "move_tutor";
    public static final int COST = 25;
    public static final int OFFERED = 3;

    private Tutor() {}

    public static boolean isTutor(Entity e) {
        if (!(e instanceof CreatureEntity c)) return false;
        VillagerJob j = Villages.jobOf(c);
        return j != null && ROLE.equals(j.profession());
    }

    /** The player's own Move Tutor, working at their base, within reach. */
    private static CreatureEntity tutor(ServerPlayer p, int entityId) {
        Entity e = p.level().getEntity(entityId);
        if (!(e instanceof CreatureEntity c) || !isTutor(c) || !c.isAlive() || p.distanceToSqr(c) > 10 * 10) return null;
        if (c.brain() == null) return null;                                     // not working at a base
        if (c.isOwnedBy(p)) return c;
        var core = dev.ahrivaea.base.Bases.coreAt((net.minecraft.server.level.ServerLevel) c.level(), c.brain().core());
        return core != null && p.getUUID().equals(core.owner()) ? c : null;
    }

    /** Who's talking to which Tutor, and about which party slot (-1: none picked yet). */
    private record Talk(int entity, int slot, boolean fresh) {}
    private static final Map<UUID, Talk> TALKING = new ConcurrentHashMap<>();

    public static void open(ServerPlayer p, int entityId) {
        Entity e = p.level().getEntity(entityId);
        if (tutor(p, entityId) == null) {
            if (e instanceof CreatureEntity c && isTutor(c))
                tell(p, "The Move Tutor only teaches when it's working at your base.");
            return;
        }
        TALKING.put(p.getUUID(), new Talk(entityId, -1, true));
        PartySync.sync(p);
    }

    public static void close(ServerPlayer p) {
        if (TALKING.remove(p.getUUID()) != null) PartySync.sync(p);
    }

    /** Pick a party creature: the Tutor makes its offer (or repeats the one it made before). */
    public static void pick(ServerPlayer p, int slot) {
        Talk t = TALKING.get(p.getUUID());
        if (t == null || tutor(p, t.entity()) == null) return;
        List<CaughtCreature> party = Party.get(p);
        if (slot < 0 || slot >= party.size()) return;
        MovesHolder h = (MovesHolder) (Object) party.get(slot);
        if (offer(h).isEmpty()) h.serantis$setTutor(MoveBook.encode(roll(party.get(slot), p.getRandom())));
        TALKING.put(p.getUUID(), new Talk(t.entity(), slot, true));
        PartySync.sync(p);
    }

    /** Teach offered move {@code offer} in place of the creature's move {@code replace}. */
    public static void teach(ServerPlayer p, int slot, int offerIndex, int replace) {
        Talk t = TALKING.get(p.getUUID());
        if (t == null || tutor(p, t.entity()) == null || t.slot() != slot || !t.fresh()) return;
        List<CaughtCreature> party = Party.get(p);
        if (slot < 0 || slot >= party.size()) return;
        CaughtCreature c = party.get(slot);
        MovesHolder h = (MovesHolder) (Object) c;
        List<MoveSpec> offer = offer(h), moves = known(c);
        if (offerIndex < 0 || offerIndex >= offer.size() || replace < 0 || replace >= moves.size()) return;
        MoveSpec learn = offer.get(offerIndex), forget = moves.get(replace);
        boolean damaging = learn.damaging();
        for (int i = 0; i < moves.size(); i++) if (i != replace && moves.get(i).damaging()) damaging = true;
        if (!damaging) { tell(p, "It has to keep at least one move that deals damage."); return; }
        int cost = Skills.price(p, COST);
        boolean free = p.getAbilities().instabuild;
        if (!free && XpBank.points(p) < cost) { tell(p, "Teaching a move costs " + cost + " XP."); return; }
        if (!free) XpBank.take(p, cost);
        moves.set(replace, learn);
        h.serantis$setMoves(MoveBook.encode(moves));
        h.serantis$setTutor(null);                       // talk to the Tutor again for a new offer
        TALKING.put(p.getUUID(), new Talk(t.entity(), slot, false));
        tell(p, name(c) + " forgot " + forget.name + " and learned " + learn.name + "! (-" + cost + " XP)");
        PartySync.sync(p);
    }

    /** 3 moves the creature could learn that it doesn't know (its influence's own and universal moves). */
    private static List<MoveSpec> roll(CaughtCreature c, RandomSource random) {
        List<MoveSpec> pool = new ArrayList<>(MoveBook.pool(c.influence()));
        List<MoveSpec> known = known(c);
        pool.removeIf(s -> known.stream().anyMatch(k -> k.id.equals(s.id)));
        List<MoveSpec> out = new ArrayList<>();
        while (out.size() < OFFERED && !pool.isEmpty()) out.add(pool.remove(random.nextInt(pool.size())));
        return out;
    }

    private static List<MoveSpec> known(CaughtCreature c) {
        List<MoveSpec> m = MoveBook.decode(((MovesHolder) (Object) c).serantis$moves());
        return m == null ? new ArrayList<>() : new ArrayList<>(m);
    }

    private static List<MoveSpec> offer(MovesHolder h) {
        String saved = h.serantis$tutor();
        List<MoveSpec> out = new ArrayList<>();
        if (saved == null || saved.isBlank()) return out;
        for (String id : saved.split(",")) {
            MoveSpec s = MoveBook.byId(id.trim());
            if (s != null && s.enabled) out.add(s);
        }
        return out;
    }

    private static String name(CaughtCreature c) {
        String nick = ((MovesHolder) (Object) c).serantis$nickname();
        return nick != null && !nick.isEmpty() ? nick : Species.get(c.species()).map(sp -> sp.nameFor(c.influence())).orElse("Your creature");
    }

    private static JsonArray json(List<MoveSpec> specs) {
        JsonArray a = new JsonArray();
        for (MoveSpec s : specs) {
            JsonObject o = new JsonObject();
            o.addProperty("name", s.name);
            o.addProperty("line", MoveExplain.line(s));
            o.addProperty("explain", MoveExplain.explain(s));
            o.addProperty("damaging", s.damaging());
            a.add(o);
        }
        return a;
    }

    /** "serantis_tutor" in the party state while a player is talking to their Move Tutor. */
    public static void describe(ServerPlayer p, JsonObject state) {
        Talk t = TALKING.get(p.getUUID());
        if (t == null) return;
        if (tutor(p, t.entity()) == null) { TALKING.remove(p.getUUID()); return; }
        JsonObject o = new JsonObject();
        o.addProperty("entity", t.entity());
        o.addProperty("slot", t.slot());
        o.addProperty("cost", Skills.price(p, COST));
        o.addProperty("xp", XpBank.points(p));
        List<CaughtCreature> party = Party.get(p);
        if (t.slot() >= 0 && t.slot() < party.size()) {
            CaughtCreature c = party.get(t.slot());
            o.addProperty("name", name(c));
            o.add("moves", json(known(c)));
            o.add("offer", t.fresh() ? json(offer((MovesHolder) (Object) c)) : new JsonArray());
            o.addProperty("taught", !t.fresh());
        }
        state.add("serantis_tutor", o);
    }

    private static void tell(ServerPlayer p, String msg) {
        p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.YELLOW));
    }
}
