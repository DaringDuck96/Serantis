package dev.ahrivaea.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.phys.AABB;

public final class Markers {
   public static final int CREATURE_RANGE = 128;
   public static final int MAX_CREATURES = 300;
   public static final int WILD = 0;
   public static final int YOURS = 1;
   public static final int TAMED = 2;
   public static final GameRule<Boolean> SHOW_PLAYERS = GameRuleBuilder.forBoolean(true)
      .category(GameRuleCategory.PLAYER)
      .buildAndRegister(Identifier.fromNamespaceAndPath("ahrivaea_map", "show_players"));
   public static final GameRule<Boolean> SHOW_CREATURES = GameRuleBuilder.forBoolean(true)
      .category(GameRuleCategory.MOBS)
      .buildAndRegister(Identifier.fromNamespaceAndPath("ahrivaea_map", "show_creatures"));

   private Markers() {
   }

   public static void init() {
      PayloadTypeRegistry.clientboundPlay().register(Markers.Payload.TYPE, Markers.Payload.CODEC);
      ServerTickEvents.END_SERVER_TICK.register(Markers::tick);
   }

   private static void tick(MinecraftServer server) {
      if (server.getTickCount() % 10 == 0) {
         List<ServerPlayer> all = server.getPlayerList().getPlayers();
         List<Markers.PlayerMark> players = new ArrayList<>();

         for (ServerPlayer p : all) {
            players.add(mark(p));
         }

         for (ServerPlayer p : all) {
            if (ServerPlayNetworking.canSend(p, Markers.Payload.TYPE)) {
               ServerLevel level = p.level();
               List<Markers.PlayerMark> shown = new ArrayList<>();
               boolean everyone = (Boolean)level.getGameRules().get(SHOW_PLAYERS);

               for (int i = 0; i < all.size(); i++) {
                  ServerPlayer o = all.get(i);
                  if (o == p || everyone && (p.isSpectator() || !o.isSpectator() && !o.isInvisible())) {
                     shown.add(players.get(i));
                  }
               }

               List<Markers.CreatureMark> creatures = level.getGameRules().get(SHOW_CREATURES) ? creaturesNear(level, p) : List.of();
               ServerPlayNetworking.send(p, new Markers.Payload(shown, creatures));
            }
         }
      }
   }

   private static Markers.PlayerMark mark(ServerPlayer p) {
      return new Markers.PlayerMark(
         p.getUUID(), p.getScoreboardName(), p.level().dimension().identifier().toString(), p.getBlockX(), p.getBlockY(), p.getBlockZ(), p.getYRot()
      );
   }

   public static List<Markers.CreatureMark> creaturesNear(ServerLevel level, ServerPlayer p) {
      AABB box = new AABB(p.getX() - 128.0, (double)level.getMinY(), p.getZ() - 128.0, p.getX() + 128.0, (double)(level.getMaxY() + 1), p.getZ() + 128.0);
      List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box, mx -> mx.isAlive() && !mx.isInvisible());
      mobs.sort(Comparator.comparingDouble(mx -> mx.distanceToSqr(p)));
      List<Markers.CreatureMark> out = new ArrayList<>();

      for (Mob m : mobs) {
         if (out.size() >= 300) {
            break;
         }

         Component name;
         int color;
         int var10000;
         label37: {
            name = m.hasCustomName() ? m.getCustomName() : m.getType().getDescription();
            TextColor tc = name.getStyle().getColor();
            color = tc != null ? tc.getValue() : (m instanceof Enemy ? 14700624 : 15263976);
            if (m instanceof TamableAnimal t && t.isTame()) {
               var10000 = t.isOwnedBy(p) ? 1 : 2;
               break label37;
            }

            var10000 = 0;
         }

         int kind = var10000;
         out.add(new Markers.CreatureMark(m.getId(), name.getString(), color & 16777215, m.getBlockX(), m.getBlockZ(), kind));
      }

      return out;
   }

   public static record CreatureMark(int entity, String name, int color, int x, int z, int kind) {
   }

   public static record Payload(List<Markers.PlayerMark> players, List<Markers.CreatureMark> creatures) implements CustomPacketPayload {
      public static final Type<Markers.Payload> TYPE = new Type(Identifier.fromNamespaceAndPath("ahrivaea_map", "markers"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Markers.Payload> CODEC = StreamCodec.of(
         (buf, p) -> {
            buf.writeVarInt(p.players.size());

            for (Markers.PlayerMark m : p.players) {
               buf.writeUUID(m.id);
               buf.writeUtf(m.name);
               buf.writeUtf(m.dimension);
               buf.writeVarInt(m.x);
               buf.writeVarInt(m.y);
               buf.writeVarInt(m.z);
               buf.writeFloat(m.yaw);
            }

            buf.writeVarInt(p.creatures.size());

            for (Markers.CreatureMark m : p.creatures) {
               buf.writeVarInt(m.entity);
               buf.writeUtf(m.name);
               buf.writeInt(m.color);
               buf.writeVarInt(m.x);
               buf.writeVarInt(m.z);
               buf.writeByte(m.kind);
            }
         },
         buf -> {
            List<Markers.PlayerMark> players = new ArrayList<>();

            for (int i = buf.readVarInt(); i > 0; i--) {
               players.add(
                  new Markers.PlayerMark(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat())
               );
            }

            List<Markers.CreatureMark> creatures = new ArrayList<>();

            for (int i = buf.readVarInt(); i > 0; i--) {
               creatures.add(new Markers.CreatureMark(buf.readVarInt(), buf.readUtf(), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readByte()));
            }

            return new Markers.Payload(players, creatures);
         }
      );

      public Type<Markers.Payload> type() {
         return TYPE;
      }
   }

   public static record PlayerMark(UUID id, String name, String dimension, int x, int y, int z, float yaw) {
   }
}
