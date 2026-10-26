package com.misanthropy.linggango.let_there_be_atmosphere.mixin.embeddium;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.RainRings;
import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniformFloat4v;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ShaderBindingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkShaderInterface.class, remap = false)
public class EmbeddiumRainUniformMixin {
    @Unique private GlUniformFloat4v linggango$rain;
    @Unique private GlUniformFloat4v linggango$rainTex;

    @Inject(method = "<init>", require = 0, at = @At("TAIL"))
    private void linggango$bindRain(ShaderBindingContext context, ChunkShaderOptions options, CallbackInfo ci) {
        try {
            linggango$rain = context.bindUniform("u_LinggangoRain", GlUniformFloat4v::new);
            linggango$rainTex = context.bindUniform("u_LinggangoRainTex", GlUniformFloat4v::new);
            RainRings.bound = true;
            Diagnostics.once("rain ring uniforms bound to the terrain shader");
        } catch (RuntimeException e) {
            linggango$rain = null;
            linggango$rainTex = null;
            Diagnostics.once("rain rings could not bind their uniforms, rain ripples stay on the old wave field");
        }
    }

    @Inject(method = "setupState", require = 0, at = @At("TAIL"))
    private void linggango$uploadRain(CallbackInfo ci) {
        if (linggango$rain == null) return;
        RainRings.update();
        linggango$rain.set(RainRings.RAIN);
        linggango$rainTex.set(RainRings.SPRITE);
    }
}
