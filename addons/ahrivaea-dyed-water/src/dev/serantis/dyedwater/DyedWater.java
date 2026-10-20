package dev.serantis.dyedwater;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.ArrayList;
import java.util.List;

/**
 * Dyed water: one water fluid, block and bucket for each of the 16 dyes. A water bucket crafted
 * with a dye gives that colour's bucket (data/serantis_dyed_water/recipe). The fluids are in the
 * #minecraft:water tag, so they act like water everywhere; only the colour differs.
 */
public final class DyedWater implements ModInitializer {
    public static final String NS = "serantis_dyed_water";
    public static final List<Kind> KINDS = new ArrayList<>();

    /** Everything for one colour. */
    public static final class Kind {
        public final DyeColor dye;
        public FlowingFluid source, flowing;
        public Block block;
        public Item bucket;

        Kind(DyeColor dye) { this.dye = dye; }

        /** The colour the water is tinted, from the dye. */
        public int color() { return 0xFF000000 | (dye.getTextureDiffuseColor() & 0xFFFFFF); }
    }

    private static Identifier id(String path) { return Identifier.parse(NS + ":" + path); }

    @Override
    public void onInitialize() {
        for (DyeColor dye : DyeColor.values()) {
            Kind k = new Kind(dye);
            String name = dye.getSerializedName() + "_water";
            k.source = new DyedWaterFluid.Source(k);
            k.flowing = new DyedWaterFluid.Flowing(k);
            Registry.register(BuiltInRegistries.FLUID, id(name), k.source);
            Registry.register(BuiltInRegistries.FLUID, id("flowing_" + name), k.flowing);

            ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id(name));
            k.block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
                    new LiquidBlock(k.source, BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).setId(blockKey)) { });

            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id(name + "_bucket"));
            k.bucket = Registry.register(BuiltInRegistries.ITEM, itemKey,
                    new BucketItem(k.source, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1).setId(itemKey)));
            KINDS.add(k);
        }
    }

    public static boolean isDyedBucket(Item item) {
        for (Kind k : KINDS) if (k.bucket == item) return true;
        return false;
    }
}
