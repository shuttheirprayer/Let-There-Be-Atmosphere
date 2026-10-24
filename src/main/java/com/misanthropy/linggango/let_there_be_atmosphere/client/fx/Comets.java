package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import net.minecraft.util.Mth;

import java.util.Random;

public final class Comets {
    private Comets() {}

    public static final int CAPACITY = 16;
    public static final int TAIL_SEGMENTS = 14;
    public static final int PALETTE_CUSTOM = 0;
    public static final int PALETTE_WARM = 1;
    public static final int PALETTE_COOL = 2;
    public static final int PALETTE_WHITE = 3;
    public static final int PALETTE_MIXED = 4;

    private static final float BASE_SPEED = 0.0185F;
    private static final float BASE_TAIL  = 0.145F;
    private static final float BASE_WIDTH = 0.0135F;
    private static final int   MIN_LIFE   = 26;
    private static final int   LIFE_SPAN  = 22;
    private static final float MIN_ELEVATION = 0.16F;

    private static final float[] DIR_X = new float[CAPACITY];
    private static final float[] DIR_Y = new float[CAPACITY];
    private static final float[] DIR_Z = new float[CAPACITY];
    private static final float[] AXIS_X = new float[CAPACITY];
    private static final float[] AXIS_Y = new float[CAPACITY];
    private static final float[] AXIS_Z = new float[CAPACITY];
    private static final float[] SPEED = new float[CAPACITY];
    private static final float[] TAIL = new float[CAPACITY];
    private static final float[] WIDTH = new float[CAPACITY];
    private static final float[] HEAD = new float[CAPACITY];
    private static final float[] AGE = new float[CAPACITY];
    private static final float[] LIFE = new float[CAPACITY];
    private static final int[] PALETTE = new int[CAPACITY];
    private static final boolean[] ALIVE = new boolean[CAPACITY];

    private static final float[] SCRATCH = new float[3];
    private static final float[] RADIANT = new float[3];

    private static int showerRemaining;
    private static int showerDelay;
    private static int showerPalette;

    private static final long SEED = 20260823L;
    private static final Random RANDOM = new Random(SEED);

    public static void reset() {
        for (int i = 0; i < CAPACITY; i++) ALIVE[i] = false;
        showerRemaining = 0;
        showerDelay = 0;
        RANDOM.setSeed(SEED);
    }

    public static boolean showerRunning() { return showerRemaining > 0; }

    public static boolean alive(int i) { return ALIVE[i]; }

    public static int liveCount() {
        int n = 0;
        for (int i = 0; i < CAPACITY; i++) if (ALIVE[i]) n++;
        return n;
    }

    public static void tick(boolean canSpawn, double rate, double max, double speedScale,
                            double tailScale, double angleDegrees, int palette,
                            double showerChance, double showerSize) {
        for (int i = 0; i < CAPACITY; i++) {
            if (!ALIVE[i]) continue;
            AGE[i] += 1F;
            if (AGE[i] >= LIFE[i]) ALIVE[i] = false;
        }
        if (!canSpawn) {
            showerRemaining = 0;
            return;
        }

        if (showerRemaining > 0) {
            if (--showerDelay > 0) return;
            showerDelay = 1 + RANDOM.nextInt(4);
            if (spawnFromRadiant(speedScale, tailScale)) showerRemaining--;
            else showerRemaining = 0;
            return;
        }

        if (showerChance > 0.0 && RANDOM.nextFloat() < showerChance) {
            startShower(showerSize, angleDegrees, palette);
            return;
        }

        if (rate <= 0.0) return;
        int cap = Mth.clamp((int) Math.round(max), 1, CAPACITY);
        if (liveCount() >= cap) return;
        if (RANDOM.nextFloat() >= rate) return;
        spawn(speedScale, tailScale, angleDegrees, palette);
    }

    private static void startShower(double showerSize, double angleDegrees, int palette) {
        float span = Mth.clamp((float) angleDegrees, 0F, 90F) * Mth.DEG_TO_RAD;
        float azimuth = RANDOM.nextFloat() * Mth.TWO_PI;
        float elevation = MIN_ELEVATION + 0.25F + RANDOM.nextFloat() * Math.max(0.05F, span * 0.7F);
        float cosE = Mth.cos(elevation);
        RADIANT[0] = Mth.cos(azimuth) * cosE;
        RADIANT[1] = Mth.sin(elevation);
        RADIANT[2] = Mth.sin(azimuth) * cosE;
        showerPalette = resolvePalette(palette);
        showerRemaining = Mth.clamp((int) Math.round(showerSize), 3, CAPACITY);
        showerDelay = 1;
    }

    private static boolean spawnFromRadiant(double speedScale, double tailScale) {
        int slot = -1;
        for (int i = 0; i < CAPACITY; i++) if (!ALIVE[i]) { slot = i; break; }
        if (slot < 0) return false;

        float rx = RANDOM.nextFloat() * 2F - 1F;
        float ry = RANDOM.nextFloat() * 2F - 1F;
        float rz = RANDOM.nextFloat() * 2F - 1F;
        float tx = RADIANT[1] * rz - RADIANT[2] * ry;
        float ty = RADIANT[2] * rx - RADIANT[0] * rz;
        float tz = RADIANT[0] * ry - RADIANT[1] * rx;
        float tl = Mth.sqrt(tx * tx + ty * ty + tz * tz);
        if (tl < 1.0E-4F) return false;
        tx /= tl; ty /= tl; tz /= tl;

        float offset = 0.05F + RANDOM.nextFloat() * 0.45F;
        rotate(RADIANT[0], RADIANT[1], RADIANT[2], tx, ty, tz, offset, SCRATCH);
        float dx = SCRATCH[0], dy = SCRATCH[1], dz = SCRATCH[2];

        float ax = RADIANT[1] * dz - RADIANT[2] * dy;
        float ay = RADIANT[2] * dx - RADIANT[0] * dz;
        float az = RADIANT[0] * dy - RADIANT[1] * dx;
        float al = Mth.sqrt(ax * ax + ay * ay + az * az);
        if (al < 1.0E-4F) return false;
        ax /= al; ay /= al; az /= al;

        float speed = BASE_SPEED * (0.85F + RANDOM.nextFloat() * 0.5F) * (float) speedScale;
        float life = MIN_LIFE + RANDOM.nextInt(LIFE_SPAN);

        DIR_X[slot] = dx; DIR_Y[slot] = dy; DIR_Z[slot] = dz;
        AXIS_X[slot] = ax; AXIS_Y[slot] = ay; AXIS_Z[slot] = az;
        SPEED[slot] = speed;
        TAIL[slot]  = BASE_TAIL * (0.8F + RANDOM.nextFloat() * 0.5F) * (float) tailScale;
        WIDTH[slot] = BASE_WIDTH * (0.7F + RANDOM.nextFloat() * 0.5F);
        HEAD[slot]  = 1.4F + RANDOM.nextFloat() * 1.0F;
        AGE[slot]   = 0F;
        LIFE[slot]  = life;
        PALETTE[slot] = showerPalette;
        ALIVE[slot] = true;
        return true;
    }

    private static void spawn(double speedScale, double tailScale, double angleDegrees, int palette) {
        int slot = -1;
        for (int i = 0; i < CAPACITY; i++) if (!ALIVE[i]) { slot = i; break; }
        if (slot < 0) return;

        float span = Mth.clamp((float) angleDegrees, 0F, 90F) * Mth.DEG_TO_RAD;
        float azimuth = RANDOM.nextFloat() * Mth.TWO_PI;
        float elevation = MIN_ELEVATION + RANDOM.nextFloat() * Math.max(0.05F, span);
        float cosE = Mth.cos(elevation);
        float dx = Mth.cos(azimuth) * cosE;
        float dy = Mth.sin(elevation);
        float dz = Mth.sin(azimuth) * cosE;

        float rx = RANDOM.nextFloat() * 2F - 1F;
        float ry = RANDOM.nextFloat() * 2F - 1F;
        float rz = RANDOM.nextFloat() * 2F - 1F;
        float ax = dy * rz - dz * ry;
        float ay = dz * rx - dx * rz;
        float az = dx * ry - dy * rx;
        float len = Mth.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1.0E-4F) { ax = 0F; ay = 1F; az = 0F; len = 1F; }
        ax /= len; ay /= len; az /= len;

        float speed = BASE_SPEED * (0.7F + RANDOM.nextFloat() * 0.6F) * (float) speedScale;
        float life = MIN_LIFE + RANDOM.nextInt(LIFE_SPAN);

        rotate(dx, dy, dz, ax, ay, az, speed * life, SCRATCH);
        if (SCRATCH[1] < 0.02F) { ax = -ax; ay = -ay; az = -az; }

        DIR_X[slot] = dx; DIR_Y[slot] = dy; DIR_Z[slot] = dz;
        AXIS_X[slot] = ax; AXIS_Y[slot] = ay; AXIS_Z[slot] = az;
        SPEED[slot] = speed;
        TAIL[slot]  = BASE_TAIL * (0.7F + RANDOM.nextFloat() * 0.6F) * (float) tailScale;
        WIDTH[slot] = BASE_WIDTH * (0.8F + RANDOM.nextFloat() * 0.5F);
        HEAD[slot]  = 1.6F + RANDOM.nextFloat() * 1.1F;
        AGE[slot]   = 0F;
        LIFE[slot]  = life;
        PALETTE[slot] = resolvePalette(palette);
        ALIVE[slot] = true;
    }

    private static int resolvePalette(int palette) {
        if (palette == PALETTE_MIXED) return PALETTE_WARM + RANDOM.nextInt(3);
        return Mth.clamp(palette, PALETTE_CUSTOM, PALETTE_WHITE);
    }

    public static int palette(int i) { return PALETTE[i]; }

    public static float width(int i) { return WIDTH[i]; }

    public static float headScale(int i) { return HEAD[i]; }

    public static float fade(int i, float partialTick) {
        if (!ALIVE[i]) return 0F;
        float t = Mth.clamp((AGE[i] + partialTick) / LIFE[i], 0F, 1F);
        return Mth.sin(t * Mth.PI);
    }

    public static float alpha(int i, float u, float partialTick) {
        float taper = 1F - Mth.clamp(u, 0F, 1F);
        return fade(i, partialTick) * taper * taper;
    }

    public static void position(int i, float u, float partialTick, float[] out) {
        float angle = (AGE[i] + partialTick) * SPEED[i] - Mth.clamp(u, 0F, 1F) * TAIL[i];
        rotate(DIR_X[i], DIR_Y[i], DIR_Z[i], AXIS_X[i], AXIS_Y[i], AXIS_Z[i], angle, out);
    }

    public static float sweep(int i, float partialTick) {
        return (AGE[i] + partialTick) * SPEED[i];
    }

    public static float tail(int i) { return TAIL[i]; }

    public static void direction(int i, float[] out) {
        out[0] = DIR_X[i];
        out[1] = DIR_Y[i];
        out[2] = DIR_Z[i];
    }

    public static void ribbon(int i, float[] out) {
        out[0] = AXIS_X[i];
        out[1] = AXIS_Y[i];
        out[2] = AXIS_Z[i];
    }

    public static void heading(int i, float[] position, float[] out) {
        float cx = AXIS_Y[i] * position[2] - AXIS_Z[i] * position[1];
        float cy = AXIS_Z[i] * position[0] - AXIS_X[i] * position[2];
        float cz = AXIS_X[i] * position[1] - AXIS_Y[i] * position[0];
        float len = Mth.sqrt(cx * cx + cy * cy + cz * cz);
        if (len < 1.0E-5F) { out[0] = 0F; out[1] = 0F; out[2] = 0F; return; }
        out[0] = cx / len; out[1] = cy / len; out[2] = cz / len;
    }

    static void rotate(float vx, float vy, float vz, float ax, float ay, float az, float angle, float[] out) {
        float c = Mth.cos(angle);
        float s = Mth.sin(angle);
        float dot = ax * vx + ay * vy + az * vz;
        float cx = ay * vz - az * vy;
        float cy = az * vx - ax * vz;
        float cz = ax * vy - ay * vx;
        out[0] = vx * c + cx * s + ax * dot * (1F - c);
        out[1] = vy * c + cy * s + ay * dot * (1F - c);
        out[2] = vz * c + cz * s + az * dot * (1F - c);
    }
}
