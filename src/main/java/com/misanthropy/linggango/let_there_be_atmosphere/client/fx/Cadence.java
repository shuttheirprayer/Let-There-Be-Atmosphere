package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Cadence {
    private Cadence() {}

    public static boolean enabled = true;
    public static int maxInterval = 8;
    public static float minHz = 144F;

    private static long frame;
    private static long lastAt;
    private static float frameNanos = 1.0E7F;
    private static int interval = 1;
    private static int refreshHz = 60;
    private static long refreshAt = Long.MIN_VALUE / 2;

    static void frame() {
        long now = System.nanoTime();
        if (lastAt != 0L) frameNanos += (Math.min(now - lastAt, 100_000_000L) - frameNanos) * 0.05F;
        lastAt = now;
        frame++;
        if (frame - refreshAt >= 1024L) {
            refreshAt = frame;
            refreshHz = Math.max(Minecraft.getInstance().getWindow().getRefreshRate(), 1);
        }
        float period = 1.0E9F / Math.max(refreshHz, minHz);
        interval = enabled ? Mth.clamp((int) (period / frameNanos), 1, Math.max(maxInterval, 1)) : 1;
    }

    static int interval() {
        return interval;
    }

    static boolean step() {
        return interval <= 1 || frame % interval == 0;
    }

    static boolean due(int age, int phase) {
        return interval <= 1 || age + 1 >= interval || frame % interval == phase % interval && age > 0;
    }
}
