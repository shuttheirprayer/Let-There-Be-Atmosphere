package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Aurora {
    private Aurora() {}

    public static final int DETAIL = 200;
    public static final int DETAIL_FAST = 50;
    public static final int MAX_BANDS = 8;

    public static final int TOP = 0;
    public static final int MID = 3;
    public static final int LOW = 6;

    private static final int STRIDE = DETAIL + 1;
    private static final int CELLS = MAX_BANDS * STRIDE;
    private static final int RISE_CELLS = (MAX_BANDS + 2) * STRIDE;
    private static final long DAY_TICKS = 24000L;
    private static final long YEAR_DAYS = 365L;

    private static final double POLE_DISTANCE = 20000.0;
    private static final double MAX_POLE_OFFSET = 5000.0;
    private static final double HEIGHT_FREQUENCY = 100.0;
    private static final double WAVE_FREQUENCY = 25.0;
    private static final double MOVEMENT_FACTOR = 1.0E-4;
    private static final double HEIGHT_DRIFT = 0.0036;
    private static final double WAVE_DRIFT = 0.0018;

    private static final float NORTH_AT_FREEZING = 0.92F;
    private static final float NORTH_PER_DEGREE = 0.4F;
    private static final float NORTH_STEP = 0.002F;

    private static final float[] HUE_MIN = {0F, 0.22F, 0.6F};
    private static final float[] HUE_MAX = {0.05F, 0.41F, 0.88F};
    private static final int COLOR_CYCLE = 1200;

    public static final int GLOW_CAPACITY = 16;

    private static final RandomSource RANDOM = RandomSource.create(54382502529626502L);
    private static final RandomSource DAY_RANDOM = RandomSource.create(0L);

    private static boolean awake;
    private static boolean ready;
    private static String quiet = "daylight";

    private static long day = Long.MIN_VALUE;
    private static long year;
    private static double visibility;
    private static float poleDistance;
    private static int bands;
    private static final float[] BAND_STRENGTH = new float[MAX_BANDS];
    private static final float[] BAND_WIDTH = new float[MAX_BANDS];
    private static final float[] BAND_HEIGHT = new float[MAX_BANDS];
    private static final float[] BAND_PHASE = new float[MAX_BANDS];
    private static final double[] COLOR_STORM = new double[3];

    private static float north;
    private static float northPrev;
    private static double phase;
    private static double heightTime;
    private static double waveTime;
    private static float speed = 1F;

    private static int detail;
    private static int revision;
    private static final double[] SIN = new double[STRIDE];
    private static final double[] COS = new double[STRIDE];
    private static float[] rise = new float[RISE_CELLS];
    private static float[] risePrev = new float[RISE_CELLS];
    private static float[] wave = new float[CELLS];
    private static float[] wavePrev = new float[CELLS];

    private static boolean colored;
    private static final float[] COLOR = new float[9];
    private static final float[] COLOR_FROM = new float[9];
    private static final float[] COLOR_TO = new float[9];
    private static final float[] CHANGE_TIME = new float[3];
    private static final float[] CHANGE_LEFT = new float[3];
    private static final float[] UNTIL_CHANGE = new float[3];

    private static int glowCount;
    private static final float[] GLOW_FREQUENCY = new float[GLOW_CAPACITY];
    private static final float[] GLOW_RATE = new float[GLOW_CAPACITY];
    private static final float[] GLOW_AMP = new float[GLOW_CAPACITY];
    private static final int[] GLOW_AGE = new int[GLOW_CAPACITY];
    private static final int[] GLOW_LIFE = new int[GLOW_CAPACITY];

    public static void sleep(String reason) {
        awake = false;
        ready = false;
        quiet = reason;
    }

    public static void reset() {
        sleep("daylight");
        day = Long.MIN_VALUE;
        colored = false;
        glowCount = 0;
        for (int layer = 0; layer < 3; layer++) {
            CHANGE_LEFT[layer] = 0F;
            UNTIL_CHANGE[layer] = 0F;
        }
    }

    public static void tick(long dayTime, float temperature, double playerX, int newDetail,
                            float night, float rain, float sky, double rate, double frequency,
                            double brightness) {
        refreshDay(dayTime);

        float target = Mth.clamp(NORTH_AT_FREEZING - NORTH_PER_DEGREE * temperature, 0F, 1F);
        if (awake) {
            northPrev = north;
            north = Mth.approach(north, target, NORTH_STEP);
        } else {
            north = northPrev = target;
            awake = true;
        }

        speed = (float) rate;
        phase += rate;
        heightTime += HEIGHT_DRIFT * rate;
        waveTime += WAVE_DRIFT * rate;

        if (!colored) {
            for (int layer = 0; layer < 3; layer++) pickColor(layer, COLOR);
            colored = true;
        }
        cycleColors();
        tickGlow();

        if (bands == 0) {
            ready = false;
            quiet = "no storm tonight";
            return;
        }
        if (strength(night, north, rain, sky, frequency, brightness) < 0.005F) {
            ready = false;
            quiet = north < 0.75F ? "too warm here" : "too faint tonight";
            return;
        }
        sample(playerX, newDetail);
    }

    public static float strength(float night, float n, float rain, float sky, double frequency, double brightness) {
        if (rain >= 1F) return 0F;
        float n2 = n * n;
        float n4 = n2 * n2;
        float n8 = n4 * n4;
        float s = night * (1F - rain) * sky * (float) brightness;
        s *= (float) Mth.clamp(n4 * n * visibility * frequency, 0.0, 1.0);
        s *= Mth.clamp(n8 * 3F, 0F, 1F);
        return s;
    }

    private static void refreshDay(long dayTime) {
        long d = Math.floorDiv(dayTime, DAY_TICKS);
        if (d == day) return;
        day = d;
        ready = false;

        long dayOfYear = Math.floorMod(d, YEAR_DAYS);
        year = Math.floorDiv(d, YEAR_DAYS);
        double x = dayOfYear;
        double activity = AuroraNoise.storm(year + 1000L, 4, x, 0.0, 5.0);
        visibility = AuroraNoise.storm(year, 1, x, 0.0, 1.0);
        COLOR_STORM[0] = AuroraNoise.storm(year - 4L, 2, x, 1.0, 10.0) * 0.1;
        COLOR_STORM[1] = AuroraNoise.storm(year - 2L, 3, x, 1.0, 10.0) * 0.1;
        COLOR_STORM[2] = AuroraNoise.storm(year - 8L, 1, x, 1.0, 10.0) * 0.1;

        DAY_RANDOM.setSeed(dayOfYear + year);
        poleDistance = (float) (POLE_DISTANCE + MAX_POLE_OFFSET * (activity * DAY_RANDOM.nextGaussian() / 5.0));

        bands = Mth.clamp((int) Math.round(activity), 0, MAX_BANDS);
        float a = (float) activity;
        for (int b = 0; b < bands; b++) {
            DAY_RANDOM.setSeed(b + 1L + dayOfYear + year);
            BAND_STRENGTH[b] = Mth.clamp(Mth.randomBetween(DAY_RANDOM, 0.4F, a), 0F, 1F);
            BAND_WIDTH[b] = Mth.randomBetween(DAY_RANDOM, 1F, a + 1F);
            BAND_HEIGHT[b] = Mth.randomBetween(DAY_RANDOM, 0.1F, 0.5F);
            BAND_PHASE[b] = Mth.randomBetween(DAY_RANDOM, 0F, 1F + a);
        }
    }

    private static void sample(double playerX, int newDetail) {
        boolean continuous = ready && newDetail == detail;
        if (newDetail != detail) {
            detail = newDetail;
            for (int p = 0; p <= detail; p++) {
                double angle = 2.0 * Math.PI * ((float) p / (float) detail);
                SIN[p] = Math.sin(angle);
                COS[p] = Math.cos(angle);
            }
        }

        float[] swap = risePrev;
        risePrev = rise;
        rise = swap;
        swap = wavePrev;
        wavePrev = wave;
        wave = swap;

        double shift = -(playerX * MOVEMENT_FACTOR);
        double n = north;
        double waveFrequency = WAVE_FREQUENCY * (1.0 + 5.0 * (1.0 - n * n));
        for (int r = 0, rows = bands + 2; r < rows; r++) {
            long seed = year + 1L + r;
            int row = r * STRIDE;
            for (int k = 1; k < detail; k++) {
                double x = heightTime + ((float) k / (float) detail - shift) * HEIGHT_FREQUENCY;
                rise[row + k] = (float) AuroraNoise.storm(seed, 1, x, 0.0, 1.0);
            }
        }
        for (int b = 0; b < bands; b++) {
            long band = b + 1L;
            int row = b * STRIDE;
            for (int p = 1; p <= detail; p++) {
                double x = waveTime + ((float) p / (float) detail - shift) * waveFrequency;
                wave[row + p] = (float) AuroraNoise.storm(year + 10L + band, 1, x, 0.0, 1.0);
            }
        }

        if (!continuous) {
            System.arraycopy(rise, 0, risePrev, 0, (bands + 2) * STRIDE);
            System.arraycopy(wave, 0, wavePrev, 0, bands * STRIDE);
        }
        revision++;
        ready = true;
    }

    private static void pickColor(int layer, float[] out) {
        float distance = (1F - north) * 2F;
        float t = (float) Mth.clamp(COLOR_STORM[0] * distance, 0.2, 1.0);
        float m = (float) Mth.clamp(COLOR_STORM[1] * distance * t, 0.15, 1.0);
        float l = (float) Mth.clamp(COLOR_STORM[2] * distance * m, 0.1, 1.0);
        float hueTop = Mth.nextFloat(RANDOM, HUE_MIN[0], HUE_MAX[0]);
        float hueMid = Mth.nextFloat(RANDOM, HUE_MIN[1], HUE_MAX[1]);
        float hueLow = Mth.nextFloat(RANDOM, HUE_MIN[2], HUE_MAX[2]);
        int rgb = switch (layer) {
            case 0 -> Mth.hsvToRgb(hueTop, m, Mth.clamp(m, 0.1F, 1F));
            case 1 -> Mth.hsvToRgb(hueMid, t, Mth.clamp(t, 0.1F, 1F));
            default -> Mth.hsvToRgb(hueLow, l, Mth.clamp(l, 0.1F, 1F));
        };
        int o = layer * 3;
        out[o] = (rgb >> 16 & 0xFF) / 255F;
        out[o + 1] = (rgb >> 8 & 0xFF) / 255F;
        out[o + 2] = (rgb & 0xFF) / 255F;
    }

    private static void cycleColors() {
        for (int layer = 0; layer < 3; layer++) {
            if (UNTIL_CHANGE[layer] > 0F) {
                UNTIL_CHANGE[layer] -= 0.1F;
            } else if (CHANGE_LEFT[layer] <= 0F && RANDOM.nextInt(1200) == 0) {
                System.arraycopy(COLOR, layer * 3, COLOR_FROM, layer * 3, 3);
                pickColor(layer, COLOR_TO);
                CHANGE_TIME[layer] = Mth.nextFloat(RANDOM, 100F, 200F);
                CHANGE_LEFT[layer] = CHANGE_TIME[layer];
            }
            if (CHANGE_LEFT[layer] <= 0F) continue;

            CHANGE_LEFT[layer] -= 0.1F;
            float t = 1F;
            if (CHANGE_LEFT[layer] <= 0F) {
                UNTIL_CHANGE[layer] = Mth.nextInt(RANDOM, COLOR_CYCLE, COLOR_CYCLE * 2);
            } else {
                t = 1F - CHANGE_LEFT[layer] / CHANGE_TIME[layer];
            }
            float ease = (1F - Mth.cos(t * Mth.PI)) * 0.5F;
            for (int c = layer * 3, end = c + 3; c < end; c++) {
                COLOR[c] = COLOR_FROM[c] + (COLOR_TO[c] - COLOR_FROM[c]) * ease;
            }
        }
    }

    private static void tickGlow() {
        for (int i = glowCount - 1; i >= 0; i--) {
            if (--GLOW_AGE[i] > 0) continue;
            int last = --glowCount;
            GLOW_FREQUENCY[i] = GLOW_FREQUENCY[last];
            GLOW_RATE[i] = GLOW_RATE[last];
            GLOW_AMP[i] = GLOW_AMP[last];
            GLOW_AGE[i] = GLOW_AGE[last];
            GLOW_LIFE[i] = GLOW_LIFE[last];
        }
        if (RANDOM.nextInt(120) == 0) {
            float frequency = Mth.nextFloat(RANDOM, 30F, 150F);
            addGlow(frequency, frequency * 0.002F, Mth.nextFloat(RANDOM, 0.05F, 0.5F), Mth.nextInt(RANDOM, 100, 400));
        }
        if (RANDOM.nextInt(300) == 0) {
            float frequency = Mth.nextFloat(RANDOM, 400F, 500F);
            addGlow(frequency, frequency * 0.004F, Mth.nextFloat(RANDOM, 0.1F, 0.2F), Mth.nextInt(RANDOM, 100, 200));
        }
    }

    private static void addGlow(float frequency, float rate, float amp, int life) {
        if (glowCount == GLOW_CAPACITY) return;
        int i = glowCount++;
        GLOW_FREQUENCY[i] = frequency;
        GLOW_RATE[i] = rate;
        GLOW_AMP[i] = amp;
        GLOW_AGE[i] = life;
        GLOW_LIFE[i] = life;
    }

    public static boolean ready() { return ready; }

    public static String quiet() { return quiet; }

    public static int bands() { return bands; }

    public static int detail() { return detail; }

    public static int stride() { return STRIDE; }

    public static float poleDistance() { return poleDistance; }

    public static float north(float partialTick) { return Mth.lerp(partialTick, northPrev, north); }

    public static double glowTime(float partialTick) { return phase + partialTick * speed; }

    public static float bandStrength(int b) { return BAND_STRENGTH[b]; }

    public static float bandWidth(int b) { return BAND_WIDTH[b]; }

    public static float bandHeight(int b) { return BAND_HEIGHT[b]; }

    public static float bandPhase(int b) { return BAND_PHASE[b]; }

    public static float color(int index) { return COLOR[index]; }

    public static double sin(int p) { return SIN[p]; }

    public static double cos(int p) { return COS[p]; }

    public static float rise(int i, float t) { return risePrev[i] + (rise[i] - risePrev[i]) * t; }

    public static float wave(int i, float t) { return wavePrev[i] + (wave[i] - wavePrev[i]) * t; }

    public static int glowCount() { return glowCount; }

    public static int revision() { return revision; }

    public static float[] riseRows() { return rise; }

    public static float[] previousRiseRows() { return risePrev; }

    public static float[] waveRows() { return wave; }

    public static float[] previousWaveRows() { return wavePrev; }

    public static float glowFrequency(int i) { return GLOW_FREQUENCY[i]; }

    public static float glowRate(int i) { return GLOW_RATE[i]; }

    public static float glowAmp(int i) {
        float f = (GLOW_LIFE[i] - GLOW_AGE[i]) / (float) GLOW_LIFE[i];
        return GLOW_AMP[i] * Math.min(f, 1F - f);
    }
}
