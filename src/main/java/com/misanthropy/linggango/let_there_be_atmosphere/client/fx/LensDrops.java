package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.CaptureHook;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
public final class LensDrops {
    private LensDrops() {}

    private static final ResourceLocation SHADER = new ResourceLocation(Let_there_be_atmosphere.MODID, "lens_drops");
    private static final int MAX = 64;
    private static final float RATE = 10F;
    private static final float SLIDE_RADIUS = 0.0185F;
    private static final float[] X = new float[MAX];
    private static final float[] Y = new float[MAX];
    private static final float[] R = new float[MAX];
    private static final float[] AGE = new float[MAX];
    private static final float[] LIFE = new float[MAX];
    private static final float[] SPEED = new float[MAX];
    private static final float[] TILT = new float[MAX];
    private static final float[] TRAIL = new float[MAX];
    private static final float[] TRACE = new float[MAX];
    private static final float[] SEED = new float[MAX];
    private static final float[] RUN = new float[MAX];
    private static final boolean[] ALIVE = new boolean[MAX];
    private static final boolean[] SLIDING = new boolean[MAX];
    private static final boolean[] DRYING = new boolean[MAX];
    private static final FloatBuffer DROPS = BufferUtils.createFloatBuffer(MAX * 4);
    private static final FloatBuffer FLOW = BufferUtils.createFloatBuffer(MAX * 4);
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();

    private static ShaderInstance shader;
    private static int dropsUniform;
    private static int flowUniform;
    private static int screenUniform;
    private static TextureTarget copy;
    private static int vao;
    private static boolean loadFailed;
    private static long last;
    private static float carry;
    private static float aspect = 1F;
    private static int count;

    public static void apply() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (!FxConfig.lensDrops || level == null || player == null) {
            Diagnostics.skip(Diagnostics.LENS_DROPS, "disabled");
            clear();
            return;
        }
        if (!DynamicFogHandler.active() || ColorGrade.shaderPackActive()) {
            Diagnostics.skip(Diagnostics.LENS_DROPS, "a shader pack owns the image");
            clear();
            return;
        }
        if (!level.dimensionType().hasSkyLight() || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            Diagnostics.skip(Diagnostics.LENS_DROPS, "no rain in this dimension");
            clear();
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        aspect = main.height > 0 ? (float) main.width / main.height : 1F;
        long now = Util.getNanos();
        float dt = last == 0L ? 0F : Math.min((now - last) / 1.0E9F, 0.1F);
        last = now;
        if (!mc.isPaused()) step(mc, level, player, dt);
        if (count == 0) {
            Diagnostics.skip(Diagnostics.LENS_DROPS, "no drops");
            return;
        }
        if (shader == null && !loadFailed) load();
        if (shader == null) {
            Diagnostics.skip(Diagnostics.LENS_DROPS, "shader failed to load, see the log");
            return;
        }
        draw(main);
        Diagnostics.mark(Diagnostics.LENS_DROPS, Diagnostics.detail() ? count + " drops" : null);
    }

    private static void step(Minecraft mc, ClientLevel level, LocalPlayer player, float dt) {
        Camera camera = mc.gameRenderer.getMainCamera();
        boolean submerged = camera.getFluidInCamera() != FogType.NONE;
        CURSOR.set(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        float rain = level.getBiome(CURSOR).value().getPrecipitationAt(CURSOR) == Biome.Precipitation.RAIN ? level.getRainLevel(1F) : 0F;
        CURSOR.set(camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
        boolean wet = !submerged && rain > 0.05F && level.canSeeSky(CURSOR);
        float up = -camera.getXRot();
        float look = up >= 0F ? 0.12F + 0.88F * (float) Math.pow(Math.min(up / 90F, 1F), 0.8) : Math.max(0F, 0.12F * (1F + up / 18F));
        if (wet) carry = Math.min(carry + RATE * rain * look * (float) FxConfig.lensDropsAmount * dt, 4F);
        while (carry >= 1F) {
            carry -= 1F;
            spawn();
        }

        count = 0;
        for (int i = 0; i < MAX; i++) {
            if (!ALIVE[i]) continue;
            if (!wet) {
                if (!DRYING[i]) {
                    DRYING[i] = true;
                    LIFE[i] = Math.min(LIFE[i], AGE[i] + (submerged ? 0.35F : 1.8F + SEED[i] * 3F));
                }
            } else {
                DRYING[i] = false;
            }
            AGE[i] += dt;
            if (AGE[i] >= LIFE[i]) {
                ALIVE[i] = false;
                continue;
            }
            if (!SLIDING[i] && R[i] > SLIDE_RADIUS && AGE[i] > 0.4F + SEED[i]) SLIDING[i] = true;
            if (SLIDING[i]) slide(i, dt);
            if (Y[i] < -R[i] * 2F) ALIVE[i] = false;
        }
        merge();
        for (int i = 0; i < MAX; i++) if (ALIVE[i]) count++;
    }

    private static void slide(int i, float dt) {
        float top = Mth.clamp((R[i] - 0.0155F) * 11F, 0F, 0.2F);
        float s = Mth.clamp(0.5F + 0.9F * (float) Math.sin(AGE[i] * 2.7F + SEED[i] * 50F), 0F, 1F);
        float target = top * s * s * (3F - 2F * s);
        SPEED[i] += (target - SPEED[i]) * Math.min(1F, dt * 4F);
        float step = SPEED[i] * dt;
        X[i] -= (float) Math.sin(TILT[i]) * step / aspect;
        Y[i] -= (float) Math.cos(TILT[i]) * step;
        R[i] = Math.max(0.0085F, R[i] - step * 0.02F);
        TRAIL[i] = Math.min(TRAIL[i] + step, 0.15F);
        TRACE[i] = SPEED[i] > 0.012F ? Math.min(1F, TRACE[i] + dt * 4F) : Math.max(0F, TRACE[i] - dt * 0.3F);
        RUN[i] += step;
        if (RUN[i] > 0.075F) {
            RUN[i] = 0F;
            int bead = free();
            if (bead >= 0) {
                birth(bead, X[i] + (float) Math.sin(TILT[i]) * R[i] * 1.4F / aspect, Y[i] + R[i] * 1.3F, 0.0035F + RANDOM.nextFloat() * 0.003F, 2.5F + RANDOM.nextFloat() * 2.5F);
            }
        }
    }

    private static void merge() {
        for (int i = 0; i < MAX; i++) {
            if (!ALIVE[i]) continue;
            for (int j = i + 1; j < MAX; j++) {
                if (!ALIVE[j]) continue;
                float dx = (X[i] - X[j]) * aspect;
                float dy = Y[i] - Y[j];
                float reach = (R[i] + R[j]) * 0.85F;
                if (dx * dx + dy * dy > reach * reach) continue;
                int big = R[i] >= R[j] ? i : j;
                int small = big == i ? j : i;
                float a = R[big] * R[big];
                float b = R[small] * R[small];
                X[big] = (X[big] * a + X[small] * b) / (a + b);
                Y[big] = (Y[big] * a + Y[small] * b) / (a + b);
                R[big] = (float) Math.sqrt(a + b);
                LIFE[big] = Math.max(LIFE[big], AGE[big] + Math.min(LIFE[small] - AGE[small], 6F));
                ALIVE[small] = false;
                if (R[big] > SLIDE_RADIUS && !SLIDING[big]) {
                    SLIDING[big] = true;
                    SPEED[big] = 0.02F;
                }
                if (small == i) break;
            }
        }
    }

    private static int free() {
        for (int i = 0; i < MAX; i++) if (!ALIVE[i]) return i;
        return -1;
    }

    private static void spawn() {
        int i = free();
        if (i < 0) return;
        float roll = RANDOM.nextFloat();
        float r = roll > 0.9F ? 0.026F + RANDOM.nextFloat() * 0.012F : 0.009F + 0.014F * roll * roll;
        birth(i, 0.02F + RANDOM.nextFloat() * 0.96F, 0.03F + RANDOM.nextFloat() * 0.97F, r, 6F + RANDOM.nextFloat() * 5F);
    }

    private static void birth(int i, float x, float y, float r, float life) {
        ALIVE[i] = true;
        SLIDING[i] = false;
        DRYING[i] = false;
        X[i] = x;
        Y[i] = y;
        R[i] = r;
        AGE[i] = 0F;
        LIFE[i] = life;
        SPEED[i] = 0F;
        TILT[i] = (RANDOM.nextFloat() - 0.5F) * 0.24F;
        TRAIL[i] = 0F;
        TRACE[i] = 0F;
        SEED[i] = RANDOM.nextFloat();
        RUN[i] = 0F;
    }

    private static void clear() {
        if (count == 0) return;
        for (int i = 0; i < MAX; i++) ALIVE[i] = false;
        count = 0;
        carry = 0F;
    }

    private static void draw(RenderTarget main) {
        int width = main.width;
        int height = main.height;
        copy = fit(copy, width, height);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        DROPS.clear();
        FLOW.clear();
        int n = 0;
        for (int i = 0; i < MAX; i++) {
            if (!ALIVE[i]) continue;
            float in = Mth.clamp(AGE[i] / 0.3F, 0F, 1F);
            float out = Mth.clamp((LIFE[i] - AGE[i]) / 1.4F, 0F, 1F);
            float fade = in * out * out * (3F - 2F * out);
            float size = (0.75F + 0.25F * in) * (0.8F + 0.2F * out);
            DROPS.put(X[i]).put(Y[i]).put(R[i] * size).put(fade);
            FLOW.put(Mth.clamp(SPEED[i] * 6F, 0F, 1F)).put(TRAIL[i]).put(TILT[i]).put(TRACE[i]);
            n++;
        }
        DROPS.flip();
        FLOW.flip();

        CaptureHook.probeBegin("lens_drops");
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        if (vao == 0) vao = GlStateManager._glGenVertexArrays();
        GlStateManager._glBindVertexArray(vao);
        main.bindWrite(true);
        ProgramManager.glUseProgram(shader.getId());
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(copy.getColorTextureId());
        GL20.glUniform4fv(dropsUniform, DROPS);
        GL20.glUniform4fv(flowUniform, FLOW);
        GL20.glUniform4f(screenUniform, aspect, 1F / height, n, 0F);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GlStateManager._bindTexture(0);
        shader.clear();
        BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(0);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        CaptureHook.probeEnd("lens_drops");
    }

    private static TextureTarget fit(TextureTarget target, int width, int height) {
        if (target == null) {
            target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        } else {
            return target;
        }
        target.setFilterMode(GL11.GL_LINEAR);
        return target;
    }

    private static void load() {
        try {
            ShaderInstance loaded = new ShaderInstance(Minecraft.getInstance().getResourceManager(), SHADER, DefaultVertexFormat.POSITION);
            int id = loaded.getId();
            dropsUniform = GlStateManager._glGetUniformLocation(id, "Drops");
            flowUniform = GlStateManager._glGetUniformLocation(id, "Flow");
            screenUniform = GlStateManager._glGetUniformLocation(id, "Screen");
            ProgramManager.glUseProgram(id);
            GlStateManager._glUniform1i(GlStateManager._glGetUniformLocation(id, "DiffuseSampler"), 0);
            loaded.clear();
            shader = loaded;
        } catch (IOException | RuntimeException e) {
            loadFailed = true;
            Diagnostics.LOG.error("[atmosphere] screen drops shader failed to load", e);
        }
    }
}
