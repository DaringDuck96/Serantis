package dev.serantis.battle.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.ahrivaea.client.base.BaseScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** The player skill tree, opened from your Base Core. Six branches, six nodes each, bought with XP. */
public class SkillTreeScreen extends Screen {
    private static final List<String> BRANCHES = List.of("Might", "Guard", "Vitality", "Arcana", "Swiftness", "Fortune", "Industry");
    private BaseScreen parent;

    public SkillTreeScreen(BaseScreen parent) {
        super(Component.literal("Skill Tree"));
        this.parent = parent;
    }

    /** The base screen is rebuilt whenever the base sends news (e.g. after buying); keep the newest. */
    public void setParent(BaseScreen next) {
        this.parent = next;
        clearWidgets();
        init();
    }

    private JsonObject data() {
        JsonObject st = parent == null ? null : parent.state();
        return st != null && st.has("serantis_skills") ? st.getAsJsonObject("serantis_skills") : null;
    }

    @Override
    protected void init() {
        JsonObject d = data();
        int colW = Math.min(112, (width - 20) / BRANCHES.size());
        int left = (width - colW * BRANCHES.size()) / 2;
        int top = 62;
        if (d != null) {
            for (JsonElement el : d.getAsJsonArray("nodes")) {
                JsonObject n = el.getAsJsonObject();
                int col = BRANCHES.indexOf(n.get("branch").getAsString());
                int row = n.get("index").getAsInt() - 1;
                if (col < 0) continue;
                boolean owned = n.get("owned").getAsBoolean();
                String why = n.has("why") ? n.get("why").getAsString() : null;
                String id = n.get("id").getAsString();
                StringBuilder tip = new StringBuilder(n.get("name").getAsString()).append("\n").append(n.get("desc").getAsString());
                tip.append("\nNeeds base Level ").append(n.get("tier").getAsInt());
                for (JsonElement r : n.getAsJsonArray("requires")) tip.append("\nNeeds ").append(r.getAsString());
                if (owned) tip.append("\nLearned.");
                else if (why != null) tip.append("\n").append(why);
                else tip.append("\nCosts ").append(d.get("next").getAsInt()).append(" XP. Click to learn.");
                Button b = Button.builder(Component.literal((owned ? "✔ " : "") + n.get("name").getAsString()), btn -> buy(id))
                        .bounds(left + col * colW + 3, top + row * 28, colW - 6, 20)
                        .tooltip(Tooltip.create(Component.literal(tip.toString())))
                        .build();
                b.active = !owned && why == null;
                addRenderableWidget(b);
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> Minecraft.getInstance().gui.setScreen(parent))
                .bounds(width / 2 - 40, height - 26, 80, 20).build());
    }

    private void buy(String id) {
        if (parent != null) parent.send("serantis_skill", 0, id);
    }

    @Override
    public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        JsonObject d = data();
        String head = "Skill Tree";
        g.text(font, head, width / 2 - font.width(head) / 2, 8, 0xFFFFFFFF);
        if (d == null) return;
        String line = "XP: " + d.get("xp").getAsInt() + "   Next skill: " + d.get("next").getAsInt() + " XP   Learned: "
                + d.get("count").getAsInt() + " / " + d.get("total").getAsInt() + "   Base Level " + d.get("baseLevel").getAsInt();
        g.text(font, line, width / 2 - font.width(line) / 2, 22, 0xFFE0D080);
        String sum = d.get("summary").getAsString();
        g.text(font, sum, width / 2 - font.width(sum) / 2, 34, 0xFFA0C0FF);
        int colW = Math.min(112, (width - 20) / BRANCHES.size());
        int left = (width - colW * BRANCHES.size()) / 2;
        for (int i = 0; i < BRANCHES.size(); i++) {
            String b = BRANCHES.get(i);
            g.text(font, b, left + i * colW + colW / 2 - font.width(b) / 2, 50, 0xFFFFFFFF);
        }
    }
}
