package dev.serantis.battle.client;

import dev.serantis.battle.Equipment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

/** Picking gear in battle (it takes your attack this turn). Calls back with (inventory slot or -1, hand 0/1). */
public class EquipScreen extends Screen {
    private static final int W = 300, GOLD = 0xFFF2C94C, DIM = 0xFF9AA4B4;
    private final Screen parent;
    private final BiConsumer<Integer, Integer> pick;

    public EquipScreen(Screen parent, BiConsumer<Integer, Integer> pick) {
        super(Component.literal("Change gear"));
        this.parent = parent;
        this.pick = pick;
    }

    private void choose(int slot, int hand) {
        Minecraft.getInstance().gui.setScreen(parent);
        pick.accept(slot, hand);
    }

    @Override
    protected void init() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        Inventory inv = player.getInventory();
        int l = (width - W) / 2, y = 52, row = 0;
        for (int slot = 0; slot < 36 && row < 12; slot++) {
            ItemStack s = inv.getItem(slot);
            if (!Equipment.battleGear(s) || s == player.getMainHandItem()) continue;
            final int sl = slot;
            boolean shield = Equipment.isShield(s), offOk = shield || dev.serantis.battle.Staffs.isStaff(s);
            String name = s.getHoverName().getString();
            Button main = Button.builder(Component.literal(name), b -> choose(sl, shield ? 1 : 0))
                    .bounds(l, y + row * 22, W - (offOk && !shield ? 64 : 0), 20)
                    .tooltip(Tooltip.create(Component.literal(shield ? "Into your off hand: +" + Equipment.SHIELD_DEFENSE + " Defense." : "Into your main hand."))).build();
            addRenderableWidget(main);
            if (offOk && !shield)
                addRenderableWidget(Button.builder(Component.literal("Off hand"), b -> choose(sl, 1)).bounds(l + W - 60, y + row * 22, 60, 20).build());
            row++;
        }
        int by = y + Math.max(1, row) * 22 + 10;
        Button emptyMain = Button.builder(Component.literal("Empty main hand"), b -> choose(-1, 0)).bounds(l, by, W / 2 - 2, 20).build();
        emptyMain.active = !player.getMainHandItem().isEmpty();
        addRenderableWidget(emptyMain);
        Button emptyOff = Button.builder(Component.literal("Empty off hand"), b -> choose(-1, 1)).bounds(l + W / 2 + 2, by, W / 2 - 2, 20).build();
        emptyOff.active = !player.getOffhandItem().isEmpty();
        addRenderableWidget(emptyOff);
        addRenderableWidget(Button.builder(Component.literal("Never mind"), b -> Minecraft.getInstance().gui.setScreen(parent))
                .bounds(width / 2 - 50, by + 26, 100, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xD0080A10, 0xD0101420);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        var player = Minecraft.getInstance().player;
        g.centeredText(font, "Change gear (instead of attacking this turn)", width / 2, 12, GOLD);
        if (player == null) return;
        g.centeredText(font, "Main hand: " + Equipment.handName(player.getMainHandItem()) + "    Off hand: " + Equipment.handName(player.getOffhandItem()),
                width / 2, 28, DIM);
    }

    @Override
    public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
