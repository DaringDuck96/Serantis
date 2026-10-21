package dev.serantis.battle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/** "Serantia: A World Forged by Dragons", the book every player starts with, whatever their class (given once). */
public final class LoreBook {
    public static final String TITLE = "A World Forged by Dragons";
    public static final String HEADING = "SERANTIA: A WORLD FORGED BY DRAGONS";

    private static final String[] TEXT = {
            "Legend tells that Serantia was born by the will of the Twelve Great Dragon Lords—beings of unfathomable power whose names would be etched into the very foundations of existence. They are the creators, the sovereigns, and the eternal guardians of this world. Even now, they sit upon the Council of Eternita, their ancient authority unchallenged by time itself.",
            "Before them, there was nothing. By their will, all things came to be. And should their will ever fade, all things shall return to nothing.",
            "It is by their power, their decree, and their eternal dominion that Serantia endures—a world alive with wonder, shaped by freedom, and bound by consequence.",
            "Vast beyond imagination, Serantia is a realm of boundless possibility. Kingdoms rise from the wilderness. Civilizations flourish beneath ancient skies. Untold treasures lie buried beneath forgotten ruins, waiting for those bold enough to claim them.",
            "But where possibility knows no bounds, neither does peril.",
            "Monsters stalk the untamed wilds. Ancient evils stir beneath the earth. Dark pacts are forged in the shadows, and the line between righteousness and damnation grows ever thinner. Every kingdom harbors secrets. Every legend conceals a truth. And every choice carries the power to change the fate of the world.",
            "From the highest citadels to the darkest depths, adventure calls to all who dare to answer.",
            "Will you carve your name into legend through the clash of steel and sorcery? Will you command armies, conquer empires, and reshape the fate of nations? Perhaps you will build cities where there was once wilderness, forge alliances that endure for centuries, or uncover the forgotten mysteries of a world older than memory.",
            "Whatever your ambition, whatever your destiny, Serantia awaits.",
            "Yet heed this warning, adventurer: the world is not yet saved.",
            "For beneath its splendor, evil endures. Shadows gather beyond the reach of kings. Forces older than the kingdoms themselves stir in the darkness, and the fate of Serantia hangs upon choices yet unmade.",
            "The Twelve Great Dragon Lords may have forged this world, but its future has not been written.",
            "That burden now falls to you.",
            "Rise, adventurer. Claim your destiny. Forge your legend.",
            "SERANTIA NEEDS YOU."};

    /** Roughly what fits on one book page. */
    private static final int PAGE_CHARS = 210;

    private LoreBook() {}

    private static List<String> sentences(String paragraph) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : paragraph.split(" ")) {
            cur.append(cur.isEmpty() ? "" : " ").append(word);
            if (word.endsWith(".") || word.endsWith("?") || word.endsWith("!")) { out.add(cur.toString()); cur.setLength(0); }
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    /** The pages: the heading first, then the paragraphs, a paragraph split by sentence where it won't fit. */
    static List<String> pages(String[] text) {
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        for (String para : text) {
            String chunk = para;
            if (!page.isEmpty() && page.length() + 2 + chunk.length() <= PAGE_CHARS) { page.append("\n\n").append(chunk); continue; }
            if (chunk.length() <= PAGE_CHARS) {
                if (!page.isEmpty()) pages.add(page.toString());
                page = new StringBuilder(chunk);
                continue;
            }
            for (String s : sentences(para)) {                    // a long paragraph: as many sentences as fit per page
                if (!page.isEmpty() && page.length() + 1 + s.length() > PAGE_CHARS) { pages.add(page.toString()); page = new StringBuilder(); }
                page.append(page.isEmpty() ? "" : " ").append(s);
            }
        }
        if (!page.isEmpty()) pages.add(page.toString());
        return pages;
    }

    public static ItemStack book() { return book(TITLE, HEADING, "Serantia", TEXT); }

    /** A signed written book: a heading page, then the paragraphs laid out over as many pages as they need. */
    public static ItemStack book(String title, String heading, String author, String[] paragraphs) { return book(title, heading, null, author, paragraphs); }

    /** As above, with a line under the heading on the first page (in plain type). */
    public static ItemStack book(String title, String heading, String subtitle, String author, String[] paragraphs) {
        List<Filterable<Component>> content = new ArrayList<>();
        var first = Component.literal("\n" + heading).withStyle(ChatFormatting.BOLD);
        if (subtitle != null && !subtitle.isBlank()) first.append(Component.literal("\n\n" + subtitle).withStyle(st -> st.withBold(false)));
        content.add(Filterable.passThrough(first));
        for (String p : pages(paragraphs)) content.add(Filterable.passThrough(Component.literal(p)));
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        String shortTitle = title.length() > 32 ? title.substring(0, 31).trim() + "\u2026" : title;    // Minecraft's limit
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(shortTitle), author, 0, content, true));
        return stack;
    }

    /** Every player gets the book once (new players with their starting gear, players already here on their next login). */
    public static void give(ServerPlayer p) {
        ItemStack b = book();
        if (!p.getInventory().add(b) && !b.isEmpty())
            p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), b));
    }
}
