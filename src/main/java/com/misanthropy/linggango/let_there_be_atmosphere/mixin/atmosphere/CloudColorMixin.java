package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.DayCurves;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.FxConfig;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Lightning;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public abstract class CloudColorMixin {
    @ModifyReturnValue(method = "getCloudColor", at = @At("RETURN"))
    private Vec3 linggango$shadeClouds(Vec3 color, float partialTick) {
        if (!DynamicFogHandler.active()) return color;

        float nightDarken = (float) FxConfig.cloudNightDarken;
        float fogBlend = (float) FxConfig.cloudFogBlend;
        float stormDarken = (float) DynamicFogHandler.weatherCloudDarken;
        float flash = Lightning.flash();
        if (nightDarken <= 0F && fogBlend <= 0F && stormDarken <= 0F && flash <= 0F) return color;

        ClientLevel level = (ClientLevel) (Object) this;
        double r = color.x, g = color.y, b = color.z;

        if (nightDarken > 0F) {
            float night = DayCurves.nightFraction(level.getDayTime() % DayCurves.PERIOD + partialTick);
            float keep = 1F - night * nightDarken;
            r *= keep; g *= keep; b *= keep;
        }

        if (stormDarken > 0F) {
            float storm = Math.max(level.getRainLevel(partialTick), level.getThunderLevel(partialTick));
            float keep = 1F - storm * stormDarken;
            r *= keep; g *= keep; b *= keep;
        }

        if (fogBlend > 0F) {
            float t = Mth.clamp(fogBlend, 0F, 1F);
            r = Mth.lerp(t, (float) r, DynamicFogHandler.fogColorR());
            g = Mth.lerp(t, (float) g, DynamicFogHandler.fogColorG());
            b = Mth.lerp(t, (float) b, DynamicFogHandler.fogColorB());
        }

        if (flash > 0F) {
            r = Lightning.mix((float) r, 0.88F, flash, 0.75F);
            g = Lightning.mix((float) g, 0.91F, flash, 0.75F);
            b = Lightning.mix((float) b, 1.0F, flash, 0.75F);
        }

        return new Vec3(Mth.clamp(r, 0.0, 1.0), Mth.clamp(g, 0.0, 1.0), Mth.clamp(b, 0.0, 1.0));
    }
}
