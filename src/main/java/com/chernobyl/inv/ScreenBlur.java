package com.chernobyl.inv;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.client.event.ScreenEvent;
import org.lwjgl.opengl.GL13;

/**
 * Aplica un desenfoque gaussiano al fondo del mundo cuando InvScreen esta abierto.
 *
 * Estrategia: captura el color del framebuffer principal antes de que la pantalla
 * se dibuje (ScreenEvent.Render.Pre) y lo renderiza desenfocado en un quad de fondo.
 *
 * Como no confiamos en GameRenderer.loadEffect() (fragil en 1.20.1 Forge),
 * hacemos un blur software: dibujamos el framebuffer varias veces con offsets
 * y opacidad baja → result visualmente identico a un blur gaussiano.
 */
public class ScreenBlur {

    // Sin pasadas de blur: solo se oscurece el fondo (ver drawBlurredBackground).
    private static final int PASSES = 0;
    private static final float STRENGTH = 0.0f;
    private static final float ALPHA_PER_PASS = 0.0f;

    // Oscurecimiento del fondo al abrir el inventario (antiguo "blur").
    private static final int OVERLAY = 0xC0060504;


    /** Llama esto desde ScreenEvent.Render.Pre cuando la pantalla es InvScreen. */
    public static void drawBlurredBackground(ScreenEvent.Render.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;   // fuera del mundo → no hacemos nada

        RenderTarget main = mc.getMainRenderTarget();
        int texId = main.getColorTextureId();
        int w = event.getScreen().width;
        int h = event.getScreen().height;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();

        // Primero un fondo totalmente opaco con el frame sin desenfoque para evitar
        // que se transparente con el negro de OpenGL.
        drawQuad(texId, w, h, 0, 0, 1.0f);

        // Luego las pasadas desplazadas con alpha bajo → efecto blur acumulado
        for (int i = 0; i < PASSES; i++) {
            double angle = (Math.PI * 2.0 * i) / PASSES;
            float dx = (float)(Math.cos(angle) * STRENGTH);
            float dy = (float)(Math.sin(angle) * STRENGTH);
            drawQuad(texId, w, h, dx, dy, ALPHA_PER_PASS);
        }

        // Overlay oscuro encima del blur: oscurece el fondo sin taparlo del todo
        // para que los paneles contrasten bien con el mundo desenfocado
        fillRect(w, h, OVERLAY);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // -------------------------------------------------------------------------

    private static void drawQuad(int texId, int screenW, int screenH,
                                 float offsetX, float offsetY, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);

        // Enlaza la textura del framebuffer principal directamente
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(texId);
        // Flip Y porque el framebuffer de GL esta invertido respecto a la pantalla
        // U/V: movemos las coordenadas de textura para simular el desplazamiento
        float u0 = -offsetX / screenW;
        float u1 =  1f - offsetX / screenW;
        float v0 =  1f + offsetY / screenH;   // invertido
        float v1 =       offsetY / screenH;

        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(0,       screenH, 0).uv(u0, v0).color(1f,1f,1f, alpha).endVertex();
        buf.vertex(screenW, screenH, 0).uv(u1, v0).color(1f,1f,1f, alpha).endVertex();
        buf.vertex(screenW, 0,       0).uv(u1, v1).color(1f,1f,1f, alpha).endVertex();
        buf.vertex(0,       0,       0).uv(u0, v1).color(1f,1f,1f, alpha).endVertex();
        Tesselator.getInstance().end();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Relleno de color solido (sin textura) sobre toda la pantalla. */
    private static void fillRect(int w, int h, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >>  8) & 0xFF) / 255f;
        float b = ( argb        & 0xFF) / 255f;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(0, h, 0).color(r, g, b, a).endVertex();
        buf.vertex(w, h, 0).color(r, g, b, a).endVertex();
        buf.vertex(w, 0, 0).color(r, g, b, a).endVertex();
        buf.vertex(0, 0, 0).color(r, g, b, a).endVertex();
        Tesselator.getInstance().end();
    }
}
