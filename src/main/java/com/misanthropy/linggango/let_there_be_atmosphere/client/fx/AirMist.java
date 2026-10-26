package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class AirMist {
    private AirMist() {}

    private static final int SLOTS = 96;
    private static final int BASE_COUNT = 24;
    private static final int TRIES = 4;
    private static final double NEAR = 12.0;
    private static final double FAR = 56.0;
    private static final double LOST = 72.0;
    private static final int MIN_SKY = 13;
    private static final ResourceLocation SHEET = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/air_mist.png");
    private static final double[] X = new double[SLOTS];
    private static final double[] Y = new double[SLOTS];
    private static final double[] Z = new double[SLOTS];
    private static final float[] BORN = new float[SLOTS];
    private static final float[] LIFE = new float[SLOTS];
    private static final float[] SIZE = new float[SLOTS];
    private static final float[] SEED = new float[SLOTS];
    private static final float[] MEANDER_X = new float[SLOTS];
    private static final float[] MEANDER_Z = new float[SLOTS];
    private static final float[] WISPS = new float[SLOTS * 8];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(SLOTS * 8);
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();

    private static final long BASE = Util.getNanos();
    private static long simClock = Util.getNanos();
    private static float pushX, pushZ;
    private static int alive;

    private static float now() {
        return (Util.getNanos() - BASE) / 1.0E9F;
    }

    private static boolean allowed(ClientLevel level) {
        return FxConfig.airMist && FxConfig.airMistAmount > 0.0 && DynamicFogHandler.active() && level.dimension() == Level.OVERWORLD;
    }

    public static void onAirMistTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || !allowed(level)) {
            if (alive > 0) java.util.Arrays.fill(LIFE, 0F);
            alive = 0;
            Diagnostics.skip(Diagnostics.AIR_MIST, "disabled or not the overworld");
            return;
        }
        if (mc.isPaused()) return;
        float now = now();
        int count = 0;
        int free = -1;
        for (int i = 0; i < SLOTS; i++) {
            float age = now - BORN[i];
            if (LIFE[i] <= 0F || age > LIFE[i]) {
                LIFE[i] = 0F;
                if (free < 0) free = i;
                continue;
            }
            double dx = X[i] - player.getX(), dz = Z[i] - player.getZ();
            if (dx * dx + dz * dz > LOST * LOST) {
                LIFE[i] = 0F;
                continue;
            }
            count++;
            CURSOR.set(X[i], Y[i], Z[i]);
            if (age < LIFE[i] - 1.5F && (!level.isEmptyBlock(CURSOR) || level.getBrightness(LightLayer.SKY, CURSOR) < MIN_SKY - 2)) {
                LIFE[i] = age + 1.5F;
            }
        }
        int target = Math.min(SLOTS, (int) Math.round(BASE_COUNT * FxConfig.airMistAmount));
        for (int t = 0; t < TRIES && count < target && free >= 0; t++) {
            if (spawn(level, player, free, now)) {
                count++;
                free = nextFree(free + 1, now);
            }
        }
        alive = count;
        Diagnostics.mark(Diagnostics.AIR_MIST, Diagnostics.detail() ? count + " wisps" : null);
    }

    private static int nextFree(int from, float now) {
        for (int i = from; i < SLOTS; i++) if (LIFE[i] <= 0F || now - BORN[i] > LIFE[i]) return i;
        return -1;
    }

    private static boolean spawn(ClientLevel level, LocalPlayer player, int slot, float now) {
        float angle = RANDOM.nextFloat() * Mth.TWO_PI;
        double dist = Math.sqrt(NEAR * NEAR + RANDOM.nextFloat() * (FAR * FAR - NEAR * NEAR));
        double x = player.getX() + Mth.cos(angle) * dist;
        double z = player.getZ() + Mth.sin(angle) * dist;
        double y = player.getEyeY() - 3.0 + RANDOM.nextFloat() * 19.0;
        CURSOR.set(x, y, z);
        if (!level.hasChunkAt(CURSOR) || !level.isEmptyBlock(CURSOR) || level.getBrightness(LightLayer.SKY, CURSOR) < MIN_SKY) return false;
        if (!level.isEmptyBlock(CURSOR.move(1, 0, 0)) || !level.isEmptyBlock(CURSOR.move(-2, 0, 0))
                || !level.isEmptyBlock(CURSOR.move(1, 0, 1)) || !level.isEmptyBlock(CURSOR.move(0, 0, -2))
                || !level.isEmptyBlock(CURSOR.move(0, -1, 1)) || !level.isEmptyBlock(CURSOR.move(0, -2, 0))) return false;
        float size = 4.0F + RANDOM.nextFloat() * 2.5F;
        double reach = size * 0.75;
        double floor = y - size * 0.4;
        if (buried(level, x, z, floor) || buried(level, x + reach, z, floor) || buried(level, x - reach, z, floor)
                || buried(level, x, z + reach, floor) || buried(level, x, z - reach, floor)) return false;
        X[slot] = x;
        Y[slot] = y;
        Z[slot] = z;
        BORN[slot] = now;
        LIFE[slot] = 14F + RANDOM.nextFloat() * 12F;
        SIZE[slot] = size;
        SEED[slot] = RANDOM.nextFloat();
        float turn = RANDOM.nextFloat() * Mth.TWO_PI;
        float pace = 0.04F + RANDOM.nextFloat() * 0.08F;
        MEANDER_X[slot] = Mth.cos(turn) * pace;
        MEANDER_Z[slot] = Mth.sin(turn) * pace;
        return true;
    }

    public static void onRenderAirMistStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || alive <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !allowed(level)) return;
        Camera camera = event.getCamera();
        if (CameraFluid.of(camera) != FogType.NONE) return;
        SkyProgram program = SkyProgram.AIR_MIST;
        if (!program.ready()) return;

        Vec3 eye = camera.getPosition();
        drift(level, event.getPartialTick(), eye.y);
        float now = now();
        int drawn = 0;
        for (int i = 0; i < SLOTS; i++) {
            float age = now - BORN[i];
            if (LIFE[i] <= 0F || age < 0F || age > LIFE[i]) continue;
            float f = age / LIFE[i];
            float fade = smooth(0F, 0.3F, f) * (1F - smooth(0.65F, 1F, f));
            int o = drawn * 8;
            WISPS[o] = (float) (X[i] - eye.x);
            WISPS[o + 1] = (float) (Y[i] - eye.y) + 0.15F * Mth.sin(age * 0.5F + SEED[i] * Mth.TWO_PI);
            WISPS[o + 2] = (float) (Z[i] - eye.z);
            WISPS[o + 3] = SIZE[i];
            WISPS[o + 4] = fade;
            WISPS[o + 5] = SEED[i];
            WISPS[o + 6] = 0F;
            WISPS[o + 7] = 0F;
            drawn++;
        }
        if (drawn == 0) return;

        MATRIX.set(event.getPoseStack().last().pose());
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        float day = Mth.clamp((level.getSkyDarken(event.getPartialTick()) - 0.2F) / 0.8F, 0F, 1F);
        float[] fog = RenderSystem.getShaderFogColor();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        program.begin();
        SkyProgram.texture(0, SkyProgram.texture(SHEET));
        program.transform(0, MATRIX);
        GL20.glUniform3f(program.uniform(1), -left.x(), -left.y(), -left.z());
        GL20.glUniform3f(program.uniform(2), up.x(), up.y(), up.z());
        GL20.glUniform4f(program.uniform(3), RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), day, (float) FAR);
        GL20.glUniform4f(program.uniform(4), fog[0], fog[1], fog[2], now);
        UPLOAD.clear();
        UPLOAD.put(WISPS, 0, drawn * 8).flip();
        GL20.glUniform4fv(program.uniform(5), UPLOAD);
        program.draw(drawn * 6);
        program.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void drift(ClientLevel level, float partialTick, double eyeY) {
        long nanos = Util.getNanos();
        float dt = Mth.clamp((nanos - simClock) / 1.0E9F, 0F, 0.1F);
        simClock = nanos;
        if (dt <= 0F) return;
        WindParticles.sampleDrift(level, partialTick, eyeY);
        float ease = 1F - (float) Math.exp(-dt * 0.8F);
        pushX += (WindParticles.driftX * 0.35F - pushX) * ease;
        pushZ += (WindParticles.driftZ * 0.35F - pushZ) * ease;
        for (int i = 0; i < SLOTS; i++) {
            if (LIFE[i] <= 0F) continue;
            X[i] += (pushX + MEANDER_X[i]) * dt;
            Z[i] += (pushZ + MEANDER_Z[i]) * dt;
        }
    }

    private static boolean buried(ClientLevel level, double x, double z, double floor) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) > floor;
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0F, 1F);
        return t * t * (3F - 2F * t);
    }
}
