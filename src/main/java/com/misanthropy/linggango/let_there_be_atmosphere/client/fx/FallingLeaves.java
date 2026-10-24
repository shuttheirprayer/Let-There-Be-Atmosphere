package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

final class FallingLeaves {
    private FallingLeaves() {}

    static final int N = 256;
    private static final int AIR = 0, GROUND = 1, WATER = 2, FADING = 3;
    private static final float FADE = 3F;
    private static final float REACH2 = 48F * 48F;

    private static final boolean[] LIVE = new boolean[N];
    private static final byte[] STATE = new byte[N];
    private static final double[] X = new double[N], Y = new double[N], Z = new double[N];
    private static final float[] VX = new float[N], VY = new float[N], VZ = new float[N];
    private static final float[] FLOOR = new float[N], TERM = new float[N], DRIFT = new float[N];
    private static final float[] YAW = new float[N], SPIN = new float[N];
    private static final int[] TIMER = new int[N], COLUMN_X = new int[N], COLUMN_Z = new int[N], AGE = new int[N];
    private static final boolean[] OPEN = new boolean[N], WET = new boolean[N];
    private static final BlockPos.MutableBlockPos P = new BlockPos.MutableBlockPos();
    private static int alive;

    static void reset() {
        java.util.Arrays.fill(LIVE, false);
        alive = 0;
    }

    static boolean live(int i) {
        return LIVE[i];
    }

    static int count() {
        return alive;
    }

    static void spawn(int i, double x, double y, double z, float vx, float vy, float vz, boolean petal, RandomSource random, float now) {
        if (!LIVE[i]) alive++;
        LIVE[i] = true;
        STATE[i] = AIR;
        X[i] = x;
        Y[i] = y;
        Z[i] = z;
        VX[i] = vx;
        VY[i] = vy;
        VZ[i] = vz;
        TERM[i] = petal ? 0.014F + random.nextFloat() * 0.008F : 0.022F + random.nextFloat() * 0.013F;
        DRIFT[i] = (petal ? 0.042F : 0.03F) * (0.7F + random.nextFloat() * 0.6F);
        YAW[i] = random.nextFloat() * Mth.TWO_PI;
        SPIN[i] = (0.5F + random.nextFloat() * 1.3F) * (random.nextBoolean() ? 1F : -1F) / 20F;
        AGE[i] = 0;
        COLUMN_X[i] = Integer.MIN_VALUE;
        floor(i);
        store(i, now);
    }

    static void tick(LocalPlayer player, float now) {
        if (alive == 0) return;
        ClientLevel level = Litter.level;
        float seconds = Wind.seconds(level, 0F);
        float gust = Wind.gust(seconds);
        float force = (0.4F + 1.2F * gust) * (float) FxConfig.windStrength;
        float wx = Wind.x(seconds), wz = Wind.z(seconds);
        float rain = level.getRainLevel(1F);
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        float pvx = (float) (px - player.xo), pvz = (float) (pz - player.zo), pvy = (float) (py - player.yo);
        boolean moving = pvx * pvx + pvz * pvz > 0.0004F || Math.abs(pvy) > 0.05F;
        RandomSource random = level.random;
        for (int i = 0; i < N; i++) {
            if (!LIVE[i]) continue;
            AGE[i]++;
            double dx = X[i] - px, dz = Z[i] - pz;
            if (dx * dx + dz * dz > REACH2 || (STATE[i] == AIR && AGE[i] > 1800)) {
                fade(i, now);
            }
            switch (STATE[i]) {
                case AIR -> air(i, wx, wz, force, rain, moving, px, py, pz, pvx, pvy, pvz);
                case GROUND -> ground(i, wx, wz, gust, force, moving, px, py, pz, pvx, pvz, random, now);
                case WATER -> {
                    VX[i] += (wx * 0.006F * force - VX[i]) * 0.05F;
                    VZ[i] += (wz * 0.006F * force - VZ[i]) * 0.05F;
                    slide(i);
                    if (--TIMER[i] <= 0) fade(i, now);
                }
                default -> {
                    if (--TIMER[i] <= 0) {
                        LIVE[i] = false;
                        alive--;
                        continue;
                    }
                }
            }
            if (LIVE[i]) store(i, now);
        }
    }

    private static void air(int i, float wx, float wz, float force, float rain, boolean moving,
                            double px, double py, double pz, float pvx, float pvy, float pvz) {
        float drift = DRIFT[i] * force;
        VX[i] += (wx * drift - VX[i]) * 0.06F;
        VZ[i] += (wz * drift - VZ[i]) * 0.06F;
        VY[i] += (-TERM[i] * (1F + 0.35F * rain) - VY[i]) * 0.12F;
        if (moving) shove(i, px, py, pz, pvx, pvy, pvz, 0.9F);
        float above = (float) Y[i] - FLOOR[i];
        YAW[i] += SPIN[i] * Mth.clamp(above / 0.85F, 0.15F, 1F);
        slide(i);
        Y[i] += VY[i];
        if (Y[i] <= FLOOR[i] + 0.012) land(i);
    }

    private static void ground(int i, float wx, float wz, float gust, float force, boolean moving,
                               double px, double py, double pz, float pvx, float pvz, RandomSource random, float now) {
        if (moving && Math.abs(Y[i] - py) < 0.6) {
            double dx = X[i] - px, dz = Z[i] - pz;
            float d2 = (float) (dx * dx + dz * dz);
            float speed = Mth.sqrt(pvx * pvx + pvz * pvz);
            if (d2 < 0.5F && speed > 0.03F) {
                float d = Math.max(Mth.sqrt(d2), 0.05F);
                VX[i] += pvx * 0.5F + (float) dx / d * 0.03F;
                VZ[i] += pvz * 0.5F + (float) dz / d * 0.03F;
                VY[i] = 0.05F + random.nextFloat() * 0.05F * Math.min(1F, speed * 6F);
                SPIN[i] = (random.nextFloat() - 0.5F) * 0.3F;
                STATE[i] = AIR;
                AGE[i] = 0;
                return;
            }
        }
        if (OPEN[i] && gust > 0.7F) {
            float a = (gust - 0.7F) * 0.012F * force;
            VX[i] += wx * a;
            VZ[i] += wz * a;
        }
        VX[i] *= 0.8F;
        VZ[i] *= 0.8F;
        if (Math.abs(VX[i]) + Math.abs(VZ[i]) > 0.002F) {
            YAW[i] += (VX[i] - VZ[i]) * 0.6F;
            slide(i);
            if (FLOOR[i] < Y[i] - 0.15F) {
                STATE[i] = AIR;
                return;
            }
            Y[i] = FLOOR[i] + 0.012;
        } else {
            VX[i] = VZ[i] = 0F;
        }
        if (--TIMER[i] <= 0) fade(i, now);
    }

    private static void shove(int i, double px, double py, double pz, float pvx, float pvy, float pvz, float reach) {
        double dy = Y[i] - py;
        if (dy < -0.2 || dy > 2.0) return;
        double dx = X[i] - px, dz = Z[i] - pz;
        float d2 = (float) (dx * dx + dz * dz);
        if (d2 > reach * reach) return;
        float d = Math.max(Mth.sqrt(d2), 0.05F);
        float near = 1F - d / reach;
        VX[i] += (pvx * 0.45F + (float) dx / d * 0.02F) * near;
        VZ[i] += (pvz * 0.45F + (float) dz / d * 0.02F) * near;
        if (pvy > 0F) VY[i] += pvy * 0.3F * near;
        float s2 = VX[i] * VX[i] + VZ[i] * VZ[i];
        if (s2 > 0.0625F) {
            float k = 0.25F / Mth.sqrt(s2);
            VX[i] *= k;
            VZ[i] *= k;
        }
        SPIN[i] += (pvx - pvz) * 0.05F;
    }

    private static void land(int i) {
        Y[i] = FLOOR[i] + (WET[i] ? 0F : 0.012F);
        VY[i] = 0F;
        RandomSource random = Litter.level.random;
        if (WET[i]) {
            STATE[i] = WATER;
            VX[i] *= 0.3F;
            VZ[i] *= 0.3F;
            TIMER[i] = 180 + random.nextInt(180);
            Ripples.emit(X[i], FLOOR[i], Z[i], 0.9F, 1F);
        } else {
            STATE[i] = GROUND;
            VX[i] *= 0.35F;
            VZ[i] *= 0.35F;
            TIMER[i] = 200 + random.nextInt(240);
            P.set(Mth.floor(X[i]), Mth.floor(Y[i] + 0.5), Mth.floor(Z[i]));
            OPEN[i] = Litter.level.getBrightness(LightLayer.SKY, P) >= 13;
        }
    }

    private static void fade(int i, float now) {
        if (STATE[i] == FADING) return;
        STATE[i] = FADING;
        TIMER[i] = (int) (FADE * 20F) + 2;
        int o = (Litter.LEAF_BASE + i) * 4;
        Litter.ROW[1][o + 3] = now - Litter.ROW[0][o + 3] + FADE;
        VX[i] = VZ[i] = 0F;
    }

    private static void slide(int i) {
        ClientLevel level = Litter.level;
        double nx = X[i] + VX[i];
        if (Mth.floor(nx) != Mth.floor(X[i]) && solid(level, nx, Y[i], Z[i])) VX[i] *= -0.1F;
        else X[i] = nx;
        double nz = Z[i] + VZ[i];
        if (Mth.floor(nz) != Mth.floor(Z[i]) && solid(level, X[i], Y[i], nz)) VZ[i] *= -0.1F;
        else Z[i] = nz;
        int cx = Mth.floor(X[i]), cz = Mth.floor(Z[i]);
        if (cx != COLUMN_X[i] || cz != COLUMN_Z[i] || (STATE[i] == AIR && (AGE[i] & 7) == 0)) floor(i);
    }

    private static boolean solid(ClientLevel level, double x, double y, double z) {
        P.set(Mth.floor(x), Mth.floor(y + 0.05), Mth.floor(z));
        BlockState state = level.getBlockState(P);
        return !state.is(BlockTags.LEAVES) && state.blocksMotion();
    }

    private static void floor(int i) {
        ClientLevel level = Litter.level;
        int bx = Mth.floor(X[i]), bz = Mth.floor(Z[i]);
        COLUMN_X[i] = bx;
        COLUMN_Z[i] = bz;
        WET[i] = false;
        int top = Mth.floor(Y[i] + 0.1);
        for (int yy = top; yy >= top - 24; yy--) {
            P.set(bx, yy, bz);
            BlockState state = level.getBlockState(P);
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                WET[i] = fluid.is(FluidTags.WATER);
                FLOOR[i] = yy + fluid.getHeight(level, P);
                return;
            }
            if (state.is(BlockTags.LEAVES)) continue;
            VoxelShape shape = state.getCollisionShape(level, P);
            if (!shape.isEmpty()) {
                FLOOR[i] = yy + (float) shape.max(Direction.Axis.Y);
                return;
            }
        }
        FLOOR[i] = top - 64;
    }

    private static void store(int i, float now) {
        int o = (Litter.LEAF_BASE + i) * 4;
        float[][] row = Litter.ROW;
        boolean air = STATE[i] == AIR;
        row[0][o] = (float) (X[i] - Litter.anchorX);
        row[0][o + 1] = (float) (Y[i] - Litter.anchorY);
        row[0][o + 2] = (float) (Z[i] - Litter.anchorZ);
        row[2][o + 1] = air ? FLOOR[i] - (float) Litter.anchorY : (float) (Y[i] - Litter.anchorY);
        row[2][o + 2] = VX[i] * 20F;
        row[2][o + 3] = VZ[i] * 20F;
        row[3][o] = air ? Math.max(VY[i], (FLOOR[i] - (float) Y[i]) / 1.4F) * 20F : 0F;
        row[3][o + 1] = now;
        float size = Math.abs(row[3][o + 2]);
        row[3][o + 2] = STATE[i] == WATER ? -size : size;
        row[3][o + 3] = YAW[i];
        if ((AGE[i] & 15) == 0) {
            P.set(Mth.floor(X[i]), Mth.floor(Y[i] + 0.2), Mth.floor(Z[i]));
            if (Litter.ready(P.getX() >> 4, P.getZ() >> 4)) row[1][o + 2] = Litter.light(P);
        }
        Litter.markDirty(Litter.LEAF_BASE + i, Litter.LEAF_BASE + i + 1);
    }
}
