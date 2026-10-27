package com.misanthropy.linggango.let_there_be_atmosphere.mixin.embeddium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.TorchFlicker;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ShaderLoader.class, remap = false)
public class EmbeddiumFlickerShaderMixin {
    @ModifyExpressionValue(method = "loadShader", require = 0, at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/gl/shader/ShaderLoader;getShaderSource(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;"))
    private static String linggango$patchFlicker(String source, @Local(argsOnly = true) ResourceLocation name) {
        if (source == null || !"sodium".equals(name.getNamespace()) || !name.getPath().endsWith("block_layer_opaque.vsh")) return source;
        return TorchFlicker.patch(source);
    }
}
