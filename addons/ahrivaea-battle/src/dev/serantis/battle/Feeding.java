package dev.serantis.battle;

import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Healing;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.party.PartySync;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Right-click food or another consumable in your inventory (client/FeedScreen): eat or drink it yourself, or give
 * it to a creature in your party (or one of yours that's out). Food and healing potions heal a creature like
 * feeding it from the party screen; golden apples open the stat picker (see Apples).
 */
public final class Feeding {
    private Feeding() {}

    /** Can a creature take this (food, or a healing or regeneration potion)? */
    public static boolean feedable(ItemStack s) {
        return !s.isEmpty() && (Healing.foodPercent(s) > 0 || Healing.potion(s) != null || Ailments.cure(s));
    }

    private static ItemStack slot(ServerPlayer p, int slot) {
        return slot >= 0 && slot < p.getInventory().getContainerSize() ? p.getInventory().getItem(slot) : ItemStack.EMPTY;
    }

    /** Eat or drink the item in this inventory slot, as if it had been used from the hand. */
    public static void eat(ServerPlayer p, int slot) {
        ItemStack s = slot(p, slot);
        var c = s.get(DataComponents.CONSUMABLE);
        if (c == null) return;
        if (Battles.of(p) != null) { say(p, "Not in the middle of a battle."); return; }
        if (!c.canConsume(p, s)) { say(p, "You're not hungry."); return; }
        String what = s.getHoverName().getString();
        ItemStack result = s.finishUsingItem(p.level(), p);
        if (result != s) {
            if (s.isEmpty()) p.getInventory().setItem(slot, result);
            else if (!result.isEmpty()) Healing.giveBack(p, result);
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f, 1f);
        say(p, "You had the " + what + ".");
    }

    /** Give the item in this inventory slot to a party creature (partySlot >= 0) or one that's out (entity id). */
    public static void feed(ServerPlayer p, int slot, int partySlot, int entity) {
        ItemStack s = slot(p, slot);
        if (!feedable(s)) return;
        if (Battles.of(p) != null) { say(p, "Not in the middle of a battle."); return; }
        if (Ailments.cure(s) && cure(p, s, partySlot, entity)) return;    // milk or honey: cures a lingering status
        int pct = Healing.foodPercent(s);
        var potion = Healing.potion(s);
        if (pct <= 0 && potion != null) pct = potion.percent();
        String what = s.getHoverName().getString();
        String name;
        int before, after, max;
        if (partySlot >= 0) {
            List<CaughtCreature> party = Party.get(p);
            if (partySlot >= party.size()) return;
            CaughtCreature c = party.get(partySlot);
            Optional<Species> sp = Species.get(c.species());
            if (sp.isEmpty()) return;
            if (Knockout.is(c)) { say(p, sp.get().nameFor(c.influence()) + " is knocked out: it can't be healed for another " + Knockout.clock(c) + "."); return; }
            max = sp.get().statsFor(c.influence()).maxHealth(c.level());
            name = sp.get().nameFor(c.influence());
            before = Math.round(c.health());
            if (c.health() >= max) { say(p, name + " is already at full health."); return; }
            float healed = Math.min(max, c.health() + Math.max(1, Math.round(max * pct / 100f)));
            Party.set(p, partySlot, c.withHealth(healed));
            after = Math.round(healed);
        } else {
            if (!(p.level().getEntity(entity) instanceof CreatureEntity e) || !e.isTame() || !e.isOwnedBy(p) || Battles.inBattle(e)) return;
            if (Knockout.is(e)) { say(p, e.displayName() + " is knocked out: it can't be healed for another " + Knockout.clock(e) + "."); return; }
            max = Math.round(e.getMaxHealth());
            name = e.getDisplayName().getString();
            before = Math.round(e.getHealth());
            if (e.getHealth() >= e.getMaxHealth()) { say(p, name + " is already at full health."); return; }
            e.heal(Math.max(1, Math.round(e.getMaxHealth() * pct / 100f)));
            after = Math.round(e.getHealth());
        }
        ItemStack left = Healing.remainder(s);
        s.consume(1, p);
        Healing.giveBack(p, left);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.7f, 1.1f);
        say(p, name + " had the " + what + ": +" + (after - before) + " HP (" + after + "/" + max + ")");
        PartySync.sync(p);
    }

    /** Milk or honey for a creature with a lingering status: cures it. False if there was nothing to cure (honey then feeds it as food). */
    private static boolean cure(ServerPlayer p, ItemStack s, int partySlot, int entity) {
        String msg;
        if (partySlot >= 0) {
            List<CaughtCreature> party = Party.get(p);
            if (partySlot >= party.size()) return true;
            CaughtCreature c = party.get(partySlot);
            String name = Species.get(c.species()).map(sp -> sp.nameFor(c.influence())).orElse("Your creature");
            String nick = ((dev.serantis.battle.moves.MovesHolder) (Object) c).serantis$nickname();
            msg = Ailments.cureCreature(c, nick != null && !nick.isEmpty() ? nick : name);
            if (msg != null) Party.set(p, partySlot, c);
        } else {
            if (!(p.level().getEntity(entity) instanceof CreatureEntity e) || !e.isTame() || !e.isOwnedBy(p)) return true;
            msg = Ailments.cureCreature(e, e.getDisplayName().getString());
        }
        if (msg == null) {
            if (Healing.foodPercent(s) > 0) return false;
            say(p, "Nothing to cure: milk and honey cure a status left over from a battle.");
            return true;
        }
        ItemStack left = Healing.remainder(s);
        s.consume(1, p);
        Healing.giveBack(p, left);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.7f, 1.1f);
        say(p, msg);
        PartySync.sync(p);
        return true;
    }

    private static void say(ServerPlayer p, String msg) {
        p.sendOverlayMessage(Component.literal(msg).withStyle(ChatFormatting.GOLD));
    }
}
