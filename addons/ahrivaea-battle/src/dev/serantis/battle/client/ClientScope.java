package dev.serantis.battle.client;

import net.minecraft.client.Minecraft;

/** "Shown once" screen flags belong to one world: joining another (or the same one again) starts them fresh. */
public final class ClientScope {
    private static Object connection;

    private ClientScope() {}

    public static void tick() {
        Object now = Minecraft.getInstance().getConnection();
        if (now == connection) return;
        connection = now;
        if (now == null) return;
        IntroScreen.resetForNewWorld();
        WelcomeScreen.resetForNewWorld();
        ClassScreen.resetForNewWorld();
        FigureScreen.resetForNewWorld();
        RiddleScreen.resetForNewWorld();
        RaidScreen.resetForNewWorld();
        CoopPromptScreen.resetForNewWorld();
        BattleFeel.reset();
        SerantisKeys.shapePicked = false;
    }
}
