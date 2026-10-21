package dev.serantis.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lore books: every creature you beat in battle has a 1% chance to drop one, picked at random from
 * serantis_battle/lore_books.json (a list of {"title", "author", "text"}, optionally "heading" and "subtitle" for the
 * first page; blank lines in the text separate paragraphs).
 */
public final class LoreDrops {
    public static final float CHANCE = 0.025f;
    private record Book(String title, String heading, String subtitle, String author, String[] paragraphs) {}
    private static List<Book> books;

    private LoreDrops() {}

    private static synchronized List<Book> books() {
        if (books != null) return books;
        List<Book> out = new ArrayList<>();
        try (var in = LoreDrops.class.getResourceAsStream("/serantis_battle/lore_books.json")) {
            if (in != null) {
                JsonArray all = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
                for (var el : all) {
                    JsonObject o = el.getAsJsonObject();
                    String text = o.get("text").getAsString().replace("\r", "");
                    List<String> paras = new ArrayList<>();
                    for (String para : text.split("\n\\s*\n")) if (!para.isBlank()) paras.add(para.strip().replace("\n", " "));
                    String title = o.get("title").getAsString();
                    out.add(new Book(title, o.has("heading") ? o.get("heading").getAsString() : title.toUpperCase(),
                            o.has("subtitle") && !o.get("subtitle").isJsonNull() ? o.get("subtitle").getAsString() : null,
                            o.has("author") ? o.get("author").getAsString() : "Krea, the All Seeing", paras.toArray(new String[0])));
                }
            }
        } catch (Exception e) {
            dev.ahrivaea.Ahrivaea.LOGGER.warn("Serantis: couldn't read the lore books: {}", e.toString());
        }
        return books = out;
    }

    /** A lore book, 1 time in 100 (null otherwise, or if there are no books yet). */
    public static ItemStack roll(RandomSource random) { return roll(random, 1f); }

    /** As roll, with the chance multiplied (Journeymen: x2). */
    public static ItemStack roll(RandomSource random, float mult) {
        List<Book> all = books();
        if (all.isEmpty() || random.nextFloat() >= CHANCE * mult) return null;
        Book b = all.get(random.nextInt(all.size()));
        return LoreBook.book(b.title(), b.heading(), b.subtitle(), b.author(), b.paragraphs());
    }

    public static int count() { return books().size(); }
}
