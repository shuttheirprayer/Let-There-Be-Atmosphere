package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Divisor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class AtmosphereUi {
    private AtmosphereUi() {}

    public static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");
    public static final ResourceLocation SLIDER = new ResourceLocation("textures/gui/slider.png");
    public static final ResourceLocation CHECKBOX = new ResourceLocation("textures/gui/checkbox.png");

    public static final int PANEL = 0xFF343B47;
    public static final int RAIL = 0xFF2B313B;
    public static final int WELL = 0xFF232830;

    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFFC2C7D0;
    public static final int TEXT_SOFT = 0xFF8A909C;
    public static final int VALUE = 0xFFFFE9A8;
    public static final int GROUP = 0xFFD8C089;
    public static final int ON = 0xFF7FE07F;
    public static final int OFF = 0xFF9A9A9A;
    public static final int NOTICE = 0xFFFFC85C;

    public static void surface(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, color);
    }

    public static void separator(GuiGraphics g, int x, int y, int w) {
        if (w <= 0) return;
        g.fill(x, y, x + w, y + 1, 0xFF161A21);
        g.fill(x, y + 1, x + w, y + 2, 0xFF414A5A);
    }

    public static void widget(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        nineSlice(g, WIDGETS, x, y, w, h, hovered ? 86 : 66);
    }

    public static void sliderTrack(GuiGraphics g, int x, int y, int w, int h) {
        nineSlice(g, SLIDER, x, y, w, h, 0);
    }

    public static void sliderHandle(GuiGraphics g, int x, int y, int h, boolean hovered) {
        nineSlice(g, SLIDER, x, y, 8, h, hovered ? 60 : 40);
    }

    private static void nineSlice(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, int v) {
        if (w < 4 || h < 4) return;
        RenderSystem.enableBlend();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        int side = Math.min(20, w / 2), cap = Math.min(4, h / 2);
        int uw = 200, vh = 20;
        if (w == uw && h == vh) {
            piece(b, m, x, y, 0, v, w, h);
        } else if (h == vh) {
            piece(b, m, x, y, 0, v, side, h);
            repeat(b, m, x + side, y, w - side - side, h, side, v, uw - side - side, vh);
            piece(b, m, x + w - side, y, uw - side, v, side, h);
        } else if (w == uw) {
            piece(b, m, x, y, 0, v, w, cap);
            repeat(b, m, x, y + cap, w, h - cap - cap, 0, v + cap, uw, vh - cap - cap);
            piece(b, m, x, y + h - cap, 0, v + vh - cap, w, cap);
        } else {
            piece(b, m, x, y, 0, v, side, cap);
            repeat(b, m, x + side, y, w - side - side, cap, side, v, uw - side - side, cap);
            piece(b, m, x + w - side, y, uw - side, v, side, cap);
            piece(b, m, x, y + h - cap, 0, v + vh - cap, side, cap);
            repeat(b, m, x + side, y + h - cap, w - side - side, cap, side, v + vh - cap, uw - side - side, cap);
            piece(b, m, x + w - side, y + h - cap, uw - side, v + vh - cap, side, cap);
            repeat(b, m, x, y + cap, side, h - cap - cap, 0, v + cap, side, vh - cap - cap);
            repeat(b, m, x + side, y + cap, w - side - side, h - cap - cap, side, v + cap, uw - side - side, vh - cap - cap);
            repeat(b, m, x + w - side, y + cap, side, h - cap - cap, uw - side, v + cap, side, vh - cap - cap);
        }
        BufferUploader.drawWithShader(b.end());
    }

    private static void piece(BufferBuilder b, Matrix4f m, int x, int y, float u, float v, int w, int h) {
        float u0 = u / 256F, u1 = (u + w) / 256F, v0 = v / 256F, v1 = (v + h) / 256F;
        b.vertex(m, x, y, 0F).uv(u0, v0).endVertex();
        b.vertex(m, x, y + h, 0F).uv(u0, v1).endVertex();
        b.vertex(m, x + w, y + h, 0F).uv(u1, v1).endVertex();
        b.vertex(m, x + w, y, 0F).uv(u1, v0).endVertex();
    }

    private static void repeat(BufferBuilder b, Matrix4f m, int x, int y, int w, int h, int u, int v, int sliceW, int sliceH) {
        int px = x;
        for (Divisor across = new Divisor(w, Mth.positiveCeilDiv(w, sliceW)); across.hasNext(); ) {
            int pw = across.nextInt();
            int du = (sliceW - pw) / 2;
            int py = y;
            for (Divisor down = new Divisor(h, Mth.positiveCeilDiv(h, sliceH)); down.hasNext(); ) {
                int ph = down.nextInt();
                piece(b, m, px, py, u + du, v + (sliceH - ph) / 2, pw, ph);
                py += ph;
            }
            px += pw;
        }
    }

    public static void checkbox(GuiGraphics g, int x, int y, boolean checked) {
        RenderSystem.enableBlend();
        g.blit(CHECKBOX, x, y, 0.0F, checked ? 20.0F : 0.0F, 20, 20, 64, 64);
    }

    public static void bevel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 1, 0xFF5A6478);
        g.fill(x, y, x + 1, y + h, 0xFF5A6478);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF161A21);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF161A21);
    }

    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 1, 0xFF161A21);
        g.fill(x, y, x + 1, y + h, 0xFF161A21);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF4A5364);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF4A5364);
    }

    public static void frame(GuiGraphics g, int x, int y, int w, int h) {
        g.renderOutline(x, y, w, h, 0xFF4A5364);
    }

    public static void focusRing(GuiGraphics g, int x, int y, int w, int h) {
        g.renderOutline(x, y, w, h, 0xFFFFFFFF);
    }

    public static void pair(GuiGraphics g, Font font, int centerX, int y, String label, String value,
                            int labelColor, int valueColor) {
        if (value == null || value.isEmpty()) {
            g.drawString(font, label, centerX - font.width(label) / 2, y, labelColor, true);
            return;
        }
        String head = label + ": ";
        int total = font.width(head) + font.width(value);
        int x = centerX - total / 2;
        g.drawString(font, head, x, y, labelColor, true);
        g.drawString(font, value, x + font.width(head), y, valueColor, true);
    }

    public static String fit(Font font, String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, maxWidth - 10) + "...";
    }

    public static void swatch(GuiGraphics g, int x, int y, int size, int rgb) {
        swatch(g, x, y, size, size, rgb);
    }

    public static void swatch(GuiGraphics g, int x, int y, int w, int h, int rgb) {
        if (rgb < 0) {
            g.drawManaged(() -> {
                g.fill(x, y, x + w, y + h, 0xFF3A414E);
                for (int row = 0; row < h; row++) {
                    for (int col = (row % 4); col < w; col += 4) {
                        g.fill(x + col, y + row, x + col + 1, y + row + 1, 0xFF596273);
                    }
                }
            });
        } else {
            g.fill(x, y, x + w, y + h, 0xFF000000 | rgb);
        }
        inset(g, x, y, w, h);
    }

    public static void scrollbar(GuiGraphics g, int x, int y, int h, int total, int visible, int offset) {
        if (total <= visible) return;
        g.fill(x, y, x + 6, y + h, 0x70000000);
        int barH = Math.max(20, h * visible / total);
        int barY = y + (h - barH) * offset / Math.max(1, total - visible);
        g.fill(x, barY, x + 6, barY + barH, 0xFF4A5364);
        bevel(g, x, barY, 6, barH);
    }

    public static void tip(AbstractWidget widget, String text) {
        if (text != null && !text.isEmpty()) widget.setTooltip(Tooltip.create(Component.literal(text)));
    }
}
