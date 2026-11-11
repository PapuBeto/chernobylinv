package com.chernobyl.inv;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado de fabricación visible en el cliente para la UI. Solo representa
 * datos del servidor; la lógica (consumo, ticks, guardado) vive en
 * {@link CraftingManager}.
 */
public final class ClientCraftingCache {

    public static final class Entry {
        public String recipeId;
        public int units;
        public int totalTicks;
        public int remainingTicks;

        public float progress() {
            if (totalTicks <= 0) return 1f;
            return Math.max(0f, Math.min(1f, 1f - (float) remainingTicks / totalTicks));
        }
    }

    private static volatile List<Entry> entries = List.of();
    private static volatile int finished = 0;

    private ClientCraftingCache() {}

    public static List<Entry> entries() { return entries; }

    public static int finished() { return finished; }

    public static void applyState(String json) {
        if (json == null || json.isBlank()) {
            entries = List.of();
            finished = 0;
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            List<Entry> parsed = new ArrayList<>();
            JsonElement queue = root.get("queue");
            if (queue != null && queue.isJsonArray()) {
                JsonArray array = queue.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    JsonObject object = array.get(i).getAsJsonObject();
                    Entry entry = new Entry();
                    entry.recipeId = object.has("recipeId") ? object.get("recipeId").getAsString() : "";
                    entry.units = object.has("units") ? object.get("units").getAsInt() : 1;
                    entry.totalTicks = object.has("totalTicks") ? object.get("totalTicks").getAsInt() : 1;
                    entry.remainingTicks = object.has("remainingTicks") ? object.get("remainingTicks").getAsInt() : 0;
                    parsed.add(entry);
                }
            }
            entries = List.copyOf(parsed);
            finished = root.has("finished") ? root.get("finished").getAsInt() : 0;
        } catch (Exception ignored) {
            entries = List.of();
            finished = 0;
        }
    }
}
