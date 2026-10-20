package dev.serantis.starters;

import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.party.RecruitRules;
import dev.ahrivaea.villager.Villagers;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The creatures offered on the starter screen, read from serantis_starters/starters.txt
 * (one creature id per line, in display order).
 */
public final class StarterList {
    private static List<String> ids;

    private StarterList() {}

    public static synchronized List<String> ids() {
        if (ids == null) {
            List<String> out = new ArrayList<>();
            try (InputStream in = StarterList.class.getResourceAsStream("/serantis_starters/starters.txt")) {
                if (in != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    for (String line; (line = r.readLine()) != null; ) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) out.add(line);
                    }
                }
            } catch (Exception e) {
                // fall through with whatever was read; options() falls back to the mod's own rule if empty
            }
            ids = List.copyOf(out);
        }
        return ids;
    }

    /** The starter options: the listed creatures, or the creatures mod's own rule if none of them are loaded. */
    public static List<Species> options() {
        List<Species> out = new ArrayList<>();
        for (String id : ids()) Species.get(id).ifPresent(out::add);
        if (!out.isEmpty()) return out;
        for (Species s : Species.all()) {
            if (s.forms().containsKey(Influence.NONE)
                    && RecruitRules.rank(RecruitRules.rarity(s, Influence.NONE)) == 0
                    && !Villagers.isVillager(s.id())) {
                out.add(s);
            }
        }
        out.sort(Comparator.comparing(s -> s.nameFor(Influence.NONE)));
        return out;
    }
}
