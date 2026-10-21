package dev.serantis.battle.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The guide's Recipes page: every craftable thing that isn't in vanilla Minecraft, drawn as a crafting grid (or a
 * smithing row) straight from the recipe files in the mods, so it's always up to date. A card with several
 * recipes (a whole armour set, every dye colour) cycles through them. The list is serantis_battle/guide_recipes.json.
 */
public final class RecipeGuide {
    static final int CARD_H = 74, HEAD_H = 18, OTHER_H = 24;
    private static final int GOLD = 0xFFF2C94C, TEXT = 0xFFE6E2D8, DIM = 0xFF9AA4B4, SLOT = 0xFF2A2638, SLOT_EDGE = 0xFF4A4460;
    private static final Map<String, Item> TAGS = Map.of("minecraft:logs", Items.OAK_LOG, "minecraft:planks", Items.OAK_PLANKS,
            "minecraft:wool", Items.WHITE_WOOL, "minecraft:stone_crafting_materials", Items.COBBLESTONE, "minecraft:coals", Items.COAL);

    /** One recipe, ready to draw. */
    record Recipe(String station, boolean shapeless, ItemStack[] grid, ItemStack result) {}

    record Entry(List<Recipe> recipes, String note) {}

    record Section(String title, List<Entry> entries) {}

    private static List<Section> sections;
    private static List<String[]> other;

    private RecipeGuide() {}

    private static void load() {
        if (sections != null) return;
        sections = new ArrayList<>();
        other = new ArrayList<>();
        try (var in = RecipeGuide.class.getResourceAsStream("/serantis_battle/guide_recipes.json")) {
            if (in == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var s : root.getAsJsonArray("sections")) {
                JsonObject so = s.getAsJsonObject();
                List<Entry> entries = new ArrayList<>();
                for (var e : so.getAsJsonArray("entries")) {
                    JsonObject eo = e.getAsJsonObject();
                    List<Recipe> rs = new ArrayList<>();
                    for (var r : eo.getAsJsonArray("recipes")) {
                        Recipe rec = read(r.getAsString());
                        if (rec != null) rs.add(rec);
                    }
                    if (!rs.isEmpty()) entries.add(new Entry(rs, eo.get("note").getAsString()));
                }
                if (!entries.isEmpty()) sections.add(new Section(so.get("title").getAsString(), entries));
            }
            for (var o : root.getAsJsonArray("other")) {
                JsonArray a = o.getAsJsonArray();
                other.add(new String[]{a.get(0).getAsString(), a.get(1).getAsString()});
            }
        } catch (Exception e) {
            dev.ahrivaea.Ahrivaea.LOGGER.warn("Couldn't read the recipe guide", e);
        }
    }

    private static Recipe read(String path) {
        try (var in = RecipeGuide.class.getResourceAsStream("/" + path)) {
            if (in == null) return null;                              // that mod isn't installed
            JsonObject r = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            String type = r.get("type").getAsString();
            JsonObject res = r.getAsJsonObject("result");
            ItemStack result = new ItemStack(item(res.get("id").getAsString()), res.has("count") ? res.get("count").getAsInt() : 1);
            ItemStack[] grid = new ItemStack[9];
            java.util.Arrays.fill(grid, ItemStack.EMPTY);
            switch (type) {
                case "minecraft:crafting_shaped" -> {
                    JsonObject key = r.getAsJsonObject("key");
                    JsonArray pattern = r.getAsJsonArray("pattern");
                    for (int row = 0; row < pattern.size() && row < 3; row++) {
                        String line = pattern.get(row).getAsString();
                        for (int col = 0; col < line.length() && col < 3; col++) {
                            String k = String.valueOf(line.charAt(col));
                            if (!" ".equals(k) && key.has(k)) grid[row * 3 + col] = ingredient(key.get(k));
                        }
                    }
                    return new Recipe("Crafting Table", false, grid, result);
                }
                case "minecraft:crafting_shapeless" -> {
                    JsonArray ing = r.getAsJsonArray("ingredients");
                    for (int i = 0; i < ing.size() && i < 9; i++) grid[i] = ingredient(ing.get(i));
                    return new Recipe("Crafting Table (any arrangement)", true, grid, result);
                }
                case "minecraft:smithing_transform" -> {
                    grid[0] = r.has("template") ? ingredient(r.get("template")) : ItemStack.EMPTY;
                    grid[1] = r.has("base") ? ingredient(r.get("base")) : ItemStack.EMPTY;
                    grid[2] = r.has("addition") ? ingredient(r.get("addition")) : ItemStack.EMPTY;
                    return new Recipe("Smithing Table", false, grid, result);
                }
                default -> { return null; }
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static ItemStack ingredient(JsonElement e) {
        if (e == null || e.isJsonNull()) return ItemStack.EMPTY;
        if (e.isJsonArray()) return e.getAsJsonArray().isEmpty() ? ItemStack.EMPTY : ingredient(e.getAsJsonArray().get(0));
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("item")) return new ItemStack(item(o.get("item").getAsString()));
            if (o.has("tag")) return new ItemStack(item("#" + o.get("tag").getAsString()));
            return ItemStack.EMPTY;
        }
        return new ItemStack(item(e.getAsString()));
    }

    private static Item item(String id) {
        if (id.startsWith("#")) return TAGS.getOrDefault(id.substring(1), Items.BARRIER);
        Item i = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        return i == null ? Items.BARRIER : i;
    }

    /** How tall the page is. */
    static int height() {
        load();
        int h = 0;
        for (Section s : sections) h += HEAD_H + s.entries().size() * CARD_H;
        if (!other.isEmpty()) h += HEAD_H + other.size() * OTHER_H;
        return h;
    }

    /** Draws the page with its top at y; only what fits between top and bottom is drawn. */
    static void draw(GuiGraphicsExtractor g, Font font, int x, int y, int w, int top, int bottom) {
        load();
        int frame = (int) (System.currentTimeMillis() / 1500);
        for (Section s : sections) {
            if (in(y, 10, top, bottom)) g.text(font, s.title(), x, y + 4, GOLD);
            y += HEAD_H;
            for (Entry e : s.entries()) {
                Recipe r = e.recipes().get(Math.floorMod(frame, e.recipes().size()));
                card(g, font, r, e.note(), x, y, w, top, bottom);
                y += CARD_H;
            }
        }
        if (!other.isEmpty()) {
            if (in(y, 10, top, bottom)) g.text(font, "Other ways to get things", x, y + 4, GOLD);
            y += HEAD_H;
            for (String[] o : other) {
                if (in(y, 9, top, bottom)) g.text(font, o[0], x, y, TEXT);
                List<String> rows = wrap(font, o[1], w - 4);
                for (int i = 0; i < rows.size() && i < 2; i++) if (in(y + 10 + i * 10, 9, top, bottom)) g.text(font, rows.get(i), x + 4, y + 10 + i * 10, DIM);
                y += OTHER_H + (rows.size() > 1 ? 0 : -6);
            }
        }
    }

    private static boolean in(int y, int h, int top, int bottom) { return y >= top - 1 && y + h <= bottom; }

    private static void card(GuiGraphicsExtractor g, Font font, Recipe r, String note, int x, int y, int w, int top, int bottom) {
        boolean smith = r.station().startsWith("Smithing");
        if (in(y, 9, top, bottom)) g.text(font, r.result().getHoverName().getString() + (r.result().getCount() > 1 ? " x" + r.result().getCount() : ""), x, y, TEXT);
        int gy = y + 12, gx = x;
        int slots = smith ? 3 : 9;
        for (int i = 0; i < slots; i++) {
            int sx = gx + (i % 3) * 18, sy = gy + (smith ? 0 : (i / 3) * 18);
            if (!in(sy, 18, top, bottom)) continue;
            g.fill(sx, sy, sx + 18, sy + 18, SLOT_EDGE);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
            if (!r.grid()[i].isEmpty()) g.item(r.grid()[i], sx + 1, sy + 1);
        }
        int ay = gy + (smith ? 5 : 23);
        if (in(ay, 9, top, bottom)) g.text(font, "→", gx + 58, ay, DIM);
        int rx = gx + 70, ry = gy + (smith ? 0 : 18);
        if (in(ry, 18, top, bottom)) {
            g.fill(rx, ry, rx + 18, ry + 18, 0xFF6B5420);
            g.fill(rx + 1, ry + 1, rx + 17, ry + 17, SLOT);
            g.item(r.result(), rx + 1, ry + 1);
            if (r.result().getCount() > 1) g.text(font, String.valueOf(r.result().getCount()), rx + 11, ry + 10, TEXT);
        }
        int tx = x + 96, tw = w - 96, ty = y + 12;
        List<String> lines = new ArrayList<>();
        lines.add(r.station());
        lines.add("Needs: " + needs(r));
        List<String> out = new ArrayList<>();
        for (String l : lines) out.addAll(wrap(font, l, tw));
        out.addAll(wrap(font, note, tw));
        for (int i = 0; i < out.size() && i < 6; i++) {
            int ly = ty + i * 10;
            if (in(ly, 9, top, bottom)) g.text(font, out.get(i), tx, ly, i == 0 ? 0xFF8FD3FF : i < 2 + (out.size() > 6 ? 0 : 0) && out.get(i).startsWith("Needs") ? TEXT : DIM);
        }
    }

    private static String needs(Recipe r) {
        Map<String, Integer> count = new LinkedHashMap<>();
        for (ItemStack s : r.grid()) if (!s.isEmpty()) count.merge(s.getHoverName().getString(), 1, Integer::sum);
        List<String> parts = new ArrayList<>();
        count.forEach((n, c) -> parts.add(c + " " + n));
        return String.join(", ", parts);
    }

    private static List<String> wrap(Font font, String text, int w) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (font.width(next) > w && !line.isEmpty()) { out.add(line.toString()); line = new StringBuilder(word); }
            else line = new StringBuilder(next);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }
}
