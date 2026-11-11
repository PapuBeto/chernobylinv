package com.chernobyl.inv;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

public class BackpackView implements Container {
    public static final int COLS = 9, ROWS = 3, SIZE = COLS * ROWS;

    private final Player player;
    private int wantedRow = 0;

    public BackpackView(Player player) { this.player = player; }

    private IItemHandlerModifiable handler() { return BackpackCompat.current().handler(player); }

    public int total() {
        IItemHandlerModifiable h = handler();
        return h == null ? 0 : h.getSlots();
    }

    private int maxRow() { return Math.max(0, (total() + COLS - 1) / COLS - ROWS); }

    public int rowOffset() { return Math.min(wantedRow, maxRow()); }

    public void setRowOffset(int row) { wantedRow = Math.max(0, Math.min(row, maxRow())); }

    private int real(int i) { return rowOffset() * COLS + i; }

    @Override public int getContainerSize() { return SIZE; }

    @Override public boolean isEmpty() {
        for (int i = 0; i < SIZE; i++) if (!getItem(i).isEmpty()) return false;
        return true;
    }

    @Override public ItemStack getItem(int i) {
        IItemHandlerModifiable h = handler();
        int r = real(i);
        return (h == null || r >= h.getSlots()) ? ItemStack.EMPTY : h.getStackInSlot(r);
    }

    @Override public ItemStack removeItem(int i, int amount) {
        IItemHandlerModifiable h = handler();
        int r = real(i);
        if (h == null || r >= h.getSlots()) return ItemStack.EMPTY;
        ItemStack s = h.getStackInSlot(r).copy();
        if (s.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = s.split(amount);
        h.setStackInSlot(r, s);
        return out;
    }

    @Override public ItemStack removeItemNoUpdate(int i) {
        IItemHandlerModifiable h = handler();
        int r = real(i);
        if (h == null || r >= h.getSlots()) return ItemStack.EMPTY;
        ItemStack s = h.getStackInSlot(r).copy();
        h.setStackInSlot(r, ItemStack.EMPTY);
        return s;
    }

    @Override public void setItem(int i, ItemStack stack) {
        IItemHandlerModifiable h = handler();
        int r = real(i);
        if (h != null && r < h.getSlots()) h.setStackInSlot(r, stack);
    }

    @Override public boolean canPlaceItem(int i, ItemStack stack) {
        IItemHandlerModifiable h = handler();
        int r = real(i);
        return h != null && r < h.getSlots() && h.isItemValid(r, stack);
    }

    @Override public void setChanged() {}
    @Override public boolean stillValid(Player p) { return true; }
    @Override public void clearContent() {}
}