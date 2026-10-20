package dev.serantis.battle.moves;

import dev.ahrivaea.battle.BattleMove;

import java.util.List;

/** Added to the creatures mod's battle Unit. */
public interface UnitAccess {
    UnitState serantis$state();
    void serantis$setMoves(List<BattleMove> moves);
}
