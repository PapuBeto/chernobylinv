package com.chernobyl.inv;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.Locale;

public class HudLayout extends SimplePreparableReloadListener<JsonObject> {
    public static final ResourceLocation FILE = new ResourceLocation(ChernobylInv.MODID, "hud.json");

    // Orden de iconos: 0 vida, 1 sed, 2 comida, 3 energia, 4 radiacion
    public static final ResourceLocation[] TEX = {
            new ResourceLocation(ChernobylInv.MODID, "textures/gui/hud/health.png"),
            new ResourceLocation(ChernobylInv.MODID, "textures/gui/hud/thirst.png"),
            new ResourceLocation(ChernobylInv.MODID, "textures/gui/hud/food.png"),
            new ResourceLocation(ChernobylInv.MODID, "textures/gui/hud/energy.png"),
            new ResourceLocation(ChernobylInv.MODID, "textures/gui/hud/radiation.png")
    };
    public static final boolean[] hasTexture = new boolean[TEX.length];

    public static String mode = "auto";       // auto | center | corner
    public static boolean compact = false;    // centro: sin panel, solo ruedas
    public static String anchorX = "right";   // left | center | right
    public static String anchorY = "bottom";  // top | middle | bottom
    public static int offsetX = -8;
    public static int offsetY = -8;
    public static int pitch = 26;
    public static int iconSize = 11;
    public static boolean showNumbers = false;
    public static boolean tint = true;
    public static boolean frame = true;

    @Override
    protected JsonObject prepare(ResourceManager rm, ProfilerFiller profiler) {
        try (Reader r = rm.getResourceOrThrow(FILE).openAsReader()) {
            return JsonParser.parseReader(r).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    @Override
    protected void apply(JsonObject o, ResourceManager rm, ProfilerFiller profiler) {
        mode = GsonHelper.getAsString(o, "mode", "auto").toLowerCase(Locale.ROOT);
        iconSize = Math.max(8, GsonHelper.getAsInt(o, "icon_size", 11));
        showNumbers = GsonHelper.getAsBoolean(o, "show_numbers", false);
        tint = GsonHelper.getAsBoolean(o, "tint", true);
        pitch = GsonHelper.getAsInt(o, "pitch", 26);

        if (isCenterMode(mode)) {
            centerPreset();
        } else if (isCornerMode(mode)) {
            cornerPreset();
        } else {
            // auto / configs viejas: posición y marco desde el JSON.
            anchorX = GsonHelper.getAsString(o, "anchor_x", "right");
            anchorY = GsonHelper.getAsString(o, "anchor_y", "bottom");
            offsetX = GsonHelper.getAsInt(o, "offset_x", -8);
            offsetY = GsonHelper.getAsInt(o, "offset_y", -8);
            frame = GsonHelper.getAsBoolean(o, "frame", true);
            compact = "center".equals(anchorX);
            if (compact) frame = false;
        }

        // La elección hecha desde el menú de pausa tiene prioridad sobre el JSON.
        try {
            String saved = HudSettings.mode();
            if (saved != null) {
                setMode(saved);
            }
        } catch (Throwable ignored) {
            // Si el archivo de preferencias no está disponible, manda el JSON.
        }

        for (int i = 0; i < TEX.length; i++) {
            hasTexture[i] = rm.getResource(TEX[i]).isPresent();
        }
    }

    /** Aplica el preset y deja el estilo listo para dibujar en el mismo frame. */
    public static void setMode(String newMode) {
        mode = newMode == null ? "auto" : newMode.toLowerCase(Locale.ROOT);
        if (isCenterMode(mode)) {
            centerPreset();
        } else if (isCornerMode(mode)) {
            cornerPreset();
        }
    }

    private static boolean isCenterMode(String value) {
        return value.equals("center") || value.equals("compact") || value.equals("minimal");
    }

    private static boolean isCornerMode(String value) {
        return value.equals("corner") || value.equals("panel") || value.equals("full");
    }

    private static void centerPreset() {
        // Modo centro: compacto, sin fondo, solo las ruedas.
        compact = true;
        anchorX = "center";
        anchorY = "bottom";
        offsetX = 0;
        offsetY = -36;
        frame = false;
    }

    private static void cornerPreset() {
        // Modo esquina: panel completo y ruedas más grandes, anclado a la
        // izquierda y abajo, en el mismo sitio que antes.
        compact = false;
        anchorX = "left";
        anchorY = "bottom";
        offsetX = 8;
        offsetY = -8;
        frame = true;
    }
}