package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import java.util.Random;

public final class ColorMathCheck {
    public static void main(String[] args) {
        float[] hsv = new float[3];
        Random rand = new Random(1234L);

        for (int i = 0; i < 20000; i++) {
            int rgb = rand.nextInt(0x1000000);
            ColorMath.rgbToHsv(rgb, hsv);
            int back = ColorMath.hsvToRgb(hsv[0], hsv[1], hsv[2]);
            check(rgb == back, "round trip " + ColorMath.hex(rgb) + " -> " + ColorMath.hex(back));
            check(hsv[0] >= 0f && hsv[0] <= 1f, "hue range " + hsv[0]);
            check(hsv[1] >= 0f && hsv[1] <= 1f, "sat range " + hsv[1]);
            check(hsv[2] >= 0f && hsv[2] <= 1f, "val range " + hsv[2]);
        }

        check(ColorMath.hsvToRgb(0f, 1f, 1f) == 0xFF0000, "pure red");
        check(ColorMath.hsvToRgb(1f / 3f, 1f, 1f) == 0x00FF00, "pure green");
        check(ColorMath.hsvToRgb(2f / 3f, 1f, 1f) == 0x0000FF, "pure blue");
        check(ColorMath.hsvToRgb(1f, 1f, 1f) == 0xFF0000, "hue wrap");
        check(ColorMath.hsvToRgb(0.5f, 0f, 0f) == 0x000000, "black");

        check(ColorMath.parseHex("#7FB238") == 0x7FB238, "parse with hash");
        check(ColorMath.parseHex("7fb238") == 0x7FB238, "parse lowercase");
        check(ColorMath.parseHex("#f0a") == 0xFF00AA, "parse short form");
        check(ColorMath.parseHex("#12345") == -1, "reject short");
        check(ColorMath.parseHex("zzzzzz") == -1, "reject garbage");
        check(ColorMath.hex(0x0088FF).equals("#0088FF"), "format");

        check(ColorMath.withChannel(0x000000, 0, 255) == 0xFF0000, "set red");
        check(ColorMath.withChannel(0xFFFFFF, 1, 0) == 0xFF00FF, "clear green");
        check(ColorMath.withChannel(0x123456, 2, 0x99) == 0x123499, "set blue");
        check(ColorMath.withChannel(0x000000, 2, 900) == 0x0000FF, "clamp high");
        check(ColorMath.channel(0x123456, 1) == 0x34, "read green");

        check(ColorMath.isLight(0xFFFFFF), "white is light");
        check(!ColorMath.isLight(0x1A1E36), "night blue is dark");

        System.out.println("ColorMath OK");
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
