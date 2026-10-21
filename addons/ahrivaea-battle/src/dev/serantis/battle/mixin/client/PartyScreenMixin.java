package dev.serantis.battle.mixin.client;

import dev.ahrivaea.client.PartyScreen;
import dev.serantis.battle.BattleStats;
import com.google.gson.JsonObject;
import dev.serantis.battle.client.AppleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(PartyScreen.class)
public abstract class PartyScreenMixin extends Screen {
    protected PartyScreenMixin(Component title) { super(title); }

    // The party screen's feed button turns off at the max level. In init() the number 50 appears three
    // times: two button positions, then the "level >= 50" check (the third one) changed here.
    // Creatures max out at Level 20.
    @ModifyConstant(method = "details", constant = @Constant(stringValue = "Stats (now / at Lv.50)"))
    private String serantis$statsLabel(String original) {
        return "Stats (now / at Lv.20)";
    }

    @ModifyConstant(method = "init", constant = @Constant(intValue = 50, ordinal = 2))
    private int serantis$feedButtonCap(int original) {
        return BattleStats.MAX_LEVEL;
    }

    @Shadow private static int selected;
    @Shadow private JsonObject chosen() { return null; }

    // Party creatures get a "Golden Apple" button (above Release) that opens the stat picker.
    // The party tab is the one with a "Send out" button; its row of buttons starts there.
    @Inject(method = "init", at = @At("TAIL"))
    private void serantis$appleButton(CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        Button sendOut = null;
        for (var child : children())
            if (child instanceof Button b && "Send out".equals(b.getMessage().getString())) sendOut = b;
        addRenderableWidget(Button.builder(Component.literal("Achievements"),
                        b -> Minecraft.getInstance().gui.setScreen(new dev.serantis.battle.client.AchievementScreen(self)))
                .bounds(width - 96, 6, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Guide"),
                        b -> Minecraft.getInstance().gui.setScreen(new dev.serantis.battle.client.WelcomeScreen(self)))
                .tooltip(Tooltip.create(Component.literal("What Serantis is about, and what to do.")))
                .bounds(width - 150, 6, 50, 20).build());
        if (sendOut == null || chosen() == null) return;
        int slot = selected;
        Button apple = Button.builder(Component.literal("Apple"),
                        b -> Minecraft.getInstance().gui.setScreen(new AppleScreen(self, this::chosen, slot)))
                .tooltip(Tooltip.create(Component.literal("Feed a golden apple to raise one stat. Common +2, Uncommon +1, Rare +0.5, Super Rare +0.25, Legendary no effect. 10 apples per stat.")))
                .bounds(sendOut.getX() + 185, sendOut.getY() - 24, 59, 20).build();
        addRenderableWidget(apple);
    }
}
