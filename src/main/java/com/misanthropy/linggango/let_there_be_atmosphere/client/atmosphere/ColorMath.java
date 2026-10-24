package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import java.util.Locale;

public final class ColorMath {
    private ColorMath() {}

    public static int hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        s = clamp01(s);
        v = clamp01(v);
        float c = v * s;
        float x = c * (1f - Math.abs((h * 6f) % 2f - 1f));
        float m = v - c;
        float r, g, b;
        switch ((int) (h * 6f) % 6) {
            case 0 -> { r = c; g = x; b = 0f; }
            case 1 -> { r = x; g = c; b = 0f; }
            case 2 -> { r = 0f; g = c; b = x; }
            case 3 -> { r = 0f; g = x; b = c; }
            case 4 -> { r = x; g = 0f; b = c; }
            default -> { r = c; g = 0f; b = x; }
        }
        return (byte8(r + m) << 16) | (byte8(g + m) << 8) | byte8(b + m);
    }

    public static void rgbToHsv(int rgb, float[] out) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float mx = Math.max(r, Math.max(g, b));
        float mn = Math.min(r, Math.min(g, b));
        float d = mx - mn;
        float h = 0f;
        if (d > 0f) {
            if (mx == r)      h = ((g - b) / d) % 6f;
            else if (mx == g) h = (b - r) / d + 2f;
            else              h = (r - g) / d + 4f;
            h /= 6f;
            if (h < 0f) h += 1f;
        }
        out[0] = h;
        out[1] = mx == 0f ? 0f : d / mx;
        out[2] = mx;
    }

    public static int withChannel(int rgb, int index, int value) {
        int shift = 16 - index * 8;
        return (rgb & ~(0xFF << shift)) | ((clampByte(value)) << shift);
    }

    public static int channel(int rgb, int index) {
        return (rgb >> (16 - index * 8)) & 0xFF;
    }

    public static int parseHex(String text) {
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() == 3) {
            t = new String(new char[]{t.charAt(0), t.charAt(0), t.charAt(1), t.charAt(1), t.charAt(2), t.charAt(2)});
        }
        if (t.length() != 6) return -1;
        try {
            return Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    public static boolean isLight(int rgb) {
        return ((rgb >> 16 & 0xFF) * 0.299f + (rgb >> 8 & 0xFF) * 0.587f + (rgb & 0xFF) * 0.114f) > 150f;
    }

    private static int clampByte(int v) { return v < 0 ? 0 : Math.min(v, 255); }

    private static float clamp01(float v) { return v < 0f ? 0f : Math.min(v, 1f); }

    private static int byte8(float v) { return (int) (clamp01(v) * 255f + 0.5f); }
}
