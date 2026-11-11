package com.chernobyl.inv;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sincroniza el catálogo de recetas de etapa 4 desde el servidor al cliente. */
public record Stage4SyncPacket(String json) {
    private static final int MAX_JSON = 1_000_000;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(json, MAX_JSON);
    }

    public static Stage4SyncPacket decode(FriendlyByteBuf buf) {
        return new Stage4SyncPacket(buf.readUtf(MAX_JSON));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> Stage4Catalog.applyClient(json));
        context.get().setPacketHandled(true);
    }
}
