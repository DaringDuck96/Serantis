package dev.ahrivaea.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.Dex;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class DexScreen extends Screen {
   static final int TOP = 14;
   private static final int ROW = 14;
   private static final int LIST_W = 176;
   private static final int PANEL_W = 236;
   private static final String[] STAT_NAMES = new String[]{"HP", "Attack", "Defense", "Speed", "Magic", "Luck", "Charisma"};
   private static DexScreen.Filter filter = DexScreen.Filter.ALL;
   private static int selected = 0;
   private JsonObject data;
   private int scroll;
   private String search = "";
   private EditBox searchBox;
   private boolean searching;
   private CreatureEntity preview;

   public DexScreen(JsonObject data) {
      super(Component.literal("Creature Dex"));
      this.data = data;
   }

   public static void open() {
      Minecraft mc = Minecraft.getInstance();
      mc.gui.setScreen(new DexScreen(null));
      ClientPlayNetworking.send(new Dex.RequestPayload());
   }

   void setData(JsonObject data) {
      this.data = data;
      this.rebuildWidgets();
   }

   private int left() {
      return Math.max(4, this.width / 2 - 210);
   }

   private int panelX() {
      return this.left() + 176 + 8;
   }

   private int listTop() {
      return 54;
   }

   private int rows() {
      return Math.max(1, (this.height - this.listTop() - 30) / 14);
   }

   private List<JsonObject> shown() {
      List<JsonObject> out = new ArrayList<>();
      if (this.data == null) {
         return out;
      } else {
         String q = this.search.toLowerCase(Locale.ROOT).trim();

         for (JsonElement e : this.data.getAsJsonArray("entries")) {
            JsonObject j = e.getAsJsonObject();
            int st = j.get("status").getAsInt();
            if ((filter != DexScreen.Filter.SEEN || st >= 1)
               && (filter != DexScreen.Filter.CAUGHT || st >= 2)
               && (
                  q.isEmpty()
                     || j.has("name") && j.get("name").getAsString().toLowerCase(Locale.ROOT).contains(q)
                     || String.valueOf(j.get("n").getAsInt()).equals(q)
               )) {
               out.add(j);
            }
         }

         return out;
      }
   }

   protected void init() {
      int x = this.left();

      for (int i = 0; i < DexScreen.Filter.values().length; i++) {
         DexScreen.Filter f = DexScreen.Filter.values()[i];
         Button b = (Button)this.addRenderableWidget(
            Button.builder(Component.literal(f.name().charAt(0) + f.name().substring(1).toLowerCase(Locale.ROOT)), btn -> {
               filter = f;
               this.scroll = 0;
               selected = 0;
               this.rebuildWidgets();
            }).bounds(x + i * 46, 32, 44, 16).build()
         );
         b.active = filter != f;
      }

      this.searchBox = new EditBox(this.font, this.panelX(), 32, 236, 16, Component.literal("Search"));
      this.searchBox.setHint(Component.literal("Search by name or number..."));
      this.searchBox.setValue(this.search);
      this.searchBox.setResponder(v -> {
         if (!v.equals(this.search)) {
            this.search = v;
            this.searching = true;
            this.scroll = 0;
            selected = 0;
            this.rebuildWidgets();
         }
      });
      this.addRenderableWidget(this.searchBox);
      if (this.searching) {
         this.setFocused(this.searchBox);
      }

      List<JsonObject> list = this.shown();
      selected = Math.clamp((long)selected, 0, Math.max(0, list.size() - 1));
      this.scroll = Math.clamp((long)this.scroll, 0, Math.max(0, list.size() - this.rows()));

      for (int i = this.scroll; i < Math.min(list.size(), this.scroll + this.rows()); i++) {
         int idx = i;
         Button row = (Button)this.addRenderableWidget(Button.builder(Component.empty(), b -> {
            selected = idx;
            this.rebuildWidgets();
         }).bounds(x, this.listTop() + (i - this.scroll) * 14, 176, 13).build());
         row.active = i != selected;
      }

      this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose()).bounds(this.width / 2 - 40, this.height - 24, 80, 20).build());
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         if (this.preview == null) {
            this.preview = new CreatureEntity(Ahrivaea.CREATURE, mc.level);
            this.preview.setId(-4243);
         }

         if (!list.isEmpty()) {
            this.preview.previewAs(list.get(selected).get("species").getAsString(), list.get(selected).get("influence").getAsString());
         }
      }
   }

   public void searchFor(String text) {
      this.searchBox.setValue(text);
   }

   public void selectFirst(int status) {
      List<JsonObject> list = this.shown();

      for (int i = 0; i < list.size(); i++) {
         if (list.get(i).get("status").getAsInt() == status) {
            selected = i;
            this.scroll = Math.max(0, i - 2);
            break;
         }
      }

      this.rebuildWidgets();
   }

   public boolean loaded() {
      return this.data != null;
   }

   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      this.scroll = this.scroll - (int)Math.signum(sy) * 3;
      this.rebuildWidgets();
      return true;
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(g, mouseX, mouseY, partialTick);
      int x = this.left();
      g.text(this.font, "Creature Dex", x, 18, -12224);
      if (this.data == null) {
         g.centeredText(this.font, "Loading...", this.width / 2, this.height / 2, -5592406);
      } else {
         int total = this.data.getAsJsonArray("entries").size();
         String counts = "Seen " + this.data.get("seen").getAsInt() + "/" + total + "   Caught " + this.data.get("caught").getAsInt() + "/" + total;
         g.text(this.font, counts, this.panelX() + 236 - this.font.width(counts), 18, -6299489);
         List<JsonObject> list = this.shown();

         for (int i = this.scroll; i < Math.min(list.size(), this.scroll + this.rows()); i++) {
            JsonObject j = list.get(i);
            int y = this.listTop() + (i - this.scroll) * 14;
            int st = j.get("status").getAsInt();
            String num = String.format("#%03d", j.get("n").getAsInt());
            g.text(this.font, num, x + 4, y + 3, -8355712);
            int color = 0xFF000000 | j.get("color").getAsInt();
            if (st >= 1) {
               g.fill(x + 30, y + 3, x + 36, y + 9, color);
               g.text(this.font, this.font.plainSubstrByWidth(j.get("name").getAsString(), 122), x + 40, y + 3, -1);
               if (st >= 2) {
                  g.text(this.font, "●", x + 176 - 10, y + 3, -44976);
               }
            } else {
               g.text(this.font, "???", x + 40, y + 3, -10461088);
            }
         }

         if (list.isEmpty()) {
            g.text(this.font, filter == DexScreen.Filter.ALL ? "Nothing matches." : "None yet.", x + 4, this.listTop() + 4, -5592406);
         } else if (list.size() > this.rows()) {
            g.text(this.font, "Scroll for more (" + list.size() + ")", x + 4, this.listTop() + this.rows() * 14 + 2, -8355712);
         }

         if (!list.isEmpty()) {
            this.details(g, list.get(selected), this.panelX(), this.listTop(), mouseX, mouseY);
         }
      }
   }

   private void details(GuiGraphicsExtractor g, JsonObject j, int x, int y, int mouseX, int mouseY) {
      int st = j.get("status").getAsInt();
      int bottom = this.height - 30;
      int color = 0xFF000000 | j.get("color").getAsInt();
      g.fill(x - 4, y - 4, x + 236 + 4, bottom, -1072688104);
      String num = String.format("#%03d", j.get("n").getAsInt());
      g.fill(x - 4, y - 4, x + 236 + 4, y + 10, st >= 1 ? color & 16777215 | -1610612736 : -1607454672);
      g.text(this.font, num + "  " + (st >= 1 ? j.get("name").getAsString() : "???"), x, y - 1, -1);
      String state = st >= 2 ? "Caught" : (st >= 1 ? "Seen" : "Unknown");
      g.text(this.font, state, x + 236 - this.font.width(state), y - 1, st >= 2 ? -32640 : (st >= 1 ? -6303489 : -7303024));
      y += 14;
      int boxBottom = y + (st >= 2 ? 50 : 62);
      if (st >= 1 && this.preview != null) {
         String id = j.get("species").getAsString();
         String inf = j.get("influence").getAsString();
         float size = Math.max(0.5F, Math.max(GeoModels.height(id, inf), GeoModels.extent(id, inf) * 0.7F) * GeoModels.scale(id, inf));
         float h = GeoModels.height(id, inf) * GeoModels.scale(id, inf);
         InventoryScreen.extractEntityInInventoryFollowsMouse(
            g,
            x + 118 - 50,
            y,
            x + 118 + 50,
            boxBottom,
            Math.max(2, Math.round((float)(boxBottom - y - 8) / size)),
            h / 2.0F - 0.4F,
            (float)mouseX,
            (float)mouseY,
            this.preview
         );
      } else {
         g.fill(x + 118 - 24, y + 4, x + 118 + 24, boxBottom - 4, -14671840);
         g.pose().pushMatrix();
         g.pose().translate((float)x + 118.0F, (float)(y + 18));
         g.pose().scale(3.0F, 3.0F);
         g.centeredText(this.font, "?", 0, 0, -9408400);
         g.pose().popMatrix();
      }

      y = boxBottom + 4;
      if (st < 1) {
         y = this.wrap(g, j.get("hint").getAsString() + ".", x, y, -6303489);
         this.wrap(g, "Find one to learn its name; catch or recruit one to learn everything about it.", x, y + 4, -7303024);
      } else {
         g.text(this.font, j.get("influenceName").getAsString() + " - " + j.get("rarity").getAsString(), x, y, color);
         String lv = "Lv." + j.get("levels").getAsString();
         g.text(this.font, lv, x + 236 - this.font.width(lv), y, -2236963);
         y += 11;
         StringBuilder where = new StringBuilder();

         for (JsonElement b : j.getAsJsonArray("biomes")) {
            where.append(where.isEmpty() ? "" : ", ").append(b.getAsString());
         }

         String move = j.get("movement").getAsString();
         y = this.wrap(
            g,
            (move.equals("fly") ? "Flies" : (move.equals("swim") ? "Swims" : "Walks")) + ". Lives in " + (where.isEmpty() ? "no wild place" : where) + ".",
            x,
            y,
            -6303489
         );
         if (st < 2) {
            this.wrap(g, "Catch or recruit one to see its stats, jobs and drops.", x, y + 4, -7303024);
         } else {
            y += 3;
            JsonArray stats = j.getAsJsonArray("stats");

            for (int i = 0; i < stats.size() && y < bottom - 30; i++) {
               int v = stats.get(i).getAsInt();
               int col = i % 2;
               int sx = x + col * 118;
               g.text(this.font, STAT_NAMES[i], sx, y, -1);
               PartyClient.bar(g, sx + 48, y + 2, 40, 5, (float)v / 100.0F, -16320);
               g.text(this.font, String.valueOf(v), sx + 92, y, -11649);
               if (col == 1 || i == stats.size() - 1) {
                  y += 10;
               }
            }

            g.text(
               this.font,
               "Total "
                  + j.get("total").getAsInt()
                  + ", moves "
                  + j.get("squares").getAsInt()
                  + " squares"
                  + (j.get("rideable").getAsBoolean() ? ", rideable" : ""),
               x,
               y + 1,
               -2236963
            );
            y = this.wrap(g, "Jobs: " + j.get("jobs").getAsString(), x, y + 12, -6305537);
            StringBuilder drops = new StringBuilder();

            for (JsonElement d : j.getAsJsonArray("drops")) {
               drops.append(drops.isEmpty() ? "" : ", ").append(d.getAsString());
            }

            if (y < bottom - 10) {
               this.wrap(g, "Drops: " + (drops.isEmpty() ? "nothing" : drops), x, y, -6299489);
            }
         }
      }
   }

   private int wrap(GuiGraphicsExtractor g, String text, int x, int y, int color) {
      for (FormattedCharSequence line : this.font.split(Component.literal(text), 236)) {
         if (y > this.height - 40) {
            break;
         }

         g.text(this.font, line, x, y, color);
         y += 10;
      }

      return y;
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static enum Filter {
      ALL,
      SEEN,
      CAUGHT;
   }
}
