package dev.serantis.battle.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Naming (and colouring) a marked place on the map, or changing or removing one. */
public class WaypointScreen extends Screen {
    private final Screen parent;
    private final Waypoints.Mark mark;          // null: a new one
    private final int x, z;
    private int color;
    private EditBox box;

    public WaypointScreen(Screen parent, int x, int z, Waypoints.Mark mark) {
        super(Component.literal(mark == null ? "Mark this place" : "Edit marker"));
        this.parent = parent;
        this.mark = mark;
        this.x = mark == null ? x : mark.x;
        this.z = mark == null ? z : mark.z;
        this.color = mark == null ? Waypoints.COLORS[0] : mark.color;
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        String old = box == null ? (mark == null ? "" : mark.name) : box.getValue();
        box = new EditBox(font, cx - 100, cy - 30, 200, 20, Component.literal("Name"));
        box.setMaxLength(32);
        box.setValue(old);
        box.setHint(Component.literal("Name (Home, Village, Cave...)"));
        addRenderableWidget(box);
        setFocused(box);
        for (int i = 0; i < Waypoints.COLORS.length; i++) {
            int c = Waypoints.COLORS[i];
            Button b = Button.builder(Component.literal(c == color ? "■" : "□").withColor(c & 0xFFFFFF), btn -> { color = c; rebuildWidgets(); })
                    .bounds(cx - 100 + i * 25, cy - 4, 23, 18).build();
            addRenderableWidget(b);
        }
        int bw = mark == null ? 98 : 64;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(cx - 100, cy + 22, bw, 20).build());
        if (mark != null) addRenderableWidget(Button.builder(Component.literal("Delete"), b -> { Waypoints.remove(mark); onClose(); })
                .bounds(cx - 100 + bw + 4, cy + 22, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(cx + 100 - bw, cy + 22, bw, 20).build());
    }

    private void save() {
        String name = box.getValue().trim();
        if (name.isEmpty()) name = "Marker";
        if (mark == null) Waypoints.add(name, x, z, color);
        else { mark.name = name; mark.color = color; Waypoints.changed(); }
        onClose();
    }

    @Override
    public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        g.centeredText(font, getTitle().getString() + "  (x " + x + ", z " + z + ")", width / 2, height / 2 - 46, 0xFFF2C94C);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
