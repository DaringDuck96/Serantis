package dev.ahrivaea.base;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.CaughtCreature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueOutput.TypedOutputList;
import net.minecraft.world.phys.AABB;

public class BaseCoreBlockEntity extends BlockEntity {
   public static final int[] CAP = new int[]{0, 5, 7, 9, 12, 15};
   public static final int MAX_LEVEL = 5;
   public static final int DOWN = 8;
   public static final int UP = 16;
   private UUID owner;
   private String ownerName = "";
   private int level = 1;
   private boolean chunkLoading = true;
   final List<Worker> workers = new ArrayList<>();
   final Set<BlockPos> crops = new LinkedHashSet<>();
   final Set<BlockPos> growables = new LinkedHashSet<>();
   final Set<BlockPos> trees = new LinkedHashSet<>();
   final Set<BlockPos> furnaces = new LinkedHashSet<>();
   final Set<BlockPos> containers = new LinkedHashSet<>();
   final Set<BlockPos> water = new LinkedHashSet<>();
   final Set<BlockPos> miningPosts = new LinkedHashSet<>();
   final Set<BlockPos> brewingStands = new LinkedHashSet<>();
   final Set<BlockPos> enchantingTables = new LinkedHashSet<>();
   final Set<BlockPos> dynamos = new LinkedHashSet<>();
   final Set<BlockPos> crafters = new LinkedHashSet<>();
   private int scanIndex;
   final Map<BlockPos, UUID> claimed = new HashMap<>();
   final Map<BlockPos, Quarry> quarries = new HashMap<>();
   final Map<BlockPos, Item> toPlant = new LinkedHashMap<>();
   private final Map<UUID, Integer> missingChecks = new HashMap<>();

   public static BaseCoreBlockEntity.Upgrade upgradeFrom(int level) {
      return switch (level) {
         case 1 -> new BaseCoreBlockEntity.Upgrade(Items.IRON_BLOCK, 4);
         case 2 -> new BaseCoreBlockEntity.Upgrade(Items.GOLD_BLOCK, 4);
         case 3 -> new BaseCoreBlockEntity.Upgrade(Items.DIAMOND_BLOCK, 2);
         case 4 -> new BaseCoreBlockEntity.Upgrade(Items.EMERALD_BLOCK, 4);
         default -> null;
      };
   }

   public Quarry quarry(BlockPos post) {
      return this.quarries.computeIfAbsent(post.immutable(), k -> {
         this.setChanged();
         return new Quarry();
      });
   }

   public boolean inQuarry(BlockPos p) {
      if (this.getLevel() instanceof ServerLevel sl) {
         for (BlockPos post : this.miningPosts) {
            if (Quarry.inHole(sl, post, p)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public BaseCoreBlockEntity(BlockPos pos, BlockState state) {
      super(Bases.BASE_CORE_ENTITY, pos, state);
   }

   public UUID owner() {
      return this.owner;
   }

   public String ownerName() {
      return this.ownerName;
   }

   public void setOwner(ServerPlayer p) {
      this.owner = p.getUUID();
      this.ownerName = p.getPlainTextName();
      this.setChanged();
      if (this.getLevel() instanceof ServerLevel sl) {
         Bases.updateLoader(sl, this);
      }
   }

   public int baseLevel() {
      return this.level;
   }

   public int chunks() {
      return this.level;
   }

   public int radius() {
      return this.level * 16 + 8;
   }

   public boolean chunkLoading() {
      return this.chunkLoading;
   }

   public void setChunkLoading(boolean on) {
      this.chunkLoading = on;
      this.setChanged();
      if (this.getLevel() instanceof ServerLevel sl) {
         Bases.updateLoader(sl, this);
      }
   }

   public int minX() {
      return (this.getBlockPos().getX() >> 4) - this.level << 4;
   }

   public int maxX() {
      return ((this.getBlockPos().getX() >> 4) + this.level << 4) + 15;
   }

   public int minZ() {
      return (this.getBlockPos().getZ() >> 4) - this.level << 4;
   }

   public int maxZ() {
      return ((this.getBlockPos().getZ() >> 4) + this.level << 4) + 15;
   }

   public int cap() {
      return CAP[this.level];
   }

   public List<Worker> workers() {
      return List.copyOf(this.workers);
   }

   public boolean contains(BlockPos p) {
      BlockPos c = this.getBlockPos();
      return p.getX() >= this.minX()
         && p.getX() <= this.maxX()
         && p.getZ() >= this.minZ()
         && p.getZ() <= this.maxZ()
         && p.getY() >= c.getY() - 8
         && p.getY() <= c.getY() + 16;
   }

   public AABB area() {
      BlockPos c = this.getBlockPos();
      return new AABB(
         (double)this.minX(), (double)(c.getY() - 8), (double)this.minZ(), (double)(this.maxX() + 1), (double)(c.getY() + 16 + 1), (double)(this.maxZ() + 1)
      );
   }

   public void setBaseLevel(int level) {
      this.level = Math.clamp((long)level, 1, 5);
      this.setChanged();
      if (this.getLevel() instanceof ServerLevel sl) {
         Bases.updateLoader(sl, this);
      }
   }

   public boolean isUpgradeItem(ItemStack stack) {
      BaseCoreBlockEntity.Upgrade u = upgradeFrom(this.level);
      return u != null && stack.is(u.item());
   }

   void tryUpgrade(ServerPlayer p, ItemStack stack) {
      BaseCoreBlockEntity.Upgrade u = upgradeFrom(this.level);
      if (u != null && stack.is(u.item())) {
         if (this.owner != null && !this.owner.equals(p.getUUID())) {
            p.sendOverlayMessage(Component.literal("This is " + this.ownerName + "'s base."));
         } else if (stack.getCount() < u.count() && !p.isCreative()) {
            p.sendOverlayMessage(
               Component.literal("Upgrading to level " + (this.level + 1) + " takes " + u.count() + " ").append(stack.getHoverName()).append(".")
            );
         } else {
            if (!p.isCreative()) {
               stack.shrink(u.count());
            }

            this.level++;
            this.setChanged();
            Bases.updateLoader((ServerLevel)this.level(), this);
            int side = 2 * this.level + 1;
            p.sendSystemMessage(
               Component.literal("Base upgraded to level " + this.level + ": " + side + "x" + side + " chunks, up to " + this.cap() + " workers.")
            );
            ((ServerLevel)this.level()).playSound(null, this.getBlockPos(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.0F);
         }
      }
   }

   private Level level() {
      return super.getLevel();
   }

   protected void saveAdditional(ValueOutput out) {
      super.saveAdditional(out);
      if (this.owner != null) {
         out.store("owner", UUIDUtil.CODEC, this.owner);
      }

      out.putString("owner_name", this.ownerName);
      out.putInt("base_level", this.level);
      out.putBoolean("chunk_loading", this.chunkLoading);
      TypedOutputList<BaseCoreBlockEntity.QuarryEntry> qs = out.list("quarries", BaseCoreBlockEntity.QuarryEntry.CODEC);
      this.quarries.forEach((pos, q) -> qs.add(new BaseCoreBlockEntity.QuarryEntry(pos, q)));
      TypedOutputList<Worker> list = out.list("workers", Worker.CODEC);

      for (Worker w : this.workers) {
         list.add(w);
      }
   }

   protected void loadAdditional(ValueInput in) {
      super.loadAdditional(in);
      this.owner = (UUID)in.read("owner", UUIDUtil.CODEC).orElse(null);
      this.ownerName = in.getStringOr("owner_name", "");
      this.level = Math.clamp((long)in.getIntOr("base_level", 1), 1, 5);
      this.chunkLoading = in.getBooleanOr("chunk_loading", true);
      this.quarries.clear();

      for (BaseCoreBlockEntity.QuarryEntry e : in.listOrEmpty("quarries", BaseCoreBlockEntity.QuarryEntry.CODEC)) {
         this.quarries.put(e.post(), e.quarry());
      }

      this.workers.clear();

      for (Worker w : in.listOrEmpty("workers", Worker.CODEC)) {
         this.workers.add(w);
      }
   }

   public void preRemoveSideEffects(BlockPos pos, BlockState state) {
      super.preRemoveSideEffects(pos, state);
      if (this.getLevel() instanceof ServerLevel sl) {
         Bases.forgetLoader(sl, pos);
      }
   }

   public void setRemoved() {
      super.setRemoved();
      if (this.getLevel() instanceof ServerLevel sl) {
         Bases.unindex(sl, this.getBlockPos());
      }
   }

   public static void serverTick(Level level, BlockPos pos, BlockState state, BaseCoreBlockEntity be) {
      ServerLevel sl = (ServerLevel)level;
      Bases.index(sl, pos);
      int side = (2 * be.level + 1) * 16;
      be.scan(sl, Math.max(2048, side * side * 25 / 200));
      if (sl.getGameTime() % 100L == 0L) {
         be.checkWorkers(sl);
         Bases.updateLoader(sl, be);
      }
   }

   void scan(ServerLevel level, int count) {
      int side = this.maxX() - this.minX() + 1;
      int height = 25;
      int total = side * side * height;
      BlockPos c = this.getBlockPos();
      MutableBlockPos p = new MutableBlockPos();

      for (int i = 0; i < count; i++) {
         int n = this.scanIndex++ % total;
         if (this.scanIndex >= total) {
            this.scanIndex = 0;
         }

         int y = n / (side * side);
         int rest = n % (side * side);
         p.set(this.minX() + rest % side, c.getY() - 8 + y, this.minZ() + rest / side);
         if (level.isLoaded(p)) {
            this.survey(level, p.immutable(), level.getBlockState(p));
         }
      }
   }

   private void survey(ServerLevel level, BlockPos p, BlockState s) {
      Set var10000;
      boolean var10002;
      label52: {
         set(this.crops, p, WorkerBrain.isCrop(level, p, s));
         var10000 = this.growables;
         if (s.getBlock() instanceof BonemealableBlock b
            && !s.is(BlockTags.LEAVES)
            && !s.is(Blocks.GRASS_BLOCK)
            && !s.is(Blocks.MOSS_BLOCK)
            && b.isValidBonemealTarget(level, p, s, BonemealSource.MOB)) {
            var10002 = true;
            break label52;
         }

         var10002 = false;
      }

      set(var10000, p, var10002);
      set(this.trees, p, s.is(BlockTags.LOGS) && level.getBlockState(p.below()).is(BlockTags.DIRT));
      BlockEntity be = s.hasBlockEntity() ? level.getBlockEntity(p) : null;
      set(this.furnaces, p, be instanceof AbstractFurnaceBlockEntity);
      set(this.containers, p, be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity);
      set(this.water, p, s.getFluidState().is(Fluids.WATER) && s.getFluidState().isSource() && level.getBlockState(p.above()).isAir());
      set(this.miningPosts, p, s.is(Bases.MINING_POST));
      set(this.brewingStands, p, be instanceof BrewingStandBlockEntity);
      set(this.enchantingTables, p, s.is(Blocks.ENCHANTING_TABLE));
      set(this.dynamos, p, s.is(Bases.CREATURE_DYNAMO));
      set(this.crafters, p, be instanceof CrafterBlockEntity);
   }

   private static void set(Set<BlockPos> set, BlockPos p, boolean in) {
      if (in) {
         set.add(p);
      } else {
         set.remove(p);
      }
   }

   private void checkWorkers(ServerLevel level) {
      for (int i = 0; i < this.workers.size(); i++) {
         Worker w = this.workers.get(i);
         if (level.getEntity(w.entity()) instanceof CreatureEntity) {
            this.missingChecks.remove(w.entity());
         } else {
            int misses = this.missingChecks.merge(w.entity(), 1, Integer::sum);
            if (misses >= 3) {
               this.missingChecks.remove(w.entity());
               CreatureEntity e = Bases.spawnWorker(level, this, w.creature());
               if (e != null) {
                  this.workers.set(i, w.withEntity(e.getUUID()));
               }

               this.setChanged();
            }
         }
      }
   }

   void update(UUID entity, CaughtCreature now) {
      for (int i = 0; i < this.workers.size(); i++) {
         if (this.workers.get(i).entity().equals(entity)) {
            this.workers.set(i, this.workers.get(i).withCreature(now));
         }
      }

      this.setChanged();
   }

   public void needsPlanting(BlockPos pos, Item item) {
      this.toPlant.put(pos.immutable(), item);
   }

   public Worker workerFor(UUID entity) {
      for (Worker w : this.workers) {
         if (w.entity().equals(entity)) {
            return w;
         }
      }

      return null;
   }

   void setWorker(int index, Worker w) {
      this.workers.set(index, w);
      this.setChanged();
   }

   boolean isWorker(UUID entity) {
      for (Worker w : this.workers) {
         if (w.entity().equals(entity)) {
            return true;
         }
      }

      return false;
   }

   void release(UUID entity) {
      this.claimed.values().removeIf(entity::equals);
   }

   Set<BlockPos> unclaimed(Set<BlockPos> set) {
      Set<BlockPos> out = new HashSet<>(set);
      out.removeAll(this.claimed.keySet());
      return out;
   }

   static record QuarryEntry(BlockPos post, Quarry quarry) {
      static final Codec<BaseCoreBlockEntity.QuarryEntry> CODEC = RecordCodecBuilder.create(
         i -> i.group(
                  BlockPos.CODEC.fieldOf("post").forGetter(BaseCoreBlockEntity.QuarryEntry::post),
                  Quarry.CODEC.fieldOf("quarry").forGetter(BaseCoreBlockEntity.QuarryEntry::quarry)
               )
               .apply(i, BaseCoreBlockEntity.QuarryEntry::new)
      );
   }

   public static record Upgrade(Item item, int count) {
   }
}
