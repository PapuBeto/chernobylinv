package com.chernobyl.inv;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base de datos de recetas cargada desde JSON editable.
 *
 * <p>Formato soportado (IDs como {@code modid:nombre}):</p>
 * <pre>
 * {
 *   "queueMax": 10,
 *   "categories": [{"id":"Tools","name":"Herramientas"}],
 *   "recipes": [{
 *     "id": "stone_hatchet",
 *     "output": "minecraft:stone_axe",
 *     "name": "Hacha de piedra",
 *     "description": "...",
 *     "icon": "Items/stone_hatchet",
 *     "category": "Tools",
 *     "craftingTime": 15,
 *     "craftingAmount": 1,
 *     "maxCraftAmount": 10,
 *     "ingredients": [{"itemId":"minecraft:oak_planks","amount":2}],
 *     "requirements": {"workbenchLevel": 0},
 *     "enabled": true
 *   }]
 * }
 * </pre>
 * <p>Si falta {@code output}, se usa el propio {@code id} como ítem de
 * salida. {@code workbenchLevel} queda preparado para futuras mesas de
 * trabajo; hoy siempre se exige 0.</p>
 */
public final class RecipeDatabase {
    private static final Logger LOGGER = LoggerFactory.getLogger("chernobylinv/recipes");
    private static final Gson GSON = new Gson();
    private static final String FILE_NAME = "chernobylinv-recipes.json";
    private static final String DEFAULT_RESOURCE = "/data/chernobylinv/recipes.json";

    /** Una receta: la salida y lo que consume cada unidad fabricada. */
    public record Recipe(
            String id,
            String outputId,
            Item outputItem,
            int outputCount,
            String name,
            String description,
            String icon,
            String category,
            int craftingTimeSec,
            int maxCraftAmount,
            List<Ingredient> ingredients,
            int workbenchLevel,
            boolean enabled,
            int order
    ) {
        public record Ingredient(String itemId, Item item, int amount) {}

        public int totalTicks(int units) {
            return Math.max(1, Math.max(1, craftingTimeSec) * 20 * Math.max(1, units));
        }
    }

    public record Category(String id, String name) {}

    private static volatile List<Recipe> serverRecipes = List.of();
    private static volatile Map<String, Recipe> serverById = Map.of();
    private static volatile List<Category> serverCategories = List.of();
    private static volatile int serverQueueMax = 10;

    private static volatile List<Recipe> clientRecipes = List.of();
    private static volatile Map<String, Recipe> clientById = Map.of();
    private static volatile List<Category> clientCategories = List.of();
    private static volatile int clientQueueMax = 10;

    private RecipeDatabase() {}

    // ---------- Servidor ----------

    public static List<Recipe> recipes() { return serverRecipes; }

    public static Map<String, Recipe> recipesById() { return serverById; }

    public static List<Category> categories() { return serverCategories; }

    public static int queueMax() { return Math.max(1, serverQueueMax); }

    public static Recipe byId(String id) {
        return id == null ? null : serverById.get(id);
    }

    public static List<Recipe> displayRecipes() { return clientRecipes; }

    public static Map<String, Recipe> displayById() { return clientById; }

    public static List<Category> displayCategories() { return clientCategories; }

    public static int displayQueueMax() { return Math.max(1, clientQueueMax); }

    public static Recipe displayById(String id) {
        return id == null ? null : clientById.get(id);
    }

    public static void load(MinecraftServer server) {
        Path configDir = server.getServerDirectory().toPath().resolve("config");
        Path file = configDir.resolve(FILE_NAME);

        try {
            if (!Files.exists(file)) {
                Files.createDirectories(configDir);
                try (InputStream in = RecipeDatabase.class.getResourceAsStream(DEFAULT_RESOURCE)) {
                    if (in != null) {
                        Files.copy(in, file);
                        LOGGER.info("Created default recipe file at {}", file);
                    } else {
                        LOGGER.warn("Default recipe resource {} is missing", DEFAULT_RESOURCE);
                    }
                }
            }

            if (!Files.exists(file)) {
                replaceServer(List.of(), List.of(), 10);
                LOGGER.warn("No recipe file found at {}", file);
                return;
            }

            String json = Files.readString(file, StandardCharsets.UTF_8);
            replaceServer(parse(json));
            LOGGER.info("Loaded {} recipe(s), {} category(ies), queueMax={} from {}",
                    serverRecipes.size(), serverCategories.size(), serverQueueMax, file);
        } catch (Exception e) {
            replaceServer(List.of(), List.of(), 10);
            LOGGER.error("Could not load recipes from {}", file, e);
        }
    }

    public static void syncTo(ServerPlayer player) {
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new CraftSyncPacket(toJson()));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTo(player);
        }
    }

    /** Solo cliente: aplica el JSON sincronizado por el servidor. */
    public static void applyClient(String json) {
        try {
            replaceClient(parse(json));
        } catch (Exception e) {
            replaceClient(List.of(), List.of(), 10);
            LOGGER.error("Could not read synced recipes", e);
        }
    }

    private record Parsed(List<Recipe> recipes, List<Category> categories, int queueMax) {}

    private static void replaceServer(Parsed parsed) {
        replaceServer(parsed.recipes(), parsed.categories(), parsed.queueMax());
    }

    private static void replaceServer(List<Recipe> recipes, List<Category> categories, int queueMax) {
        Map<String, Recipe> index = new LinkedHashMap<>();
        for (Recipe recipe : recipes) {
            index.putIfAbsent(recipe.id(), recipe);
        }
        serverRecipes = List.copyOf(recipes);
        serverById = Map.copyOf(index);
        serverCategories = List.copyOf(categories);
        serverQueueMax = Math.max(1, queueMax);
    }

    private static void replaceClient(List<Recipe> recipes, List<Category> categories, int queueMax) {
        Map<String, Recipe> index = new LinkedHashMap<>();
        for (Recipe recipe : recipes) {
            index.putIfAbsent(recipe.id(), recipe);
        }
        clientRecipes = List.copyOf(recipes);
        clientById = Map.copyOf(index);
        clientCategories = List.copyOf(categories);
        clientQueueMax = Math.max(1, queueMax);
    }

    private static void replaceClient(Parsed parsed) {
        replaceClient(parsed.recipes(), parsed.categories(), parsed.queueMax());
    }

    private static Parsed parse(String json) {
        if (json == null || json.isBlank()) return new Parsed(List.of(), List.of(), 10);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int queueMax = root.has("queueMax") ? Math.max(1, root.get("queueMax").getAsInt()) : 10;

        List<Category> categories = new ArrayList<>();
        if (root.has("categories") && root.get("categories").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("categories")) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                String id = object.has("id") ? object.get("id").getAsString() : "Other";
                String name = object.has("name") ? object.get("name").getAsString() : id;
                categories.add(new Category(id, name));
            }
        }

        List<Recipe> recipes = new ArrayList<>();
        JsonElement recipesElement = root.get("recipes");
        if (recipesElement == null || !recipesElement.isJsonArray()) {
            return new Parsed(recipes, categories, queueMax);
        }

        JsonArray array = recipesElement.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            if (!array.get(i).isJsonObject()) {
                LOGGER.warn("Recipe #{} ignored: not an object", i);
                continue;
            }
            JsonObject object = array.get(i).getAsJsonObject();
            if (!object.has("id")) {
                LOGGER.warn("Recipe #{} ignored: missing id", i);
                continue;
            }
            String id = object.get("id").getAsString();
            String outputId = object.has("output") ? object.get("output").getAsString() : id;
            Item outputItem = ItemDatabase.resolve(outputId);
            if (outputItem == null) {
                LOGGER.warn("Recipe '{}' ignored: unknown output item '{}'", id, outputId);
                continue;
            }
            int outputCount = object.has("craftingAmount") ? object.get("craftingAmount").getAsInt() : 1;
            outputCount = Math.max(1, Math.min(outputCount, Math.max(1, outputItem.getMaxStackSize())));

            if (!object.has("ingredients") || !object.get("ingredients").isJsonArray()) {
                LOGGER.warn("Recipe '{}' ignored: missing ingredients", id);
                continue;
            }

            List<Recipe.Ingredient> ingredients = new ArrayList<>();
            boolean valid = true;
            for (JsonElement element : object.getAsJsonArray("ingredients")) {
                if (!element.isJsonObject()) {
                    valid = false;
                    break;
                }
                JsonObject ingredientObject = element.getAsJsonObject();
                String ingredientId = ingredientObject.has("itemId")
                        ? ingredientObject.get("itemId").getAsString()
                        : (ingredientObject.has("item") ? ingredientObject.get("item").getAsString() : null);
                if (ingredientId == null) {
                    LOGGER.warn("Recipe '{}' ignored: ingredient without itemId", id);
                    valid = false;
                    break;
                }
                Item item = ItemDatabase.resolve(ingredientId);
                if (item == null) {
                    LOGGER.warn("Recipe '{}' ignored: unknown ingredient item '{}'", id, ingredientId);
                    valid = false;
                    break;
                }
                int amount = ingredientObject.has("amount") ? ingredientObject.get("amount").getAsInt()
                        : (ingredientObject.has("count") ? ingredientObject.get("count").getAsInt() : 1);
                ingredients.add(new Recipe.Ingredient(ingredientId, item, Math.max(1, amount)));
            }
            if (!valid || ingredients.isEmpty()) {
                LOGGER.warn("Recipe '{}' ignored: empty or invalid ingredient list", id);
                continue;
            }

            String name = object.has("name") ? object.get("name").getAsString() : id;
            String description = object.has("description") ? object.get("description").getAsString() : "";
            String icon = object.has("icon") ? object.get("icon").getAsString() : "";
            String category = object.has("category") ? object.get("category").getAsString() : "Other";
            int craftingTime = object.has("craftingTime") ? Math.max(0, object.get("craftingTime").getAsInt()) : 5;
            int maxCraft = object.has("maxCraftAmount") ? Math.max(1, object.get("maxCraftAmount").getAsInt()) : 10;
            int workbench = 0;
            if (object.has("requirements") && object.get("requirements").isJsonObject()) {
                JsonObject requirements = object.getAsJsonObject("requirements");
                if (requirements.has("workbenchLevel")) {
                    workbench = Math.max(0, requirements.get("workbenchLevel").getAsInt());
                }
            }
            boolean enabled = !object.has("enabled") || object.get("enabled").getAsBoolean();
            int order = object.has("order") ? object.get("order").getAsInt() : i;

            recipes.add(new Recipe(id, outputId, outputItem, outputCount, name, description,
                    icon, category, craftingTime, maxCraft, List.copyOf(ingredients),
                    workbench, enabled, order));
        }

        recipes.sort((a, b) -> Integer.compare(a.order(), b.order()));
        return new Parsed(recipes, categories, queueMax);
    }

    private static String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("queueMax", serverQueueMax);

        JsonArray categoryArray = new JsonArray();
        for (Category category : serverCategories) {
            JsonObject object = new JsonObject();
            object.addProperty("id", category.id());
            object.addProperty("name", category.name());
            categoryArray.add(object);
        }
        root.add("categories", categoryArray);

        JsonArray array = new JsonArray();
        for (Recipe recipe : serverRecipes) {
            JsonObject object = new JsonObject();
            object.addProperty("id", recipe.id());
            object.addProperty("output", recipe.outputId());
            object.addProperty("name", recipe.name());
            object.addProperty("description", recipe.description());
            object.addProperty("icon", recipe.icon());
            object.addProperty("category", recipe.category());
            object.addProperty("craftingTime", recipe.craftingTimeSec());
            object.addProperty("craftingAmount", recipe.outputCount());
            object.addProperty("maxCraftAmount", recipe.maxCraftAmount());
            object.addProperty("workbenchLevel", recipe.workbenchLevel());
            object.addProperty("enabled", recipe.enabled());
            object.addProperty("order", recipe.order());

            JsonArray ingredients = new JsonArray();
            for (Recipe.Ingredient ingredient : recipe.ingredients()) {
                JsonObject ingredientObject = new JsonObject();
                ingredientObject.addProperty("itemId", ingredient.itemId());
                ingredientObject.addProperty("amount", ingredient.amount());
                ingredients.add(ingredientObject);
            }
            object.add("ingredients", ingredients);
            array.add(object);
        }
        root.add("recipes", array);
        return GSON.toJson(root);
    }
}
