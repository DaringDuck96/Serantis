package dev.serantis.battle.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;

/**
 * Right-clicked food (or another consumable) in your inventory: eat or drink it yourself, or give it to one of your
 * creatures (see Feeding on the server). A golden apple opens the stat picker for the creature you choose.
 */
public class FeedScreen extends Screen {
    private static final int GOLD = 0xFFF2C94C, DIM = 0xFF9AA4B4, W = 240;
    private final Screen parent;
    private final int slot;
    private ItemStack seen = ItemStack.EMPTY;

    public FeedScreen(Screen parent, int slot) {
        super(Component.literal("Use"));
        this.parent = parent;
        this.slot = slot;
    }

    private ItemStack item() {
        var p = Minecraft.getInstance().player;
        return p == null || slot < 0 || slot >= p.getInventory().getContainerSize() ? ItemStack.EMPTY : p.getInventory().getItem(slot);
    }

    /** Can the right-click menu handle this item? */
    public static boolean usable(ItemStack s) {
        return !s.isEmpty() && (s.has(DataComponents.CONSUMABLE) || dev.serantis.battle.Feeding.feedable(s));
    }

    private static void send(String kind, int slot, int party, int entity) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        o.addProperty("slot", slot);
        o.addProperty("party", party);
        o.addProperty("entity", entity);
        dev.ahrivaea.client.battle.BattleClient.send(o);
    }

    @Override
    protected void init() {
        ItemStack s = item();
        seen = s.copy();
        if (s.isEmpty()) return;
        int l = (width - W) / 2, y = height / 2 - 70;
        var c = s.get(DataComponents.CONSUMABLE);
        boolean apple = s.is(Items.GOLDEN_APPLE), feedable = dev.serantis.battle.Feeding.feedable(s);
        if (c != null) {
            boolean drink = c.animation() == ItemUseAnimation.DRINK;
            addRenderableWidget(Button.builder(Component.literal(drink ? "Drink it yourself" : "Eat it yourself"), b -> {
                send("serantis_eat", slot, -1, -1);
                onClose();
            }).bounds(l, y + 24, W, 20).build());
        }
        int by = y + 50;
        if (apple || feedable) {
            JsonArray party = TradeClient.party();
            for (int i = 0; i < party.size() && i < 6; i++) {
                JsonObject cr = party.get(i).getAsJsonObject();
                int hp = cr.has("hp") ? cr.get("hp").getAsInt() : 0, max = cr.has("maxHp") ? cr.get("maxHp").getAsInt() : 0;
                String name = cr.has("serantis_nickname") && !cr.get("serantis_nickname").getAsString().isEmpty() ? cr.get("serantis_nickname").getAsString()
                        : cr.has("name") ? cr.get("name").getAsString() : "Creature";
                int ps = i;
                Button b = Button.builder(Component.literal((apple ? "Give to " : "Feed to ") + name + (apple ? "" : "  (" + hp + "/" + max + " HP)")), x -> {
                    if (apple) {
                        Minecraft.getInstance().gui.setScreen(new AppleScreen(parent, () -> TradeClient.party().size() > ps ? TradeClient.party().get(ps).getAsJsonObject() : null, ps));
                        return;
                    }
                    send("serantis_feed", slot, ps, -1);
                    onClose();
                }).bounds(l, by, W, 20).build();
                b.active = apple || hp < max;
                addRenderableWidget(b);
                by += 22;
            }
            if (!apple) {                                               // creatures of yours that are out nearby
                JsonObject st = TradeClient.state();
                JsonArray out = st != null && st.has("out") && st.get("out").isJsonArray() ? st.getAsJsonArray("out") : new JsonArray();
                for (int i = 0; i < out.size() && by < height - 50; i++) {
                    JsonObject cr = out.get(i).getAsJsonObject();
                    int hp = cr.has("hp") ? cr.get("hp").getAsInt() : 0, max = cr.has("maxHp") ? cr.get("maxHp").getAsInt() : 0;
                    int id = cr.has("entity") ? cr.get("entity").getAsInt() : -1;
                    Button b = Button.builder(Component.literal("Feed to " + (cr.has("name") ? cr.get("name").getAsString() : "Creature") + " (out)  (" + hp + "/" + max + " HP)"), x -> {
                        send("serantis_feed", slot, -1, id);
                        onClose();
                    }).bounds(l, by, W, 20).build();
                    b.active = hp < max && id >= 0;
                    addRenderableWidget(b);
                    by += 22;
                }
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(l, by + 6, W, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (!ItemStack.matches(item(), seen)) { if (item().isEmpty()) onClose(); else rebuildWidgets(); }   // used up, or changed
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        ItemStack s = item();
        int l = (width - W) / 2, y = height / 2 - 70;
        g.item(s, l, y);
        g.text(font, s.getHoverName().getString(), l + 22, y + 4, GOLD);
        if (dev.serantis.battle.Feeding.feedable(s) && !s.is(Items.GOLDEN_APPLE)) g.text(font, "Heals a creature outside battle.", l, y + 47 - 11, DIM);
    }

    @Override
    public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
