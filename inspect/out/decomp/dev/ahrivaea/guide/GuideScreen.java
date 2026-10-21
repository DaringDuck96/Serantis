package dev.ahrivaea.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class GuideScreen extends Screen {
   private static final int GOLD = -14006;
   private static final int WHITE = -1;
   private static final int GREY = -5723976;
   private static final int DIM = -9408384;
   private static final int NEW = -8586134;
   private static final int CARD_W = 144;
   private static final int CARD_H = 86;
   private static final int GAP = 6;
   private static final int ROW = 16;
   private static final int LIST_W = 120;
   private final GuideData data;
   private GuideData.Mod mod;
   private GuideScreen.Page page = GuideScreen.Page.ABOUT;
   private Item focus;
   private String search = "";
   private double scroll;
   private int contentHeight;
   private EditBox searchBox;
   private ItemStack hovered = ItemStack.EMPTY;
   private int hoveredOptions;

   public GuideScreen(GuideData data, String open) {
      super(Component.literal("Ahrivaea Guide"));
      this.data = data;
      if (open != null) {
         String q = open.toLowerCase(Locale.ROOT).trim();

         for (GuideData.Mod m : data.mods) {
            if (m.key().equalsIgnoreCase(q) || m.id().equalsIgnoreCase(q) || m.name().toLowerCase(Locale.ROOT).contains(q)) {
               this.select(m);
               break;
            }
         }
      }
   }

   private int left() {
      return 8;
   }

   private int top() {
      return 22;
   }

   private int right() {
      return this.width - 8;
   }

   private int bottom() {
      return this.height - 8;
   }

   private int contentX() {
      return this.left() + 6 + 120 + 8;
   }

   private int contentW() {
      return this.right() - 6 - this.contentX();
   }

   private int bodyTop() {
      return this.top() + 24 + (this.page == GuideScreen.Page.RECIPES ? 40 : 18);
   }

   private int bodyBottom() {
      return this.bottom() - 6;
   }

   protected void init() {
      int x = this.contentX();
      int w = this.contentW();
      int y = this.top() + 22;
      Button about = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("About"), b -> this.setPage(GuideScreen.Page.ABOUT)).bounds(x + w - 166, y - 2, 70, 16).build()
      );
      int n = this.recipesOfMod().size();
      Button recipes = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Recipes (" + n + ")"), b -> this.setPage(GuideScreen.Page.RECIPES)).bounds(x + w - 92, y - 2, 92, 16).build()
      );
      about.active = this.page != GuideScreen.Page.ABOUT;
      recipes.active = this.page != GuideScreen.Page.RECIPES;
      if (this.page == GuideScreen.Page.RECIPES) {
         this.searchBox = new EditBox(this.font, x, y + 18, Math.min(220, w), 16, Component.literal("Search"));
         this.searchBox.setHint(Component.literal("Search recipes...").withStyle(ChatFormatting.DARK_GRAY));
         this.searchBox.setValue(this.search);
         this.searchBox.setResponder(s -> {
            this.search = s;
            this.scroll = 0.0;
         });
         this.addRenderableWidget(this.searchBox);
         if (this.focus != null) {
            this.addRenderableWidget(Button.builder(Component.literal("Show all"), b -> {
               this.focus = null;
               this.scroll = 0.0;
               this.rebuildWidgets();
            }).bounds(x + Math.min(220, w) + 6, y + 18, 60, 16).build());
         }
      } else {
         this.searchBox = null;
      }

      this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose()).bounds(this.left() + 6, this.bottom() - 22, 120, 16).build());
   }

   private void select(GuideData.Mod m) {
      this.mod = m;
      this.focus = null;
      this.search = "";
      this.scroll = 0.0;
      if (m != null && m.recipes().isEmpty()) {
         this.page = GuideScreen.Page.ABOUT;
      }

      if (m != null && this.page == GuideScreen.Page.ABOUT) {
         GuideClient.markSeen(m.key());
      }
   }

   private void setPage(GuideScreen.Page p) {
      this.page = p;
      this.scroll = 0.0;
      if (this.mod != null && p == GuideScreen.Page.ABOUT) {
         GuideClient.markSeen(this.mod.key());
      }

      this.rebuildWidgets();
   }

   private List<GuideData.Recipe> recipesOfMod() {
      return this.mod == null ? this.data.allRecipes() : this.mod.recipes();
   }

   private List<GuideData.Recipe> shownRecipes() {
      List<GuideData.Recipe> out = new ArrayList<>();
      String[] words = this.search.toLowerCase(Locale.ROOT).trim().split("\\s+");

      for (GuideData.Recipe r : this.focus != null ? this.data.allRecipes() : this.recipesOfMod()) {
         if (this.focus == null || r.makes(this.focus) || r.uses(this.focus)) {
            boolean ok = true;

            for (String w : words) {
               if (!w.isEmpty() && !r.searchText().contains(w)) {
                  ok = false;
               }
            }

            if (ok) {
               out.add(r);
            }
         }
      }

      if (this.focus != null) {
         out.sort((a, b) -> Boolean.compare(!a.makes(this.focus), !b.makes(this.focus)));
      }

      return out;
   }

   public void showRecipes() {
      this.setPage(GuideScreen.Page.RECIPES);
   }

   public void showRecipesFor(Item item) {
      this.focus = item;
      this.mod = null;
      this.search = "";
      this.page = GuideScreen.Page.RECIPES;
      this.scroll = 0.0;
      this.rebuildWidgets();
   }

   public void search(String text) {
      this.search = text;
      if (this.searchBox != null) {
         this.searchBox.setValue(text);
      }
   }

   public int shownCount() {
      return this.shownRecipes().size();
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (super.mouseClicked(event, doubleClick)) {
         return true;
      } else {
         double mx = event.x();
         double my = event.y();
         int lx = this.left() + 6;
         int ly = this.top() + 24;
         if (mx >= (double)lx && mx < (double)(lx + 120)) {
            int i = (int)Math.floor((my - (double)ly) / 16.0);
            if (my >= (double)ly && i >= 0 && i <= this.data.mods.size()) {
               this.select(i == 0 ? null : this.data.mods.get(i - 1));
               this.rebuildWidgets();
               return true;
            }
         }

         if (!this.hovered.isEmpty() && event.button() == 1) {
            this.focus = this.hovered.getItem();
            this.mod = null;
            this.page = GuideScreen.Page.RECIPES;
            this.search = "";
            this.scroll = 0.0;
            this.rebuildWidgets();
            return true;
         } else {
            return false;
         }
      }
   }

   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      int max = Math.max(0, this.contentHeight - (this.bodyBottom() - this.bodyTop()));
      this.scroll = Math.clamp(this.scroll - sy * 24.0, 0.0, (double)max);
      return true;
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      super.extractBackground(g, mouseX, mouseY, partialTick);
      g.fill(this.left(), this.top(), this.right(), this.bottom(), -267382760);
      g.outline(this.left(), this.top(), this.right() - this.left(), this.bottom() - this.top(), -12829616);
      g.text(this.font, "Ahrivaea Guide", this.left() + 8, this.top() + 8, -14006);
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(g, mouseX, mouseY, partialTick);
      this.hovered = ItemStack.EMPTY;
      this.drawModList(g, mouseX, mouseY);
      int x = this.contentX();
      int y = this.top() + 24;
      String heading = this.focus != null
         ? "Recipes with " + new ItemStack(this.focus).getHoverName().getString()
         : (this.mod == null ? "All mods" : this.mod.name());
      g.text(this.font, heading, x, y + 2, -1);
      int top = this.bodyTop();
      int bot = this.bodyBottom();
      g.enableScissor(x - 2, top, x + this.contentW() + 2, bot);
      boolean inBody = mouseY >= top && mouseY < bot && mouseX >= x && mouseX < x + this.contentW();
      int end = this.page == GuideScreen.Page.ABOUT
         ? this.drawAbout(g, x, top - (int)this.scroll, this.contentW(), inBody, mouseX, mouseY)
         : this.drawRecipes(g, x, top - (int)this.scroll, this.contentW(), inBody, mouseX, mouseY, top, bot);
      g.disableScissor();
      this.contentHeight = end - (top - (int)this.scroll);
      if (this.contentHeight > bot - top) {
         int h = bot - top;
         int barH = Math.max(16, h * h / this.contentHeight);
         int barY = top + (int)((double)(h - barH) * (this.scroll / (double)Math.max(1, this.contentHeight - h)));
         g.fill(this.right() - 5, top, this.right() - 3, bot, 1090519039);
         g.fill(this.right() - 5, barY, this.right() - 3, barY + barH, -1056964609);
      }

      if (!this.hovered.isEmpty()) {
         List<Component> tip = new ArrayList<>();
         tip.add(this.hovered.getHoverName());
         if (this.hoveredOptions > 1) {
            tip.add(Component.literal("Any of " + this.hoveredOptions + " kinds (they take turns here)").withStyle(ChatFormatting.GRAY));
         }

         String by = this.data.addedBy.get(this.hovered.getItem());
         String ns = BuiltInRegistries.ITEM.getKey(this.hovered.getItem()).getNamespace();
         tip.add(Component.literal(by != null ? "Added by " + by : (ns.equals("minecraft") ? "Minecraft" : ns)).withStyle(ChatFormatting.BLUE));
         tip.add(Component.literal("Click: recipes that make or use it").withStyle(ChatFormatting.DARK_GRAY));
         g.setComponentTooltipForNextFrame(this.font, tip, mouseX, mouseY);
      }
   }

   private void drawModList(GuiGraphicsExtractor g, int mx, int my) {
      int x = this.left() + 6;
      int y = this.top() + 24;

      for (int i = 0; i <= this.data.mods.size(); i++) {
         GuideData.Mod m = i == 0 ? null : this.data.mods.get(i - 1);
         boolean selected = this.focus == null && m == this.mod;
         boolean over = mx >= x && mx < x + 120 && my >= y && my < y + 16;
         if (selected) {
            g.fill(x, y, x + 120, y + 16 - 1, -13355958);
         } else if (over) {
            g.fill(x, y, x + 120, y + 16 - 1, -14540242);
         }

         String name = m == null ? "All mods" : m.name().replaceFirst("^Ahrivaea ", "");
         boolean fresh = m != null && !GuideClient.seen(m.key());
         int room = 112 - (fresh ? this.font.width("NEW") + 4 : 0);
         g.text(this.font, this.font.plainSubstrByWidth(name, room), x + 4, y + 4, selected ? -14006 : -1);
         if (fresh) {
            g.text(this.font, "NEW", x + 120 - 4 - this.font.width("NEW"), y + 4, -8586134);
         }

         y += 16;
      }
   }

   private int para(GuiGraphicsExtractor g, String text, int x, int y, int w, int color) {
      for (FormattedCharSequence line : this.font.split(Component.literal(text), w)) {
         g.text(this.font, line, x, y, color);
         y += 9 + 1;
      }

      return y;
   }

   private int bullets(GuiGraphicsExtractor g, List<String> items, int x, int y, int w, int color, boolean numbered) {
      for (int i = 0; i < items.size(); i++) {
         String mark = numbered ? i + 1 + "." : "•";
         g.text(this.font, mark, x, y, -5723976);
         y = this.para(g, items.get(i), x + 12, y, w - 12, color) + 3;
      }

      return y;
   }

   private int drawAbout(GuiGraphicsExtractor g, int x, int y, int w, boolean inBody, int mx, int my) {
      w = Math.min(w - 8, 520);
      if (this.mod == null) {
         y = this.para(
               g,
               "Every mod in the Ahrivaea pack. Pick one on the left to read what it does and how to get started, or open Recipes to see how to make everything. Click any item to see what makes it and what it's used in.",
               x,
               y,
               w,
               -5723976
            )
            + 8;

         for (GuideData.Mod m : this.data.mods) {
            g.text(this.font, m.name(), x, y, -14006);
            if (!GuideClient.seen(m.key())) {
               g.text(this.font, "NEW", x + this.font.width(m.name()) + 6, y, -8586134);
            }

            y = this.para(g, m.summary(), x, y + 11, w, -1) + 8;
         }

         return y;
      } else {
         y = this.para(g, this.mod.summary(), x, y, w, -1) + 8;
         if (!this.mod.guide().isEmpty()) {
            g.text(this.font, "Getting started", x, y, -14006);
            y = this.bullets(g, this.mod.guide(), x, y + 12, w, -1, true) + 6;
         }

         if (!this.mod.items().isEmpty()) {
            g.text(this.font, "Its items", x, y, -14006);
            y += 12;
            int ix = x;

            for (ItemStack st : this.mod.items()) {
               if (ix + 18 > x + w) {
                  ix = x;
                  y += 20;
               }

               this.slot(g, st, 1, ix, y, inBody, mx, my);
               ix += 20;
            }

            y += 24;
            y = this.para(g, "Click one to see how it's made. Items with no recipe come from creatures, loot or the mod itself.", x, y, w, -9408384) + 6;
         }

         if (!this.mod.features().isEmpty()) {
            g.text(this.font, "Everything it does", x, y, -14006);
            y = this.bullets(g, this.mod.features(), x, y + 12, w, -5723976, false) + 6;
         }

         if (!this.mod.commands().isBlank() && !this.mod.commands().startsWith("(none)")) {
            g.text(this.font, "Commands", x, y, -14006);
            y = this.para(g, this.mod.commands(), x, y + 12, w, -5723976) + 6;
         }

         if (!this.mod.install().isBlank()) {
            g.text(this.font, "Do players need it installed?", x, y, -14006);
            y = this.para(g, this.mod.install(), x, y + 12, w, -5723976) + 6;
         }

         return y;
      }
   }

   private int drawRecipes(GuiGraphicsExtractor g, int x, int y, int w, boolean inBody, int mx, int my, int top, int bot) {
      List<GuideData.Recipe> list = this.shownRecipes();
      if (list.isEmpty()) {
         String why = this.focus != null
            ? "Nothing in these mods makes or uses this item."
            : (!this.search.isBlank() ? "No recipes match \"" + this.search + "\"." : "This mod has no recipes of its own. Its About page says how it works.");
         return this.para(g, why, x, y + 4, w, -5723976);
      } else {
         int cols = Math.max(1, (w + 6) / 150);

         for (int i = 0; i < list.size(); i++) {
            int cx = x + i % cols * 150;
            int cy = y + i / cols * 92;
            if (cy + 86 >= top && cy <= bot) {
               this.card(g, list.get(i), cx, cy, inBody, mx, my);
            }
         }

         int rows = (list.size() + cols - 1) / cols;
         return y + rows * 92;
      }
   }

   private void card(GuiGraphicsExtractor g, GuideData.Recipe r, int x, int y, boolean inBody, int mx, int my) {
      g.fill(x, y, x + 144, y + 86, -14540244);
      g.outline(x, y, 144, 86, -12303270);
      String name = r.result().getHoverName().getString() + (r.result().getCount() > 1 ? " x" + r.result().getCount() : "");
      g.text(this.font, this.font.plainSubstrByWidth(name, 136), x + 4, y + 4, -1);
      int gx = x + 6;
      int gy = y + 16;
      int mid = gy + 18;
      switch (r.station()) {
         case CRAFTING:
            for (int i = 0; i < 9; i++) {
               this.slotOptions(g, r.slots().get(i), gx + i % 3 * 18, gy + i / 3 * 18, inBody, mx, my);
            }
            break;
         case STONECUTTER:
            this.slotOptions(g, r.slots().get(0), gx + 18, mid, inBody, mx, my);
            break;
         case SMITHING:
            for (int i = 0; i < 3; i++) {
               this.slotOptions(g, r.slots().get(i), gx + i * 18, mid, inBody, mx, my);
            }
      }

      g.text(this.font, "→", gx + 54 + 6, mid + 5, -5723976);
      this.slot(g, r.result(), 1, gx + 54 + 20, mid, inBody, mx, my);
      g.text(this.font, r.station().title, x + 4, y + 86 - 11, -9408384);
      if (this.mod == null || this.focus != null) {
         String m = this.font.plainSubstrByWidth(r.mod().name().replaceFirst("^Ahrivaea ", ""), 130 - this.font.width(r.station().title));
         g.text(this.font, m, x + 144 - 4 - this.font.width(m), y + 86 - 11, -9794880);
      }
   }

   private void slotOptions(GuiGraphicsExtractor g, List<ItemStack> options, int x, int y, boolean inBody, int mx, int my) {
      ItemStack shown = options.isEmpty() ? ItemStack.EMPTY : options.get((int)(System.currentTimeMillis() / 1000L % (long)options.size()));
      this.slot(g, shown, Math.max(1, options.size()), x, y, inBody, mx, my);
   }

   private void slot(GuiGraphicsExtractor g, ItemStack stack, int options, int x, int y, boolean inBody, int mx, int my) {
      g.fill(x, y, x + 18, y + 18, -15395556);
      g.fill(x + 1, y + 1, x + 17, y + 17, -12961208);
      if (!stack.isEmpty()) {
         g.item(stack, x + 1, y + 1);
         g.itemDecorations(this.font, stack, x + 1, y + 1);
         if (inBody && mx >= x && mx < x + 18 && my >= y && my < y + 18) {
            g.fill(x + 1, y + 1, x + 17, y + 17, 1627389951);
            this.hovered = stack;
            this.hoveredOptions = options;
         }
      }
   }

   private static enum Page {
      ABOUT,
      RECIPES;
   }
}
