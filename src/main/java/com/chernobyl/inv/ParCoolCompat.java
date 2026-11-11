package com.chernobyl.inv;

import com.alrex.parcool.api.Stamina;
import com.alrex.parcool.api.client.gui.ParCoolHUDEvent;
import net.minecraft.world.entity.player.Player;

final class ParCoolCompat {
    private ParCoolCompat() {}

    // {energía actual, energía máxima} o null si ParCool aún no inicializa
    static int[] stamina(Player p) {
        Stamina s = Stamina.get(p);
        return s == null ? null : new int[]{s.getValue(), s.getMaxValue()};
    }

    // Oculta la barra de energía original de ParCool
    public static void onHud(ParCoolHUDEvent.RenderEvent event) {
        event.setCanceled(true);
    }
}