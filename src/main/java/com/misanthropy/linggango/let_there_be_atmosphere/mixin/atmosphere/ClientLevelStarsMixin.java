package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.DayCurves;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public abstract class ClientLevelStarsMixin {
    @ModifyReturnValue(method = "getStarBrightness", at = @At("RETURN"))
    private float linggango$modifyStarBrightness(float original, float partialTick) {
        if (!DynamicFogHandler.active()) return original;

        if (!DynamicFogHandler.starsEnabled) return original;

        ClientLevel level = (ClientLevel) (Object) this;
        float base = DayCurves.starBrightness(level.getDayTime() % DayCurves.PERIOD + partialTick);
        boolean isDay = base < 0.05F;

        if (isDay && !DynamicFogHandler.dayStarsEnabled) return original;

        float brightness = isDay ? (float) DynamicFogHandler.dayStarBrightness : (float) DynamicFogHandler.starBrightness;
        float twinkle    = isDay ? (float) DynamicFogHandler.dayStarTwinkle    : (float) DynamicFogHandler.starTwinkle;
        float baseBrightness = isDay ? 1.0F : base;
        if (twinkle > 0f) {
            float t = (level.getGameTime() % 24000L + partialTick) * 0.07f;
            baseBrightness *= 1.0f + Mth.sin(t) * twinkle * 0.4f;
        }

        return Mth.clamp(baseBrightness * brightness, 0.0F, 1.0F);
    }
}
