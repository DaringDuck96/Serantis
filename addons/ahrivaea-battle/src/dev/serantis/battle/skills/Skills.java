package dev.serantis.battle.skills;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.character.PlayerCharacter;
import dev.ahrivaea.creature.Stats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The player skill tree. Every player starts with 40 in every stat. Nodes are bought at your own
 * Base Core (Level 1 or higher) with XP; each node needs the one before it in its branch, a
 * capstone also needs a node from a neighbouring branch, and node N's base level requirement
 * grows with depth. Every node bought makes the next one 15% dearer, whichever branch it's in.
 */
public final class Skills {
    public static final int START_STAT = 40;
    public static final double BASE_COST = 150, GROWTH = 1.15;

    public record Node(String id, String branch, int index, int tier, String name, String desc, List<String> requires,
                       int attack, int defense, int magic, int speed, int luck, int hearts, int walk, int tiles, int mining) {}

    public static final Map<String, Node> NODES = new LinkedHashMap<>();
    public static final List<String> BRANCHES = List.of("Vitality", "Guard", "Might", "Arcana", "Fortune", "Swiftness", "Industry");

    private static void node(String id, String branch, int index, int tier, String name, String desc, List<String> requires,
                             int atk, int def, int mag, int spd, int luck, int hearts, int walk, int tiles, int mining) {
        NODES.put(id, new Node(id, branch, index, tier, name, desc, requires, atk, def, mag, spd, luck, hearts, walk, tiles, mining));
    }

    private static List<String> after(String prev, String... more) {
        List<String> r = new ArrayList<>();
        if (prev != null) r.add(prev);
        Collections.addAll(r, more);
        return r;
    }

    static {
        //   id            branch       #  tier name                desc                                              requires                      atk def mag spd lck hrt walk tile mine
        node("might_1", "Might", 1, 1, "Strength I", "+3 Attack", after(null), 3, 0, 0, 0, 0, 0, 0, 0, 0);
        node("might_2", "Might", 2, 1, "Strength II", "+3 Attack", after("might_1"), 3, 0, 0, 0, 0, 0, 0, 0, 0);
        node("might_3", "Might", 3, 2, "Strength III", "+4 Attack", after("might_2"), 4, 0, 0, 0, 0, 0, 0, 0, 0);
        node("might_4", "Might", 4, 3, "Heavy Hitter", "Your melee hits deal 5% more damage", after("might_3"), 0, 0, 0, 0, 0, 0, 0, 0, 0);
        node("might_5", "Might", 5, 4, "Strength IV", "+5 Attack", after("might_4"), 5, 0, 0, 0, 0, 0, 0, 0, 0);
        node("might_6", "Might", 6, 5, "Overpower", "Your critical hits deal x2.25 instead of x2", after("might_5", "guard_4"), 0, 0, 0, 0, 0, 0, 0, 0, 0);

        node("guard_1", "Guard", 1, 1, "Toughness I", "+3 Defense", after(null), 0, 3, 0, 0, 0, 0, 0, 0, 0);
        node("guard_2", "Guard", 2, 1, "Toughness II", "+3 Defense", after("guard_1"), 0, 3, 0, 0, 0, 0, 0, 0, 0);
        node("guard_3", "Guard", 3, 2, "Toughness III", "+4 Defense", after("guard_2"), 0, 4, 0, 0, 0, 0, 0, 0, 0);
        node("guard_4", "Guard", 4, 3, "Toughness IV", "+4 Defense", after("guard_3"), 0, 4, 0, 0, 0, 0, 0, 0, 0);
        node("guard_5", "Guard", 5, 4, "Steady", "Ranged hits on you deal 5% less damage", after("guard_4"), 0, 0, 0, 0, 0, 0, 0, 0, 0);
        node("guard_6", "Guard", 6, 5, "Bulwark", "+6 Defense", after("guard_5", "might_4"), 0, 6, 0, 0, 0, 0, 0, 0, 0);

        node("vital_1", "Vitality", 1, 1, "Vigor I", "+4 HP", after(null), 0, 0, 0, 0, 0, 0, 0, 0, 0);
        node("vital_2", "Vitality", 2, 1, "Hearty I", "+1 heart", after("vital_1"), 0, 0, 0, 0, 0, 1, 0, 0, 0);
        node("vital_3", "Vitality", 3, 2, "Vigor II", "+5 HP", after("vital_2"), 0, 0, 0, 0, 0, 0, 0, 0, 0);
        node("vital_4", "Vitality", 4, 3, "Hearty II", "+1 heart", after("vital_3"), 0, 0, 0, 0, 0, 1, 0, 0, 0);
        node("vital_5", "Vitality", 5, 4, "Vigor III", "+6 HP", after("vital_4"), 0, 0, 0, 0, 0, 0, 0, 0, 0);
        node("vital_6", "Vitality", 6, 5, "Juggernaut", "+5 HP and +2 hearts", after("vital_5", "guard_4"), 0, 0, 0, 0, 0, 2, 0, 0, 0);

        node("arcana_1", "Arcana", 1, 1, "Insight I", "+3 Magic", after(null), 0, 0, 3, 0, 0, 0, 0, 0, 0);
        node("arcana_2", "Arcana", 2, 1, "Insight II", "+3 Magic", after("arcana_1"), 0, 0, 3, 0, 0, 0, 0, 0, 0);
        node("arcana_3", "Arcana", 3, 2, "Spellpower", "Your magic attacks deal 5% more damage", after("arcana_2"), 0, 0, 0, 0, 0, 0, 0, 0, 0);
        node("arcana_4", "Arcana", 4, 3, "Insight III", "+4 Magic", after("arcana_3"), 0, 0, 4, 0, 0, 0, 0, 0, 0);
        node("arcana_5", "Arcana", 5, 4, "Insight IV", "+5 Magic", after("arcana_4"), 0, 0, 5, 0, 0, 0, 0, 0, 0);
        node("arcana_6", "Arcana", 6, 5, "Attuned", "Anvils cost 25% less XP", after("arcana_5", "fortune_4"), 0, 0, 0, 0, 0, 0, 0, 0, 0);

        node("swift_1", "Swiftness", 1, 1, "Quickness I", "+3 Speed", after(null), 0, 0, 0, 3, 0, 0, 0, 0, 0);
        node("swift_2", "Swiftness", 2, 1, "Stride I", "+5% walking speed", after("swift_1"), 0, 0, 0, 0, 0, 0, 5, 0, 0);
        node("swift_3", "Swiftness", 3, 2, "Footwork I", "+1 tile of movement in battle", after("swift_2"), 0, 0, 0, 0, 0, 0, 0, 1, 0);
        node("swift_4", "Swiftness", 4, 3, "Stride II", "+5% walking speed", after("swift_3"), 0, 0, 0, 0, 0, 0, 5, 0, 0);
        node("swift_5", "Swiftness", 5, 4, "Footwork II", "+1 tile of movement in battle", after("swift_4"), 0, 0, 0, 0, 0, 0, 0, 1, 0);
        node("swift_6", "Swiftness", 6, 5, "Fleet", "+10% walking speed and +4 Speed", after("swift_5", "industry_4"), 0, 0, 0, 4, 0, 0, 10, 0, 0);

        node("fortune_1", "Fortune", 1, 1, "Luck I", "+3 Luck", after(null), 0, 0, 0, 0, 3, 0, 0, 0, 0);
        node("fortune_2", "Fortune", 2, 1, "Scavenger", "5% chance mobs you kill drop their loot twice", after("fortune_1"), 0, 0, 0, 0, 0, 0, 0, 0, 0);
        node("fortune_3", "Fortune", 3, 2, "Luck II", "+4 Luck", after("fortune_2"), 0, 0, 0, 0, 4, 0, 0, 0, 0);
        node("fortune_4", "Fortune", 4, 3, "Spoils", "5% chance each creature you defeat in battle drops twice", after("fortune_3"), 0, 0, 0, 0, 0, 0, 0, 0, 0);
        node("fortune_5", "Fortune", 5, 4, "Luck III", "+5 Luck", after("fortune_4"), 0, 0, 0, 0, 5, 0, 0, 0, 0);
        node("fortune_6", "Fortune", 6, 5, "Treasure Sense", "Rare creature drops (under 25%) are twice as likely", after("fortune_5", "arcana_4"), 0, 0, 0, 0, 0, 0, 0, 0, 0);

        node("industry_1", "Industry", 1, 1, "Labor I", "+8% mining and chopping speed", after(null), 0, 0, 0, 0, 0, 0, 0, 0, 8);
        node("industry_2", "Industry", 2, 1, "Labor II", "+8% mining and chopping speed", after("industry_1"), 0, 0, 0, 0, 0, 0, 0, 0, 8);
        node("industry_3", "Industry", 3, 2, "Prospector", "Ores and other XP blocks give +1 XP", after("industry_2"), 0, 0, 0, 0, 0, 0, 0, 0, 0);
        node("industry_4", "Industry", 4, 3, "Labor III", "+8% mining and chopping speed", after("industry_3"), 0, 0, 0, 0, 0, 0, 0, 0, 8);
        node("industry_5", "Industry", 5, 4, "Labor IV", "+8% mining and chopping speed", after("industry_4"), 0, 0, 0, 0, 0, 0, 0, 0, 8);
        node("industry_6", "Industry", 6, 5, "Tireless", "+10% mining speed; breaking blocks uses half the hunger", after("industry_5", "swift_4"), 0, 0, 0, 0, 0, 0, 0, 0, 10);
    }

    /** The last answer each player got when trying to learn a skill (shown on the tree screen). */
    private static final Map<java.util.UUID, String> LAST = new java.util.concurrent.ConcurrentHashMap<>();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("serantis_skills");

    private Skills() {}

    public static void answer(ServerPlayer p, String message) {
        LAST.put(p.getUUID(), message);
        LOG.info("{} skill tree: {}", p.getPlainTextName(), message);
    }

    public static Set<String> owned(Player p) { return ((SkillHolder) (Object) p).serantis$skills(); }
    public static boolean has(Player p, String id) { return p != null && owned(p).contains(id); }

    /** XP the next node costs, given how many are already owned (rounded to 5). */
    public static int cost(int owned) {
        return (int) (Math.round(BASE_COST * Math.pow(GROWTH, owned) / 5.0) * 5);
    }

    private static int sum(Player p, java.util.function.ToIntFunction<Node> f) {
        int s = 0;
        for (String id : owned(p)) { Node n = NODES.get(id); if (n != null) s += f.applyAsInt(n); }
        return s;
    }

    public static Stats stats(Player p) {
        return new Stats(START_STAT + hp(p), START_STAT + sum(p, Node::attack), START_STAT + sum(p, Node::defense), START_STAT + sum(p, Node::speed),
                START_STAT + sum(p, Node::magic), START_STAT + sum(p, Node::luck), START_STAT);
    }

    public static int hearts(Player p) { return sum(p, Node::hearts); }

    /** HP stat from the Vitality branch. */
    public static int hp(Player p) {
        int s = 0;
        for (String id : owned(p)) s += switch (id) { case "vital_1" -> 4; case "vital_3" -> 5; case "vital_5" -> 6; case "vital_6" -> 5; default -> 0; };
        return s;
    }
    public static int walkPercent(Player p) { return sum(p, Node::walk); }
    public static int tiles(Player p) { return sum(p, Node::tiles); }
    public static int miningPercent(Player p) { return sum(p, Node::mining); }

    /** Why a node can't be bought right now, or null if it can. */
    public static String blocker(ServerPlayer p, BaseCoreBlockEntity core, Node n) {
        if (core == null || core.owner() == null || !core.owner().equals(p.getUUID())) return "Only the base's owner can train here.";
        if (core.baseLevel() < 1) return "Your base must be Level 1 first.";
        if (owned(p).contains(n.id())) return "You already have " + n.name() + ".";
        for (String r : n.requires()) if (!owned(p).contains(r)) return "Needs " + NODES.get(r).name() + " first.";
        if (core.baseLevel() < n.tier()) return "Needs your base at Level " + n.tier() + ".";
        int cost = cost(owned(p).size());
        if (XpBank.points(p) < cost) return "Needs " + cost + " XP (you have " + XpBank.points(p) + ").";
        return null;
    }

    public static String buy(ServerPlayer p, BaseCoreBlockEntity core, String id) {
        Node n = NODES.get(id);
        if (n == null) return "No such skill.";
        String why = blocker(p, core, n);
        if (why != null) return why;
        XpBank.take(p, cost(owned(p).size()));
        owned(p).add(id);
        PlayerCharacter.applySpeed(p);
        PlayerCharacter.applyHealth(p);
        return "Learned " + n.name() + ": " + n.desc() + ".";
    }

    /** What the skill tree screen shows, sent with the base's screen. */
    public static JsonObject view(ServerPlayer p, BaseCoreBlockEntity core) {
        JsonObject o = new JsonObject();
        boolean owner = core != null && core.owner() != null && core.owner().equals(p.getUUID());
        o.addProperty("owner", owner);
        o.addProperty("baseLevel", core == null ? 0 : core.baseLevel());
        o.addProperty("xp", XpBank.points(p));
        o.addProperty("next", cost(owned(p).size()));
        o.addProperty("count", owned(p).size());
        o.addProperty("total", NODES.size());
        String last = LAST.get(p.getUUID());
        if (last != null) o.addProperty("message", last);
        JsonArray nodes = new JsonArray();
        for (Node n : NODES.values()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", n.id());
            j.addProperty("branch", n.branch());
            j.addProperty("index", n.index());
            j.addProperty("tier", n.tier());
            j.addProperty("name", n.name());
            j.addProperty("desc", n.desc());
            j.addProperty("owned", owned(p).contains(n.id()));
            String why = owned(p).contains(n.id()) ? null : blocker(p, core, n);
            if (why != null) j.addProperty("why", why);
            JsonArray req = new JsonArray();
            for (String r : n.requires()) req.add(NODES.get(r).name());
            j.add("requires", req);
            nodes.add(j);
        }
        o.add("nodes", nodes);
        Stats s = stats(p);
        o.addProperty("summary", "HP " + s.hp() + " · Attack " + s.attack() + " · Defense " + s.defense() + " · Magic " + s.magic() + " · Speed " + s.speed()
                + " · Luck " + s.luck() + " · +" + hearts(p) + " hearts · +" + walkPercent(p) + "% walk · +" + miningPercent(p) + "% mining");
        return o;
    }
}
