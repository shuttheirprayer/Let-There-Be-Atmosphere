package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.StormRain;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class RainColumnMixin {
    @Unique
    private static boolean linggango$rainLogged;

    @WrapOperation(method = "renderSnowAndRain", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation linggango$customRain(Biome biome, BlockPos pos, Operation<Biome.Precipitation> original) {
        Biome.Precipitation precipitation = original.call(biome, pos);
        if (precipitation != Biome.Precipitation.RAIN || !StormRain.hidesVanilla()) return precipitation;
        if (!linggango$rainLogged) {
            linggango$rainLogged = true;
            Diagnostics.once("custom rain hides the vanilla rain sheets");
        }
        return Biome.Precipitation.NONE;
    }
}
