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
                .bounds(width - 124, 6, 50, 20).build());
        JsonObject st = self.state();
        if (st == null || !st.has("serantis_skills")) return;
        JsonObject s = st.getAsJsonObject("serantis_skills");
        if (!s.get("owner").getAsBoolean()) return;
        Button b = Button.builder(Component.literal("Skills"), btn -> Minecraft.getInstance().gui.setScreen(new SkillTreeScreen(self)))
                .bounds(width - 70, 6, 64, 20).build();
        if (s.get("baseLevel").getAsInt() < 1) {
            b.active = false;
            b.setTooltip(Tooltip.create(Component.literal("Upgrade this base to Level 1 to train your skills here.")));
        }
        addRenderableWidget(b);
    }

    @Redirect(method = "lambda$register$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private static void serantis$keepTree(Gui gui, Screen screen) {
        if (gui.screen() instanceof SkillTreeScreen tree && screen instanceof BaseScreen base) { tree.setParent(base); return; }
        if (gui.screen() instanceof dev.serantis.battle.client.WelcomeScreen guide && screen instanceof BaseScreen base) { guide.setParent(base); return; }
        gui.setScreen(screen);
    }
}
