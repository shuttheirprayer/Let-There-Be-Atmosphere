package com.misanthropy.linggango.let_there_be_atmosphere.mixin.embeddium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WaterShine;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ShaderLoader.class, remap = false)
public class EmbeddiumSheenShaderMixin {
    @ModifyExpressionValue(method = "loadShader", require = 0, at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/gl/shader/ShaderLoader;getShaderSource(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;"))
    private static String linggango$patchSheen(String source, @Local(argsOnly = true) ResourceLocation name) {
        if (source == null || !"sodium".equals(name.getNamespace())) return source;
        String path = name.getPath();
        if (path.endsWith("block_layer_opaque.vsh")) return WaterShine.patchSheenVertex(source);
        if (path.endsWith("block_layer_opaque.fsh")) return WaterShine.patchSheenFragment(source);
        return source;
    }
}
