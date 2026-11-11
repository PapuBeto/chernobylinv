package com.chernobyl.inv.mixin;

import net.minecraftforge.network.NetworkHooks;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin aparcado: la integración de los cofres de loot de Survivor's
 * Arsenal queda sin uso. Se mantiene la clase y la anotación para que el
 * proyecto siga compilando y el owner pueda reactivarlo luego; no tiene
 * inyecciones activas.
 */
@Mixin(NetworkHooks.class)
public final class NetworkHooksMixin {
}
