package dev.serantis.battle.moves;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.BattleMove;
import dev.ahrivaea.battle.DamageCalc;
import dev.ahrivaea.battle.Unit;

import java.util.List;
import java.util.Map;

/** Added to the creatures mod's Battle: its private helpers, plus state the move engine keeps per battle. */
public interface BattleAccess {
    void serantis$strike(Unit attacker, Unit target, BattleMove move);
    void serantis$attack(Unit attacker, Unit target, BattleMove move, boolean counter);
    void serantis$knockOut(Unit unit);
    void serantis$say(String line);
    DamageCalc.Result serantis$preview(Unit attacker, Unit target, BattleMove move);
    boolean serantis$checkEnd();
    List<Unit> serantis$enemyQueue();
    Battle.Phase serantis$lastPhase();
    void serantis$setLastPhase(Battle.Phase phase);
    /** Moves of knocked-out units, keyed by their entry in the battle's defeated list, for recruiting. */
    Map<Object, String> serantis$defeatedMoves();
}
