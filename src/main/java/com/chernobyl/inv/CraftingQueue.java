package com.chernobyl.inv;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Cola de fabricación de un jugador: órdenes pendientes, la activa es
 * siempre la primera. Los objetos terminados que no caben en el inventario
 * ni en la mochila quedan en {@link #finished} hasta que haya hueco.
 */
public final class CraftingQueue {

    public static final int FINISHED_MAX_STACKS = 64;

    public static final class Entry {
        public String recipeId;
        public int units;
        public int totalTicks;
        public int remainingTicks;
        /** Materiales consumidos (por unidad multiplicada), para poder devolverlos al cancelar. */
        public final List<ItemStack> consumed = new ArrayList<>();

        public Entry() {}

        public Entry(String recipeId, int units, int totalTicks) {
            this.recipeId = recipeId;
            this.units = units;
            this.totalTicks = Math.max(1, totalTicks);
            this.remainingTicks = this.totalTicks;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<ItemStack> finished = new ArrayList<>();

    public List<Entry> entries() { return entries; }

    public List<ItemStack> finished() { return finished; }

    public boolean isEmpty() { return entries.isEmpty(); }

    public void addEntry(Entry entry) { entries.add(entry); }

    public void removeEntry(Entry entry) { entries.remove(entry); }

    public void addFinished(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (finished.size() >= FINISHED_MAX_STACKS) return;
        finished.add(stack.copy());
    }

    public boolean hasFinishedSpace() { return finished.size() < FINISHED_MAX_STACKS; }

    public float progress() {
        if (entries.isEmpty()) return 1f;
        Entry first = entries.get(0);
        if (first.totalTicks <= 0) return 1f;
        return Math.max(0f, Math.min(1f, 1f - (float) first.remainingTicks / first.totalTicks));
    }
}
