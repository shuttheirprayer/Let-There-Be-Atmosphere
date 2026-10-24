package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import net.minecraft.util.Mth;

public final class WeatherTint {
    private WeatherTint() {}

    private static final float PEAK_ALPHA = 0.24F;

    private static final float THUNDER_BOOST = 0.35F;

    private static final float NIGHT_FLOOR = 0.25F;

    public static float amount(float rain, float thunder, float skyVisible,
                               float daylight, double strength) {
        if (strength <= 0.0) return 0F;
        float wet = Mth.clamp(rain, 0F, 1F);
        if (wet <= 0F || skyVisible <= 0F) return 0F;

        float storm = 1F + smooth(Mth.clamp(thunder, 0F, 1F)) * THUNDER_BOOST;
        float lit   = NIGHT_FLOOR + (1F - NIGHT_FLOOR) * Mth.clamp(daylight, 0F, 1F);
        float a = smooth(wet) * storm * Mth.clamp(skyVisible, 0F, 1F) * lit
                * (float) strength * PEAK_ALPHA;
        return Mth.clamp(a, 0F, 1F);
    }

    public static int color(int rainHex, int snowHex, float snow) {
        float t = Mth.clamp(snow, 0F, 1F);
        int r = channel(rainHex, snowHex, t, 16);
        int g = channel(rainHex, snowHex, t, 8);
        int b = channel(rainHex, snowHex, t, 0);
        return (r << 16) | (g << 8) | b;
    }

    private static int channel(int from, int to, float t, int shift) {
        int a = (from >> shift) & 0xFF, b = (to >> shift) & 0xFF;
        return Mth.clamp(Math.round(Mth.lerp(t, a, b)), 0, 255);
    }

    private static float smooth(float v) { return v * v * (3F - 2F * v); }
}
