package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A co-op question for this player: a group invite, a spar challenge, or a group member's battle to join. */
public class CoopPromptScreen extends Screen {
    private static String shown = "";

    public static void resetForNewWorld() { shown = ""; }
    private final int id;
    private final long opened = System.currentTimeMillis();
    private int startLeft = -1;
    private boolean answered;

    public CoopPromptScreen(int id) {
        super(Component.literal("Co-op"));
        this.id = id;
    }

    private static JsonObject prompt() {
        JsonObject s = TradeClient.state();
        if (s == null || !s.has("serantis_coop")) return null;
        JsonObject c = s.getAsJsonObject("serantis_coop");
        return c.has("prompt") ? c.getAsJsonObject("prompt") : null;
    }

    /** Every client tick: pop up a new question when nothing else is open. */
    public static void autoOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null || dev.ahrivaea.client.battle.BattleClient.active()) return;
        JsonObject p = prompt();
        if (p == null) return;
        int id = p.get("id").getAsInt();
        String key = id + "|" + p.get("from").getAsString() + "|" + p.get("kind").getAsString();
        if (key.equals(shown)) return;
        shown = key;
        mc.gui.setScreen(new CoopPromptScreen(id));
    }

    private void answer(boolean yes) {
        if (answered) return;
        answered = true;
        JsonObject o = new JsonObject();
        o.addProperty("kind", "serantis_coop_answer");
        o.addProperty("id", id);
        o.addProperty("yes", yes);
        dev.ahrivaea.client.battle.BattleClient.send(o);
        Minecraft.getInstance().gui.setScreen(null);
    }

    private String kind() { JsonObject p = prompt(); return p == null ? "" : p.get("kind").getAsString(); }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        String yes = switch (kind()) { case "spar" -> "Accept the spar"; case "join" -> "Join the battle"; default -> "Join the group"; };
        addRenderableWidget(Button.builder(Component.literal(yes), b -> answer(true)).bounds(cx - 122, cy + 10, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("No thanks"), b -> answer(false)).bounds(cx + 2, cy + 10, 120, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        JsonObject p = prompt();
        if (!answered && (p == null || p.get("id").getAsInt() != id || secondsLeft() <= 0)) Minecraft.getInstance().gui.setScreen(null);
    }

    private int secondsLeft() {
        JsonObject p = prompt();
        if (startLeft < 0 && p != null) startLeft = p.get("secondsLeft").getAsInt();
        return startLeft < 0 ? 30 : startLeft - (int) ((System.currentTimeMillis() - opened) / 1000);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int cx = width / 2, cy = height / 2;
        g.fill(cx - 140, cy - 40, cx + 140, cy + 40, 0xE014161C);
        g.outline(cx - 140, cy - 40, 280, 80, 0xFF3C5A70);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        JsonObject p = prompt();
        String who = p != null ? p.get("from").getAsString() : "Someone";
        String line = switch (kind()) {
            case "spar" -> who + " challenges you to a friendly spar!";
            case "join" -> who + " is in a battle nearby. Join in?";
            default -> who + " invites you to their group.";
        };
        String sub = switch (kind()) {
            case "spar" -> "No XP or loot, and no real harm to anyone.";
            case "join" -> "You bring yourself and up to 2 creatures.";
            default -> "Join each other's battles, share bases and a group contract.";
        };
        g.centeredText(font, line, width / 2, height / 2 - 28, 0xFFFFFFFF);
        g.centeredText(font, sub + " (" + Math.max(0, secondsLeft()) + "s)", width / 2, height / 2 - 14, 0xFFA0A8B8);
    }

    @Override
    public void onClose() { answer(false); }

    @Override
    public boolean isPauseScreen() { return false; }
}
