package com.chernobyl.inv;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper;

import javax.annotation.Nullable;

public final class BackpackAccess {
    private BackpackAccess() {}

    // Inventario de la mochila que lleva el jugador en la pechera (o null si no lleva)
    @Nullable
    public static IItemHandlerModifiable handler(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.isEmpty()) return null;
        return chest.getCapability(CapabilityBackpackWrapper.getCapabilityInstance())
                .map(w -> (IItemHandlerModifiable) w.getInventoryHandler())
                .orElse(null);
    }
}