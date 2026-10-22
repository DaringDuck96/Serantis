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
 * and everyone in the Overworld is told as night falls. Bloodfang Shreekers (Rare, Normal) appear only then: on the
 * open surface (under the sky, never in caves or oceans) around players, and they're gone again by morning.
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
        shreekers(p, ow);
        if (!tonight(ow) || announced == day(ow)) return;
        announced = day(ow);
        if (timeOfDay(ow) > 14000) return;                              // already well into the night (just joined): no fanfare
        for (ServerPlayer q : ow.players()) {
            q.sendSystemMessage(Component.literal("A Blood Moon rises...").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            q.level().playSound(null, q.getX(), q.getY(), q.getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1f, 0.6f);
        }
    }

    // ------------------------------------------------------------------ Bloodfang Shreekers

    public static final String SHREEKER = "bloodfang_shreeker";
    /** Chance per check (every 2 seconds, per player) that one appears: about 4 to 5 over a Blood Moon night. */
    public static final double SHREEKER_CHANCE = 0.015;
    public static final int SHREEKER_NEAR = 2, MIN_DIST = 24, MAX_DIST = 48;

    private static boolean isShreeker(dev.ahrivaea.creature.CreatureEntity e) { return SHREEKER.equals(e.getSpeciesId()); }

    private static void shreekers(ServerPlayer p, ServerLevel ow) {
        if (p.level() != ow) return;
        var around = ow.getEntitiesOfClass(dev.ahrivaea.creature.CreatureEntity.class, p.getBoundingBox().inflate(96),
                e -> isShreeker(e) && !e.isTame() && !dev.ahrivaea.battle.Battles.inBattle(e) && !BattleHold.isHeld(e));
        if (!tonight(ow)) {                                             // morning: the wild ones have gone
            if (ow.isDarkOutside()) return;
            for (var e : around) e.discard();
            return;
        }
        if (around.size() >= SHREEKER_NEAR || ow.getRandom().nextDouble() >= SHREEKER_CHANCE) return;
        var species = dev.ahrivaea.creature.Species.get(SHREEKER).orElse(null);
        if (species == null) return;
        var r = ow.getRandom();
        for (int tries = 0; tries < 12; tries++) {
            double a = r.nextDouble() * Math.PI * 2, d = MIN_DIST + r.nextDouble() * (MAX_DIST - MIN_DIST);
            int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
            if (!ow.isLoaded(new net.minecraft.core.BlockPos(x, 0, z))) continue;
            var pos = new net.minecraft.core.BlockPos(x, ow.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!ow.canSeeSky(pos) || !ow.getFluidState(pos).isEmpty() || !ow.getFluidState(pos.below()).isEmpty()) continue;
            if (ow.getBiome(pos).is(net.minecraft.tags.BiomeTags.IS_OCEAN)) continue;
            if (ow.getBlockState(pos.below()).getCollisionShape(ow, pos.below()).isEmpty()) continue;
            if (dev.ahrivaea.base.Bases.inAnyBaseColumn(ow, pos)) continue;
            int me = dev.ahrivaea.character.PlayerCharacter.get(p).level();
            int lo = Math.max(1, LevelBand.lo(me)), hi = Math.min(20, Math.max(lo, LevelBand.hi(me)));
            int lvl = lo + r.nextInt(hi - lo + 1);
            var e = dev.ahrivaea.Ahrivaea.spawn(ow, species, dev.ahrivaea.creature.Influence.NONE, lvl, -1f, x + 0.5, pos.getY(), z + 0.5);
            if (e != null) e.setVariant(dev.ahrivaea.creature.Variant.roll(ow, r));
            return;
        }
    }
}
