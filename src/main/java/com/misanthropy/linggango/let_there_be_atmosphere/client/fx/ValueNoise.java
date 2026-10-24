package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public final class ValueNoise {
    private final int[] permutation = new int[512];

    public ValueNoise(long seed) {
        RandomSource random = RandomSource.create(seed);
        for (int i = 0; i < 256; i++) permutation[i] = i;
        for (int i = 0; i < 256; i++) {
            int j = random.nextInt(256);
            int swap = permutation[i];
            permutation[i] = permutation[j];
            permutation[j] = swap;
            permutation[i + 256] = permutation[i];
        }
    }

    public float at(float x) {
        int fx = Mth.floor(x);
        int xi = fx & 255;
        float xf = x - fx;
        float u = xf * xf * (3F - 2F * xf);
        return (Mth.lerp(u, grad(permutation[permutation[xi]], xf),
                grad(permutation[permutation[xi + 1]], xf - 1F)) + 1F) * 0.5F;
    }

    public float at(float x, float y) {
        int fx = Mth.floor(x), fy = Mth.floor(y);
        float xf = x - fx, yf = y - fy;
        float u = xf * xf * (3F - 2F * xf);
        float v = yf * yf * (3F - 2F * yf);

        int px0 = permutation[fx & 255];
        int px1 = permutation[(fx + 1) & 255];
        int yi0 = fy & 255, yi1 = (fy + 1) & 255;

        float c00 = permutation[(px0 + yi0) & 255] / 255F;
        float c10 = permutation[(px1 + yi0) & 255] / 255F;
        float c01 = permutation[(px0 + yi1) & 255] / 255F;
        float c11 = permutation[(px1 + yi1) & 255] / 255F;

        return Mth.lerp(v, Mth.lerp(u, c00, c10), Mth.lerp(u, c01, c11));
    }

    private static float grad(int hash, float x) {
        return (hash & 1) == 0 ? x : -x;
    }
}
