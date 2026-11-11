package com.chernobyl.inv;

import dev.ghen.thirst.foundation.common.capability.IThirst;
import dev.ghen.thirst.foundation.common.capability.ModCapabilities;
import net.minecraft.world.entity.player.Player;

final class ThirstCompat {
    private ThirstCompat() {}

    // Sed actual del jugador (0 a 20), o -1 si no está disponible
    static int value(Player p) {
        return p.getCapability(ModCapabilities.PLAYER_THIRST).map(IThirst::getThirst).orElse(-1);
    }
}