package dev.serantis.battle.moves;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-English descriptions of moves for the battle screen, written from each move's actual rules
 * (its shape, reach, hit chance and every effect with its numbers), so players can see exactly
 * what a move does.
 */
public final class MoveExplain {
    private MoveExplain() {}

    private static String s(JsonObject o, String k) { return MoveSpec.str(o, k); }
    private static int i(JsonObject o, String k, int def) { return o.has(k) ? o.get(k).getAsInt() : def; }
    private static double d(JsonObject o, String k, double def) { return o.has(k) ? o.get(k).getAsDouble() : def; }

    private static String title(String id) {
        if (id == null) return "";
        StringBuilder b = new StringBuilder();
        for (String w : id.split("_")) if (!w.isEmpty()) b.append(b.length() > 0 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }

    private static String turns(int t) { return t + (t == 1 ? " turn" : " turns"); }
    private static String tiles(int t) { return t + (t == 1 ? " tile" : " tiles"); }
    private static String pct(double c) { return Math.round(c * 100) + "%"; }

    /** One short line: kind, influence, reach, hit chance and power. */
    public static String line(MoveSpec s) {
        List<String> parts = new ArrayList<>();
        boolean support = s.support();
        parts.add(support ? "Support" : s.magic() ? "Magic" : "ranged".equals(s.category) ? "Physical (shot)" : "Physical");
        parts.add("universal".equals(s.influence) ? "any creature" : "none".equals(s.influence) ? "Normal" : title(s.influence));
        if ("self".equals(s.target)) parts.add("on itself");
        else if ("self_radius".equals(s.shape)) parts.add("around itself");
        else if (s.max <= 1) parts.add("melee, next to it");
        else parts.add("ranged, " + (s.min == s.max ? tiles(s.max) : s.min + "–" + s.max + " tiles"));
        if (!support && !"self".equals(s.target))
            parts.add(pct(s.accuracy) + " base hit");
        if (s.sheetPower > 0) parts.add("Power " + s.sheetPower + (s.hits > 1 ? " ×" + s.hits : ""));
        return String.join(" · ", parts);
    }

    /** Full explanation, a few sentences. */
    public static String explain(MoveSpec s) {
        List<String> out = new ArrayList<>();
        String stat = s.magic() ? "its Magic against the target's Magic" : "its Attack against the target's Defense";

        if (s.has("charge"))
            out.add("Takes a turn to charge, standing still. At the start of its next turn it fires at the same target (if still in reach), which uses that turn too. Stuns, freezes, sleep and interrupt moves cancel it.");
        if (s.has("delayed"))
            out.add("Marks a 3×3 area around the target. At the start of the user's next turn it crashes down on everyone still inside, allies included. Step out of the red area to dodge it.");
        if (s.has("dash")) out.add("The user rushes next to the target first.");

        if (s.power > 0) {
            String who = switch (s.shape) {
                case "self_radius" -> "every enemy within " + tiles(Math.max(1, s.radius)) + " of the user";
                case "target_radius" -> "the target and every enemy within " + tiles(Math.max(1, s.radius)) + " of it";
                case "cross" -> "the target and every enemy right next to it";
                case "square" -> "every enemy in the " + (2 * Math.max(1, s.radius) + 1) + "×" + (2 * Math.max(1, s.radius) + 1) + " area around the target";
                case "line" -> "every enemy in a line from the user through the target";
                case "line_first" -> "the first enemy in a line toward the target";
                case "arc" -> "the target and up to 2 other enemies next to the user";
                default -> "the target";
            };
            String hits = s.hits > 1 ? s.hits + " times" : "";
            out.add("Hits " + who + (hits.isEmpty() ? "" : " " + hits) + ", using " + stat + "."
                    + ("melee".equals(s.range) ? " Melee moves deal 15% extra damage." : ""));
            if ("chain".equals(s.shape)) {
                for (JsonObject e : s.effects) if ("chain".equals(s(e, "type")))
                    out.add("Then jumps to up to " + i(e, "jumps", 2) + " more enemies within 2 tiles (Power " + i(e, "power", 6) + " each).");
            }
        } else if (!s.support() && !"self".equals(s.target)) {
            out.add("Deals no damage; it must hit to apply its effects.");
        }

        for (JsonObject e : s.effects) {
            String type = s(e, "type"), whoKey = s(e, "who");
            String who = whoKey == null ? "The target" : switch (whoKey) {
                case "self" -> "The user";
                case "allies_near_self" -> "The user and allies within " + tiles(i(e, "radius", 1));
                case "allies_near_target" -> "The target and its allies within " + tiles(i(e, "radius", 1));
                case "ally" -> "The ally";
                case "hit" -> "Each enemy hit";
                default -> "The target";
            };
            String chance = e.has("chance") && d(e, "chance", 1) < 1 ? pct(d(e, "chance", 1)) + " chance: " : "";
            switch (type) {
                case "stat" -> {
                    int amt = i(e, "amount", 0);
                    String st = title(s(e, "stat"));
                    boolean acc = "accuracy".equals(s(e, "stat"));
                    String size = (amt > 0 ? "+" : "−") + Math.abs(amt) * (acc ? 1 : 10) + (acc ? " points" : "%");
                    boolean many = who.contains(" and ");
                    out.add(chance + who + (amt > 0 ? (many ? " get " : " gets ") : (many ? " suffer " : " suffers ")) + (acc ? "hit chance" : st) + " " + size + " for " + turns(i(e, "turns", 2)) + ".");
                }
                case "status" -> {
                    String status = s(e, "status");
                    int t = i(e, "turns", 2);
                    String what = switch (status == null ? "" : status) {
                        case "burning" -> "is set Burning for " + turns(t) + " (about " + i(e, "power", 0) + " damage at the end of each of its turns, more from stronger users)";
                        case "bleeding" -> "starts Bleeding for " + turns(t) + " (loses 5% of its max HP each turn)";
                        case "poison" -> "is Poisoned for " + turns(t) + " (loses 5% of its max HP each turn)";
                        case "doom" -> "is held in a deadly grasp for " + turns(t) + " (about " + i(e, "power", 0) + " damage at the end of each of its turns, more from stronger users)";
                        case "freeze" -> "is Frozen for " + turns(t) + " (can't act)";
                        case "stun" -> "is Stunned for " + turns(t) + " (can't act)";
                        case "sleep" -> "falls Asleep for up to " + turns(t) + " (skips turns; wakes when hit)";
                        case "confused" -> "is Confused for " + turns(t) + " (half the time it attacks a random nearby creature)";
                        case "frightened" -> "is Frightened for " + turns(t) + " (Attack and Magic −30%)";
                        case "immobilized" -> "is Rooted for " + turns(t) + " (can't move)";
                        case "slowed" -> "is Slowed for " + turns(t) + " (moves 2 fewer tiles)";
                        default -> "is " + status + " for " + turns(t);
                    };
                    out.add(chance + (whoKey == null ? "The target" : who) + " " + what + ".");
                }
                case "movement" -> {
                    int n = i(e, "amount", 0);
                    boolean many = who.contains(" and ");
                    out.add(who + (many ? " move " : " moves ") + (n > 0 ? tiles(n) + " further" : tiles(-n) + " less") + " next turn.");
                }
                case "heal" -> out.add(who + " recovers HP (Power " + i(e, "amount", 0) + ", grows with the user's level).");
                case "ignore" -> out.add("Ignores " + i(e, "amount", 0) + " points (" + (i(e, "amount", 0) * 10) + "%) of the target's " + title(s(e, "stat")) + ".");
                case "bonus" -> out.add("moved2".equals(s(e, "when"))
                        ? "+" + i(e, "amount", 0) + " Power if the user moved at least 2 tiles before attacking."
                        : "+" + i(e, "amount", 0) + " Power against a target that has a boost.");
                case "cleanse" -> out.add(who + " shake" + (who.contains(" and ") ? "" : "s") + " off " + i(e, "count", 1) + " bad effect" + (i(e, "count", 1) == 1 ? "" : "s") + ".");
                case "recoil_stance" -> out.add("Counter Stance: until the user's next turn, anything that damages it takes 25% of that damage back.");
                case "no_consecutive" -> out.add("Can't be used two turns in a row.");
                case "dispel" -> out.add("Strips one boost from the target, and it can't be boosted for " + turns(i(e, "block_turns", 1)) + ".");
                case "push" -> out.add("Pushes enemies hit " + tiles(i(e, "tiles", 1)) + " away from " + ("center".equals(s(e, "origin")) ? "where it lands" : "the user") + " (walls, high ledges and other creatures stop them).");
                case "pull" -> out.add("Pulls enemies " + (e.has("first") && e.get("first").getAsBoolean() ? "in the area " : "hit ") + tiles(i(e, "tiles", 1)) + " toward " + ("user".equals(s(e, "toward")) ? "the user" : "the centre")
                        + (e.has("first") && e.get("first").getAsBoolean() ? ", before the damage." : "."));
                case "teleport" -> out.add("Flings each creature hit " + tiles(i(e, "tiles", 2)) + " away, to a random open spot.");
                case "shift" -> out.add("Knocks each creature hit 1 tile in a random open direction.");
                case "interrupt" -> out.add((d(e, "chance", 1) < 1 ? pct(d(e, "chance", 1)) + " chance to cancel" : "Cancels") + " the target's Charged Shot or Blazing Meteor.");
                case "zone" -> out.add(zone(s(e, "zone"), i(e, "turns", 2), i(e, "amount", 0)));
                default -> { }
            }
        }
        if (s.support() && out.isEmpty() && s.text != null) out.add(s.text + ".");
        return String.join(" ", out);
    }

    private static String zone(String kind, int turns, int amount) {
        String t = turns(turns);
        return switch (kind == null ? "" : kind) {
            case "burning" -> "Leaves burning ground on the 3×3 area for " + t + ": Power " + amount + " damage to anyone who steps in or starts a turn there.";
            case "ring" -> "Leaves a ring of fire just outside the blast for " + t + ": Power " + amount + " damage to anyone who crosses it.";
            case "ice" -> "Freezes the 3×3 area for " + t + ": each tile costs 1 extra movement.";
            case "sink" -> "Opens a 3×3 Sinkhole for " + t + ": each tile costs 1 extra movement, and no movement bonuses while standing in it.";
            case "storm" -> "Leaves a Storm Cloud over the 3×3 area for " + t + ": at the end of each side's turn, one creature of that side inside is struck (Power " + amount + ").";
            case "horizon" -> "Opens an Event Horizon over the 3×3 area for " + t + ": anyone ending a turn inside takes Power " + amount + " damage and is Slowed.";
            case "night" -> "Casts Eternal Night over the 3×3 area for " + t + ": anyone ending a turn inside takes Power " + amount + " damage and loses 2 Defense.";
            default -> "Leaves a zone for " + t + ".";
        } + " Zones affect friend and foe; flyers pass over ground zones.";
    }
}
