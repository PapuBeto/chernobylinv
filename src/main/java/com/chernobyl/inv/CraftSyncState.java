package com.chernobyl.inv;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Construye y envía el estado de cola del jugador al cliente. */
public final class CraftSyncState {
    private static final Logger LOGGER = LoggerFactory.getLogger("chernobylinv/crafting");

    private CraftSyncState() {}

    public static void send(ServerPlayer player) {
        if (player == null) return;
        CraftingQueue queue = CraftingManager.queueOf(player);
        StringBuilder json = new StringBuilder("{\"queue\":[");
        boolean first = true;
        for (CraftingQueue.Entry entry : queue.entries()) {
            if (!first) json.append(",");
            first = false;
            json.append("{\"recipeId\":\"").append(escape(entry.recipeId)).append("\",")
                    .append("\"units\":").append(entry.units).append(",")
                    .append("\"remainingTicks\":").append(entry.remainingTicks).append(",")
                    .append("\"totalTicks\":").append(entry.totalTicks).append("}");
        }
        json.append("],\"finished\":").append(queue.finished().size()).append("}");
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new CraftStatePacket(json.toString()));
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
