package com.chernobyl.inv;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Base de datos de ítems: resuelve IDs con formato {@code modid:nombre}
 * a {@link Item} del registro. Así las recetas son compatibles con
 * objetos de otros mods sin depender de clases concretas.
 */
public final class ItemDatabase {
    private ItemDatabase() {}

    @Nullable
    public static Item resolve(String id) {
        if (id == null || id.isBlank()) return null;
        ResourceLocation location = ResourceLocation.tryParse(id.trim());
        if (location == null) return null;
        return ForgeRegistries.ITEMS.getValue(location);
    }

    @Nullable
    public static ResourceLocation idOf(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }

    public static String idString(Item item) {
        ResourceLocation location = idOf(item);
        return location == null ? "minecraft:air" : location.toString();
    }
}
