package dev.serantis.battle;

import com.google.gson.JsonObject;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.party.PartySync;
import dev.ahrivaea.party.Starters;
import dev.serantis.battle.moves.MovesHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Player classes, picked once when a player first joins (before their starter):
 *  - Knight: Attack boost; starts with an Iron Chestplate and an Iron Sword.
 *  - Bulwark: Defense boost; starts with a Shield and an Iron Mace.
 *  - Wizard: Magic boost; starts with a Magic Staff of the influence they choose.
 *  - Journeyman: Luck boost; starts with 2 Golden Apples.
 *  - Performer: Charisma boost; picks the temperament (nature) of their starter.
 *  - Tradesman: no boost; crafts anywhere (a 3x3 crafting grid on a key, see openCrafting).
 * The boost is a flat +5 to that stat, for good.
 */
public final class PlayerClass {
    public record Kind(String id, String name, String stat, String perk) {
        /** Whether the class boosts a stat (the Tradesman doesn't). */
        public boolean boosts() { return !"none".equals(stat); }
        /** "+5 Attack, for good" or "No stat boost". */
        public String boostText() { return boosts() ? "+" + START_BOOST + " " + Temperament.label(stat) + ", for good" : "No stat boost"; }
    }

    public static final List<Kind> ALL = List.of(
            new Kind("knight", "Knight", "attack", "Starts with an Iron Chestplate and an Iron Sword."),
            new Kind("bulwark", "Bulwark", "defense", "Starts with a Shield and an Iron Mace."),
            new Kind("wizard", "Wizard", "magic", "Starts with a Magic Staff linked to a random spell of the influence you choose."),
            new Kind("journeyman", "Journeyman", "luck", "Starts with 2 Golden Apples. Lucky Finds: beaten creatures drop their loot twice 10% more often, and lore books drop twice as often."),
            new Kind("performer", "Performer", "charisma", "Picks the temperament (nature) of their starter. Showstopper: rallies are twice as strong, and workers at your bases work 5% faster."),
            new Kind("tradesman", "Tradesman", "none", "Crafts anywhere: press U (Controls > Serantis) to open a crafting grid, no crafting table needed."));

    /** Journeyman: extra chance a beaten creature drops its loot twice, and the lore book chance multiplier. */
    public static final float JOURNEYMAN_DOUBLE_LOOT = 0.10f, JOURNEYMAN_LORE = 2f;
    /** Performer: rally strength multiplier, and base work speed multiplier. */
    public static final double PERFORMER_RALLY = 2.0, PERFORMER_WORK = 1.05;

    public static boolean is(Player p, String id) {
        Kind k = p == null ? null : of(p);
        return k != null && k.id().equals(id);
    }

    public static final int START_BOOST = 5, PER_SKILL = 0, MIN_BOOST = 5;

    /** Added to players: their class, and the temperament a Performer chose for a starter not picked yet. */
    public interface Holder {
        String serantis$playerClass();
        void serantis$setPlayerClass(String id);
        String serantis$pendingTemperament();
        void serantis$setPendingTemperament(String t);
    }

    private PlayerClass() {}

    public static Kind of(Player p) {
        String id = p instanceof Holder h ? h.serantis$playerClass() : null;
        for (Kind k : ALL) if (k.id().equals(id)) return k;
        return null;
    }

    /** The class's boost to this stat right now ("attack", "defense", ...), given how many skills are learned. */
    public static int boost(Player p, String stat, int skills) {
        Kind k = of(p);
        if (k == null || !k.stat().equals(stat)) return 0;
        return Math.max(MIN_BOOST, START_BOOST - PER_SKILL * skills);
    }

    /** The player picked a class (once). extra: the staff's influence (Wizard) or the starter's temperament (Performer). */
    public static void pick(ServerPlayer p, String id, String extra) {
        Holder h = (Holder) p;
        if (h.serantis$playerClass() != null) return;
        Kind k = null;
        for (Kind c : ALL) if (c.id().equals(id)) k = c;
        if (k == null) return;
        switch (k.id()) {
            case "wizard" -> {
                if (!Staffs.hasStaff(extra)) { tell(p, "Choose your staff's influence."); return; }
                give(p, Staffs.starter(extra, p.getRandom()));
            }
            case "performer" -> {
                if (Temperament.of(extra) == null) { tell(p, "Choose your starter's temperament."); return; }
                applyTemperament(p, extra);
            }
            case "knight" -> { give(p, new ItemStack(Items.IRON_CHESTPLATE)); give(p, new ItemStack(Items.IRON_SWORD)); }
            case "bulwark" -> {
                give(p, new ItemStack(Items.SHIELD));
                ItemStack mace = new ItemStack(Items.MACE);
                mace.set(DataComponents.CUSTOM_NAME, Component.literal("Iron Mace"));
                give(p, mace);
            }
            case "journeyman" -> give(p, new ItemStack(Items.GOLDEN_APPLE, 2));
            default -> { }
        }
        h.serantis$setPlayerClass(k.id());
        p.sendSystemMessage(Component.literal("You are a " + k.name() + "! " + (k.boosts() ? k.boostText() + ". " : "") + k.perk()).withStyle(ChatFormatting.GOLD));
        PartySync.sync(p);
    }

    /** A Performer's chosen temperament goes on their starter (now, if they already have it; otherwise when they pick it). */
    private static void applyTemperament(ServerPlayer p, String t) {
        List<CaughtCreature> party = Party.get(p);
        if (Starters.chosen(p) && !party.isEmpty()) {
            ((MovesHolder) (Object) party.get(0)).serantis$setTemperament(t);
            tell(p, "Your " + label(party.get(0)) + " is now " + t + ".");
        } else ((Holder) p).serantis$setPendingTemperament(t);
    }

    /** A starter was just picked: a Performer's temperament goes on it. */
    public static void starterPicked(ServerPlayer p, CaughtCreature starter) {
        Holder h = (Holder) p;
        String t = h.serantis$pendingTemperament();
        if (t == null || starter == null) return;
        ((MovesHolder) (Object) starter).serantis$setTemperament(t);
        h.serantis$setPendingTemperament(null);
    }

    private static String label(CaughtCreature c) {
        return dev.ahrivaea.creature.Species.get(c.species()).map(sp -> sp.nameFor(c.influence())).orElse("starter");
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack) && !stack.isEmpty())
            p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), stack));
    }

    private static void tell(ServerPlayer p, String msg) { p.sendSystemMessage(Component.literal(msg)); }

    /** Tradesman: a crafting table's 3x3 grid, wherever they are. */
    public static void openCrafting(ServerPlayer p) {
        if (!is(p, "tradesman") || p.isSpectator() || dev.ahrivaea.battle.Battles.of(p) != null) return;
        var access = net.minecraft.world.inventory.ContainerLevelAccess.create(p.level(), p.blockPosition());
        p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, who) -> new net.minecraft.world.inventory.CraftingMenu(id, inv, access) {
            @Override public boolean stillValid(Player player) { return player.isAlive(); }     // no table to stand by
        }, Component.translatable("container.crafting")));
    }

    /** "serantis_class" in the party state: the class, or that one still needs picking. */
    public static void describe(ServerPlayer p, JsonObject state) {
        Kind k = of(p);
        JsonObject o = new JsonObject();
        o.addProperty("chosen", k != null);
        if (k != null) {
            o.addProperty("name", k.name());
            if (k.boosts()) o.addProperty("stat", Temperament.label(k.stat()));
            o.addProperty("boost", boost(p, k.stat(), dev.serantis.battle.skills.Skills.owned(p).size()));
        }
        state.add("serantis_class", o);
    }
}
