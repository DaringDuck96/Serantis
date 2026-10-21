package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One-line "what it's for" tooltips on every modpack item that doesn't already explain itself, from
 * serantis_battle/item_tips.json ("namespace:path" keys, "*" matching any part of the path).
 */
public final class ItemTips {
    private static List<String[]> patterns;
    private static final Map<Item, String> CACHE = new HashMap<>();

    private ItemTips() {}

    private static void load() {
        if (patterns != null) return;
        patterns = new ArrayList<>();
        try (var in = ItemTips.class.getResourceAsStream("/serantis_battle/item_tips.json")) {
            if (in == null) return;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : o.entrySet()) patterns.add(new String[]{e.getKey(), e.getValue().getAsString()});
        } catch (Exception ignored) { }
    }

    /** The tip for an item, or null. Exact ids win over patterns. */
    public static String of(Item item) {
        if (CACHE.containsKey(item)) return CACHE.get(item);
        load();
        String id = BuiltInRegistries.ITEM.getKey(item).toString(), found = null;
        for (String[] p : patterns) if (p[0].equals(id)) found = p[1];
        if (found == null)
            for (String[] p : patterns) {
                if (!p[0].contains("*")) continue;
                String re = java.util.regex.Pattern.quote(p[0]).replace("*", "\\E[a-z0-9_]+\\Q");
                if (id.matches(re)) { found = p[1]; break; }
            }
        CACHE.put(item, found);
        return found;
    }

    /** Adds the tip under the item's name (wrapped to a readable width). */
    public static List<Component> add(net.minecraft.world.item.ItemStack stack, List<Component> lines) {
        if (stack.isEmpty() || lines.isEmpty()) return lines;
        var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) return lines;           // it already explains itself
        String tip = of(stack.getItem());
        if (tip == null) return lines;
        List<Component> out = new ArrayList<>(lines);
        int at = 1;
        StringBuilder line = new StringBuilder();
        for (String word : tip.split(" ")) {
            if (line.length() + word.length() + 1 > 42 && !line.isEmpty()) {
                out.add(at++, Component.literal(line.toString()).withStyle(ChatFormatting.DARK_AQUA));
                line = new StringBuilder();
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) out.add(at, Component.literal(line.toString()).withStyle(ChatFormatting.DARK_AQUA));
        return out;
    }
}
