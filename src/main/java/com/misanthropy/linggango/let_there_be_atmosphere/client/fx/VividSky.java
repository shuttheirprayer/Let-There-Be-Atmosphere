package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class VividSky {
    private VividSky() {}

    public static final float[] ZENITH = new float[3];
    public static final float[] HORIZON = new float[3];

    private static final float[][] ZENITHS = {
            {0.015F, 0.025F, 0.08F},
            {0.30F, 0.10F, 0.48F},
            {0.90F, 0.16F, 0.82F},
            {0.06F, 0.38F, 1.00F}};
    private static final float[][] HORIZONS = {
            {0.03F, 0.06F, 0.15F},
            {0.80F, 0.32F, 0.30F},
            {0.97F, 0.55F, 0.10F},
            {0.00F, 0.86F, 1.00F}};
    private static final float[][] AWAY = {
            {0.03F, 0.06F, 0.15F},
            {0.55F, 0.24F, 0.55F},
            {0.98F, 0.56F, 0.80F},
            {0.00F, 0.86F, 1.00F}};
    private static final float[] SUNWARD = new float[3];
    private static final float[] WEIGHTS = new float[4];

    public static boolean on() {
        return FxConfig.vividSky && FxConfig.vividSkyStrength > 0.0 && DynamicFogHandler.active();
    }

    public static float strength() {
        return (float) FxConfig.vividSkyStrength;
    }

    public static void update(ClientLevel level, float partialTick, float facing) {
        float sunY = Mth.cos(level.getTimeOfDay(partialTick) * Mth.TWO_PI);
        float day = SkyGlow.smooth(0.12F, 0.45F, sunY);
        float golden = (1F - day) * SkyGlow.smooth(-0.06F, 0.06F, sunY);
        float dusk = (1F - day - golden) * SkyGlow.smooth(-0.3F, -0.06F, sunY);
        WEIGHTS[0] = Math.max(0F, 1F - day - golden - dusk);
        WEIGHTS[1] = dusk;
        WEIGHTS[2] = golden;
        WEIGHTS[3] = day;
        float rain = level.getRainLevel(partialTick);
        float thunder = level.getThunderLevel(partialTick);
        mix(ZENITHS, ZENITH, rain, thunder);
        mix(HORIZONS, SUNWARD, rain, thunder);
        mix(AWAY, HORIZON, rain, thunder);
        float toward = SkyGlow.smooth(0.05F, 0.85F, facing) * SkyGlow.smooth(0.55F, 0.95F, dusk + golden);
        for (int i = 0; i < 3; i++) HORIZON[i] = Mth.lerp(toward, HORIZON[i], SUNWARD[i]);
    }

    private static void mix(float[][] keys, float[] out, float rain, float thunder) {
        float r = 0F, g = 0F, b = 0F;
        for (int i = 0; i < 4; i++) {
            r += keys[i][0] * WEIGHTS[i];
            g += keys[i][1] * WEIGHTS[i];
            b += keys[i][2] * WEIGHTS[i];
        }
        float grey = (r * 0.3F + g * 0.59F + b * 0.11F) * (1F - 0.35F * thunder);
        float wet = rain * 0.75F;
        out[0] = Mth.lerp(wet, r, grey * 0.8F);
        out[1] = Mth.lerp(wet, g, grey * 0.82F);
        out[2] = Mth.lerp(wet, b, grey * 0.88F);
    }

    public static Vec3 sky(ClientLevel level, float partialTick, Vec3 original) {
        update(level, partialTick, 0F);
        float k = strength();
        return new Vec3(Mth.lerp(k, original.x, ZENITH[0]), Mth.lerp(k, original.y, ZENITH[1]), Mth.lerp(k, original.z, ZENITH[2]));
    }
}
