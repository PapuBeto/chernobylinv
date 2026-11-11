package com.chernobyl.inv;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nullable;

/** Implementación actual de la capa de mochilas con Sophisticated Backpacks. */
public final class SophisticatedBackpackCompat implements BackpackCompat {

    public static final SophisticatedBackpackCompat INSTANCE = new SophisticatedBackpackCompat();

    private SophisticatedBackpackCompat() {}

    @Nullable
    @Override
    public IItemHandlerModifiable handler(Player player) {
        return BackpackAccess.handler(player);
    }
}
