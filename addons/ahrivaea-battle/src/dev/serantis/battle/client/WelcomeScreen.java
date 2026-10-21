package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The welcome guide: shown once, the first time a player joins (over the starter screen if that's up,
 * which comes back after), and any time from the Guide button on the party screen.
 */
public class WelcomeScreen extends Screen {
    private static final int GOLD = 0xFFF2C94C, TEXT = 0xFFE6E2D8, HEAD = 0xFF8FD16A, DIM = 0xFF9AA4B4;

    /** {heading, paragraph}; a null heading is a plain paragraph. */
    private static final String[][] GUIDE = {
            {null, "Pull up a block. This is Minecraft: the same blocky world you know, with trees to punch, caves to explore and plenty of mobs to fight. "
                    + "But Serantia is alive with creatures, and they're waiting to meet you."},
            {"Choose your class", "Knight (Attack), Bulwark (Defense), Wizard (Magic), Journeyman (Luck) or Performer (Charisma). "
                    + "Your class gives its stat a boost that matters most early on, plus a head start: Knights get an iron chestplate and "
                    + "sword, Bulwarks a shield and an iron mace, Wizards a Magic Staff of their chosen influence, Journeymen two golden "
                    + "apples, and Performers choose their starter's temperament. You choose once."},
            {"Pick your starter", "Your first friend is waiting. Choose a starter creature: it fights beside you, grows with you, "
                    + "and might just carry you through your first rough night."},
            {"Battle and recruit", "Wild creatures roam every biome. Hit one with any weapon (sword, axe, bow, trident, whatever's handy) "
                    + "and a battle begins on a tactical grid. Win, and the creatures you beat can join your party for some XP. "
                    + "Common, Uncommon, Rare, Super Rare, Legendary... and once in a while, a Mutated one. Keep your eyes open."},
            {"Collect them all", "Twelve influences (Normal, Fire, Frost, Storm, Earth, Water, Nature, Void, Fae, Darkness, Arcane and Radiant) "
                    + "and hundreds of creatures and forms to find. Some fly, some swim the deep, some only show up in the strangest corners of the world. "
                    + "Your dex remembers everything you've seen."},
            {"XP is everything", "Experience isn't just for enchanting anymore. It recruits creatures, levels them up, buys from villagers "
                    + "and shady merchants, and unlocks your skill tree. You keep it when you die. Spend it wisely (or don't)."},
            {"Rebuild the towers", "Ruined towers rise over the world's strongholds. Make the first offering at one and it's yours for good: "
                    + "anyone can pitch in to rebuild it, but you claimed it. A finished tower gives you a Base Core, and your base is "
                    + "where your creatures and recruited villagers live and work for you."},
            {"Challenge the Creature Masters", "Now and then you'll meet a Creature Master, a gym leader with a team of 3 to 5 tough creatures "
                    + "of one influence. You never have to fight one. But beat one and you earn double XP and a Gym Master Stone in its colour."},
            {"Collect the stones", "Every Gym Master Stone raises your mastery of its influence, so its creatures turn up around you a little "
                    + "more often. You can hold up to 5 of each. Display them proudly."},
            {"Chase achievements", "Place blocks, win battles, plant seeds, walk ridiculous distances, die embarrassingly... nearly everything "
                    + "earns achievements, and achievements pay out in golden apples. Feed those to your creatures to make them stronger. "
                    + "Check your log any time from the party screen."},
            {"Win the game (technically)", "Gather all 5 stones of all 12 influences, 60 in all, and they fuse into the rainbow Eternita Stone. "
                    + "Mutated creatures become five times as common around you, and you've officially won the game. "
                    + "Nothing ends. You just get to say you won."},
            {"It's still Minecraft", "None of this is required. Build a castle, a farming village, a city in the clouds, or a perfectly "
                    + "ordinary dirt hut. Your creatures can work at your base and villagers can join you. Building and creativity are as "
                    + "much a part of Serantia as any battle, and spending your whole world making something beautiful is a completely "
                    + "valid way to play."},
            {null, "Ready? Go meet someone new."}};

    private static boolean shown;

    private Screen parent;

    public void setParent(Screen parent) { this.parent = parent; }
    private final boolean first;
    private int tab;
    private static final String[] TABS = {"Welcome", "Your Base"};
    private static final String[] SUBTITLES = {"A world of creatures, towers and masters (and it's still Minecraft)",
            "How creatures work at your base, and what to expect from them"};
    private final List<Object[]> lines = new ArrayList<>();      // {text, color, gapBefore}
    private int scroll, contentH, panelW, viewTop, viewBottom;

    public WelcomeScreen(Screen parent) { this(parent, false); }

    /** Opens the guide on a tab: 0 the welcome, 1 how bases and their workers work. */
    public WelcomeScreen(Screen parent, int tab) { this(parent, false); this.tab = tab; }

    private WelcomeScreen(Screen parent, boolean first) {
        super(Component.literal("Welcome to Serantia"));
        this.parent = parent;
        this.first = first;
    }

    /** Every client tick: shows the guide the first time a player joins. */
    public static void autoOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (shown || mc.player == null) return;
        JsonObject s = TradeClient.state();
        if (s == null || !s.has("serantis_welcome")) return;
        Screen open = mc.gui.screen();
        if (open != null && !(open instanceof dev.ahrivaea.client.StarterScreen)) return;
        shown = true;
        mc.gui.setScreen(new WelcomeScreen(open, true));
    }

    private List<String> wrap(String text, int w) {
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

    @Override
    protected void init() {
        panelW = Math.min(380, width - 24);
        viewTop = 64;
        viewBottom = height - 34;
        lines.clear();
        int h = 0;
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            Button b = Button.builder(Component.literal(TABS[i]), btn -> { tab = t; scroll = 0; rebuildWidgets(); })
                    .bounds(width / 2 - TABS.length * 47 + i * 94, 38, 90, 18).build();
            b.active = i != tab;
            addRenderableWidget(b);
        }
        for (String[] part : tab == 1 ? BaseGuide.TEXT : GUIDE) {
            int gap = h == 0 ? 0 : 10;
            if (part[0] != null) { lines.add(new Object[]{part[0], HEAD, gap}); h += gap + 12; gap = 0; }
            boolean firstLine = true;
            for (String l : wrap(part[1], panelW - 20)) {
                int g = firstLine ? gap : 0;
                lines.add(new Object[]{l, TEXT, g});
                h += g + 10;
                firstLine = false;
            }
        }
        contentH = h;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - (viewBottom - viewTop))));
        addRenderableWidget(Button.builder(Component.literal(first ? "Let's go!" : "Done"), b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        int max = Math.max(0, contentH - (viewBottom - viewTop));
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.round(dy * 20)));
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xF00C0B14, 0xF0181226);
        int l = (width - panelW) / 2;
        g.fill(l, viewTop - 6, l + panelW, viewBottom + 2, 0xC0141220);
        g.outline(l, viewTop - 6, panelW, viewBottom - viewTop + 8, 0xFF6B5420);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        g.pose().pushMatrix();
        g.pose().translate(width / 2f, 10);
        g.pose().scale(1.5f, 1.5f);
        g.centeredText(font, tab == 1 ? "Your Base and Its Workers" : "Welcome to Serantia!", 0, 0, GOLD);
        g.pose().popMatrix();
        g.centeredText(font, SUBTITLES[tab], width / 2, 26, DIM);

        int x = (width - panelW) / 2 + 10, y = viewTop - scroll;
        for (Object[] line : lines) {
            y += (int) line[2];
            if (y >= viewTop - 2 && y + 9 <= viewBottom) g.text(font, (String) line[0], x, y, (int) line[1]);
            y += (int) line[1] == HEAD ? 12 : 10;
        }
        int max = Math.max(0, contentH - (viewBottom - viewTop));
        if (max > 0) {                                          // a scroll bar
            int track = viewBottom - viewTop, bar = Math.max(16, track * track / (contentH + 1));
            int bx = (width + panelW) / 2 - 5, by = viewTop + (int) ((track - bar) * (scroll / (double) max));
            g.fill(bx, viewTop, bx + 3, viewBottom, 0x40FFFFFF);
            g.fill(bx, by, bx + 3, by + bar, 0xC0F2C94C);
        }
    }

    @Override
    public void onClose() {
        if (first) TradeClient.send("serantis_welcome_seen", null, null);
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
