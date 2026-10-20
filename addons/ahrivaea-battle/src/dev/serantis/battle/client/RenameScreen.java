package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import dev.ahrivaea.client.battle.BattleClient;
import dev.serantis.battle.Naming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Offered right after recruiting a creature or picking a starter: give it a name, or keep its species name. */
public class RenameScreen extends Screen {
    private Screen parent;
    private final String species;
    private EditBox box;
    private boolean sent;

    public RenameScreen(Screen parent, String species) {
        super(Component.literal("Name your " + species + "?"));
        this.parent = parent;
        this.species = species;
    }

    /** The battle screen can be rebuilt while this is open; return to the newest one. */
    public void setParent(Screen parent) { this.parent = parent; }

    public static void open(Screen parent, String species) {
        Minecraft.getInstance().gui.setScreen(new RenameScreen(parent, species));
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        box = new EditBox(font, cx - 100, cy - 10, 200, 20, Component.literal("Name"));
        box.setMaxLength(Naming.MAX_LENGTH);
        box.setValue(species);
        addRenderableWidget(box);
        setFocused(box);
        addRenderableWidget(Button.builder(Component.literal("Name it"), b -> finish(box.getValue())).bounds(cx - 102, cy + 18, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Keep \"" + shortName() + "\""), b -> finish("")).bounds(cx + 2, cy + 18, 100, 20).build());
    }

    private String shortName() {
        return species.length() > 12 ? species.substring(0, 11) + "…" : species;
    }

    private void finish(String name) {
        if (!sent) {
            sent = true;
            String clean = Naming.clean(name);
            JsonObject o = new JsonObject();
            o.addProperty("kind", "serantis_rename");
            o.addProperty("name", clean.equals(species) ? "" : clean);
            BattleClient.send(o);
        }
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void onClose() { finish(""); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        String t = getTitle().getString();
        g.text(font, t, width / 2 - font.width(t) / 2, height / 2 - 28, 0xFFFFFFFF);
        String hint = "Up to " + Naming.MAX_LENGTH + " characters.";
        g.text(font, hint, width / 2 - font.width(hint) / 2, height / 2 + 44, 0xFFA0A0A0);
    }
}
