package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;

/**
 * The base screen's Look picker: give an Overworld base the grass, leaf and water colours of any Overworld biome
 * (see BaseLooks). Each choice shows its three colours; "Its own biome" puts it back.
 */
public class BaseLookScreen extends Screen {
    private static final int COL_W = 170, ROW_H = 22, SWATCH = 9;
    private final Screen parent;
    private final BlockPos core;
    private String current;
    private int page;
    private final List<int[]> swatches = new ArrayList<>();      // x, y, grass, foliage, water

    public BaseLookScreen(Screen parent, BlockPos core, String current) {
        super(Component.literal("Base look"));
        this.parent = parent;
        this.core = core;
        this.current = current == null ? "" : current;
    }

    private int perPage() {
        int rows = Math.max(3, (height - 100) / ROW_H);
        int cols = width >= 2 * COL_W + 20 ? 2 : 1;
        return rows * cols;
    }

    @Override
    protected void init() {
        swatches.clear();
        List<String> all = new ArrayList<>();
        all.add("");
        all.addAll(dev.serantis.battle.BaseLooks.BIOMES);
        int per = perPage(), cols = width >= 2 * COL_W + 20 ? 2 : 1, rows = per / cols;
        int pages = (all.size() + per - 1) / per;
        page = Math.max(0, Math.min(page, pages - 1));
        int left = width / 2 - cols * COL_W / 2, top = 44;
        for (int i = 0; i < per && page * per + i < all.size(); i++) {
            String id = all.get(page * per + i);
            int col = i / rows, row = i % rows;
            int x = left + col * COL_W, y = top + row * ROW_H;
            String name = id.isEmpty() ? "Its own biome" : Component.translatable("biome.minecraft." + id).getString();
            boolean on = id.equals(current);
            addRenderableWidget(Button.builder(Component.literal((on ? "» " : "") + name), b -> pick(id))
                    .bounds(x + 3 * (SWATCH + 2) + 4, y, COL_W - 3 * (SWATCH + 2) - 10, 20).build());
            Biome biome = id.isEmpty() ? null : BaseLooksClient.biome(id);
            if (biome != null) swatches.add(new int[]{x, y + 5, biome.getGrassColor(0, 0), biome.getFoliageColor(), biome.getWaterColor()});
        }
        int by = height - 28;
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(width / 2 - 104, by, 30, 20).build()).active = page > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(width / 2 + 74, by, 30, 20).build()).active = page < pages - 1;
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 40, by, 80, 20).build());
    }

    private void pick(String id) {
        current = id;
        JsonObject o = new JsonObject();
        o.addProperty("kind", "serantis_base_look");
        o.addProperty("x", core.getX());
        o.addProperty("y", core.getY());
        o.addProperty("z", core.getZ());
        o.addProperty("biome", id);
        dev.ahrivaea.client.battle.BattleClient.send(o);
        rebuildWidgets();
    }

    @Override
    public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        g.centeredText(font, "Base look", width / 2, 12, 0xFFF2C94C);
        g.centeredText(font, "Grass, leaf and water colours across the whole base. Only the look changes.", width / 2, 26, 0xFFB8B8B8);
        for (int[] s : swatches)
            for (int k = 0; k < 3; k++) {
                int x = s[0] + k * (SWATCH + 2);
                g.fill(x - 1, s[1] - 1, x + SWATCH + 1, s[1] + SWATCH + 1, 0xFF202020);
                g.fill(x, s[1], x + SWATCH, s[1] + SWATCH, 0xFF000000 | (s[2 + k] & 0xFFFFFF));
            }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
