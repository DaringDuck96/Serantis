package dev.serantis.battle;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Every fifth night (nights 5, 10, 15...) is a Blood Moon: the moon rises red (client: mixin/client/SkyMoonMixin)
 * and everyone in the Overworld is told as night falls.
 */
public final class BloodMoon {
    public static final int EVERY = 5;
    private static MinecraftServer seenFor;
    private static long announced = -1;

    private BloodMoon() {}

    /** The Overworld day number (0 = the first day) and the time of day, from the world's clock. */
    private static long day(Level level) { return level.getOverworldClockTime() / 24000L; }
    private static long timeOfDay(Level level) { return Math.floorMod(level.getOverworldClockTime(), 24000L); }

    /** Tonight is a Blood Moon (from dusk until dawn). */
    public static boolean tonight(Level level) {
        return level != null && level.dimension() == Level.OVERWORLD && day(level) % EVERY == EVERY - 1 && timeOfDay(level) >= 12000;
    }

    /** Every few seconds: tell the Overworld when a Blood Moon rises. */
    public static synchronized void tick(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        if (seenFor != server) { seenFor = server; announced = -1; }
        ServerLevel ow = server.overworld();
        if (!tonight(ow) || announced == day(ow)) return;
        announced = day(ow);
        if (timeOfDay(ow) > 14000) return;                              // already well into the night (just joined): no fanfare
        for (ServerPlayer q : ow.players()) {
            q.sendSystemMessage(Component.literal("A Blood Moon rises...").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            q.level().playSound(null, q.getX(), q.getY(), q.getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1f, 0.6f);
        }
    }
}
