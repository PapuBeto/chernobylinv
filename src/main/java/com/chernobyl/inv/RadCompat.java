package com.chernobyl.inv;

import com.radar.radioactive.client.config.SyncedServerConfig;
import com.radar.radioactive.client.overlay.ClientRadiationState;
import com.radar.radioactive.client.overlay.GeigerOverlaySelector;
import com.radar.radioactive.common.radiation.RadiationTier;
import com.radar.radioactive.common.registries.RadItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public class RadCompat {
    record Readout(String text, int color) {}

    /** true si el jugador lleva cualquiera de los dos geiger (mano, hotbar, inventario o curios). */
    static boolean hasGeiger(Player p) {
        // Mano, hotbar y slot de curios
        if (GeigerOverlaySelector.getActiveOverlay(p) != GeigerOverlaySelector.OverlayMode.NONE) {
            return true;
        }
        // Resto del inventario (mochila, offhand, armadura)
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (isGeiger(p.getInventory().getItem(i))) return true;
        }
        return isGeiger(p.getOffhandItem());
    }

    private static boolean isGeiger(ItemStack s) {
        return !s.isEmpty()
                && (s.is(RadItems.GEIGER_COUNTER.get()) || s.is(RadItems.SHODDY_GEIGER_COUNTER.get()));
    }

    /** {dosis, maximo} o null si el servidor todavia no mando datos. Solo llamar si "radioactive" esta cargado. */
    static int[] radiation(Player p) {
        if (!ClientRadiationState.hasServerState()) return null;
        float max = SyncedServerConfig.maxRadiation();
        if (max <= 0f) return null;
        float stored = ClientRadiationState.getSmoothed().storedRadiation();
        return new int[]{Math.round(stored), Math.round(max)};
    }

    /** Lectura segun el Geiger que lleves: numero exacto (normal), palabra aproximada (malo) o null (ninguno). */
    static Readout geigerReadout(Player p, long now) {
        GeigerOverlaySelector.OverlayMode mode = GeigerOverlaySelector.getActiveOverlay(p);
        float rate = ClientRadiationState.getSmoothed().effectiveRate();

        if (mode == GeigerOverlaySelector.OverlayMode.OLD) {
            return new Readout(String.format(Locale.ROOT, "%.1f R/s", rate), tierColor(RadiationTier.of(rate)));
        }
        if (mode == GeigerOverlaySelector.OverlayMode.SHODDY) {
            // lectura imprecisa: cambia un poco cada ~0.8 s
            float noise = 0.75f + 0.5f * (float) Math.abs(Math.sin((now / 800L) * 12.9898));
            RadiationTier tier = RadiationTier.of(rate * noise);
            return new Readout(tier.describe().getString(), tierColor(tier));
        }
        return null;
    }

    private static int tierColor(RadiationTier tier) {
        Integer c = tier.color().getColor();
        return 0xFF000000 | (c == null ? 0xFFFFFF : c);
    }
}