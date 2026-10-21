package dev.serantis.battle;

import dev.ahrivaea.challenge.ChallengeDef;
import dev.ahrivaea.challenge.ChallengeState;
import dev.ahrivaea.challenge.Challenges;
import dev.ahrivaea.challenge.StrongholdTowers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * "Atlas of the Fallen Towers": every player's starting book, listing every tower (one stands over each
 * stronghold) within 10,000 blocks of the world spawn, nearest first, with its X and Z. The Mysterious Figure hands
 * it over in the opening scene; players who never see that scene (or were here before) get it on their next login.
 */
public final class TowerAtlas {
    public static final String TAG = "serantis_tower_atlas";
    public static final int RANGE = 10_000, PER_PAGE = 4;

    private TowerAtlas() {}

    private record Tower(String name, int x, int z, int dist, boolean rebuilt) {}

    /** Gives the book once. False if the tower positions aren't known yet (try again later). */
    public static boolean give(ServerPlayer p) {
        if (p.entityTags().contains(TAG)) return true;
        ItemStack book = book(p.level().getServer().overworld());
        if (book == null) return false;
        p.addTag(TAG);
        if (!p.getInventory().add(book) && !book.isEmpty())
            p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), book));
        return true;
    }

    private static List<ChunkPos> rings(ServerLevel level) {
        StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(BuiltinStructureSets.STRONGHOLDS);
        if (set == null || !(set.placement() instanceof ConcentricRingsStructurePlacement rings)) return List.of();
        List<ChunkPos> list = level.getChunkSource().getGeneratorState().getRingPositionsFor(rings);
        return list == null ? List.of() : list;
    }

    static ItemStack book(ServerLevel level) {
        List<ChunkPos> rings = rings(level);
        if (rings.isEmpty()) return null;
        BlockPos spawn = level.getRespawnData().pos();
        List<Tower> towers = new ArrayList<>();
        for (int i = 0; i < rings.size(); i++) {
            ChunkPos cp = rings.get(i);
            ChallengeState st = Challenges.state(level.getServer(), StrongholdTowers.towerId(i)).orElse(null);
            int x = st != null ? st.origin.getX() : cp.getMiddleBlockX(), z = st != null ? st.origin.getZ() : cp.getMiddleBlockZ();
            int dist = (int) Math.round(Math.sqrt((double) (x - spawn.getX()) * (x - spawn.getX()) + (double) (z - spawn.getZ()) * (z - spawn.getZ())));
            if (dist > RANGE) continue;
            String name = ChallengeDef.forTower(i).map(ChallengeDef::name).orElse("Tower " + (i + 1));
            towers.add(new Tower(name, x, z, dist, st != null && st.complete));
        }
        towers.sort(Comparator.comparingInt(Tower::dist));

        List<Filterable<Component>> pages = new ArrayList<>();
        pages.add(Filterable.passThrough(Component.literal("\nAtlas of the Fallen Towers").withStyle(ChatFormatting.BOLD)
                .append(Component.literal("\n\nOnce the First Dragon's watchposts. Now ruins, waiting to be rebuilt.\n\n" + towers.size()
                        + " towers stand within " + String.format("%,d", RANGE) + " blocks of where you woke, nearest first.")
                        .withStyle(st -> st.withBold(false)))));
        for (int i = 0; i < towers.size(); i += PER_PAGE) {
            var page = Component.empty();
            for (int k = i; k < Math.min(towers.size(), i + PER_PAGE); k++) {
                Tower t = towers.get(k);
                if (k > i) page.append(Component.literal("\n\n"));
                page.append(Component.literal((k + 1) + ". " + t.name()).withStyle(ChatFormatting.BOLD));
                if (t.rebuilt()) page.append(Component.literal(" (rebuilt)").withStyle(ChatFormatting.DARK_GREEN));
                page.append(Component.literal("\nX " + t.x() + ", Z " + t.z() + "\n" + String.format("%,d", t.dist()) + " blocks away"));
            }
            pages.add(Filterable.passThrough(page));
        }
        pages.add(Filterable.passThrough(Component.literal("Each tower stands over a stronghold. Bring what it asks for and offer it: "
                + "the first to offer claims it, and anyone can help rebuild it. A finished tower opens the way to the End.\n\nFind the towers. Save Serantia.")));

        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Atlas of the Fallen Towers"), "Mysterious Figure", 0, pages, true));
        return stack;
    }
}
