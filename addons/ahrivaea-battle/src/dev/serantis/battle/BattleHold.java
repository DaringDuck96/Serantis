package dev.serantis.battle;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.creature.CreatureEntity;
import net.minecraft.server.level.ServerLevel;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Villagers (Creature Masters included) aren't thrown away when their battle moves to the arena: the creatures
 * mod would discard them and, if you lose or flee, spawn a plain look-alike back without its job, home or
 * Master status. Instead the real one waits, hidden and still, until the battle is decided: beaten, it goes
 * (as before); otherwise it simply comes back as it was.
 */
public final class BattleHold {
    private static final ThreadLocal<CreatureEntity> STARTING = new ThreadLocal<>();
    /** The wild creature a battle started from (the arena fights a copy of it). */
    private static final ThreadLocal<CreatureEntity> SOURCE = new ThreadLocal<>();

    /** Battles.start is about to throw the wild creature away (it fights a copy in the arena): remember it. */
    public static void source(CreatureEntity wild) { SOURCE.set(wild); }
    private static final Map<Battle, Object[]> HELD = Collections.synchronizedMap(new WeakHashMap<>());   // {ServerLevel, UUID}
    private static final Set<UUID> HIDDEN = ConcurrentHashMap.newKeySet();

    private BattleHold() {}

    public static boolean wantsHold(CreatureEntity e) {
        return e != null && dev.ahrivaea.villager.Villagers.SPECIES.equals(e.getSpeciesId()) && !GoblinRaids.isRaider(e);
    }

    public static boolean isHeld(net.minecraft.world.entity.Entity e) { return e != null && HIDDEN.contains(e.getUUID()); }

    /** Battles.start wants to discard the wild villager it moved into the arena: hide it instead. */
    public static void hold(CreatureEntity e) {
        e.setInvisible(true);
        e.setNoAi(true);
        e.setSilent(true);
        e.setDeltaMovement(0, 0, 0);
        HIDDEN.add(e.getUUID());
        STARTING.set(e);
    }

    /** Battles.start returned: tie the hidden villager to its battle. */
    public static void started(Battle b) {
        CreatureEntity src = SOURCE.get();
        SOURCE.remove();
        if (b != null && src != null) copyToLead(b, src);
        CreatureEntity e = STARTING.get();
        STARTING.remove();
        if (e == null) return;
        if (b == null) { release(e); return; }
        HELD.put(b, new Object[]{e.level(), e.getUUID()});
    }

    /** The arena copy gets what the wild one carried: its moves, temperament, apples, name, taught moves and job. */
    private static void copyToLead(Battle b, CreatureEntity src) {
        var lead = ((dev.serantis.battle.mixin.BattleRaidAccess) (Object) b).serantis$lead();
        if (lead == null || lead.entity == null || lead.entity.equals(src.getUUID())) return;
        if (!(b.level.getEntity(lead.entity) instanceof CreatureEntity copy)) return;
        var from = (dev.serantis.battle.moves.MovesHolder) (Object) src;
        var to = (dev.serantis.battle.moves.MovesHolder) (Object) copy;
        if (from.serantis$moves() != null) to.serantis$setMoves(from.serantis$moves());
        if (from.serantis$temperament() != null) to.serantis$setTemperament(from.serantis$temperament());
        if (from.serantis$apples() != null) to.serantis$setApples(from.serantis$apples());
        if (from.serantis$nickname() != null) to.serantis$setNickname(from.serantis$nickname());
        if (from.serantis$tutor() != null) to.serantis$setTutor(from.serantis$tutor());
        var job = Villages.jobOf(src);
        if (job != null) Villages.setJob(copy, job);
    }

    /** The battle is over (Battles.cleanUp): a beaten villager goes; otherwise it's back, just as it was. */
    public static void ended(Battle b) {
        Object[] h = HELD.get(b);
        if (h == null) return;
        var e = ((ServerLevel) h[0]).getEntity((UUID) h[1]);
        if (e instanceof CreatureEntity c) {
            if (b.outcome == Battle.Outcome.WON) { HIDDEN.remove(c.getUUID()); c.discard(); }
            else release(c);
        } else HIDDEN.remove((UUID) h[1]);
    }

    /** Battles.goHome would spawn a look-alike back for a lost or fled battle: not when the real one was kept. */
    public static boolean keptFor(Battle b) {
        return b != null && HELD.remove(b) != null;
    }

    private static void release(CreatureEntity c) {
        HIDDEN.remove(c.getUUID());
        c.setInvisible(false);
        c.setNoAi(false);
        c.setSilent(false);
    }

    /** Loaded from a save made while it was held (the server stopped mid-battle): let it go. */
    public static void loadedHeld(CreatureEntity c) { release(c); }
}
