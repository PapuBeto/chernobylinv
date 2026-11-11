package com.chernobyl.inv;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Catálogo de recetas de etapa 4. El servidor lee un JSON editable en
 * config/chernobylinv-stage4.json y lo sincroniza al cliente para que la UI
 * muestre exactamente las recetas permitidas. Si un ID de item no existe se
 * ignora con un warning, sin romper el resto del catálogo.
 */
public final class Stage4Catalog {
    private static final Logger LOGGER = LoggerFactory.getLogger("chernobylinv/stage4");
    private static final Gson GSON = new Gson();
    private static final String FILE_NAME = "chernobylinv-stage4.json";
    private static final String DEFAULT_RESOURCE = "/data/chernobylinv/stage4_recipes.json";

    private static volatile List<Stage4Recipe> serverRecipes = List.of();
    private static volatile List<Stage4Recipe> clientRecipes = List.of();

    private Stage4Catalog() {}

    /** Recetas que debe dibujar el cliente. */
    public static List<Stage4Recipe> displayRecipes() {
        return clientRecipes;
    }

    /** Solo para el servidor. */
    public static List<Stage4Recipe> serverRecipes() {
        return serverRecipes;
    }

    public static void load(MinecraftServer server) {
        Path configDir = server.getServerDirectory().toPath().resolve("config");
        Path file = configDir.resolve(FILE_NAME);

        try {
            if (!Files.exists(file)) {
                Files.createDirectories(configDir);
                try (InputStream in = Stage4Catalog.class.getResourceAsStream(DEFAULT_RESOURCE)) {
                    if (in != null) {
                        Files.copy(in, file);
                        LOGGER.info("Created default stage-4 recipe file at {}", file);
                    } else {
                        LOGGER.warn("Default recipe resource {} is missing", DEFAULT_RESOURCE);
                    }
                }
            }

            if (!Files.exists(file)) {
                serverRecipes = List.of();
                LOGGER.warn("No stage-4 recipe file found at {}", file);
                return;
            }

            String json = Files.readString(file, StandardCharsets.UTF_8);
            serverRecipes = parse(json);
            LOGGER.info("Loaded {} stage-4 recipe(s) from {}", serverRecipes.size(), file);
        } catch (Exception e) {
            serverRecipes = List.of();
            LOGGER.error("Could not load stage-4 recipes from {}", file, e);
        }
    }

    public static void syncTo(ServerPlayer player) {
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new Stage4SyncPacket(toJson(serverRecipes)));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTo(player);
        }
    }

    /** Llamado en el cliente cuando llega el paquete de sincronización. */
    public static void applyClient(String json) {
        try {
            clientRecipes = parse(json);
        } catch (Exception e) {
            clientRecipes = List.of();
            LOGGER.error("Could not read synced stage-4 recipes", e);
        }
    }

    /** Intento de crafteo validado en el servidor. */
    public static void craft(ServerPlayer player, int index) {
        if (!(player.containerMenu instanceof InvMenu)) return;
        if (index < 0 || index >= serverRecipes.size()) return;

        Stage4Recipe recipe = serverRecipes.get(index);
        if (!recipe.canCraft(player)) {
            player.displayClientMessage(Component.literal("Faltan materiales para esta receta."), true);
            return;
        }
        if (!recipe.consume(player)) {
            player.displayClientMessage(Component.literal("No se pudieron consumir los materiales."), true);
            return;
        }

        giveWithFallback(player, recipe.output().copy());
        player.displayClientMessage(
                Component.literal("Creado: ").append(recipe.output().getHoverName()), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE,
                SoundSource.PLAYERS, 0.45F, 1.25F);
        player.containerMenu.broadcastChanges();
    }

    private static void giveWithFallback(ServerPlayer player, ItemStack stack) {
        ItemStack remaining = stack.copy();

        // El inventario normal puede aceptar una parte y devolver el resto en
        // 'remaining'. Después probamos la mochila y, como último recurso, el
        // suelo, para que un crafteo nunca se pierda.
        if (!player.getInventory().add(remaining)) {
            IItemHandlerModifiable backpack = BackpackAccess.handler(player);
            if (backpack != null) {
                remaining = ItemHandlerHelper.insertItemStacked(backpack, remaining, false);
            }
            if (!remaining.isEmpty()) {
                player.drop(remaining, false);
                LOGGER.warn("Stage-4 craft output dropped at {} because inventory and backpack were full",
                        player.blockPosition());
            }
        }
    }

    private static List<Stage4Recipe> parse(String json) {
        if (json == null || json.isBlank()) return List.of();

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonElement recipesElement = root.get("recipes");
        if (recipesElement == null || !recipesElement.isJsonArray()) return List.of();

        List<Stage4Recipe> result = new ArrayList<>();
        JsonArray recipes = recipesElement.getAsJsonArray();
        for (int i = 0; i < recipes.size(); i++) {
            JsonObject object = recipes.get(i).getAsJsonObject();
            String id = object.has("id") ? object.get("id").getAsString() : "recipe_" + i;

            Item outputItem = item(object.get("output").getAsString());
            if (outputItem == null) {
                LOGGER.warn("Recipe '{}' ignored: unknown output item", id);
                continue;
            }
            int outputCount = object.has("count") ? Math.max(1, object.get("count").getAsInt()) : 1;
            outputCount = Math.min(outputCount, Math.max(1, outputItem.getMaxStackSize()));
            ItemStack output = new ItemStack(outputItem, outputCount);

            JsonElement ingredientsElement = object.get("ingredients");
            if (ingredientsElement == null || !ingredientsElement.isJsonArray()) {
                LOGGER.warn("Recipe '{}' ignored: missing ingredients", id);
                continue;
            }

            List<Stage4Recipe.Ingredient> ingredients = new ArrayList<>();
            boolean valid = true;
            for (JsonElement element : ingredientsElement.getAsJsonArray()) {
                JsonObject ingredientObject = element.getAsJsonObject();
                Item item = item(ingredientObject.get("item").getAsString());
                if (item == null) {
                    LOGGER.warn("Recipe '{}' ignored: unknown ingredient item", id);
                    valid = false;
                    break;
                }
                int count = Math.max(1, ingredientObject.get("count").getAsInt());
                ingredients.add(new Stage4Recipe.Ingredient(item, count));
            }
            if (!valid || ingredients.isEmpty()) {
                LOGGER.warn("Recipe '{}' ignored: empty ingredient list", id);
                continue;
            }

            result.add(new Stage4Recipe(id, output, List.copyOf(ingredients)));
        }
        return List.copyOf(result);
    }

    private static Item item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return null;
        return ForgeRegistries.ITEMS.getValue(location);
    }

    private static String toJson(List<Stage4Recipe> recipes) {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();

        for (Stage4Recipe recipe : recipes) {
            JsonObject object = new JsonObject();
            object.addProperty("id", recipe.id());
            object.addProperty("output", Objects.toString(
                    ForgeRegistries.ITEMS.getKey(recipe.output().getItem()), "minecraft:air"));
            object.addProperty("count", recipe.output().getCount());

            JsonArray ingredients = new JsonArray();
            for (Stage4Recipe.Ingredient ingredient : recipe.ingredients()) {
                JsonObject ingredientObject = new JsonObject();
                ingredientObject.addProperty("item", Objects.toString(
                        ForgeRegistries.ITEMS.getKey(ingredient.item()), "minecraft:air"));
                ingredientObject.addProperty("count", ingredient.count());
                ingredients.add(ingredientObject);
            }
            object.add("ingredients", ingredients);
            array.add(object);
        }

        root.add("recipes", array);
        return GSON.toJson(root);
    }
}
