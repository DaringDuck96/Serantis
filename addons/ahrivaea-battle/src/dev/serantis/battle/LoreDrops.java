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
 * serantis_battle/lore_books.json (a list of {"title", "author", "text"}; blank lines in the text separate paragraphs).
 */
public final class LoreDrops {
    public static final float CHANCE = 0.01f;
    private record Book(String title, String author, String[] paragraphs) {}
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
                    out.add(new Book(o.get("title").getAsString(), o.has("author") ? o.get("author").getAsString() : "Unknown", paras.toArray(new String[0])));
                }
            }
        } catch (Exception e) {
            dev.ahrivaea.Ahrivaea.LOGGER.warn("Serantis: couldn't read the lore books: {}", e.toString());
        }
        return books = out;
    }

    /** A lore book, 1 time in 100 (null otherwise, or if there are no books yet). */
    public static ItemStack roll(RandomSource random) {
        List<Book> all = books();
        if (all.isEmpty() || random.nextFloat() >= CHANCE) return null;
        Book b = all.get(random.nextInt(all.size()));
        return LoreBook.book(b.title(), b.title().toUpperCase(), b.author(), b.paragraphs());
    }

    public static int count() { return books().size(); }
}
