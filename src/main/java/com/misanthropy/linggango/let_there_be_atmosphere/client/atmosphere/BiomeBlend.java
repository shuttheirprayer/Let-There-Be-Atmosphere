package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.config.AtmosphereConfigManager;
import com.misanthropy.linggango.let_there_be_atmosphere.config.AtmosphereConfigManager.AtmosphereSettings;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.phys.Vec3;

public final class BiomeBlend {
    private BiomeBlend() {}

    private static final double[] KERNEL = {0.0, 1.0, 4.0, 6.0, 4.0, 1.0, 0.0};
    private static final int BREADTH = 6;
    private static final int RADIUS  = 2;

    private static final double[] WX = new double[BREADTH];
    private static final double[] WY = new double[BREADTH];
    private static final double[] WZ = new double[BREADTH];

    private static final AtmosphereSettings BLEND   = new AtmosphereSettings();
    private static final AtmosphereSettings NEUTRAL = new AtmosphereSettings();

    private static boolean anyConfigured = false;
    private static float configuredWeight = 0F;
    private static float fogR = 0.5F, fogG = 0.6F, fogB = 0.7F;

    private static float covSky, covAmbient, covDawn, covNight, covHaze;

    public static AtmosphereSettings settings() { return anyConfigured ? BLEND : null; }

    public static float coverage() { return configuredWeight; }

    public static float fogR() { return fogR; }
    public static float fogG() { return fogG; }
    public static float fogB() { return fogB; }

    public static float skyHexCoverage()        { return covSky; }
    public static float ambientHexCoverage()    { return covAmbient; }
    public static float dawnDuskHexCoverage()   { return covDawn; }
    public static float nightHexCoverage()      { return covNight; }
    public static float hazeHexCoverage()       { return covHaze; }

    public static void reset() {
        anyConfigured = false;
        configuredWeight = 0F;
        covSky = covAmbient = covDawn = covNight = covHaze = 0F;
    }

    public static void update(Level level, Vec3 pos) {
        if (AtmosphereConfigManager.ATMOSPHERES.isEmpty()) {
            int fc = DynamicFogHandler.defaultFogHex;
            fogR = ((fc >> 16) & 0xFF) / 255F;
            fogG = ((fc >> 8)  & 0xFF) / 255F;
            fogB = ( fc        & 0xFF) / 255F;
            reset();
            return;
        }

        double qx = pos.x * 0.25 - 0.5, qy = pos.y * 0.25 - 0.5, qz = pos.z * 0.25 - 0.5;
        int ix = Mth.floor(qx), iy = Mth.floor(qy), iz = Mth.floor(qz);
        double rx = qx - ix, ry = qy - iy, rz = qz - iz;

        for (int i = 0; i < BREADTH; i++) {
            WX[i] = Mth.lerp(rx, KERNEL[i + 1], KERNEL[i]);
            WY[i] = Mth.lerp(ry, KERNEL[i + 1], KERNEL[i]);
            WZ[i] = Mth.lerp(rz, KERNEL[i + 1], KERNEL[i]);
        }

        BiomeManager biomes = level.getBiomeManager();
        NEUTRAL.skyTintStr = (float) DynamicFogHandler.skyFogBlendStrength;
        NEUTRAL.fogDensity = (float) DynamicFogHandler.fogDensity;
        NEUTRAL.nightFogStartMul = (float) DynamicFogHandler.nightFogStartMul;
        NEUTRAL.nightFogEndMul = (float) DynamicFogHandler.nightFogEndMul;

        double total = 0, configured = 0;
        double fr = 0, fg = 0, fb = 0;
        double fogStart = 0, fogEnd = 0;
        double skyTint = 0, rainMult = 0, nearHaze = 0, voidStr = 0, waterDist = 0;
        double sat = 0, bright = 0, dawnStr = 0, nightStr = 0, heightFog = 0, sphere = 0;
        double fogDensity = 0, nightNear = 0, nightFar = 0, hazeStr = 0;

        double skyR = 0, skyG = 0, skyB = 0, skyW = 0;
        double rainR = 0, rainG = 0, rainB = 0, rainW = 0;
        double thunR = 0, thunG = 0, thunB = 0, thunW = 0;
        double watR = 0, watG = 0, watB = 0, watW = 0;
        double ambR = 0, ambG = 0, ambB = 0, ambW = 0;
        double dawnR = 0, dawnG = 0, dawnB = 0, dawnW = 0;
        double nitR = 0, nitG = 0, nitB = 0, nitW = 0;
        double hazR = 0, hazG = 0, hazB = 0, hazW = 0;

        Holder<Biome> lastHolder = null;
        AtmosphereSettings lastSettings = null;

        for (int z = 0; z < BREADTH; z++) {
            double wz = WZ[z];
            if (wz <= 0.0) continue;
            for (int x = 0; x < BREADTH; x++) {
                double wxz = WX[x] * wz;
                if (wxz <= 0.0) continue;
                for (int y = 0; y < BREADTH; y++) {
                    double w = wxz * WY[y];
                    if (w <= 0.0) continue;

                    Holder<Biome> holder = biomes.getNoiseBiomeAtQuart(
                            ix - RADIUS + x, iy - RADIUS + y, iz - RADIUS + z);
                    AtmosphereSettings s;
                    if (holder == lastHolder) {
                        s = lastSettings;
                    } else {
                        ResourceKey<Biome> key = holder instanceof Holder.Reference<Biome> ref ? ref.key() : null;
                        s = key == null ? null : AtmosphereConfigManager.ATMOSPHERES.get(key.location());
                        lastHolder = holder;
                        lastSettings = s;
                    }
                    AtmosphereSettings v = s != null ? s : NEUTRAL;

                    total += w;
                    if (s != null) {
                        configured += w;
                        fogStart += s.fogStart * w;
                        fogEnd   += s.fogEnd   * w;
                    }

                    int fc = s != null ? s.fogHex : DynamicFogHandler.defaultFogHex;
                    fr += ((fc >> 16) & 0xFF) / 255.0 * w;
                    fg += ((fc >> 8)  & 0xFF) / 255.0 * w;
                    fb += (fc         & 0xFF) / 255.0 * w;

                    skyTint   += v.skyTintStr   * w;
                    rainMult  += v.rainMult     * w;
                    nearHaze  += v.nearHaze     * w;
                    voidStr   += v.voidFogStr   * w;
                    waterDist += v.waterFogDist * w;
                    sat       += v.satBoost     * w;
                    bright    += v.brightness   * w;
                    dawnStr   += v.dawnDuskStr  * w;
                    nightStr  += v.nightStr     * w;
                    heightFog += v.heightFogStr * w;
                    fogDensity += v.fogDensity       * w;
                    nightNear  += v.nightFogStartMul * w;
                    nightFar   += v.nightFogEndMul   * w;
                    hazeStr    += v.hazeStrength     * w;
                    if (v.sphereFog) sphere += w;

                    if (v.skyHex >= 0)       { skyW  += w; skyR  += red(v.skyHex) * w;       skyG  += green(v.skyHex) * w;       skyB  += blue(v.skyHex) * w; }
                    if (v.rainFogHex >= 0)   { rainW += w; rainR += red(v.rainFogHex) * w;   rainG += green(v.rainFogHex) * w;   rainB += blue(v.rainFogHex) * w; }
                    if (v.thunderFogHex >= 0){ thunW += w; thunR += red(v.thunderFogHex) * w;thunG += green(v.thunderFogHex) * w;thunB += blue(v.thunderFogHex) * w; }
                    if (v.waterFogHex >= 0)  { watW  += w; watR  += red(v.waterFogHex) * w;  watG  += green(v.waterFogHex) * w;  watB  += blue(v.waterFogHex) * w; }
                    if (v.ambientHex >= 0)   { ambW  += w; ambR  += red(v.ambientHex) * w;   ambG  += green(v.ambientHex) * w;   ambB  += blue(v.ambientHex) * w; }
                    if (v.dawnDuskHex >= 0)  { dawnW += w; dawnR += red(v.dawnDuskHex) * w;  dawnG += green(v.dawnDuskHex) * w;  dawnB += blue(v.dawnDuskHex) * w; }
                    if (v.nightHex >= 0)     { nitW  += w; nitR  += red(v.nightHex) * w;     nitG  += green(v.nightHex) * w;     nitB  += blue(v.nightHex) * w; }
                    if (v.hazeHex >= 0)      { hazW  += w; hazR  += red(v.hazeHex) * w;      hazG  += green(v.hazeHex) * w;      hazB  += blue(v.hazeHex) * w; }
                }
            }
        }

        if (total <= 0.0) { reset(); return; }

        fogR = (float) (fr / total);
        fogG = (float) (fg / total);
        fogB = (float) (fb / total);

        configuredWeight = (float) (configured / total);
        anyConfigured = configured > 0.0;
        if (!anyConfigured) {
            covSky = covAmbient = covDawn = covNight = covHaze = 0F;
            return;
        }

        BLEND.fogStart = (float) (fogStart / configured);
        BLEND.fogEnd   = (float) (fogEnd   / configured);

        BLEND.skyTintStr   = (float) (skyTint   / total);
        BLEND.rainMult     = (float) (rainMult  / total);
        BLEND.nearHaze     = (float) (nearHaze  / total);
        BLEND.voidFogStr   = (float) (voidStr   / total);
        BLEND.waterFogDist = (float) (waterDist / total);
        BLEND.satBoost     = (float) (sat       / total);
        BLEND.brightness   = (float) (bright    / total);
        BLEND.dawnDuskStr  = (float) (dawnStr   / total);
        BLEND.nightStr     = (float) (nightStr  / total);
        BLEND.heightFogStr = (float) (heightFog / total);
        BLEND.fogDensity       = (float) (fogDensity / total);
        BLEND.nightFogStartMul = (float) (nightNear  / total);
        BLEND.nightFogEndMul   = (float) (nightFar   / total);
        BLEND.hazeStrength     = (float) (hazeStr    / total);
        BLEND.sphereFog    = sphere * 2.0 > total;

        covSky     = (float) (skyW / total); BLEND.skyHex        = pack(skyR,  skyG,  skyB,  skyW);
        covAmbient = (float) (ambW / total); BLEND.ambientHex    = pack(ambR,  ambG,  ambB,  ambW);
        BLEND.rainFogHex    = pack(rainR, rainG, rainB, rainW);
        BLEND.thunderFogHex = pack(thunR, thunG, thunB, thunW);
        BLEND.waterFogHex   = pack(watR,  watG,  watB,  watW);
        covDawn       = (float) (dawnW / total); BLEND.dawnDuskHex   = pack(dawnR, dawnG, dawnB, dawnW);
        covNight      = (float) (nitW  / total); BLEND.nightHex      = pack(nitR,  nitG,  nitB,  nitW);
        covHaze       = (float) (hazW  / total); BLEND.hazeHex       = pack(hazR,  hazG,  hazB,  hazW);
    }

    private static int red(int hex)   { return (hex >> 16) & 0xFF; }
    private static int green(int hex) { return (hex >> 8)  & 0xFF; }
    private static int blue(int hex)  { return hex         & 0xFF; }

    private static int pack(double r, double g, double b, double w) {
        if (w <= 0.0) return -1;
        int ri = Mth.clamp((int) Math.round(r / w), 0, 255);
        int gi = Mth.clamp((int) Math.round(g / w), 0, 255);
        int bi = Mth.clamp((int) Math.round(b / w), 0, 255);
        return (ri << 16) | (gi << 8) | bi;
    }
}
