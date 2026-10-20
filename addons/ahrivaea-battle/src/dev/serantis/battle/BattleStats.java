package dev.serantis.battle;

import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Stats;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Serantis battle rules for creatures:
 *  - every creature's stats are its Level 20 stats from serantis_battle/stats.tsv;
 *  - Level 1 is 50% of Level 20, rising in a straight line, and creatures stop at Level 20;
 *  - every wild creature can appear at any level from 1 to 20; how often it appears comes from its rarity.
 */
public final class BattleStats {
    public static final int MAX_LEVEL = 20;

    private static Map<String, Stats> table;

    private BattleStats() {}

    /** Share of a creature's Level 20 stats it has at the given level: 50% at Level 1, 100% at Level 20. */
    public static double levelScale(int level) {
        int l = Math.max(1, Math.min(MAX_LEVEL, level));
        return 0.5 + 0.5 * (l - 1) / (double) (MAX_LEVEL - 1);
    }

    public static int clampLevel(int level) {
        return Math.max(1, Math.min(MAX_LEVEL, level));
    }

    /** Rebuilds the loaded creatures with the table's stats and Level 1-20 spawning. */
    public static Map<String, Species> apply(Map<String, Species> loaded) {
        Map<String, Stats> stats = table();
        Map<String, Species> out = new LinkedHashMap<>();
        for (Map.Entry<String, Species> e : loaded.entrySet()) {
            Species s = e.getValue();
            Map<Influence, Species.Form> forms = new LinkedHashMap<>();
            Stats first = null;
            for (Map.Entry<Influence, Species.Form> fe : s.forms().entrySet()) {
                Species.Form f = fe.getValue();
                Stats st = stats.get(s.id() + "\t" + fe.getKey().getSerializedName());
                Optional<Stats> formStats = st != null ? Optional.of(st) : f.stats();
                if (first == null && st != null) first = st;
                forms.put(fe.getKey(), new Species.Form(f.name(), formStats, f.rarity(),
                        f.spawn().map(BattleStats::anyLevel), f.drops(), f.jobs(), f.size()));
            }
            Stats none = stats.get(s.id() + "\tnone");
            Stats base = none != null ? none : first != null ? first : s.stats();
            out.put(e.getKey(), new Species(s.name(), base, forms, s.size(), s.movement(),
                    s.spawn().map(BattleStats::anyLevel), s.id()));
        }
        return Map.copyOf(out);
    }

    private static Species.Spawn anyLevel(Species.Spawn sp) {
        return new Species.Spawn(sp.biomes(), 1, MAX_LEVEL, sp.weight());
    }

    private static synchronized Map<String, Stats> table() {
        if (table == null) {
            Map<String, Stats> out = new HashMap<>();
            try (InputStream in = BattleStats.class.getResourceAsStream("/serantis_battle/stats.tsv")) {
                if (in != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    for (String line; (line = r.readLine()) != null; ) {
                        if (line.isBlank() || line.startsWith("#")) continue;
                        String[] p = line.trim().split("\t");
                        if (p.length != 9) continue;
                        int[] v = new int[7];
                        for (int i = 0; i < 7; i++) v[i] = Integer.parseInt(p[i + 2].trim());
                        out.put(p[0] + "\t" + p[1], new Stats(v[0], v[1], v[2], v[3], v[4], v[5], v[6]));
                    }
                }
            } catch (Exception e) {
                // an unreadable table leaves every creature on its own stats
            }
            table = out;
        }
        return table;
    }
}
