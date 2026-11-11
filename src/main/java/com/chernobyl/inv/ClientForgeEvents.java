package com.chernobyl.inv;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ChernobylInv.MODID, value = Dist.CLIENT)
public class ClientForgeEvents {

    private static final int BELT = 6;

    // 2. E abre nuestro inventario (creativo queda con el normal)
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getNewScreen() instanceof InventoryScreen
                && mc.player != null && !mc.player.isCreative()) {
            event.setCanceled(true);
            Net.CHANNEL.sendToServer(new OpenInvPacket());
        }
    }

    // Fondo oscurecido: lo dibuja directamente InvScreen.renderBackground.
    // (Se quitó el blur: el método queda sin uso.)

    // 2b. Botón HUD arriba a la derecha del menú de pausa (Escape)
    @SubscribeEvent
    public static void onPauseMenu(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen pause)) return;

        int y = 6;
        int x = Minecraft.getInstance().getWindow().getGuiScaledWidth() - 50;

        event.addListener(Button.builder(Component.literal("HUD"),
                        b -> Minecraft.getInstance().setScreen(new HudConfigScreen(pause)))
                .bounds(x, y, 44, 20)
                .tooltip(Tooltip.create(Component.literal("Configurar HUD: centro o esquina")))
                .build());
    }

    // 3. Hotbar solo 6 slots
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getInventory().selected >= BELT) {
            mc.player.getInventory().selected = BELT - 1;
        }
    }

    // 4. Rueda del mouse cicla en 6 slots
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.player.isSpectator()) return;
        double delta = event.getScrollDelta();
        if (delta == 0) return;
        event.setCanceled(true);
        int sel = mc.player.getInventory().selected - (int) Math.signum(delta);
        mc.player.getInventory().selected = Math.floorMod(sel, BELT);
    }

    // 5. ══════════════════════════════════════════════════════════════
    //    CANCELA OVERLAYS VANILLA **EN EL MISMO FRAME** ANTES DE QUE SE DIBUJEN
    //    Debe estar en el bus FORGE (default) y tener prioridad ALTA
    // ══════════════════════════════════════════════════════════════
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        // Con el inventario abierto no debe verse ningún HUD encima del blur.
        if (mc.screen instanceof InvScreen) {
            event.setCanceled(true);
            return;
        }
        // Cancelar HUD vanilla siempre que estemos en un mundo (jugando, pausa, inventario, etc.)
        if (mc.player == null || mc.level == null) return;

        // Cancela TODOS los overlays vanilla de HUD
        ResourceLocation id = event.getOverlay().id();
        String ns = id.getNamespace();
        String path = id.getPath();

        // Vanilla health, food, armor, air, etc. (la mira y la hotbar se manejan aparte)
            if (event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()
                    || event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()
                    || event.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type()
                    || event.getOverlay() == VanillaGuiOverlay.AIR_LEVEL.type()
                    || event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()) {
                event.setCanceled(true);
                return;
            }

            // Thirst mod
            if (ns.equals("thirst")) {
                event.setCanceled(true);
                return;
            }

            // Radioactive Geiger (opcional, si querés ocultarlo también)
            if (ns.equals("radioactive") &&
                    (path.equals("geiger_counter") || path.equals("vacuum_tube"))) {
                event.setCanceled(true);
                return;
            }

            // ParCool energy bar
            if (ns.equals("parcool") && path.equals("energy")) {
                event.setCanceled(true);
                return;
            }
    }

    // 6. Hotbar custom 6 slots con estilo STALKER (mismo que el inventario)
    @SubscribeEvent
    public static void onHotbar(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui || p.isSpectator()) return;

        event.setCanceled(true);
        // En el inventario custom no dibujar la hotbar del HUD (el menu ya tiene su fila CINTURON)
        if (mc.screen instanceof InvScreen) return;
        GuiGraphics g = event.getGuiGraphics();

        final int SLOT = 18, GAP = 2, PAD = 4;
        int totalW = BELT * SLOT + (BELT - 1) * GAP + PAD * 2;
        int totalH = SLOT + PAD * 2;
        int x = (event.getWindow().getGuiScaledWidth() - totalW) / 2;
        int y = event.getWindow().getGuiScaledHeight() - totalH - 4;

        zone(g, x, y, x + totalW, y + totalH);

        int selected = p.getInventory().selected;
        for (int i = 0; i < BELT; i++) {
            int sx = x + PAD + i * (SLOT + GAP);
            int sy = y + PAD;
            slotBox(g, sx, sy, i == selected ? BELT_LINE : SLOT_LINE, false);
            ItemStack s = p.getInventory().items.get(i);
            g.renderItem(p, s, sx, sy, i + 1);
            g.renderItemDecorations(mc.font, s, sx, sy);
        }
    }

    // ─── Paleta industrial (misma que InvScreen) ────────────────────
    private static final int ZONE_BG = 0xFF1B1B1B;
    private static final int PANEL_OUTER = 0xFF060606;
    private static final int PANEL_INNER = 0xFF8C8C8C;
    private static final int SLOT_BG = 0xFF141414;
    private static final int SLOT_LINE = 0xFF555555;
    private static final int BELT_LINE = 0xFF6FD6D6;
    private static final int HOVER_LINE = 0xFFD8D8D8;

    // Panel: fondo carbon, borde claro y linea interior oscura
    private static void zone(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fill(x1, y1, x2, y2, ZONE_BG);
        g.renderOutline(x1, y1, x2 - x1, y2 - y1, PANEL_OUTER);
        g.renderOutline(x1 + 1, y1 + 1, x2 - x1 - 2, y2 - y1 - 2, PANEL_INNER);
        g.renderOutline(x1 + 2, y1 + 2, x2 - x1 - 4, y2 - y1 - 4, 0xFF303030);
    }

    // Slot: fondo carbon con borde gris
    private static void slotBox(GuiGraphics g, int x, int y, int line, boolean hover) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BG);
        g.fill(x, y, x + 16, y + 1, 0xFF0A0A0A);
        g.fill(x, y, x + 1, y + 16, 0xFF0A0A0A);
        g.renderOutline(x - 1, y - 1, 18, 18, hover ? HOVER_LINE : line);
    }
}