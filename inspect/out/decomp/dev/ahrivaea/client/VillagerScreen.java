package dev.ahrivaea.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.ahrivaea.villager.Villagers;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ItemLike;

public class VillagerScreen extends Screen {
   static final int TOP = 14;
   static final int CELL = 20;
   static final int COLS = 12;
   private JsonObject state;
   private final List<ItemStack> stacks = new ArrayList<>();
   private int scroll;
   private boolean confirmChange;
   private boolean choosing;

   public VillagerScreen(JsonObject state) {
      super(Component.literal("Villager"));
      this.setState(state);
   }

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(Villagers.StatePayload.TYPE, (payload, ctx) -> {
         JsonObject o = JsonParser.parseString(payload.json()).getAsJsonObject();
         Minecraft mc = ctx.client();
         if (mc.gui.screen() instanceof VillagerScreen s) {
            s.update(o);
         } else {
            mc.gui.setScreen(new VillagerScreen(o));
         }
      });
   }

   public JsonObject state() {
      return this.state;
   }

   private void setState(JsonObject o) {
      this.state = o;
      this.stacks.clear();
      Minecraft mc = Minecraft.getInstance();
      if (o.has("offers") && mc.level != null) {
         RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess());

         for (JsonElement e : o.getAsJsonArray("offers")) {
            this.stacks.add(ItemStack.CODEC.parse(ops, e.getAsJsonObject().get("stack")).result().orElse(ItemStack.EMPTY));
         }
      }
   }

   void update(JsonObject o) {
      this.setState(o);
      this.rebuildWidgets();
   }

   private boolean trained() {
      return !this.state.get("profession").getAsString().isEmpty();
   }

   public void send(String kind, Consumer<JsonObject> extra) {
      JsonObject a = new JsonObject();
      a.addProperty("kind", kind);
      a.add("handle", this.state.getAsJsonObject("handle"));
      extra.accept(a);
      ClientPlayNetworking.send(new Villagers.ActionPayload(a.toString()));
   }

   private int left() {
      return this.width / 2 - 250;
   }

   private int shopX() {
      return this.left() + 210;
   }

   private int shopY() {
      return 64;
   }

   private int rowsShown() {
      return Math.max(1, (this.height - this.shopY() - 34) / 20);
   }

   protected void init() {
      if (this.trained() && !this.choosing) {
         JsonArray ranks = this.state.getAsJsonArray("ranks");
         int rank = this.state.get("rank").getAsInt();
         if (rank < ranks.size()) {
            JsonObject r = ranks.get(rank).getAsJsonObject();
            Button promote = (Button)this.addRenderableWidget(
               Button.builder(Component.literal("Become " + r.get("name").getAsString()), b -> this.send("promote", a -> {
                  })).bounds(this.left(), 74 + ranks.size() * 34, 190, 20).build()
            );
            promote.active = r.has("progress") && r.get("progress").getAsInt() >= r.get("amount").getAsInt();
         }

         this.addRenderableWidget(Button.builder(Component.literal(this.confirmChange ? "Start over in another? (click again)" : "Change profession"), b -> {
            if (!this.confirmChange) {
               this.confirmChange = true;
               this.rebuildWidgets();
            } else {
               this.confirmChange = false;
               this.choosing = true;
               this.scroll = 0;
               this.rebuildWidgets();
            }
         }).bounds(this.left(), 98 + ranks.size() * 34, 190, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose()).bounds(this.width / 2 - 40, this.height - 26, 80, 20).build());
      } else {
         JsonArray profs = this.state.getAsJsonArray("professions");
         this.scroll = Math.clamp((long)this.scroll, 0, Math.max(0, profs.size() - this.professionRows()));

         for (int i = this.scroll; i < Math.min(profs.size(), this.scroll + this.professionRows()); i++) {
            String id = profs.get(i).getAsJsonObject().get("id").getAsString();
            Button b = (Button)this.addRenderableWidget(
               Button.builder(Component.literal(id.equals(this.state.get("profession").getAsString()) ? "Current" : "Train"), x -> {
                  this.choosing = false;
                  this.send("train", a -> a.addProperty("profession", id));
               }).bounds(this.left() + 430, 60 + (i - this.scroll) * 30, 66, 20).build()
            );
            b.active = !id.equals(this.state.get("profession").getAsString());
         }

         if (this.choosing) {
            this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
               this.choosing = false;
               this.rebuildWidgets();
            }).bounds(this.width / 2 - 40, this.height - 26, 80, 20).build());
         } else {
            this.addRenderableWidget(
               Button.builder(Component.literal("Done"), b -> this.onClose()).bounds(this.width / 2 - 40, this.height - 26, 80, 20).build()
            );
         }
      }
   }

   private int professionRows() {
      return Math.max(1, (this.height - 46 - 14 - 34) / 30);
   }

   public boolean mouseScrolled(double x, double y, double dx, double dy) {
      this.scroll = this.scroll - (int)Math.signum(dy);
      if (this.trained() && !this.choosing) {
         this.scroll = Math.clamp((long)this.scroll, 0, Math.max(0, this.shopRows() - this.rowsShown()));
      }

      this.rebuildWidgets();
      return true;
   }

   private int[][] layout() {
      JsonArray offers = this.state.getAsJsonArray("offers");
      int[][] at = new int[offers.size()][2];
      int row = -1;
      int col = 12;
      int lastRank = -1;

      for (int i = 0; i < offers.size(); i++) {
         int r = offers.get(i).getAsJsonObject().get("rank").getAsInt();
         if (r != lastRank) {
            row += 2;
            col = 0;
            lastRank = r;
         } else if (col >= 12) {
            row++;
            col = 0;
         }

         at[i][0] = row;
         at[i][1] = col++;
      }

      return at;
   }

   private int shopRows() {
      int[][] at = this.layout();
      return at.length == 0 ? 0 : at[at.length - 1][0] + 1;
   }

   private int offerAt(double mx, double my) {
      int[][] at = this.layout();

      for (int i = 0; i < at.length; i++) {
         int x = this.shopX() + at[i][1] * 20;
         int y = this.shopY() + (at[i][0] - this.scroll) * 20;
         if (at[i][0] >= this.scroll
            && at[i][0] < this.scroll + this.rowsShown()
            && mx >= (double)x
            && mx < (double)(x + 20)
            && my >= (double)y
            && my < (double)(y + 20)) {
            return i;
         }
      }

      return -1;
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (super.mouseClicked(event, doubleClick)) {
         return true;
      } else {
         if (this.trained() && !this.choosing && event.button() == 1) {
            int i = this.offerAt(event.x(), event.y());
            if (i >= 0) {
               int times = event.hasShiftDown() ? 10 : 1;
               this.send("buy", a -> {
                  a.addProperty("offer", i);
                  a.addProperty("times", times);
               });
               return true;
            }
         }

         return false;
      }
   }

   public void buy(int offer, int times) {
      this.send("buy", a -> {
         a.addProperty("offer", offer);
         a.addProperty("times", times);
      });
   }

   public void train(String profession) {
      this.send("train", a -> a.addProperty("profession", profession));
   }

   public void promote() {
      this.send("promote", a -> {
      });
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(g, mouseX, mouseY, partialTick);
      int x = this.left();
      String title = "Villager Lv."
         + this.state.get("level").getAsInt()
         + (this.trained() ? ": " + this.rankName() + " " + this.state.get("professionName").getAsString() : ", untrained");
      g.centeredText(this.font, title, this.width / 2, 24, -12224);
      String xp = "Your XP: " + this.state.get("xp").getAsInt();
      g.text(this.font, xp, x + 500 - this.font.width(xp), 24, -6299489);
      if (this.trained() && !this.choosing) {
         JsonArray ranks = this.state.getAsJsonArray("ranks");
         int rank = this.state.get("rank").getAsInt();
         g.text(this.font, "Ranks", x, 58, -1);

         for (int i = 0; i < ranks.size(); i++) {
            JsonObject r = ranks.get(i).getAsJsonObject();
            int y = 72 + i * 34;
            boolean earned = r.get("earned").getAsBoolean();
            boolean now = i == rank;
            g.fill(x, y - 2, x + 190, y + 30, now ? -1607458752 : Integer.MIN_VALUE);
            g.text(
               this.font,
               (earned ? "✔ " : (now ? "▶ " : "\ud83d\udd12 ")) + r.get("name").getAsString(),
               x + 4,
               y,
               earned ? -8396673 : (now ? -12224 : -7829368)
            );
            String deed = r.get("deed").getAsString();
            if (this.font.width(deed) > 180) {
               deed = this.font.plainSubstrByWidth(deed, 175) + "...";
            }

            g.text(this.font, deed, x + 4, y + 11, earned ? -7360625 : (now ? -1 : -8947849));
            if (now && r.has("progress")) {
               int p = r.get("progress").getAsInt();
               int a = r.get("amount").getAsInt();
               g.fill(x + 4, y + 22, x + 150, y + 26, -13421773);
               g.fill(x + 4, y + 22, x + 4 + (int)(146L * (long)p / (long)Math.max(1, a)), y + 26, p >= a ? -11149995 : -2250189);
               g.text(this.font, p + "/" + a, x + 154, y + 20, -3355444);
            }
         }

         if (rank >= ranks.size()) {
            g.text(this.font, "A Master: everything is unlocked.", x, 74 + ranks.size() * 34, -8396673);
         }

         JsonArray offers = this.state.getAsJsonArray("offers");
         int[][] at = this.layout();
         g.text(this.font, "Shop (click to buy, shift-click for 10; scroll for more)", this.shopX(), 48, -1);
         int hovered = this.offerAt((double)mouseX, (double)mouseY);

         for (int i = 0; i < at.length; i++) {
            int row = at[i][0] - this.scroll;
            int rx = offers.get(i).getAsJsonObject().get("rank").getAsInt();
            boolean firstOfRank = i == 0 || offers.get(i - 1).getAsJsonObject().get("rank").getAsInt() != rx;
            if (firstOfRank && row - 1 >= 0 && row - 1 < this.rowsShown()) {
               g.text(
                  this.font,
                  ranks.get(rx).getAsJsonObject().get("name").getAsString() + (rx < rank ? "" : " (locked)"),
                  this.shopX(),
                  this.shopY() + (row - 1) * 20 + 8,
                  rx < rank ? -12224 : -7829368
               );
            }

            if (row >= 0 && row < this.rowsShown()) {
               int cx = this.shopX() + at[i][1] * 20;
               int cy = this.shopY() + row * 20;
               boolean open = rx < rank;
               g.fill(cx, cy, cx + 20 - 2, cy + 20 - 2, i == hovered ? -1067425664 : (open ? -2144325568 : -2146430960));
               ItemStack s = this.stacks.get(i);
               g.item(s, cx + 1, cy + 1);
               if (s.getCount() > 1) {
                  String n = String.valueOf(s.getCount());
                  g.text(this.font, n, cx + 20 - 2 - this.font.width(n), cy + 20 - 9, -1, true);
               }

               if (!open) {
                  g.fill(cx, cy, cx + 20 - 2, cy + 20 - 2, -1610612736);
               }
            }
         }

         if (hovered >= 0) {
            ItemStack sx = this.stacks.get(hovered);
            JsonObject off = offers.get(hovered).getAsJsonObject();
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(sx.getCount() + " x ").append(sx.getHoverName()));
            ItemEnchantments books = (ItemEnchantments)sx.get(DataComponents.STORED_ENCHANTMENTS);
            if (books != null) {
               for (Entry<Holder<Enchantment>> e : books.entrySet()) {
                  tip.add(Enchantment.getFullname((Holder)e.getKey(), e.getIntValue()).copy().withStyle(ChatFormatting.AQUA));
               }
            }

            PotionContents potion = (PotionContents)sx.get(DataComponents.POTION_CONTENTS);
            if (potion != null && potion.potion().isPresent()) {
               tip.add(
                  Component.literal(((Holder)potion.potion().get()).unwrapKey().map(k -> k.identifier().getPath().replace('_', ' ')).orElse(""))
                     .withStyle(ChatFormatting.BLUE)
               );
            }

            int cost = off.get("cost").getAsInt();
            tip.add(Component.literal(cost + " XP").withStyle(this.state.get("xp").getAsInt() >= cost ? ChatFormatting.GREEN : ChatFormatting.RED));
            int rxx = off.get("rank").getAsInt();
            tip.add(
               rxx < rank
                  ? Component.literal("Click to buy, shift-click for 10").withStyle(ChatFormatting.DARK_GRAY)
                  : Component.literal("Sold from " + ranks.get(rxx).getAsJsonObject().get("name").getAsString() + " rank").withStyle(ChatFormatting.GRAY)
            );
            g.setComponentTooltipForNextFrame(this.font, tip, mouseX, mouseY);
         }
      } else {
         g.centeredText(
            this.font,
            this.choosing
               ? "Pick a new profession. It starts over as a Novice."
               : "Pick a profession to train it in. Each rank takes a deed; each rank sells more.",
            this.width / 2,
            38,
            -5592406
         );
         JsonArray profs = this.state.getAsJsonArray("professions");

         for (int i = this.scroll; i < Math.min(profs.size(), this.scroll + this.professionRows()); i++) {
            JsonObject p = profs.get(i).getAsJsonObject();
            int yx = 58 + (i - this.scroll) * 30;
            g.fill(x, yx - 2, x + 500, yx + 26, Integer.MIN_VALUE);
            g.item(new ItemStack((ItemLike)BuiltInRegistries.ITEM.getValue(Identifier.parse(p.get("icon").getAsString()))), x + 4, yx + 3);
            g.text(this.font, p.get("name").getAsString() + "  (" + p.get("offers").getAsInt() + " items)", x + 26, yx, -1);
            g.text(this.font, p.get("description").getAsString(), x + 26, yx + 10, -5592406);
            String first = "Novice: " + p.get("first").getAsString();
            g.text(this.font, first, x + 426 - this.font.width(first), yx, -6305537);
         }
      }
   }

   private String rankName() {
      int rank = this.state.get("rank").getAsInt();
      JsonArray ranks = this.state.getAsJsonArray("ranks");
      return rank == 0 ? "training" : ranks.get(Math.min(rank, ranks.size()) - 1).getAsJsonObject().get("name").getAsString();
   }

   public boolean isPauseScreen() {
      return false;
   }
}
