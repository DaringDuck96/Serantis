package dev.serantis.battle;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.Unit;
import dev.ahrivaea.creature.CreatureEntity;
import net.minecraft.world.entity.Entity;

/**
 * Running from a battle: the wild creature you ran from is gone (it doesn't wait for you, or come back to the
 * world as it would after a loss). Villagers and Creature Masters, raid goblins and the bosses (the Harbinger of
 * Calamity, the False Hydra, the End dragons) stay, as before.
 */
public final class Fled {
    private Fled() {}

    /** Does a creature of this species stay in the world when you run from it? */
    public static boolean stays(String species) {
        return species == null || dev.ahrivaea.villager.Villagers.SPECIES.equals(species) || Harbinger.SPECIES.equals(species)
                || FalseHydra.SPECIES.equals(species) || Riddles.isDragon(species);
    }

    public static boolean stays(Entity e) {
        return !(e instanceof CreatureEntity c) || c.isTame() || stays(c.getSpeciesId()) || Masters.isMaster(c)
                || GoblinRaids.isRaider(c) || BattleHold.isHeld(c);
    }

    /** Battles.cleanUp after a fled battle fought where it started: the enemies still standing leave. */
    public static void cleanUp(Battle b) {
        if (b == null || b.outcome != Battle.Outcome.FLED) return;
        for (Unit u : b.units) {
            if (u.isPlayer || u.side != Unit.Side.ENEMY || u.entity == null) continue;
            Entity e = b.level.getEntity(u.entity);
            if (e != null && e.isAlive() && !stays(e)) e.discard();
        }
    }
}
