/*
 * Derived from Mekanism (https://github.com/mekanism/Mekanism, tag v1.21.1-10.7.19.85):
 * mekanism.client.gui.GuiUtils (blitNineSlicedSized, blitRepeating) and the button / color-button
 * drawing in mekanism.client.gui.element.GuiElement and BasicColorButton.
 *
 * MIT License, Copyright (c) 2017-2025 Aidan C. Brady. Full text in THIRD_PARTY_NOTICES.md and
 * licenses/mekanism/LICENSE.txt.
 */
package de.germanrangers.hstglabs.mek.client;

import de.germanrangers.hstglabs.mek.MekanismCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import mekanism.api.text.EnumColor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared Mekanism GUI drawing (GUI-03), ported from mekanism.client.gui.GuiUtils / GuiElement so our
 * screens look exactly like Mekanism's: 9-slices tile their edges and centre 1:1 instead of stretching,
 * buttons use Mekanism's button.png states, colored buttons use BasicColorButton's tint.
 */
public final class MekGui {

    public static final ResourceLocation BASE = MekanismCompat.gui("base.png");
    public static final ResourceLocation SHADOW = MekanismCompat.gui("shadow.png");
    public static final ResourceLocation BUTTON = MekanismCompat.gui("button.png");
    public static final ResourceLocation INNER_SCREEN = MekanismCompat.gui("inner_screen.png");
    public static final ResourceLocation HOLDER_LEFT = MekanismCompat.gui("holder_left.png");
    public static final ResourceLocation BAR = MekanismCompat.gui("bar/base.png");
    public static final ResourceLocation HOLDER_RIGHT = MekanismCompat.gui("holder_right.png");
    public static final ResourceLocation ELEMENT_HOLDER = MekanismCompat.gui("element_holder.png");
    public static final ResourceLocation BUTTON_DIGITAL = MekanismCompat.gui("button_digital.png");

    /** SpecialColors defaults. */
    public static final int TEXT_TITLE = 0xFF404040;
    public static final int TEXT_SUBHEADING = 0xFF787878;
    public static final int TEXT_SCREEN = 0xFF3CFE9A;

    /** GuiElement.getButtonTextureY: row 0 disabled, 1 normal, 2 hovered. */
    public static final int BUTTON_DISABLED = 0, BUTTON_NORMAL = 1, BUTTON_HOVERED = 2;

    private MekGui() {
    }

    /** GuiMekanism background / GuiWindow panel: base.png, 4 px border. */
    public static void panel(GuiGraphics g, int x, int y, int width, int height) {
        nineSliced(g, BASE, x, y, width, height, 4, 4, 256, 256, 0, 0, 256, 256);
    }

    /** GuiWindow drop shadow (non-focus windows): shadow.png at 75% alpha, 3 px outside the window. */
    public static void windowShadow(GuiGraphics g, int x, int y, int width, int height) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1F, 1F, 1F, 0.75F);
        nineSliced(g, SHADOW, x - 3, y - 3, width + 6, height + 6, 4, 4, 256, 256, 0, 0, 256, 256);
        g.setColor(1F, 1F, 1F, 1F);
    }

    /** GuiElement.drawButton with ButtonBackground.DEFAULT. */
    public static void button(GuiGraphics g, int x, int y, int width, int height, int state) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        nineSliced(g, BUTTON, x, y, width, height, 20, 4, 200, 20, 0, state * 20, 200, 60);
    }

    /** BasicColorButton: button tinted with the color, saturation -0.1 and value +0.1 in HSV. */
    public static void colorButton(GuiGraphics g, int x, int y, int width, int height, int state, EnumColor color) {
        int[] rgb = color.getRgbCode();
        float[] hsv = java.awt.Color.RGBtoHSB(rgb[0], rgb[1], rgb[2], null);
        int tinted = java.awt.Color.HSBtoRGB(hsv[0], Math.max(0F, hsv[1] - 0.1F), Math.min(1F, hsv[2] + 0.1F));
        g.setColor(((tinted >> 16) & 0xFF) / 255F, ((tinted >> 8) & 0xFF) / 255F, (tinted & 0xFF) / 255F, 1F);
        button(g, x, y, width, height, state);
        g.setColor(1F, 1F, 1F, 1F);
    }

    /** GuiSideHolder (left): holder_left.png 26x9, 4 px slices. */
    public static void leftHolder(GuiGraphics g, int x, int y, int height) {
        nineSliced(g, HOLDER_LEFT, x, y, 26, height, 4, 4, 26, 9, 0, 0, 26, 9);
    }

    /** GuiSideHolder (right): holder_right.png 26x9, 4 px slices. */
    public static void rightHolder(GuiGraphics g, int x, int y, int height) {
        nineSliced(g, HOLDER_RIGHT, x, y, 26, height, 4, 4, 26, 9, 0, 0, 26, 9);
    }

    /** GuiElementHolder background (scroll lists, supported-upgrades strip): element_holder.png, 32 px slices. */
    public static void elementHolder(GuiGraphics g, int x, int y, int width, int height) {
        nineSliced(g, ELEMENT_HOLDER, x, y, width, height, 32, 32, 256, 256, 0, 0, 256, 256);
    }

    /** DigitalButton: button_digital.png background, centred label (white / #A0A0A0 when inactive, with shadow). */
    public static void digitalButton(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, int width, int height, int state,
          net.minecraft.network.chat.Component label) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        nineSliced(g, BUTTON_DIGITAL, x, y, width, height, 20, 4, 200, 20, 0, state * 20, 200, 60);
        int color = state == BUTTON_DISABLED ? 0xFFA0A0A0 : 0xFFFFFFFF;
        g.drawString(font, label, x + (width - font.width(label)) / 2, y + (height + 1 - 8) / 2, color, true);
    }

    /** GuiBar frame: bar/base.png 5x5, 2 px slices; size includes the 1 px frame. */
    public static void barFrame(GuiGraphics g, int x, int y, int width, int height) {
        nineSliced(g, BAR, x, y, width, height, 2, 2, 5, 5, 0, 0, 5, 5);
    }

    /** GuiInnerScreen background: inner_screen.png 256x256, 32 px slices (clamped to half the size). */
    public static void innerScreen(GuiGraphics g, int x, int y, int width, int height) {
        nineSliced(g, INNER_SCREEN, x, y, width, height, 32, 32, 256, 256, 0, 0, 256, 256);
    }

    /** Port of GuiUtils.blitNineSlicedSized: corners 1:1, edges and centre tiled (never stretched). */
    public static void nineSliced(GuiGraphics g, ResourceLocation tex, int x, int y, int width, int height, int sliceWidth, int sliceHeight,
          int uWidth, int vHeight, int u, int v, int texWidth, int texHeight) {
        int cw = Math.min(sliceWidth, width / 2);
        int ew = Math.min(sliceWidth, width / 2);
        int ch = Math.min(sliceHeight, height / 2);
        int eh = Math.min(sliceHeight, height / 2);
        if (width == uWidth && height == vHeight) {
            g.blit(tex, x, y, u, v, width, height, texWidth, texHeight);
        } else if (height == vHeight) {
            g.blit(tex, x, y, u, v, cw, height, texWidth, texHeight);
            tile(g, tex, x + cw, y, width - ew - cw, height, u + cw, v, uWidth - ew - cw, vHeight, texWidth, texHeight);
            g.blit(tex, x + width - ew, y, u + uWidth - ew, v, ew, height, texWidth, texHeight);
        } else if (width == uWidth) {
            g.blit(tex, x, y, u, v, width, ch, texWidth, texHeight);
            tile(g, tex, x, y + ch, width, height - eh - ch, u, v + ch, uWidth, vHeight - eh - ch, texWidth, texHeight);
            g.blit(tex, x, y + height - eh, u, v + vHeight - eh, width, eh, texWidth, texHeight);
        } else {
            g.blit(tex, x, y, u, v, cw, ch, texWidth, texHeight);
            tile(g, tex, x + cw, y, width - ew - cw, ch, u + cw, v, uWidth - ew - cw, ch, texWidth, texHeight);
            g.blit(tex, x + width - ew, y, u + uWidth - ew, v, ew, ch, texWidth, texHeight);
            g.blit(tex, x, y + height - eh, u, v + vHeight - eh, cw, eh, texWidth, texHeight);
            tile(g, tex, x + cw, y + height - eh, width - ew - cw, eh, u + cw, v + vHeight - eh, uWidth - ew - cw, eh, texWidth, texHeight);
            g.blit(tex, x + width - ew, y + height - eh, u + uWidth - ew, v + vHeight - eh, ew, eh, texWidth, texHeight);
            tile(g, tex, x, y + ch, cw, height - eh - ch, u, v + ch, cw, vHeight - eh - ch, texWidth, texHeight);
            tile(g, tex, x + cw, y + ch, width - ew - cw, height - eh - ch, u + cw, v + ch, uWidth - ew - cw, vHeight - eh - ch, texWidth, texHeight);
            tile(g, tex, x + width - ew, y + ch, cw, height - eh - ch, u + uWidth - ew, v + ch, ew, vHeight - eh - ch, texWidth, texHeight);
        }
    }

    /** GuiUtils.blitRepeating: split the target into near-equal tiles no larger than the source, each cut from the source's centre. */
    private static void tile(GuiGraphics g, ResourceLocation tex, int x, int y, int width, int height, int u, int v, int srcWidth, int srcHeight,
          int texWidth, int texHeight) {
        if (width <= 0 || height <= 0 || srcWidth <= 0 || srcHeight <= 0) {
            return;
        }
        int cols = Math.ceilDiv(width, srcWidth);
        int rows = Math.ceilDiv(height, srcHeight);
        int px = x;
        for (int i = 0; i < cols; i++) {
            int w = width / cols + (i < width % cols ? 1 : 0);
            int du = (srcWidth - w) / 2;
            int py = y;
            for (int j = 0; j < rows; j++) {
                int h = height / rows + (j < height % rows ? 1 : 0);
                int dv = (srcHeight - h) / 2;
                g.blit(tex, px, py, u + du, v + dv, w, h, texWidth, texHeight);
                py += h;
            }
            px += w;
        }
    }

    public static boolean isIn(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
