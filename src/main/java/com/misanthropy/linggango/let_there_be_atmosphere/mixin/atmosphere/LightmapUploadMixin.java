package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.LightTint;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DynamicTexture.class)
public abstract class LightmapUploadMixin {
    @Shadow public abstract NativeImage getPixels();

    @Inject(method = "upload", at = @At("HEAD"))
    private void linggango$tintLightmap(CallbackInfo ci) {
        if ((Object) this == LightTint.lightmap) {
            LightTint.apply(getPixels());
        }
    }
}
