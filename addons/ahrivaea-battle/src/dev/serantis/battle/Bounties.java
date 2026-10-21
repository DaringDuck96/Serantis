package dev.serantis.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.party.PartySync;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Village bounty boards: right-click any bell to see today's three contracts (sneak to ring it).
 * Each player gets their own three each Minecraft day; a new day brings new ones. Kinds: beat creatures of an
 * influence, win battles, recruit something Rare or rarer, bring the board some items, beat a Creature Master,
 * repel a goblin raid. Finish one and claim it at a bell for XP and golden apples, sometimes a lore book or an
 * influence enchantment book.
 */
public final class Bounties {
    private static final String[] INFLUENCES = Masters.INFLUENCES;
    private static final Item[] DELIVER = {Items.IRON_INGOT, Items.BREAD, Items.WHEAT, Items.COAL, Items.LEATHER, Items.GOLD_INGOT,
            Items.COPPER_INGOT, Items.COOKED_BEEF, Items.OAK_LOG, Items.REDSTONE, Items.STRING, Items.FEATHER};
    private static final int[] DELIVER_N = {16, 16, 32, 32, 12, 8, 24, 16, 64, 24, 16, 16};

    /** kind:param:goal:progress:claimed */
    public record Contract(String kind, String param, int goal, int progress, boolean claimed) {
        String encode() { return kind + ":" + param + ":" + goal + ":" + progress + ":" + (claimed ? 1 : 0); }
        static Contract decode(String s) {
            String[] f = s.split(":");
            return new Contract(f[0], f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3]), "1".equals(f[4]));
        }
        boolean done() { return progress >= goal; }
        Contract plus(int n) { return new Contract(kind, param, goal, Math.min(goal, progress + n), claimed); }
        Contract claim() { return new Contract(kind, param, goal, progress, true); }
    }

    private Bounties() {}

    private static long day(ServerPlayer p) { return p.level().getServer().overworld().getDayTime() / 24000L; }

    /** Today's contracts (made fresh on a new day). */
    public static List<Contract> today(ServerPlayer p) {
        Welcome.Holder h = (Welcome.Holder) p;
        long day = day(p);
        String saved = h.serantis$bounty();
        if (saved != null && saved.startsWith(day + "|")) {
            try {
                List<Contract> out = new ArrayList<>();
                for (String c : saved.substring(saved.indexOf('|') + 1).split(";")) out.add(Contract.decode(c));
                return out;
            } catch (RuntimeException ignored) { }
        }
        List<Contract> out = roll(p, day);
        save(p, out);
        return out;
    }

    private static void save(ServerPlayer p, List<Contract> list) {
        StringBuilder sb = new StringBuilder(day(p) + "|");
        for (int i = 0; i < list.size(); i++) { if (i > 0) sb.append(';'); sb.append(list.get(i).encode()); }
        ((Welcome.Holder) p).serantis$setBounty(sb.toString());
    }

    private static List<Contract> roll(ServerPlayer p, long day) {
        RandomSource r = RandomSource.create(day * 31L + p.getUUID().getLeastSignificantBits());
        List<String> kinds = new ArrayList<>(List.of("defeat", "defeat", "win", "recruit", "deliver", "deliver", "master", "raid"));
        if (!((Welcome.Holder) p).serantis$raid().isEmpty() || !hasBase(p)) kinds.remove("raid");
        List<Contract> out = new ArrayList<>();
        while (out.size() < 3 && !kinds.isEmpty()) {
            String k = kinds.remove(r.nextInt(kinds.size()));
            Contract c = switch (k) {
                case "defeat" -> new Contract("defeat", INFLUENCES[r.nextInt(INFLUENCES.length)], 4 + r.nextInt(5), 0, false);
                case "win" -> new Contract("win", "-", 3 + r.nextInt(4), 0, false);
                case "recruit" -> new Contract("recruit", "Rare", 1, 0, false);
                case "deliver" -> { int i = r.nextInt(DELIVER.length); yield new Contract("deliver", BuiltInRegistries.ITEM.getKey(DELIVER[i]).toString(), DELIVER_N[i], 0, false); }
                case "master" -> new Contract("master", "-", 1, 0, false);
                default -> new Contract("raid", "-", 1, 0, false);
            };
            boolean dup = false;
            for (Contract o : out) if (o.kind().equals(c.kind()) && o.param().equals(c.param())) dup = true;
            if (!dup) out.add(c);
        }
        return out;
    }

    private static boolean hasBase(ServerPlayer p) {
        for (var level : p.level().getServer().getAllLevels())
            for (var pos : dev.ahrivaea.base.Bases.cores(level)) {
                if (!level.isLoaded(pos)) continue;
                var core = dev.ahrivaea.base.Bases.coreAt(level, pos);
                if (core != null && p.getUUID().equals(core.owner())) return true;
            }
        return false;
    }

    // ------------------------------------------------------------------ progress

    private static void progress(ServerPlayer p, String kind, String param, int n) {
        if (p == null || n <= 0) return;
        List<Contract> list = today(p);
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            Contract c = list.get(i);
            if (c.claimed() || c.done() || !c.kind().equals(kind) || !(c.param().equals(param) || "-".equals(c.param()))) continue;
            list.set(i, c.plus(n));
            changed = true;
            if (list.get(i).done()) p.sendSystemMessage(Component.literal("Bounty complete: " + describe(list.get(i)) + ". Claim it at a village bell!").withStyle(ChatFormatting.GOLD));
        }
        if (changed) save(p, list);
    }

    /** A battle was won: the creatures beaten, and whether it was a Master's. */
    public static void battleWon(ServerPlayer p, List<dev.ahrivaea.battle.Battle.Defeated> beaten, boolean master) {
        if (p == null) return;
        progress(p, "win", "-", 1);
        if (master) progress(p, "master", "-", 1);
        for (var d : beaten) progress(p, "defeat", d.influence() == null ? "none" : d.influence().getSerializedName(), 1);
    }

    public static void recruited(ServerPlayer p, dev.ahrivaea.party.CaughtCreature c) {
        if (p == null || c == null) return;
        String r = Apples.rarity(c.species(), c.influence());
        if ("Rare".equals(r) || "Super Rare".equals(r) || "Legendary".equals(r)) progress(p, "recruit", "Rare", 1);
    }

    public static void raidRepelled(ServerPlayer p) { progress(p, "raid", "-", 1); }

    // ------------------------------------------------------------------ the board

    public static String describe(Contract c) {
        return switch (c.kind()) {
            case "defeat" -> "Beat " + c.goal() + " " + Masters.influenceName(c.param()) + " creatures";
            case "win" -> "Win " + c.goal() + " battles";
            case "recruit" -> "Recruit a Rare (or rarer) creature";
            case "deliver" -> "Bring " + c.goal() + " " + BuiltInRegistries.ITEM.getValue(Identifier.parse(c.param())).getName().getString();
            case "master" -> "Beat a Creature Master";
            default -> "Repel a goblin raid on your base";
        };
    }

    private static int xp(ServerPlayer p, Contract c) {
        int base = switch (c.kind()) { case "master", "raid" -> 220; case "recruit" -> 150; case "defeat" -> 20 * c.goal(); case "win" -> 30 * c.goal(); default -> 80; };
        return (int) Math.round(base * (1 + dev.serantis.battle.skills.Skills.owned(p).size() / 24.0));   // grows with you
    }

    /** Hand in a delivery, or claim a finished contract. */
    public static void act(ServerPlayer p, int index) {
        List<Contract> list = today(p);
        if (index < 0) { PartySync.sync(p); return; }                   // opened the board: fresh contracts
        if (index >= list.size()) return;
        Contract c = list.get(index);
        if (c.claimed()) return;
        if ("deliver".equals(c.kind()) && !c.done()) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(c.param()));
            int need = c.goal() - c.progress(), have = 0;
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) have += inv.getItem(i).getCount();
            if (have < need) { p.sendSystemMessage(Component.literal("You need " + need + " " + item.getName().getString() + " (you have " + have + ").").withStyle(ChatFormatting.YELLOW)); return; }
            int left = need;
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.is(item)) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
            c = c.plus(need);
        }
        if (!c.done()) return;
        list.set(index, c.claim());
        save(p, list);
        int xp = xp(p, c);
        dev.serantis.battle.skills.XpBank.give(p, xp);
        int apples = "master".equals(c.kind()) || "raid".equals(c.kind()) ? 2 : 1;
        give(p, new ItemStack(Items.GOLDEN_APPLE, apples));
        String extra = "";
        RandomSource r = p.getRandom();
        if (r.nextFloat() < 0.25f) {
            ItemStack book = LoreDrops.roll(r, 1000f);
            if (book != null) {
                var content = book.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
                if (content != null) ((Welcome.Holder) p).serantis$loreTitles().add(content.title().raw());
                give(p, book);
                extra = ", and a lore book";
            }
        } else if (r.nextFloat() < 0.12f) {
            var reg = p.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            String id = "bane_" + INFLUENCES[r.nextInt(INFLUENCES.length)];
            var h = reg.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, Identifier.parse("serantis_battle:" + id)));
            if (h.isPresent()) {
                give(p, net.minecraft.world.item.enchantment.EnchantmentHelper.createBook(new net.minecraft.world.item.enchantment.EnchantmentInstance(h.get(), 1 + r.nextInt(2))));
                extra = ", and an enchanted book";
            }
        }
        p.sendSystemMessage(Component.literal("Bounty claimed: +" + xp + " XP, " + apples + " golden apple" + (apples > 1 ? "s" : "") + extra + ".").withStyle(ChatFormatting.GREEN));
        PartySync.sync(p);
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s) && !s.isEmpty())
            p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), s));
    }

    /** "serantis_bounties" in the party state: today's contracts, and how long until new ones. */
    public static void describe(ServerPlayer p, JsonObject state) {
        JsonArray a = new JsonArray();
        for (Contract c : today(p)) {
            JsonObject o = new JsonObject();
            o.addProperty("text", describe(c));
            o.addProperty("progress", c.progress());
            o.addProperty("goal", c.goal());
            o.addProperty("claimed", c.claimed());
            o.addProperty("deliver", "deliver".equals(c.kind()));
            o.addProperty("xp", xp(p, c));
            a.add(o);
        }
        JsonObject o = new JsonObject();
        o.add("contracts", a);
        long t = p.level().getServer().overworld().getDayTime() % 24000L;
        o.addProperty("minutesLeft", (int) Math.ceil((24000L - t) / 1200.0));
        state.add("serantis_bounties", o);
    }
}
