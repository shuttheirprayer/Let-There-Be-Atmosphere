package com.misanthropy.linggango.let_there_be_atmosphere.mixin.embeddium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.SwayVertex;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CompactChunkVertex.class, remap = false)
public class EmbeddiumSwayEncoderMixin {
    @ModifyExpressionValue(method = "lambda$getEncoder$0", require = 0, at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/terrain/material/Material;bits()I"))
    private static int linggango$swayBits(int bits, @Local(argsOnly = true) ChunkVertexEncoder.Vertex vertex) {
        return bits | ((SwayVertex) vertex).linggango$sway();
    }
}
