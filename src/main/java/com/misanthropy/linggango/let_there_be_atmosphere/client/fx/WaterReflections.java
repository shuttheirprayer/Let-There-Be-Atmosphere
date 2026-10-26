package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.CaptureHook;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;

@OnlyIn(Dist.CLIENT)
public final class WaterReflections {
    private WaterReflections() {}

    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final float[] FOG = new float[3];
    private static TextureTarget solid;
    private static TextureTarget water;
    private static TextureTarget scene;
    private static TextureTarget reflect;
    private static TextureTarget near;
    private static boolean armed;
    private static boolean taken;
    private static float open = -1F;
    private static long openAt;
    private static final int[] SEEN = new int[4];
    private static final boolean[] PENDING = new boolean[4];
    private static int seenSlot;
    private static long seenAt = Long.MIN_VALUE / 2;
    private static long holdUntil;
    private static int queries = -1;
    private static int idleFrames;
    private static long probedAt;

    public static void onRenderReflectionSolidStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        armed = false;
        taken = false;
        String why = !FxConfig.waterReflections || FxConfig.waterReflectionStrength <= 0.0 ? "disabled"
                : !DynamicFogHandler.active() || ColorGrade.shaderPackActive() ? "fog handler off or a shader pack owns the image"
                : Minecraft.useShaderTransparency() ? "Fabulous graphics keeps water in its own layer"
                : !SkyProgram.WATER_REFLECT.ready() || !SkyProgram.WATER_REFLECT_MIX.ready() || !SkyProgram.WATER_REFLECT_NEAR.ready() ? "shader failed to load, see the log" : null;
        if (why != null) {
            Diagnostics.skip(Diagnostics.REFLECTIONS, why);
            return;
        }
        if (SkyGate.air(event, Diagnostics.REFLECTIONS) == null) return;
        if (!waterLikely()) {
            Diagnostics.skip(Diagnostics.REFLECTIONS, "no water on screen");
            return;
        }
        CaptureHook.probeBegin("reflect_copy");
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        solid = fit(solid, main, true);
        solid.copyDepthFrom(main);
        main.bindWrite(false);
        CaptureHook.probeEnd("reflect_copy");
        armed = true;
    }

    public static void onRenderReflectionWaterStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !armed) return;
        CaptureHook.probeBegin("reflect_water");
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        water = fit(water, main, false);
        blit(main, water, GL11.GL_COLOR_BUFFER_BIT);
        main.bindWrite(false);
        CaptureHook.probeEnd("reflect_water");
        float[] fog = RenderSystem.getShaderFogColor();
        System.arraycopy(fog, 0, FOG, 0, 3);
        taken = true;
    }

    public static void onRenderReflectionStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !armed) return;
        armed = false;
        if (!taken || !ViewCapture.fresh) {
            Diagnostics.skip(Diagnostics.REFLECTIONS, "the water pass did not run this frame");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        SkyProgram program = SkyProgram.WATER_REFLECT;
        RenderTarget main = mc.getMainRenderTarget();
        Vec3 eye = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        Vec3 sky = level.getSkyColor(eye, partialTick);
        float time = (CaveMotes.now() % 3600F);
        float rough = 0.035F + 0.05F * Puddles.raining();
        float spread = 2F / (ViewCapture.PROJECTION.m11() * main.height);
        CaptureHook.probeBegin("reflect");
        boolean live = ColorGrade.inPlace();
        int frame;
        if (live) {
            frame = main.getColorTextureId();
        } else {
            scene = fit(scene, main, false);
            blit(main, scene, GL11.GL_COLOR_BUFFER_BIT);
            frame = scene.getColorTextureId();
        }
        int halfWidth = (main.width + 1) / 2;
        int halfHeight = (main.height + 1) / 2;
        if (near == null) {
            near = new TextureTarget(halfWidth, halfHeight, true, Minecraft.ON_OSX);
        } else if (near.width != halfWidth || near.height != halfHeight) {
            near.resize(halfWidth, halfHeight, Minecraft.ON_OSX);
        }
        near.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.disableBlend();
        SkyProgram shrink = SkyProgram.WATER_REFLECT_NEAR;
        shrink.begin();
        SkyProgram.texture(0, solid.getDepthTextureId());
        shrink.draw(3);
        shrink.end();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.colorMask(true, true, true, true);
        reflect = fitHalf(reflect, halfWidth, halfHeight);
        reflect.setClearColor(0F, 0F, 0F, 0F);
        reflect.clear(Minecraft.ON_OSX);
        reflect.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        program.begin();
        SkyProgram.texture(4, near.getDepthTextureId());
        SkyProgram.texture(3, Haze.noiseTexture());
        SkyProgram.texture(2, solid.getDepthTextureId());
        SkyProgram.texture(1, main.getDepthTextureId());
        SkyProgram.texture(0, frame);
        int linear = live ? ColorGrade.sceneSampler() : 0;
        if (linear != 0) GL33.glBindSampler(0, linear);
        ViewCapture.INVERSE.get(MATRIX);
        GL20.glUniformMatrix4fv(program.uniform(0), false, MATRIX);
        ViewCapture.VIEW_PROJECTION.get(MATRIX);
        GL20.glUniformMatrix4fv(program.uniform(1), false, MATRIX);
        GL20.glUniform4f(program.uniform(2), (float) (eye.y - Math.floor(eye.y)),
                (float) FxConfig.waterReflectionStrength * (1F - 0.3F * Puddles.raining()), ViewCapture.fogStart, ViewCapture.fogEnd);
        GL20.glUniform4f(program.uniform(3), (float) (eye.x - Math.floor(eye.x / 16.0) * 16.0),
                (float) (eye.z - Math.floor(eye.z / 16.0) * 16.0), time, rough);
        GL20.glUniform4f(program.uniform(4), FOG[0], FOG[1], FOG[2], open(level, eye));
        GL20.glUniform4f(program.uniform(5), (float) sky.x, (float) sky.y, (float) sky.z, spread);
        int query = seenQuery();
        if (query != 0) GL15.glBeginQuery(GL33.GL_ANY_SAMPLES_PASSED, query);
        program.draw(3);
        if (query != 0) GL15.glEndQuery(GL33.GL_ANY_SAMPLES_PASSED);
        if (linear != 0) GL33.glBindSampler(0, 0);
        program.end();
        SkyProgram resolve = SkyProgram.WATER_REFLECT_MIX;
        Caustics.bindColour(main);
        RenderSystem.colorMask(true, true, true, false);
        resolve.begin();
        SkyProgram.texture(4, solid.getDepthTextureId());
        SkyProgram.texture(3, main.getDepthTextureId());
        SkyProgram.texture(2, water.getColorTextureId());
        SkyProgram.texture(1, frame);
        SkyProgram.texture(0, reflect.getColorTextureId());
        ViewCapture.INVERSE.get(MATRIX);
        GL20.glUniformMatrix4fv(resolve.uniform(0), false, MATRIX);
        GL20.glUniform4f(resolve.uniform(1), spread, main.height, (float) (eye.y - Math.floor(eye.y)), 0F);
        if (live) ColorGrade.barrier();
        resolve.draw(3);
        resolve.end();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        main.bindWrite(true);
        CaptureHook.probeEnd("reflect");
        Diagnostics.mark(Diagnostics.REFLECTIONS, Diagnostics.detail()
                ? String.format("strength %.2f sky %.2f", FxConfig.waterReflectionStrength, open) : null);
    }

    private static boolean waterLikely() {
        if (!queriesSupported()) return true;
        long now = Util.getMillis();
        for (int i = 0; i < SEEN.length; i++) {
            if (!PENDING[i] || GL15.glGetQueryObjecti(SEEN[i], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) continue;
            PENDING[i] = false;
            if (GL15.glGetQueryObjecti(SEEN[i], GL15.GL_QUERY_RESULT) != 0) seenAt = now;
            else holdUntil = 0L;
        }
        if (now - seenAt < 1500L || now < holdUntil) {
            idleFrames = 0;
            return true;
        }
        if (++idleFrames < 6 || now - probedAt < 80L) return false;
        idleFrames = 0;
        probedAt = now;
        holdUntil = now + 250L;
        return true;
    }

    private static boolean queriesSupported() {
        if (queries < 0) {
            GLCapabilities caps = GL.getCapabilities();
            queries = caps.OpenGL33 || caps.GL_ARB_occlusion_query2 ? 1 : 0;
        }
        return queries == 1;
    }

    private static int seenQuery() {
        if (!queriesSupported()) return 0;
        if (SEEN[0] == 0) {
            for (int i = 0; i < SEEN.length; i++) SEEN[i] = GL15.glGenQueries();
        }
        int slot = seenSlot;
        if (PENDING[slot]) return 0;
        seenSlot = (slot + 1) % SEEN.length;
        PENDING[slot] = true;
        return SEEN[slot];
    }

    private static float open(ClientLevel level, Vec3 eye) {
        float target = level.dimensionType().hasSkyLight()
                ? SkyGlow.smooth(4F, 12F, level.getBrightness(LightLayer.SKY, BlockPos.containing(eye))) : 0F;
        long now = Util.getNanos();
        if (open < 0F) open = target;
        float dt = Math.min((now - openAt) / 1.0E9F, 0.25F);
        openAt = now;
        open += (target - open) * (1F - (float) Math.exp(-dt * 1.5F));
        return open;
    }

    private static void blit(RenderTarget from, RenderTarget to, int bits) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height, bits, GL11.GL_NEAREST);
    }

    private static TextureTarget fitHalf(TextureTarget target, int width, int height) {
        if (target == null) {
            target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        } else {
            return target;
        }
        target.setFilterMode(GL11.GL_NEAREST);
        return target;
    }

    private static TextureTarget fit(TextureTarget target, RenderTarget main, boolean depth) {
        if (target == null) {
            target = new TextureTarget(main.width, main.height, depth, Minecraft.ON_OSX);
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height, Minecraft.ON_OSX);
        } else if (!depth || main.isStencilEnabled() == target.isStencilEnabled()) {
            return target;
        }
        if (depth && main.isStencilEnabled() && !target.isStencilEnabled()) target.enableStencil();
        target.setFilterMode(GL11.GL_LINEAR);
        return target;
    }
}
