package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class EndSky {
    private EndSky() {}

    private static final int SIZE = 64;
    private static final int FACE = 512;
    private static final int[] PERIODS = {4, 8, 16, 4};
    private static final int[] SEEDS = {11, 23, 37, 53};
    private static final int[][] GRADIENTS = {{1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1},
            {1, 0, -1}, {-1, 0, -1}, {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}, {1, 1, 0}, {-1, 1, 0}, {0, -1, 1}, {0, -1, -1}};
    private static final float[][] FACES = {{0, 0, -1, 0, -1, 0, 1, 0, 0}, {0, 0, 1, 0, -1, 0, -1, 0, 0}, {1, 0, 0, 0, 0, 1, 0, 1, 0},
            {1, 0, 0, 0, 0, -1, 0, -1, 0}, {1, 0, 0, 0, -1, 0, 0, 0, 1}, {-1, 0, 0, 0, -1, 0, 0, 0, -1}};
    private static final float[] HAZE = {0.22F, 0.125F, 0.39F};
    private static final int STARS = 2600;
    private static final Quaternionf ALIGN = new Quaternionf().rotationTo(0.33996F, 0.30996F, 0.88789F, 0.3003F, 0.8610F, 0.4105F);
    private static final Quaternionf SPIN = new Quaternionf();
    private static final Quaternionf UNSPIN = new Quaternionf();
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final Matrix3f TURN = new Matrix3f();
    private static final FloatBuffer INVERSE = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer SPIN_MATRIX = BufferUtils.createFloatBuffer(9);
    private static int nebula;
    private static int cube;

    static boolean on(ClientLevel level) {
        return FxConfig.endSky && DynamicFogHandler.active() && level != null
                && level.effects().skyType() == DimensionSpecialEffects.SkyType.END;
    }

    public static boolean covers(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        return on(mc.level) && SkyProgram.END_SKY.ready() && SkyProgram.END_NEBULA.ready()
                && CameraFluid.of(camera) == FogType.NONE
                && (mc.player == null || !(mc.player.hasEffect(MobEffects.BLINDNESS) || mc.player.hasEffect(MobEffects.DARKNESS)))
                && !mc.gui.getBossOverlay().shouldCreateWorldFog();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEndSkyFogColor(ViewportEvent.ComputeFogColor event) {
        if (!on(Minecraft.getInstance().level) || CameraFluid.of(event.getCamera()) != FogType.NONE) return;
        float mix = FxConfig.endFog ? 0.4F : 0.85F;
        event.setRed(Mth.lerp(mix, event.getRed(), HAZE[0]));
        event.setGreen(Mth.lerp(mix, event.getGreen(), HAZE[1]));
        event.setBlue(Mth.lerp(mix, event.getBlue(), HAZE[2]));
    }

    public static void onRenderEndSkyStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (!covers(event.getCamera())) {
            Diagnostics.skip(Diagnostics.END_SKY, "off, not the End, or the sky is hidden");
            return;
        }
        float seconds = (Util.getMillis() % 3_600_000L) / 1000F;
        SPIN.rotationAxis(seconds / 3600F * Mth.TWO_PI, 0.17F, 0.98F, 0.11F);
        TURN.rotation(SPIN).get(SPIN_MATRIX);
        MATRIX.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix())
                .mul(event.getPoseStack().last().pose()).invert();
        MATRIX.get(INVERSE);
        float[] fog = RenderSystem.getShaderFogColor();
        int sky = cube();

        SkyProgram program = SkyProgram.END_SKY;
        RenderSystem.disableBlend();
        RenderSystem.depthMask(false);
        program.begin();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, sky);
        GL20.glUniformMatrix4fv(program.uniform(0), false, INVERSE);
        GL20.glUniformMatrix3fv(program.uniform(1), false, SPIN_MATRIX);
        GL20.glUniform4f(program.uniform(2), (seconds / 15F) % 1F, 0.92F, 0.24F, 0F);
        GL20.glUniform4f(program.uniform(3), fog[0], fog[1], fog[2], 0.4F);
        program.draw(3);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);
        program.end();
        stars(event, seconds);
        RenderSystem.depthMask(true);
        Diagnostics.mark(Diagnostics.END_SKY, null);
    }

    private static void stars(RenderLevelStageEvent event, float seconds) {
        SkyProgram stars = SkyProgram.STARS;
        if (!stars.ready()) return;
        var main = Minecraft.getInstance().getMainRenderTarget();
        MATRIX.set(event.getPoseStack().last().pose()).rotate(UNSPIN.set(SPIN).conjugate()).rotate(ALIGN);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        stars.begin();
        SkyProgram.texture(0, Haze.noiseTexture());
        stars.transform(0, MATRIX);
        GL20.glUniform4f(stars.uniform(1), 17F, seconds, STARS, 0.7F);
        GL20.glUniform4f(stars.uniform(2), 2F / Math.max(main.width, 1), 2F / Math.max(main.height, 1), 0F, 0F);
        GL20.glUniform4f(stars.uniform(3), 1F, main.height / 1080F, 0F, 0F);
        stars.draw(STARS * 6);
        stars.end();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static int cube() {
        if (cube != 0) return cube;
        long started = Util.getNanos();
        int volume = nebula();
        cube = GlStateManager._genTexture();
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cube);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        for (int face = 0; face < 6; face++) {
            GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, 0, GL11.GL_RGBA8, FACE, FACE, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        }
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);
        int frame = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, frame);
        GlStateManager._viewport(0, 0, FACE, FACE);
        RenderSystem.disableBlend();
        SkyProgram bake = SkyProgram.END_NEBULA;
        bake.begin();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, volume);
        for (int face = 0; face < 6; face++) {
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, cube, 0);
            GL20.glUniformMatrix3fv(bake.uniform(0), false, FACES[face]);
            bake.draw(3);
        }
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        bake.end();
        GlStateManager._glDeleteFramebuffers(frame);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        GL11.glEnable(GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS);
        Diagnostics.LOG.info("[atmosphere] End sky baked in {} ms", (Util.getNanos() - started) / 1_000_000L);
        return cube;
    }

    private static int nebula() {
        if (nebula != 0) return nebula;
        int count = SIZE * SIZE * SIZE;
        float[][] fields = new float[4][count];
        for (int c = 0; c < 4; c++) {
            int period = PERIODS[c];
            float cell = SIZE / (float) period;
            float[] field = fields[c];
            for (int z = 0; z < SIZE; z++) {
                for (int y = 0; y < SIZE; y++) {
                    for (int x = 0; x < SIZE; x++) {
                        field[(z * SIZE + y) * SIZE + x] = gradient((x + 0.5F) / cell, (y + 0.5F) / cell, (z + 0.5F) / cell, period, SEEDS[c]);
                    }
                }
            }
            float[] sorted = field.clone();
            Arrays.sort(sorted);
            float lo = sorted[(int) (count * 0.005F)];
            float hi = sorted[(int) (count * 0.995F)];
            for (int i = 0; i < count; i++) field[i] = Mth.clamp((field[i] - lo) / (hi - lo), 0F, 1F);
        }
        ByteBuffer voxels = BufferUtils.createByteBuffer(count * 4);
        for (int i = 0; i < count; i++) {
            for (int c = 0; c < 4; c++) voxels.put((byte) Math.round(fields[c][i] * 255F));
        }
        voxels.flip();
        nebula = GlStateManager._genTexture();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, nebula);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT);
        SkyHeights.unpack();
        GlStateManager._pixelStore(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
        GlStateManager._pixelStore(GL12.GL_UNPACK_SKIP_IMAGES, 0);
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGBA8, SIZE, SIZE, SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, voxels);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        return nebula;
    }

    private static float gradient(float x, float y, float z, int period, int seed) {
        int x0 = (int) x, y0 = (int) y, z0 = (int) z;
        float fx = x - x0, fy = y - y0, fz = z - z0;
        float u = fade(fx), v = fade(fy), w = fade(fz);
        float sum = 0F;
        for (int dz = 0; dz < 2; dz++) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dx = 0; dx < 2; dx++) {
                    int[] g = GRADIENTS[hash((x0 + dx) % period, (y0 + dy) % period, (z0 + dz) % period, seed)];
                    float dot = g[0] * (fx - dx) + g[1] * (fy - dy) + g[2] * (fz - dz);
                    sum += dot * (dx == 1 ? u : 1F - u) * (dy == 1 ? v : 1F - v) * (dz == 1 ? w : 1F - w);
                }
            }
        }
        return sum;
    }

    private static int hash(int x, int y, int z, int seed) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return h & 15;
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6F - 15F) + 10F);
    }
}
