package com.chernobyl.inv;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nullable;

/**
 * Capa de compatibilidad de mochilas.
 *
 * <p>Por ahora usa Sophisticated Backpacks. Cuando el owner entregue su
 * propio mod de mochilas, se crea otra implementación de esta interfaz y se
 * cambia el método {@link #current()} sin tocar el inventario ni el crafteo.</p>
 */
public interface BackpackCompat {

    @Nullable
    IItemHandlerModifiable handler(Player player);

    default boolean hasBackpack(Player player) {
        return handler(player) != null;
    }

    /** Compatibilidad actual: Sophisticated Backpacks. */
    static BackpackCompat current() {
        return SophisticatedBackpackCompat.INSTANCE;
    }
}
