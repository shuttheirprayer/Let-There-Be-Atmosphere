package com.misanthropy.linggango.let_there_be_atmosphere.mixin.embeddium;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WaterShine;
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
public class EmbeddiumSheenUniformMixin {
    @Unique private GlUniformFloat4v linggango$sheen;
    @Unique private GlUniformFloat4v linggango$sheenZenith;

    @Inject(method = "<init>", require = 0, at = @At("TAIL"))
    private void linggango$bindSheen(ShaderBindingContext context, ChunkShaderOptions options, CallbackInfo ci) {
        try {
            linggango$sheen = context.bindUniform("u_LinggangoSheen", GlUniformFloat4v::new);
            linggango$sheenZenith = context.bindUniform("u_LinggangoSheenZenith", GlUniformFloat4v::new);
            WaterShine.sheenBound = true;
            Diagnostics.once("sky reflection uniforms bound to the terrain shader");
        } catch (RuntimeException e) {
            linggango$sheen = null;
            linggango$sheenZenith = null;
            WaterShine.sheenBound = false;
            Diagnostics.once("sky reflection could not bind its uniforms, it stays a screen pass");
        }
    }

    @Inject(method = "setupState", require = 0, at = @At("TAIL"))
    private void linggango$uploadSheen(CallbackInfo ci) {
        if (linggango$sheen == null) return;
        WaterShine.updateSheen();
        linggango$sheen.set(WaterShine.SHEEN);
        linggango$sheenZenith.set(WaterShine.ZENITH);
    }
}
