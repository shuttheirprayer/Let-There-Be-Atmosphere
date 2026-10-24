package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
final class CloudBlur {
    private CloudBlur() {}

    private static final int CLAMP = 0x812F;
    private static final int GL14_DEPTH24 = 0x81A6;
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final Matrix4f INVERSE = new Matrix4f();

    private static int layer;
    private static int layerFrame;
    private static int depth;
    private static int width;
    private static int height;
    private static int layerWidth;
    private static int layerHeight;
    private static final int[] HALF = new int[2];
    private static final int[] HALF_FRAME = new int[2];
    private static int out;
    private static int attached;
    private static int cover;

    static int takeCover() {
        int texture = cover;
        cover = 0;
        return texture;
    }

    static boolean ready() {
        return SkyProgram.CLOUD_BLUR.ready() && SkyProgram.CLOUD_SOFT.ready() && !Minecraft.useShaderTransparency();
    }

    static void begin(RenderTarget main) {
        fit(main.width, main.height);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, layerFrame);
        GlStateManager._viewport(0, 0, layerWidth, layerHeight);
        GlStateManager._clearColor(0F, 0F, 0F, 0F);
        GlStateManager._clearDepth(1.0);
        GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    static void end(RenderTarget main, Matrix4f view, Matrix4f projection, float[] layers, float spread) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        int halfW = Math.max(width / 2, 1);
        int halfH = Math.max(height / 2, 1);
        float scale = spread * Math.max(height / 1080F, 0.5F);

        SkyProgram blur = SkyProgram.CLOUD_BLUR;
        blur.begin();
        GlStateManager._viewport(0, 0, halfW, halfH);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, HALF_FRAME[0]);
        SkyProgram.texture(0, layer);
        GL20.glUniform4f(blur.uniform(0), scale / halfW, 0F, 0F, 0F);
        blur.draw(3);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, HALF_FRAME[1]);
        SkyProgram.texture(0, HALF[0]);
        GL20.glUniform4f(blur.uniform(0), 0F, scale / halfH, 0F, 0F);
        blur.draw(3);
        blur.end();

        if (out == 0) out = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, out);
        if (attached != main.getColorTextureId()) {
            attached = main.getColorTextureId();
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, attached, 0);
        }
        GlStateManager._viewport(0, 0, main.width, main.height);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        INVERSE.set(projection).mul(view);
        int floor = horizonRow(INVERSE, layers, main.height, (int) Math.ceil(8F * scale) + 4);
        INVERSE.invert();
        INVERSE.get(MATRIX);
        SkyProgram soft = SkyProgram.CLOUD_SOFT;
        soft.begin();
        SkyProgram.texture(1, main.getDepthTextureId());
        SkyProgram.texture(0, HALF[1]);
        GL20.glUniformMatrix4fv(soft.uniform(0), false, MATRIX);
        GL20.glUniform4f(soft.uniform(1), layers[0], layers[1], layers[2], layers[3]);
        if (floor > 0) RenderSystem.enableScissor(0, floor, main.width, main.height - floor);
        if (floor < main.height) soft.draw(3);
        if (floor > 0) RenderSystem.disableScissor();
        soft.end();
        cover = HALF[1];

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        main.bindWrite(true);
    }

    private static int horizonRow(Matrix4f m, float[] layers, int height, int margin) {
        boolean below = layers[0] > 0.5F && (layers[3] <= 0F || layers[2] > 0.5F);
        if (!below) return 0;
        float lowest = Float.POSITIVE_INFINITY;
        for (int edge = -1; edge <= 1; edge += 2) {
            float a = m.m00() - edge * m.m03(), b = m.m20() - edge * m.m23();
            float c = b, s = -a;
            float w = m.m03() * c + m.m23() * s;
            if (w < 0F) {
                c = -c;
                s = -s;
                w = -w;
            }
            if (w < 1.0E-6F) return 0;
            lowest = Math.min(lowest, (m.m01() * c + m.m21() * s) / w);
        }
        int row = (int) Math.floor((lowest * 0.5F + 0.5F) * height) - margin;
        return Math.max(0, Math.min(row, height));
    }

    private static void fit(int w, int h) {
        int lw = Math.max(w / 2, 1), lh = Math.max(h / 2, 1);
        if (layerFrame != 0 && w == width && h == height && lw == layerWidth && lh == layerHeight) return;
        width = w;
        height = h;
        layerWidth = lw;
        layerHeight = lh;
        if (layer == 0) layer = texture();
        GlStateManager._bindTexture(layer);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, lw, lh, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        if (layerFrame == 0) layerFrame = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, layerFrame);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, layer, 0);
        if (depth == 0) depth = GlStateManager._genTexture();
        GlStateManager._bindTexture(depth);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14_DEPTH24, lw, lh, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, (ByteBuffer) null);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, layerFrame);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth, 0);
        for (int i = 0; i < 2; i++) {
            if (HALF[i] == 0) HALF[i] = texture();
            GlStateManager._bindTexture(HALF[i]);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, Math.max(w / 2, 1), Math.max(h / 2, 1), 0,
                    GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
            if (HALF_FRAME[i] == 0) HALF_FRAME[i] = GlStateManager.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, HALF_FRAME[i]);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, HALF[i], 0);
        }
        GlStateManager._bindTexture(0);
    }

    private static int texture() {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, CLAMP);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, CLAMP);
        return id;
    }
}
