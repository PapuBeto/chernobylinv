package com.chernobyl.inv;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: cancelar una orden de la cola (reembolsa materiales). */
public record CraftCancelPacket(int queueIndex) {
    public static void encode(CraftCancelPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.queueIndex);
    }

    public static CraftCancelPacket decode(FriendlyByteBuf buf) {
        return new CraftCancelPacket(buf.readVarInt());
    }

    public static void handle(CraftCancelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                CraftingManager.cancel(player, msg.queueIndex);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
