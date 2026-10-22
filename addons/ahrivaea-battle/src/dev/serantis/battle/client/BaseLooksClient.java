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
    private record Look(int x0, int x1, int z0, int z1, Biome biome) {}

    private static volatile List<Look> looks = List.of();
    private static String last = "";

    private BaseLooksClient() {}

    public static Biome biome(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        return mc.level.registryAccess().lookupOrThrow(Registries.BIOME).getValue(Identifier.parse("minecraft:" + id));
    }

    /** Every client tick: the looks changed, so read them again and redraw the world's colours. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        JsonObject st = TradeClient.state();
        JsonArray arr = st != null && st.has("serantis_base_looks") ? st.getAsJsonArray("serantis_base_looks") : new JsonArray();
        String key = (mc.level == null ? "" : mc.level.dimension().identifier().toString()) + arr;
        if (key.equals(last)) return;
        last = key;
        List<Look> out = new ArrayList<>();
        if (mc.level != null && mc.level.dimension() == Level.OVERWORLD)
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                Biome b = biome(o.get("biome").getAsString());
                if (b != null) out.add(new Look(o.get("x0").getAsInt(), o.get("x1").getAsInt(), o.get("z0").getAsInt(), o.get("z1").getAsInt(), b));
            }
        boolean changed = !out.equals(looks);
        looks = List.copyOf(out);
        if (changed && mc.level != null) mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, mc.gameRenderer.getMainCamera(), mc.getBlockColors());
    }

    /** The biome whose colours this spot takes, or null for its own. */
    public static Biome at(BlockPos pos) {
        List<Look> l = looks;
        if (l.isEmpty()) return null;
        int x = pos.getX(), z = pos.getZ();
        for (Look k : l) if (x >= k.x0() && x <= k.x1() && z >= k.z0() && z <= k.z1()) return k.biome();
        return null;
    }
}
