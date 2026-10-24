package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeColorModifier;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Biome.class)
public abstract class BiomeColorMixin {
    @ModifyReturnValue(method = "getGrassColor", at = @At("RETURN"))
    private int linggango$grassColor(int original) {
        if (!DynamicFogHandler.running() || !DynamicFogHandler.biomeColorsEnabled) return original;
        return BiomeColorModifier.grass(original, (Biome) (Object) this);
    }

    @ModifyReturnValue(method = "getFoliageColor", at = @At("RETURN"))
    private int linggango$foliageColor(int original) {
        if (!DynamicFogHandler.running() || !DynamicFogHandler.biomeColorsEnabled) return original;
        return BiomeColorModifier.foliage(original, (Biome) (Object) this);
    }

    @ModifyReturnValue(method = "getWaterColor", at = @At("RETURN"))
    private int linggango$waterColor(int original) {
        if (!DynamicFogHandler.running() || !DynamicFogHandler.biomeColorsEnabled) return original;
        return BiomeColorModifier.water(original, (Biome) (Object) this);
    }
}
