package com.chernobyl.inv;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class Net {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ChernobylInv.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, OpenInvPacket.class,
                OpenInvPacket::encode, OpenInvPacket::decode, OpenInvPacket::handle);
        CHANNEL.registerMessage(1, ScrollBackpackPacket.class,
                ScrollBackpackPacket::encode, ScrollBackpackPacket::decode, ScrollBackpackPacket::handle);
        CHANNEL.registerMessage(2, Stage4SyncPacket.class,
                Stage4SyncPacket::encode, Stage4SyncPacket::decode, Stage4SyncPacket::handle);
        CHANNEL.registerMessage(3, CraftRequestPacket.class,
                CraftRequestPacket::encode, CraftRequestPacket::decode, CraftRequestPacket::handle);
        CHANNEL.registerMessage(4, CraftSyncPacket.class,
                CraftSyncPacket::encode, CraftSyncPacket::decode, CraftSyncPacket::handle);
        CHANNEL.registerMessage(5, CraftStatePacket.class,
                CraftStatePacket::encode, CraftStatePacket::decode, CraftStatePacket::handle);
        CHANNEL.registerMessage(6, CraftEnqueuePacket.class,
                CraftEnqueuePacket::encode, CraftEnqueuePacket::decode, CraftEnqueuePacket::handle);
        CHANNEL.registerMessage(7, CraftCancelPacket.class,
                CraftCancelPacket::encode, CraftCancelPacket::decode, CraftCancelPacket::handle);
    }
}