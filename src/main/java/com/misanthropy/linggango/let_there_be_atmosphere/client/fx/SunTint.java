package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

public final class SunTint {
    private SunTint() {}

    private static final float NARROW_WINDOW = 0.40F;

    public static boolean sampleNarrow(float timeOfDay, float[] out) {
        float cos = Mth.cos(timeOfDay * ((float) Math.PI * 2F));
        if (cos < -NARROW_WINDOW || cos > NARROW_WINDOW) return false;

        float t = cos / NARROW_WINDOW * 0.5F + 0.5F;
        float a = 1F - (1F - Mth.sin(t * (float) Math.PI)) * 0.99F;
        out[0] = t * 0.3F + 0.7F;
        out[1] = t * t * 0.7F + 0.2F;
        out[2] = 0.2F;
        out[3] = a * a;
        return true;
    }

    public static float lookFactor(Level level, Vector3f look, float partialTick) {
        float side = Mth.sin(level.getSunAngle(partialTick)) > 0F ? -1F : 1F;
        return Math.max(0F, look.x() * side);
    }

    public static float amount(Level level, Vector3f look, float partialTick,
                               boolean wide, double strength, float[] out) {
        if (strength <= 0.0) return 0F;
        float look0 = lookFactor(level, look, partialTick);
        if (look0 <= 0F) return 0F;
        boolean glowing = wide
                ? DayCurves.sunriseSunset(level.getDayTime() % DayCurves.PERIOD + partialTick, out)
                : sampleNarrow(level.getTimeOfDay(partialTick), out);
        if (!glowing) return 0F;
        return Mth.clamp(look0 * out[3] * (float) strength, 0F, 1F);
    }
}
