package dev.serantis.battle.skills;

import java.util.Set;

/** Added to players: the skill tree nodes they own (saved with the player, kept through death). */
public interface SkillHolder {
    Set<String> serantis$skills();
}
