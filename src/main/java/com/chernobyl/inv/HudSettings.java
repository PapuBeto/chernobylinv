package com.chernobyl.inv;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Preferencia del HUD elegida desde el juego (menú de pausa).
 * Vive en config/chernobylinv-hud.properties y tiene prioridad sobre hud.json.
 */
public final class HudSettings {
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("chernobylinv-hud.properties");

    private static String mode = null;
    private static boolean loaded = false;

    private HudSettings() {
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;

        if (!Files.exists(FILE)) return;

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            props.load(in);
            String value = props.getProperty("mode", "").trim().toLowerCase(Locale.ROOT);
            if (value.equals("center") || value.equals("corner")) {
                mode = value;
            }
        } catch (Exception ignored) {
            // Si el archivo está roto, se usa el hud.json.
        }
    }

    /** null = todavía no hay preferencia guardada; manda el hud.json. */
    public static synchronized String mode() {
        load();
        return mode;
    }

    public static synchronized void setMode(String newMode) {
        load();

        String value = newMode == null ? "center" : newMode.toLowerCase(Locale.ROOT);
        if (!value.equals("center") && !value.equals("corner")) return;

        mode = value;
        Properties props = new Properties();
        props.setProperty("mode", value);

        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                props.store(out, "ChernobylInv - HUD");
            }
        } catch (Exception ignored) {
            // Aunque no se pueda guardar, el cambio se aplica en esta sesión.
        }
    }
}
