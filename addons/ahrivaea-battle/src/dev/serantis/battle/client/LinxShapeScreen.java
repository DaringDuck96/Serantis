package dev.serantis.battle.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The Helm of Linx's Shape Lift menu: pick a shape and a size, or put the lift away. */
public class LinxShapeScreen extends Screen {
    private static final String[] NAMES = {"Sphere", "Cube", "Diamond", "Pillar", "Disc"};
    private static int size = 5;
    private static int shape;

    public LinxShapeScreen() { super(Component.literal("Shape Lift")); }

    @Override
    protected void init() {
        int cx = width / 2, top = height / 2 - 60;
        for (int i = 0; i < NAMES.length; i++) {
            int s = i;
            Button b = Button.builder(Component.literal(NAMES[i]), x -> { shape = s; rebuildWidgets(); }).bounds(cx - 160 + i * 64, top + 24, 60, 20).build();
            b.active = shape != i;
            addRenderableWidget(b);
        }
        int[] sizes = {3, 5, 7};
        for (int i = 0; i < sizes.length; i++) {
            int z = sizes[i];
            Button b = Button.builder(Component.literal(z + " across"), x -> { size = z; rebuildWidgets(); }).bounds(cx - 100 + i * 68, top + 60, 64, 20).build();
            b.active = size != z;
            addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.literal("Lift with this"), x -> {
            SerantisKeys.send(2, false, dev.serantis.battle.Linx.SHAPES[shape], size);
            SerantisKeys.shapePicked = true;
            onClose();
        }).bounds(cx - 102, top + 100, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Put away"), x -> {
            SerantisKeys.send(2, false, "stop", 0);
            SerantisKeys.shapePicked = false;
            onClose();
        }).bounds(cx + 2, top + 100, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int top = height / 2 - 60;
        g.centeredText(font, "Shape Lift", width / 2, top, 0xFF6FE3FF);
        g.centeredText(font, "Natural blocks only: no water, lava, bedrock or anything with a block entity.", width / 2, top + 132, 0xFF9AA4B4);
        g.centeredText(font, "Press the key to lift, again to set down. Sneak + the key: this menu.", width / 2, top + 144, 0xFF9AA4B4);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
