package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.CameraFluid;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FogRenderer.class)
public abstract class FogRendererFluidMixin {
    @WrapOperation(method = {"setupColor", "setupFog"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getFluidInCamera()Lnet/minecraft/world/level/material/FogType;"),
            require = 0)
    private static FogType linggango$sharedFluid(Camera camera, Operation<FogType> original) {
        Diagnostics.once("fog setup shares the per-frame camera fluid");
        return CameraFluid.of(camera, original);
    }
}
