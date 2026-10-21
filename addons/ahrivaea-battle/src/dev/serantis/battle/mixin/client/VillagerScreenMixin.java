package dev.serantis.battle.mixin.client;

import com.google.gson.JsonObject;
import dev.ahrivaea.client.VillagerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Villagers keep the profession they were born with, so there's no "Change profession" button; in its
 * place an Alchemist offers to turn a golden apple into XP.
 */
@Mixin(VillagerScreen.class)
public abstract class VillagerScreenMixin extends Screen {
    protected VillagerScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void serantis$buttons(CallbackInfo ci) {
        VillagerScreen self = (VillagerScreen) (Object) this;
        Button change = null;
        List<GuiEventListener> remove = new ArrayList<>();
        for (GuiEventListener c : children())
            if (c instanceof Button b) {
                String label = b.getMessage().getString();
                if (label.equals("Change profession") || label.startsWith("Start over in another")) { change = b; remove.add(b); }
            }
        for (GuiEventListener c : remove) removeWidget(c);

        JsonObject st = self.state();
        boolean alchemist = st != null && st.has("profession") && !st.get("profession").isJsonNull()
                && "alchemist".equals(st.get("profession").getAsString());
        if (!alchemist || change == null) return;
        Button apple = Button.builder(Component.literal("Golden apple → " + dev.serantis.battle.Villages.APPLE_XP + " XP"),
                        b -> self.send("serantis_apple_xp", o -> { }))
                .tooltip(Tooltip.create(Component.literal("Trade one golden apple from your inventory for "
                        + dev.serantis.battle.Villages.APPLE_XP + " XP.")))
                .bounds(change.getX(), change.getY(), change.getWidth(), change.getHeight()).build();
        addRenderableWidget(apple);
    }
}
