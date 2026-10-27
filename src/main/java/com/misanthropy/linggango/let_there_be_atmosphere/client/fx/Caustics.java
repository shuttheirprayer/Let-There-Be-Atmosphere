package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
final class Caustics {
    private Caustics() {}

    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final double SCALE = 1.0 / 4.5;

    private static int frame;

    static void draw(Vec3 camera, float top, float slant, float strength) {
        SkyProgram program = SkyProgram.CAUSTICS;
        if (!program.ready()) {
            Diagnostics.skip(Diagnostics.CAUSTICS, "shader failed to load, see the log");
            return;
        }
        if (strength < 0.004F) {
            Diagnostics.skip(Diagnostics.CAUSTICS, "no sun to shine down");
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        bindColour(main);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        program.begin();
        SkyProgram.texture(1, Haze.noiseTexture());
        SkyProgram.texture(0, main.getDepthTextureId());
        ViewCapture.INVERSE.get(MATRIX);
        GL20.glUniformMatrix4fv(program.uniform(0), false, MATRIX);
        GL20.glUniform4f(program.uniform(1), top, slant, 0F, strength);
        double u = camera.x * SCALE;
        double v = camera.z * SCALE;
        GL20.glUniform4f(program.uniform(2), (float) (u - Math.floor(u)), (float) (v - Math.floor(v)), (float) SCALE, CaveMotes.now());
        program.draw(3);
        program.end();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        main.bindWrite(true);
        Diagnostics.mark(Diagnostics.CAUSTICS, Diagnostics.detail() ? String.format("strength %.2f", strength) : null);
    }

    static void bindColour(RenderTarget main) {
        if (frame == 0) frame = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, frame);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, main.getColorTextureId(), 0);
        GlStateManager._viewport(0, 0, main.width, main.height);
    }
}
