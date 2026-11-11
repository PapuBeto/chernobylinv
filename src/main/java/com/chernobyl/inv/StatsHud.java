package com.chernobyl.inv;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

public class StatsHud {
    // ─── Paleta industrial (la misma que InvScreen) ────────────────
    private static final int ZONE_BG = 0xFF1B1B1B;
    private static final int PANEL_OUTER = 0xFF060606;
    private static final int PANEL_INNER = 0xFF8C8C8C;
    private static final int SLOT_BG = 0xFF141414;
    private static final int SLOT_LINE = 0xFF5A5A5A;
    private static final int TRACK = 0x555A5A5A;
    private static final int TEXT = 0xFFE6E6E6;

    private static final int COL_HEALTH = 0xFF8FE08F;
    private static final int COL_WATER = 0xFF6FD6D6;
    private static final int COL_FOOD = 0xFFE8D06A;
    private static final int COL_STAMINA = 0xFFC8C8C8;
    private static final int COL_RAD_LOW = 0xFF8FE08F;
    private static final int COL_RAD_HIGH = 0xFFE8D06A;
    private static final int COL_WARN = 0xFFFFA04A;
    private static final int COL_RED = 0xFFFF5555;

    private static final boolean THIRST = ModList.get().isLoaded("thirst");
    private static final boolean PARCOOL = ModList.get().isLoaded("parcool");
    private static final boolean RADIOACTIVE = ModList.get().isLoaded("radioactive");

    private static final String[] CROSS = {
            "....XXX....", "....XXX....", "....XXX....", "....XXX....",
            "XXXXXXXXXXX", "XXXXXXXXXXX", "XXXXXXXXXXX",
            "....XXX....", "....XXX....", "....XXX....", "....XXX...."};

    private static final String[] DROP = {
            ".....X.....", ".....X.....", "....XXX....", "....XXX....",
            "...XXXXX...", "..XXXXXXX..", "..XXXXXXX..",
            ".XXXXXXXXX.", ".XXXXXXXXX.", "..XXXXXXX..", "...XXXXX..."};

    private static final String[] FORK = {
            "X.X.X...XX.", "X.X.X...XX.", "X.X.X...XX.", "X.X.X...XX.",
            "XXXXX...XX.", "..X.....XX.", "..X.....XX.",
            "..X.....X..", "..X.....X..", "..X.....X..", "..X.....X.."};

    private static final String[] BOLT = {
            "......XXX..", ".....XXX...", "....XXX....", "...XXXXXX..",
            "...XXXXXX..", ".....XXX...", "....XXX....",
            "...XXX.....", "..XXX......", "..XX.......", "..X........"};

    private static final String[] RADIATION = trefoil();

    // inverse = true: cuanto más alto, peor (radiación)
    private record Stat(int id, String[] icon, int value, int max, boolean inverse) {}

    private static boolean logged = false;

    private static void logOnce(Throwable t) {
        if (logged) return;
        logged = true;
        System.err.println("[ChernobylInv] fallo el HUD de stats:");
        t.printStackTrace();
    }

    public static final IGuiOverlay OVERLAY = (gui, g, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (mc.screen instanceof InvScreen) return;
        if (p == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer() || p.isSpectator()) return;
        if (mc.options.hideGui) return;

        List<Stat> stats = new ArrayList<>();
        stats.add(new Stat(0, CROSS, (int) Math.ceil(p.getHealth()), (int) Math.ceil(p.getMaxHealth()), false));

        if (THIRST) {
            int t = ThirstCompat.value(p);
            if (t >= 0) stats.add(new Stat(1, DROP, t, 20, false));
        }

        stats.add(new Stat(2, FORK, p.getFoodData().getFoodLevel(), 20, false));

        if (PARCOOL) {
            int[] s = ParCoolCompat.stamina(p);
            if (s != null && s[1] > 0) stats.add(new Stat(3, BOLT, s[0], s[1], false));
        }

        boolean hasGeiger = false;
        int[] rad = null;
        if (RADIOACTIVE) {
            try {
                hasGeiger = RadCompat.hasGeiger(p);
                if (hasGeiger) rad = RadCompat.radiation(p);
            } catch (Throwable t) {
                logOnce(t);
            }
        }

        // La rueda de radiación solo existe si llevás geiger.
        // Si todavía no llegó el dato del servidor, queda vacía pero visible.
        if (hasGeiger) {
            int value = rad == null ? 0 : rad[0];
            int max = rad == null || rad[1] <= 0 ? 100 : rad[1];
            stats.add(new Stat(4, RADIATION, value, max, true));
        }

        // Dos presets:
        //  - centro: ruedas chicas, sin panel ni zócalos, solo los iconos.
        //  - esquina: panel completo con ruedas más grandes.
        boolean compact = HudLayout.compact;
        int icon = iconSize(compact);
        int cell = cellSize(compact);
        int pad = pad(compact);
        int gap = gap(compact);
        int count = stats.size();
        int w = count * cell + (count - 1) * gap + pad * 2;
        int h = cell + pad * 2;

        // Los offsets son márgenes desde el borde anclado (en píxeles de GUI)
        int x = switch (HudLayout.anchorX) {
            case "left" -> HudLayout.offsetX;
            case "center" -> (width - w) / 2 + HudLayout.offsetX;
            default -> width - w + HudLayout.offsetX;
        };
        int y = switch (HudLayout.anchorY) {
            case "top" -> HudLayout.offsetY;
            case "middle" -> (height - h) / 2 + HudLayout.offsetY;
            default -> height - h + HudLayout.offsetY;
        };

        // Indicador flotante de la orden activa: aparece arriba del HUD de estado.
        if (!ClientCraftingCache.entries().isEmpty()) {
            renderCraftingIndicator(g, width, height, HudLayout.compact);
        }

        draw(g, stats, icon, cell, pad, gap, compact, HudLayout.frame, w, h, x, y, System.currentTimeMillis());
    };

    private static void renderCraftingIndicator(GuiGraphics g, int width, int height, boolean compact) {
        ClientCraftingCache.Entry entry = ClientCraftingCache.entries().get(0);
        RecipeDatabase.Recipe recipe = RecipeDatabase.displayById(entry.recipeId);
        if (recipe == null) return;

        var font = Minecraft.getInstance().font;
        int panelW = 96, panelH = 24;
        int x = compact ? (width - panelW) / 2 : width - panelW - 8;
        int y = compact ? height - 44 : height - 52;

        g.fill(x, y, x + panelW, y + panelH, 0xC01B1B1B);
        g.renderOutline(x, y, panelW, panelH, 0xFF6FD6D6);

        net.minecraft.world.item.ItemStack output = new net.minecraft.world.item.ItemStack(recipe.outputItem(), recipe.outputCount());
        g.renderItem(output, x + 4, y + 4);
        g.renderItemDecorations(font, output, x + 4, y + 4);

        float progress = entry.progress();
        int barX = x + 22, barY = y + 7, barW = panelW - 28;
        g.fill(barX, barY, barX + barW, barY + 3, 0xFF0A0A0A);
        g.fill(barX, barY, barX + Math.round(barW * progress), barY + 3, 0xFF7FE3E3);

        int secs = Math.max(0, entry.remainingTicks) / 20;
        g.drawString(font, secs + "s", x + 22, y + 12, 0xFFE6E6E6, false);
    }

    private static int iconSize(boolean compact) {
        // 11x11 es el tamaño nativo de las mascaras: en modo centro queda 1:1 y nitido.
        // En esquina se agranda a 14 px, pero con una celda que evita que toque el anillo.
        return compact
                ? Math.max(10, Math.min(12, HudLayout.iconSize))
                : 14;
    }

    private static int cellSize(boolean compact) {
        // La celda de 28 px deja 9 px libres dentro del anillo de 2 px,
        // suficiente para que las esquinas del icono de 11/12 px no lo toquen.
        return compact ? 28 : 30;
    }

    private static int pad(boolean compact) {
        return compact ? 0 : 2;
    }

    private static int gap(boolean compact) {
        // Un solo pixel tambien en el panel: deja mas aire respecto a la hotbar
        // cuando la ventana usa GUI Scale 4.
        return 1;
    }

    /** Tamaño en píxeles de GUI que ocupa la vista previa de cada modo. */
    public static int[] previewSize(boolean compact) {
        int cell = cellSize(compact);
        int pad = pad(compact);
        int gap = gap(compact);
        int count = 5;
        return new int[]{
                count * cell + (count - 1) * gap + pad * 2,
                cell + pad * 2
        };
    }

    /** Dibuja una muestra de las 5 ruedas, sin depender del jugador ni de los mods. */
    public static void renderPreview(GuiGraphics g, int x, int y, boolean compact) {
        List<Stat> sample = List.of(
                new Stat(0, CROSS, 16, 20, false),
                new Stat(1, DROP, 12, 20, false),
                new Stat(2, FORK, 9, 20, false),
                new Stat(3, BOLT, 18, 20, false),
                new Stat(4, RADIATION, 25, 100, true)
        );

        int icon = iconSize(compact);
        int cell = cellSize(compact);
        int pad = pad(compact);
        int gap = gap(compact);
        int[] size = previewSize(compact);
        draw(g, sample, icon, cell, pad, gap, compact, !compact, size[0], size[1],
                x, y, System.currentTimeMillis());
    }

    private static void draw(GuiGraphics g, List<Stat> stats, int icon, int cell,
                             int pad, int gap, boolean compact, boolean frame,
                             int w, int h, int x, int y, long now) {
        if (frame) {
            g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x44000000);
            frame(g, x, y, w, h);
        }

        int r = cell / 2 - 3;

        for (int i = 0; i < stats.size(); i++) {
            Stat s = stats.get(i);
            int cx = x + pad + i * (cell + gap) + cell / 2;
            int cy = y + pad + cell / 2;

            float ratio = ratio(s);
            float danger = s.inverse() ? ratio : 1f - ratio;
            boolean severe = danger >= 0.9f;
            float pulse = 1f;
            if (danger >= 0.75f) {
                pulse = 0.65f + 0.35f * (float) Math.sin(now * (severe ? 0.016 : 0.006));
                if (severe && (now / 70) % 9 == 0) pulse *= 0.6f;
            }

            int ring = ringColor(s.id(), danger);
            int iconColor = severe ? COL_RED : TEXT;

            if (compact) {
                // Modo centro: sin panel ni zócalo, solo la rueda.
                // Contorno de 1 px del propio icono (no es un fondo).
                int ix = cx - icon / 2;
                int iy = cy - icon / 2;
                maskScaled(g, s.icon(), ix - 1, iy, icon, 0xCC000000);
                maskScaled(g, s.icon(), ix + 1, iy, icon, 0xCC000000);
                maskScaled(g, s.icon(), ix, iy - 1, icon, 0xCC000000);
                maskScaled(g, s.icon(), ix, iy + 1, icon, 0xCC000000);

                ring(g, cx, cy, r, 2, TRACK);
            } else {
                // Modo esquina: zócalo circular como un slot del inventario
                disc(g, cx + 1, cy + 1, r + 2, 0x33000000);
                disc(g, cx, cy, r + 2, SLOT_LINE);
                disc(g, cx, cy, r + 1, SLOT_BG);

                // Pista oscura de 2 px
                disc(g, cx, cy, r, TRACK);
                disc(g, cx, cy, r - 2, SLOT_BG);
            }

            // Progreso desde las 12 en punto, en sentido horario
            if (ratio > 0.001f) {
                arc(g, cx, cy, r, -90f, 360f * ratio, alpha(ring, pulse), 2);
            }

            // Icono centrado
            maskScaled(g, s.icon(), cx - icon / 2, cy - icon / 2, icon, iconColor);
        }
    }

    // Panel: fondo carbon, borde claro y linea interior oscura (igual que InvScreen)
    private static void frame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, ZONE_BG);
        g.renderOutline(x, y, w, h, PANEL_OUTER);
        g.renderOutline(x + 1, y + 1, w - 2, h - 2, PANEL_INNER);
        g.renderOutline(x + 2, y + 2, w - 4, h - 4, 0xFF303030);
    }

    // Círculo relleno por filas
    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt((double) r * r - (double) dy * dy);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    // Anillo (solo la banda, sin relleno) para el modo compacto
    private static void ring(GuiGraphics g, int cx, int cy, int r, int thickness, int color) {
        int innerR = Math.max(0, r - thickness);
        for (int dy = -r; dy <= r; dy++) {
            int outer = (int) Math.sqrt((double) r * r - (double) dy * dy);
            if (Math.abs(dy) >= innerR) {
                g.fill(cx - outer, cy + dy, cx + outer + 1, cy + dy + 1, color);
            } else {
                int inner = (int) Math.sqrt((double) innerR * innerR - (double) dy * dy);
                g.fill(cx - outer, cy + dy, cx - inner, cy + dy + 1, color);
                g.fill(cx + inner + 1, cy + dy, cx + outer + 1, cy + dy + 1, color);
            }
        }
    }

    // Arco con grosor, muestreado en ángulo
    private static void arc(GuiGraphics g, int cx, int cy, int r,
                            float startDeg, float sweepDeg, int color, int thickness) {
        float circumference = (float) (Math.PI * 2 * r);
        int steps = Math.max(24, (int) (circumference * Math.abs(sweepDeg) / 360f * 1.6f));
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(startDeg + sweepDeg * i / steps);
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            for (int t = 0; t < thickness; t++) {
                int rr = r - t;
                int xx = cx + (int) Math.round(cos * rr);
                int yy = cy + (int) Math.round(sin * rr);
                g.fill(xx, yy, xx + 1, yy + 1, color);
            }
        }
    }

    private static float ratio(Stat s) {
        return s.max() <= 0 ? 0f : Math.max(0f, Math.min(1f, (float) s.value() / s.max()));
    }

    private static int ringColor(int id, float danger) {
        if (danger >= 0.9f) return COL_RED;
        if (danger >= 0.75f) return COL_WARN;
        if (id == 4) {
            return danger >= 0.5f ? COL_RAD_HIGH : COL_RAD_LOW;
        }
        return switch (id) {
            case 0 -> COL_HEALTH;
            case 1 -> COL_WATER;
            case 2 -> COL_FOOD;
            case 3 -> COL_STAMINA;
            default -> COL_FOOD;
        };
    }

    private static int alpha(int argb, float f) {
        int a = (int) (((argb >>> 24) & 255) * Math.max(0f, Math.min(1f, f)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    // Escala un icono de 11x11 al tamaño configurado
    private static void maskScaled(GuiGraphics g, String[] rows, int x, int y, int size, int color) {
        int srcH = rows.length;
        int srcW = rows[0].length();
        for (int oy = 0; oy < size; oy++) {
            int sy = oy * srcH / size;
            String row = rows[sy];
            int runStart = -1;
            for (int ox = 0; ox <= size; ox++) {
                boolean on = ox < size && row.charAt(ox * srcW / size) == 'X';
                if (on && runStart < 0) {
                    runStart = ox;
                } else if (!on && runStart >= 0) {
                    g.fill(x + runStart, y + oy, x + ox, y + oy + 1, color);
                    runStart = -1;
                }
            }
        }
    }

    // Trébol de radiación: tres aspas separadas por huecos y un centro
    private static String[] trefoil() {
        String[] rows = new String[11];
        for (int y = 0; y < 11; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < 11; x++) {
                double dx = x - 5, dy = 5 - y;
                double rr = Math.hypot(dx, dy);
                double a = Math.toDegrees(Math.atan2(dy, dx));
                if (a < 0) a += 360;
                boolean hub = rr <= 1.3;
                boolean blade = rr >= 2.2 && rr <= 5.4
                        && (inArc(a, 90) || inArc(a, 210) || inArc(a, 330));
                sb.append(hub || blade ? 'X' : '.');
            }
            rows[y] = sb.toString();
        }
        return rows;
    }

    private static boolean inArc(double a, double center) {
        return Math.abs(a - center) <= 28;
    }
}
