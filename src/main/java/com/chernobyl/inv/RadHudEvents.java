package com.chernobyl.inv;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ChernobylInv.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RadHudEvents {
    // true = oculta el HUD de Geiger del mod de radiacion y deja solo el nuestro
    public static final boolean HIDE_THEIR_GEIGER = true;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!HIDE_THEIR_GEIGER) return;
        ResourceLocation id = event.getOverlay().id();
        if (!id.getNamespace().equals("radioactive")) return;

        String path = id.getPath();
        // Mantener solo los efectos de muerte/warp; ocultar geiger, vacuum tube y radiation sense
        if (!path.equals("impending_death") && !path.equals("ego_dissolving")) {
            event.setCanceled(true);
        }
    }
}
