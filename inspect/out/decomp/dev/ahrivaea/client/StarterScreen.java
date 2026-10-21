package dev.ahrivaea.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.Starters;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class StarterScreen extends Screen {
   static final int TOP = 14;
   private final JsonObject offer;
   private int selected;
   private CreatureEntity preview;
   private static final String[] STAT_NAMES = new String[]{"HP", "Attack", "Defense", "Speed", "Magic", "Luck", "Charisma"};
   private static final int COLS = 4;
   private static final int BTN_W = 66;
   private static final int BTN_H = 16;
   private static final int ROW = 17;
   private static final int PANEL_W = 186;
   private boolean picked;

   public StarterScreen(JsonObject offer) {
      super(Component.literal("Choose your starter"));
      this.offer = offer;
   }

   private JsonArray options() {
      return this.offer.getAsJsonArray("options");
   }

   private int gridLeft() {
      return Math.max(4, this.width / 2 - 233);
   }

   private int panelLeft() {
      return this.gridLeft() + 272 + 8;
   }

   protected void init() {
      JsonArray l = this.options();
      this.selected = Math.clamp((long)this.selected, 0, Math.max(0, l.size() - 1));
      int x0 = this.gridLeft();

      for (int i = 0; i < l.size(); i++) {
         int idx = i;
         Button b = (Button)this.addRenderableWidget(Button.builder(Component.literal(l.get(i).getAsJsonObject().get("name").getAsString()), btn -> {
            this.selected = idx;
            this.rebuildWidgets();
         }).bounds(x0 + i % 4 * 68, 38 + i / 4 * 17, 66, 16).build());
         b.active = i != this.selected;
      }

      if (!l.isEmpty()) {
         JsonObject c = l.get(this.selected).getAsJsonObject();
         this.addRenderableWidget(
            Button.builder(Component.literal("Choose " + c.get("name").getAsString()), bx -> this.confirm())
               .bounds(this.panelLeft(), this.height - 26, 186, 20)
               .build()
         );
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && !l.isEmpty()) {
         if (this.preview == null) {
            this.preview = new CreatureEntity(Ahrivaea.CREATURE, mc.level);
            this.preview.setId(-4242);
         }

         this.preview.previewAs(l.get(this.selected).getAsJsonObject().get("id").getAsString(), "none");
      }
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(g, mouseX, mouseY, partialTick);
      g.centeredText(this.font, "Choose your starter: it joins your party at level " + this.offer.get("level").getAsInt(), this.width / 2, 22, -12224);
      JsonArray l = this.options();
      if (!l.isEmpty()) {
         JsonObject c = l.get(this.selected).getAsJsonObject();
         int x = this.panelLeft();
         int y = 38;
         int w = 186;
         g.fill(x - 4, y - 4, x + w + 4, this.height - 30, Integer.MIN_VALUE);
         g.text(this.font, c.get("name").getAsString(), x, y, -1);
         String move = c.get("movement").getAsString();
         String how = (move.equals("fly") ? "Flies" : (move.equals("swim") ? "Swims" : "Walks")) + (c.get("rideable").getAsBoolean() ? ", rideable" : "");
         g.text(this.font, how, x + w - this.font.width(how), y, -6303489);
         int boxTop = y + 12;
         int boxBottom = boxTop + 66;
         if (this.preview != null) {
            String id = c.get("id").getAsString();
            float size = Math.max(0.5F, Math.max(GeoModels.height(id, "none"), GeoModels.extent(id, "none") * 0.7F) * GeoModels.scale(id, "none"));
            int px = Math.max(2, Math.round(54.0F / size));
            float h = GeoModels.height(id, "none") * GeoModels.scale(id, "none");
            InventoryScreen.extractEntityInInventoryFollowsMouse(
               g, x + w / 2 - 50, boxTop, x + w / 2 + 50, boxBottom, px, h / 2.0F - 0.4F, (float)mouseX, (float)mouseY, this.preview
            );
         }

         y = boxBottom + 4;
         JsonArray stats = c.getAsJsonArray("stats");

         for (int i = 0; i < stats.size(); i++) {
            int v = stats.get(i).getAsInt();
            g.text(this.font, STAT_NAMES[i], x, y, -1);
            PartyClient.bar(g, x + 50, y + 2, 104, 5, (float)v / 100.0F, -16320);
            g.text(this.font, String.valueOf(v), x + 160, y, -11649);
            y += 10;
         }

         g.text(this.font, "Total " + c.get("total").getAsInt() + ", moves " + c.get("squares").getAsInt() + " squares in battle", x, y + 2, -2236963);
         y += 13;

         for (FormattedCharSequence line : this.font.split(Component.literal("Jobs: " + c.get("jobs").getAsString()), w)) {
            g.text(this.font, line, x, y, -6305537);
            y += 10;
         }
      }
   }

   public void choose(String speciesId) {
      JsonArray l = this.options();

      for (int i = 0; i < l.size(); i++) {
         if (l.get(i).getAsJsonObject().get("id").getAsString().equals(speciesId)) {
            this.selected = i;
         }
      }

      this.rebuildWidgets();
   }

   public void confirm() {
      JsonArray l = this.options();
      if (!l.isEmpty()) {
         this.picked = true;
         ClientPlayNetworking.send(new Starters.PickPayload(l.get(this.selected).getAsJsonObject().get("id").getAsString()));
         this.onClose();
      }
   }

   public void onClose() {
      super.onClose();
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !this.picked) {
         mc.player.sendSystemMessage(Component.literal("Type /starter to choose your starter creature."));
      }
   }

   public boolean isPauseScreen() {
      return false;
   }
}
