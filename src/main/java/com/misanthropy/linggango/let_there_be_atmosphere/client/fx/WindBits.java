package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeFx;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
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
public final class WindBits {
    private WindBits() {}

    private static final int FLECK = 0;
    private static final int LEAF = 1;
    private static final int GRASS = 2;
    private static final int STREAK = 3;

    private static final int SLOTS = 256;
    private static final int ROWS = 4;
    private static final int RADIUS = 14;
    private static final int PROBES = 10;
    private static final float REBASE = 1800F;
    private static final double REACH = 2048.0;
    private static final float[] ROW_A = new float[SLOTS * 4];
    private static final float[] ROW_B = new float[SLOTS * 4];
    private static final float[] ROW_C = new float[SLOTS * 4];
    private static final float[] ROW_D = new float[SLOTS * 4];
    private static final float[] DRAG = {1.0F, 0.85F, 0.7F, 1.0F};
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(SLOTS * 4);
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();

    private static long base = Util.getNanos();
    private static int head;
    private static int dirtyFrom = -1;
    private static int dirtyCount;
    private static boolean drifted;
    private static boolean anchored;
    private static double anchorX, anchorY, anchorZ;
    private static float lastBirth = -100F;
    private static int data;
    private static float budget;
    private static int born;
    private static float windX = 1F, windZ, windLevel;
    private static long simClock = Util.getNanos();

    private static float now() {
        return (Util.getNanos() - base) / 1.0E9F;
    }

    private static float speedOf(int kind) {
        float w = Math.min(windLevel, 1.2F);
        return kind == STREAK ? 9F + 9F * w : (1.6F + 4.2F * w) * DRAG[kind];
    }

    private static void emit(int kind, double x, double y, double z, int tint, int packed, float life, float mult) {
        if (!anchored || Math.abs(x - anchorX) > REACH || Math.abs(z - anchorZ) > REACH || Math.abs(y - anchorY) > REACH) {
            clear();
            anchorX = Math.floor(x);
            anchorY = Math.floor(y);
            anchorZ = Math.floor(z);
            anchored = true;
        }
        float now = now();
        if (now > REBASE) rebase(now);
        int slot = head;
        head = (head + 1) % SLOTS;
        int o = slot * 4;
        ROW_A[o] = (float) (x - anchorX);
        ROW_A[o + 1] = (float) (y - anchorY);
        ROW_A[o + 2] = (float) (z - anchorZ);
        ROW_A[o + 3] = now;
        ROW_B[o] = kind;
        ROW_B[o + 1] = RANDOM.nextFloat();
        ROW_B[o + 2] = packed;
        ROW_B[o + 3] = life;
        ROW_C[o] = (tint >> 16 & 255) / 255F;
        ROW_C[o + 1] = (tint >> 8 & 255) / 255F;
        ROW_C[o + 2] = (tint & 255) / 255F;
        ROW_C[o + 3] = mult;
        float launch = speedOf(kind) * mult;
        ROW_D[o] = windX * launch;
        ROW_D[o + 1] = 0F;
        ROW_D[o + 2] = windZ * launch;
        ROW_D[o + 3] = 0F;
        if (dirtyFrom < 0) {
            dirtyFrom = slot;
            dirtyCount = 1;
        } else {
            dirtyCount = Math.min(dirtyCount + 1, SLOTS);
        }
        lastBirth = now;
        born++;
    }

    private static void clear() {
        java.util.Arrays.fill(ROW_B, 0F);
        java.util.Arrays.fill(ROW_D, 0F);
        dirtyFrom = 0;
        dirtyCount = SLOTS;
    }

    private static void rebase(float now) {
        for (int i = 0; i < SLOTS; i++) ROW_A[i * 4 + 3] -= now;
        lastBirth -= now;
        base = Util.getNanos();
        dirtyFrom = 0;
        dirtyCount = SLOTS;
    }

    static void tick(ClientLevel level, LocalPlayer player, float seconds) {
        boolean bits = BiomeFx.on(FxConfig.windBits, BiomeFx.WIND_BITS) && BiomeFx.amount(FxConfig.windBitsAmount, BiomeFx.WIND_BITS) > 0.0;
        boolean streaks = FxConfig.windStreaks;
        if (!bits && !streaks) {
            Diagnostics.skip(Diagnostics.WINDBITS, "disabled");
            return;
        }
        float w = Wind.level(seconds) * Wind.altitude(player.getY()) * (float) FxConfig.windStrength;
        windX = Wind.x(seconds);
        windZ = Wind.z(seconds);
        windLevel = w;
        float drive = Mth.clamp((w - 0.32F) / 0.53F, 0F, 1F);
        drive = drive * drive * (3F - 2F * drive) * (FxConfig.windShelter ? Wind.shelter : 1F);
        if (drive < 0.02F) {
            Diagnostics.skip(Diagnostics.WINDBITS, "wind too gentle");
            return;
        }
        float wx = windX, wz = windZ;
        int before = born;
        if (bits) {
            budget = Math.min(budget + drive * 1.6F * (float) BiomeFx.amount(FxConfig.windBitsAmount, BiomeFx.WIND_BITS), 6F);
            for (int tries = 0; tries < PROBES && budget >= 1F; tries++) {
                budget -= spawn(level, player, wx, wz) ? 1F : 0.12F;
            }
        }
        if (streaks && w > 0.45F && RANDOM.nextFloat() < drive * (0.04F + 0.28F * Wind.pulse(seconds))) {
            streak(level, player, wx, wz);
        }
        Diagnostics.mark(Diagnostics.WINDBITS, Diagnostics.detail() ? (born - before) + " born, drive " + String.format("%.2f", drive) : null);
    }

    private static boolean spawn(ClientLevel level, LocalPlayer player, float wx, float wz) {
        double cx = player.getX() - wx * 5.0 + (RANDOM.nextFloat() * 2F - 1F) * RADIUS;
        double cz = player.getZ() - wz * 5.0 + (RANDOM.nextFloat() * 2F - 1F) * RADIUS;
        int x = Mth.floor(cx), z = Mth.floor(cz);
        int y = player.getBlockY() - 4 + RANDOM.nextInt(14);
        if (!level.hasChunk(x >> 4, z >> 4)) return false;
        CURSOR.set(x, y, z);
        BlockState state = level.getBlockState(CURSOR);
        boolean leaves = state.is(BlockTags.LEAVES);
        if (!leaves) {
            int plant = WindSway.kind(state);
            if (plant != WindSway.PLANT && plant != WindSway.WEAK) return false;
        } else {
            CURSOR.set(x + (wx > 0F ? 1 : -1), y, z + (wz > 0F ? 1 : -1));
            if (level.getBlockState(CURSOR).canOcclude()) return false;
            CURSOR.set(x, y, z);
        }
        if (!open(level, x, y, z)) return false;
        CURSOR.set(x, y, z);
        int sky = level.getBrightness(LightLayer.SKY, CURSOR);
        int block = level.getBrightness(LightLayer.BLOCK, CURSOR);
        int tint = Minecraft.getInstance().getBlockColors().getColor(state, level, CURSOR, 0);
        if (tint == -1) tint = BiomeColors.getAverageFoliageColor(level, CURSOR);
        float shade = 0.85F + RANDOM.nextFloat() * 0.3F;
        int kind = RANDOM.nextFloat() < 0.5F ? FLECK : leaves ? LEAF : GRASS;
        if (kind == FLECK && RANDOM.nextFloat() < 0.3F) tint = 0xDDE6C8;
        double px = x + 0.1 + RANDOM.nextFloat() * 0.8;
        double py = y + 0.2 + RANDOM.nextFloat() * 0.7;
        double pz = z + 0.1 + RANDOM.nextFloat() * 0.8;
        emit(kind, px, py, pz, shade(tint, shade), block * 16 + sky, 1.8F + RANDOM.nextFloat() * 2.2F, 0.8F + RANDOM.nextFloat() * 0.4F);
        return true;
    }

    private static void streak(ClientLevel level, LocalPlayer player, float wx, float wz) {
        double along = 6.0 + RANDOM.nextFloat() * 8.0;
        double across = (RANDOM.nextFloat() * 2F - 1F) * 12.0;
        double x = player.getX() - wx * along - wz * across;
        double z = player.getZ() - wz * along + wx * across;
        double y = player.getEyeY() + (RANDOM.nextFloat() - 0.3F) * 5.0;
        CURSOR.set(x, y, z);
        if (!level.hasChunkAt(CURSOR) || !level.getBlockState(CURSOR).isAir()) return;
        if (!open(level, CURSOR.getX(), CURSOR.getY(), CURSOR.getZ())) return;
        CURSOR.set(x, y, z);
        int sky = level.getBrightness(LightLayer.SKY, CURSOR);
        int block = level.getBrightness(LightLayer.BLOCK, CURSOR);
        emit(STREAK, x, y, z, 0xEEF6FF, block * 16 + sky, 0.8F + RANDOM.nextFloat() * 0.7F, 1F);
    }

    private static boolean open(ClientLevel level, int x, int y, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (top - y > 32) return false;
        for (int at = y + 1; at < top; at++) {
            CURSOR.set(x, at, z);
            BlockState state = level.getBlockState(CURSOR);
            if (!state.is(BlockTags.LEAVES) && (state.blocksMotion() || !state.getFluidState().isEmpty())) return false;
        }
        return true;
    }

    private static int shade(int rgb, float k) {
        int r = Mth.clamp((int) ((rgb >> 16 & 255) * k), 0, 255);
        int g = Mth.clamp((int) ((rgb >> 8 & 255) * k), 0, 255);
        int b = Mth.clamp((int) ((rgb & 255) * k), 0, 255);
        return r << 16 | g << 8 | b;
    }

    public static void onWindBitsStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!anchored || now() - lastBirth > 6F || LightTint.lightmap == null) return;
        if (!(BiomeFx.on(FxConfig.windBits, BiomeFx.WIND_BITS) || FxConfig.windStreaks) || ColorGrade.shaderPackActive()) return;
        SkyProgram program = SkyProgram.WIND_BITS;
        if (!program.ready()) return;

        simulate();
        upload();
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        float ox = (float) (anchorX - eye.x);
        float oy = (float) (anchorY - eye.y);
        float oz = (float) (anchorZ - eye.z);
        MATRIX.set(event.getPoseStack().last().pose()).translate(ox, oy, oz);
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        program.begin();
        SkyProgram.texture(1, data);
        SkyProgram.texture(0, LightTint.lightmap.getId());
        program.transform(0, MATRIX);
        GL20.glUniform3f(program.uniform(1), -left.x(), -left.y(), -left.z());
        GL20.glUniform3f(program.uniform(2), up.x(), up.y(), up.z());
        GL20.glUniform3f(program.uniform(3), ox, oy, oz);
        GL20.glUniform4f(program.uniform(4), now(), RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), 0F);
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.uniform(5), fog[0], fog[1], fog[2], 1F);
        program.draw(SLOTS * 6);
        program.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void simulate() {
        long nanos = Util.getNanos();
        float dt = Mth.clamp((nanos - simClock) / 1.0E9F, 0F, 0.1F);
        simClock = nanos;
        if (dt <= 0F) return;
        float now = now();
        for (int i = 0; i < SLOTS; i++) {
            int o = i * 4;
            float life = ROW_B[o + 3];
            float age = now - ROW_A[o + 3];
            if (life <= 0F || age < 0F || age > life) continue;
            int kind = (int) ROW_B[o];
            float ease = 1F - (float) Math.exp(-dt * (kind == STREAK ? 2.5F : 1.4F));
            float target = speedOf(kind) * ROW_C[o + 3];
            ROW_D[o] += (windX * target - ROW_D[o]) * ease;
            ROW_D[o + 2] += (windZ * target - ROW_D[o + 2]) * ease;
            ROW_A[o] += ROW_D[o] * dt;
            ROW_A[o + 2] += ROW_D[o + 2] * dt;
        }
        drifted = true;
    }

    private static void upload() {
        if (data == 0) {
            data = GlStateManager._genTexture();
            GlStateManager._bindTexture(data);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            SkyHeights.unpack();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, SLOTS, ROWS, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            dirtyFrom = 0;
            dirtyCount = SLOTS;
        }
        if (dirtyFrom < 0 && !drifted) return;
        GlStateManager._bindTexture(data);
        SkyHeights.unpack();
        if (drifted) {
            drifted = false;
            row(0, ROW_A, 0, SLOTS);
            row(3, ROW_D, 0, SLOTS);
            if (dirtyFrom < 0) return;
        }
        int first = dirtyFrom;
        int count = dirtyCount;
        int run = Math.min(count, SLOTS - first);
        rows(first, run);
        if (count > run) rows(0, count - run);
        dirtyFrom = -1;
        dirtyCount = 0;
    }

    private static void rows(int first, int count) {
        row(0, ROW_A, first, count);
        row(1, ROW_B, first, count);
        row(2, ROW_C, first, count);
        row(3, ROW_D, first, count);
    }

    private static void row(int y, float[] source, int first, int count) {
        UPLOAD.clear();
        UPLOAD.put(source, first * 4, count * 4).flip();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, first, y, count, 1, GL11.GL_RGBA, GL11.GL_FLOAT, UPLOAD);
    }
}
