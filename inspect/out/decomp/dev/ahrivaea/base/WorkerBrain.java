package dev.ahrivaea.base;

import dev.ahrivaea.base.mixin.BrewingAccess;
import dev.ahrivaea.base.mixin.CrafterAccess;
import dev.ahrivaea.base.mixin.FurnaceAccess;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Drops;
import dev.ahrivaea.creature.Job;
import dev.ahrivaea.creature.Species;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootParams.Builder;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WorkerBrain {
   private int studies;
   private final CreatureEntity self;
   private final BlockPos core;
   private WorkerBrain.Task task;
   private BlockPos plantAt;
   private int sortSlot;
   private int transportTrips;
   private int workTicks;
   private int travelTicks;
   private int cooldown;
   private int ranchTimer = 600;
   private ItemStack carried = ItemStack.EMPTY;
   private String status = "Getting settled";
   private double lastDist = Double.MAX_VALUE;
   public static int TENDED;
   private int restTicks;
   private CreatureEntity fighting;
   private int attackCooldown;
   public static final int PAR_LEVEL = 20;
   public static final double PER_LEVEL = 0.02;
   public static final int SILK_TOUCH_LEVEL = 20;
   private double extraProgress;
   private BlockPos minePost;
   private String waiting;
   private static final Map<Block, Block> SAPLINGS = Map.of(
      Blocks.OAK_LOG,
      Blocks.OAK_SAPLING,
      Blocks.SPRUCE_LOG,
      Blocks.SPRUCE_SAPLING,
      Blocks.BIRCH_LOG,
      Blocks.BIRCH_SAPLING,
      Blocks.JUNGLE_LOG,
      Blocks.JUNGLE_SAPLING,
      Blocks.ACACIA_LOG,
      Blocks.ACACIA_SAPLING,
      Blocks.DARK_OAK_LOG,
      Blocks.DARK_OAK_SAPLING,
      Blocks.CHERRY_LOG,
      Blocks.CHERRY_SAPLING,
      Blocks.PALE_OAK_LOG,
      Blocks.PALE_OAK_SAPLING,
      Blocks.MANGROVE_LOG,
      Blocks.MANGROVE_PROPAGULE
   );

   public WorkerBrain(CreatureEntity self, BlockPos core) {
      this.self = self;
      this.core = core;
   }

   public String status() {
      return this.status;
   }

   public void knockOut() {
      this.restTicks = 1200;
      this.fighting = null;
      if (this.task != null) {
         BaseCoreBlockEntity be = Bases.coreAt((ServerLevel)this.self.level(), this.core);
         if (be != null) {
            this.finish(be, 20);
         } else {
            this.task = null;
         }
      }

      this.self.getNavigation().stop();
   }

   public boolean resting() {
      return this.restTicks > 0;
   }

   public BlockPos powering() {
      return this.task != null && this.task.kind() == WorkerBrain.Kind.POWER && this.workTicks > 0 ? this.task.target() : null;
   }

   public BlockPos core() {
      return this.core;
   }

   private List<Job> allJobs() {
      return Species.get(this.self.getSpeciesId()).map(s -> s.jobsFor(this.self.getInfluence())).orElse(List.of());
   }

   private List<Job> jobs(BaseCoreBlockEntity be) {
      Worker w = be.workerFor(this.self.getUUID());
      return w == null ? this.allJobs() : w.active(this.allJobs());
   }

   public static double speedAt(int level) {
      return Math.max(0.1, 1.0 + 0.02 * (double)(level - 20));
   }

   private boolean silkTouch(BaseCoreBlockEntity be) {
      Worker w = be.workerFor(this.self.getUUID());
      return this.self.getCreatureLevel() >= 20 && (w == null || !w.off().contains("silk_touch"));
   }

   public double speed() {
      return speedAt(this.self.getCreatureLevel());
   }

   private int scaled(int ticks) {
      return Math.max(2, (int)Math.round((double)ticks / this.speed()));
   }

   private int extraTicks() {
      this.extraProgress = this.extraProgress + (this.speed() - 1.0);
      int whole = (int)this.extraProgress;
      this.extraProgress -= (double)whole;
      return whole;
   }

   public void tick(ServerLevel level) {
      BaseCoreBlockEntity be = Bases.coreAt(level, this.core);
      if (be == null) {
         this.status = "Its base is gone";
      } else {
         if (!be.contains(this.self.blockPosition())
            && !be.inQuarry(this.self.blockPosition())
            && this.self.distanceToSqr(Vec3.atCenterOf(this.core)) > Math.pow((double)(be.radius() + 8), 2.0)) {
            this.self.teleportTo((double)this.core.getX() + 1.5, (double)(this.core.getY() + 1), (double)this.core.getZ() + 0.5);
            this.drop();
         }

         if (this.restTicks > 0) {
            this.restTicks--;
            this.status = "Knocked out, resting (" + (this.restTicks / 20 + 1) + "s)";
            if (this.restTicks % 20 == 0) {
               this.self.heal(this.self.getMaxHealth() / 60.0F);
            }
         } else {
            this.ranch(level, be);
            if (!this.jobs(be).contains(Job.DEFENSE) || !this.defend(level, be)) {
               if (this.task == null) {
                  if (--this.cooldown > 0) {
                     return;
                  }

                  this.task = this.plan(level, be);
                  this.workTicks = this.travelTicks = 0;
                  this.lastDist = Double.MAX_VALUE;
                  if (this.task == null) {
                     this.cooldown = 40;
                     this.status = this.waiting != null && this.jobs(be).contains(Job.MINING)
                        ? this.waiting
                        : (
                           this.jobs(be).contains(Job.DEFENSE)
                              ? "On guard"
                              : (
                                 this.jobs(be).stream().anyMatch(Job::working)
                                    ? "Idle (nothing to do)"
                                    : (this.allJobs().stream().anyMatch(Job::working) ? "Idle (its jobs are switched off)" : "Idle (no work it can do yet)")
                              )
                        );
                     this.wander(level, be);
                     return;
                  }

                  if (this.task.target() != null) {
                     be.claimed.put(this.task.target(), this.self.getUUID());
                  }
               }

               Vec3 where = this.task.item() != null ? this.task.item().position() : Vec3.atCenterOf(this.task.target());
               if (this.task.item() != null && !this.task.item().isAlive()) {
                  this.finish(be, 10);
               } else {
                  double reach = (
                        this.task.kind() == WorkerBrain.Kind.MINE
                           ? 4.0
                           : (
                              this.task.kind() == WorkerBrain.Kind.FELL
                                 ? 3.5
                                 : (this.task.kind() != WorkerBrain.Kind.FISH && this.task.kind() != WorkerBrain.Kind.ICE ? 2.3 : 3.0)
                           )
                     )
                     + (double)(this.self.getBbWidth() / 2.0F)
                     - 0.35;
                  double d = this.self.position().distanceTo(where);
                  if (d > reach) {
                     this.status = "On the way: " + this.task.doing();
                     if (++this.travelTicks % 20 == 1) {
                        this.self.getNavigation().moveTo(where.x, where.y, where.z, 1.1);
                     }

                     if (d < this.lastDist - 0.5) {
                        this.lastDist = d;
                        this.travelTicks = Math.min(this.travelTicks, 1);
                     }

                     if (this.task.kind() == WorkerBrain.Kind.MINE && this.travelTicks > 100) {
                        BlockPos stand = this.standNear(level, this.task.target());
                        if (stand != null) {
                           this.self.getNavigation().stop();
                           this.self.teleportTo((double)stand.getX() + 0.5, (double)stand.getY(), (double)stand.getZ() + 0.5);
                           this.travelTicks = 1;
                           return;
                        }
                     }

                     if (this.travelTicks > 300) {
                        this.finish(be, 100);
                     }
                  } else {
                     this.self.getNavigation().stop();
                     this.self.getLookControl().setLookAt(where.x, where.y, where.z);
                     this.status = this.task.doing();
                     this.workTicks++;
                     if (this.task.kind() == WorkerBrain.Kind.SMELT) {
                        if (!this.smeltTick(level, this.task.target()) || this.workTicks > 1200) {
                           this.finish(be, 20);
                        }
                     } else if (this.task.kind() != WorkerBrain.Kind.BREW) {
                        if (this.task.kind() == WorkerBrain.Kind.POWER) {
                           if (level.getBlockState(this.task.target()).is(Bases.CREATURE_DYNAMO) && this.workTicks <= 2400) {
                              if (this.workTicks % 20 == 1) {
                                 CreatureDynamoBlock.power(level, this.task.target());
                              }

                              if (this.workTicks % this.scaled(1200) == 0) {
                                 output(level, this.task.target(), new ItemStack(Items.REDSTONE));
                              }
                           } else {
                              this.finish(be, 20);
                           }
                        } else {
                           if (this.workTicks % 10 == 0) {
                              level.sendParticles(ParticleTypes.HAPPY_VILLAGER, where.x, where.y + 0.5, where.z, 2, 0.3, 0.3, 0.3, 0.0);
                           }

                           if (this.workTicks >= this.task.duration()) {
                              this.complete(level, be, this.task);
                           }
                        }
                     } else {
                        if (!this.brewTick(level, this.task.target()) || this.workTicks > 1200) {
                           this.finish(be, 20);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void finish(BaseCoreBlockEntity be, int wait) {
      if (this.task != null && this.task.target() != null) {
         be.claimed.remove(this.task.target());
      }

      this.task = null;
      this.cooldown = wait;
   }

   private WorkerBrain.Task plan(ServerLevel level, BaseCoreBlockEntity be) {
      if (!this.carried.isEmpty() && this.plantAt != null) {
         if (be.toPlant.get(this.plantAt) == this.carried.getItem()) {
            return new WorkerBrain.Task(
               WorkerBrain.Kind.PLANT, this.plantAt, null, this.scaled(10), "Planting " + Drops.describe(this.carried.copyWithCount(1))
            );
         }

         this.plantAt = null;
      }

      if (!this.carried.isEmpty()) {
         BlockPos chest = this.chestFor(level, be, this.carried);
         if (chest != null) {
            return new WorkerBrain.Task(WorkerBrain.Kind.DELIVER, chest, null, this.scaled(10), "Putting " + Drops.describe(this.carried) + " away");
         }

         this.drop();
      }

      for (Job job : this.jobs(be)) {
         if (job == Job.FARMING || job == Job.LOGGING) {
            WorkerBrain.Task replant = this.replantTask(level, be, job == Job.LOGGING);
            if (replant != null) {
               return replant;
            }
         }
         WorkerBrain.Task t = switch (job) {
            case FARMING -> this.nearest(
            level,
            be,
            be.crops,
            p -> isRipe(level, p, level.getBlockState(p)),
            p -> new WorkerBrain.Task(WorkerBrain.Kind.HARVEST, p, null, this.scaled(20), "Harvesting " + name(level, p))
         );
            case GROWTH -> this.any(
            level,
            be,
            be.growables,
            p -> {
               if (level.getBlockState(p).getBlock() instanceof BonemealableBlock b
                  && b.isValidBonemealTarget(level, p, level.getBlockState(p), BonemealSource.MOB)) {
                  return true;
               }

               return false;
            },
            p -> new WorkerBrain.Task(WorkerBrain.Kind.TEND, p, null, this.scaled(40), "Tending " + name(level, p))
         );
            case LOGGING -> this.nearest(
            level,
            be,
            be.trees,
            p -> naturalTree(level, p) != null,
            p -> new WorkerBrain.Task(WorkerBrain.Kind.FELL, p, null, this.scaled(15 * Math.max(1, sizeOf(naturalTree(level, p))) + 40), "Felling a tree")
         );
            case MINING -> this.quarryTask(level, be);
            case SMELTING -> this.nearest(
            level,
            be,
            be.furnaces,
            p -> needsSmelting(level, p),
            p -> new WorkerBrain.Task(WorkerBrain.Kind.SMELT, p, null, 0, "Running the " + name(level, p))
         );
            case FISHING -> this.nearest(
            level, be, be.water, p -> level.getFluidState(p).isSource(), p -> new WorkerBrain.Task(WorkerBrain.Kind.FISH, p, null, this.scaled(400), "Fishing")
         );
            case TRANSPORT -> this.transportTask(level, be);
            case BREWING -> this.nearest(
            level, be, be.brewingStands, p -> needsBrewing(level, p), p -> new WorkerBrain.Task(WorkerBrain.Kind.BREW, p, null, 0, "Brewing potions")
         );
            case ENCHANTING -> this.nearest(
            level,
            be,
            be.enchantingTables,
            p -> level.getBlockState(p).is(Blocks.ENCHANTING_TABLE),
            p -> new WorkerBrain.Task(WorkerBrain.Kind.STUDY, p, null, this.scaled(1200), "Studying at the enchanting table")
         );
            case REDSTONE -> this.nearest(
            level,
            be,
            be.dynamos,
            p -> level.getBlockState(p).is(Bases.CREATURE_DYNAMO),
            p -> new WorkerBrain.Task(WorkerBrain.Kind.POWER, p, null, 0, "Powering the Creature Dynamo")
         );
            case CRAFTING -> this.nearest(level, be, be.crafters, p -> {
            if (level.getBlockEntity(p) instanceof CrafterBlockEntity c && !c.isEmpty()) {
               return true;
            }

            return false;
         }, p -> new WorkerBrain.Task(WorkerBrain.Kind.CRAFT, p, null, this.scaled(20), "Working the crafter"));
            case ICE -> this.nearest(
            level,
            be,
            be.water,
            p -> level.getFluidState(p).isSource(),
            p -> new WorkerBrain.Task(WorkerBrain.Kind.ICE, p, null, this.scaled(300), "Making ice")
         );
            case LIGHTING -> this.lightingTask(level, be);
            default -> null;
         };
         if (t != null) {
            return t;
         }
      }

      return null;
   }

   private WorkerBrain.Task any(ServerLevel level, BaseCoreBlockEntity be, Set<BlockPos> set, WorkerBrain.Check check, WorkerBrain.Make make) {
      List<BlockPos> options = new ArrayList<>(be.unclaimed(set));
      Collections.shuffle(options, new Random(level.getRandom().nextLong()));

      for (BlockPos p : options) {
         if (level.isLoaded(p) && check.ok(p)) {
            return make.make(p);
         }
      }

      return null;
   }

   private WorkerBrain.Task nearest(ServerLevel level, BaseCoreBlockEntity be, Set<BlockPos> set, WorkerBrain.Check check, WorkerBrain.Make make) {
      BlockPos best = null;
      double bestD = Double.MAX_VALUE;

      for (BlockPos p : be.unclaimed(set)) {
         double d = p.distSqr(this.self.blockPosition());
         if (d < bestD && level.isLoaded(p) && check.ok(p)) {
            best = p;
            bestD = d;
         }
      }

      return best == null ? null : make.make(best);
   }

   private WorkerBrain.Task replantTask(ServerLevel level, BaseCoreBlockEntity be, boolean saplings) {
      Iterator<Entry<BlockPos, Item>> it = be.toPlant.entrySet().iterator();

      while (it.hasNext()) {
         Entry<BlockPos, Item> e = it.next();
         BlockPos spot = e.getKey();
         if (level.isLoaded(spot)) {
            if (level.getBlockState(spot).isAir() && e.getValue() instanceof BlockItem bi && bi.getBlock().defaultBlockState().canSurvive(level, spot)) {
               boolean isSapling = new ItemStack((ItemLike)e.getValue()).is(ItemTags.SAPLINGS);
               if (isSapling == saplings && !be.claimed.containsKey(spot)) {
                  for (BlockPos c : be.unclaimed(be.containers)) {
                     Container box = level.isLoaded(c) ? HopperBlockEntity.getContainerAt(level, c) : null;
                     if (box != null && Sorting.holds(box, new ItemStack((ItemLike)e.getValue()))) {
                        this.plantAt = spot.immutable();
                        be.claimed.put(spot, this.self.getUUID());
                        return new WorkerBrain.Task(
                           WorkerBrain.Kind.FETCH,
                           c,
                           null,
                           this.scaled(10),
                           "Fetching " + Drops.describe(new ItemStack((ItemLike)e.getValue())) + " to replant"
                        );
                     }
                  }
               }
            } else {
               it.remove();
            }
         }
      }

      return null;
   }

   private WorkerBrain.Task quarryTask(ServerLevel level, BaseCoreBlockEntity be) {
      this.waiting = null;
      List<BlockPos> posts = new ArrayList<>(be.miningPosts);
      posts.sort(Comparator.comparingDouble(p -> p.distSqr(this.self.blockPosition())));

      for (BlockPos post : posts) {
         if (level.isLoaded(post) && level.getBlockState(post).is(Bases.MINING_POST)) {
            Quarry q = be.quarry(post);
            if (!q.paused && q.phase != 2) {
               List<Container> chests = Quarry.chests(level, post);
               if (chests.isEmpty()) {
                  q.needsChest = true;
                  this.waiting = "Waiting: its Mining Post needs a chest next to it";
                  Bases.alertNoChest(level, be, post, q);
               } else {
                  BlockPos target = q.next(level, post, this.self.blockPosition(), p -> !be.claimed.containsKey(p));
                  be.setChanged();
                  if (target != null) {
                     BlockState s = level.getBlockState(target);
                     if (Quarry.fits(chests, Quarry.drops(level, target, s, this.silkTouch(be)))) {
                        q.needsChest = false;
                        this.minePost = post;
                        float hardness = Math.max(0.0F, s.getDestroySpeed(level, target));
                        return new WorkerBrain.Task(
                           WorkerBrain.Kind.MINE,
                           target,
                           null,
                           this.scaled(Math.min(200, 8 + (int)(10.0F * hardness))),
                           "Digging " + name(level, target) + (q.phase == 0 ? " (the staircase)" : " (layer y=" + target.getY() + ")")
                        );
                     }

                     q.needsChest = true;
                     this.waiting = "Waiting: the quarry's chests are full, it needs another chest";
                     Bases.alertNoChest(level, be, post, q);
                  }
               }
            }
         }
      }

      return null;
   }

   private BlockPos standNear(ServerLevel level, BlockPos target) {
      BlockPos best = null;
      double bestD = Double.MAX_VALUE;

      for (BlockPos p : BlockPos.betweenClosed(target.offset(-2, -3, -2), target.offset(2, 1, 2))) {
         if (!p.equals(target)
            && !p.above().equals(target)
            && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
            && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
            && level.getFluidState(p).isEmpty()
            && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()
            && !(Vec3.atCenterOf(p).distanceTo(Vec3.atCenterOf(target)) > 3.5)) {
            double d = p.distSqr(this.self.blockPosition());
            if (d < bestD) {
               best = p.immutable();
               bestD = d;
            }
         }
      }

      return best;
   }

   private WorkerBrain.Task transportTask(ServerLevel level, BaseCoreBlockEntity be) {
      if (be.containers.isEmpty()) {
         return null;
      } else {
         if (++this.transportTrips % 3 == 0) {
            WorkerBrain.Task sort = this.sortTask(level, be);
            if (sort != null) {
               return sort;
            }
         }

         List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, be.area(), i -> i.isAlive() && !i.hasPickUpDelay() && i.getAge() > 40);
         items.removeIf(i -> be.claimed.containsKey(i.blockPosition()));
         items.sort(Comparator.comparingDouble(i -> i.distanceToSqr(this.self)));
         if (!items.isEmpty()) {
            ItemEntity i = items.getFirst();
            return new WorkerBrain.Task(WorkerBrain.Kind.PICK_UP, i.blockPosition(), i, this.scaled(5), "Picking up " + Drops.describe(i.getItem()));
         } else {
            WorkerBrain.Task furnace = this.nearest(level, be, be.furnaces, p -> {
               if (level.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity f && !f.getItem(2).isEmpty()) {
                  return true;
               }

               return false;
            }, p -> new WorkerBrain.Task(WorkerBrain.Kind.EMPTY_FURNACE, p, null, this.scaled(10), "Emptying the " + name(level, p)));
            return furnace != null ? furnace : this.sortTask(level, be);
         }
      }
   }

   private WorkerBrain.Task sortTask(ServerLevel level, BaseCoreBlockEntity be) {
      Sorting.Misplaced m = Sorting.misplaced(level, be.unclaimed(be.containers), be.containers, this.self.blockPosition());
      if (m == null) {
         return null;
      } else {
         this.sortSlot = m.slot();
         Container from = HopperBlockEntity.getContainerAt(level, m.from());
         return new WorkerBrain.Task(
            WorkerBrain.Kind.SORT, m.from(), null, this.scaled(10), "Sorting " + (from == null ? "a chest" : Drops.describe(from.getItem(m.slot())))
         );
      }
   }

   private void complete(ServerLevel level, BaseCoreBlockEntity be, WorkerBrain.Task t) {
      switch (t.kind()) {
         case HARVEST:
            this.harvest(level, be, t.target());
            break;
         case TEND:
            TENDED++;
            BlockState sxx = level.getBlockState(t.target());
            if (sxx.getBlock() instanceof BonemealableBlock b
               && level.getRandom().nextBoolean()
               && b.isValidBonemealTarget(level, t.target(), sxx, BonemealSource.MOB)) {
               b.performBonemeal(level, level.getRandom(), t.target(), sxx, BonemealSource.MOB);
               level.levelEvent(null, 1505, t.target(), 15);
               break;
            }

            for (int ix = 0; ix < 4 && level.getBlockState(t.target()) == sxx; ix++) {
               sxx.randomTick(level, t.target(), level.getRandom());
            }
            break;
         case FELL:
            this.fell(level, be, t.target());
            break;
         case MINE:
            Quarry q = this.minePost == null ? null : be.quarry(this.minePost);
            BlockState sx = level.getBlockState(t.target());
            if (q != null && q.keep(level, t.target(), sx) == null) {
               List<Container> chests = Quarry.chests(level, this.minePost);
               List<ItemStack> drops = Quarry.drops(level, t.target(), sx, this.silkTouch(be));
               if (!chests.isEmpty() && Quarry.fits(chests, drops)) {
                  if (sx.hasBlockEntity() && level.getBlockEntity(t.target()) instanceof Container cxx) {
                     cxx.clearContent();
                  }

                  level.levelEvent(2014, t.target(), Block.getId(sx));
                  level.setBlock(t.target(), Blocks.AIR.defaultBlockState(), 3);
                  Quarry.store(chests, drops);
                  q.mined++;
                  be.setChanged();
               }
            }
         case SMELT:
         case BREW:
         case POWER:
         default:
            break;
         case PICK_UP:
            if (t.item().isAlive()) {
               this.carried = t.item().getItem().copy();
               t.item().discard();
            }
            break;
         case DELIVER:
            Container c = HopperBlockEntity.getContainerAt(level, t.target());
            if (c != null) {
               this.carried = HopperBlockEntity.addItem(null, c, this.carried, null);
            }

            if (!this.carried.isEmpty() && this.chestFor(level, be, this.carried) == null) {
               this.drop();
            }
            break;
         case EMPTY_FURNACE:
            if (level.getBlockEntity(t.target()) instanceof AbstractFurnaceBlockEntity f) {
               this.carried = f.removeItem(2, 64);
            }
            break;
         case FISH:
            LootParams params = new Builder(level)
               .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(t.target()))
               .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
               .withOptionalParameter(LootContextParams.THIS_ENTITY, this.self)
               .withLuck((float)this.self.getCreatureLevel() / 25.0F)
               .create(LootContextParamSets.FISHING);
            ObjectListIterator var21 = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING).getRandomItems(params).iterator();

            while (var21.hasNext()) {
               ItemStack sxx = (ItemStack)var21.next();
               Block.popResource(level, this.self.blockPosition(), sxx);
            }
            break;
         case STUDY:
            if (this.studies++ % 4 == 0) {
               output(level, t.target(), new ItemStack(Items.EXPERIENCE_BOTTLE));
            }

            if (level.getRandom().nextInt(20) == 0) {
               output(
                  level,
                  t.target(),
                  EnchantmentHelper.enchantItem(
                     level.getRandom(), new ItemStack(Items.BOOK), 10 + this.self.getCreatureLevel() / 2, level.registryAccess(), Optional.empty()
                  )
               );
            }
            break;
         case CRAFT:
            BlockState s = level.getBlockState(t.target());
            if (s.getBlock() instanceof CrafterBlock cb) {
               ((CrafterAccess)cb).ahrivaea$craft(s, level, t.target());
            }
            break;
         case ICE:
            RandomSource r = level.getRandom();
            Block.popResource(level, this.self.blockPosition(), new ItemStack(Items.ICE, 1 + r.nextInt(2)));
            Block.popResource(level, this.self.blockPosition(), new ItemStack(Items.SNOWBALL, 2 + r.nextInt(3)));
            if (r.nextInt(8) == 0) {
               Block.popResource(level, this.self.blockPosition(), new ItemStack(Items.PACKED_ICE));
            }

            if (r.nextInt(40) == 0) {
               Block.popResource(level, this.self.blockPosition(), new ItemStack(Items.BLUE_ICE));
            }
            break;
         case LIGHT:
            if (level.getBlockState(t.target()).isAir() && Blocks.TORCH.defaultBlockState().canSurvive(level, t.target())) {
               level.setBlockAndUpdate(t.target(), Blocks.TORCH.defaultBlockState());
               this.carried = ItemStack.EMPTY;
            }
            break;
         case SORT:
            Container cx = HopperBlockEntity.getContainerAt(level, t.target());
            if (cx != null && this.sortSlot < cx.getContainerSize() && !cx.getItem(this.sortSlot).isEmpty()) {
               this.carried = cx.removeItem(this.sortSlot, cx.getItem(this.sortSlot).getCount());
               cx.setChanged();
            }
            break;
         case FETCH:
            Container cxx = HopperBlockEntity.getContainerAt(level, t.target());
            Item want = this.plantAt == null ? null : be.toPlant.get(this.plantAt);
            if (cxx != null && want != null && this.carried.isEmpty()) {
               for (int i = 0; i < cxx.getContainerSize(); i++) {
                  if (cxx.getItem(i).is(want)) {
                     this.carried = cxx.removeItem(i, 1);
                     cxx.setChanged();
                     break;
                  }
               }
            }

            if (this.plantAt != null && this.carried.isEmpty()) {
               be.claimed.remove(this.plantAt);
               this.plantAt = null;
            }
            break;
         case PLANT:
            if (this.carried.getItem() instanceof BlockItem bi
               && level.getBlockState(t.target()).isAir()
               && bi.getBlock().defaultBlockState().canSurvive(level, t.target())) {
               level.setBlockAndUpdate(t.target(), bi.getBlock().defaultBlockState());
               this.carried.shrink(1);
            }

            be.toPlant.remove(t.target());
            this.plantAt = null;
      }

      this.finish(be, 10);
   }

   private void ranch(ServerLevel level, BaseCoreBlockEntity be) {
      if (this.jobs(be).contains(Job.RANCHING) && --this.ranchTimer <= 0) {
         this.ranchTimer = this.scaled(2400 + level.getRandom().nextInt(1200));
         Species.get(this.self.getSpeciesId()).ifPresent(s -> {
            for (ItemStack stack : Drops.roll(s, this.self.getInfluence(), this.self.getCreatureLevel(), level.getRandom(), level.registryAccess())) {
               Block.popResource(level, this.self.blockPosition(), stack);
            }
         });
      }
   }

   private void drop() {
      if (!this.carried.isEmpty()) {
         this.self.spawnAtLocation((ServerLevel)this.self.level(), this.carried);
         this.carried = ItemStack.EMPTY;
      }
   }

   private void wander(ServerLevel level, BaseCoreBlockEntity be) {
      if (!this.self.getNavigation().isInProgress() && level.getRandom().nextInt(4) == 0) {
         RandomSource r = level.getRandom();
         int range = Math.clamp((long)(be.radius() / 3), 4, 16);
         BlockPos to = this.core.offset(r.nextInt(2 * range + 1) - range, 0, r.nextInt(2 * range + 1) - range);
         this.self
            .getNavigation()
            .moveTo((double)to.getX() + 0.5, (double)level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, to.getX(), to.getZ()), (double)to.getZ() + 0.5, 0.8);
      }
   }

   private static String name(Level level, BlockPos p) {
      return level.getBlockState(p).getBlock().getName().getString().toLowerCase(Locale.ROOT);
   }

   private static void output(ServerLevel level, BlockPos at, ItemStack stack) {
      for (Direction d : Direction.values()) {
         Container c = HopperBlockEntity.getContainerAt(level, at.relative(d));
         if (c != null && !(c instanceof AbstractFurnaceBlockEntity)) {
            stack = HopperBlockEntity.addItem(null, c, stack, null);
            if (stack.isEmpty()) {
               return;
            }
         }
      }

      Block.popResourceFromFace(level, at, Direction.UP, stack);
   }

   private BlockPos chestFor(ServerLevel level, BaseCoreBlockEntity be, ItemStack stack) {
      return Sorting.chestFor(level, be.containers, stack, this.self.blockPosition(), null, false);
   }

   private boolean defend(ServerLevel level, BaseCoreBlockEntity be) {
      if (Raids.at(this.core) == null) {
         this.fighting = null;
         return false;
      } else {
         if (this.fighting == null || !this.fighting.isAlive() || !this.fighting.hasAttached(Raids.RAIDER_OF)) {
            this.fighting = Raids.nearestRaider(level, this.core, this.self);
         }

         if (this.fighting == null) {
            return false;
         } else {
            if (this.task != null) {
               this.finish(be, 0);
            }

            double reach = Combat.reach(this.self, this.fighting);
            if ((double)this.self.distanceTo(this.fighting) > reach) {
               this.status = "Charging " + this.fighting.displayName();
               if (this.self.tickCount % 10 == 0) {
                  this.self.getNavigation().moveTo(this.fighting, 1.3);
               }
            } else {
               this.self.getNavigation().stop();
               this.self.getLookControl().setLookAt(this.fighting);
               this.status = "Fighting " + this.fighting.displayName();
               if (--this.attackCooldown <= 0) {
                  Combat.hit(level, this.self, this.fighting);
                  this.attackCooldown = this.scaled(30);
               }
            }

            return true;
         }
      }
   }

   private static boolean needsBrewing(ServerLevel level, BlockPos p) {
      if (level.getBlockEntity(p) instanceof BrewingStandBlockEntity b && BrewingAccess.ahrivaea$isBrewable(level, b)) {
         return true;
      }

      return false;
   }

   private boolean brewTick(ServerLevel level, BlockPos p) {
      if (level.getBlockEntity(p) instanceof BrewingStandBlockEntity b) {
         BrewingAccess a = (BrewingAccess)b;
         boolean brewing = a.ahrivaea$brewTime() > 0;
         if (!brewing && !BrewingAccess.ahrivaea$isBrewable(level, b)) {
            return false;
         } else {
            if (a.ahrivaea$fuel() < 1) {
               a.ahrivaea$setFuel(1);
            }

            int extra = this.extraTicks();
            if (a.ahrivaea$brewTime() > 2) {
               a.ahrivaea$setBrewTime(Math.clamp((long)(a.ahrivaea$brewTime() - extra), 2, 400));
            }

            b.setChanged();
            if (this.workTicks % 20 == 0) {
               level.sendParticles(ParticleTypes.WITCH, (double)p.getX() + 0.5, (double)p.getY() + 0.8, (double)p.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.0);
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private WorkerBrain.Task lightingTask(ServerLevel level, BaseCoreBlockEntity be) {
      if (!this.carried.isEmpty() && !this.carried.is(Items.TORCH)) {
         return null;
      } else {
         RandomSource r = level.getRandom();
         BlockPos spot = null;

         for (int i = 0; i < 24 && spot == null; i++) {
            int x = Math.clamp((long)(this.self.getBlockX() + r.nextInt(49) - 24), be.minX(), be.maxX());
            int z = Math.clamp((long)(this.self.getBlockZ() + r.nextInt(49) - 24), be.minZ(), be.maxZ());
            BlockPos top = level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (be.contains(top)
               && level.getBlockState(top).isAir()
               && level.getLightEngine().getLayerListener(LightLayer.BLOCK).getLightValue(top) < 8
               && Blocks.TORCH.defaultBlockState().canSurvive(level, top)
               && !be.claimed.containsKey(top)) {
               spot = top;
            }
         }

         if (spot == null) {
            return null;
         } else {
            return this.carried.isEmpty() && !this.takeTorch(level, be)
               ? null
               : new WorkerBrain.Task(WorkerBrain.Kind.LIGHT, spot, null, this.scaled(10), "Lighting up a dark spot");
         }
      }
   }

   private boolean takeTorch(ServerLevel level, BaseCoreBlockEntity be) {
      for (BlockPos p : be.containers) {
         Container c = HopperBlockEntity.getContainerAt(level, p);

         for (int i = 0; c != null && i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(Items.TORCH)) {
               this.carried = c.removeItem(i, 1);
               return true;
            }
         }
      }

      return false;
   }

   static boolean isCrop(Level level, BlockPos p, BlockState s) {
      Block b = s.getBlock();
      if (b instanceof CropBlock || b instanceof NetherWartBlock || b instanceof SweetBerryBushBlock || b instanceof CocoaBlock) {
         return true;
      } else {
         return b instanceof AttachedStemBlock
            ? true
            : (b instanceof SugarCaneBlock || b instanceof CactusBlock || b instanceof BambooStalkBlock) && !level.getBlockState(p.below()).is(b);
      }
   }

   static boolean isRipe(Level level, BlockPos p, BlockState s) {
      Block b = s.getBlock();
      if (b instanceof CropBlock c) {
         return c.isMaxAge(s);
      } else if (b instanceof NetherWartBlock) {
         return (Integer)s.getValue(NetherWartBlock.AGE) >= 3;
      } else if (b instanceof SweetBerryBushBlock) {
         return (Integer)s.getValue(SweetBerryBushBlock.AGE) >= 2;
      } else if (b instanceof CocoaBlock) {
         return (Integer)s.getValue(CocoaBlock.AGE) >= 2;
      } else if (!(b instanceof AttachedStemBlock)) {
         return !(b instanceof SugarCaneBlock) && !(b instanceof CactusBlock) && !(b instanceof BambooStalkBlock)
            ? false
            : level.getBlockState(p.above()).is(b);
      } else {
         BlockState fruit = level.getBlockState(p.relative((Direction)s.getValue(AttachedStemBlock.FACING)));
         return fruit.is(Blocks.PUMPKIN) || fruit.is(Blocks.MELON);
      }
   }

   private void harvest(ServerLevel level, BaseCoreBlockEntity be, BlockPos p) {
      BlockState s = level.getBlockState(p);
      if (isRipe(level, p, s)) {
         Block b = s.getBlock();
         if (!(b instanceof SugarCaneBlock) && !(b instanceof CactusBlock) && !(b instanceof BambooStalkBlock)) {
            if (b instanceof AttachedStemBlock) {
               level.destroyBlock(p.relative((Direction)s.getValue(AttachedStemBlock.FACING)), true, this.self);
            } else {
               Item seed = b.asItem();
               boolean needsSeed = (b instanceof CropBlock || b instanceof NetherWartBlock || b instanceof CocoaBlock) && seed != Items.AIR;
               boolean replanted = !needsSeed;

               for (ItemStack drop : Block.getDrops(s, level, p, null)) {
                  if (!replanted && drop.is(seed)) {
                     drop.shrink(1);
                     replanted = true;
                  }

                  if (!drop.isEmpty()) {
                     Block.popResource(level, p, drop);
                  }
               }

               BlockState replant = b instanceof CropBlock c
                  ? c.getStateForAge(0)
                  : (
                     b instanceof NetherWartBlock
                        ? (BlockState)s.setValue(NetherWartBlock.AGE, 0)
                        : (
                           b instanceof SweetBerryBushBlock
                              ? (BlockState)s.setValue(SweetBerryBushBlock.AGE, 1)
                              : (b instanceof CocoaBlock ? (BlockState)s.setValue(CocoaBlock.AGE, 0) : Blocks.AIR.defaultBlockState())
                        )
                  );
               if (replanted) {
                  level.setBlockAndUpdate(p, replant);
               } else {
                  level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                  if (!(b instanceof CocoaBlock)) {
                     be.toPlant.put(p.immutable(), seed);
                  }
               }
            }
         } else {
            BlockPos up = p.above();

            List<BlockPos> stack;
            for (stack = new ArrayList<>(); level.getBlockState(up).is(b); up = up.above()) {
               stack.add(up);
            }

            for (int i = stack.size() - 1; i >= 0; i--) {
               level.destroyBlock(stack.get(i), true, this.self);
            }
         }
      }
   }

   private static boolean needsSmelting(ServerLevel level, BlockPos p) {
      if (level.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity f
         && !f.getItem(0).isEmpty()
         && f.getItem(2).getCount() < f.getItem(2).getMaxStackSize()) {
         return true;
      }

      return false;
   }

   private boolean smeltTick(ServerLevel level, BlockPos p) {
      if (level.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity f && needsSmelting(level, p)) {
         FurnaceAccess a = (FurnaceAccess)f;
         if (a.ahrivaea$litTime() < 2) {
            a.ahrivaea$setLitTime(20);
            a.ahrivaea$setLitTotal(20);
            BlockState s = level.getBlockState(p);
            if (s.hasProperty(AbstractFurnaceBlock.LIT) && !(Boolean)s.getValue(AbstractFurnaceBlock.LIT)) {
               level.setBlock(p, (BlockState)s.setValue(AbstractFurnaceBlock.LIT, true), 3);
            }
         }

         int extra = this.extraTicks();
         if (a.ahrivaea$cookTotal() > 0 && extra != 0) {
            a.ahrivaea$setCookTime(Math.clamp((long)(a.ahrivaea$cookTime() + extra), 0, a.ahrivaea$cookTotal() - 1));
         }

         f.setChanged();
         if (this.workTicks % 20 == 0) {
            level.sendParticles(ParticleTypes.FLAME, (double)p.getX() + 0.5, (double)p.getY() + 0.6, (double)p.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.0);
         }

         return true;
      }

      return false;
   }

   static List<BlockPos> naturalTree(ServerLevel level, BlockPos base) {
      if (!level.getBlockState(base).is(BlockTags.LOGS)) {
         return null;
      } else {
         List<BlockPos> logs = new ArrayList<>();
         Set<BlockPos> seen = new HashSet<>();
         ArrayDeque<BlockPos> todo = new ArrayDeque<>(List.of(base));
         boolean leaves = false;

         while (!todo.isEmpty() && logs.size() < 200) {
            BlockPos p = todo.poll();
            if (seen.add(p)) {
               BlockState s = level.getBlockState(p);
               if (s.is(BlockTags.LEAVES)) {
                  if (s.hasProperty(LeavesBlock.PERSISTENT) && !(Boolean)s.getValue(LeavesBlock.PERSISTENT)) {
                     leaves = true;
                  }
               } else if (s.is(BlockTags.LOGS)) {
                  logs.add(p);

                  for (int dx = -1; dx <= 1; dx++) {
                     for (int dy = 0; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                           if (dx != 0 || dy != 0 || dz != 0) {
                              todo.add(p.offset(dx, dy, dz));
                           }
                        }
                     }
                  }
               }
            }
         }

         return leaves && logs.size() < 200 ? logs : null;
      }
   }

   private static int sizeOf(List<BlockPos> logs) {
      return logs == null ? 0 : logs.size();
   }

   private void fell(ServerLevel level, BaseCoreBlockEntity be, BlockPos base) {
      List<BlockPos> logs = naturalTree(level, base);
      if (logs != null) {
         Block kind = level.getBlockState(base).getBlock();
         List<BlockPos> leaves = leavesOf(level, logs);
         logs.sort(Comparator.<BlockPos>comparingInt(p -> p.getY()).reversed());

         for (BlockPos p : logs) {
            level.destroyBlock(p, true, this.self);
         }

         for (BlockPos p : leaves) {
            level.destroyBlock(p, true, this.self);
         }

         Block sapling = SAPLINGS.get(kind);
         if (sapling != null && level.getBlockState(base).isAir() && sapling.defaultBlockState().canSurvive(level, base)) {
            AABB around = new AABB(base).inflate(8.0, 16.0, 8.0);
            Iterator var9 = level.getEntitiesOfClass(ItemEntity.class, around, i -> i.isAlive() && i.getItem().is(sapling.asItem())).iterator();
            if (var9.hasNext()) {
               ItemEntity drop = (ItemEntity)var9.next();
               drop.getItem().shrink(1);
               if (drop.getItem().isEmpty()) {
                  drop.discard();
               }

               level.setBlockAndUpdate(base, sapling.defaultBlockState());
            } else {
               be.toPlant.put(base.immutable(), sapling.asItem());
            }
         }
      }
   }

   private static List<BlockPos> leavesOf(ServerLevel level, List<BlockPos> logs) {
      Set<BlockPos> seen = new HashSet<>(logs);
      ArrayDeque<BlockPos> todo = new ArrayDeque<>(logs);
      List<BlockPos> out = new ArrayList<>();

      while (!todo.isEmpty() && out.size() < 400) {
         BlockPos p = todo.poll();

         for (Direction d : Direction.values()) {
            BlockPos n = p.relative(d);
            if (seen.add(n)) {
               BlockState s = level.getBlockState(n);
               if (s.is(BlockTags.LEAVES)
                  && s.hasProperty(LeavesBlock.PERSISTENT)
                  && !(Boolean)s.getValue(LeavesBlock.PERSISTENT)
                  && !logs.stream()
                     .noneMatch(l -> Math.abs(l.getX() - n.getX()) <= 4 && Math.abs(l.getY() - n.getY()) <= 4 && Math.abs(l.getZ() - n.getZ()) <= 4)) {
                  out.add(n);
                  todo.add(n);
               }
            }
         }
      }

      return out;
   }

   private interface Check {
      boolean ok(BlockPos var1);
   }

   private static enum Kind {
      HARVEST,
      TEND,
      FELL,
      MINE,
      SMELT,
      PICK_UP,
      DELIVER,
      EMPTY_FURNACE,
      FISH,
      BREW,
      STUDY,
      POWER,
      CRAFT,
      ICE,
      LIGHT,
      SORT,
      FETCH,
      PLANT;
   }

   private interface Make {
      WorkerBrain.Task make(BlockPos var1);
   }

   private static record Task(WorkerBrain.Kind kind, BlockPos target, ItemEntity item, int duration, String doing) {
   }
}
