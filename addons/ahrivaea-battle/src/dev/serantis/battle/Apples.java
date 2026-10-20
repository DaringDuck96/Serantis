package dev.serantis.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Stats;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.party.PartySync;
import dev.ahrivaea.party.RecruitRules;
import dev.serantis.battle.moves.MovesHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Golden apples: feed one to a creature in your party and pick a stat to raise. The weaker the
 * creature's rarity, the bigger the gain, so common creatures can keep up late in the game:
 * Common +2, Uncommon +1, Rare +0.5, Super Rare +0.25, Legendary no effect.
 * Each stat takes at most 10 apples.
 *
 * Like every creature stat, the gain is to its Level 20 value (it scales down at lower levels the same
 * way the rest of the stat does). Stats are whole numbers, so halves and quarters are counted exactly and
 * the stat goes up each time they add up to a whole point.
 */
public final class Apples {
    public static final String[] STATS = {"hp", "attack", "defense", "speed", "magic", "luck", "charisma"};
    public static final int MAX_PER_STAT = 10;

    private Apples() {}

    public static double rate(String rarity) {
        if (rarity == null) return 2;
        return switch (rarity) {
            case "Uncommon" -> 1;
            case "Rare" -> 0.5;
            case "Super Rare" -> 0.25;
            case "Legendary" -> 0;
            default -> 2;
        };
    }

    public static String rarity(String speciesId, Influence influence) {
        if (speciesId == null) return "Common";
        return Species.get(speciesId).map(sp -> RecruitRules.rarity(sp, influence)).orElse("Common");
    }

    public static int index(String stat) {
        for (int i = 0; i < STATS.length; i++) if (STATS[i].equals(stat)) return i;
        return -1;
    }

    public static int[] parse(String saved) {
        int[] out = new int[STATS.length];
        if (saved == null || saved.isEmpty()) return out;
        String[] p = saved.split(",");
        for (int i = 0; i < out.length && i < p.length; i++) {
            try { out[i] = Math.max(0, Math.min(MAX_PER_STAT, Integer.parseInt(p[i].trim()))); } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    public static String encode(int[] counts) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < STATS.length; i++) b.append(i > 0 ? "," : "").append(i < counts.length ? counts[i] : 0);
        return b.toString();
    }

    public static boolean any(String saved) {
        for (int c : parse(saved)) if (c > 0) return true;
        return false;
    }

    /** The exact gain on one stat (may be a half or quarter). */
    public static double gain(int count, double rate) { return count * rate; }

    /** Stats with the apples' gains added (the whole-number part of each). */
    public static Stats apply(Stats s, String saved, String rarity) {
        if (s == null || !any(saved)) return s;
        int[] c = parse(saved);
        double r = rate(rarity);
        int[] v = {s.hp(), s.attack(), s.defense(), s.speed(), s.magic(), s.luck(), s.charisma()};
        for (int i = 0; i < v.length; i++) v[i] += (int) Math.floor(gain(c[i], r) + 1e-9);
        return new Stats(v[0], v[1], v[2], v[3], v[4], v[5], v[6]);
    }

    /** A creature's stats with its temperament and apples. */
    public static Stats full(Stats species, String temperament, String apples, String speciesId, Influence influence, int level) {
        Stats s = Temperament.apply(species, temperament, level);
        return any(apples) ? apply(s, apples, rarity(speciesId, influence)) : s;
    }

    public static Stats of(CaughtCreature c, Stats species) {
        MovesHolder h = (MovesHolder) (Object) c;
        return full(species, h.serantis$temperament(), h.serantis$apples(), c.species(), c.influence(), c.level());
    }

    /** What the party screen needs to show the apple screen. */
    public static void describe(CaughtCreature c, JsonObject o) {
        String rarity = rarity(c.species(), c.influence());
        int[] counts = parse(((MovesHolder) (Object) c).serantis$apples());
        JsonArray a = new JsonArray();
        for (int n : counts) a.add(n);
        o.add("serantis_apples", a);
        o.addProperty("serantis_rarity", rarity);
        o.addProperty("serantis_apple_rate", rate(rarity));
    }

    // ------------------------------------------------------------------ feeding

    private static int find(Inventory inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(Items.GOLDEN_APPLE)) return i;
        return -1;
    }

    /** Feeds the creature in a party slot a golden apple for one stat. Tells the player what happened. */
    public static void feed(ServerPlayer player, int slot, String stat) {
        List<CaughtCreature> party = Party.get(player);
        int i = index(stat);
        if (slot < 0 || slot >= party.size() || i < 0) return;
        CaughtCreature c = party.get(slot);
        MovesHolder h = (MovesHolder) (Object) c;
        String name = h.serantis$nickname() != null && !h.serantis$nickname().isEmpty() ? h.serantis$nickname()
                : Species.get(c.species()).map(sp -> sp.nameFor(c.influence())).orElse("Your creature");
        String rarity = rarity(c.species(), c.influence());
        double r = rate(rarity);
        if (r <= 0) { tell(player, "Golden apples have no effect on Legendary creatures."); return; }
        int[] counts = parse(h.serantis$apples());
        if (counts[i] >= MAX_PER_STAT) { tell(player, name + "'s " + Temperament.label(stat) + " has already had 10 golden apples."); return; }
        Inventory inv = player.getInventory();
        int at = find(inv);
        boolean free = player.getAbilities().instabuild;
        if (at < 0 && !free) { tell(player, "You need a golden apple."); return; }
        if (at >= 0 && !free) {
            ItemStack stack = inv.getItem(at);
            stack.shrink(1);
            inv.setChanged();
        }
        counts[i]++;
        h.serantis$setApples(encode(counts));
        PartySync.sync(player);
        tell(player, name + " ate a golden apple: " + Temperament.label(stat) + " +" + number(r)
                + " (now +" + number(gain(counts[i], r)) + ", " + counts[i] + "/10 apples).");
    }

    public static String number(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        String s = String.format(java.util.Locale.ROOT, "%.2f", v);
        return s.endsWith("0") ? s.substring(0, s.length() - 1) : s;
    }

    private static void tell(ServerPlayer player, String msg) {
        player.sendSystemMessage(Component.literal(msg));
    }
}
