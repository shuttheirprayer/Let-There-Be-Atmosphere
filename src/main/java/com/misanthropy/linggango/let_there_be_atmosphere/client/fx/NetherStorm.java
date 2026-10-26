package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeFx;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class NetherStorm {
    private NetherStorm() {}

    private static final ResourceLocation ATLAS = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/nether_storm.png");
    private static final int[] COUNTS = {3600, 520, 56, 64};
    private static final float[][] BOXES = {{56F, 24F, 56F}, {128F, 36F, 128F}, {400F, 80F, 400F}, {200F, 48F, 200F}};
    private static final float[] SPEED = {17F, 12F, 7F, 15F};
    private static final float[] DENSITY = {1F, 0.9F, 0.85F, 1F};
    private static final float[][] EMBER = {
            {1.0F, 0.52F, 0.14F}, {1.0F, 0.2F, 0.16F}, {0.5F, 0.85F, 1.0F}, {0.35F, 1.0F, 0.8F}, {1.0F, 0.7F, 0.35F},
    };
    private static final float[][] ASH = {
            {0.24F, 0.13F, 0.1F}, {0.26F, 0.06F, 0.07F}, {0.1F, 0.17F, 0.24F}, {0.07F, 0.2F, 0.2F}, {0.3F, 0.26F, 0.25F},
    };
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(16);
    private static final int[] ACTIVE = new int[COUNTS.length];
    private static final FloatBuffer RING_BOXES = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer RING_CAMS = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer RING_WINDS = BufferUtils.createFloatBuffer(16);
    private static final float[] EMBER_NOW = EMBER[0].clone();
    private static final float[] ASH_NOW = ASH[0].clone();

    private static int layer;
    private static int layerFrame;
    private static int lowDepth;
    private static int lowFrame;
    private static int layerWidth;
    private static int layerHeight;
    private static double phase;
    private static long frameAt;

    public static void onRenderNetherStormStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!NetherAir.here(level)) {
            Diagnostics.skip(Diagnostics.NETHER_STORM, "not in the Nether");
            frameAt = 0L;
            return;
        }
        if (!(FxConfig.netherStorms || BiomeFx.on(false, BiomeFx.NETHER_STORMS))) {
            Diagnostics.skip(Diagnostics.NETHER_STORM, "disabled");
            return;
        }
        float density = NetherDust.density();
        if (density < 0.01F) {
            phase = 0.0;
            frameAt = 0L;
            Diagnostics.skip(Diagnostics.NETHER_STORM, "calm");
            return;
        }
        SkyProgram program = SkyProgram.NETHER_STORM;
        Camera camera = event.getCamera();
        if (!DynamicFogHandler.active() || ColorGrade.shaderPackActive() || !program.ready() || CameraFluid.of(camera) != FogType.NONE) {
            Diagnostics.skip(Diagnostics.NETHER_STORM, "fog handler off, shader pack, camera in a fluid or shader failed");
            return;
        }
        long now = Util.getNanos();
        float dt = frameAt == 0L ? 0F : Math.min((now - frameAt) / 1.0E9F, 0.1F);
        boolean snap = frameAt == 0L;
        frameAt = now;
        float surge = NetherDust.surge();
        phase += dt * (1F + 0.9F * surge);
        int look = NetherAir.look();
        float k = snap ? 1F : 1F - (float) Math.exp(-dt * 0.8F);
        for (int i = 0; i < 3; i++) {
            EMBER_NOW[i] += (EMBER[look][i] - EMBER_NOW[i]) * k;
            ASH_NOW[i] += (ASH[look][i] - ASH_NOW[i]) * k;
        }

        Vec3 eye = camera.getPosition();
        double blockX = Math.floor(eye.x), blockY = Math.floor(eye.y), blockZ = Math.floor(eye.z);
        float fx = (float) (eye.x - blockX), fy = (float) (eye.y - blockY), fz = (float) (eye.z - blockZ);
        MATRIX.set(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose()).translate(-fx, -fy, -fz);
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        float[] fog = RenderSystem.getShaderFogColor();
        float speed = 1F + 0.35F * Mth.clamp((float) FxConfig.netherStormStrength - 1F, 0F, 1F);

        float drive = (NetherDust.storm() * 0.55F + NetherDust.gust() * 0.45F) * (float) FxConfig.netherStormStrength * (1F + 0.3F * surge);
        float billow = (float) Mth.smoothstep(Mth.clamp((drive - 0.25F) / 0.45F, 0F, 1F));
        Matrix4f projection = event.getProjectionMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderTarget main = mc.getMainRenderTarget();
        boolean split = SkyProgram.STORM_DEPTH.ready() && SkyProgram.STORM_MIX.ready()
                && GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) == main.frameBufferId;
        if (split) {
            fit(main.width, main.height);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, lowFrame);
            GlStateManager._viewport(0, 0, layerWidth, layerHeight);
            RenderSystem.disableBlend();
            SkyProgram low = SkyProgram.STORM_DEPTH;
            low.begin();
            SkyProgram.texture(0, main.getDepthTextureId());
            low.draw(3);
            low.end();
            main.bindWrite(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        program.begin();
        SkyProgram.texture(1, mc.getMainRenderTarget().getDepthTextureId());
        SkyProgram.texture(0, SkyProgram.texture(ATLAS));
        MATRIX.get(UPLOAD);
        GL20.glUniformMatrix4fv(program.uniform(0), false, UPLOAD);
        GL20.glUniform4f(program.uniform(1), fx, fy, fz, (float) phase);
        GL20.glUniform4f(program.uniform(5), EMBER_NOW[0], EMBER_NOW[1], EMBER_NOW[2], 1F);
        GL20.glUniform4f(program.uniform(6), ASH_NOW[0], ASH_NOW[1], ASH_NOW[2], 1F);
        GL20.glUniform4f(program.uniform(7), Math.min(fog[0] * 1.5F + 0.04F, 1F), Math.min(fog[1] * 1.5F + 0.04F, 1F),
                Math.min(fog[2] * 1.5F + 0.04F, 1F), 1F);
        GL20.glUniform4f(program.uniform(8), fog[0] * 0.4F, fog[1] * 0.4F, fog[2] * 0.4F, 1F);
        GL20.glUniform3f(program.uniform(9), -left.x(), -left.y(), -left.z());
        GL20.glUniform3f(program.uniform(10), up.x(), up.y(), up.z());
        GL20.glUniform4f(program.uniform(11), RenderSystem.getShaderFogStart(), Math.max(RenderSystem.getShaderFogEnd(), 16F), 1F, 0.4F + 0.6F * billow);
        GL20.glUniform4f(program.uniform(12), fog[0], fog[1], fog[2], 1F);
        float seconds = (Util.getMillis() % 3_600_000L) / 1000F;
        float windX = NetherDust.windX(), windZ = NetherDust.windZ();
        int total = 0;
        RING_BOXES.clear();
        RING_CAMS.clear();
        RING_WINDS.clear();
        for (int ring = 0; ring < COUNTS.length; ring++) {
            float[] box = BOXES[ring];
            float share = ring == 3 ? billow : Math.min(density * DENSITY[ring], 1F);
            ACTIVE[ring] = Math.min(COUNTS[ring], (int) Math.ceil(COUNTS[ring] * share));
            total += ACTIVE[ring];
            RING_BOXES.put(box[0]).put(box[1]).put(box[2]).put(0F);
            RING_CAMS.put(wrap(eye.x, box[0])).put(wrap(eye.y, box[1])).put(wrap(eye.z, box[2])).put(1F);
            RING_WINDS.put(windX).put(windZ).put(SPEED[ring] * speed).put(seconds);
        }
        RING_BOXES.flip();
        RING_CAMS.flip();
        RING_WINDS.flip();
        GL20.glUniform4fv(program.uniform(2), RING_BOXES);
        GL20.glUniform4fv(program.uniform(3), RING_CAMS);
        GL20.glUniform4fv(program.uniform(4), RING_WINDS);
        GL20.glUniform4f(program.uniform(13), ACTIVE[0], ACTIVE[0] + ACTIVE[1], ACTIVE[0] + ACTIVE[1] + ACTIVE[2], 0F);
        int embers = ACTIVE[0] * 6;
        if (!split) {
            GL20.glUniform4f(program.uniform(14), projection.m22(), projection.m32(), 0F, 0F);
            program.draw(total * 6);
        } else {
            SkyProgram.texture(1, 0);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            GL20.glUniform4f(program.uniform(14), projection.m22(), projection.m32(), 1F, 0F);
            program.drawRange(0, embers);
            RenderSystem.disableDepthTest();
            if (total * 6 > embers) {
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, layerFrame);
                GlStateManager._viewport(0, 0, layerWidth, layerHeight);
                GlStateManager._clearColor(0F, 0F, 0F, 0F);
                GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                SkyProgram.texture(1, lowDepth);
                GL20.glUniform4f(program.uniform(14), projection.m22(), projection.m32(), 0F, 0F);
                program.drawRange(embers, total * 6 - embers);
                program.end();
                main.bindWrite(true);
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                SkyProgram mix = SkyProgram.STORM_MIX;
                mix.begin();
                SkyProgram.texture(2, main.getDepthTextureId());
                SkyProgram.texture(1, lowDepth);
                SkyProgram.texture(0, layer);
                GL20.glUniform4f(mix.uniform(0), projection.m22(), projection.m32(), 0F, 0F);
                mix.draw(3);
                mix.end();
            } else {
                program.end();
            }
        }
        if (!split) program.end();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        Diagnostics.mark(Diagnostics.NETHER_STORM, Diagnostics.detail()
                ? String.format("look %d, density %.2f, surge %.2f, fog %.0f-%.0f", look, density, surge,
                RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd()) : null);
    }

    private static void fit(int w, int h) {
        int lw = Math.max(w / 2, 1), lh = Math.max(h / 2, 1);
        if (layerFrame != 0 && lw == layerWidth && lh == layerHeight) return;
        layerWidth = lw;
        layerHeight = lh;
        if (layer == 0) {
            layer = GlStateManager._genTexture();
            GlStateManager._bindTexture(layer);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F);
        }
        GlStateManager._bindTexture(layer);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, lw, lh, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
        if (layerFrame == 0) layerFrame = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, layerFrame);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, layer, 0);
        if (lowDepth == 0) {
            lowDepth = GlStateManager._genTexture();
            GlStateManager._bindTexture(lowDepth);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }
        GlStateManager._bindTexture(lowDepth);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, lw, lh, 0, GL11.GL_RED, GL11.GL_FLOAT, (ByteBuffer) null);
        if (lowFrame == 0) lowFrame = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, lowFrame);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, lowDepth, 0);
        GlStateManager._bindTexture(0);
    }

    private static float wrap(double value, float size) {
        return (float) (value - Math.floor(value / size) * size);
    }
}
