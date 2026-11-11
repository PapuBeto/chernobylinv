package com.chernobyl.inv;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import com.chernobyl.inv.mixin.SlotAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventario de tres zonas:
 * <ul>
 *   <li>Izquierda: personaje + equipamiento (el personaje se desvanece al
 *       abrir el detalle de una receta).</li>
 *   <li>Centro: inventario principal + mochila (slots reales del menú).</li>
 *   <li>Derecha: catálogo de crafteo o detalle de la receta seleccionada,
 *       con transición animada entre ambos.</li>
 * </ul>
 * Las coordenadas de los slots las define el servidor en {@link InvMenu};
 * por eso el inventario no se desplaza en pantalla, pero los paneles de
 * la derecha sí animan su posición y opacidad.
 */
public class InvScreen extends AbstractContainerScreen<InvMenu> {
    // Paleta industrial: gris carbon, bordes claros, cian y verde
    private static final int ZONE_BG = 0xFF1D1D1D;
    private static final int PANEL_OUTER = 0xFF060606;
    private static final int PANEL_INNER = 0xFF8C8C8C;
    private static final int PANEL_LINE = 0xFF4A4A4A;
    private static final int LINE_ACCENT = 0xFF4A4A4A;
    private static final int SLOT_BG = 0xFF141414;
    private static final int SLOT_LINE = 0xFF555555;
    private static final int BELT_LINE = 0xFF6FD6D6;
    private static final int BELT_IDLE = 0xFF3F6F6F;
    private static final int HOVER_LINE = 0xFFD8D8D8;
    private static final int GHOST = 0x668A8A8A;
    private static final int SLASH = 0xCCFF5555;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int TEXT_DIM = 0xFF8A8A8A;
    private static final int TITLE = 0xFFE8F6F6;
    private static final int CYAN = 0xFF7FE3E3;
    private static final int GREEN = 0xFF8FE08F;
    private static final int RED = 0xFFFF5555;
    private static final int BAR_BG = 0xFF0A0A0A;

    // Catálogo / detalle (zona derecha)
    private static final int CRAFT_X = 372, CRAFT_Y = 50, CRAFT_W = 132, ROW_H = 22, ROWS = 6;

    private String selectedId = null;
    private int craftUnits = 1;
    private String categoryFilter = "ALL";
    private float transitionT = 0f;
    private int catalogScroll = 0;

    // Posiciones originales de los slots (Estado A). En Estado C el inventario
    // se mueve a la izquierda (donde estaba el personaje), el catálogo al
    // centro y el detalle queda a la derecha.
    private int[] originX;
    private int[] originY;

    // Siluetas 12x12 para los slots de armadura vacios: cabeza, pechera, piernas, botas
    private static final String[][] ARMOR_GHOST = {
            {"............", "...XXXXXX...", "..XXXXXXXX..", ".XXXXXXXXXX.", ".XXXXXXXXXX.", ".XXXXXXXXXX.",
                    ".XXXXXXXXXX.", ".XXX....XXX.", ".XX......XX.", ".XX......XX.", "............", "............"},
            {"..XX....XX..", ".XXXX..XXXX.", "XXXXXXXXXXXX", "XXXXXXXXXXXX", "XXXXXXXXXXXX", ".XXXXXXXXXX.",
                    ".XXXXXXXXXX.", ".XXXXXXXXXX.", ".XXXXXXXXXX.", ".XXXXXXXXXX.", ".XXXXXXXXXX.", "............"},
            {"XXXXXXXXXXXX", "XXXXXXXXXXXX", "XXXXXXXXXXXX", "XXXXX..XXXXX", "XXXX....XXXX", "XXXX....XXXX",
                    "XXXX....XXXX", "XXXX....XXXX", "XXXX....XXXX", "XXXX....XXXX", "XXXX....XXXX", "............"},
            {"............", "..XXX..XXX..", "..XXX..XXX..", "..XXX..XXX..", "..XXX..XXX..", ".XXXX..XXXX.",
                    "XXXXX..XXXXX", "XXXXX..XXXXX", "............", "............", "............", "............"}
    };

    // Icono de mochila 14x14 para cuando no llevas una
    private static final String[] BACKPACK = {
            "....XXXXXX....", "...XX....XX...", "..XXXXXXXXXX..", ".XXXXXXXXXXXX.", ".XXXXXXXXXXXX.",
            ".XXX......XXX.", ".XXX......XXX.", ".XXXXXXXXXXXX.", ".XXX..XX..XXX.", ".XXX..XX..XXX.",
            ".XXXXXXXXXXXX.", ".XXXXXXXXXXXX.", ".XXXXXXXXXXXX.", "..XXXXXXXXXX.."
    };

    public InvScreen(InvMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 520;
        this.imageHeight = 280;
    }

    // Escala de la UI: si la ventana (con GUI Scale alto) no entra, se reduce todo proporcionalmente
    private float uiScale = 1f;

    @Override
    protected void init() {
        super.init();
        float fitW = (this.width - 8f) / this.imageWidth;
        float fitH = (this.height - 8f) / this.imageHeight;
        this.uiScale = Math.max(0.25f, Math.min(1f, Math.min(fitW, fitH)));
        if (originX == null) {
            originX = new int[this.menu.slots.size()];
            originY = new int[this.menu.slots.size()];
            for (int i = 0; i < this.menu.slots.size(); i++) {
                Slot s = this.menu.slots.get(i);
                originX[i] = s.x;
                originY[i] = s.y;
            }
        }
    }

    /** Mueve los slots hacia Estado C (inventario a la izquierda). */
    private void applyLayout() {
        if (originX == null) return;
        int inventoryShiftX = -167; // 179 -> 12 (encaja dentro de la zona izquierda)
        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot s = this.menu.slots.get(i);
            int tx = originX[i];
            int ty = originY[i];
            if (i < 4) {
                // La sección de personaje desaparece entera: la armadura sale de pantalla.
                tx = originX[i] - 400;
            } else if (i >= InvMenu.MAIN_START) {
                tx = originX[i] + inventoryShiftX;
            }
            int nx = Math.round(originX[i] + (tx - originX[i]) * transitionT);
            int ny = Math.round(originY[i] + (ty - originY[i]) * transitionT);
            ((SlotAccessor) (Object) s).chernobylinv$setX(nx);
            ((SlotAccessor) (Object) s).chernobylinv$setY(ny);
        }
    }

    /** Parte un texto en líneas que quepan en {@code width} sin cortar palabras. */
    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null) return lines;
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (this.font.width(candidate) <= width || current.length() == 0) {
                current = new StringBuilder(candidate);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private float uiX(double mx) {
        return (float) ((mx - this.width / 2.0) / uiScale + this.width / 2.0);
    }

    private float uiY(double my) {
        return (float) ((my - this.height / 2.0) / uiScale + this.height / 2.0);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // Fondo oscurecido (sin blur): tapa el mundo con una velo oscuro.
        g.fill(0, 0, this.width, this.height, 0xC0060504);
    }

    // Panel: fondo carbon, borde claro y linea interior oscura
    private void zone(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fill(x1, y1, x2, y2, ZONE_BG);
        g.renderOutline(x1, y1, x2 - x1, y2 - y1, PANEL_OUTER);
        g.renderOutline(x1 + 1, y1 + 1, x2 - x1 - 2, y2 - y1 - 2, PANEL_INNER);
        g.renderOutline(x1 + 2, y1 + 2, x2 - x1 - 4, y2 - y1 - 4, 0xFF303030);
    }

    // Slot: fondo carbon con borde gris; cian en el cinturon, blanco al pasar el mouse
    private void slotBox(GuiGraphics g, int x, int y, int line, boolean hover) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BG);
        g.fill(x, y, x + 16, y + 1, 0xFF0A0A0A);
        g.fill(x, y, x + 1, y + 16, 0xFF0A0A0A);
        g.renderOutline(x - 1, y - 1, 18, 18, hover ? HOVER_LINE : line);
    }

    // Dibuja una mascara de texto (X = pixel encendido), agrupando pixeles seguidos
    private static void mask(GuiGraphics g, String[] rows, int x, int y, int color) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int c = 0;
            while (c < row.length()) {
                if (row.charAt(c) == 'X') {
                    int start = c;
                    while (c < row.length() && row.charAt(c) == 'X') c++;
                    g.fill(x + start, y + r, x + c, y + r + 1, color);
                } else {
                    c++;
                }
            }
        }
    }

    private static float clamp(float v) {
        return Math.max(-45f, Math.min(45f, v));
    }

    private static int alpha(int argb, float f) {
        int a = (int) (((argb >>> 24) & 255) * Math.max(0f, Math.min(1f, f)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Recetas visibles según el filtro de categoría (solo habilitadas). */
    private List<RecipeDatabase.Recipe> visibleRecipes() {
        List<RecipeDatabase.Recipe> out = new ArrayList<>();
        for (RecipeDatabase.Recipe recipe : RecipeDatabase.displayRecipes()) {
            if (!recipe.enabled()) continue;
            if (!categoryFilter.equals("ALL") && !recipe.category().equals(categoryFilter)) continue;
            out.add(recipe);
        }
        return out;
    }

    private String categoryName(String id) {
        for (RecipeDatabase.Category category : RecipeDatabase.displayCategories()) {
            if (category.id().equals(id)) return category.name();
        }
        return id;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos, y = this.topPos;

        // Actualiza la transición antes de dibujar para que paneles y slots
        // se muevan juntos (Estado A ↔ Estado C).
        boolean showDetail = selectedId != null;
        float target = showDetail ? 1f : 0f;
        transitionT += (target - transitionT) * 0.18f;
        if (transitionT < 0.01f) transitionT = 0f;
        if (transitionT > 0.99f) transitionT = 1f;
        applyLayout();

        int leftW = Math.round(146 + (28f * transitionT)); // 146 -> 174 para que quepa el inventario
        int midX = Math.round(170 + (20f * transitionT)); // centro se desplaza según el catálogo
        zone(g, x + 4, y + 16, x + 4 + leftW, y + 268);
        zone(g, x + midX, y + 16, x + 350, y + 268);
        zone(g, x + 362, y + 16, x + 516, y + 268);

        // Linea tenue bajo cada titulo
        g.fill(x + 10, y + 33, x + Math.min(144, 4 + leftW - 6), y + 34, LINE_ACCENT);
        g.fill(x + midX + 8, y + 33, x + 342, y + 34, LINE_ACCENT);
        g.fill(x + 372, y + 33, x + 510, y + 34, LINE_ACCENT);

        // Personaje: la sección entera (zona + personaje + armadura) desaparece en Estado C.
        if (transitionT < 0.05f) {
            float lookX = clamp((float) (x + 88) - mouseX);
            float lookY = clamp((float) (y + 100) - mouseY);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 88, y + 255, 95,
                    lookX, lookY, this.minecraft.player);
        }

        boolean bp = this.menu.hasBackpack();
        int invX = x + Math.round(179 + (-167f * transitionT));
        if (!bp) {
            // Sin mochila: cuadricula apagada con una mochila tachada
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    slotBox(g, invX + c * 18, y + 114 + r * 18, 0x335A5A5A, false);
                }
            }
            g.pose().pushPose();
            g.pose().translate(invX + 67, y + 127, 0);
            g.pose().scale(2f, 2f, 1f);
            mask(g, BACKPACK, 0, 0, GHOST);
            g.pose().popPose();
            for (int k = 0; k < 28; k++) {
                g.fill(invX + 67 + k, y + 127 + k, invX + 70 + k, y + 130 + k, SLASH);
            }
        } else {
            // Barra de scroll si la mochila tiene mas de 3 filas
            int rows = (this.menu.backpackSlots() + 8) / 9;
            if (rows > 3) {
                int th = Math.max(8, 55 * 3 / rows);
                int ty = y + 113 + (55 - th) * this.menu.rowOffsetRows() / (rows - 3);
                g.fill(invX + 164, y + 113, invX + 168, y + 168, 0x88000000);
                g.renderOutline(invX + 163, y + 112, 6, 57, PANEL_INNER);
                g.fill(invX + 164, ty, invX + 168, ty + th, BELT_LINE);
            }
        }

        // Separador entre el cinturón y los bolsillos
        int sepX = invX + 110;
        g.fill(sepX, y + 246, sepX + 1, y + 265, PANEL_LINE);

        // Slots reales (los de la mochila solo se dibujan si estan activos)
        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot s = this.menu.slots.get(i);
            if (!s.isActive()) continue;
            boolean belt = i >= InvMenu.BELT_START && i < InvMenu.RESERVE_START;
            int line = SLOT_LINE;
            if (belt) {
                // El slot del cinturon que coincide con la hotbar seleccionada
                // lleva el cian vivo; los otros quedan en un teal apagado.
                int hotbarSlot = i - InvMenu.BELT_START;
                line = hotbarSlot == this.minecraft.player.getInventory().selected
                        ? BELT_LINE : BELT_IDLE;
            }
            boolean hover = this.isHovering(s.x, s.y, 16, 16, mouseX, mouseY);
            slotBox(g, x + s.x, y + s.y, line, hover);
            // Los 4 primeros son armadura: silueta si estan vacios
            if (i < 4 && !s.hasItem()) {
                mask(g, ARMOR_GHOST[i], x + s.x + 2, y + s.y + 2, GHOST);
            }
        }

        renderCraftPanel(g, x, y, mouseX, mouseY);
    }

    /** Lectura corta del Geiger en la zona del personaje. */
    private void renderGeigerLine(GuiGraphics g, int x, int y) {
        Player p = this.minecraft.player;
        if (p == null || !ModList.get().isLoaded("radioactive")) return;
        try {
            if (!RadCompat.hasGeiger(p)) {
                g.drawString(this.font, "RAD: sin geiger", x, y, TEXT_DIM, false);
                return;
            }
            int[] rad = RadCompat.radiation(p);
            if (rad == null) {
                g.drawString(this.font, "RAD: ...", x, y, TEXT_DIM, false);
                return;
            }
            RadCompat.Readout readout = RadCompat.geigerReadout(p, System.currentTimeMillis());
            String text = readout == null ? (rad[0] + "/" + rad[1]) : readout.text();
            int color = readout == null ? TEXT : readout.color();
            g.drawString(this.font, "RAD " + text, x, y, color, false);
        } catch (Throwable ignored) {
            g.drawString(this.font, "RAD: n/d", x, y, TEXT_DIM, false);
        }
    }

    private void renderCraftPanel(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        renderCatalog(g, x, y, mouseX, mouseY, 1f);
        if (transitionT > 0.02f) renderDetail(g, x, y, mouseX, mouseY, transitionT);
    }

    private int catalogPx(int x) {
        // Estado A: catálogo a la derecha (372). Estado C: catálogo al centro,
        // dentro de la zona central (190..350) con 8px de margen.
        return x + Math.round(372 + (198 - 372) * transitionT);
    }

    private void renderCatalog(GuiGraphics g, int x, int y, int mouseX, int mouseY, float fade) {
        int px = catalogPx(x);
        int py = y + CRAFT_Y;

        // Filtro de categoría (hace clic para ciclar: TODAS → categoría… → TODAS)
        g.drawString(this.font, "Cat.: " + (categoryFilter.equals("ALL") ? "TODAS" : categoryName(categoryFilter)),
                px, py, alpha(CYAN, fade), false);

        List<RecipeDatabase.Recipe> visible = visibleRecipes();
        int maxScroll = Math.max(0, visible.size() - ROWS);
        catalogScroll = Math.max(0, Math.min(catalogScroll, maxScroll));

        for (int row = 0; row < ROWS; row++) {
            int index = catalogScroll + row;
            if (index >= visible.size()) break;
            RecipeDatabase.Recipe recipe = visible.get(index);
            int ry = py + 16 + row * ROW_H;
            boolean hover = isHovering(px, ry, CRAFT_W, ROW_H - 2, mouseX, mouseY);
            boolean selected = recipe.id().equals(selectedId);

            // Escala al pasar el cursor: borde más grueso (efecto sutil)
            int pad = hover ? 1 : 0;
            g.fill(px - pad, ry, px + CRAFT_W + pad, ry + ROW_H - 2, alpha(hover || selected ? 0xFF2A2A2A : 0xFF181818, fade));
            if (hover || selected) {
                g.renderOutline(px - pad, ry, CRAFT_W + pad * 2, ROW_H - 2, alpha(selected ? CYAN : HOVER_LINE, fade));
            }

            ItemStack output = new ItemStack(recipe.outputItem(), recipe.outputCount());
            g.renderItem(output, px + 2, ry + 2);
            g.renderItemDecorations(this.font, output, px + 2, ry + 2);
            g.drawString(this.font, this.font.plainSubstrByWidth(recipe.name(), 106),
                    px + 22, ry + 3, alpha(selected ? CYAN : TEXT, fade), false);
            g.drawString(this.font, this.font.plainSubstrByWidth(ingredientSummary(recipe), 106),
                    px + 22, ry + 12, alpha(TEXT_DIM, fade), false);
        }

        if (visible.size() > ROWS) {
            int barHeight = Math.max(8, (ROWS * ROW_H) * ROWS / visible.size());
            int barY = py + 16 + (ROWS * ROW_H - barHeight) * catalogScroll / Math.max(1, maxScroll);
            g.fill(px + CRAFT_W + 2, py + 16, px + CRAFT_W + 4, py + 16 + ROWS * ROW_H, alpha(0x88000000, fade));
            g.fill(px + CRAFT_W + 2, barY, px + CRAFT_W + 4, barY + barHeight, alpha(BELT_LINE, fade));
        }

        if (visible.isEmpty()) {
            g.drawString(this.font, "(sin recetas en esta categoría)", px, py + 20, alpha(TEXT_DIM, fade), false);
        }

        renderQueue(g, px, py + 16 + ROWS * ROW_H + 10, fade);
    }

    private void renderDetail(GuiGraphics g, int x, int y, int mouseX, int mouseY, float fade) {
        int px = x + CRAFT_X + Math.round((1f - fade) * 30f);
        int py = y + CRAFT_Y;
        RecipeDatabase.Recipe recipe = RecipeDatabase.displayById(selectedId);
        if (recipe == null) return;

        ItemStack output = new ItemStack(recipe.outputItem(), recipe.outputCount() * craftUnits);
        g.renderItem(output, px + 2, py);
        g.renderItemDecorations(this.font, output, px + 2, py);
        g.drawString(this.font, this.font.plainSubstrByWidth(recipe.name() + " x" + craftUnits, 108),
                px + 22, py + 2, alpha(GREEN, fade), false);
        // Descripción con salto de línea por palabras: no se corta a mitad de palabra.
        int descY = py + 24;
        for (String lineText : wrap(recipe.description(), 130)) {
            g.drawString(this.font, lineText, px, descY, alpha(TEXT_DIM, fade), false);
            descY += 10;
        }

        g.drawString(this.font, "Tiempo: " + recipe.craftingTimeSec() + "s c/u | x" + craftUnits, px, descY + 2, alpha(CYAN, fade), false);

        int line = descY + 16;
        for (RecipeDatabase.Recipe.Ingredient ingredient : recipe.ingredients()) {
            int need = ingredient.amount() * craftUnits;
            int have = countHeld(ingredient.item());
            int color = have >= need ? GREEN : RED;
            g.drawString(this.font, this.font.plainSubstrByWidth(
                            ingredient.item().getDescription().getString() + ": " + have + " / " + need, 130),
                    px, line, alpha(color, fade), false);
            line += 11;
        }

        // Selector de cantidad
        int qtyY = line + 4;
        g.drawString(this.font, "Cant:", px, qtyY + 3, alpha(TEXT, fade), false);
        drawSmallButton(g, px + 44, qtyY, 22, 16, "-", mouseX, mouseY, fade);
        g.drawString(this.font, String.valueOf(craftUnits), px + 70, qtyY + 3, alpha(TEXT, fade), false);
        drawSmallButton(g, px + 92, qtyY, 22, 16, "+", mouseX, mouseY, fade);

        // Botón FABRICAR
        boolean canCraft = hasMaterials(recipe);
        int buttonX = px, buttonY = qtyY + 26, buttonW = 132, buttonH = 20;
        g.fill(buttonX, buttonY, buttonX + buttonW, buttonY + buttonH,
                alpha(canCraft ? 0xFF263A3A : 0xFF181818, fade));
        g.renderOutline(buttonX, buttonY, buttonW, buttonH, alpha(canCraft ? CYAN : SLOT_LINE, fade));
        String button = canCraft ? "FABRICAR" : "FALTAN MATERIALES";
        g.drawString(this.font, button, buttonX + (buttonW - this.font.width(button)) / 2,
                buttonY + 6, alpha(canCraft ? CYAN : TEXT_DIM, fade), false);

        // Botón volver
        int backY = buttonY + 26;
        g.drawString(this.font, "< Volver al catálogo", px, backY, alpha(TEXT_DIM, fade), false);

        renderQueue(g, px, backY + 18, fade);
    }

    private void renderQueue(GuiGraphics g, int px, int py, float fade) {
        g.drawString(this.font, "COLA (" + ClientCraftingCache.entries().size() + "/" + RecipeDatabase.displayQueueMax() + ")",
                px, py, alpha(CYAN, fade), false);
        int line = py + 12;
        ReplyFix:
        for (int i = 0; i < ClientCraftingCache.entries().size(); i++) {
            ClientCraftingCache.Entry entry = ClientCraftingCache.entries().get(i);
            RecipeDatabase.Recipe recipe = RecipeDatabase.displayById(entry.recipeId);
            String name = recipe == null ? entry.recipeId : recipe.name();
            g.drawString(this.font, this.font.plainSubstrByWidth((i + 1) + ". " + name + " x" + entry.units, 104),
                    px, line, alpha(i == 0 ? GREEN : TEXT, fade), false);
            line += 11;
            if (i == 0) {
                float progress = entry.progress();
                g.fill(px, line, px + 124, line + 3, alpha(BAR_BG, fade));
                g.fill(px, line, px + Math.round(124 * progress), line + 3, alpha(BELT_LINE, fade));
                line += 5;
            }
            // botón cancelar (X)
            g.drawString(this.font, "x", px + 118, line - 13, alpha(RED, fade), false);
            if (line > py + 90) break ReplyFix;
        }
        if (ClientCraftingCache.finished() > 0) {
            g.drawString(this.font, "En salida: " + ClientCraftingCache.finished() + " (está lleno)", px, line + 2,
                    alpha(0xFFE8D06A, fade), false);
        }
    }

    private void drawSmallButton(GuiGraphics g, int bx, int by, int bw, int bh, String label,
                                 int mouseX, int mouseY, float fade) {
        boolean hover = isHovering(bx, by, bw, bh, mouseX, mouseY);
        g.fill(bx, by, bx + bw, by + bh, alpha(hover ? 0xFF2A2A2A : 0xFF181818, fade));
        g.renderOutline(bx, by, bw, bh, alpha(hover ? HOVER_LINE : SLOT_LINE, fade));
        g.drawString(this.font, label, bx + (bw - this.font.width(label)) / 2, by + 4, alpha(TEXT, fade), false);
    }

    private static String ingredientSummary(RecipeDatabase.Recipe recipe) {
        StringBuilder text = new StringBuilder();
        for (RecipeDatabase.Recipe.Ingredient ingredient : recipe.ingredients()) {
            if (text.length() > 0) text.append("  ");
            text.append(ingredient.amount()).append("x ").append(ingredient.item().getDescription().getString());
        }
        return text.toString();
    }

    private int countHeld(net.minecraft.world.item.Item item) {
        if (this.minecraft == null || this.minecraft.player == null) return 0;
        Player p = this.minecraft.player;
        int total = 0;
        for (ItemStack stack : p.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == item) total += stack.getCount();
        }
        return total;
    }

    private boolean hasMaterials(RecipeDatabase.Recipe recipe) {
        for (RecipeDatabase.Recipe.Ingredient ingredient : recipe.ingredients()) {
            if (countHeld(ingredient.item()) < ingredient.amount() * craftUnits) return false;
        }
        return true;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        String name = this.minecraft.player.getName().getString().toUpperCase();
        int invLabelX = Math.round(179 + (-167f * transitionT));
        if (transitionT < 0.05f) {
            g.drawString(this.font, this.font.plainSubstrByWidth(name, 130), 10, 22, TITLE, false);
        }
        g.drawString(this.font, "INVENTARIO", invLabelX, 24, CYAN, false);
        g.drawString(this.font, "CINTURON", invLabelX + 27, 234, CYAN, false);
        g.drawString(this.font, "BOLSILLOS", invLabelX + 104, 234, TEXT_DIM, false);

        boolean bp = this.menu.hasBackpack();
        g.drawString(this.font, "MOCHILA", invLabelX, 102, bp ? GREEN : TEXT_DIM, false);
        if (bp) {
            g.drawString(this.font, this.menu.backpackSlots() + " slots", invLabelX + 56, 102, GREEN, false);
        } else {
            g.drawString(this.font, "sin mochila", invLabelX + 56, 102, TEXT_DIM, false);
        }

        int catalogLabelX = Math.round(372 + (198 - 372) * transitionT);
        g.drawString(this.font, "CRAFTEO", catalogLabelX, 22, CYAN, false);
        if (transitionT > 0.05f) {
            g.drawString(this.font, "RECETA", 372, 22, CYAN, false);
        }
    }

    // Rueda del mouse sobre la mochila = scroll
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        double ux = uiX(mx), uy = uiY(my);
        int backpackX = this.leftPos + Math.round(179 + (-167f * transitionT));
        if (this.menu.hasBackpack()
                && ux >= backpackX && ux < backpackX + 162
                && uy >= this.topPos + 113 && uy < this.topPos + 168) {
            Net.CHANNEL.sendToServer(new ScrollBackpackPacket(delta > 0 ? -1 : 1));
            return true;
        }

        int catX = this.catalogPx(this.leftPos);
        if (ux >= catX && ux < catX + CRAFT_W
                && uy >= this.topPos + CRAFT_Y + 16 && uy < this.topPos + CRAFT_Y + 16 + ROWS * ROW_H) {
            int count = visibleRecipes().size();
            int maxScroll = Math.max(0, count - ROWS);
            this.catalogScroll = Math.max(0, Math.min(maxScroll,
                    this.catalogScroll + (delta > 0 ? -1 : 1)));
            return true;
        }

        return super.mouseScrolled(ux, uy, delta);
    }

    // Los eventos del mouse llegan en coordenadas de pantalla; los pasamos a coordenadas de la UI
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double ux = uiX(mx);
        double uy = uiY(my);
        if (button == 0 && clickCraftPanel(ux, uy)) return true;
        return super.mouseClicked(ux, uy, button);
    }

    private boolean clickCraftPanel(double ux, double uy) {
        int listY = this.topPos + CRAFT_Y;
        int listX = this.catalogPx(this.leftPos);
        if (selectedId != null) {
            listX = this.leftPos + CRAFT_X;
        }

        if (selectedId == null) {
            // Filtro de categoría
            if (ux >= listX && ux < listX + CRAFT_W && uy >= listY && uy < listY + 14) {
                cycleCategory();
                return true;
            }
            // Filas del catálogo
            List<RecipeDatabase.Recipe> visible = visibleRecipes();
            for (int row = 0; row < ROWS; row++) {
                int index = this.catalogScroll + row;
                if (index >= visible.size()) break;
                double rowY = listY + 16 + row * ROW_H;
                if (ux >= listX && ux < listX + CRAFT_W && uy >= rowY && uy < rowY + ROW_H - 2) {
                    RecipeDatabase.Recipe recipe = visible.get(index);
                    this.selectedId = recipe.id();
                    this.craftUnits = 1;
                    return true;
                }
            }
        } else {
            RecipeDatabase.Recipe recipe = RecipeDatabase.displayById(selectedId);
            if (recipe == null) {
                this.selectedId = null;
                return true;
            }
            int descLines = wrap(recipe.description(), 130).size();
            int qtyAbsY = listY + 24 + descLines * 10 + 2 + 14 + recipe.ingredients().size() * 11 + 4;
            if (ux >= listX + 44 && ux < listX + 66 && uy >= qtyAbsY && uy < qtyAbsY + 16) {
                this.craftUnits = Math.max(1, this.craftUnits - 1);
                return true;
            }
            if (ux >= listX + 92 && ux < listX + 114 && uy >= qtyAbsY && uy < qtyAbsY + 16) {
                this.craftUnits = Math.min(recipe.maxCraftAmount(), this.craftUnits + 1);
                return true;
            }

            int buttonY = qtyAbsY + 26;
            if (ux >= listX && ux < listX + 132 && uy >= buttonY && uy < buttonY + 20 && hasMaterials(recipe)) {
                Net.CHANNEL.sendToServer(new CraftEnqueuePacket(recipe.id(), this.craftUnits));
                return true;
            }

            int backY = buttonY + 26;
            if (ux >= listX && ux < listX + 132 && uy >= backY && uy < backY + 14) {
                this.selectedId = null;
                return true;
            }
        }

        // Cancelar entradas de la cola (x en cada línea)
        if (ux >= listX + 118 && ux < listX + 126) {
            for (int i = 0; i < ClientCraftingCache.entries().size(); i++) {
                double lineY = this.queueLineY(listY, i, selectedId != null);
                if (uy >= lineY - 4 && uy < lineY + 8) {
                    Net.CHANNEL.sendToServer(new CraftCancelPacket(i));
                    return true;
                }
            }
        }

        return false;
    }

    private double queueLineY(int listY, int index, boolean detail) {
        int base;
        if (detail) {
            RecipeDatabase.Recipe recipe = RecipeDatabase.displayById(selectedId);
            int descLines = recipe == null ? 1 : wrap(recipe.description(), 130).size();
            int ing = recipe == null ? 0 : recipe.ingredients().size();
            base = listY + 24 + descLines * 10 + 2 + 14 + ing * 11 + 4 + 26 + 26 + 18 + 12;
        } else {
            base = listY + 16 + ROWS * ROW_H + 10 + 12;
        }
        int line = base;
        for (int i = 0; i < index; i++) {
            line += 11;
            if (i == 0) line += 5;
        }
        return line;
    }

    private void cycleCategory() {
        List<String> ids = new ArrayList<>();
        ids.add("ALL");
        for (RecipeDatabase.Category category : RecipeDatabase.displayCategories()) {
            ids.add(category.id());
        }
        int current = ids.indexOf(this.categoryFilter);
        this.categoryFilter = ids.get((current + 1) % ids.size());
        this.catalogScroll = 0;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return super.mouseReleased(uiX(mx), uiY(my), button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return super.mouseDragged(uiX(mx), uiY(my), button, dx / uiScale, dy / uiScale);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Oscurecido a pantalla completa, sin zoom (el blur ya está quitado).
        g.fill(0, 0, this.width, this.height, 0xC0060504);
        g.pose().pushPose();
        g.pose().translate(this.width / 2f, this.height / 2f, 0f);
        g.pose().scale(this.uiScale, this.uiScale, 1f);
        g.pose().translate(-this.width / 2f, -this.height / 2f, 0f);

        int mx = Math.round(uiX(mouseX));
        int my = Math.round(uiY(mouseY));
        super.render(g, mx, my, partialTick);
        this.renderTooltip(g, mx, my);

        g.pose().popPose();
    }
}
