package com.misanthropy.linggango.let_there_be_atmosphere.mixin.embeddium;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.TorchFlicker;
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
public class EmbeddiumFlickerUniformMixin {
    @Unique private GlUniformFloat4v linggango$flameState;
    @Unique private TorchFlicker.Flames linggango$flames;

    @Inject(method = "<init>", require = 0, at = @At("TAIL"))
    private void linggango$bindFlicker(ShaderBindingContext context, ChunkShaderOptions options, CallbackInfo ci) {
        try {
            linggango$flameState = context.bindUniform("u_LinggangoFlameState", GlUniformFloat4v::new);
            linggango$flames = context.bindUniform("u_LinggangoFlames", TorchFlicker.Flames::new);
            TorchFlicker.bound = true;
            Diagnostics.once("torch flicker uniforms bound to the terrain shader");
        } catch (RuntimeException e) {
            linggango$flameState = null;
            linggango$flames = null;
            Diagnostics.once("torch flicker could not bind its uniforms, so flames stay steady");
        }
    }

    @Inject(method = "setupState", require = 0, at = @At("TAIL"))
    private void linggango$uploadFlicker(CallbackInfo ci) {
        if (linggango$flameState == null) return;
        TorchFlicker.update();
        linggango$flameState.set(TorchFlicker.STATE);
        if (TorchFlicker.STATE[0] > 0F) linggango$flames.set(TorchFlicker.FLAMES);
    }
}
