package dev.serantis.towers;

import java.util.Map;

/**
 * New goals for towers, by tower name. The first ring of strongholds (the three closest to spawn) is
 * where a player gets their first base, so those are cheap. Every other tower keeps its own goal.
 */
public final class TowerCosts {
    public static final Map<String, Integer> GOALS = Map.of(
            "The Ember Spire", 64,        // was 1000 coal
            "The Timber Watch", 64,       // was 1500 logs
            "The Quarry Keep", 128);      // was 5000 cobblestone

    private TowerCosts() {}

    public static int goal(String name, int original) {
        Integer g = name == null ? null : GOALS.get(name);
        return g == null ? original : g;
    }
}
