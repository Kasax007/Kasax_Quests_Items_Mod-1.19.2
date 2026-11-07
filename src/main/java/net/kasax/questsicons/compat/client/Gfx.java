package net.kasax.questsicons.compat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Textured quads with the argument order of 1.21.6+ ({@code blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, ...)}),
 * drawn the 1.21.1 way: blending on and the tint as shader colour.
 */
public final class Gfx {
    private Gfx() {
    }

    public static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, float u, float v, int w, int h, int texW, int texH) {
        blit(g, tex, x, y, u, v, w, h, w, h, texW, texH, 0xFFFFFFFF);
    }

    public static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, float u, float v, int w, int h, int texW, int texH, int argb) {
        blit(g, tex, x, y, u, v, w, h, w, h, texW, texH, argb);
    }

    public static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, float u, float v, int w, int h, int srcW, int srcH,
                            int texW, int texH) {
        blit(g, tex, x, y, u, v, w, h, srcW, srcH, texW, texH, 0xFFFFFFFF);
    }

    public static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, float u, float v, int w, int h, int srcW, int srcH,
                            int texW, int texH, int argb) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
        g.blit(tex, x, y, w, h, u, v, srcW, srcH, texW, texH);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
