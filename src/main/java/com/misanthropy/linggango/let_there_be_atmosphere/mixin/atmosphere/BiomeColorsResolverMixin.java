package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeColorModifier;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ColorResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BiomeColors.class)
public abstract class BiomeColorsResolverMixin {
    @Shadow @Final @Mutable public static ColorResolver GRASS_COLOR_RESOLVER;
    @Shadow @Final @Mutable public static ColorResolver FOLIAGE_COLOR_RESOLVER;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void linggango$patchyGreens(CallbackInfo ci) {
        GRASS_COLOR_RESOLVER = linggango$vary(GRASS_COLOR_RESOLVER);
        FOLIAGE_COLOR_RESOLVER = linggango$vary(FOLIAGE_COLOR_RESOLVER);
    }

    @Unique
    private static ColorResolver linggango$vary(ColorResolver inner) {
        return (biome, x, z) -> {
            int color = inner.getColor(biome, x, z);
            if (DynamicFogHandler.vegVariation <= 0.0 || !DynamicFogHandler.running()) return color;
            return BiomeColorModifier.vary(color, Mth.floor(x), Mth.floor(z));
        };
    }
}
