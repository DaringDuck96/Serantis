package dev.serantis.battle;

import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * The False Hydra is summoned exactly like the Wither: a T of four soul sand or soul soil blocks with three wither
 * skeleton skulls on top. The blocks vanish and the False Hydra (Legendary, Arcane, Level 20) appears in place of
 * the Wither, as an ordinary wild creature to battle and recruit.
 */
public final class FalseHydra {
    public static final String SPECIES = "false_hydra";

    private record Pending(ServerLevel level, double x, double y, double z) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    private FalseHydra() {}

    public static boolean is(Entity e) {
        return e instanceof CreatureEntity c && SPECIES.equals(c.getSpeciesId());
    }

    /** A Wither was summoned: the False Hydra comes instead (spawned on the next tick). */
    public static void queue(ServerLevel level, double x, double y, double z) {
        synchronized (PENDING) { PENDING.add(new Pending(level, x, y, z)); }
    }

    /** Every server tick. */
    public static void tick() {
        List<Pending> todo;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return;
            todo = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        Species sp = Species.get(SPECIES).orElse(null);
        if (sp == null) return;
        for (Pending p : todo)
            Ahrivaea.spawn(p.level(), sp, sp.formOrDefault(Influence.ARCANE), 20, -1f, p.x(), p.y(), p.z());
    }
}
