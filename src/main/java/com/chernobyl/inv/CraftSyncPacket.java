package com.chernobyl.inv;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: catálogo de recetas. */
public record CraftSyncPacket(String json) {
    private static final int MAX_JSON = 1 << 20;

    public static void encode(CraftSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.json, MAX_JSON);
    }

    public static CraftSyncPacket decode(FriendlyByteBuf buf) {
        return new CraftSyncPacket(buf.readUtf(MAX_JSON));
    }

    public static void handle(CraftSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> RecipeDatabase.applyClient(msg.json));
        ctx.get().setPacketHandled(true);
    }
}
