package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.FxConfig;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.SkyGlow;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(LevelRenderer.class)
public abstract class CelestialSizeMixin {
    @ModifyConstant(method = "renderSky", constant = @Constant(floatValue = 30.0F), require = 0)
    private float linggango$sunSize(float original) {
        if (!DynamicFogHandler.active()) return original;
        if (SkyGlow.replacesSun()) return 0F;
        return original * Mth.clamp((float) FxConfig.sunScale, 0.1F, 5.0F);
    }

    @ModifyConstant(method = "renderSky", constant = @Constant(floatValue = 20.0F), require = 0)
    private float linggango$moonSize(float original) {
        if (!DynamicFogHandler.active()) return original;
        return original * Mth.clamp((float) FxConfig.moonScale, 0.1F, 5.0F);
    }
}
