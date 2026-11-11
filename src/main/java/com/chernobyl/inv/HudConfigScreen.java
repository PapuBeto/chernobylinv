package com.chernobyl.inv;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Configuración del HUD accesible desde el menú de pausa.
 * Dos tarjetas con vista previa real: CENTRO (compacto) y ESQUINA (panel).
 */
public class HudConfigScreen extends Screen {
    private static final int ZONE_BG = 0xFF1D1D1D;
    private static final int CARD_BG = 0xFF181818;
    private static final int CARD_BG_SELECTED = 0xFF242424;
    private static final int PANEL_OUTER = 0xFF060606;
    private static final int PANEL_INNER = 0xFF8C8C8C;
    private static final int SLOT_LINE = 0xFF555555;
    private static final int HOVER_LINE = 0xFFD8D8D8;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int TEXT_DIM = 0xFF8A8A8A;
    private static final int CYAN = 0xFF7FE3E3;
    private static final int GREEN = 0xFF8FE08F;

    private final Screen parent;

    private int panelX, panelY, panelW, panelH;
    private int cardX1, cardX2, cardY, cardW, cardH;
    private int closeX, closeY;
    private static final int CLOSE_W = 100;
    private static final int CLOSE_H = 20;

    public HudConfigScreen(Screen parent) {
        super(Component.literal("Configuración del HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.panelW = Math.min(440, this.width - 32);
        this.panelH = Math.min(230, this.height - 24);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        int gap = 16;
        this.cardW = (this.panelW - 40 - gap) / 2;
        this.cardH = 118;
        this.cardY = this.panelY + 54;
        this.cardX1 = this.panelX + 20;
        this.cardX2 = this.cardX1 + this.cardW + gap;

        this.closeX = (this.width - CLOSE_W) / 2;
        this.closeY = this.panelY + this.panelH - 32;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Fondo oscuro propio, sin blur, para mantener la estética del mod.
        g.fill(0, 0, this.width, this.height, 0xE6060606);

        drawPanel(g);
        drawCard(g, cardX1, cardY, cardW, cardH, true, mouseX, mouseY);
        drawCard(g, cardX2, cardY, cardW, cardH, false, mouseX, mouseY);
        drawClose(g, mouseX, mouseY);

        g.drawCenteredString(this.font, "El cambio se guarda automáticamente",
                this.width / 2, closeY - 11, TEXT_DIM);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawPanel(GuiGraphics g) {
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, ZONE_BG);
        g.renderOutline(panelX, panelY, panelW, panelH, PANEL_OUTER);
        g.renderOutline(panelX + 1, panelY + 1, panelW - 2, panelH - 2, PANEL_INNER);
        g.renderOutline(panelX + 2, panelY + 2, panelW - 4, panelH - 4, 0xFF303030);

        g.drawString(this.font, "CONFIGURACIÓN DEL HUD", panelX + 18, panelY + 14, CYAN, false);
        g.drawString(this.font, "Elige el estilo y la posición de las ruedas",
                panelX + 18, panelY + 30, TEXT_DIM, false);
        g.fill(panelX + 18, panelY + 44, panelX + panelW - 18, panelY + 45, 0xFF4A4A4A);
    }

    private void drawCard(GuiGraphics g, int x, int y, int w, int h,
                          boolean centerMode, int mouseX, int mouseY) {
        boolean selected = centerMode == HudLayout.compact;
        boolean hover = inside(mouseX, mouseY, x, y, w, h);

        g.fill(x, y, x + w, y + h, selected ? CARD_BG_SELECTED : CARD_BG);
        g.renderOutline(x, y, w, h, selected ? CYAN : (hover ? HOVER_LINE : SLOT_LINE));
        if (selected) {
            g.renderOutline(x + 1, y + 1, w - 2, h - 2, 0x557FE3E3);
        }

        String title = centerMode ? "CENTRO" : "ESQUINA";
        String desc = centerMode ? "Sin fondo, compacto" : "Panel, iconos grandes";
        g.drawString(this.font, title, x + 12, y + 10, selected ? CYAN : TEXT, false);
        g.drawString(this.font, desc, x + 12, y + 24, TEXT_DIM, false);

        if (selected) {
            String tag = "ACTIVO";
            g.drawString(this.font, tag, x + w - 12 - this.font.width(tag),
                    y + 10, GREEN, false);
        }

        g.fill(x + 10, y + 42, x + w - 10, y + 43, 0xFF3A3A3A);

        int[] size = StatsHud.previewSize(centerMode);
        int px = x + (w - size[0]) / 2;
        int py = y + 52 + (32 - size[1]) / 2;
        StatsHud.renderPreview(g, px, py, centerMode);

        String dims = size[0] + " x " + size[1];
        g.drawCenteredString(this.font, dims, x + w / 2, y + 94, TEXT_DIM);
    }

    private void drawClose(GuiGraphics g, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, closeX, closeY, CLOSE_W, CLOSE_H);
        g.fill(closeX, closeY, closeX + CLOSE_W, closeY + CLOSE_H,
                hover ? 0xF02A2A2A : CARD_BG);
        g.renderOutline(closeX, closeY, CLOSE_W, CLOSE_H, hover ? CYAN : SLOT_LINE);
        g.drawCenteredString(this.font, "CERRAR", this.width / 2,
                closeY + 6, hover ? CYAN : TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (inside(mouseX, mouseY, closeX, closeY, CLOSE_W, CLOSE_H)) {
                click();
                onClose();
                return true;
            }
            if (inside(mouseX, mouseY, cardX1, cardY, cardW, cardH)) {
                select("center");
                return true;
            }
            if (inside(mouseX, mouseY, cardX2, cardY, cardW, cardH)) {
                select("corner");
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void select(String mode) {
        boolean center = "center".equals(mode);
        if (center == HudLayout.compact && HudSettings.mode() != null) return;

        HudSettings.setMode(mode);
        HudLayout.setMode(mode);
        click();
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
