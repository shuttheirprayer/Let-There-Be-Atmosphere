package com.misanthropy.linggango.let_there_be_atmosphere.mixin.embeddium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.ColorGrade;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.StoryClouds;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import me.jellysquid.mods.sodium.client.render.immediate.CloudRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CloudRenderer.class, remap = false)
public class EmbeddiumCloudShaderMixin {
    @ModifyExpressionValue(method = "render", require = 0, at = @At(value = "FIELD",
            target = "Lme/jellysquid/mods/sodium/client/render/immediate/CloudRenderer;shader:Lnet/minecraft/client/renderer/ShaderInstance;"))
    private ShaderInstance linggango$farCloudShader(ShaderInstance original) {
        ShaderInstance far = StoryClouds.farCloudShader();
        if (far == null || !DynamicFogHandler.active() || ColorGrade.shaderPackActive()) return original;
        Diagnostics.once("far clouds stop drawing once fog has faded them, so no outline is left behind");
        return far;
    }
}
