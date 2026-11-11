package com.chernobyl.inv;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Adapta el inventario de un contenedor de loot de Survivor's Arsenal a una
 * vista de tamaño fijo. Los slots sobrantes quedan siempre vacíos, así el menú
 * puede reservar una cuadrícula 9x6 sin depender del tamaño real del cofre.
 */
public final class LootView implements Container {
    private final Container source;
    private final int realSlots;

    public LootView(Container source) {
        this.source = source;
        this.realSlots = Math.min(source.getContainerSize(), InvMenu.LOOT_SLOTS);
    }

    public int realSlots() {
        return realSlots;
    }

    @Override public int getContainerSize() { return InvMenu.LOOT_SLOTS; }

    @Override public boolean isEmpty() {
        for (int i = 0; i < realSlots; i++) {
            if (!source.getItem(i).isEmpty()) return false;
        }
        return true;
    }

    @Override public ItemStack getItem(int slot) {
        return slot >= 0 && slot < realSlots ? source.getItem(slot) : ItemStack.EMPTY;
    }

    @Override public ItemStack removeItem(int slot, int amount) {
        return slot >= 0 && slot < realSlots ? source.removeItem(slot, amount) : ItemStack.EMPTY;
    }

    @Override public ItemStack removeItemNoUpdate(int slot) {
        return slot >= 0 && slot < realSlots ? source.removeItemNoUpdate(slot) : ItemStack.EMPTY;
    }

    @Override public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < realSlots) source.setItem(slot, stack);
    }

    @Override public int getMaxStackSize() { return source.getMaxStackSize(); }

    @Override public void setChanged() { source.setChanged(); }

    @Override public boolean stillValid(Player player) { return source.stillValid(player); }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < realSlots && source.canPlaceItem(slot, stack);
    }

    @Override public void startOpen(Player player) { source.startOpen(player); }

    @Override public void stopOpen(Player player) { source.stopOpen(player); }

    @Override public void clearContent() { source.clearContent(); }
}
