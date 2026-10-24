package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeBlend;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Lightning;
import com.misanthropy.linggango.let_there_be_atmosphere.config.AtmosphereConfigManager;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.VividSky;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public abstract class SkyColorMixin {
    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private Vec3 applyCustomSkyColor(Vec3 original, Vec3 pos, float partialTick) {
        if (!DynamicFogHandler.active()) return original;

        ClientLevel level = (ClientLevel) (Object) this;
        Vec3 result = original;

        if (AtmosphereConfigManager.hasSkyOverrides()) {
            AtmosphereConfigManager.AtmosphereSettings bs = BiomeBlend.settings();
            float coverage = BiomeBlend.skyHexCoverage();
            if (bs != null && bs.skyHex >= 0 && coverage > 0F) {
                Vec3 themed = linggango$shade(level, bs.skyHex, partialTick);
                result = new Vec3(
                        Mth.lerp(coverage, result.x, themed.x),
                        Mth.lerp(coverage, result.y, themed.y),
                        Mth.lerp(coverage, result.z, themed.z));
            }
        }

        if (VividSky.on()) result = VividSky.sky(level, partialTick, result);

        float extra = (float) DynamicFogHandler.weatherSkyDarken;
        if (extra > 0F) {
            float storm = Math.max(level.getRainLevel(partialTick), level.getThunderLevel(partialTick));
            if (storm > 0F) {
                float keep = 1F - storm * extra;
                result = new Vec3(result.x * keep, result.y * keep, result.z * keep);
            }
        }

        float flash = Lightning.flash();
        if (flash > 0F) {
            result = new Vec3(Lightning.mix((float) result.x, 0.78F, flash, 0.45F),
                    Lightning.mix((float) result.y, 0.84F, flash, 0.45F),
                    Lightning.mix((float) result.z, 1.0F, flash, 0.45F));
        }

        return result;
    }

    @Unique
    private static Vec3 linggango$shade(ClientLevel level, int skyHex, float partialTick) {
        float r = ((skyHex >> 16) & 0xFF) / 255.0f;
        float g = ((skyHex >> 8) & 0xFF) / 255.0f;
        float b = (skyHex & 0xFF) / 255.0f;

        float timeOfDay = level.getTimeOfDay(partialTick);
        float sunBrightness = Mth.clamp(Mth.cos(timeOfDay * ((float) Math.PI * 2.0F)) * 2.0F + 0.5F, 0.0F, 1.0F);
        r *= sunBrightness;
        g *= sunBrightness;
        b *= sunBrightness;

        float rainLevel = level.getRainLevel(partialTick);
        if (rainLevel > 0.0F) {
            float luma = r * 0.3F + g * 0.59F + b * 0.11F;
            float rainMix = 1.0F - rainLevel * 0.75F;
            r = r * rainMix + (luma * 0.6F) * (1.0F - rainMix);
            g = g * rainMix + (luma * 0.6F) * (1.0F - rainMix);
            b = b * rainMix + (luma * 0.6F) * (1.0F - rainMix);
        }

        float thunderLevel = level.getThunderLevel(partialTick);
        if (thunderLevel > 0.0F) {
            float luma = r * 0.3F + g * 0.59F + b * 0.11F;
            float thunderMix = 1.0F - thunderLevel * 0.75F;
            r = r * thunderMix + (luma * 0.2F) * (1.0F - thunderMix);
            g = g * thunderMix + (luma * 0.2F) * (1.0F - thunderMix);
            b = b * thunderMix + (luma * 0.2F) * (1.0F - thunderMix);
        }

        return new Vec3(r, g, b);
    }
}
