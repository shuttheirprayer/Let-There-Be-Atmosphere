package com.misanthropy.linggango.let_there_be_atmosphere.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public final class Tempo {
    private Tempo() {}

    private static volatile IntSupplier source = () -> 1;
    private static volatile BooleanSupplier rainSource = () -> false;

    private static int every = 1;
    private static int sub;
    private static long ticks;
    private static boolean due = true;
    private static double clock;
    private static long clockAt;

    public static void slowWith(IntSupplier every) {
        source = every == null ? () -> 1 : every;
    }

    public static void holdRainWith(BooleanSupplier held) {
        rainSource = held == null ? () -> false : held;
    }

    static void advance() {
        every = Math.max(1, source.getAsInt());
        if (++sub >= every) {
            sub = 0;
            ticks++;
            due = true;
        } else {
            due = false;
        }
    }

    public static boolean due() {
        return due;
    }

    public static boolean slowed() {
        return every > 1;
    }

    public static int every() {
        return every;
    }

    public static float rate() {
        return 1F / every;
    }

    public static float partial(float partial) {
        return every > 1 ? (sub + partial) / every : partial;
    }

    public static double ticks(float partial) {
        return ticks + partial(partial);
    }

    public static long ticks() {
        return ticks;
    }

    public static boolean rainHeld() {
        return rainSource.getAsBoolean();
    }

    public static double seconds() {
        long now = Util.getNanos();
        if (now != clockAt) {
            if (clockAt != 0L && !Minecraft.getInstance().isPaused()) clock += Math.min((now - clockAt) * 1.0E-9, 0.25) / every;
            clockAt = now;
        }
        return clock;
    }

    public static long millis() {
        return (long) (seconds() * 1000.0);
    }
}
