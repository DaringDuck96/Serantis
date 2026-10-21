package dev.ahrivaea.challenge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class ChallengeState {
   public static final Codec<ChallengeState> CODEC = RecordCodecBuilder.create(
      i -> i.group(
               Codec.STRING.optionalFieldOf("challenge", "").forGetter(s -> s.challenge),
               Codec.STRING.optionalFieldOf("palette", "none").forGetter(s -> s.palette),
               Codec.STRING.fieldOf("dimension").forGetter(s -> s.dimension),
               BlockPos.CODEC.fieldOf("origin").forGetter(s -> s.origin),
               Codec.INT.fieldOf("total").forGetter(s -> s.total),
               Codec.INT.fieldOf("stage").forGetter(s -> s.stage),
               Codec.BOOL.fieldOf("complete").forGetter(s -> s.complete),
               Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("donations").forGetter(s -> s.donations),
               Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("names").forGetter(s -> s.names),
               Codec.unboundedMap(Codec.STRING, ChallengeDef.RewardItem.CODEC.listOf()).fieldOf("pending_rewards").forGetter(s -> s.pendingRewards),
               Codec.STRING.optionalFieldOf("owner", "").forGetter(s -> s.owner),
               ChallengeState.Extras.MAP_CODEC.forGetter(s -> new ChallengeState.Extras(s.portalRoom, s.gates, s.deposits, s.returns))
            )
            .apply(
               i,
               (challenge, palette, dimension, origin, total, stage, complete, donations, names, pending, owner, x) -> new ChallengeState(
                     challenge,
                     palette,
                     dimension,
                     origin,
                     total,
                     stage,
                     complete,
                     donations,
                     names,
                     pending,
                     owner,
                     x.portalRoom(),
                     x.gates(),
                     x.deposits(),
                     x.returns()
                  )
            )
   );
   public String challenge;
   public final String palette;
   public final String dimension;
   public final BlockPos origin;
   public int total;
   public int stage;
   public boolean complete;
   public final Map<String, Integer> donations;
   public final Map<String, String> names;
   public final Map<String, List<ChallengeDef.RewardItem>> pendingRewards;
   public String owner;
   public List<Integer> portalRoom;
   public List<BlockPos> gates;
   public final Map<String, List<ItemStack>> deposits = new HashMap<>();
   public final Map<String, List<ItemStack>> returns;

   public ChallengeState(
      String challenge,
      String palette,
      String dimension,
      BlockPos origin,
      int total,
      int stage,
      boolean complete,
      Map<String, Integer> donations,
      Map<String, String> names,
      Map<String, List<ChallengeDef.RewardItem>> pendingRewards,
      String owner,
      List<Integer> portalRoom,
      List<BlockPos> gates,
      Map<String, List<ItemStack>> deposits,
      Map<String, List<ItemStack>> returns
   ) {
      deposits.forEach((k, v) -> this.deposits.put(k, new ArrayList<>(v.stream().map(ItemStack::copy).toList())));
      this.returns = new HashMap<>();
      returns.forEach((k, v) -> this.returns.put(k, new ArrayList<>(v.stream().map(ItemStack::copy).toList())));
      this.owner = owner;
      this.portalRoom = List.copyOf(portalRoom);
      this.gates = List.copyOf(gates);
      this.challenge = challenge;
      this.palette = palette;
      this.dimension = dimension;
      this.origin = origin;
      this.total = total;
      this.stage = stage;
      this.complete = complete;
      this.donations = new HashMap<>(donations);
      this.names = new HashMap<>(names);
      this.pendingRewards = new HashMap<>();
      pendingRewards.forEach((k, v) -> this.pendingRewards.put(k, new ArrayList<>(v)));
   }

   public static ChallengeState fresh(String challenge, String palette, String dimension, BlockPos origin) {
      return new ChallengeState(challenge, palette, dimension, origin, 0, 0, false, Map.of(), Map.of(), Map.of(), "", List.of(), List.of(), Map.of(), Map.of());
   }

   public List<String> ranking() {
      List<String> keys = new ArrayList<>(this.donations.keySet());
      keys.sort(Comparator.<String>comparingInt(k -> -this.donations.get(k)).thenComparing(k -> this.names.getOrDefault(k, k)));
      return keys;
   }

   public String controller() {
      if (!this.owner.isEmpty()) {
         return this.owner;
      } else {
         return this.complete && !this.donations.isEmpty() ? this.ranking().getFirst() : "";
      }
   }

   public BoundingBox portalRoomBox() {
      return this.portalRoom.size() != 6
         ? null
         : new BoundingBox(
            this.portalRoom.get(0), this.portalRoom.get(1), this.portalRoom.get(2), this.portalRoom.get(3), this.portalRoom.get(4), this.portalRoom.get(5)
         );
   }

   public static void addStacks(List<ItemStack> list, ItemStack stack) {
      ItemStack rest = stack.copy();

      for (ItemStack s : list) {
         if (rest.isEmpty()) {
            return;
         }

         if (ItemStack.isSameItemSameComponents(s, rest)) {
            int move = Math.min(rest.getCount(), s.getMaxStackSize() - s.getCount());
            if (move > 0) {
               s.grow(move);
               rest.shrink(move);
            }
         }
      }

      while (!rest.isEmpty()) {
         list.add(rest.split(rest.getMaxStackSize()));
      }
   }

   public int rankOf(String key) {
      return this.ranking().indexOf(key) + 1;
   }

   static record Extras(List<Integer> portalRoom, List<BlockPos> gates, Map<String, List<ItemStack>> deposits, Map<String, List<ItemStack>> returns) {
      static final MapCodec<ChallengeState.Extras> MAP_CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(
                  Codec.INT.listOf().optionalFieldOf("portal_room", List.of()).forGetter(ChallengeState.Extras::portalRoom),
                  BlockPos.CODEC.listOf().optionalFieldOf("gates", List.of()).forGetter(ChallengeState.Extras::gates),
                  Codec.unboundedMap(Codec.STRING, ItemStack.CODEC.listOf()).optionalFieldOf("deposits", Map.of()).forGetter(ChallengeState.Extras::deposits),
                  Codec.unboundedMap(Codec.STRING, ItemStack.CODEC.listOf()).optionalFieldOf("returns", Map.of()).forGetter(ChallengeState.Extras::returns)
               )
               .apply(i, ChallengeState.Extras::new)
      );
   }
}
