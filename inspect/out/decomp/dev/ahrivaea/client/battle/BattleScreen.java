package dev.ahrivaea.client.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.ahrivaea.battle.BattleGrid;
import dev.ahrivaea.battle.Pathing;
import dev.ahrivaea.battle.Pathing.Occupancy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public class BattleScreen extends Screen {
   static final int TOP = 14;
   static final int LEFT = 1;
   static final int RIGHT = 3;
   private BattleScreen.Step step = BattleScreen.Step.UNIT;
   private int selected = -1;
   private int destX;
   private int destZ;
   private int pending;
   private int hoverX = -1;
   private int hoverZ = -1;
   private boolean pressed;
   private boolean dragging;
   private double pressX;
   private double pressY;
   private final Map<Integer, int[]> cards = new HashMap<>();
   private int hoveredCard = -1;
   private final List<BattleScreen.Popup> popups = new ArrayList<>();
   private final Map<Integer, Integer> lastHp = new HashMap<>();
   private static final int CARD_W = 118;
   private static final int CARD_H = 24;
   private static final int MENU_W = 96;

   public BattleScreen() {
      super(Component.literal("Battle"));
   }

   public boolean isPauseScreen() {
      return false;
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }

   protected void init() {
      this.noteHp();
      this.rebuild();
   }

   private void noteHp() {
      BattleGrid g = BattleClient.grid();

      for (JsonObject u : units()) {
         int id = u.get("id").getAsInt();
         int hp = u.get("hp").getAsInt();
         Integer before = this.lastHp.put(id, hp);
         if (before != null && before != hp && g != null) {
            int x = u.get("x").getAsInt();
            int z = u.get("z").getAsInt();
            int diff = hp - before;
            this.popups
               .add(
                  new BattleScreen.Popup(
                     (double)(g.originX + x) + 0.5,
                     (double)g.y(x, z),
                     (double)(g.originZ + z) + 0.5,
                     (diff > 0 ? "+" : "") + diff + (hp <= 0 ? " KO" : ""),
                     diff > 0 ? -10420352 : -44992,
                     Util.getMillis()
                  )
               );
         }
      }
   }

   void refresh() {
      this.noteHp();
      JsonObject u = unit(this.selected);
      if (u == null || u.get("hp").getAsInt() <= 0 || u.get("acted").getAsBoolean() || !"PLAYER".equals(phase())) {
         this.reset();
      }

      this.rebuild();
   }

   private void reset() {
      this.step = BattleScreen.Step.UNIT;
      this.selected = -1;
   }

   private static JsonObject state() {
      return BattleClient.state();
   }

   private static String phase() {
      return state() == null ? "" : state().get("phase").getAsString();
   }

   private static List<JsonObject> units() {
      List<JsonObject> out = new ArrayList<>();
      if (state() != null) {
         for (JsonElement e : state().getAsJsonArray("units")) {
            out.add(e.getAsJsonObject());
         }
      }

      return out;
   }

   private static JsonObject unit(int id) {
      for (JsonObject u : units()) {
         if (u.get("id").getAsInt() == id) {
            return u;
         }
      }

      return null;
   }

   private static JsonObject unitAt(int x, int z) {
      for (JsonObject u : units()) {
         if (u.get("hp").getAsInt() > 0 && u.get("x").getAsInt() == x && u.get("z").getAsInt() == z) {
            return u;
         }
      }

      return null;
   }

   private static boolean mine(JsonObject u) {
      return "PLAYER".equals(u.get("side").getAsString());
   }

   private Map<Integer, Integer> reach(JsonObject u) {
      BattleGrid g = BattleClient.grid();
      String side = u.get("side").getAsString();
      return Pathing.reachable(
         g,
         u.get("x").getAsInt(),
         u.get("z").getAsInt(),
         u.get("squares").getAsInt(),
         u.get("flies").getAsBoolean(),
         u.get("lava").getAsBoolean(),
         this.occupancy(u, side)
      );
   }

   private Occupancy occupancy(JsonObject self, String side) {
      return (x, z) -> {
         JsonObject o = unitAt(x, z);
         if (o != null && o.get("id").getAsInt() != self.get("id").getAsInt()) {
            return side.equals(o.get("side").getAsString()) ? 1 : 2;
         } else {
            return 0;
         }
      };
   }

   private boolean canStop(JsonObject u, int x, int z) {
      return Pathing.canStop(
         BattleClient.grid(),
         x,
         z,
         u.get("flies").getAsBoolean(),
         u.get("lava").getAsBoolean(),
         this.occupancy(u, u.get("side").getAsString()),
         u.get("x").getAsInt(),
         u.get("z").getAsInt()
      );
   }

   private boolean validTarget(JsonObject u, JsonObject target) {
      if (target != null && target.get("hp").getAsInt() > 0) {
         int d = Pathing.distance(this.destX, this.destZ, target.get("x").getAsInt(), target.get("z").getAsInt());
         boolean self = target.get("id").getAsInt() == u.get("id").getAsInt();
         if (self) {
            d = 0;
         }

         if (this.pending >= 0) {
            JsonObject m = u.getAsJsonArray("moves").get(this.pending).getAsJsonObject();
            return !mine(target) && d >= m.get("min").getAsInt() && d <= m.get("max").getAsInt();
         } else {
            return this.pending == -2 ? mine(target) && d <= 1 : mine(target) && !self && d == 1 && !target.get("player").getAsBoolean();
         }
      } else {
         return false;
      }
   }

   private double scale(double y) {
      double fov = Math.toRadians((double)this.minecraft.gameRenderer.mainCamera().getFov());
      double f = (double)this.height / 2.0 / Math.tan(fov / 2.0);
      return f / Math.max(0.5, BattleClient.camY() - y);
   }

   public double[] project(double wx, double wy, double wz) {
      double s = this.scale(wy);
      return new double[]{(double)this.width / 2.0 + (wx - BattleClient.camX()) * s, (double)this.height / 2.0 + (wz - BattleClient.camZ()) * s};
   }

   public double[] cellRect(int x, int z) {
      BattleGrid g = BattleClient.grid();
      double y = (double)g.y(x, z);
      double[] a = this.project((double)(g.originX + x), y, (double)(g.originZ + z));
      double[] b = this.project((double)(g.originX + x + 1), y, (double)(g.originZ + z + 1));
      return new double[]{a[0], a[1], b[0], b[1]};
   }

   public int[] cellAt(double mx, double my) {
      BattleGrid g = BattleClient.grid();
      if (g == null) {
         return null;
      } else {
         int[] best = null;
         int bestY = Integer.MIN_VALUE;

         for (int x = 0; x < g.size; x++) {
            for (int z = 0; z < g.size; z++) {
               double[] r = this.cellRect(x, z);
               if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3] && g.y(x, z) > bestY) {
                  bestY = g.y(x, z);
                  best = new int[]{x, z};
               }
            }
         }

         return best;
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (super.mouseClicked(event, doubleClick)) {
         return true;
      } else if (event.button() == 3) {
         this.back();
         return true;
      } else {
         if (event.button() == 1) {
            this.pressed = true;
            this.dragging = false;
            this.pressX = event.x();
            this.pressY = event.y();
         }

         return true;
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      if (this.pressed && event.button() == 1) {
         if (!this.dragging && Math.hypot(event.x() - this.pressX, event.y() - this.pressY) > 4.0) {
            this.dragging = true;
         }

         if (this.dragging && BattleClient.grid() != null) {
            double s = this.scale((double)BattleClient.grid().y(BattleClient.grid().size / 2, BattleClient.grid().size / 2));
            BattleClient.pan(-dx / s, -dy / s);
         }

         return true;
      } else {
         return super.mouseDragged(event, dx, dy);
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.pressed && event.button() == 1) {
         this.pressed = false;
         if (!this.dragging) {
            this.click(event.x(), event.y());
         }

         this.dragging = false;
         return true;
      } else {
         return super.mouseReleased(event);
      }
   }

   private void click(double mx, double my) {
      for (Entry<Integer, int[]> e : this.cards.entrySet()) {
         int[] r = e.getValue();
         if (mx >= (double)r[0] && mx < (double)r[2] && my >= (double)r[1] && my < (double)r[3]) {
            this.clickCard(e.getKey());
            return;
         }
      }

      int[] c = this.cellAt(mx, my);
      if (c != null) {
         this.clickCell(c[0], c[1]);
      }
   }

   private void clickCard(int id) {
      JsonObject t = unit(id);
      if (t != null) {
         int x = t.get("x").getAsInt();
         int z = t.get("z").getAsInt();
         BattleClient.centerOn(x, z);
         if (t.get("hp").getAsInt() > 0
            && (this.step == BattleScreen.Step.TARGET || this.step == BattleScreen.Step.ACTION || mine(t) && !t.get("acted").getAsBoolean())) {
            this.clickCell(x, z);
         }
      }
   }

   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      BattleGrid g = BattleClient.grid();
      if (g == null) {
         return true;
      } else {
         double s = this.scale((double)g.y(g.size / 2, g.size / 2));
         BattleClient.zoomAt(sy, BattleClient.camX() + (mx - (double)this.width / 2.0) / s, BattleClient.camZ() + (my - (double)this.height / 2.0) / s);
         return true;
      }
   }

   public boolean keyPressed(KeyEvent event) {
      int k = event.key();
      if (k == 41) {
         this.back();
         return true;
      } else {
         double d = Math.max(1.0, (BattleClient.camY() - BattleClient.minHeight()) / 6.0 + 1.0);
         if (k == 26 || k == 82) {
            BattleClient.pan(0.0, -d);
         } else if (k == 22 || k == 81) {
            BattleClient.pan(0.0, d);
         } else if (k == 4 || k == 80) {
            BattleClient.pan(-d, 0.0);
         } else if (k == 7 || k == 79) {
            BattleClient.pan(d, 0.0);
         } else if (k == 21) {
            BattleClient.resetView();
         } else {
            if (k != 44) {
               return super.keyPressed(event);
            }

            JsonObject u = unit(this.selected);
            if (u != null) {
               BattleClient.centerOn(u.get("x").getAsInt(), u.get("z").getAsInt());
            }
         }

         return true;
      }
   }

   private void back() {
      switch (this.step) {
         case ACTION:
            this.step = BattleScreen.Step.DESTINATION;
            break;
         case TARGET:
            this.step = BattleScreen.Step.ACTION;
            break;
         default:
            this.reset();
      }

      this.rebuild();
   }

   public void clickCell(int x, int z) {
      if ("PLAYER".equals(phase())) {
         JsonObject u = unit(this.selected);
         JsonObject there = unitAt(x, z);
         switch (this.step) {
            case UNIT:
            case DESTINATION:
               if (there == null
                  || !mine(there)
                  || there.get("acted").getAsBoolean()
                  || u != null && (there == u || there.get("id").getAsInt() == u.get("id").getAsInt())) {
                  if (u != null && this.reach(u).containsKey(x + z * BattleClient.grid().size) && this.canStop(u, x, z)) {
                     this.destX = x;
                     this.destZ = z;
                     this.step = BattleScreen.Step.ACTION;
                  } else if (u != null && there != null && !mine(there)) {
                     this.destX = u.get("x").getAsInt();
                     this.destZ = u.get("z").getAsInt();

                     for (int ix = 0; ix < u.getAsJsonArray("moves").size(); ix++) {
                        this.pending = ix;
                        if (this.validTarget(u, there)) {
                           this.sendAction("attack", there.get("id").getAsInt());
                           return;
                        }
                     }
                  } else {
                     this.reset();
                  }
               } else {
                  this.selected = there.get("id").getAsInt();
                  this.step = BattleScreen.Step.DESTINATION;
               }
               break;
            case ACTION:
               if (there != null && !mine(there)) {
                  for (int i = 0; i < u.getAsJsonArray("moves").size(); i++) {
                     this.pending = i;
                     if (this.validTarget(u, there)) {
                        this.sendAction("attack", there.get("id").getAsInt());
                        return;
                     }
                  }
               }
               break;
            case TARGET:
               if (u != null && this.validTarget(u, there)) {
                  this.sendAction(this.pending >= 0 ? "attack" : (this.pending == -2 ? "item" : "rally"), there.get("id").getAsInt());
               }
         }

         this.rebuild();
      }
   }

   private void sendAction(String kind, int target) {
      JsonObject a = new JsonObject();
      a.addProperty("kind", kind);
      a.addProperty("unit", this.selected);
      a.addProperty("x", this.destX);
      a.addProperty("z", this.destZ);
      a.addProperty("move", Math.max(this.pending, 0));
      a.addProperty("target", target);
      BattleClient.send(a);
      this.reset();
      this.rebuild();
   }

   public String debugState() {
      return "step "
         + this.step
         + ", selected "
         + this.selected
         + ", dest "
         + this.destX
         + ","
         + this.destZ
         + ", pending "
         + this.pending
         + ", phase "
         + phase()
         + ", widgets "
         + this.children().size();
   }

   public void chooseMove(int i) {
      this.pending = i;
      this.step = BattleScreen.Step.TARGET;
      this.rebuild();
   }

   public void waitHere() {
      this.sendAction("wait", -1);
   }

   public void endTurn() {
      JsonObject a = new JsonObject();
      a.addProperty("kind", "end_turn");
      BattleClient.send(a);
      this.reset();
   }

   public void flee() {
      JsonObject a = new JsonObject();
      a.addProperty("kind", "flee");
      BattleClient.send(a);
      this.reset();
   }

   public void recruit(int index) {
      JsonObject a = new JsonObject();
      a.addProperty("kind", "recruit");
      a.addProperty("index", index);
      BattleClient.send(a);
   }

   public void leave() {
      JsonObject a = new JsonObject();
      a.addProperty("kind", "leave");
      BattleClient.send(a);
   }

   private void rebuild() {
      this.clearWidgets();
      if (state() != null) {
         if (state().has("result")) {
            JsonObject r = state().getAsJsonObject("result");
            JsonArray rec = r.getAsJsonArray("recruits");
            int y = this.resultTop() + 84;

            for (int i = 0; i < rec.size(); i++) {
               JsonObject d = rec.get(i).getAsJsonObject();
               int idx = i;
               String rarity = d.has("rarity") ? d.get("rarity").getAsString() + " " : "";
               boolean blocked = d.has("why");
               Button rb = (Button)this.addRenderableWidget(
                  Button.builder(
                        Component.literal(
                           "Recruit " + rarity + d.get("name").getAsString() + " Lv." + d.get("level").getAsInt() + " (" + d.get("cost").getAsInt() + " XP)"
                        ),
                        b -> this.recruit(idx)
                     )
                     .bounds(this.width / 2 - 140, y + i * 22, 280, 20)
                     .build()
               );
               if (blocked) {
                  rb.active = false;
                  rb.setTooltip(Tooltip.create(Component.literal("Can't recruit: " + d.get("why").getAsString())));
                  rb.setMessage(Component.literal(d.get("name").getAsString() + " Lv." + d.get("level").getAsInt() + ": " + d.get("why").getAsString()));
               }
            }

            this.addRenderableWidget(
               Button.builder(Component.literal("Leave battle"), b -> this.leave()).bounds(this.width / 2 - 60, y + rec.size() * 22 + 6, 120, 20).build()
            );
         } else {
            boolean myTurn = "PLAYER".equals(phase());
            Button end = (Button)this.addRenderableWidget(
               Button.builder(Component.literal("End turn"), b -> this.endTurn()).bounds(this.width - 86, this.height - 26, 80, 20).build()
            );
            Button run = (Button)this.addRenderableWidget(
               Button.builder(Component.literal("Flee"), b -> this.flee()).bounds(this.width - 86, this.height - 50, 80, 20).build()
            );
            end.active = myTurn;
            run.active = true;
            JsonObject u = unit(this.selected);
            if (myTurn && u != null && this.step == BattleScreen.Step.ACTION) {
               List<String> labels = new ArrayList<>();
               List<Runnable> actions = new ArrayList<>();
               JsonArray moves = u.getAsJsonArray("moves");

               for (int ix = 0; ix < moves.size(); ix++) {
                  JsonObject m = moves.get(ix).getAsJsonObject();
                  int min = m.get("min").getAsInt();
                  int max = m.get("max").getAsInt();
                  int idx = ix;
                  labels.add(m.get("name").getAsString() + "  " + (min == max ? min + "" : min + "-" + max));
                  actions.add(() -> this.chooseMove(idx));
               }

               if (u.get("player").getAsBoolean()) {
                  labels.add("Item");
                  actions.add(() -> {
                     this.pending = -2;
                     this.step = BattleScreen.Step.TARGET;
                     this.rebuild();
                  });
                  labels.add("Rally");
                  actions.add(() -> {
                     this.pending = -3;
                     this.step = BattleScreen.Step.TARGET;
                     this.rebuild();
                  });
               }

               labels.add("Wait");
               actions.add(this::waitHere);
               int[] at = this.menuPos(labels.size());

               for (int ix = 0; ix < labels.size(); ix++) {
                  Runnable act = actions.get(ix);
                  this.addRenderableWidget(Button.builder(Component.literal(labels.get(ix)), b -> act.run()).bounds(at[0], at[1] + ix * 18, 96, 17).build());
               }
            }
         }
      }
   }

   private int[] menuPos(int items) {
      double[] r = this.cellRect(this.destX, this.destZ);
      int cell = (int)Math.ceil(r[2] - r[0]);
      int x = (int)r[2] + cell + 4;
      int y = (int)r[1] - 4;
      if (x + 96 > this.width - 90) {
         x = (int)r[0] - cell - 4 - 96;
      }

      return new int[]{Math.clamp((long)x, 128, Math.max(128, this.width - 90 - 96)), Math.clamp((long)y, 96, Math.max(96, this.height - 30 - items * 18))};
   }

   private int resultTop() {
      int recruits = state().getAsJsonObject("result").getAsJsonArray("recruits").size();
      return Math.max(24, this.height / 2 - (84 + recruits * 22 + 30) / 2);
   }

   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
   }

   public void tick() {
      super.tick();
      long now = Util.getMillis();
      this.popups.removeIf(p -> now - p.start() > 1400L);
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      if (state() != null && BattleClient.grid() != null) {
         BattleGrid grid = BattleClient.grid();
         boolean over = this.cards.values().stream().anyMatch(r -> mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3]);
         int[] hover = over ? null : this.cellAt((double)mouseX, (double)mouseY);
         this.hoverX = hover == null ? -1 : hover[0];
         this.hoverZ = hover == null ? -1 : hover[1];
         JsonObject u = unit(this.selected);
         boolean result = state().has("result");
         if (u != null && "PLAYER".equals(phase()) && !result) {
            if (this.step == BattleScreen.Step.DESTINATION) {
               for (int cell : this.reach(u).keySet()) {
                  int x = cell % grid.size;
                  int z = cell / grid.size;
                  if (this.canStop(u, x, z)) {
                     this.fillCell(g, x, z, 1430031615);
                  }
               }
            } else {
               this.fillCell(g, this.destX, this.destZ, 2000988927);
               if (this.step == BattleScreen.Step.TARGET || this.step == BattleScreen.Step.ACTION) {
                  for (JsonObject t : units()) {
                     if (this.step == BattleScreen.Step.ACTION) {
                        for (int i = 0; i < u.getAsJsonArray("moves").size(); i++) {
                           this.pending = i;
                           if (this.validTarget(u, t)) {
                              this.fillCell(g, t.get("x").getAsInt(), t.get("z").getAsInt(), 1728004160);
                           }
                        }

                        this.pending = 0;
                     } else if (this.validTarget(u, t)) {
                        this.fillCell(g, t.get("x").getAsInt(), t.get("z").getAsInt(), this.pending >= 0 ? -1996537792 : -2009006240);
                     }
                  }
               }
            }
         }

         if (this.hoverX >= 0 && !result) {
            this.outlineCell(g, this.hoverX, this.hoverZ, -1, 1);
         }

         long now = Util.getMillis();

         for (JsonObject tx : units()) {
            if (tx.get("hp").getAsInt() > 0) {
               int x = tx.get("x").getAsInt();
               int z = tx.get("z").getAsInt();
               int id = tx.get("id").getAsInt();
               boolean acted = mine(tx) && tx.get("acted").getAsBoolean();
               int frame = mine(tx) ? (acted ? -10456928 : -11884289) : -44976;
               if (id == this.selected) {
                  frame = now / 250L % 2L == 0L ? -8128 : -96;
               } else if (id == this.hoveredCard) {
                  frame = -1;
               }

               this.fillCell(g, x, z, mine(tx) ? (acted ? 809517152 : 809274623) : 822034496);
               this.outlineCell(g, x, z, frame, 2);
               double[] r = this.cellRect(x, z);
               int bw = Math.max(16, (int)(r[2] - r[0]));
               int bx = (int)((r[0] + r[2]) / 2.0) - bw / 2;
               int by = (int)r[1] - 7;
               double frac = tx.get("hp").getAsDouble() / tx.get("max").getAsDouble();
               g.fill(bx - 1, by - 1, bx + bw + 1, by + 4, -536870912);
               g.fill(bx, by, bx + (int)Math.round((double)bw * frac), by + 3, hpColor(frac, mine(tx)));
            }
         }

         for (BattleScreen.Popup p : this.popups) {
            double age = (double)(now - p.start()) / 1400.0;
            double[] at = this.project(p.wx(), p.wy() + 1.0, p.wz());
            int alpha = (int)(255.0 * Math.clamp(1.4 - age * 1.4, 0.0, 1.0));
            if (alpha >= 8) {
               g.pose().pushMatrix();
               g.pose().translate((float)at[0], (float)(at[1] - 10.0 - age * 24.0));
               g.pose().scale(1.5F, 1.5F);
               g.centeredText(this.font, p.text(), 0, 0, alpha << 24 | p.color() & 16777215);
               g.pose().popMatrix();
            }
         }

         if (!result) {
            boolean mineTurn = "PLAYER".equals(phase());
            String banner = mineTurn ? "YOUR TURN" : "ENEMY TURN";
            String sub = "Turn " + state().get("turn").getAsInt();
            int bw = Math.max(170, this.font.width(banner) * 2 + 40);
            int bx = this.width / 2 - bw / 2;
            int by = 16;
            int c1 = mineTurn ? -534754672 : -527425504;
            int c2 = mineTurn ? -2146424752 : -2142236656;
            g.fillGradient(bx, by, bx + bw, by + 28, c1, c2);
            g.fill(bx, by + 28, bx + bw, by + 29, mineTurn ? -8402689 : -32640);
            g.pose().pushMatrix();
            g.pose().translate((float)this.width / 2.0F, (float)(by + 2));
            g.pose().scale(2.0F, 2.0F);
            g.centeredText(this.font, banner, 0, 0, -1);
            g.pose().popMatrix();
            g.centeredText(this.font, sub, this.width / 2, by + 19, -2236963);

            String hint = switch (this.step) {
               case UNIT -> mineTurn ? "Pick one of your creatures (or you)" : "The wild creatures are moving...";
               case DESTINATION -> "Click where it should move (or itself to stay)";
               case ACTION -> "Pick an action - or click an enemy in red";
               case TARGET -> this.pending >= 0 ? "Click a target in red" : "Click a unit in green";
            };
            int hw = this.font.width(hint) + 12;
            g.fill(this.width / 2 - hw / 2, by + 31, this.width / 2 + hw / 2, by + 43, -1610612736);
            g.centeredText(this.font, hint, this.width / 2, by + 33, -96);
         }

         this.cards.clear();
         this.hoveredCard = -1;
         if (!result) {
            this.drawRoster(g, units().stream().filter(BattleScreen::mine).toList(), 4, "YOUR TEAM", -8402689, mouseX, mouseY);
            this.drawRoster(g, units().stream().filter(txx -> !mine(txx)).toList(), this.width - 118 - 4, "WILD", -32640, mouseX, mouseY);
         }

         if (!result) {
            JsonArray log = state().getAsJsonArray("log");
            int lines = Math.min(3, log.size());
            int ly = 62;

            for (int ix = log.size() - lines; ix < log.size(); ix++) {
               String line = log.get(ix).getAsString();
               if (this.font.width(line) > this.width - 236 - 20) {
                  line = this.font.plainSubstrByWidth(line, this.width - 236 - 30) + "...";
               }

               int fade = ix == log.size() - 1 ? -1 : -7303024;
               int lw = this.font.width(line);
               g.fill(this.width / 2 - lw / 2 - 4, ly - 1, this.width / 2 + lw / 2 + 4, ly + 9, -1879048192);
               g.centeredText(this.font, line, this.width / 2, ly, fade);
               ly += 10;
            }
         }

         JsonObject info = u != null ? u : (this.hoveredCard >= 0 ? unit(this.hoveredCard) : unitAt(this.hoverX, this.hoverZ));
         if (info != null && !result) {
            this.drawDetails(g, info, 4, this.height - 4);
         }

         if (result) {
            this.drawResult(g);
         }

         super.extractRenderState(g, mouseX, mouseY, partialTick);
      }
   }

   private static int hpColor(double frac, boolean mine) {
      if (frac <= 0.25) {
         return -2080704;
      } else if (frac <= 0.5) {
         return -2047936;
      } else {
         return mine ? -12529568 : -2068416;
      }
   }

   private void drawRoster(GuiGraphicsExtractor g, List<JsonObject> side, int x, String title, int titleColor, int mouseX, int mouseY) {
      int y = 62;
      g.fill(x, y - 11, x + 118, y - 1, -1610612736);
      g.text(this.font, title, x + 4, y - 10, titleColor);

      for (JsonObject t : side) {
         int id = t.get("id").getAsInt();
         int hp = Math.max(0, t.get("hp").getAsInt());
         int max = t.get("max").getAsInt();
         boolean down = hp <= 0;
         boolean acted = mine(t) && t.get("acted").getAsBoolean();
         boolean hovered = mouseX >= x && mouseX < x + 118 && mouseY >= y && mouseY < y + 24;
         if (hovered) {
            this.hoveredCard = id;
         }

         this.cards.put(id, new int[]{x, y, x + 118, y + 24});
         int color = 0xFF000000 | t.get("color").getAsInt();
         g.fill(x, y, x + 118, y + 24, down ? -1340076016 : (hovered ? -802146232 : -1072688104));
         g.fill(x, y, x + 3, y + 24, t.get("player").getAsBoolean() ? -1 : color);
         if (id == this.selected) {
            g.outline(x, y, 118, 24, -8128);
         }

         String name = t.get("name").getAsString();
         String lv = t.get("player").getAsBoolean() ? "" : "Lv." + t.get("level").getAsInt();
         int room = 108 - this.font.width(lv);
         if (this.font.width(name) > room) {
            name = this.font.plainSubstrByWidth(name, room - 6) + "..";
         }

         g.text(this.font, name, x + 6, y + 3, down ? -8355712 : (acted ? -7300944 : -1));
         g.text(this.font, lv, x + 118 - 3 - this.font.width(lv), y + 3, -3355444);
         double frac = max <= 0 ? 0.0 : (double)hp / (double)max;
         String hpText = down ? "KO" : hp + "/" + max;
         int barEnd = x + 118 - 6 - this.font.width(hpText);
         g.fill(x + 6, y + 14, barEnd, y + 19, -13619152);
         g.fill(x + 6, y + 14, x + 6 + (int)Math.round((double)(barEnd - x - 6) * frac), y + 19, down ? -11513776 : hpColor(frac, mine(t)));
         g.text(this.font, hpText, x + 118 - 3 - this.font.width(hpText), y + 13, down ? -40864 : -2236963);
         if (acted && !down) {
            g.text(this.font, "done", x + 118 - 3 - this.font.width("done") - this.font.width(lv) - 4, y + 3, -9406320);
         }

         y += 26;
      }
   }

   private void drawDetails(GuiGraphicsExtractor g, JsonObject t, int x, int bottom) {
      List<String> moves = new ArrayList<>();

      for (JsonElement me : t.getAsJsonArray("moves")) {
         JsonObject m = me.getAsJsonObject();
         int min = m.get("min").getAsInt();
         int max = m.get("max").getAsInt();
         moves.add(
            m.get("name").getAsString()
               + "  pow "
               + m.get("power").getAsInt()
               + "  range "
               + (min == max ? min + "" : min + "-" + max)
               + "  "
               + m.get("kind").getAsString().toLowerCase(Locale.ROOT)
         );
      }

      String move = "Moves "
         + t.get("squares").getAsInt()
         + " squares"
         + (t.get("flies").getAsBoolean() ? ", flies" : "")
         + (t.get("lava").getAsBoolean() ? ", crosses lava" : "");
      int w = Math.max(150, this.font.width(move) + 10);

      for (String m : moves) {
         w = Math.max(w, this.font.width(m) + 14);
      }

      w = Math.min(w, this.width / 2 - 20);
      int h = 36 + moves.size() * 10 + 4;
      int y = bottom - h;
      int color = 0xFF000000 | t.get("color").getAsInt();
      g.fill(x, y, x + w, y + h, -804252648);
      g.fill(x, y, x + w, y + 13, t.get("player").getAsBoolean() ? -12564392 : color & 16777215 | -1073741824);
      String title = t.get("name").getAsString()
         + (t.get("player").getAsBoolean() ? "" : "  Lv." + t.get("level").getAsInt() + "  " + t.get("influence").getAsString());
      g.text(this.font, title, x + 4, y + 3, -1);
      int hp = Math.max(0, t.get("hp").getAsInt());
      int max = t.get("max").getAsInt();
      double frac = max <= 0 ? 0.0 : (double)hp / (double)max;
      String hpText = "HP " + hp + "/" + max;
      int barEnd = x + w - 8 - this.font.width(hpText);
      g.fill(x + 4, y + 17, barEnd, y + 22, -13619152);
      g.fill(x + 4, y + 17, x + 4 + (int)Math.round((double)(barEnd - x - 4) * frac), y + 22, hpColor(frac, mine(t)));
      g.text(this.font, hpText, x + w - 4 - this.font.width(hpText), y + 16, -2236963);
      g.text(this.font, move, x + 4, y + 25, -6303489);

      for (int i = 0; i < moves.size(); i++) {
         g.text(this.font, "• " + moves.get(i), x + 4, y + 36 + i * 10, -11649);
      }
   }

   private void drawResult(GuiGraphicsExtractor g) {
      JsonObject r = state().getAsJsonObject("result");
      String o = r.get("outcome").getAsString();
      boolean won = o.equals("WON");
      String title = won ? "VICTORY!" : (o.equals("LOST") ? "DEFEATED" : "ESCAPED");
      int recruits = r.getAsJsonArray("recruits").size();
      int w = 300;
      int x = this.width / 2 - w / 2;
      int y = this.resultTop();
      int h = 84 + recruits * 22 + 30;
      g.fill(x, y, x + w, y + h, -804252648);
      g.fillGradient(x, y, x + w, y + 30, won ? -257921008 : (o.equals("LOST") ? -260038624 : -264220576), 1610612736);
      g.pose().pushMatrix();
      g.pose().translate((float)this.width / 2.0F, (float)(y + 6));
      g.pose().scale(2.0F, 2.0F);
      g.centeredText(this.font, title, 0, 0, won ? -8080 : -1);
      g.pose().popMatrix();
      if (won) {
         g.centeredText(this.font, "+" + r.get("xp").getAsInt() + " XP to spend on your creatures or yourself", this.width / 2, y + 38, -1);
         StringBuilder drops = new StringBuilder();
         JsonArray loot = r.has("loot") ? r.getAsJsonArray("loot") : new JsonArray();

         for (int i = 0; i < loot.size(); i++) {
            drops.append(i > 0 ? ", " : "").append(loot.get(i).getAsString());
         }

         String line = loot.isEmpty() ? "No drops" : "Drops: " + drops;
         if (this.font.width(line) > w - 16) {
            line = this.font.plainSubstrByWidth(line, w - 26) + "...";
         }

         g.centeredText(this.font, line, this.width / 2, y + 52, -6299489);
         if (recruits > 0) {
            g.centeredText(this.font, "Recruit one of them (costs creature XP):", this.width / 2, y + 68, -5592406);
         }
      } else {
         g.centeredText(
            this.font, o.equals("LOST") ? "You were knocked out. Your creatures keep their damage." : "You got away safely.", this.width / 2, y + 44, -2236963
         );
      }
   }

   private void fillCell(GuiGraphicsExtractor g, int x, int z, int color) {
      double[] r = this.cellRect(x, z);
      g.fill((int)r[0] + 1, (int)r[1] + 1, (int)r[2], (int)r[3], color);
   }

   private void outlineCell(GuiGraphicsExtractor g, int x, int z, int color, int thickness) {
      double[] r = this.cellRect(x, z);

      for (int i = 0; i < thickness; i++) {
         g.outline((int)r[0] + i, (int)r[1] + i, (int)(r[2] - r[0]) - 2 * i, (int)(r[3] - r[1]) - 2 * i, color);
      }
   }

   private static record Popup(double wx, double wy, double wz, String text, int color, long start) {
   }

   private static enum Step {
      UNIT,
      DESTINATION,
      ACTION,
      TARGET;
   }
}
