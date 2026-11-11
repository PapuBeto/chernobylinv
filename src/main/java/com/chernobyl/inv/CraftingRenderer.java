package com.chernobyl.inv;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilidades de renderizado compartidas entre el inventario y el catálogo.
 * La UI solo representa datos que llegan de {@link RecipeDatabase} y
 * {@link ClientCraftingCache}; la lógica de fabricación vive en el servidor.
 */
public final class CraftingRenderer {
    private CraftingRenderer() {}

    // Paleta industrial: gris carbon, bordes claros, cian y verde
    public static final int ZONE_BG = 0xFF1D1D1D;
    public static final int PANEL_OUTER = 0xFF060606;
    public static final int PANEL_INNER = 0xFF8C8C8C;
    public static final int PANEL_LINE = 0xFF4A4A4A;
    public static final int SLOT_BG = 0xFF141414;
    public static final int SLOT_LINE = 0xFF555555;
    public static final int BELT_LINE = 0xFF6FD6D6;
    public static final int BELT_IDLE = 0xFF3F6F6F;
    public static final int HOVER_LINE = 0xFFD8D8D8;
    public static final int TEXT = 0xFFE6E6E6;
    public static final int TEXT_DIM = 0xFF8A8A8A;
    public static final int TITLE = 0xFFE8F6F6;
    public static final int CYAN = 0xFF7FE3E3;
    public static final int GREEN = 0xFF8FE08F;
    public static final int RED = 0xFFFF5555;
    public static final int BAR_BG = 0xFF0A0A0A;

    public static void zone(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fill(x1, y1, x2, y2, ZONE_BG);
        g.renderOutline(x1, y1, x2 - x1, y2 - y1, PANEL_OUTER);
        g.renderOutline(x1 + 1, y1 + 1, x2 - x1 - 2, y2 - y1 - 2, PANEL_INNER);
        g.renderOutline(x1 + 2, y1 + 2, x2 - x1 - 4, y2 - y1 - 4, 0xFF303030);
    }

    public static void slotBox(GuiGraphics g, int x, int y, int line, boolean hover) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BG);
        g.fill(x, y, x + 16, y + 1, 0xFF0A0A0A);
        g.fill(x, y, x + 1, y + 16, 0xFF0A0A0A);
        g.renderOutline(x - 1, y - 1, 18, 18, hover ? HOVER_LINE : line);
    }

    public static int alpha(int argb, float f) {
        int a = (int) (((argb >>> 24) & 255) * Math.max(0f, Math.min(1f, f)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static List<String> wrap(Font font, String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null) return lines;
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (font.width(candidate) <= width || current.length() == 0) {
                current = new StringBuilder(candidate);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    public static String ingredientSummary(RecipeDatabase.Recipe recipe) {
        StringBuilder text = new StringBuilder();
        for (RecipeDatabase.Recipe.Ingredient ingredient : recipe.ingredients()) {
            if (text.length() > 0) text.append("  ");
            text.append(ingredient.amount()).append("x ").append(ingredient.item().getDescription().getString());
        }
        return text.toString();
    }

    public static int countHeld(Player player, Item item) {
        if (player == null || item == null) return 0;
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == item) total += stack.getCount();
        }
        return total;
    }

    public static boolean hasMaterials(Player player, RecipeDatabase.Recipe recipe, int units) {
        if (recipe == null) return false;
        for (RecipeDatabase.Recipe.Ingredient ingredient : recipe.ingredients()) {
            if (countHeld(player, ingredient.item()) < ingredient.amount() * units) return false;
        }
        return true;
    }

    public static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
