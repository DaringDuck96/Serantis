package dev.serantis.battle;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.Unit;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.party.PartySync;
import dev.serantis.battle.moves.MovesHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * A creature of yours that drops to 0 HP in battle is knocked out for 5 minutes: it shows 0 HP, nothing heals it
 * (food, potions, inns, resting) and it sits out battles. After 5 minutes it's back on its feet at 1 HP, and food
 * or potions can heal it again. (Its stored health is 1 all along, so nothing else treats it as gone.)
 */
public final class Knockout {
    public static final long MILLIS = 5 * 60 * 1000L;

    private Knockout() {}

    public static boolean is(Object creature) {
        return creature instanceof MovesHolder h && h.serantis$koUntil() > System.currentTimeMillis();
    }

    /** Seconds left, or 0. */
    public static long secondsLeft(Object creature) {
        if (!(creature instanceof MovesHolder h)) return 0;
        return Math.max(0, (h.serantis$koUntil() - System.currentTimeMillis() + 999) / 1000);
    }

    /** "4:32": the time left. */
    public static String clock(Object creature) {
        long s = secondsLeft(creature);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static void knock(Object creature) {
        if (creature instanceof MovesHolder h) h.serantis$setKoUntil(System.currentTimeMillis() + MILLIS);
    }

    private static String name(CaughtCreature c) {
        String nick = ((MovesHolder) (Object) c).serantis$nickname();
        if (nick != null && !nick.isEmpty()) return nick;
        return dev.ahrivaea.creature.Species.get(c.species()).map(s -> s.nameFor(c.influence())).orElse("Your creature");
    }

    /** A battle's results were saved to the party: the player's creatures that fell are knocked out. */
    public static void afterBattle(Battle b, ServerPlayer p) {
        if (p == null || dev.serantis.battle.coop.CoopBattles.isSpar(b)) return;     // a friendly spar knocks no one out
        List<CaughtCreature> party = Party.get(p);
        var out = ((dev.serantis.battle.mixin.BattleRaidAccess) (Object) b).serantis$outCreatures();
        boolean any = false;
        for (Unit u : b.units) {
            if (u.side != Unit.Side.PLAYER || u.isPlayer || u.hp > 0) continue;
            CaughtCreature c = null;
            if (u.partySlot >= 0 && u.partySlot < party.size()) c = party.get(u.partySlot);
            int o = -2 - u.partySlot;
            if (c == null && out != null && o >= 0 && o < out.size()) c = out.get(o);
            if (c == null) continue;
            knock(c);
            if (u.partySlot >= 0) Party.set(p, u.partySlot, c);
            p.sendSystemMessage(Component.literal(name(c) + " was knocked out. It can't be healed or fight for 5 minutes, then it's back on its feet at 1 HP.")
                    .withStyle(ChatFormatting.GRAY));
            any = true;
        }
        if (any) PartySync.sync(p);
    }

    /** A co-op ally's creature that fell (saved by CoopBattles). */
    public static CaughtCreature knockedOut(CaughtCreature c, ServerPlayer p) {
        knock(c);
        p.sendSystemMessage(Component.literal(name(c) + " was knocked out. It can't be healed or fight for 5 minutes, then it's back on its feet at 1 HP.")
                .withStyle(ChatFormatting.GRAY));
        return c;
    }

    /** Battles.start: the party creatures that can fight. Knocked-out ones are moved to the back of the party first. */
    public static List<CaughtCreature> fighters(ServerPlayer p) {
        List<CaughtCreature> party = Party.get(p);
        List<CaughtCreature> up = new ArrayList<>(), down = new ArrayList<>();
        for (CaughtCreature c : party) (is(c) ? down : up).add(c);
        if (!down.isEmpty()) {
            List<CaughtCreature> order = new ArrayList<>(up);
            order.addAll(down);
            boolean moved = false;
            for (int i = 0; i < order.size(); i++) if (party.get(i) != order.get(i)) { Party.set(p, i, order.get(i)); moved = true; }
            if (moved) PartySync.sync(p);
        }
        return up;
    }

    /** Every few seconds: creatures whose 5 minutes are up are back on their feet. */
    public static void tick(ServerPlayer p) {
        List<CaughtCreature> party = Party.get(p);
        boolean any = false;
        for (int i = 0; i < party.size(); i++) {
            CaughtCreature c = party.get(i);
            MovesHolder h = (MovesHolder) (Object) c;
            if (h.serantis$koUntil() == 0 || h.serantis$koUntil() > System.currentTimeMillis()) continue;
            h.serantis$setKoUntil(0);
            Party.set(p, i, c);
            p.sendSystemMessage(Component.literal(name(c) + " is back on its feet (1 HP). Food or a potion will heal it.").withStyle(ChatFormatting.GREEN));
            any = true;
        }
        for (CreatureEntity e : p.level().getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(64), x -> x.isTame() && x.isOwnedBy(p))) {
            MovesHolder h = (MovesHolder) (Object) e;
            if (h.serantis$koUntil() == 0 || h.serantis$koUntil() > System.currentTimeMillis()) continue;
            h.serantis$setKoUntil(0);
            p.sendSystemMessage(Component.literal(e.displayName() + " is back on its feet (1 HP). Food or a potion will heal it.").withStyle(ChatFormatting.GREEN));
            any = true;
        }
        if (any) PartySync.sync(p);
    }
}
