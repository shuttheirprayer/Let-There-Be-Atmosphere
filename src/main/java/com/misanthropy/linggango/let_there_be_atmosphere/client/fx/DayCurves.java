package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import net.minecraft.util.Mth;

public final class DayCurves {
    private DayCurves() {}

    public static final int PERIOD = 24000;

    private static final int[] SUNRISE = {
                  71,  95, 239, 163, 51,
                 310,  41, 245, 186, 51,
                 565,   6, 251, 212, 51,
                 730,   0, 255, 229, 51,
               11270,   0, 255, 229, 51,
               11397,   4, 252, 216, 51,
               11522,  15, 249, 203, 51,
               11690,  41, 245, 186, 51,
               11929,  95, 239, 163, 51,
               12243, 177, 231, 135, 51,
               12358, 204, 228, 126, 51,
               12512, 233, 224, 114, 51,
               12613, 246, 221, 107, 51,
               12732, 254, 218,  99, 51,
               12841, 254, 215,  92, 51,
               13035, 236, 210,  81, 51,
               13252, 193, 204,  71, 51,
               13775,  54, 190,  55, 51,
               13888,  31, 187,  53, 51,
               14039,   9, 183,  51, 51,
               14192,   0, 179,  51, 51,
               21807,   0, 178,  51, 51,
               21961,   9, 183,  51, 51,
               22112,  31, 187,  53, 51,
               22225,  54, 190,  55, 51,
               22748, 193, 204,  71, 51,
               22965, 236, 210,  81, 51,
               23159, 254, 215,  92, 51,
               23272, 254, 218,  99, 51,
               23488, 233, 224, 114, 51,
               23642, 204, 228, 126, 51,
               23757, 177, 231, 135, 51,
    };
    private static final int SUNRISE_STRIDE = 5;

    private static final float[] STAR_BRIGHTNESS = {
                92F, 0.037F,
               627F, 0.0F,
             11373F, 0.0F,
             11732F, 0.016F,
             11959F, 0.044F,
             12399F, 0.143F,
             12729F, 0.258F,
             13228F, 0.5F,
             22772F, 0.5F,
             23032F, 0.364F,
             23356F, 0.225F,
             23758F, 0.101F,
    };

    private static final int[] FOG_MULTIPLY = {
                 133, 255, 255, 255,
               11867, 255, 255, 255,
               13670,  12,  12,  22,
               22330,  22,  22,  22,
    };
    private static final int FOG_STRIDE = 4;

    private static final int[] SKY_LIGHT_COLOR = {
                 730, 255, 255, 255,
               11270, 255, 255, 255,
               13140, 122, 122, 255,
               22860, 122, 122, 255,
    };

    private static final float[] SKY_LIGHT_FACTOR = {
               730F, 1.0F,
             11270F, 1.0F,
             13140F, 0.24F,
             22860F, 0.24F,
    };

    private static final float SKY_LIGHT_FLOOR = 0.24F;

    public static void skyLightColor(float dayTimeTicks, float[] out) {
        float t = wrap(dayTimeTicks);
        int keys = SKY_LIGHT_COLOR.length / FOG_STRIDE;
        int i = segment(t, SKY_LIGHT_COLOR, FOG_STRIDE, keys);
        int a = i * FOG_STRIDE;
        int b = next(i, keys) * FOG_STRIDE;
        float f = fraction(t, SKY_LIGHT_COLOR[a], SKY_LIGHT_COLOR[b]);

        out[0] = Mth.lerp(f, SKY_LIGHT_COLOR[a + 1], SKY_LIGHT_COLOR[b + 1]) / 255F;
        out[1] = Mth.lerp(f, SKY_LIGHT_COLOR[a + 2], SKY_LIGHT_COLOR[b + 2]) / 255F;
        out[2] = Mth.lerp(f, SKY_LIGHT_COLOR[a + 3], SKY_LIGHT_COLOR[b + 3]) / 255F;
    }

    public static float skyLightFactor(float dayTimeTicks) {
        float t = wrap(dayTimeTicks);
        int keys = SKY_LIGHT_FACTOR.length / 2;
        int i = segmentF(t, SKY_LIGHT_FACTOR, 2, keys);
        int a = i * 2;
        int b = next(i, keys) * 2;
        return Mth.lerp(fraction(t, SKY_LIGHT_FACTOR[a], SKY_LIGHT_FACTOR[b]),
                SKY_LIGHT_FACTOR[a + 1], SKY_LIGHT_FACTOR[b + 1]);
    }

    public static float nightFraction(float dayTimeTicks) {
        float factor = skyLightFactor(dayTimeTicks);
        return Mth.clamp((1F - factor) / (1F - SKY_LIGHT_FLOOR), 0F, 1F);
    }

    public static boolean sunriseSunset(float dayTimeTicks, float[] out) {
        float t = wrap(dayTimeTicks);
        int keys = SUNRISE.length / SUNRISE_STRIDE;
        int i = segment(t, SUNRISE, SUNRISE_STRIDE, keys);
        int a = i * SUNRISE_STRIDE;
        int b = next(i, keys) * SUNRISE_STRIDE;
        float f = fraction(t, SUNRISE[a], SUNRISE[b]);

        out[0] = Mth.lerp(f, SUNRISE[a + 2], SUNRISE[b + 2]) / 255F;
        out[1] = Mth.lerp(f, SUNRISE[a + 3], SUNRISE[b + 3]) / 255F;
        out[2] = Mth.lerp(f, SUNRISE[a + 4], SUNRISE[b + 4]) / 255F;
        out[3] = Mth.lerp(f, SUNRISE[a + 1], SUNRISE[b + 1]) / 255F;
        return out[3] > 0F;
    }

    public static float starBrightness(float dayTimeTicks) {
        float t = wrap(dayTimeTicks);
        int keys = STAR_BRIGHTNESS.length / 2;
        int i = segmentF(t, STAR_BRIGHTNESS, 2, keys);
        int a = i * 2;
        int b = next(i, keys) * 2;
        return Mth.lerp(fraction(t, STAR_BRIGHTNESS[a], STAR_BRIGHTNESS[b]),
                STAR_BRIGHTNESS[a + 1], STAR_BRIGHTNESS[b + 1]);
    }

    public static void fogMultiply(float dayTimeTicks, float[] out) {
        float t = wrap(dayTimeTicks);
        int keys = FOG_MULTIPLY.length / FOG_STRIDE;
        int i = segment(t, FOG_MULTIPLY, FOG_STRIDE, keys);
        int a = i * FOG_STRIDE;
        int b = next(i, keys) * FOG_STRIDE;
        float f = fraction(t, FOG_MULTIPLY[a], FOG_MULTIPLY[b]);

        out[0] = Mth.lerp(f, FOG_MULTIPLY[a + 1], FOG_MULTIPLY[b + 1]) / 255F;
        out[1] = Mth.lerp(f, FOG_MULTIPLY[a + 2], FOG_MULTIPLY[b + 2]) / 255F;
        out[2] = Mth.lerp(f, FOG_MULTIPLY[a + 3], FOG_MULTIPLY[b + 3]) / 255F;
    }

    private static int segment(float t, int[] track, int stride, int keys) {
        for (int k = 0; k < keys - 1; k++) {
            if (t >= track[k * stride] && t < track[(k + 1) * stride]) return k;
        }
        return keys - 1;
    }

    private static int segmentF(float t, float[] track, int stride, int keys) {
        for (int k = 0; k < keys - 1; k++) {
            if (t >= track[k * stride] && t < track[(k + 1) * stride]) return k;
        }
        return keys - 1;
    }

    private static int next(int i, int keys) { return i == keys - 1 ? 0 : i + 1; }

    private static float fraction(float t, float from, float to) {
        float span = wrap(to - from);
        if (span <= 0F) return 0F;
        return Mth.clamp(wrap(t - from) / span, 0F, 1F);
    }

    private static float wrap(float ticks) {
        float t = ticks % PERIOD;
        return t < 0F ? t + PERIOD : t;
    }
}
