package com.misanthropy.linggango.let_there_be_atmosphere.mixin.embeddium;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.GlowTag;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WindSway;
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
public class EmbeddiumGlowUniformMixin {
    @Unique private GlUniformFloat4v linggango$glow;
    @Unique private GlUniformFloat4v linggango$glowCam;

    @Inject(method = "<init>", require = 0, at = @At("TAIL"))
    private void linggango$bindGlow(ShaderBindingContext context, ChunkShaderOptions options, CallbackInfo ci) {
        try {
            linggango$glow = context.bindUniform("u_LinggangoGlow", GlUniformFloat4v::new);
            linggango$glowCam = context.bindUniform("u_LinggangoGlowCam", GlUniformFloat4v::new);
            Diagnostics.once("ore glow and stem pulse uniforms bound to the terrain shader");
        } catch (RuntimeException e) {
            linggango$glow = null;
            linggango$glowCam = null;
            Diagnostics.once("ore glow and stem pulse could not bind their uniforms, so they stay off");
        }
    }

    @Inject(method = "setupState", require = 0, at = @At("TAIL"))
    private void linggango$uploadGlow(CallbackInfo ci) {
        if (linggango$glow == null) return;
        GlowTag.update();
        linggango$glow.set(GlowTag.GLOW);
        linggango$glowCam.set(WindSway.CAMERA);
    }
}
