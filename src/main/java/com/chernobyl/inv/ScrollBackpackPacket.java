package com.chernobyl.inv;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ScrollBackpackPacket {
    private final int dir;

    public ScrollBackpackPacket(int dir) { this.dir = dir; }

    public static void encode(ScrollBackpackPacket msg, FriendlyByteBuf buf) { buf.writeByte(msg.dir); }

    public static ScrollBackpackPacket decode(FriendlyByteBuf buf) { return new ScrollBackpackPacket(buf.readByte()); }

    public static void handle(ScrollBackpackPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && p.containerMenu instanceof InvMenu menu) menu.scroll(msg.dir);
        });
        ctx.get().setPacketHandled(true);
    }
}