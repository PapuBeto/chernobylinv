package com.chernobyl.inv;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Guardado persistente de colas de fabricación y objetos terminados.
 * Vive en los datos del mundo, así sobrevive a cerrar el inventario,
 * a salir del juego y a reiniciar el servidor.
 */
public final class CraftingSave extends SavedData {
    private static final String KEY = "chernobylinv_crafting";
    private static final String TAG_QUEUES = "queues";
    private static final String TAG_FINISHED = "finished";
    private static final String TAG_ENTRIES = "entries";

    private final Map<UUID, CraftingQueue> queues = new LinkedHashMap<>();

    public CraftingQueue queue(UUID uuid) {
        return queues.computeIfAbsent(uuid, k -> new CraftingQueue());
    }

    public Map<UUID, CraftingQueue> queues() { return queues; }

    public void markDirty() {
        setDirty();
    }

    public static CraftingSave get(MinecraftServer server) {
        if (server == null) return null;
        net.minecraft.server.level.ServerLevel level = server.overworld();
        if (level == null) return null;
        return level.getDataStorage().computeIfAbsent(CraftingSave::load, () -> new CraftingSave(), KEY);
    }

    public static CraftingSave getFor(ServerPlayer player) {
        return player == null ? null : get(player.getServer());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag queueList = new ListTag();
        for (Map.Entry<UUID, CraftingQueue> entry : queues.entrySet()) {
            CompoundTag queueTag = new CompoundTag();
            queueTag.putUUID("player", entry.getKey());
            CraftingQueue queue = entry.getValue();

            ListTag entries = new ListTag();
            for (CraftingQueue.Entry e : queue.entries()) {
                CompoundTag eTag = new CompoundTag();
                eTag.putString("recipeId", e.recipeId);
                eTag.putInt("units", e.units);
                eTag.putInt("totalTicks", e.totalTicks);
                eTag.putInt("remainingTicks", e.remainingTicks);
                ListTag consumed = new ListTag();
                for (ItemStack stack : e.consumed) {
                    CompoundTag stackTag = new CompoundTag();
                    stack.save(stackTag);
                    consumed.add(stackTag);
                }
                eTag.put("consumed", consumed);
                entries.add(eTag);
            }
            queueTag.put(TAG_ENTRIES, entries);

            ListTag finished = new ListTag();
            for (ItemStack stack : queue.finished()) {
                CompoundTag stackTag = new CompoundTag();
                stack.save(stackTag);
                finished.add(stackTag);
            }
            queueTag.put(TAG_FINISHED, finished);
            queueList.add(queueTag);
        }
        tag.put(TAG_QUEUES, queueList);
        return tag;
    }

    public static CraftingSave load(CompoundTag tag) {
        CraftingSave save = new CraftingSave();
        if (!tag.contains(TAG_QUEUES)) return save;

        ListTag list = tag.getList(TAG_QUEUES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag queueTag = list.getCompound(i);
            UUID uuid = queueTag.getUUID("player");
            CraftingQueue queue = save.queue(uuid);

            if (queueTag.contains(TAG_ENTRIES)) {
                ListTag entries = queueTag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
                for (int j = 0; j < entries.size(); j++) {
                    CompoundTag eTag = entries.getCompound(j);
                    CraftingQueue.Entry e = new CraftingQueue.Entry(
                            eTag.getString("recipeId"),
                            eTag.getInt("units"),
                            eTag.getInt("totalTicks"));
                    e.remainingTicks = Math.max(0, eTag.getInt("remainingTicks"));
                    if (eTag.contains("consumed")) {
                        ListTag consumed = eTag.getList("consumed", Tag.TAG_COMPOUND);
                        for (int k = 0; k < consumed.size(); k++) {
                            ItemStack stack = ItemStack.of(consumed.getCompound(k));
                            if (!stack.isEmpty()) e.consumed.add(stack);
                        }
                    }
                    queue.addEntry(e);
                }
            }

            if (queueTag.contains(TAG_FINISHED)) {
                ListTag finished = queueTag.getList(TAG_FINISHED, Tag.TAG_COMPOUND);
                for (int j = 0; j < finished.size(); j++) {
                    ItemStack stack = ItemStack.of(finished.getCompound(j));
                    if (!stack.isEmpty()) queue.addFinished(stack);
                }
            }
        }
        return save;
    }
}
