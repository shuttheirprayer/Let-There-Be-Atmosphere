package com.misanthropy.linggango.let_there_be_atmosphere.mixin.embeddium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.SwayVertex;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.impl.VanillaLikeChunkVertex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = VanillaLikeChunkVertex.class, remap = false)
public class EmbeddiumSwayVanillaEncoderMixin {
    @ModifyExpressionValue(method = "lambda$getEncoder$0", require = 0, at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/vertex/format/impl/VanillaLikeChunkVertex;encodeDrawParameters(Lme/jellysquid/mods/sodium/client/render/chunk/terrain/material/Material;I)I"))
    private static int linggango$swayBits(int packed, @Local(argsOnly = true) ChunkVertexEncoder.Vertex vertex) {
        return packed | ((SwayVertex) vertex).linggango$sway();
    }
}
