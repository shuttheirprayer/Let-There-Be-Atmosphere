package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.CaptureHook;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;

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
    private static TextureTarget nearDepth;
    private static TextureTarget tilesWide;
    private static TextureTarget tiles;
    private static final int TILE = 16;
    private static int rows;
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
    private static final Matrix4f FRESH_VIEW_PROJECTION = new Matrix4f();
    private static final Matrix4f REPROJECT = new Matrix4f();
    private static double freshX, freshY, freshZ;
    private static int freshAge;
    private static int freshWidth = -1;
    private static ClientLevel freshLevel;

    public static void onRenderReflectionSolidStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        armed = false;
        taken = false;
        String why = !FxConfig.waterReflections || FxConfig.waterReflectionStrength <= 0.0 ? "disabled"
                : !DynamicFogHandler.active() || ColorGrade.shaderPackActive() ? "fog handler off or a shader pack owns the image"
                : Minecraft.useShaderTransparency() ? "Fabulous graphics keeps water in its own layer"
                : !SkyProgram.WATER_REFLECT.ready() || !SkyProgram.WATER_REFLECT_MIX.ready() || !SkyProgram.WATER_REFLECT_NEAR.ready()
                || !SkyProgram.WATER_REFLECT_DEPTH.ready() || !SkyProgram.WATER_COPY.ready() || !SkyProgram.WATER_TILES.ready() ? "shader failed to load, see the log" : null;
        if (why != null) {
            Diagnostics.skip(Diagnostics.REFLECTIONS, why);
            return;
        }
        if (SkyGate.air(event, Diagnostics.REFLECTIONS) == null) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        rows = waterRows(event, (main.height + 1) / 2);
        if (rows == 0) {
            Diagnostics.skip(Diagnostics.REFLECTIONS, "looking above the horizon");
            return;
        }
        if (!waterLikely()) {
            Diagnostics.skip(Diagnostics.REFLECTIONS, "no water on screen");
            return;
        }
        CaptureHook.probeBegin("reflect_copy");
        solid = fit(solid, main, true);
        blit(main, solid, GL11.GL_DEPTH_BUFFER_BIT, Math.min(rows * 2, main.height));
        main.bindWrite(false);
        CaptureHook.probeEnd("reflect_copy");
        armed = true;
    }

    public static void onRenderReflectionWaterStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !armed) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        water = fit(water, main, false);
        CaptureHook.probeBegin("reflect_near");
        mask(main);
        CaptureHook.probeEnd("reflect_near");
        CaptureHook.probeBegin("reflect_water");
        water.bindWrite(true);
        SkyProgram copy = SkyProgram.WATER_COPY;
        copy.begin();
        SkyProgram.texture(1, near.getColorTextureId());
        SkyProgram.texture(0, main.getColorTextureId());
        tiles(copy, 0, 2, main.width / 2F, main.height / 2F);
        copy.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        main.bindWrite(true);
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
        float spread = 2F / (ViewCapture.PROJECTION.m11() * main.height);
        CaptureHook.probeBegin("reflect_ssr");
        boolean live = ColorGrade.inPlace();
        int frame;
        if (live) {
            frame = main.getColorTextureId();
        } else {
            scene = fit(scene, main, false);
            blit(main, scene, GL11.GL_COLOR_BUFFER_BIT, main.height);
            frame = scene.getColorTextureId();
        }
        int halfWidth = (main.width + 1) / 2;
        int halfHeight = (main.height + 1) / 2;
        reflect = fitHalf(reflect, halfWidth, halfHeight);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        double dx = eye.x - freshX, dy = eye.y - freshY, dz = eye.z - freshZ;
        boolean reuse = !Cadence.due(freshAge, Cadence.interval() * 3 / 4) && freshWidth == halfWidth && freshLevel == level
                && dx * dx + dy * dy + dz * dz < 4.0;
        if (reuse) {
            freshAge++;
        } else {
            depth(main, halfWidth, halfHeight);
            Vec3 sky = ViewCapture.skyColor(level, eye, partialTick);
            program.begin();
            SkyProgram.texture(5, near.getColorTextureId());
            SkyProgram.texture(4, nearDepth.getColorTextureId());
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
                    (float) (eye.z - Math.floor(eye.z / 16.0) * 16.0), CaveMotes.now() % 3600F, 0.035F + 0.05F * Puddles.raining());
            GL20.glUniform4f(program.uniform(4), FOG[0], FOG[1], FOG[2], open(level, eye));
            GL20.glUniform4f(program.uniform(5), (float) sky.x, (float) sky.y, (float) sky.z, spread);
            reflect.setClearColor(0F, 0F, 0F, 0F);
            reflect.clear(Minecraft.ON_OSX);
            reflect.bindWrite(true);
            int query = seenQuery();
            if (query != 0) GL15.glBeginQuery(GL33.GL_ANY_SAMPLES_PASSED, query);
            tiles(program, 6, 6, near.width, near.height);
            if (query != 0) GL15.glEndQuery(GL33.GL_ANY_SAMPLES_PASSED);
            if (linear != 0) GL33.glBindSampler(0, 0);
            program.end();
            FRESH_VIEW_PROJECTION.set(ViewCapture.VIEW_PROJECTION);
            freshX = eye.x;
            freshY = eye.y;
            freshZ = eye.z;
            freshAge = 0;
            freshWidth = halfWidth;
            freshLevel = level;
        }
        CaptureHook.probeEnd("reflect_ssr");
        CaptureHook.probeBegin("reflect_mix");
        SkyProgram resolve = SkyProgram.WATER_REFLECT_MIX;
        Caustics.bindColour(main);
        RenderSystem.colorMask(true, true, true, false);
        resolve.begin();
        SkyProgram.texture(5, near.getColorTextureId());
        SkyProgram.texture(4, solid.getDepthTextureId());
        SkyProgram.texture(3, main.getDepthTextureId());
        SkyProgram.texture(2, water.getColorTextureId());
        SkyProgram.texture(1, frame);
        SkyProgram.texture(0, reflect.getColorTextureId());
        ViewCapture.INVERSE.get(MATRIX);
        GL20.glUniformMatrix4fv(resolve.uniform(0), false, MATRIX);
        GL20.glUniform4f(resolve.uniform(1), spread, main.height, (float) (eye.y - Math.floor(eye.y)), 0F);
        GL20.glUniform1f(resolve.uniform(3), reuse ? 1F : 0F);
        if (reuse) {
            REPROJECT.set(FRESH_VIEW_PROJECTION).translate((float) dx, (float) dy, (float) dz).mul(ViewCapture.INVERSE).get(MATRIX);
            GL20.glUniformMatrix4fv(resolve.uniform(2), false, MATRIX);
        }
        if (live) ColorGrade.barrier();
        tiles(resolve, 4, 6, main.width / 2F, main.height / 2F);
        resolve.end();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        main.bindWrite(true);
        CaptureHook.probeEnd("reflect_mix");
        Diagnostics.mark(Diagnostics.REFLECTIONS, Diagnostics.detail()
                ? String.format("strength %.2f sky %.2f", FxConfig.waterReflectionStrength, open) : null);
    }

    private static int waterRows(RenderLevelStageEvent event, int halfHeight) {
        Matrix4f m = REPROJECT.set(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
        float highest = Float.NEGATIVE_INFINITY;
        for (int edge = -1; edge <= 1; edge += 2) {
            float a = m.m00() - edge * m.m03(), b = m.m20() - edge * m.m23();
            float c = b, s = -a;
            float w = m.m03() * c + m.m23() * s;
            if (w < 0F) {
                c = -c;
                s = -s;
                w = -w;
            }
            if (w < 1.0E-6F) return halfHeight;
            highest = Math.max(highest, (m.m01() * c + m.m21() * s) / w);
        }
        return Mth.clamp((int) Math.ceil((highest * 0.5F + 0.5F) * halfHeight) + 2, 0, halfHeight);
    }

    private static void mask(RenderTarget main) {
        int halfWidth = (main.width + 1) / 2;
        int halfHeight = (main.height + 1) / 2;
        near = fitHalf(near, halfWidth, halfHeight);
        tilesWide = fitHalf(tilesWide, (halfWidth + 3) / 4, (halfHeight + 3) / 4);
        tiles = fitHalf(tiles, (tilesWide.width + 3) / 4, (tilesWide.height + 3) / 4);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        near.setClearColor(0F, 0F, 0F, 0F);
        near.clear(Minecraft.ON_OSX);
        near.bindWrite(true);
        RenderSystem.enableScissor(0, 0, halfWidth, rows);
        SkyProgram program = SkyProgram.WATER_REFLECT_NEAR;
        program.begin();
        SkyProgram.texture(1, main.getDepthTextureId());
        SkyProgram.texture(0, solid.getDepthTextureId());
        program.draw(3);
        program.end();
        RenderSystem.disableScissor();
        SkyProgram reduce = SkyProgram.WATER_TILES;
        reduce.begin();
        tilesWide.bindWrite(true);
        SkyProgram.texture(0, near.getColorTextureId());
        reduce.draw(3);
        tiles.bindWrite(true);
        SkyProgram.texture(0, tilesWide.getColorTextureId());
        reduce.draw(3);
        reduce.end();
    }

    private static void depth(RenderTarget main, int halfWidth, int halfHeight) {
        if (nearDepth == null || nearDepth.width != halfWidth || nearDepth.height != halfHeight) {
            nearDepth = fitHalf(nearDepth, halfWidth, halfHeight);
            GlStateManager._bindTexture(nearDepth.getColorTextureId());
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, halfWidth, halfHeight, 0, GL11.GL_RED, GL11.GL_FLOAT, null);
        }
        nearDepth.bindWrite(true);
        SkyProgram program = SkyProgram.WATER_REFLECT_DEPTH;
        program.begin();
        SkyProgram.texture(1, main.getDepthTextureId());
        SkyProgram.texture(0, solid.getDepthTextureId());
        GL20.glUniform1f(program.uniform(0), rows);
        program.draw(3);
        program.end();
    }

    private static void tiles(SkyProgram program, int uniform, int unit, float width, float height) {
        SkyProgram.texture(unit, tiles.getColorTextureId());
        GL20.glUniform4f(program.uniform(uniform), tiles.width, tiles.height, TILE / width, TILE / height);
        program.draw(tiles.width * tiles.height * 6);
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

    private static void blit(RenderTarget from, RenderTarget to, int bits, int rows) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, from.width, rows, 0, 0, to.width, rows, bits, GL11.GL_NEAREST);
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
