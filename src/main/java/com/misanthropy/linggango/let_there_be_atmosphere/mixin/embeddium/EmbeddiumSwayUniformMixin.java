package com.misanthropy.linggango.let_there_be_atmosphere.mixin.embeddium;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
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
public class EmbeddiumSwayUniformMixin {
    @Unique private GlUniformFloat4v linggango$wind;
    @Unique private GlUniformFloat4v linggango$camera;
    @Unique private GlUniformFloat4v linggango$gust;
    @Unique private GlUniformFloat4v linggango$flow;
    @Unique private final GlUniformFloat4v[] linggango$push = new GlUniformFloat4v[4];

    @Inject(method = "<init>", require = 0, at = @At("TAIL"))
    private void linggango$bindSway(ShaderBindingContext context, ChunkShaderOptions options, CallbackInfo ci) {
        try {
            linggango$wind = context.bindUniform("u_LinggangoWind", GlUniformFloat4v::new);
            linggango$camera = context.bindUniform("u_LinggangoCamera", GlUniformFloat4v::new);
            linggango$gust = context.bindUniform("u_LinggangoGust", GlUniformFloat4v::new);
            linggango$flow = context.bindUniform("u_LinggangoFlow", GlUniformFloat4v::new);
            for (int i = 0; i < linggango$push.length; i++) linggango$push[i] = context.bindUniform("u_LinggangoPush" + i, GlUniformFloat4v::new);
            Diagnostics.once("wind sway uniforms bound to the terrain shader");
        } catch (RuntimeException e) {
            linggango$wind = null;
            linggango$camera = null;
            linggango$gust = null;
            Diagnostics.once("wind sway could not bind its uniforms, plants stay still");
        }
    }

    @Inject(method = "setupState", require = 0, at = @At("TAIL"))
    private void linggango$uploadSway(CallbackInfo ci) {
        if (linggango$wind == null) return;
        WindSway.update();
        WindSway.uploads++;
        linggango$wind.set(WindSway.WIND);
        linggango$camera.set(WindSway.CAMERA);
        linggango$gust.set(WindSway.GUST);
        linggango$flow.set(WindSway.FLOW);
        for (int i = 0; i < linggango$push.length; i++) linggango$push[i].set(WindSway.PUSH[i]);
    }
}
