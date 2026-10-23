package dev.serantis.battle;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.base.Bases;
import dev.serantis.battle.mixin.BaseCoreAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Mining Posts anywhere in a base, at any height. The core's own survey only looks from 8 blocks below it to 16
 * above, so a post on a hill, down in a valley or in a cave was never found and its miners stood idle. Every 5
 * seconds the base looks through just the chunk sections that hold a Mining Post (cheap: most hold none).
 */
public final class MiningPosts {
    private MiningPosts() {}

    public static void find(ServerLevel level, BaseCoreBlockEntity be) {
        var posts = ((BaseCoreAccess) (Object) be).serantis$miningPosts();
        posts.removeIf(p -> level.isLoaded(p) && !level.getBlockState(p).is(Bases.MINING_POST));   // broken since
        for (int cx = be.minX() >> 4; cx <= be.maxX() >> 4; cx++)
            for (int cz = be.minZ() >> 4; cz <= be.maxZ() >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection s = sections[i];
                    if (s == null || s.hasOnlyAir() || !s.maybeHas(st -> st.is(Bases.MINING_POST))) continue;
                    int baseY = level.getSectionYFromSectionIndex(i) << 4;
                    for (int y = 0; y < 16; y++)
                        for (int z = 0; z < 16; z++)
                            for (int x = 0; x < 16; x++) {
                                if (!s.getBlockState(x, y, z).is(Bases.MINING_POST)) continue;
                                BlockPos p = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
                                if (be.contains(p) && posts.add(p)) be.setChanged();
                            }
                }
            }
    }
}
