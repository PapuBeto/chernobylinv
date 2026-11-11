package com.chernobyl.inv;

import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Lógica de crafteo independiente de la interfaz. Corre en servidor con
 * ticks reales: cerrar el inventario no detiene nada.
 *
 * <p>Flujo: valida materiales (inventario + mochila vía {@link BackpackCompat})
 * → consume → encola la orden (máx. {@link RecipeDatabase#queueMax()} en total)
 * → al terminar intenta entregar en inventario, luego en mochila, y si no
 * hay hueco guarda el objeto en {@link CraftingQueue#finished()} sin perderlo.
 * Al cancelar se devuelven todos los materiales consumidos (mismo fallback).</p>
 */
@Mod.EventBusSubscriber(modid = ChernobylInv.MODID)
public final class CraftingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("chernobylinv/crafting");

    private CraftingManager() {}

    // ---------- Inventario: contar, consumir y entregar ----------

    public static int countItem(Player player, net.minecraft.world.item.Item item) {
        int total = 0;
        if (player == null || item == null) return 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == item) total += stack.getCount();
        }
        IItemHandlerModifiable backpack = BackpackCompat.current().handler(player);
        if (backpack != null) {
            for (int i = 0; i < backpack.getSlots(); i++) {
                ItemStack stack = backpack.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() == item) total += stack.getCount();
            }
        }
        return total;
    }

    /** Consume los materiales. Devuelve la lista consumida para poder reembolsarla. */
    public static List<ItemStack> consume(ServerPlayer player, RecipeDatabase.Recipe recipe, int units) {
        List<ItemStack> consumed = new ArrayList<>();
        if (!canCraft(player, recipe, units)) return List.of();

        for (RecipeDatabase.Recipe.Ingredient ingredient : recipe.ingredients()) {
            int remaining = ingredient.amount() * units;

            for (ItemStack stack : player.getInventory().items) {
                if (remaining <= 0) break;
                if (stack.isEmpty() || stack.getItem() != ingredient.item()) continue;
                int taken = Math.min(remaining, stack.getCount());
                ItemStack takenStack = stack.copy();
                takenStack.setCount(taken);
                consumed.add(takenStack);
                stack.shrink(taken);
                remaining -= taken;
            }

            if (remaining > 0) {
                IItemHandlerModifiable backpack = BackpackCompat.current().handler(player);
                if (backpack != null) {
                    for (int i = 0; i < backpack.getSlots() && remaining > 0; i++) {
                        ItemStack stack = backpack.getStackInSlot(i);
                        if (stack.isEmpty() || stack.getItem() != ingredient.item()) continue;
                        int taken = Math.min(remaining, stack.getCount());
                        ItemStack takenStack = stack.copy();
                        takenStack.setCount(taken);
                        consumed.add(takenStack);
                        ItemStack copy = stack.copy();
                        copy.shrink(taken);
                        backpack.setStackInSlot(i, copy);
                        remaining -= taken;
                    }
                }
            }

            if (remaining > 0) {
                // No ocurre tras canCraft(), pero evita duplicar materiales.
                refund(player, consumed);
                return List.of();
            }
        }

        player.getInventory().setChanged();
        return consumed;
    }

    public static boolean canCraft(Player player, RecipeDatabase.Recipe recipe, int units) {
        if (recipe == null || units <= 0) return false;
        if (recipe.workbenchLevel() > 0) return false; // mesas de trabajo: pendiente futuro
        for (RecipeDatabase.Recipe.Ingredient ingredient : recipe.ingredients()) {
            if (countItem(player, ingredient.item()) < ingredient.amount() * units) return false;
        }
        return true;
    }

    /** Entrega con respaldo: inventario → mochila → almacenamiento de salida. Nunca se pierde. */
    public static void give(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ItemStack remaining = stack.copy();

        if (!player.getInventory().add(remaining)) {
            IItemHandlerModifiable backpack = BackpackCompat.current().handler(player);
            if (backpack != null) {
                remaining = ItemHandlerHelper.insertItemStacked(backpack, remaining, false);
            }
        }

        if (!remaining.isEmpty()) {
            CraftingSave save = CraftingSave.getFor(player);
            if (save != null) {
                CraftingQueue queue = save.queue(player.getUUID());
                queue.addFinished(remaining);
                save.markDirty();
            } else {
                player.drop(remaining, false);
            }
        }
        player.getInventory().setChanged();
    }

    /** Devuelve materiales (reembolso o vuelta de cancelación) con el mismo respaldo. */
    public static void refund(ServerPlayer player, List<ItemStack> stacks) {
        if (stacks == null) return;
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                give(player, stack.copy());
            }
        }
    }

    // ---------- Cola ----------

    public static CraftingQueue queueOf(ServerPlayer player) {
        CraftingSave save = CraftingSave.getFor(player);
        return save == null ? new CraftingQueue() : save.queue(player.getUUID());
    }

    public static boolean enqueue(ServerPlayer player, String recipeId, int units) {
        CraftingSave save = CraftingSave.getFor(player);
        if (save == null) return false;
        CraftingQueue queue = save.queue(player.getUUID());

        RecipeDatabase.Recipe recipe = RecipeDatabase.byId(recipeId);
        if (recipe == null || !recipe.enabled()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Receta no disponible."), true);
            return false;
        }
        if (queue.entries().size() >= RecipeDatabase.queueMax()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Cola de fabricación llena."), true);
            return false;
        }
        int amount = Math.max(1, Math.min(units, recipe.maxCraftAmount()));
        if (!canCraft(player, recipe, amount)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Faltan materiales para esta receta."), true);
            return false;
        }

        List<ItemStack> consumed = consume(player, recipe, amount);
        if (consumed.isEmpty() && !recipe.ingredients().isEmpty()) return false;

        CraftingQueue.Entry entry = new CraftingQueue.Entry(recipeId, amount, recipe.totalTicks(amount));
        entry.consumed.addAll(consumed);
        queue.addEntry(entry);
        save.markDirty();
        syncState(player);
        return true;
    }

    public static boolean cancel(ServerPlayer player, int queueIndex) {
        CraftingSave save = CraftingSave.getFor(player);
        if (save == null) return false;
        CraftingQueue queue = save.queue(player.getUUID());

        if (queueIndex < 0 || queueIndex >= queue.entries().size()) return false;
        CraftingQueue.Entry entry = queue.entries().get(queueIndex);
        queue.removeEntry(entry);
        refund(player, entry.consumed);
        save.markDirty();
        syncState(player);
        return true;
    }

    /** Intenta entregar al jugador lo acumulado en almacenamiento de salida. */
    public static void flushFinished(ServerPlayer player) {
        CraftingSave save = CraftingSave.getFor(player);
        if (save == null) return;
        CraftingQueue queue = save.queue(player.getUUID());
        if (queue.finished().isEmpty()) return;

        List<ItemStack> keep = new ArrayList<>();
        for (ItemStack stack : queue.finished()) {
            ItemStack remaining = stack.copy();
            if (!player.getInventory().add(remaining)) {
                IItemHandlerModifiable backpack = BackpackCompat.current().handler(player);
                if (backpack != null) {
                    remaining = ItemHandlerHelper.insertItemStacked(backpack, remaining, false);
                }
            }
            if (!remaining.isEmpty()) keep.add(remaining);
        }
        queue.finished().clear();
        queue.finished().addAll(keep);
        save.markDirty();
        player.getInventory().setChanged();
        if (!keep.isEmpty()) {
            syncState(player);
        }
    }

    // ---------- Ticks (segundo plano real) ----------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) return;
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
    }

    public static void tickPlayer(ServerPlayer player) {
        CraftingSave save = CraftingSave.getFor(player);
        if (save == null) return;
        CraftingQueue queue = save.queue(player.getUUID());
        if (queue.isEmpty()) {
            if (!queue.finished().isEmpty()) flushFinished(player);
            return;
        }

        // Entregar salida pendiente si apareció hueco (p. ej. el jugador recogió algo).
        flushFinished(player);

        CraftingQueue.Entry first = queue.entries().get(0);
        first.remainingTicks--;
        save.markDirty();

        if (first.remainingTicks <= 0) {
            RecipeDatabase.Recipe recipe = RecipeDatabase.byId(first.recipeId);
            queue.removeEntry(first);
            if (recipe != null) {
                int total = recipe.outputCount() * first.units;
                ItemStack output = new ItemStack(recipe.outputItem(), 1);
                int maxStack = Math.max(1, output.getMaxStackSize());
                int left = total;
                while (left > 0) {
                    int count = Math.min(left, maxStack);
                    give(player, new ItemStack(recipe.outputItem(), count));
                    left -= count;
                }
                player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE,
                        SoundSource.PLAYERS, 0.45F, 1.25F);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Fabricado: ").append(output.getHoverName()), true);
            } else {
                LOGGER.warn("Craft queue entry with unknown recipe '{}' removed without output", first.recipeId);
            }
            save.markDirty();
        }

        syncState(player);
    }

    public static void syncState(ServerPlayer player) {
        CraftSyncState.send(player);
    }

    /** Recibe una orden del cliente (FABRICAR). */
    public static void handleEnqueue(ServerPlayer player, String recipeId, int units) {
        if (enqueue(player, recipeId, units)) {
            if (player.containerMenu != null) {
                player.containerMenu.broadcastChanges();
            }
        }
    }
}
