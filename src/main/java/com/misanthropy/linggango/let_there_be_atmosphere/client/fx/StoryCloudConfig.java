package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.config.ConfigText;
import net.minecraft.util.Mth;

import java.util.Properties;

public final class StoryCloudConfig {
    private StoryCloudConfig() {}

    public static boolean enabled       = false;
    public static double  cellSize      = 24.0;
    public static double  thickness     = 14.0;
    public static double  heightOffset  = 0.0;
    public static double  coverage      = 0.45;
    public static double  speed         = 1.0;
    public static double  widthScale    = 1.0;
    public static double  lengthScale   = 1.0;
    public static double  alpha         = 0.85;
    public static double  bottomFade    = 0.25;
    public static double  topFade       = 0.0;
    public static boolean secondLayer   = false;
    public static double  secondOffset  = 28.0;
    public static double  secondCoverage = 0.30;

    public static boolean shading       = true;
    public static boolean lighting      = true;
    public static int     tintHex       = -1;
    public static double  tintStrength  = 1.0;
    public static double  fogFade       = 1.0;
    public static double  heightVariation = 0.35;
    public static double  detail        = 0.35;
    public static boolean pastel        = false;
    public static double  softEdges     = 0.0;

    private static final String P = "clouds.";

    public static void read(Properties p) {
        enabled        = bool(p, "storyClouds",     false);
        cellSize       = num(p, "cellSize",         24.0,  8.0,  96.0);
        thickness      = num(p, "thickness",        14.0,  2.0,  64.0);
        heightOffset   = num(p, "heightOffset",      0.0, -64.0, 128.0);
        coverage       = num(p, "coverage",         0.45,  0.05,  0.95);
        speed          = num(p, "speed",             1.0,  0.0,  10.0);
        widthScale     = num(p, "widthScale",        1.0,  0.25,  4.0);
        lengthScale    = num(p, "lengthScale",       1.0,  0.25,  4.0);
        alpha          = num(p, "alpha",            0.85,  0.1,   1.0);
        bottomFade     = num(p, "bottomFade",       0.25,  0.0,   0.95);
        topFade        = num(p, "topFade",           0.0,  0.0,   0.95);
        secondLayer    = bool(p, "secondLayer",     false);
        secondOffset   = num(p, "secondOffset",     28.0, -64.0, 128.0);
        secondCoverage = num(p, "secondCoverage",   0.30,  0.05,  0.95);
        shading        = bool(p, "shading",         true);
        lighting       = bool(p, "lighting",        true);
        tintHex        = hex(p, "tintHex",          -1);
        tintStrength   = num(p, "tintStrength",     1.0,   0.0,   1.0);
        fogFade        = num(p, "fogFade",          1.0,   0.0,   1.0);
        heightVariation = num(p, "heightVariation", 0.35,  0.0,   1.0);
        detail         = num(p, "detail",           0.35,  0.0,   0.9);
        pastel         = bool(p, "pastel",          false);
        softEdges      = num(p, "softEdges",        0.0,   0.0,   3.0);
    }

    public static void write(ConfigText out) {
        out.section("Story clouds");
        out.note("Replaces the flat cloud sheet with blocky drifting cloud banks,",
                 "shaped from noise rather than from a texture.");
        out.put(P + "storyClouds", enabled);
        out.note("Size of one cloud block across, and how tall it stands.");
        out.put(P + "cellSize", cellSize);
        out.put(P + "thickness", thickness);
        out.note("Height above or below the usual cloud layer.");
        out.put(P + "heightOffset", heightOffset);
        out.note("Share of the sky the banks fill, and how fast they drift.");
        out.put(P + "coverage", coverage);
        out.put(P + "speed", speed);
        out.note("Stretch across and along the wind. Above 1 makes longer banks.");
        out.put(P + "widthScale", widthScale);
        out.put(P + "lengthScale", lengthScale);
        out.note("Body opacity, then how far the bottom and top faces fade out.");
        out.put(P + "alpha", alpha);
        out.put(P + "bottomFade", bottomFade);
        out.put(P + "topFade", topFade);
        out.note("A second bank at its own height and coverage.");
        out.put(P + "secondLayer", secondLayer);
        out.put(P + "secondOffset", secondOffset);
        out.put(P + "secondCoverage", secondCoverage);

        out.note("Face shading. Off gives flat clouds, which is what you want next to",
                 "a mod that removes block shading.");
        out.put(P + "shading", shading);
        out.note("Soft lighting: darker cores and brighter, feathered edges underneath, sunlit sides and warm sunsets.");
        out.put(P + "lighting", lighting);
        out.note("Cloud colour. auto keeps the colour the game already gives them;",
                 "a hex pulls them toward that colour instead.");
        if (tintHex < 0) out.put(P + "tintHex", "auto");
        else out.putHex(P + "tintHex", tintHex);
        out.put(P + "tintStrength", tintStrength);
        out.note("How much distant clouds take on the fog colour as they fade out at the",
                 "edge of the cloud range. 0 keeps their own colour all the way out.");
        out.put(P + "fogFade", fogFade);
        out.note("How much the bank height varies from cell to cell, and how ragged",
                 "the edges are.");
        out.put(P + "heightVariation", heightVariation);
        out.put(P + "detail", detail);
        out.note("Pastel look: see-through cartoon clouds that take on the sky's colour,",
                 "with soft fading sides. Replaces Cloud Lighting and face shading.");
        out.put(P + "pastel", pastel);
        out.note("Blurs cloud outlines like a camera focused on the ground. 0 keeps them sharp.");
        out.put(P + "softEdges", softEdges);
    }

    public static int signature() {
        int hash = Double.hashCode(cellSize);
        hash = hash * 31 + Double.hashCode(thickness);
        hash = hash * 31 + Double.hashCode(coverage);
        hash = hash * 31 + Double.hashCode(widthScale);
        hash = hash * 31 + Double.hashCode(lengthScale);
        hash = hash * 31 + Double.hashCode(alpha);
        hash = hash * 31 + Double.hashCode(bottomFade);
        hash = hash * 31 + Double.hashCode(topFade);
        hash = hash * 31 + (secondLayer ? 1 : 0);
        hash = hash * 31 + Double.hashCode(secondOffset);
        hash = hash * 31 + Double.hashCode(secondCoverage);
        hash = hash * 31 + (shading ? 1 : 0);
        hash = hash * 31 + Double.hashCode(heightVariation);
        hash = hash * 31 + Double.hashCode(detail);
        return hash;
    }

    private static double num(Properties p, String key, double def, double min, double max) {
        String v = p.getProperty(P + key);
        if (v == null) return Mth.clamp(def, min, max);
        try { return Mth.clamp(Double.parseDouble(v), min, max); }
        catch (Exception e) { return def; }
    }

    private static int hex(Properties p, String key, int def) {
        String v = p.getProperty(P + key);
        if (v == null) return def;
        v = v.trim();
        if (v.equalsIgnoreCase("auto") || v.equalsIgnoreCase("none")) return -1;
        try { return Integer.parseInt(v.startsWith("#") ? v.substring(1) : v, 16) & 0xFFFFFF; }
        catch (Exception e) { return def; }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(P + key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }
}
