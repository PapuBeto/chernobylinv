package com.chernobyl.inv.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Permite mover los slots en cliente para la transición dinámica del
 * inventario (Estado A ↔ Estado C) sin recrear el menú del servidor.
 */
@Mixin(Slot.class)
public interface SlotAccessor {
    @Mutable @Accessor("x") void chernobylinv$setX(int x);
    @Mutable @Accessor("y") void chernobylinv$setY(int y);
    @Accessor("x") int chernobylinv$getX();
    @Accessor("y") int chernobylinv$getY();
}
