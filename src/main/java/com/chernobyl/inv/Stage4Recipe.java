package com.chernobyl.inv;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.List;

/**
 * Receta de etapa 4. Solo existen las recetas que aparecen en el catálogo
 * editable; no hay crafteo libre. Los ingredientes se cuentan y se consumen
 * del inventario normal y de la mochila equipada.
 */
public record Stage4Recipe(String id, ItemStack output, List<Ingredient> ingredients) {

    public record Ingredient(Item item, int count) {}

    public boolean canCraft(Player player) {
        for (Ingredient ingredient : ingredients) {
            if (count(player, ingredient.item()) < ingredient.count()) return false;
        }
        return true;
    }

    /**
     * Consume los ingredientes. Se comprueba antes para que nunca se consuma
     * media receta si falta un material.
     */
    public boolean consume(Player player) {
        if (!canCraft(player)) return false;

        for (Ingredient ingredient : ingredients) {
            int remaining = ingredient.count();

            for (ItemStack stack : player.getInventory().items) {
                if (remaining <= 0) break;
                if (stack.isEmpty() || stack.getItem() != ingredient.item()) continue;
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }

            if (remaining > 0) {
                IItemHandlerModifiable backpack = BackpackAccess.handler(player);
                if (backpack != null) {
                    for (int i = 0; i < backpack.getSlots() && remaining > 0; i++) {
                        ItemStack stack = backpack.getStackInSlot(i);
                        if (stack.isEmpty() || stack.getItem() != ingredient.item()) continue;
                        int taken = Math.min(remaining, stack.getCount());
                        ItemStack copy = stack.copy();
                        copy.shrink(taken);
                        backpack.setStackInSlot(i, copy);
                        remaining -= taken;
                    }
                }
            }

            // No debería ocurrir después de canCraft(), pero evita duplicar
            // materiales si el inventario cambia entre las dos fases.
            if (remaining > 0) return false;
        }

        player.getInventory().setChanged();
        return true;
    }

    private static int count(Player player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == item) total += stack.getCount();
        }
        IItemHandlerModifiable backpack = BackpackAccess.handler(player);
        if (backpack != null) {
            for (int i = 0; i < backpack.getSlots(); i++) {
                ItemStack stack = backpack.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() == item) total += stack.getCount();
            }
        }
        return total;
    }
}
