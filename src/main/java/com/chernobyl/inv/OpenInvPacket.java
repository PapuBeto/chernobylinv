package com.chernobyl.inv;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

public class OpenInvPacket {
    public static void encode(OpenInvPacket msg, FriendlyByteBuf buf) {}

    public static OpenInvPacket decode(FriendlyByteBuf buf) { return new OpenInvPacket(); }

    public static void handle(OpenInvPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && !player.isCreative()) {
                NetworkHooks.openScreen(player, new SimpleMenuProvider(
                        (id, inv, p) -> new InvMenu(id, inv, new BackpackView(p)),
                        Component.literal("Inventario")));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
