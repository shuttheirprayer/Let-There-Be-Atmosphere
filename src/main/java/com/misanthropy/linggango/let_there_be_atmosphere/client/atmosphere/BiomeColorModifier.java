package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.config.AtmosphereConfigManager;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BiomeColorModifier {
    private BiomeColorModifier() {}

    private static final Map<Biome, AtmosphereConfigManager.AtmosphereSettings> CACHE = new ConcurrentHashMap<>();
    private static final AtmosphereConfigManager.AtmosphereSettings NONE =
            new AtmosphereConfigManager.AtmosphereSettings();

    public static void clearCache() {
        CACHE.clear();
    }

    private enum Type { GRASS, FOLIAGE, WATER }

    public static int grass  (int original, Biome biome) { return apply(original, biome, Type.GRASS);   }
    public static int foliage(int original, Biome biome) { return apply(original, biome, Type.FOLIAGE); }
    public static int water  (int original, Biome biome) { return apply(original, biome, Type.WATER);   }

    private static int apply(int original, Biome biome, Type type) {
        if (!DynamicFogHandler.running() || !DynamicFogHandler.biomeColorsEnabled) return original;

        int base = original;
        if (AtmosphereConfigManager.hasColorOverrides()) {
            AtmosphereConfigManager.AtmosphereSettings s = lookup(biome);
            if (s != null) {
                int ov = switch (type) {
                    case GRASS   -> s.grassHex;
                    case FOLIAGE -> s.foliageHex;
                    case WATER   -> s.waterHex;
                };
                if (ov >= 0) base = ov;
            }
        }

        float sat, bri;
        if (type == Type.WATER) {
            sat = (float) DynamicFogHandler.waterSaturation;
            bri = (float) DynamicFogHandler.waterBrightness;
        } else {
            sat = (float) DynamicFogHandler.vegSaturation;
            bri = (float) DynamicFogHandler.vegBrightness;
        }

        float sunny = type == Type.WATER ? 0f : (float) DynamicFogHandler.vegSunny;
        if (base == original && sat == 1f && bri == 1f && sunny <= 0f) return original;
        int adjusted = adjust(base, sat, bri);
        return sunny > 0f ? sunlit(adjusted, sunny) : adjusted;
    }

    private static AtmosphereConfigManager.AtmosphereSettings lookup(Biome biome) {
        AtmosphereConfigManager.AtmosphereSettings s = CACHE.get(biome);
        if (s == null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return null;

            ResourceLocation id = mc.level.registryAccess()
                    .registryOrThrow(Registries.BIOME).getKey(biome);
            AtmosphereConfigManager.AtmosphereSettings found =
                    id == null ? null : AtmosphereConfigManager.ATMOSPHERES.get(id);
            s = found == null ? NONE : found;
            CACHE.put(biome, s);
        }
        return s == NONE ? null : s;
    }

    private static int sunlit(int rgb, float amount) {
        float r = ((rgb >> 16) & 0xFF) / 255F;
        float g = ((rgb >> 8) & 0xFF) / 255F;
        float b = (rgb & 0xFF) / 255F;
        r = Mth.lerp(amount, r, r * 1.3F + 0.08F);
        g = Mth.lerp(amount, g, g * 1.18F + 0.06F);
        b = Mth.lerp(amount, b, b * 0.7F);
        int ir = (int) (Mth.clamp(r, 0F, 1F) * 255F + 0.5F);
        int ig = (int) (Mth.clamp(g, 0F, 1F) * 255F + 0.5F);
        int ib = (int) (Mth.clamp(b, 0F, 1F) * 255F + 0.5F);
        return (ir << 16) | (ig << 8) | ib;
    }

    public static int vary(int rgb, int x, int z) {
        float amount = (float) DynamicFogHandler.vegVariation;
        float n = noise(x / 41F, z / 41F) * 0.65F + noise(x / 13F + 71.3F, z / 13F - 19.7F) * 0.35F;
        float t = Mth.clamp((n - 0.5F) * 4.5F, -1F, 1F) * amount;
        float r = ((rgb >> 16) & 0xFF) / 255F;
        float g = ((rgb >> 8) & 0xFF) / 255F;
        float b = (rgb & 0xFF) / 255F;
        float tr, tg, tb;
        if (t > 0F) {
            tr = r * 1.35F + 0.08F;
            tg = g * 1.1F + 0.04F;
            tb = b * 0.7F;
        } else {
            tr = r * 0.68F;
            tg = g * 0.86F;
            tb = b * 0.95F + 0.03F;
            t = -t;
        }
        int ir = (int) (Mth.clamp(Mth.lerp(t, r, tr), 0F, 1F) * 255F + 0.5F);
        int ig = (int) (Mth.clamp(Mth.lerp(t, g, tg), 0F, 1F) * 255F + 0.5F);
        int ib = (int) (Mth.clamp(Mth.lerp(t, b, tb), 0F, 1F) * 255F + 0.5F);
        return (ir << 16) | (ig << 8) | ib;
    }

    private static float noise(float x, float z) {
        int x0 = Mth.floor(x), z0 = Mth.floor(z);
        float fx = x - x0, fz = z - z0;
        fx = fx * fx * (3F - 2F * fx);
        fz = fz * fz * (3F - 2F * fz);
        return Mth.lerp(fz, Mth.lerp(fx, hash(x0, z0), hash(x0 + 1, z0)), Mth.lerp(fx, hash(x0, z0 + 1), hash(x0 + 1, z0 + 1)));
    }

    private static float hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535F;
    }

    private static int adjust(int rgb, float sat, float bri) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8)  & 0xFF) / 255f;
        float b = ( rgb        & 0xFF) / 255f;

        float lum = r * 0.299f + g * 0.587f + b * 0.114f;
        r = lum + (r - lum) * sat;
        g = lum + (g - lum) * sat;
        b = lum + (b - lum) * sat;

        r *= bri; g *= bri; b *= bri;

        int ir = (int) (Mth.clamp(r, 0f, 1f) * 255f + 0.5f);
        int ig = (int) (Mth.clamp(g, 0f, 1f) * 255f + 0.5f);
        int ib = (int) (Mth.clamp(b, 0f, 1f) * 255f + 0.5f);
        return (ir << 16) | (ig << 8) | ib;
    }
}
