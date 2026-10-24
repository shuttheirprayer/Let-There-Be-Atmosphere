package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class Butterflies {
    private Butterflies() {}

    private static final int SLOTS = 48;
    private static final int RADIUS = 20;
    private static final double REACH = 1024.0;
    private static final ResourceLocation SHEET = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/butterfly.png");

    private static final float[][] ROW = new float[2][SLOTS * 4];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(SLOTS * 4);
    private static final float[] PUSH_X = new float[SLOTS];
    private static final float[] PUSH_Z = new float[SLOTS];
    private static long driftClock = Util.getNanos();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Matrix4f MATRIX = new Matrix4f();

    private static long base = Util.getNanos();
    private static int data;
    private static boolean dirty = true;
    private static boolean anchored;
    private static double anchorX, anchorY, anchorZ;
    private static ClientLevel level;
    private static int next;
    private static float budget;
    private static float lastDeath = -100F;

    private static float now() {
        return ((Util.getNanos() - base) / 1.0E9F) % 3600F;
    }

    public static void onButterflyTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        LocalPlayer player = mc.player;
        if (current == null || player == null || !FxConfig.butterflies || FxConfig.butterfliesAmount <= 0.0
                || !DynamicFogHandler.active() || !current.dimensionType().hasSkyLight()) {
            if (anchored) reset();
            Diagnostics.skip(Diagnostics.BUTTERFLIES, "disabled or no sky here");
            return;
        }
        if (mc.isPaused()) return;
        if (current != level) {
            level = current;
            reset();
        }
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        if (!anchored || Math.abs(px - anchorX) > REACH || Math.abs(pz - anchorZ) > REACH || Math.abs(py - anchorY) > REACH) {
            reset();
            anchorX = Math.floor(px);
            anchorY = Math.floor(py);
            anchorZ = Math.floor(pz);
            anchored = true;
        }
        float angle = level.getTimeOfDay(1F) * Mth.TWO_PI;
        float day = SkyGlow.smooth(0.1F, 0.35F, Mth.cos(angle)) * (1F - level.getRainLevel(1F));
        if (day < 0.01F) {
            Diagnostics.skip(Diagnostics.BUTTERFLIES, "not day or raining");
            return;
        }
        budget += day * 0.25F * (float) FxConfig.butterfliesAmount;
        while (budget >= 1F) {
            budget -= 1F;
            spawn(player.getBlockX(), player.getBlockZ());
        }
        Diagnostics.mark(Diagnostics.BUTTERFLIES, null);
    }

    private static void spawn(int px, int pz) {
        int x = px + RANDOM.nextInt(RADIUS * 2 + 1) - RADIUS;
        int z = pz + RANDOM.nextInt(RADIUS * 2 + 1) - RADIUS;
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        CURSOR.set(x, top, z);
        boolean flower = false;
        for (int i = 0; i < 3 && !flower; i++) {
            flower = level.getBlockState(CURSOR).is(BlockTags.FLOWERS);
            if (!flower) CURSOR.move(0, -1, 0);
        }
        if (!flower) return;
        Biome biome = level.getBiome(CURSOR).value();
        float temperature = biome.getBaseTemperature();
        if (temperature < 0.4F || temperature > 1.6F) return;
        if (level.getBrightness(LightLayer.SKY, CURSOR) < 12) return;
        float time = now();
        int o = next * 4;
        if (time - ROW[0][o + 3] < ROW[1][o + 2]) return;
        ROW[0][o] = (float) (x + 0.5 - anchorX);
        ROW[0][o + 1] = (float) (CURSOR.getY() + 0.3 - anchorY);
        ROW[0][o + 2] = (float) (z + 0.5 - anchorZ);
        ROW[0][o + 3] = time;
        ROW[1][o] = RANDOM.nextInt(4) + RANDOM.nextFloat() * 0.98F;
        ROW[1][o + 1] = level.getBrightness(LightLayer.BLOCK, CURSOR) * 16 + level.getBrightness(LightLayer.SKY, CURSOR);
        ROW[1][o + 2] = 18F + RANDOM.nextFloat() * 22F;
        PUSH_X[next] = 0F;
        PUSH_Z[next] = 0F;
        next = (next + 1) % SLOTS;
        lastDeath = Math.max(lastDeath, time + ROW[1][o + 2]);
        dirty = true;
    }

    private static void reset() {
        for (float[] row : ROW) java.util.Arrays.fill(row, 0F);
        for (int i = 0; i < SLOTS; i++) ROW[0][i * 4 + 3] = -1000F;
        anchored = false;
        lastDeath = -100F;
        dirty = true;
    }

    public static void onRenderButterflyStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!anchored || LightTint.lightmap == null) return;
        float time = now();
        if (time > lastDeath || time < lastDeath - 100F) return;
        SkyProgram program = SkyProgram.BUTTERFLY;
        if (!program.ready()) return;

        Vec3 eye = event.getCamera().getPosition();
        drift(event.getPartialTick(), eye.y, time);
        upload();
        float ox = (float) (anchorX - eye.x);
        float oy = (float) (anchorY - eye.y);
        float oz = (float) (anchorZ - eye.z);
        MATRIX.set(event.getPoseStack().last().pose()).translate(ox, oy, oz);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        program.begin();
        SkyProgram.texture(2, data);
        SkyProgram.texture(1, LightTint.lightmap.getId());
        SkyProgram.texture(0, Minecraft.getInstance().getTextureManager().getTexture(SHEET).getId());
        program.transform(0, MATRIX);
        GL20.glUniform3f(program.uniform(1), ox, oy, oz);
        GL20.glUniform4f(program.uniform(2), time, RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), 0F);
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.uniform(3), fog[0], fog[1], fog[2], 1F);
        program.draw(SLOTS * 12);
        program.end();
        RenderSystem.enableCull();
    }

    private static void drift(float partialTick, double eyeY, float time) {
        long nanos = Util.getNanos();
        float dt = Mth.clamp((nanos - driftClock) / 1.0E9F, 0F, 0.1F);
        driftClock = nanos;
        if (level == null || dt <= 0F || FxConfig.windStrength <= 0.0) return;
        WindParticles.sampleDrift(level, partialTick, eyeY);
        float ease = 1F - (float) Math.exp(-dt * 0.8F);
        for (int i = 0; i < SLOTS; i++) {
            int o = i * 4;
            float age = time - ROW[0][o + 3];
            if (ROW[1][o + 2] <= 0F || age < 0F || age > ROW[1][o + 2]) continue;
            float open = WindParticles.sky((int) ROW[1][o + 1]) * 0.12F;
            PUSH_X[i] += (WindParticles.driftX * open - PUSH_X[i]) * ease;
            PUSH_Z[i] += (WindParticles.driftZ * open - PUSH_Z[i]) * ease;
            ROW[0][o] += PUSH_X[i] * dt;
            ROW[0][o + 2] += PUSH_Z[i] * dt;
            dirty = true;
        }
    }

    private static void upload() {
        if (data == 0) {
            data = GlStateManager._genTexture();
            GlStateManager._bindTexture(data);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            SkyHeights.unpack();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, SLOTS, 2, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            dirty = true;
        }
        if (!dirty) return;
        GlStateManager._bindTexture(data);
        SkyHeights.unpack();
        for (int row = 0; row < 2; row++) {
            UPLOAD.clear();
            UPLOAD.put(ROW[row]).flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, row, SLOTS, 1, GL11.GL_RGBA, GL11.GL_FLOAT, UPLOAD);
        }
        dirty = false;
    }
}
