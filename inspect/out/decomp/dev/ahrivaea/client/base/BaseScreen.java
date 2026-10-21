package dev.ahrivaea.client.base;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.base.Bases;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BaseScreen extends Screen {
   static final int TOP = 14;
   static final int Y0 = 72;
   private JsonObject state;
   private int tick;
   private int boxScroll;
   private int workerScroll;
   private int jobsFor = -1;

   private int workerRows() {
      return Math.max(1, (this.height - 72 - 34) / 26);
   }

   public BaseScreen(JsonObject state) {
      super(Component.literal("Base"));
      this.state = state;
   }

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(Bases.StatePayload.TYPE, (payload, ctx) -> {
         JsonObject o = JsonParser.parseString(payload.json()).getAsJsonObject();
         Minecraft mc = ctx.client();
         if (mc.gui.screen() instanceof BaseScreen s) {
            s.update(o);
         } else {
            mc.gui.setScreen(new BaseScreen(o));
         }
      });
   }

   public JsonObject state() {
      return this.state;
   }

   void update(JsonObject o) {
      this.state = o;
      this.rebuildWidgets();
   }

   public void send(String kind, int index) {
      this.send(kind, index, null);
   }

   public void send(String kind, int index, String job) {
      JsonObject a = new JsonObject();
      a.addProperty("kind", kind);
      a.addProperty("index", index);
      if (job != null) {
         a.addProperty("job", job);
      }

      a.add("pos", this.state.getAsJsonArray("pos"));
      ClientPlayNetworking.send(new Bases.ActionPayload(a.toString()));
   }

   public void showJobs(int worker) {
      this.jobsFor = worker;
      this.rebuildWidgets();
   }

   private JsonArray jobList() {
      JsonArray workers = this.state.getAsJsonArray("workers");
      if (this.jobsFor >= 0 && this.jobsFor < workers.size()) {
         JsonObject w = workers.get(this.jobsFor).getAsJsonObject();
         return w.has("jobList") ? w.getAsJsonArray("jobList") : new JsonArray();
      } else {
         return null;
      }
   }

   protected void init() {
      JsonArray jobs = this.jobList();
      if (jobs != null) {
         int x = this.width / 2 - 220;

         for (int i = 0; i < jobs.size(); i++) {
            JsonObject job = jobs.get(i).getAsJsonObject();
            String id = job.get("id").getAsString();
            int y = 72 + i * 40;
            boolean on = job.get("on").getAsBoolean();
            this.addRenderableWidget(
               Button.builder(Component.literal(on ? "On" : "Off"), bx -> this.send("job_toggle", this.jobsFor, id)).bounds(x, y, 40, 20).build()
            );
            Button up = (Button)this.addRenderableWidget(
               Button.builder(Component.literal("▲"), bx -> this.send("job_up", this.jobsFor, id)).bounds(x + 44, y, 20, 20).build()
            );
            up.active = i > 0;
            Button down = (Button)this.addRenderableWidget(
               Button.builder(Component.literal("▼"), bx -> this.send("job_down", this.jobsFor, id)).bounds(x + 66, y, 20, 20).build()
            );
            down.active = i < jobs.size() - 1;
         }

         JsonObject w = this.state.getAsJsonArray("workers").get(this.jobsFor).getAsJsonObject();
         if (w.has("silk")) {
            boolean silk = w.get("silk").getAsBoolean();
            boolean can = w.get("silkLevel").getAsBoolean();
            Button b = (Button)this.addRenderableWidget(
               Button.builder(Component.literal("Silk touch: " + (!can ? "at level 20" : (silk ? "On" : "Off"))), x2 -> this.send("silk_toggle", this.jobsFor))
                  .tooltip(
                     Tooltip.create(
                        Component.literal("Level 20 and up: stone, ore blocks and glowstone come out whole instead of as cobblestone, raw ore and dust.")
                     )
                  )
                  .bounds(x, 72 + jobs.size() * 40 + 8, 130, 20)
                  .build()
            );
            b.active = can;
         }

         this.addRenderableWidget(
            Button.builder(Component.literal("Back"), bx -> this.showJobs(-1)).bounds(this.width / 2 - 40, this.height - 26, 80, 20).build()
         );
      } else {
         this.jobsFor = -1;
         int left = this.width / 2 - 250;
         int right = this.width / 2 + 10;
         JsonArray workers = this.state.getAsJsonArray("workers");
         this.workerScroll = Math.clamp((long)this.workerScroll, 0, Math.max(0, workers.size() - this.workerRows()));

         for (int i = this.workerScroll; i < Math.min(workers.size(), this.workerScroll + this.workerRows()); i++) {
            this.addRenderableWidget(
               Button.builder(Component.literal("Jobs"), bx -> this.showJobs(i))
                  .tooltip(Tooltip.create(Component.literal("Switch its jobs on or off, and choose which comes first")))
                  .bounds(left + 128, 72 + (i - this.workerScroll) * 26, 40, 18)
                  .build()
            );
            this.addRenderableWidget(
               Button.builder(Component.literal("Send away"), bx -> this.send("unassign", i))
                  .bounds(left + 170, 72 + (i - this.workerScroll) * 26, 70, 18)
                  .build()
            );
         }

         boolean full = workers.size() >= this.state.get("cap").getAsInt();
         int y = 72;
         JsonArray party = this.state.getAsJsonArray("party");

         for (int i = 0; i < party.size(); i++) {
            Button b = (Button)this.addRenderableWidget(
               Button.builder(Component.literal("Work here"), x -> this.send("assign_party", i)).bounds(right + 170, y + i * 26, 70, 18).build()
            );
            b.active = !full;
         }

         JsonArray box = this.state.getAsJsonArray("box");
         int boxTop = y + Math.max(1, party.size()) * 26 + 22;
         int rows = Math.max(1, (this.height - boxTop - 34) / 26);
         this.boxScroll = Math.clamp((long)this.boxScroll, 0, Math.max(0, box.size() - rows));

         for (int i = this.boxScroll; i < Math.min(box.size(), this.boxScroll + rows); i++) {
            int idx = i;
            Button b = (Button)this.addRenderableWidget(
               Button.builder(Component.literal("Work here"), x -> this.send("assign_box", idx))
                  .bounds(right + 170, boxTop + (i - this.boxScroll) * 26, 70, 18)
                  .build()
            );
            b.active = !full;
         }

         this.addRenderableWidget(Button.builder(Component.literal("Done"), bx -> this.onClose()).bounds(this.width / 2 - 40, this.height - 26, 80, 20).build());
         boolean loading = !this.state.has("loading") || this.state.get("loading").getAsBoolean();
         this.addRenderableWidget(
            Button.builder(Component.literal("Keep loaded: " + (loading ? "On" : "Off")), bx -> this.send("toggle_loading", -1))
               .tooltip(
                  Tooltip.create(
                     Component.literal(
                        "On: the whole base stays loaded and working while you're online, wherever you are. Turn it off for bases you don't need running."
                     )
                  )
               )
               .bounds(this.width / 2 - 200, this.height - 26, 150, 20)
               .build()
         );
      }
   }

   public boolean mouseScrolled(double x, double y, double dx, double dy) {
      if (x < (double)this.width / 2.0) {
         this.workerScroll = this.workerScroll - (int)Math.signum(dy);
      } else {
         this.boxScroll = this.boxScroll - (int)Math.signum(dy);
      }

      this.rebuildWidgets();
      return true;
   }

   public void tick() {
      if (++this.tick % 40 == 0) {
         this.send("refresh", -1);
      }
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(g, mouseX, mouseY, partialTick);
      JsonArray jobs = this.jobList();
      if (jobs != null) {
         JsonObject w = this.state.getAsJsonArray("workers").get(this.jobsFor).getAsJsonObject();
         g.centeredText(this.font, "Jobs: " + w.get("name").getAsString() + " Lv." + w.get("level").getAsInt(), this.width / 2, 24, -12224);
         g.centeredText(
            this.font, "It works on the first job in the list that has something to do. Jobs that are off are skipped.", this.width / 2, 36, -5592406
         );
         g.text(this.font, "Now: " + w.get("status").getAsString(), this.width / 2 - 220, 56, -6299489);
         if (w.has("speed")) {
            String speed = "Works at " + w.get("speed").getAsInt() + "% speed (100% at level 20, 2% more each level)";
            g.text(this.font, speed, this.width / 2 + 220 - this.font.width(speed), 56, -12224);
         }

         int x = this.width / 2 - 220 + 94;

         for (int i = 0; i < jobs.size(); i++) {
            JsonObject job = jobs.get(i).getAsJsonObject();
            boolean on = job.get("on").getAsBoolean();
            int y = 72 + i * 40;
            g.text(
               this.font,
               i + 1 + ". " + job.get("name").getAsString() + (job.get("works").getAsBoolean() ? "" : " (not a base job yet)"),
               x,
               y + 1,
               on ? -1 : -8947849
            );
            g.text(this.font, job.get("desc").getAsString(), x, y + 12, on ? -6305537 : -10066330);
         }

         if (jobs.isEmpty()) {
            g.text(this.font, "This creature has no jobs.", this.width / 2 - 220, 72, -5592406);
         }
      } else {
         int left = this.width / 2 - 250;
         int right = this.width / 2 + 10;
         JsonArray workers = this.state.getAsJsonArray("workers");
         int side = this.state.has("chunks") ? this.state.get("chunks").getAsInt() : 3;
         g.centeredText(this.font, "Base (level " + this.state.get("level").getAsInt() + ": " + side + " x " + side + " chunks)", this.width / 2, 24, -12224);
         g.centeredText(this.font, this.state.get("upgrade").getAsString(), this.width / 2, 36, -5592406);
         g.text(
            this.font,
            "Workers " + workers.size() + "/" + this.state.get("cap").getAsInt() + (workers.size() > this.workerRows() ? " (scroll for more)" : ""),
            left,
            56,
            -1
         );

         for (int i = this.workerScroll; i < Math.min(workers.size(), this.workerScroll + this.workerRows()); i++) {
            this.entry(g, workers.get(i).getAsJsonObject(), left, 72 + (i - this.workerScroll) * 26, true);
         }

         if (workers.isEmpty()) {
            g.text(this.font, "Nobody works here yet: pick creatures on the right.", left, 72, -5592406);
         }

         JsonArray party = this.state.getAsJsonArray("party");
         g.text(this.font, "Party", right, 56, -1);

         for (int i = 0; i < party.size(); i++) {
            this.entry(g, party.get(i).getAsJsonObject(), right, 72 + i * 26, false);
         }

         if (party.isEmpty()) {
            g.text(this.font, "(empty)", right, 72, -5592406);
         }

         JsonArray box = this.state.getAsJsonArray("box");
         int boxTop = 72 + Math.max(1, party.size()) * 26 + 22;
         g.text(this.font, "Box (" + box.size() + (box.size() > 0 ? ", scroll for more" : "") + ")", right, boxTop - 16, -1);
         int rows = Math.max(1, (this.height - boxTop - 34) / 26);

         for (int i = this.boxScroll; i < Math.min(box.size(), this.boxScroll + rows); i++) {
            this.entry(g, box.get(i).getAsJsonObject(), right, boxTop + (i - this.boxScroll) * 26, false);
         }
      }
   }

   private void entry(GuiGraphicsExtractor g, JsonObject c, int x, int y, boolean worker) {
      g.fill(x - 2, y - 2, x + 244, y + 22, Integer.MIN_VALUE);
      String title = c.get("name").getAsString() + " Lv." + c.get("level").getAsInt();
      if (worker && this.font.width(title) > 124) {
         title = this.font.plainSubstrByWidth(title, 119) + "...";
      }

      g.text(this.font, title, x, y, !worker && !c.get("works").getAsBoolean() ? -5592406 : -1);
      String sub = worker ? c.get("status").getAsString() : c.get("jobs").getAsString();
      int room = worker ? 122 : 165;
      if (this.font.width(sub) > room) {
         sub = this.font.plainSubstrByWidth(sub, room - 5) + "...";
      }

      g.text(this.font, sub, x, y + 11, worker ? -6299489 : -6305537);
   }

   public boolean isPauseScreen() {
      return false;
   }
}
