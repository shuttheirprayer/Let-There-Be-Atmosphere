package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.util.RandomSource;
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
public final class WaveSplash {
    private WaveSplash() {}

    private static final int CROWNS = 24;
    private static final int DROPS = 192;
    private static final float GRAVITY = 14F;
    private static final float CROWN_LIFE = 0.6F;
    private static final float DROP_LIFE_MAX = 1.2F;

    private static final float[][] CROWN = new float[3][CROWNS * 4];
    private static final float[][] DROP = new float[3][DROPS * 4];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(DROPS * 4);
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final Vector3f RIGHT = new Vector3f();
    private static final Vector3f UP = new Vector3f();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final long BASE = Util.getNanos();

    private static int crownTex, dropTex;
    private static boolean dirty = true;
    private static int nextCrown, nextDrop;
    private static float newest = -100F;

    private static float now() {
        return ((Util.getNanos() - BASE) / 1.0E9F) % 3600F;
    }

    static void reset() {
        for (float[] row : CROWN) java.util.Arrays.fill(row, 0F);
        for (float[] row : DROP) java.util.Arrays.fill(row, 0F);
        for (int i = 0; i < CROWNS; i++) CROWN[0][i * 4 + 3] = -1000F;
        for (int i = 0; i < DROPS; i++) DROP[0][i * 4 + 3] = -1000F;
        newest = -100F;
        dirty = true;
    }

    static {
        reset();
    }

    static void emit(double x, double surfaceY, double z, float width, float power) {
        if (!WaveField.ready() || !FxConfig.ripples) return;
        float time = now();
        float p = Math.min(Math.max(power, 0F), 1F);
        int light = WaveField.packedLight(x, surfaceY, z);
        int tint = WaveField.waterTint(x, surfaceY, z);
        float rx = (float) (x - WaveField.anchorX());
        float ry = (float) (surfaceY - WaveField.anchorY());
        float rz = (float) (z - WaveField.anchorZ());

        int c = nextCrown * 4;
        CROWN[0][c] = rx;
        CROWN[0][c + 1] = ry;
        CROWN[0][c + 2] = rz;
        CROWN[0][c + 3] = time;
        CROWN[1][c] = Math.min(0.5F + width * 0.9F, 1.6F);
        CROWN[1][c + 1] = 0.3F + 0.6F * p;
        CROWN[1][c + 2] = light;
        CROWN[1][c + 3] = tint;
        CROWN[2][c] = RANDOM.nextFloat() * 50F;
        CROWN[2][c + 1] = RANDOM.nextFloat() * 6.2832F;
        nextCrown = (nextCrown + 1) % CROWNS;

        int drops = 4 + (int) (p * 7F);
        for (int i = 0; i < drops; i++) {
            int d = nextDrop * 4;
            float angle = RANDOM.nextFloat() * 6.2832F;
            float out = (0.35F + RANDOM.nextFloat() * 0.9F) * (0.6F + 0.7F * p) * (0.7F + width * 0.4F);
            float up = 2.4F + RANDOM.nextFloat() * 1.1F + 1.6F * p;
            DROP[0][d] = rx + (float) Math.cos(angle) * width * 0.25F;
            DROP[0][d + 1] = ry;
            DROP[0][d + 2] = rz + (float) Math.sin(angle) * width * 0.25F;
            DROP[0][d + 3] = time + RANDOM.nextFloat() * 0.04F;
            DROP[1][d] = (float) Math.cos(angle) * out;
            DROP[1][d + 1] = up;
            DROP[1][d + 2] = (float) Math.sin(angle) * out;
            DROP[1][d + 3] = RANDOM.nextBoolean() ? 0.0625F : 0.09F;
            DROP[2][d] = light;
            DROP[2][d + 1] = tint;
            DROP[2][d + 2] = Math.min(2F * up / GRAVITY, DROP_LIFE_MAX);
            nextDrop = (nextDrop + 1) % DROPS;
        }
        newest = time;
        dirty = true;
    }

    public static void onRenderSplashStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!WaveField.ready() || LightTint.lightmap == null) return;
        float time = now();
        if (time < newest || time - newest > DROP_LIFE_MAX + 0.2F) return;
        SkyProgram program = SkyProgram.WAKE;
        if (!program.ready()) return;

        upload();
        Vec3 eye = event.getCamera().getPosition();
        float ox = (float) (WaveField.anchorX() - eye.x);
        float oy = (float) (WaveField.anchorY() - eye.y);
        float oz = (float) (WaveField.anchorZ() - eye.z);
        MATRIX.set(event.getPoseStack().last().pose()).translate(ox, oy, oz);
        RIGHT.set(event.getCamera().getLeftVector()).negate();
        UP.set(event.getCamera().getUpVector());

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        program.begin();
        SkyProgram.texture(0, LightTint.lightmap.getId());
        program.transform(0, MATRIX);
        GL20.glUniform3f(program.uniform(1), ox, oy, oz);
        GL20.glUniform4f(program.uniform(2), time, RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), 1F);
        GL20.glUniform3f(program.uniform(4), RIGHT.x, RIGHT.y, RIGHT.z);
        GL20.glUniform3f(program.uniform(5), UP.x, UP.y, UP.z);
        float[] fog = RenderSystem.getShaderFogColor();
        SkyProgram.texture(1, crownTex);
        GL20.glUniform4f(program.uniform(3), fog[0], fog[1], fog[2], 0F);
        program.draw(CROWNS * 12);
        SkyProgram.texture(1, dropTex);
        GL20.glUniform4f(program.uniform(3), fog[0], fog[1], fog[2], 1F);
        program.draw(DROPS * 6);
        program.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static int create(int width) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        SkyHeights.unpack();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, width, 3, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
        return id;
    }

    private static void fill(int id, float[][] rows, int width) {
        GlStateManager._bindTexture(id);
        SkyHeights.unpack();
        for (int row = 0; row < 3; row++) {
            UPLOAD.clear();
            UPLOAD.put(rows[row], 0, width * 4).flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, row, width, 1, GL11.GL_RGBA, GL11.GL_FLOAT, UPLOAD);
        }
    }

    private static void upload() {
        if (crownTex == 0) {
            crownTex = create(CROWNS);
            dropTex = create(DROPS);
            dirty = true;
        }
        if (!dirty) return;
        fill(crownTex, CROWN, CROWNS);
        fill(dropTex, DROP, DROPS);
        dirty = false;
    }
}
