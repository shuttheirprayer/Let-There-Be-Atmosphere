package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class LightTint {
    private LightTint() {}

    public static DynamicTexture lightmap;

    private static final float[] SUNRISE = new float[4];
    private static final float[] BRIGHTNESS = new float[16];
    private static final float[] BLOCK_WEIGHT = new float[16];
    private static final float[] PALETTE = new float[9];

    public static void apply(NativeImage pixels) {
        if (!DynamicFogHandler.active() || !FxConfig.lightTintEnabled) {
            Diagnostics.skip(Diagnostics.LIGHTMAP, "coloured light is off");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || pixels == null) return;
        Diagnostics.once("lightmap tint injection is live");

        if (mc.player != null && (mc.player.hasEffect(MobEffects.NIGHT_VISION)
                || mc.player.hasEffect(MobEffects.DARKNESS))) {
            Diagnostics.skip(Diagnostics.LIGHTMAP, "night vision or darkness owns the lightmap");
            return;
        }

        float blockStrength = (float) FxConfig.blockLightStrength;
        float skyStrength   = (float) FxConfig.skyLightStrength;

        float dayTime = level.getDayTime() % DayCurves.PERIOD + mc.getFrameTime();
        float night = DayCurves.nightFraction(dayTime);

        float sunStrength = 0F;
        if (FxConfig.sunLightTint && FxConfig.sunLightStrength > 0.0 && DayCurves.sunriseSunset(dayTime, SUNRISE)) {
            sunStrength = SUNRISE[3] * (float) FxConfig.sunLightStrength;
        }
        if (blockStrength <= 0F && skyStrength <= 0F && sunStrength <= 0F) {
            Diagnostics.skip(Diagnostics.LIGHTMAP, "every tint strength is zero");
            return;
        }

        DimensionType dim = level.dimensionType();
        float[] brightness = BRIGHTNESS;
        for (int i = 0; i < 16; i++) brightness[i] = LightTexture.getBrightness(dim, i);

        float[] palette = PALETTE;
        palette(FxConfig.blockLightHex, palette, 0);
        palette(FxConfig.skyLightNightHex, palette, 3);
        palette(FxConfig.sunLightHex, palette, 6);
        float[] blockWeight = BLOCK_WEIGHT;
        float warmStr = Math.max(0F, blockStrength);
        for (int block = 0; block < 16; block++) blockWeight[block] = Math.max(0F, brightness[block]) * warmStr;
        float nightClamped = Mth.clamp(night, 0F, 1F);
        float coolStr = Math.max(0F, skyStrength);
        float sunStr = Mth.clamp(sunStrength, 0F, 1F);
        for (int sky = 0; sky < 16; sky++) {
            float skyLight = Math.max(0F, brightness[sky]);
            float skyWeight = skyLight * nightClamped * coolStr;
            float sunWeight = skyLight * sunStr;
            for (int block = 0; block < 16; block++) {
                int pixel = pixels.getPixelRGBA(block, sky);
                int tinted = mix(pixel, blockWeight[block], skyWeight, sunWeight,
                        Math.max(0F, brightness[block]), skyLight, palette);
                if (tinted != pixel) pixels.setPixelRGBA(block, sky, tinted);
            }
        }
        Diagnostics.mark(Diagnostics.LIGHTMAP, Diagnostics.detail()
                ? String.format("night %.2f sun %.3f", night, sunStrength) : null);
    }

    public static int tint(int pixel, float blockLight, float skyLight, float night,
                           int warmHex, float warmStr, int coolHex, float coolStr,
                           int sunHex, float sunStr) {
        float[] palette = new float[9];
        palette(warmHex, palette, 0);
        palette(coolHex, palette, 3);
        palette(sunHex, palette, 6);
        float block = Math.max(0F, blockLight);
        float sky = Math.max(0F, skyLight);
        return mix(pixel, block * Math.max(0F, warmStr), sky * Mth.clamp(night, 0F, 1F) * Math.max(0F, coolStr),
                sky * Mth.clamp(sunStr, 0F, 1F), block, sky, palette);
    }

    private static int mix(int pixel, float blockWeight, float skyWeight, float sunWeight,
                           float blockLight, float skyLight, float[] palette) {
        float total = blockWeight + skyWeight + sunWeight;
        if (total <= 0F) return pixel;

        float mixR = (palette[0] * blockWeight + palette[3] * skyWeight + palette[6] * sunWeight) / total;
        float mixG = (palette[1] * blockWeight + palette[4] * skyWeight + palette[7] * sunWeight) / total;
        float mixB = (palette[2] * blockWeight + palette[5] * skyWeight + palette[8] * sunWeight) / total;

        float corner = Mth.clamp(blockLight * skyLight, 0F, 1F);
        float fullBrightWashout = 1F - corner * corner * corner * corner;

        float hold = Math.min(1F, total) * fullBrightWashout;
        mixR = Mth.lerp(hold, 1F, mixR);
        mixG = Mth.lerp(hold, 1F, mixG);
        mixB = Mth.lerp(hold, 1F, mixB);

        int r = channel(pixel, 0, mixR);
        int g = channel(pixel, 8, mixG);
        int b = channel(pixel, 16, mixB);
        return (pixel & 0xFF000000) | (b << 16) | (g << 8) | r;
    }

    private static void palette(int hex, float[] out, int at) {
        out[at] = norm(hex, 16);
        out[at + 1] = norm(hex, 8);
        out[at + 2] = norm(hex, 0);
    }

    private static float norm(int hex, int shift) {
        int max = Math.max((hex >> 16) & 0xFF, Math.max((hex >> 8) & 0xFF, hex & 0xFF));
        if (max <= 0) return 1F;
        return ((hex >> shift) & 0xFF) / (float) max;
    }

    private static int channel(int pixel, int shift, float scale) {
        return Mth.clamp(Math.round(((pixel >> shift) & 0xFF) * scale), 0, 255);
    }
}
