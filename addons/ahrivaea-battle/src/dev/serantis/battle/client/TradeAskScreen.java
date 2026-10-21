package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** After right-clicking another player: trade creatures, invite them to your group, or challenge them to a spar. */
public class TradeAskScreen extends Screen {
    private final String name, uuid;

    public TradeAskScreen(String name, String uuid) {
        super(Component.literal(name));
        this.name = name;
        this.uuid = uuid;
    }

    private boolean inMyGroup() {
        JsonObject s = TradeClient.state();
        if (s == null || !s.has("serantis_coop")) return false;
        for (var el : s.getAsJsonObject("serantis_coop").getAsJsonArray("members"))
            if (uuid.equals(el.getAsJsonObject().get("uuid").getAsString())) return true;
        return false;
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        addRenderableWidget(Button.builder(Component.literal("Trade creatures"), b -> {
            TradeClient.send("serantis_trade_request", "target", uuid);
            onClose();
        }).bounds(cx - 122, cy - 8, 120, 20).build());
        Button invite = Button.builder(Component.literal(inMyGroup() ? "In your group" : "Invite to group"), b -> {
            TradeClient.send("serantis_coop_invite", "target", uuid);
            onClose();
        }).bounds(cx + 2, cy - 8, 120, 20).build();
        invite.active = !inMyGroup();
        addRenderableWidget(invite);
        addRenderableWidget(Button.builder(Component.literal("Challenge to a spar"), b -> {
            TradeClient.send("serantis_coop_spar", "target", uuid);
            onClose();
        }).bounds(cx - 122, cy + 16, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(cx + 2, cy + 16, 120, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int cx = width / 2, cy = height / 2;
        g.fill(cx - 132, cy - 44, cx + 132, cy + 46, 0xE014161C);
        g.outline(cx - 132, cy - 44, 264, 90, 0xFF3C4560);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        g.centeredText(font, name, width / 2, height / 2 - 34, 0xFFFFFFFF);
        g.centeredText(font, "Group members can join each other's battles.", width / 2, height / 2 - 22, 0xFFA0A8B8);
    }

    @Override
    public void onClose() { Minecraft.getInstance().gui.setScreen(null); }

    @Override
    public boolean isPauseScreen() { return false; }
}
