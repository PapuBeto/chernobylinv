package com.chernobyl.inv;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Inventario custom ChernobylInv.
 *
 * <p>La mochila se muestra a través de la capa {@link BackpackCompat}
 * (hoy Sophisticated Backpacks). Cuando el owner conecte su propio mod de
 * mochilas, solo se cambia esa implementación; este menú no cambia.</p>
 */
public class InvMenu extends AbstractContainerMenu {
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    // Orden: 0-3 armadura, 4-30 inventario, 31-36 cinturón,
    // 37-39 bolsillos (slots 6-8 del inventario vanilla), 40-66 mochila (ventana 9x3).
    public static final int MAIN_START = 4, BELT_START = 31, RESERVE_START = 37,
            BACK_START = 40, BACK_END = 67;

    /**
     * @deprecated Solo quedan para que {@link LootView} (aparcado) siga
     * compilando. No se crean slots de loot desde este menú.
     */
    @Deprecated
    public static final int LOOT_COLS = 9, LOOT_ROWS = 6, LOOT_SLOTS = LOOT_COLS * LOOT_ROWS;

    /**
     * @deprecated Solo queda para que {@link LootView} (aparcado) siga
     * compilando. No se crean slots de loot desde este menú.
     */
    @Deprecated
    public static final int LOOT_START = BACK_END, LOOT_END = LOOT_START + LOOT_SLOTS;

    /**
     * @deprecated Solo queda para compatibilidad con {@link LootView}
     * (aparcado). No se crean slots de loot desde este menú.
     */
    @Deprecated
    public static final int LOOT_X = 369, LOOT_Y = 52, LOOT_PITCH = 16;

    private final DataSlot totalSlots = DataSlot.standalone();
    private final DataSlot rowOffset = DataSlot.standalone();
    private final BackpackView serverView; // solo existe en el servidor

    public InvMenu(int id, Inventory inv, Container view) {
        this(ChernobylInv.INV_MENU.get(), id, inv, view);
    }

    public InvMenu(MenuType<?> type, int id, Inventory inv, Container view) {
        super(type, id);
        this.serverView = view instanceof BackpackView bv ? bv : null;

        this.addDataSlot(totalSlots);
        this.addDataSlot(rowOffset);

        // Ropa (armadura), alternada a los lados del personaje:
        // casco = derecha, pechera = izquierda, piernas = derecha, botas = izquierda
        for (int i = 0; i < 4; i++) {
            final EquipmentSlot typeSlot = ARMOR[i];
            final int ax = (i % 2 == 0) ? 127 : 15;
            this.addSlot(new Slot(inv, 39 - i, ax, 67 + i * 56) {
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean mayPlace(ItemStack stack) { return stack.canEquip(typeSlot, inv.player); }
            });
        }

        // Inventario principal 9x3
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, 9 + r * 9 + c, 179 + c * 18, 36 + r * 18));
            }
        }

        // Cinturón: 6 slots
        for (int i = 0; i < 6; i++) {
            this.addSlot(new Slot(inv, i, 179 + i * 18, 247));
        }

        // Bolsillos: los 3 slots de hotbar que la HUD de 6 no muestra.
        // Sin estos slots, lo que se recoge ahí queda invisible e inaccesible.
        for (int i = 0; i < 3; i++) {
            this.addSlot(new Slot(inv, 6 + i, 291 + i * 18, 247));
        }

        // Mochila: ventana de 9x3 a través de la capa de compatibilidad
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new BackpackSlot(this, view, r * 9 + c, 179 + c * 18, 114 + r * 18));
            }
        }
    }

    public boolean hasBackpack() { return totalSlots.get() > 0; }

    public int backpackSlots() { return totalSlots.get(); }

    public int rowOffsetRows() { return rowOffset.get(); }

    public boolean viewSlotActive(int viewIndex) {
        return rowOffset.get() * BackpackView.COLS + viewIndex < totalSlots.get();
    }

    /** Solo en el servidor. */
    public void scroll(int dir) {
        if (serverView != null) serverView.setRowOffset(serverView.rowOffset() + dir);
    }

    @Override
    public void broadcastChanges() {
        if (serverView != null) {
            totalSlots.set(serverView.total());
            rowOffset.set(serverView.rowOffset());
        }
        super.broadcastChanges();
    }

    /**
     * @deprecated El loot de SA queda aparcado; siempre 0.
     */
    @Deprecated
    public int lootSlots() { return 0; }

    /**
     * @deprecated El loot de SA queda aparcado; siempre false.
     */
    @Deprecated
    public boolean hasLoot() { return false; }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < MAIN_START || index >= BACK_START) {
            // armadura o mochila -> inventario / cinturón
            if (!this.moveItemStackTo(stack, MAIN_START, BACK_START, false)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            // 1) intenta equipar si es armadura
            EquipmentSlot es = LivingEntity.getEquipmentSlotForItem(stack);
            int armorIdx = 3 - es.getIndex();
            if (es.getType() == EquipmentSlot.Type.ARMOR && !this.slots.get(armorIdx).hasItem()) {
                moved = this.moveItemStackTo(stack, armorIdx, armorIdx + 1, false);
            }
            // 2) a la mochila si hay una puesta
            if (!moved && hasBackpack()) {
                moved = this.moveItemStackTo(stack, BACK_START, BACK_END, false);
            }
            // 3) inventario <-> cinturón
            if (!moved) {
                moved = index < BELT_START
                        ? this.moveItemStackTo(stack, BELT_START, BACK_START, false)
                        : this.moveItemStackTo(stack, MAIN_START, BELT_START, false);
            }
            if (!moved) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    // Slot de la mochila: se oculta si no hay mochila o si no existe ese slot
    static class BackpackSlot extends Slot {
        private final InvMenu menu;
        private final int viewIndex;

        BackpackSlot(InvMenu menu, Container view, int viewIndex, int x, int y) {
            super(view, viewIndex, x, y);
            this.menu = menu;
            this.viewIndex = viewIndex;
        }

        @Override public boolean isActive() { return menu.viewSlotActive(viewIndex); }

        @Override public boolean mayPickup(Player player) { return isActive(); }

        @Override public boolean mayPlace(ItemStack stack) {
            return isActive() && this.container.canPlaceItem(this.getContainerSlot(), stack);
        }
    }

    /**
     * @deprecated Aparcado junto con el loot de Survivor's Arsenal.
     * Se mantiene para que {@link LootView} siga compilando.
     */
    @Deprecated
    static class LootSlot extends Slot {
        LootSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override public boolean isActive() { return false; }
    }
}
