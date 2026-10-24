package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.StarSky;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererStarsMixin {
    @ModifyConstant(method = "drawStars", constant = @Constant(intValue = 1500))
    private int linggango$starCount(int original) {
        Diagnostics.once("vanilla star count injection is live");
        if (!DynamicFogHandler.active() || !DynamicFogHandler.starsEnabled) return original;
        if (StarSky.on()) return 0;
        int count = (int) Math.round(original * (DynamicFogHandler.effectiveStarDensity() / 100.0));
        return Mth.clamp(count, 0, original * 12);
    }
}
