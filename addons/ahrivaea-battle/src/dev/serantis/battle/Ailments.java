package dev.serantis.battle;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.Unit;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.party.PartySync;
import dev.serantis.battle.moves.MoveEngine;
import dev.serantis.battle.moves.MovesHolder;
import dev.serantis.battle.moves.UnitState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/**
 * Statuses that linger after a battle: annoying, never dangerous.
 *
 * Whatever status a unit on your side still has when a battle ends stays with it for 3 minutes.
 *
 * You (a vanilla effect, so a bucket of milk cures it as usual):
 *   Poison, Bleeding: Hunger.   Burning, Doom: Weakness.   Frozen, Slowed: Slowness.   Immobilized: Slowness II.
 *   Stunned, Asleep: Mining Fatigue.   Confused: Nausea (15 seconds).   Frightened: Darkness (15 seconds).
 *
 * Your creatures (one ailment each, shown on the party screen, "Name - Poisoned"):
 *   Poison, Bleeding, Burning, Doom: it slowly loses HP (1% of its max every 5 seconds), but never below half its max.
 *   Anything else: it's sluggish while it's out with you (slow to follow, slow to ride).
 *   Feeding it a bucket of milk or a honey bottle (right-click it in your inventory) cures it.
 * The same goes for a co-op ally's creatures, and for enemies still standing when you flee or lose (a wild creature
 * keeps it while it roams, and if you recruit it, it comes with it). Nothing carries into the next battle.
 */
public final class Ailments {
    public static final long MILLIS = 3 * 60 * 1000L;
    private static final int TICKS = 3 * 60 * 20, SHORT = 15 * 20;

    private Ailments() {}

    // ------------------------------------------------------------------ what a status becomes

    public static String label(String status) {
        return switch (status) {
            case "poison" -> "Poisoned";
            case "bleeding" -> "Bleeding";
            case "burning" -> "Burned";
            case "doom" -> "Doomed";
            case "freeze" -> "Frozen";
            case "slowed" -> "Slowed";
            case "immobilized" -> "Stiff";
            case "stun" -> "Dazed";
            case "sleep" -> "Drowsy";
            case "confused" -> "Confused";
            case "frightened" -> "Shaken";
            default -> null;
        };
    }

    private static boolean hurts(String status) {
        return switch (status) { case "poison", "bleeding", "burning", "doom" -> true; default -> false; };
    }

    private static MobEffectInstance playerEffect(String status) {
        Holder<MobEffect> e;
        int amp = 0, t = TICKS;
        switch (status) {
            case "poison", "bleeding" -> e = MobEffects.HUNGER;
            case "burning", "doom" -> e = MobEffects.WEAKNESS;
            case "freeze", "slowed" -> e = MobEffects.SLOWNESS;
            case "immobilized" -> { e = MobEffects.SLOWNESS; amp = 1; }
            case "stun", "sleep" -> e = MobEffects.MINING_FATIGUE;
            case "confused" -> { e = MobEffects.NAUSEA; t = SHORT; }
            case "frightened" -> { e = MobEffects.DARKNESS; t = SHORT; }
            default -> { return null; }
        }
        return new MobEffectInstance(e, t, amp);
    }

    /** The status a unit is left with (the one with the most turns to go), or null. */
    private static String leftover(Unit u) {
        UnitState st = MoveEngine.state(u);
        if (st == null) return null;
        UnitState.Effect best = null;
        for (UnitState.Effect e : st.effects)
            if ("status".equals(e.kind) && label(e.key) != null && (best == null || e.turns > best.turns)) best = e;
        return best == null ? null : best.key;
    }

    // ------------------------------------------------------------------ stored on a creature: "status@until"

    public static String status(Object creature) {
        if (!(creature instanceof MovesHolder h) || h.serantis$ailment() == null) return null;
        String a = h.serantis$ailment();
        int at = a.indexOf('@');
        if (at < 0) return null;
        try {
            if (Long.parseLong(a.substring(at + 1)) <= System.currentTimeMillis()) return null;
        } catch (NumberFormatException e) {
            return null;
        }
        return a.substring(0, at);
    }

    private static boolean expired(Object creature) {
        return creature instanceof MovesHolder h && h.serantis$ailment() != null && status(creature) == null;
    }

    private static void set(Object creature, String status) {
        if (creature instanceof MovesHolder h) h.serantis$setAilment(status == null ? null : status + "@" + (System.currentTimeMillis() + MILLIS));
    }

    private static String name(CaughtCreature c) {
        String nick = ((MovesHolder) (Object) c).serantis$nickname();
        if (nick != null && !nick.isEmpty()) return nick;
        return Species.get(c.species()).map(s -> s.nameFor(c.influence())).orElse("Your creature");
    }

    private static String what(String status) {
        return hurts(status) ? "it'll slowly lose some HP (never below half)" : "it'll be sluggish while it's out with you";
    }

    // ------------------------------------------------------------------ after a battle

    /** A battle's results were saved to the owner's party: what's left of the statuses lingers. */
    public static void afterBattle(Battle b, ServerPlayer owner) {
        if (owner == null || dev.serantis.battle.coop.CoopBattles.isSpar(b)) return;   // a friendly spar leaves nothing behind
        for (Unit u : b.units) {                                        // enemies still standing (you fled or lost) keep theirs too
            if (u.side == Unit.Side.PLAYER || u.hp <= 0 || u.entity == null) continue;
            String s = leftover(u);
            if (s == null) continue;
            for (var level : owner.level().getServer().getAllLevels())
                if (level.getEntity(u.entity) instanceof CreatureEntity e) { set(e, s); break; }
        }
        List<CaughtCreature> party = Party.get(owner);
        var out = ((dev.serantis.battle.mixin.BattleRaidAccess) (Object) b).serantis$outCreatures();
        boolean any = false;
        for (Unit u : b.units) {
            if (u.side != Unit.Side.PLAYER || u.hp <= 0) continue;
            String s = leftover(u);
            if (s == null) continue;
            if (u.isPlayer) {
                ServerPlayer p = dev.serantis.battle.coop.CoopBattles.playerOf(b, u);
                MobEffectInstance fx = playerEffect(s);
                if (p != null && fx != null) {
                    p.addEffect(fx);
                    p.sendSystemMessage(Component.literal("You're still " + label(s).toLowerCase() + " after the battle. A bucket of milk will sort you out.")
                            .withStyle(ChatFormatting.GRAY));
                }
                continue;
            }
            UUID ctl = dev.serantis.battle.coop.CoopBattles.controllerId(b, u);
            if (ctl != null && !ctl.equals(owner.getUUID())) continue;      // an ally's creature: saved with their party (CoopBattles, linger())
            CaughtCreature c = null;
            if (u.partySlot >= 0 && u.partySlot < party.size()) c = party.get(u.partySlot);
            int o = -2 - u.partySlot;
            if (c == null && out != null && o >= 0 && o < out.size()) c = out.get(o);
            if (c == null || Knockout.is(c)) continue;
            set(c, s);
            if (u.partySlot >= 0) Party.set(owner, u.partySlot, c);
            else for (CreatureEntity e : owner.level().getEntitiesOfClass(CreatureEntity.class, owner.getBoundingBox().inflate(64),
                    x -> x.isTame() && x.isOwnedBy(owner) && x.getSpeciesId().equals(u.speciesId))) { set(e, s); break; }
            owner.sendSystemMessage(Component.literal(name(c) + " is still " + label(s).toLowerCase() + ": for 3 minutes " + what(s)
                    + ". Feed it milk or a honey bottle to cure it.").withStyle(ChatFormatting.GRAY));
            any = true;
        }
        if (any) PartySync.sync(owner);
    }

    /** A co-op ally's creature saved back to their party (CoopBattles): it keeps what it was left with. */
    public static CaughtCreature linger(CaughtCreature c, Unit u, ServerPlayer p) {
        String s = leftover(u);
        if (s == null || Knockout.is(c)) return c;
        set(c, s);
        p.sendSystemMessage(Component.literal(name(c) + " is still " + label(s).toLowerCase() + ": for 3 minutes " + what(s)
                + ". Feed it milk or a honey bottle to cure it.").withStyle(ChatFormatting.GRAY));
        return c;
    }

    // ------------------------------------------------------------------ over time

    /** Every 5 seconds, per player. */
    public static void tick(ServerPlayer p) {
        List<CaughtCreature> party = Party.get(p);
        boolean sync = false;
        for (int i = 0; i < party.size(); i++) {
            CaughtCreature c = party.get(i);
            if (expired(c)) {
                ((MovesHolder) (Object) c).serantis$setAilment(null);
                Party.set(p, i, c);
                p.sendSystemMessage(Component.literal(name(c) + " is feeling itself again.").withStyle(ChatFormatting.GREEN));
                sync = true;
                continue;
            }
            String s = status(c);
            if (s == null || !hurts(s) || Knockout.is(c)) continue;
            var sp = Species.get(c.species()).orElse(null);
            if (sp == null) continue;
            int max = sp.statsFor(c.influence()).maxHealth(c.level());
            float floor = max / 2f;
            if (c.health() <= floor) continue;
            CaughtCreature next = c.withHealth(Math.max(floor, c.health() - Math.max(1, Math.round(max * 0.01f))));
            Party.set(p, i, next);
            sync = true;
        }
        for (CreatureEntity e : p.level().getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(64),
                x -> ((MovesHolder) (Object) x).serantis$ailment() != null && (!x.isTame() || x.isOwnedBy(p)))) {   // yours, or wild ones (each wild one ticked by every player near it: fine, the floor still holds)
            if (dev.ahrivaea.battle.Battles.inBattle(e)) continue;
            if (expired(e)) {
                ((MovesHolder) (Object) e).serantis$setAilment(null);
                if (e.isTame()) p.sendSystemMessage(Component.literal(e.displayName() + " is feeling itself again.").withStyle(ChatFormatting.GREEN));
                sync = true;
                continue;
            }
            String s = status(e);
            if (s == null || Knockout.is(e)) continue;
            if (hurts(s)) {
                float floor = e.getMaxHealth() / 2f;
                if (e.getHealth() > floor) e.setHealth(Math.max(floor, e.getHealth() - Math.max(1, Math.round(e.getMaxHealth() * 0.01f))));
            } else {
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 7 * 20, 1, false, false));
            }
        }
        if (sync) PartySync.sync(p);
    }

    // ------------------------------------------------------------------ cures

    public static boolean cure(ItemStack s) {
        return s.is(Items.MILK_BUCKET) || s.is(Items.HONEY_BOTTLE);
    }

    /** Cure a creature (a party one or one that's out). Returns the message, or null if it had nothing to cure. */
    public static String cureCreature(Object creature, String name) {
        String s = status(creature);
        if (s == null) return null;
        if (creature instanceof MovesHolder h) h.serantis$setAilment(null);
        if (creature instanceof CreatureEntity e) e.removeEffect(MobEffects.SLOWNESS);
        return name + " is no longer " + label(s).toLowerCase() + ".";
    }

    /** Party screen: " - Poisoned" after the name while an ailment lingers. */
    public static String suffix(Object creature) {
        String s = status(creature);
        return s == null ? "" : " - " + label(s);
    }
}
