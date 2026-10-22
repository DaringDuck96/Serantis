package dev.serantis.battle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.battle.BattleMove;
import dev.ahrivaea.battle.DamageCalc;
import dev.ahrivaea.creature.Influence;
import dev.serantis.battle.moves.MoveSpec;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every kind of weapon fights differently in battle. Power starts from the weapon's own damage (and Sharpness), as
 * before: 20 + 4 x damage for melee weapons, 35 + 4 x (damage - 1) for bows and crossbows. Then:
 *
 *   Bare hands  Punch    reach 1     always hits (100%)
 *   Sword       Slash    reach 1     x1.0, 100% hit, keen: half again the chance of a critical hit
 *   Axe         Cleave   reach 1     x0.9, 85% hit, also hits up to 2 more enemies next to you, and lowers Defense
 *                                    of whoever it hits (1 stage, 2 turns)
 *   Mace        Smash    reach 1     x1.35, 80% hit, 25% chance to stun (1 turn), +25% Power from higher ground;
 *                                    but swinging it leaves you exposed: critical hits on you are twice as likely
 *                                    (and +10%) until your next turn
 *   Spear       Thrust   reach 1-2   x1.0, 95% hit, pierces: hits every enemy in a line up to 2 tiles out
 *   Trident     Hurl     reach 1-3   x0.8, 90% hit, +40% Power on Water creatures, +20% when it's raining
 *   Bow         Shoot    reach 2-6   x1.0, 85% hit, +10% Power per tile past 3 (+30% at 6); can't shoot point blank
 *   Crossbow    Bolt     reach 2-7   x1.3, 90% hit, ignores 20% of Defense; reloading: can't fire two turns running
 *
 * Each is a move from the same catalogue creatures use (MoveSpec), built for the weapon in hand, so the battle screen
 * explains it and areas, statuses and bonuses work the same way. Sweeps and thrusts (axe, spear) don't give the target
 * the chance to strike straight back.
 */
public final class WeaponStyles {
    /** Weapon move name (the item's name) -> its move, as built for the latest player holding it. */
    private static final Map<String, MoveSpec> BY_NAME = new ConcurrentHashMap<>();

    private WeaponStyles() {}

    public enum Kind { FIST, SWORD, AXE, MACE, SPEAR, TRIDENT, BOW, CROSSBOW }

    public static Kind kind(ItemStack s) {
        if (s == null || s.isEmpty()) return Kind.FIST;
        if (s.getItem() instanceof CrossbowItem) return Kind.CROSSBOW;
        if (s.getItem() instanceof BowItem) return Kind.BOW;
        if (s.is(Items.MACE)) return Kind.MACE;
        if (s.is(Items.TRIDENT)) return Kind.TRIDENT;
        if (s.is(ItemTags.SPEARS)) return Kind.SPEAR;
        if (s.is(ItemTags.AXES)) return Kind.AXE;
        return Kind.SWORD;                                                  // swords, and any other weapon
    }

    /** Is this battle move a player's weapon attack (not a spell)? */
    public static boolean isWeaponMove(BattleMove m) {
        return m != null && BY_NAME.containsKey(m.name());
    }

    /** The weapon move behind this battle move, or null. */
    public static MoveSpec spec(BattleMove m) {
        if (m == null) return null;
        MoveSpec s = BY_NAME.get(m.name());
        return s != null && s.min == m.minRange() && s.max == m.maxRange() ? s : null;
    }

    private static JsonObject e(String json) { return JsonParser.parseString(json).getAsJsonObject(); }

    /** The battle move for this weapon: name is what the battle screen shows, damage its attack damage (with Sharpness). */
    public static BattleMove build(ItemStack hand, String name, double damage) {
        Kind k = kind(hand);
        MoveSpec same = BY_NAME.get(name);                               // two kinds of weapon renamed alike: keep them apart
        if (same != null && !same.id.equals("weapon_" + k.name().toLowerCase()))
            name = name + " (" + k.name().charAt(0) + k.name().substring(1).toLowerCase() + ")";
        boolean ranged = k == Kind.BOW || k == Kind.CROSSBOW;
        double base = ranged ? 35 + 4 * Math.max(0, damage - 1) : 20 + 4 * damage;
        MoveSpec s = new MoveSpec();
        s.id = "weapon_" + k.name().toLowerCase();
        s.name = name;
        s.category = ranged || k == Kind.TRIDENT ? "ranged" : "physical";
        s.influence = "none";
        s.range = "weapon";                                                 // (not "melee": no catalogue melee bonus)
        s.target = "enemy";
        s.shape = "single";
        s.hits = 1;
        s.enabled = true;
        s.effects = new ArrayList<>();
        double mult = 1.0;
        switch (k) {
            case FIST -> { s.min = 1; s.max = 1; s.accuracy = 1.0;
                s.text = "Bare hands: always hits, but weak. Pick up a weapon!"; }
            case SWORD -> { s.min = 1; s.max = 1; s.accuracy = 1.0;
                s.effects.add(e("{\"type\":\"keen\"}"));
                s.text = "Sword: reliable. Never misses, and lands critical hits half again as often."; }
            case AXE -> { s.min = 1; s.max = 1; s.accuracy = 0.85; mult = 0.9; s.shape = "arc";
                s.effects.add(e("{\"type\":\"stat\",\"stat\":\"defense\",\"amount\":-1,\"turns\":2}"));
                s.text = "Axe: cleaves the target and up to 2 more enemies next to you, and cracks their Defense. Less accurate. Too wide a swing for them to strike straight back."; }
            case MACE -> { s.min = 1; s.max = 1; s.accuracy = 0.8; mult = 1.35;
                s.effects.add(e("{\"type\":\"status\",\"status\":\"stun\",\"chance\":0.25,\"turns\":1}"));
                s.effects.add(e("{\"type\":\"bonus\",\"when\":\"higher\",\"pct\":25}"));
                s.effects.add(e("{\"type\":\"status\",\"who\":\"self\",\"status\":\"exposed\",\"turns\":1}"));
                s.text = "Mace: hits hardest, may stun, and hits harder still from higher ground. Often misses, and leaves you exposed: critical hits on you are twice as likely until your next turn."; }
            case SPEAR -> { s.min = 1; s.max = 2; s.accuracy = 0.95; s.shape = "line";
                s.text = "Spear: reaches 2 tiles and pierces, hitting every enemy in the line. Strike from behind an ally; thrusts can't be struck straight back."; }
            case TRIDENT -> { s.min = 1; s.max = 3; s.accuracy = 0.9; mult = 0.8;
                s.effects.add(e("{\"type\":\"bonus\",\"when\":\"water_target\",\"pct\":40}"));
                s.effects.add(e("{\"type\":\"bonus\",\"when\":\"raining\",\"pct\":20}"));
                s.text = "Trident: hurled up to 3 tiles. Deadly against Water creatures (+40%) and in the rain (+20%)."; }
            case BOW -> { s.min = 2; s.max = 6; s.accuracy = 0.85;
                s.effects.add(e("{\"type\":\"bonus\",\"when\":\"far\",\"pct\":10}"));
                s.text = "Bow: shoots 2 to 6 tiles, and the farther the shot the harder it hits (+10% per tile past 3). Useless point blank."; }
            case CROSSBOW -> { s.min = 2; s.max = 7; s.accuracy = 0.9; mult = 1.3;
                s.effects.add(e("{\"type\":\"ignore\",\"stat\":\"defense\",\"amount\":2}"));
                s.effects.add(e("{\"type\":\"no_consecutive\"}"));
                s.text = "Crossbow: a heavy bolt up to 7 tiles that punches through 20% of Defense, but it has to reload: it can't fire two turns in a row."; }
        }
        int power = (int) Math.round(base * mult);
        s.power = power;
        s.sheetPower = power;
        BY_NAME.put(name, s);
        return new BattleMove(name, new DamageCalc.Move(power, DamageCalc.Category.PHYSICAL, Influence.NONE, s.accuracy), s.min, s.max);
    }

    public static List<String> kinds() {
        List<String> out = new ArrayList<>();
        for (Kind k : Kind.values()) out.add(k.name());
        return out;
    }
}
