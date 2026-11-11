package com.chernobyl.inv;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Catálogo completo de crafteos. Muestra todas las recetas registradas en
 * {@code config/chernobylinv-recipes.json}; no inventa ni rellena contenido.
 */
public class CraftCatalogScreen extends Screen {
    private static final int PANEL_BG = 0xFF1D1D1D;
    private static final int PANEL_OUTER = 0xFF060606;
    private static final int PANEL_INNER = 0xFF8C8C8C;
    private static final int SLOT_BG = 0xFF141414;
    private static final int SLOT_LINE = 0xFF555555;
    private static final int HOVER_LINE = 0xFFD8D8D8;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int TEXT_DIM = 0xFF8A8A8A;
    private static final int CYAN = 0xFF7FE3E3;
    private static final int GREEN = 0xFF8FE08F;
    private static final int RED = 0xFFFF5555;

    private static final int COLS = 5, ROWS = 4, CELL = 52;
    private static final int PAGE_SIZE = COLS * ROWS;

    private final Screen parent;
    private final EditBox search;
    private String categoryFilter = "ALL";
    private int page = 0;
    private String selectedId = null;
    private int craftUnits = 1;
    private float uiScale = 1f;
    private float pageFade = 1f;

    private final int panelW = 600, panelH = 280;
    private int panelX, panelY;

    public CraftCatalogScreen(Screen parent) {
        super(Component.literal("Catálogo de crafteo"));
        this.parent = parent;
        this.search = new EditBox(Minecraft.getInstance().font, 0, 0, 160, 18, Component.literal("Buscar"));
        this.search.setMaxLength(64);
        this.search.setHint(Component.literal("Buscar receta..."));
        this.search.setResponder(s -> this.page = 0);
    }

    @Override
    protected void init() {
        this.panelX = (Minecraft.getInstance().getWindow().getGuiScaledWidth() - panelW) / 2;
        this.panelY = (Minecraft.getInstance().getWindow().getGuiScaledHeight() - panelH) / 2;
        this.search.setX(this.panelX + 20);
        this.search.setY(this.panelY + 36);
        this.addRenderableWidget(this.search);

        float fitW = (this.width - 8f) / panelW;
        float fitH = (this.height - 8f) / panelH;
        this.uiScale = Math.max(0.25f, Math.min(1f, Math.min(fitW, fitH)));
        this.pageFade = 1f;
    }

    private float uiX(double mx) {
        return (float) ((mx - this.width / 2.0) / uiScale + this.width / 2.0);
    }

    private float uiY(double my) {
        return (float) ((my - this.height / 2.0) / uiScale + this.height / 2.0);
    }

    private boolean isHovering(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private List<RecipeDatabase.Recipe> visibleRecipes() {
        List<RecipeDatabase.Recipe> out = new ArrayList<>();
        String query = this.search.getValue().trim().toLowerCase();
        for (RecipeDatabase.Recipe recipe : RecipeDatabase.displayRecipes()) {
            if (!recipe.enabled()) continue;
            if (!categoryFilter.equals("ALL") && !recipe.category().equals(categoryFilter)) continue;
            if (!query.isEmpty()) {
                String name = recipe.name().toLowerCase();
                String id = recipe.id().toLowerCase();
                if (!name.contains(query) && !id.contains(query)) continue;
            }
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

    private void cycleCategory() {
        List<String> ids = new ArrayList<>();
        ids.add("ALL");
        for (RecipeDatabase.Category category : RecipeDatabase.displayCategories()) {
            ids.add(category.id());
        }
        int current = ids.indexOf(this.categoryFilter);
        this.categoryFilter = ids.get((current + 1) % ids.size());
        this.page = 0;
        this.pageFade = 0f;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (this.pageFade < 1f) {
            this.pageFade = Math.min(1f, this.pageFade + 0.18f);
        }
        g.fill(0, 0, this.width, this.height, 0xC0060504);

        g.pose().pushPose();
        g.pose().translate(this.width / 2f, this.height / 2f, 0f);
        g.pose().scale(this.uiScale, this.uiScale, 1f);
        g.pose().translate(-this.width / 2f, -this.height / 2f, 0f);

        int mx = Math.round(uiX(mouseX));
        int my = Math.round(uiY(mouseY));

        drawPanel(g, mx, my);
        super.render(g, mx, my, partialTick);

        renderGrid(g, mx, my);
        if (selectedId != null) {
            renderDetail(g, mx, my);
        }

        renderNav(g, mx, my);

        g.pose().popPose();
    }

    private void drawPanel(GuiGraphics g, int mouseX, int mouseY) {
        int x = this.panelX, y = this.panelY;
        g.fill(x, y, x + panelW, y + panelH, PANEL_BG);
        g.renderOutline(x, y, panelW, panelH, PANEL_OUTER);
        g.renderOutline(x + 1, y + 1, panelW - 2, panelH - 2, PANEL_INNER);
        g.renderOutline(x + 2, y + 2, panelW - 4, panelH - 4, 0xFF303030);

        g.drawString(this.font, "CATÁLOGO DE CRAFTEO", x + 20, y + 14, CYAN, false);
        g.drawString(this.font, "Cat.:", x + 200, y + 40, TEXT_DIM, false);

        // Botón de categoría (clic para ciclar)
        int catX = x + 240, catW = 130, catY = y + 36;
        g.fill(catX, catY, catX + catW, catY + 18, 0xFF181818);
        g.renderOutline(catX, catY, catW, 18, isHovering(catX, catY, catW, 18, mouseX, mouseY) ? CYAN : SLOT_LINE);
        g.drawString(this.font, this.font.plainSubstrByWidth(categoryFilter.equals("ALL") ? "TODAS" : categoryName(categoryFilter), catW - 8),
                catX + 4, catY + 5, CYAN, false);
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        List<RecipeDatabase.Recipe> visible = visibleRecipes();
        int totalPages = Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Math.max(0, Math.min(this.page, totalPages - 1));
        int start = this.page * PAGE_SIZE;

        int gridX = this.panelX + 20;
        int gridY = this.panelY + 64;

        if (visible.isEmpty()) {
            g.drawString(this.font, "No hay recetas registradas en chernobylinv-recipes.json",
                    gridX, gridY + 40, TEXT_DIM, false);
            g.drawString(this.font, "El owner debe añadir las recetas allí.",
                    gridX, gridY + 54, TEXT_DIM, false);
            return;
        }

        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = start + i;
            if (index >= visible.size()) break;
            RecipeDatabase.Recipe recipe = visible.get(index);
            int col = i % COLS, row = i / COLS;
            int cellX = gridX + col * (CELL + 4);
            int cellY = gridY + row * (CELL + 4);

            boolean hover = isHovering(cellX, cellY, CELL, CELL, mouseX, mouseY);
            boolean selected = recipe.id().equals(selectedId);
            int alpha = (int) (0xFF * this.pageFade) << 24 | 0xFFFFFF;

            if (this.pageFade >= 0.99f) alpha = 0xFFFFFFFF;
            g.fill(cellX, cellY, cellX + CELL, cellY + CELL,
                    selected ? 0xFF263A3A : (hover ? 0xFF222A2A : SLOT_BG));
            g.renderOutline(cellX, cellY, CELL, CELL, selected ? CYAN : (hover ? HOVER_LINE : SLOT_LINE));

            ItemStack output = new ItemStack(recipe.outputItem(), recipe.outputCount());
            g.renderItem(output, cellX + (CELL - 16) / 2, cellY + 4);
            g.renderItemDecorations(this.font, output, cellX + (CELL - 16) / 2, cellY + 4);
            g.drawString(this.font, this.font.plainSubstrByWidth(recipe.name(), CELL - 4),
                    cellX + 2, cellY + CELL - 12, selected ? CYAN : TEXT, false);
        }
    }

    private void renderDetail(GuiGraphics g, int mouseX, int mouseY) {
        RecipeDatabase.Recipe recipe = RecipeDatabase.displayById(selectedId);
        if (recipe == null) return;

        int px = this.panelX + 320;
        int py = this.panelY + 64;

        ItemStack output = new ItemStack(recipe.outputItem(), recipe.outputCount() * craftUnits);
        g.renderItem(output, px, py);
        g.renderItemDecorations(this.font, output, px, py);
        g.drawString(this.font, this.font.plainSubstrByWidth(recipe.name() + " x" + craftUnits, 250),
                px + 22, py + 2, GREEN, false);

        int descY = py + 24;
        for (String lineText : CraftingRenderer.wrap(this.font, recipe.description(), 250)) {
            g.drawString(this.font, lineText, px, descY, TEXT_DIM, false);
            descY += 10;
        }

        g.drawString(this.font, "Tiempo: " + recipe.craftingTimeSec() + "s c/u | x" + craftUnits,
                px, descY + 2, CYAN, false);

        int line = descY + 16;
        for (RecipeDatabase.Recipe.Ingredient ingredient : recipe.ingredients()) {
            int need = ingredient.amount() * craftUnits;
            int have = CraftingRenderer.countHeld(this.minecraft.player, ingredient.item());
            int color = have >= need ? GREEN : RED;
            g.drawString(this.font, this.font.plainSubstrByWidth(
                            ingredient.item().getDescription().getString() + ": " + have + " / " + need, 250),
                    px, line, color, false);
            line += 11;
        }

        // Selector de cantidad
        int qtyY = line + 4;
        g.drawString(this.font, "Cant:", px, qtyY + 3, TEXT, false);
        drawShopButton(g, px + 44, qtyY, 22, 16, "-", mouseX, mouseY);
        g.drawString(this.font, String.valueOf(craftUnits), px + 70, qtyY + 3, TEXT, false);
        drawShopButton(g, px + 92, qtyY, 22, 16, "+", mouseX, mouseY);

        // Botón FABRICAR
        boolean canCraft = CraftingRenderer.hasMaterials(this.minecraft.player, recipe, craftUnits);
        int buttonX = px, buttonY = qtyY + 26, buttonW = 250, buttonH = 20;
        g.fill(buttonX, buttonY, buttonX + buttonW, buttonY + buttonH,
                canCraft ? 0xFF263A3A : 0xFF181818);
        g.renderOutline(buttonX, buttonY, buttonW, buttonH, canCraft ? CYAN : SLOT_LINE);
        String button = canCraft ? "FABRICAR" : "FALTAN MATERIALES";
        g.drawString(this.font, button, buttonX + (buttonW - this.font.width(button)) / 2,
                buttonY + 6, canCraft ? CYAN : TEXT_DIM, false);

        // Botón volver al catálogo
        int backY = buttonY + 26;
        g.drawString(this.font, "< Volver al catálogo", px, backY, TEXT_DIM, false);
    }

    private void renderNav(GuiGraphics g, int mouseX, int mouseY) {
        int y = this.panelY + panelH - 48;
        // Botón volver
        boolean hover = isHovering(this.panelX + 20, y, 90, 20, mouseX, mouseY);
        g.fill(this.panelX + 20, y, this.panelX + 110, y + 20, hover ? 0xFF2A2A2A : 0xFF181818);
        g.renderOutline(this.panelX + 20, y, 90, 20, hover ? CYAN : SLOT_LINE);
        g.drawString(this.font, "Volver", this.panelX + 20 + (90 - this.font.width("Volver")) / 2,
                y + 6, hover ? CYAN : TEXT, false);

        // Paginación
        List<RecipeDatabase.Recipe> visible = visibleRecipes();
        int totalPages = Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int navX = this.panelX + panelW - 80;
        drawShopButton(g, navX, y, 18, 20, "<", mouseX, mouseY);
        g.drawString(this.font, String.valueOf(this.page + 1), navX + 24, y + 6, TEXT_DIM, false);
        drawShopButton(g, navX + 42, y, 18, 20, ">", mouseX, mouseY);
    }

    private void drawShopButton(GuiGraphics g, int bx, int by, int bw, int bh, String label,
                                int mouseX, int mouseY) {
        boolean hover = isHovering(bx, by, bw, bh, mouseX, mouseY);
        g.fill(bx, by, bx + bw, by + bh, hover ? 0xFF2A2A2A : 0xFF181818);
        g.renderOutline(bx, by, bw, bh, hover ? HOVER_LINE : SLOT_LINE);
        g.drawString(this.font, label, bx + (bw - this.font.width(label)) / 2, by + 4, TEXT, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double ux = uiX(mouseX), uy = uiY(mouseY);
        if (button != 0) return super.mouseClicked(ux, uy, button);

        int x = this.panelX, y = this.panelY;

        // Botón volver
        if (isHovering(x + 20, y + panelH - 48, 90, 20, ux, uy)) {
            this.onClose();
            return true;
        }

        // Filtro de categoría
        if (isHovering(x + 240, y + 36, 130, 18, ux, uy)) {
            cycleCategory();
            return true;
        }

        // Paginación
        List<RecipeDatabase.Recipe> visible = visibleRecipes();
        int totalPages = Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int navX = x + panelW - 80;
        if (isHovering(navX, y + panelH - 48, 18, 20, ux, uy) && this.page > 0) {
            this.page--;
            this.pageFade = 0f;
            return true;
        }
        if (isHovering(navX + 42, y + panelH - 48, 18, 20, ux, uy) && this.page < totalPages - 1) {
            this.page++;
            this.pageFade = 0f;
            return true;
        }

        // Grid: si el detalle está abierto, selecciona otra receta al instante
        int gridX = this.panelX + 20;
        int gridY = this.panelY + 64;
        int start = this.page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = start + i;
            if (index >= visible.size()) break;
            int col = i % COLS, row = i / COLS;
            int cellX = gridX + col * (CELL + 4);
            int cellY = gridY + row * (CELL + 4);
            if (isHovering(cellX, cellY, CELL, CELL, ux, uy)) {
                this.selectedId = visible.get(index).id();
                this.craftUnits = 1;
                return true;
            }
        }

        // Clic en "Volver al catálogo"
        if (selectedId != null) {
            int px = this.panelX + 320;
            int py = this.panelY + 64;
            int qtyAbsY = py + 24 + CraftingRenderer.wrap(this.font, RecipeDatabase.displayById(selectedId).description(), 250).size() * 10 + 14 + RecipeDatabase.displayById(selectedId).ingredients().size() * 11 + 4;
            int backY = qtyAbsY + 52;
            if (isHovering(px, backY, 250, 14, ux, uy)) {
                this.selectedId = null;
                return true;
            }

            RecipeDatabase.Recipe recipe = RecipeDatabase.displayById(selectedId);
            if (recipe == null) {
                this.selectedId = null;
                return true;
            }
            if (isHovering(px + 44, qtyAbsY, 22, 16, ux, uy)) {
                this.craftUnits = Math.max(1, this.craftUnits - 1);
                return true;
            }
            if (isHovering(px + 92, qtyAbsY, 22, 16, ux, uy)) {
                this.craftUnits = Math.min(recipe.maxCraftAmount(), this.craftUnits + 1);
                return true;
            }
            int buttonY = qtyAbsY + 26;
            if (isHovering(px, buttonY, 250, 20, ux, uy) && CraftingRenderer.hasMaterials(this.minecraft.player, recipe, craftUnits)) {
                Net.CHANNEL.sendToServer(new CraftEnqueuePacket(recipe.id(), this.craftUnits));
                return true;
            }

            // Clic fuera del detalle vuelve a la cuadrícula
            if (!isHovering(px, this.panelY + 64, 250, 200, ux, uy)) {
                this.selectedId = null;
                return true;
            }
            return super.mouseClicked(ux, uy, button);
        }

        return super.mouseClicked(ux, uy, button);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<RecipeDatabase.Recipe> visible = visibleRecipes();
        int totalPages = Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (delta > 0 && this.page > 0) {
            this.page--;
            return true;
        }
        if (delta < 0 && this.page < totalPages - 1) {
            this.page++;
            return true;
        }
        return super.mouseScrolled(uiX(mouseX), uiY(mouseY), delta);
    }
}
