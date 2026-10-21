package dev.serantis.battle.moves;

import dev.ahrivaea.battle.BattleMove;

import java.util.List;

/** Added to the creatures mod's battle Unit. */
public interface UnitAccess {
    UnitState serantis$state();
    void serantis$setMoves(List<BattleMove> moves);
    /** Replaces the unit's stats (temperament), keeping its health at the same share of the new max. */
    void serantis$setStats(dev.ahrivaea.creature.Stats stats);
    void serantis$setName(String name);
    /** Replaces the stats only (the player's, when their gear changes; their HP comes from their hearts). */
    void serantis$replaceStats(dev.ahrivaea.creature.Stats stats);
}
