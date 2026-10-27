package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import net.minecraft.util.Mth;

public final class TorchFlickerCheck {
    public static void main(String[] args) {
        int samples = 60 * 120;
        int a = (int) Mth.getSeed(22004, 101, 9), b = (int) Mth.getSeed(22011, 101, 9);
        double[] wa = new double[samples], wb = new double[samples], ws = new double[samples];
        double step = 0;
        for (int i = 0; i < samples; i++) {
            float t = 1000F + i / 60F;
            wa[i] = TorchFlicker.wave(t, a, false);
            wb[i] = TorchFlicker.wave(t, b, false);
            ws[i] = TorchFlicker.wave(t, a, true);
            check(Math.abs(wa[i]) < 3.9 && Math.abs(ws[i]) < 3.9, "wave must fit the packed range: " + wa[i]);
            if (i > 0) step = Math.max(step, Math.abs(wa[i] - wa[i - 1]));
        }
        double corr = corr(wa, wb);
        System.out.printf("std %.3f soul std %.3f max step/frame %.3f corr %.3f%n", std(wa), std(ws), step, corr);
        check(step < 0.12, "wave must be smooth at 60 fps, step " + step);
        check(Math.abs(corr) < 0.5, "two torches must not pulse together, corr " + corr);
        check(std(ws) < std(wa), "soul flames must be calmer");
        check(std(wa) > 0.15, "wave must actually move");
        System.out.println("Torch flicker OK");
    }

    private static double std(double[] v) {
        double m = 0, s = 0;
        for (double x : v) m += x;
        m /= v.length;
        for (double x : v) s += (x - m) * (x - m);
        return Math.sqrt(s / v.length);
    }

    private static double corr(double[] x, double[] y) {
        double mx = 0, my = 0;
        for (int i = 0; i < x.length; i++) { mx += x[i]; my += y[i]; }
        mx /= x.length;
        my /= y.length;
        double sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < x.length; i++) {
            sxy += (x[i] - mx) * (y[i] - my);
            sxx += (x[i] - mx) * (x[i] - mx);
            syy += (y[i] - my) * (y[i] - my);
        }
        return sxy / Math.sqrt(sxx * syy);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
