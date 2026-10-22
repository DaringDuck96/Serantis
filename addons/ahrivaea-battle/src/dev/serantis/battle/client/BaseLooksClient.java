package dev.serantis.battle.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;

/**
 * Tints Overworld bases with the grass, leaf and water colours of the biome their owner picked (see BaseLooks on the
 * server, and mixin/client/BiomeColorsMixin). Purely on screen.
 */
public final class BaseLooksClient {
    /** A base's look: a real biome's colours, or a Nether palette (nether != null). */
    private record Look(int x0, int x1, int z0, int z1, Biome biome, Nether nether) {}

    /** The Nether looks: grass, leaves, water, and the particles that drift about (with how many a tick). */
    public record Nether(int grass, int foliage, int water, net.minecraft.core.particles.ParticleOptions particle, int perTick,
                         net.minecraft.world.level.block.Block ground) {}

    public static final java.util.Map<String, Nether> NETHER = java.util.Map.of(
            "nether_wastes", new Nether(0x8E3B2A, 0x7A2C1E, 0xD0561E, net.minecraft.core.particles.ParticleTypes.ASH, 1, net.minecraft.world.level.block.Blocks.NETHERRACK),
            "crimson_forest", new Nether(0xA3152B, 0x8E0F22, 0x7A1022, net.minecraft.core.particles.ParticleTypes.CRIMSON_SPORE, 6, net.minecraft.world.level.block.Blocks.CRIMSON_NYLIUM),
            "warped_forest", new Nether(0x168A8C, 0x14A59E, 0x2A8F9A, net.minecraft.core.particles.ParticleTypes.WARPED_SPORE, 6, net.minecraft.world.level.block.Blocks.WARPED_NYLIUM),
            "soul_sand_valley", new Nether(0x6B5E50, 0x584C42, 0x4FC3D6, net.minecraft.core.particles.ParticleTypes.ASH, 4, net.minecraft.world.level.block.Blocks.SOUL_SAND),
            "basalt_deltas", new Nether(0x5E5D66, 0x4A4952, 0x6C6B76, net.minecraft.core.particles.ParticleTypes.WHITE_ASH, 8, net.minecraft.world.level.block.Blocks.BASALT));

    private static volatile List<Look> looks = List.of();
    private static String last = "";

    private BaseLooksClient() {}

    public static Biome biome(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        return mc.level.registryAccess().lookupOrThrow(Registries.BIOME).getValue(Identifier.parse("minecraft:" + id));
    }

    /** Every client tick: a Nether look's particles drift around you while you're in it. */
    private static void particles(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.isPaused()) return;
        Look k = look(mc.player.blockPosition());
        if (k == null || k.nether() == null) return;
        var r = mc.level.getRandom();
        for (int i = 0; i < k.nether().perTick(); i++) {
            double x = mc.player.getX() + (r.nextDouble() - 0.5) * 32, z = mc.player.getZ() + (r.nextDouble() - 0.5) * 32;
            if (x < k.x0() || x > k.x1() + 1 || z < k.z0() || z > k.z1() + 1) continue;
            double y = mc.player.getY() + (r.nextDouble() - 0.3) * 16;
            mc.level.addParticle(k.nether().particle(), x, y, z, 0, 0, 0);
        }
    }

    /** Every client tick: the looks changed, so read them again and redraw the world's colours. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        particles(mc);
        JsonObject st = TradeClient.state();
        JsonArray arr = st != null && st.has("serantis_base_looks") ? st.getAsJsonArray("serantis_base_looks") : new JsonArray();
        String key = (mc.level == null ? "" : mc.level.dimension().identifier().toString()) + arr;
        if (key.equals(last)) return;
        last = key;
        List<Look> out = new ArrayList<>();
        if (mc.level != null && mc.level.dimension() == Level.OVERWORLD)
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String id = o.get("biome").getAsString();
                Nether n = NETHER.get(id);
                Biome b = n == null ? biome(id) : null;
                if (b != null || n != null) out.add(new Look(o.get("x0").getAsInt(), o.get("x1").getAsInt(), o.get("z0").getAsInt(), o.get("z1").getAsInt(), b, n));
            }
        boolean changed = !out.equals(looks);
        looks = List.copyOf(out);
        if (changed && mc.level != null) mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
    }

    private static Look look(BlockPos pos) {
        List<Look> l = looks;
        if (l.isEmpty()) return null;
        int x = pos.getX(), z = pos.getZ();
        for (Look k : l) if (x >= k.x0() && x <= k.x1() && z >= k.z0() && z <= k.z1()) return k;
        return null;
    }

    /** A Nether look turns grass blocks into its ground (netherrack, nylium, soul sand, basalt) on screen; null: leave it. */
    public static net.minecraft.world.level.block.state.BlockState ground(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (!state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) return null;
        Look k = look(pos);
        return k == null || k.nether() == null ? null : k.nether().ground().defaultBlockState();
    }

    /** The colours this spot takes (kind 0 grass, 1 leaves, 2 dry leaves, 3 water), or -1 for its own. */
    public static int color(BlockPos pos, int kind) {
        Look k = look(pos);
        if (k == null) return -1;
        if (k.nether() != null) return switch (kind) { case 0 -> k.nether().grass(); case 3 -> k.nether().water(); default -> k.nether().foliage(); };
        return switch (kind) {
            case 0 -> k.biome().getGrassColor(pos.getX(), pos.getZ());
            case 1 -> k.biome().getFoliageColor();
            case 2 -> k.biome().getDryFoliageColor();
            default -> k.biome().getWaterColor();
        };
    }
}
