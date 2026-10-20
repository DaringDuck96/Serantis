package dev.serantis.battle.moves;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** How many tiles per side each creature form covers in battle (serantis_battle/footprints.tsv). */
public final class Footprints {
    private static Map<String, Integer> table;

    private Footprints() {}

    public static int tiles(String species, String influence) {
        if (species == null) return 1;
        if (table == null) load();
        Integer t = table.get(species + "/" + influence);
        if (t == null) t = table.get(species + "/*");
        return t == null ? 1 : Math.max(1, Math.min(4, t));
    }

    private static synchronized void load() {
        if (table != null) return;
        Map<String, Integer> t = new HashMap<>();
        try (InputStream in = Footprints.class.getResourceAsStream("/serantis_battle/footprints.tsv")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] p = line.split("\t");
                    if (p.length < 3) continue;
                    int n = Integer.parseInt(p[2].trim());
                    t.put(p[0] + "/" + p[1], n);
                    t.merge(p[0] + "/*", n, Math::max);
                }
            }
        } catch (Exception e) {
            // no footprints: everyone is 1x1
        }
        table = t;
    }
}
