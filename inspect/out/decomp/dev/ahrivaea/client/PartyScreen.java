package dev.ahrivaea.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class PartyScreen extends Screen {
   static final int TOP = 14;
   private static PartyScreen.Tab tab = PartyScreen.Tab.PARTY;
   private static int selected = 0;
   private int scroll;
   private boolean confirmRelease;
   private static final String[] STAT_NAMES = new String[]{"HP", "Attack", "Defense", "Speed", "Magic", "Luck", "Charisma"};
   private static final int CARD_H = 24;
   private static final int[] TAB_X = new int[]{0, 46, 84, 122};
   private static final int[] TAB_W = new int[]{45, 37, 37, 30};
   private static final String[] STAT_KEYS = new String[]{"hp", "attack", "defense", "speed", "magic", "luck", "charisma"};

   public PartyScreen() {
      super(Component.literal("Party"));
   }

   public static void showYou() {
      tab = PartyScreen.Tab.YOU;
   }

   void refresh() {
      this.rebuildWidgets();
   }

   private JsonArray list() {
      return tab != PartyScreen.Tab.YOU && PartyClient.state.has(tab.key()) ? PartyClient.state.getAsJsonArray(tab.key()) : new JsonArray();
   }

   private static JsonObject me() {
      return PartyClient.state.has("character") ? PartyClient.state.getAsJsonObject("character") : null;
   }

   private JsonObject chosen() {
      JsonArray l = this.list();
      if (l.isEmpty()) {
         return null;
      } else {
         selected = Math.clamp((long)selected, 0, l.size() - 1);
         return l.get(selected).getAsJsonObject();
      }
   }

   private int left() {
      return Math.max(6, this.width / 2 - 205);
   }

   private int right() {
      return this.left() + 160;
   }

   private int rows() {
      return Math.max(1, (this.height - 46 - 14 - 32) / 24);
   }

   private void command(String cmd) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.connection.sendCommand(cmd);
      }

      this.confirmRelease = false;
   }

   protected void init() {
      if (PartyClient.state == null) {
         this.onClose();
      } else {
         int x = this.left();

         for (int i = 0; i < PartyScreen.Tab.values().length; i++) {
            PartyScreen.Tab t = PartyScreen.Tab.values()[i];
            String label = t == PartyScreen.Tab.YOU ? "You" : t.title + " " + PartyClient.state.getAsJsonArray(t.key()).size();
            Button b = (Button)this.addRenderableWidget(Button.builder(Component.literal(label), btn -> {
               tab = t;
               selected = 0;
               this.scroll = 0;
               this.confirmRelease = false;
               this.rebuildWidgets();
            }).bounds(x + TAB_X[i], 34, TAB_W[i], 18).build());
            b.active = tab != t;
         }

         if (tab == PartyScreen.Tab.YOU) {
            JsonObject me = me();
            if (me != null) {
               int rx = this.right();
               int need = me.get("xpNext").getAsInt() - me.get("xp").getAsInt();
               int pool = PartyClient.state.get("xp").getAsInt();
               Button up = (Button)this.addRenderableWidget(
                  Button.builder(Component.literal(need <= 0 ? "Max level" : "Level up (" + need + " XP)"), b -> this.command("character levelup"))
                     .tooltip(Tooltip.create(Component.literal("Spend " + need + " of your XP to go up a level: 6 more stat points")))
                     .bounds(rx, this.height - 50, 120, 20)
                     .build()
               );
               up.active = need > 0 && pool >= need;
               int points = me.get("points").getAsInt();
               JsonArray base = me.getAsJsonArray("base");

               for (int i = 0; i < STAT_KEYS.length; i++) {
                  String key = STAT_KEYS[i];
                  Button plus = (Button)this.addRenderableWidget(
                     Button.builder(
                           Component.literal("+"), b -> this.command("character spend " + key + " " + (Minecraft.getInstance().hasShiftDown() ? 5 : 1))
                        )
                        .tooltip(Tooltip.create(Component.literal("Put a point into " + STAT_NAMES[i] + " (shift: 5)")))
                        .bounds(rx + 150, 91 + i * 14, 14, 12)
                        .build()
                  );
                  plus.active = points > 0 && base.get(i).getAsInt() < 100;
               }
            }
         }

         JsonArray l = this.list();
         this.scroll = Math.clamp((long)this.scroll, 0, Math.max(0, l.size() - this.rows()));

         for (int i = this.scroll; i < Math.min(l.size(), this.scroll + this.rows()); i++) {
            int idx = i;
            Button card = (Button)this.addRenderableWidget(Button.builder(Component.empty(), btn -> {
               selected = idx;
               this.confirmRelease = false;
               this.rebuildWidgets();
            }).bounds(x, 58 + (i - this.scroll) * 24, 152, 22).build());
            card.active = idx != selected;
         }

         JsonObject c = this.chosen();
         int rx = this.right();
         int by = this.height - 50;
         if (c != null) {
            int slot = selected + 1;
            switch (tab) {
               case PARTY:
                  this.addRenderableWidget(
                     Button.builder(Component.literal("Send out"), b -> this.command("party send " + slot)).bounds(rx, by, 54, 20).build()
                  );
                  if (c.has("species") && c.get("species").getAsString().equals("villager")) {
                     this.addRenderableWidget(
                        Button.builder(Component.literal("Villager: train and trade"), b -> this.command("villager party " + slot))
                           .bounds(rx, by - 24, 160, 20)
                           .build()
                     );
                  }

                  int need = c.get("xpNext").getAsInt() - c.get("xp").getAsInt();
                  int pool = PartyClient.state.get("xp").getAsInt();
                  Button up = (Button)this.addRenderableWidget(
                     Button.builder(Component.literal("Level up"), b -> this.command("party xp " + slot + " " + need))
                        .tooltip(Tooltip.create(Component.literal("Spend " + need + " of your XP to raise it one level")))
                        .bounds(rx + 57, by, 76, 20)
                        .build()
                  );
                  up.active = pool >= need && c.get("level").getAsInt() < 50;
                  this.addRenderableWidget(
                     Button.builder(Component.literal("To box"), b -> this.command("party box store " + slot)).bounds(rx + 136, by, 46, 20).build()
                  );
                  this.addRenderableWidget(Button.builder(Component.literal(this.confirmRelease ? "Release?" : "Release"), b -> {
                     if (this.confirmRelease) {
                        this.command("party release " + slot);
                     } else {
                        this.confirmRelease = true;
                        this.rebuildWidgets();
                     }
                  }).bounds(rx + 185, by, 59, 20).build());
                  break;
               case OUT:
                  this.addRenderableWidget(
                     Button.builder(Component.literal("Call back"), b -> this.command("party recall " + c.get("entity").getAsInt()))
                        .bounds(rx, by, 90, 20)
                        .build()
                  );
                  if (c.has("species") && c.get("species").getAsString().equals("villager")) {
                     this.addRenderableWidget(
                        Button.builder(Component.literal("Villager"), b -> this.command("villager out " + c.get("entity").getAsInt()))
                           .bounds(rx + 93, by, 70, 20)
                           .build()
                     );
                  }
                  break;
               case BOX:
                  Button take = (Button)this.addRenderableWidget(
                     Button.builder(Component.literal("To party"), b -> this.command("party box take " + slot)).bounds(rx, by, 90, 20).build()
                  );
                  take.active = PartyClient.state.getAsJsonArray("party").size() < 6;
            }
         }

         if (c != null && tab != PartyScreen.Tab.BOX) {
            Minecraft mc = Minecraft.getInstance();
            boolean food = mc.player != null && mc.player.getMainHandItem().has(DataComponents.FOOD);
            int slot = selected + 1;
            String cmd = tab == PartyScreen.Tab.PARTY ? "party feed " + slot : "party feed out " + (c.has("entity") ? c.get("entity").getAsInt() : -1);
            Button feed = (Button)this.addRenderableWidget(
               Button.builder(Component.literal("Feed"), b -> this.command(cmd))
                  .bounds(this.right() + 208, 49, 36, 12)
                  .tooltip(
                     Tooltip.create(
                        Component.literal(
                           food
                              ? "Feed it the " + mc.player.getMainHandItem().getHoverName().getString() + " in your hand"
                              : "Hold food in your main hand to feed it (food heals outside battle)"
                        )
                     )
                  )
                  .build()
            );
            int hp = c.get("hp").getAsInt();
            int max = c.has("maxHp") ? c.get("maxHp").getAsInt() : hp;
            feed.active = food && hp < max;
         }

         this.addRenderableWidget(Button.builder(Component.literal("Summary: " + (PartyClient.showSummary ? "on" : "off")), b -> {
            PartyClient.setShowSummary(!PartyClient.showSummary);
            this.rebuildWidgets();
         }).bounds(x, this.height - 26, 100, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("Dex"), b -> DexScreen.open()).bounds(x + 104, this.height - 26, 48, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose()).bounds(this.width / 2 - 40, this.height - 26, 80, 20).build());
      }
   }

   public boolean mouseScrolled(double x, double y, double dx, double dy) {
      this.scroll = this.scroll - (int)Math.signum(dy);
      this.rebuildWidgets();
      return true;
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(g, mouseX, mouseY, partialTick);
      if (PartyClient.state != null) {
         int x = this.left();
         int rx = this.right();
         g.text(this.font, "Party", x, 20, -12224);
         String pool = "XP to give out: " + PartyClient.state.get("xp").getAsInt();
         g.text(this.font, pool, rx + 244 - this.font.width(pool), 20, -6299489);
         JsonArray l = this.list();

         for (int i = this.scroll; i < Math.min(l.size(), this.scroll + this.rows()); i++) {
            JsonObject c = l.get(i).getAsJsonObject();
            int y = 58 + (i - this.scroll) * 24;
            String lv = "Lv." + c.get("level").getAsInt();
            String name = c.get("name").getAsString();
            int room = 146 - this.font.width(lv) - 8;
            if (this.font.width(name) > room) {
               name = this.font.plainSubstrByWidth(name, room - 6) + "..";
            }

            g.text(this.font, name, x + 4, y + 3, 0xFF000000 | c.get("color").getAsInt());
            g.text(this.font, lv, x + 148 - this.font.width(lv), y + 3, -1);
            int hp = c.get("hp").getAsInt();
            int max = c.has("maxHp") ? c.get("maxHp").getAsInt() : hp;
            PartyClient.bar(g, x + 4, y + 14, 80, 4, max <= 0 ? 0.0F : (float)hp / (float)max, PartyClient.hpColor(hp, max));
            g.text(this.font, hp + "/" + max, x + 88, y + 12, -2236963);
         }

         if (tab == PartyScreen.Tab.YOU) {
            JsonObject me = me();
            if (me != null) {
               this.youDetails(g, me, rx, 36);
               g.text(this.font, "Your level and stats.", x, 62, -5592406);
               this.wrapped(
                  g,
                  "Battles fill your XP pool. Spend it on your creatures' levels or your own: each level gives you 6 stat points. Your stats are yours in battle, and Speed makes you faster everywhere.",
                  x,
                  76,
                  150,
                  -7829368
               );
            }
         } else {
            if (l.isEmpty()) {
               g.text(this.font, switch (tab) {
                  case PARTY -> "Empty. Catch creatures with a Vessel.";
                  case OUT -> "None out. Send one from your party.";
                  case BOX -> "Empty. Extras go here.";
                  case YOU -> "";
               }, x, 62, -5592406);
            } else if (l.size() > this.rows()) {
               g.text(this.font, "(scroll for more)", x, 58 + this.rows() * 24, -7829368);
            }

            this.details(g, this.chosen(), rx, 36);
         }
      }
   }

   private void details(GuiGraphicsExtractor g, JsonObject c, int x, int y) {
      if (c != null) {
         int w = 244;
         g.fill(x - 4, y - 4, x + w + 4, this.height - 54, Integer.MIN_VALUE);
         g.text(this.font, c.get("name").getAsString(), x, y, 0xFF000000 | c.get("color").getAsInt());
         String lv = "Lv." + c.get("level").getAsInt() + "  " + c.get("influence").getAsString();
         g.text(this.font, lv, x + w - this.font.width(lv), y, -1);
         y += 14;
         int hp = c.get("hp").getAsInt();
         int max = c.has("maxHp") ? c.get("maxHp").getAsInt() : hp;
         g.text(this.font, "HP", x, y, -1);
         PartyClient.bar(g, x + 24, y + 2, 120, 5, max <= 0 ? 0.0F : (float)hp / (float)max, PartyClient.hpColor(hp, max));
         g.text(this.font, hp + "/" + max, x + 150, y, -2236963);
         y += 12;
         int perLevel = c.has("xpNext") ? c.get("xpNext").getAsInt() : 100;
         g.text(this.font, "XP", x, y, -1);
         PartyClient.bar(g, x + 24, y + 2, 120, 5, (float)c.get("xp").getAsInt() / (float)perLevel, -10444545);
         g.text(this.font, c.get("xp").getAsInt() + "/" + perLevel, x + 150, y, -2236963);
         y += 16;
         if (c.has("stats")) {
            g.text(this.font, "Stats (now / at Lv.50)", x, y, -5592406);
            y += 11;
            JsonArray stats = c.getAsJsonArray("stats");

            for (int i = 0; i < stats.size(); i++) {
               JsonArray s = stats.get(i).getAsJsonArray();
               int col = i % 2;
               int row = i / 2;
               int sx = x + col * 124;
               int sy = y + row * 11;
               g.text(this.font, STAT_NAMES[i], sx, sy, -1);
               g.text(this.font, s.get(0).getAsInt() + " / " + s.get(1).getAsInt(), sx + 56, sy, -11649);
            }

            y += 48;
            g.text(this.font, "Moves " + c.get("squares").getAsInt() + " squares a turn in battle", x, y, -2236963);
            y += 11;
            y = this.wrapped(g, "Jobs: " + c.get("jobs").getAsString(), x, y, w, -6305537);
            y += 4;
            String move = c.get("movement").getAsString();
            if (c.get("rideable").getAsBoolean()) {
               g.text(
                  this.font,
                  "Riding: tier "
                     + c.get("rideTier").getAsInt()
                     + ", "
                     + c.get("topSpeed").getAsDouble()
                     + " blocks/sec"
                     + (move.equals("fly") ? ", flies" : (move.equals("swim") ? ", swims" : "")),
                  x,
                  y,
                  -11649
               );
               y += 11;
               g.text(
                  this.font, (c.get("saddled").getAsBoolean() ? "Saddled. " : "No saddle. ") + "Next tier: " + c.get("nextTier").getAsString(), x, y, -5592406
               );
            } else {
               g.text(this.font, "Too small to ride", x, y, -5592406);
            }

            y += 13;
         }

         if (tab == PartyScreen.Tab.OUT) {
            String where = c.get("riding").getAsBoolean()
               ? "You're riding it."
               : (c.get("sitting").getAsBoolean() ? "Out with you, waiting where you left it." : "Out with you, following you.");
            g.text(this.font, where, x, y, -6299489);
         } else if (tab == PartyScreen.Tab.PARTY) {
            g.text(this.font, "Party slot " + (selected + 1) + (selected < 4 ? " (goes into battles)" : ""), x, y, -6299489);
         }
      }
   }

   private void youDetails(GuiGraphicsExtractor g, JsonObject me, int x, int y) {
      int w = 244;
      g.fill(x - 4, y - 4, x + w + 4, this.height - 54, Integer.MIN_VALUE);
      g.text(this.font, me.get("name").getAsString(), x, y, -12224);
      String lv = "Lv." + me.get("level").getAsInt();
      g.text(this.font, lv, x + w - this.font.width(lv), y, -1);
      y += 14;
      int next = me.get("xpNext").getAsInt();
      int xp = me.get("xp").getAsInt();
      g.text(this.font, "XP", x, y, -1);
      PartyClient.bar(g, x + 24, y + 2, 120, 5, next <= 0 ? 1.0F : (float)xp / (float)next, -10444545);
      g.text(this.font, next <= 0 ? "max" : xp + "/" + next, x + 150, y, -2236963);
      y += 12;
      g.text(this.font, "Battle HP " + me.get("maxHp").getAsInt() + ", " + me.get("squares").getAsInt() + " squares a turn", x, y, -2236963);
      y += 12;
      int points = me.get("points").getAsInt();
      g.text(
         this.font,
         points > 0 ? points + (points == 1 ? " stat point" : " stat points") + " to spend" : "No stat points (level up for 6 more)",
         x,
         y,
         points > 0 ? -6299489 : -5592406
      );
      y += 16;
      JsonArray stats = me.getAsJsonArray("stats");
      JsonArray base = me.getAsJsonArray("base");

      for (int i = 0; i < stats.size(); i++) {
         int sy = y + i * 14;
         g.text(this.font, STAT_NAMES[i], x, sy + 2, -1);
         int v = stats.get(i).getAsInt();
         int b = base.get(i).getAsInt();
         PartyClient.bar(g, x + 56, sy + 4, 60, 4, (float)v / 100.0F, -11649);
         g.text(this.font, v + (v != b ? " (" + b + " + armor)" : ""), x + 120, sy + 2, -11649);
      }

      y += 104;
      int hearts = me.has("hearts") ? me.get("hearts").getAsInt() : 0;
      this.wrapped(
         g,
         "Outside battles: "
            + me.get("speedBonus").getAsInt()
            + "% faster (2% per Speed point over 30), "
            + (hearts == 0 ? "no extra hearts" : "+" + hearts + (hearts == 1 ? " heart" : " hearts"))
            + " (one per 5 HP points over 30)",
         x,
         y,
         w,
         -6305537
      );
   }

   private int wrapped(GuiGraphicsExtractor g, String text, int x, int y, int w, int color) {
      for (FormattedCharSequence line : this.font.split(Component.literal(text), w)) {
         g.text(this.font, line, x, y, color);
         y += 10;
      }

      return y;
   }

   public boolean isPauseScreen() {
      return false;
   }

   static enum Tab {
      PARTY("Party"),
      OUT("Out"),
      BOX("Box"),
      YOU("You");

      final String title;

      private Tab(String title) {
         this.title = title;
      }

      String key() {
         return this.name().toLowerCase(Locale.ROOT);
      }
   }
}
