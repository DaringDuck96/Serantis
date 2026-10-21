package dev.serantis.battle.client;

import java.util.Map;

/**
 * Which villager model each profession wears (assets/ahrivaea/geo/creature/villager/<look>.geo.json, with
 * skins in textures/entity/creature/villager/). Professions without a model of their own here keep the
 * creatures mod's villager with its vanilla outfit.
 */
public final class VillagerLooks {
    private static final Map<String, String> LOOKS = Map.ofEntries(
            // the creatures mod's professions, by the vanilla outfit they wear
            Map.entry("farmer", "farmer"), Map.entry("fisherman", "fisherman"), Map.entry("hunter", "fletcher"),
            Map.entry("librarian", "librarian"), Map.entry("explorer", "cartographer"), Map.entry("smith", "armorer"),
            Map.entry("leatherworker", "leatherworker"), Map.entry("mason", "mason"), Map.entry("jeweler", "nitwit"),
            Map.entry("voidwalker", "cleric"), Map.entry("gardener", "shepherd"), Map.entry("weaver", "shepherd"),
            Map.entry("netherwright", "weaponsmith"), Map.entry("carpenter", "toolsmith"), Map.entry("engineer", "toolsmith"),
            Map.entry("cook", "butcher"),
            // Serantis's own
            Map.entry("alchemist", "alchemist"), Map.entry("bard", "bard"), Map.entry("innkeeper", "innkeeper"),
            Map.entry("noble", "noble"), Map.entry("merchant", "merchant"), Map.entry("move_tutor", "move_tutor"),
            Map.entry("town_guard", "town_guard"), Map.entry("mysterious_figure", "mysterious_figure"), Map.entry("acolyte_of_the_last", "acolyte_of_the_last"), Map.entry("black_market", "black_market_merchant"),
            Map.entry("wandering_trader", "wandering_trader"));

    private VillagerLooks() {}

    /** The model for this profession (null or empty: no profession), or null to keep the default look. */
    public static String look(String profession) {
        if (profession == null || profession.isEmpty()) return "villager";
        if (profession.startsWith("creature_master:")) return "master_" + profession.substring("creature_master:".length());
        return LOOKS.get(profession);
    }
}
