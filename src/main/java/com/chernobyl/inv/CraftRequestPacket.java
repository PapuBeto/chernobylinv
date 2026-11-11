package com.chernobyl.inv;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Petición del cliente para craftear una receta concreta del catálogo. */
public record CraftRequestPacket(int index) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(index);
    }

    public static CraftRequestPacket decode(FriendlyByteBuf buf) {
        return new CraftRequestPacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) Stage4Catalog.craft(player, index);
        });
        ctx.setPacketHandled(true);
    }
}
