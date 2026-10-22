package dev.serantis.battle.mixin.client;

import com.google.gson.JsonObject;
import dev.ahrivaea.client.base.BaseScreen;
import dev.serantis.battle.client.SkillTreeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Skills button on your own Base Core's screen, and keeping the skill tree open as the base updates. */
@Mixin(BaseScreen.class)
public abstract class BaseScreenMixin extends Screen {
    protected BaseScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("RETURN"))
    private void serantis$skillsButton(CallbackInfo ci) {
        BaseScreen self = (BaseScreen) (Object) this;
        addRenderableWidget(Button.builder(Component.literal("Guide"),
                        btn -> Minecraft.getInstance().gui.setScreen(new dev.serantis.battle.client.WelcomeScreen(self, 1)))
                .tooltip(Tooltip.create(Component.literal("How creatures work at your base: their jobs, and what to expect from them.")))
                .bounds(width - 124, 2, 50, 18).build());
        JsonObject st = self.state();
        if (st != null && st.has("serantis_look") && st.has("pos")) {   // an Overworld base you can use: pick its look (see BaseLooks)
            var pos = st.getAsJsonArray("pos");
            var core = new net.minecraft.core.BlockPos(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt());
            String look = st.get("serantis_look").getAsString();
            addRenderableWidget(Button.builder(Component.literal("Look"), btn -> Minecraft.getInstance().gui.setScreen(new dev.serantis.battle.client.BaseLookScreen(self, core, look)))
                    .tooltip(Tooltip.create(Component.literal("Give this base the grass, leaf and water colours of any Overworld or Nether biome. Only the look changes.")))
                    .bounds(width - 178, 2, 50, 18).build());
        }
        if (st == null || !st.has("serantis_skills")) return;
        JsonObject s = st.getAsJsonObject("serantis_skills");
        if (!s.get("owner").getAsBoolean()) return;
        Button b = Button.builder(Component.literal("Skills"), btn -> Minecraft.getInstance().gui.setScreen(new SkillTreeScreen(self)))
                .bounds(width - 70, 2, 64, 18).build();
        if (s.get("baseLevel").getAsInt() < 1) {
            b.active = false;
            b.setTooltip(Tooltip.create(Component.literal("Upgrade this base to Level 1 to train your skills here.")));
        }
        addRenderableWidget(b);
    }

    // The size line under the title: bases are round, so how far across (see BaseShape), not a square of chunks.
    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;centeredText(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"), index = 1)
    private String serantis$roundSize(String text) {
        JsonObject st = ((BaseScreen) (Object) this).state();
        if (text == null || !text.startsWith("Base (level ") || st == null || !st.has("radius") || !st.has("level")) return text;
        int r = st.get("radius").getAsInt();
        return "Base (level " + st.get("level").getAsInt() + ": round, " + (2 * r) + " blocks across)";
    }

    @Redirect(method = "lambda$register$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private static void serantis$keepTree(Gui gui, Screen screen) {
        if (gui.screen() instanceof SkillTreeScreen tree && screen instanceof BaseScreen base) { tree.setParent(base); return; }
        if (gui.screen() instanceof dev.serantis.battle.client.WelcomeScreen guide && screen instanceof BaseScreen base) { guide.setParent(base); return; }
        if (gui.screen() instanceof dev.serantis.battle.client.BaseLookScreen look && screen instanceof BaseScreen base) { look.setParent(base); return; }   // keep picking
        gui.setScreen(screen);
    }
}
