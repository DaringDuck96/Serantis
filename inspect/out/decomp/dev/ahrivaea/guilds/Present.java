package dev.ahrivaea.guilds;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record Present() implements CustomPacketPayload {
   public static final Type<Present> TYPE = new Type(Identifier.fromNamespaceAndPath("ahrivaea_guilds", "present"));
   public static final StreamCodec<RegistryFriendlyByteBuf, Present> CODEC = StreamCodec.unit(new Present());

   public Type<Present> type() {
      return TYPE;
   }

   public static void register() {
      PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
      ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
      });
   }
}
