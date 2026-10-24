package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import net.minecraft.util.Mth;

public final class HazeBand {
    private HazeBand() {}

    public static final int SEGMENTS = 32;

    public static float alpha(float y, float bottom, float middle, float top) {
        if (top <= bottom) return 0F;
        float mid = Mth.clamp(middle, bottom, top);
        if (y <= bottom || y >= top) return 0F;
        if (y <= mid) {
            float span = mid - bottom;
            if (span <= 0F) return 1F;
            return smooth((y - bottom) / span);
        }
        float span = top - mid;
        if (span <= 0F) return 1F;
        return smooth((top - y) / span);
    }

    private static float smooth(float v) {
        float t = Mth.clamp(v, 0F, 1F);
        return t * t * (3F - 2F * t);
    }
}
