package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import net.minecraft.util.Mth;

public final class FogDepth {
    private FogDepth() {}

    public static void spread(float near, float far, float night, float nightNearMul, float nightFarMul,
                              float density, float skyVisibility, float caveScale, float caveMinEnd,
                              float[] out) {
        float n = Mth.clamp(night, 0F, 1F);
        float resultNear = near * Mth.lerp(n, 1F, nightNearMul);
        float resultFar = far * Mth.lerp(n, 1F, nightFarMul);

        float thickness = Mth.clamp(density, 0.2F, 5F);
        resultNear /= thickness;
        resultFar /= thickness;

        if (caveScale != 1F) {
            float enclosed = 1F - Mth.clamp(skyVisibility, 0F, 1F);
            float caveMul = Mth.lerp(enclosed, 1F, caveScale);
            resultNear *= caveMul;
            resultFar *= caveMul;
        }

        resultFar = Math.max(resultFar, caveMinEnd);
        out[0] = Math.min(resultNear, resultFar);
        out[1] = resultFar;
    }
}
