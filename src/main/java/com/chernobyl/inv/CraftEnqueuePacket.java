package com.chernobyl.inv;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: FABRICAR. */
public record CraftEnqueuePacket(String recipeId, int units) {
    public static void encode(CraftEnqueuePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.recipeId, 128);
        buf.writeVarInt(msg.units);
    }

    public static CraftEnqueuePacket decode(FriendlyByteBuf buf) {
        return new CraftEnqueuePacket(buf.readUtf(128), buf.readVarInt());
    }

    public static void handle(CraftEnqueuePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                CraftingManager.handleEnqueue(player, msg.recipeId, msg.units);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
