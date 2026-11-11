package com.chernobyl.inv;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: cola de fabricación y salida pendiente. */
public record CraftStatePacket(String json) {
    private static final int MAX_JSON = 1 << 20;

    public static void encode(CraftStatePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.json, MAX_JSON);
    }

    public static CraftStatePacket decode(FriendlyByteBuf buf) {
        return new CraftStatePacket(buf.readUtf(MAX_JSON));
    }

    public static void handle(CraftStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientCraftingCache.applyState(msg.json));
        ctx.get().setPacketHandled(true);
    }
}
