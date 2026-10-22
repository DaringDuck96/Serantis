package dev.serantis.battle.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A conversation with a Creature Master, shown as a cutscene: the camera frames whoever is speaking
 * (DialogueCamera) and their words appear in a subtitle bar. Each Master has five conversations
 * (serantis_battle/master_dialogue.json); one is picked at random each time you right-click them. They end with
 * your choice: challenge them (the battle starts) or walk away.
 */
public class DialogueScreen extends Screen {
    private static final int GOLD = 0xFFF2C94C, TEXT = 0xFFF0ECE4, YOU = 0xFF8FD1E8, DIM = 0xFF9AA4B4;
    private static JsonObject all;

    private final Entity master;
    private final String title;
    private final JsonObject convo;
    private final List<String[]> lines = new ArrayList<>();     // {speaker "master"/"player", text}
    private int index;
    private long shownAt;
    private boolean choosing, challenged, done;
    private boolean villager;                                    // a villager's talk (see VillagerTalk), not a Master's
    private static JsonObject villagers;
    private static String lastTalk = "";
    private int warnLevel = -1;                                  // a new player's level, for the "villagers are tough" warning

    private DialogueScreen(Entity master, String title, JsonObject convo) {
        super(Component.literal(title));
        this.master = master;
        this.title = title;
        this.convo = convo;
        for (var l : convo.getAsJsonArray("lines")) {
            JsonArray a = l.getAsJsonArray();
            lines.add(new String[]{a.get(0).getAsString(), a.get(1).getAsString()});
        }
    }

    private static JsonObject dialogue() {
        if (all == null) {
            try (var in = DialogueScreen.class.getResourceAsStream("/serantis_battle/master_dialogue.json")) {
                all = in == null ? new JsonObject() : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                all = new JsonObject();
            }
        }
        return all;
    }

    /** The server says a villager wants a word before the fight: one of its profession's five conversations. */
    public static void autoOpen() {
        Minecraft mc = Minecraft.getInstance();
        JsonObject s = TradeClient.state();
        JsonObject t = s != null && s.has("serantis_villager_talk") ? s.getAsJsonObject("serantis_villager_talk") : null;
        if (t == null) { lastTalk = ""; return; }
        if (mc.player == null || mc.level == null || mc.gui.screen() != null || t.toString().equals(lastTalk)) return;
        Entity e = mc.level.getEntity(t.get("entity").getAsInt());
        if (e == null) return;
        if (villagers == null) {
            try (var in = DialogueScreen.class.getResourceAsStream("/serantis_battle/villager_dialogue.json")) {
                villagers = in == null ? new JsonObject() : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ex) {
                villagers = new JsonObject();
            }
        }
        String key = t.get("key").getAsString();
        if (!villagers.has(key)) return;
        lastTalk = t.toString();
        JsonObject v = villagers.getAsJsonObject(key);
        JsonArray convos = v.getAsJsonArray("dialogues");
        JsonObject convo = convos.get(e.level().getRandom().nextInt(convos.size())).getAsJsonObject();
        String look = VillagerLooks.look("none".equals(key) ? null : key);
        float face = e.getBbHeight() * 0.85f;
        try {
            float h = dev.ahrivaea.client.GeoModels.height("villager", look == null ? "villager" : look);
            if (h > face) face = h * 0.88f;
        } catch (RuntimeException ignored) { }
        DialogueCamera.start(e, face);
        DialogueScreen screen = new DialogueScreen(e, v.get("title").getAsString(), convo);
        screen.villager = true;
        if (t.has("warn")) screen.warnLevel = t.get("warn").getAsInt();
        mc.gui.setScreen(screen);
    }

    /** Right-clicked a Creature Master of this influence: one of its five conversations, at random. */
    public static boolean open(Entity master, String influence) {
        JsonObject d = dialogue();
        if (influence == null || !d.has(influence)) return false;
        JsonObject m = d.getAsJsonObject(influence);
        JsonArray convos = m.getAsJsonArray("dialogues");
        if (convos.isEmpty()) return false;
        JsonObject convo = convos.get(master.level().getRandom().nextInt(convos.size())).getAsJsonObject();
        float face = master.getBbHeight() * 0.85f;
        try {
            float h = dev.ahrivaea.client.GeoModels.height("villager", "master_" + influence);
            if (h > face) face = h * 0.88f;
        } catch (RuntimeException ignored) { }
        DialogueCamera.start(master, face);
        Minecraft.getInstance().gui.setScreen(new DialogueScreen(master, m.get("title").getAsString(), convo));
        return true;
    }

    private void show(int i) {
        index = i;
        shownAt = System.currentTimeMillis();
        DialogueCamera.focusPlayer(index < lines.size() && "player".equals(lines.get(index)[0]));
        rebuildWidgets();
    }

    @Override
    protected void init() {
        if (shownAt == 0) shownAt = System.currentTimeMillis();
        if (!choosing) return;
        int bw = Math.min(220, width / 2 - 20), y = height - 46;
        addRenderableWidget(Button.builder(Component.literal(convo.get("challenge").getAsString()), b -> pick(true))
                .bounds(width / 2 - bw - 6, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal(convo.get("decline").getAsString()), b -> pick(false))
                .bounds(width / 2 + 6, y, bw, 20).build());
    }

    private void pick(boolean challenge) {
        choosing = false;
        challenged = challenge;
        lines.add(new String[]{"player", convo.get(challenge ? "challenge" : "decline").getAsString()});
        lines.add(new String[]{"master", convo.get(challenge ? "accept" : "farewell").getAsString()});
        done = true;
        show(index + 1);
    }

    private int visibleChars() {
        return (int) Math.min(Integer.MAX_VALUE, (System.currentTimeMillis() - shownAt) * 45 / 1000);
    }

    private void advance() {
        if (choosing) return;
        String text = lines.get(index)[1];
        if (visibleChars() < text.length()) { shownAt = System.currentTimeMillis() - 100_000; return; }   // show the rest of the line
        if (index + 1 < lines.size()) { show(index + 1); return; }
        if (!done) { choosing = true; DialogueCamera.focusPlayer(true); rebuildWidgets(); return; }
        finish();
    }

    private void finish() {
        if (villager) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", "serantis_villager_answer");
            o.addProperty("fight", challenged);
            dev.ahrivaea.client.battle.BattleClient.send(o);
            DialogueCamera.end(!challenged);
            Minecraft.getInstance().gui.setScreen(null);
            return;
        }
        if (challenged) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", "serantis_master_challenge");
            o.addProperty("entity", master.getId());
            dev.ahrivaea.client.battle.BattleClient.send(o);
            DialogueCamera.end(false);                     // the battle camera takes over
        } else DialogueCamera.end(true);
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        advance();
        return true;
    }

    @Override
    public void onClose() {
        if (villager) {                                          // walked away: no fight
            JsonObject o = new JsonObject();
            o.addProperty("kind", "serantis_villager_answer");
            o.addProperty("fight", false);
            dev.ahrivaea.client.battle.BattleClient.send(o);
        }
        DialogueCamera.end(true);
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int top = height - 78;
        g.fillGradient(0, top - 30, width, top, 0x00000000, 0xA0000000);   // the world stays visible above
        g.fill(0, top, width, height, 0xC8080608);
        g.fill(0, 0, width, 18, 0x90000000);                                 // letterbox
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int top = height - 78, l = Math.max(12, width / 2 - 200), w = Math.min(400, width - 24);
        if (choosing) {
            g.text(font, "What do you say?", l, top + 10, DIM);
            if (villager && warnLevel >= 0) {
                String warn = "⚠ Villagers are tough, and town guards may join in. Best fought from player level 5 (you're level " + warnLevel + ": learn skills at your Base Core).";
                var rows = wrap(warn, w);
                int y = top - 6 - 11 * rows.size();                     // just above the dialogue bar
                g.fill(l - 4, y - 3, l + w + 4, top - 3, 0xC0200808);
                for (String row : rows) { g.text(font, row, l, y, 0xFFFF7A6A); y += 11; }
            }
            return;
        }
        String[] line = lines.get(index);
        boolean you = "player".equals(line[0]);
        var player = Minecraft.getInstance().player;
        g.text(font, you ? (player == null ? "You" : player.getPlainTextName()) : title, l, top + 8, you ? YOU : GOLD);
        String text = line[1];
        int n = Math.min(text.length(), visibleChars());
        int y = top + 22;
        for (String row : wrap(text.substring(0, n), w)) {
            g.text(font, row, l, y, TEXT);
            y += 11;
        }
        if (n >= text.length() && (System.currentTimeMillis() / 500) % 2 == 0)
            g.text(font, "▼ click to continue", l + w - font.width("▼ click to continue"), height - 14, DIM);
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
    public boolean isPauseScreen() { return false; }
}
