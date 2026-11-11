package com.chernobyl.inv;

import net.minecraftforge.fml.common.Mod;

/**
 * Sin lógica por ahora.
 *
 * <p>El guardado automático en la mochila queda aparcado: el owner
 * conectará su propio sistema de mochilas con los slots reservados de
 * {@link InvMenu}. Sin eso, la recogida sigue siendo la vanilla de
 * Minecraft y ningún ítem se toca.</p>
 */
@Mod.EventBusSubscriber(modid = ChernobylInv.MODID)
public final class CommonForgeEvents {
    private CommonForgeEvents() {}
}
