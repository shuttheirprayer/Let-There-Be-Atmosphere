package com.misanthropy.linggango.let_there_be_atmosphere.mixin.embeddium;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.GlowTag;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.SwaySink;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.SwayVertex;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WindSway;
import me.jellysquid.mods.sodium.client.compat.ccl.SinkingVertexBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SinkingVertexBuilder.class, remap = false)
public class EmbeddiumSwaySinkMixin implements SwaySink {
    @Shadow @Final private ChunkVertexEncoder.Vertex[] sodiumVertexArray;

    @Unique private BlockState linggango$state;
    @Unique private int linggango$column;
    @Unique private BlockState linggango$tagged;
    @Unique private int linggango$kind;
    @Unique private int linggango$glow;
    @Unique private int linggango$epoch;

    @Override
    public void linggango$block(BlockState state, int column) {
        linggango$state = state;
        linggango$column = column;
    }

    @Inject(method = "flush(Lme/jellysquid/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuilder;Lme/jellysquid/mods/sodium/client/render/chunk/terrain/material/Material;FFF)Z",
            require = 0, at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/vertex/builder/ChunkMeshBufferBuilder;push([Lme/jellysquid/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lme/jellysquid/mods/sodium/client/render/chunk/terrain/material/Material;)V"))
    private void linggango$tagSway(ChunkModelBuilder builder, Material material, float ox, float oy, float oz, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = linggango$state;
        if (state == null) return;
        if (state != linggango$tagged || linggango$epoch != GlowTag.epoch) {
            Diagnostics.once("custom block renderers, such as foliage overlays, are tagged for sway and glow");
            linggango$tagged = state;
            linggango$epoch = GlowTag.epoch;
            linggango$kind = WindSway.kind(state);
            linggango$glow = GlowTag.bits(state);
        }
        int kind = linggango$kind;
        if (kind == WindSway.NONE || linggango$column == WindSway.STILL) {
            for (ChunkVertexEncoder.Vertex vertex : sodiumVertexArray) ((SwayVertex) vertex).linggango$setSway(linggango$glow);
            return;
        }
        if (kind == WindSway.VINE) {
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (ChunkVertexEncoder.Vertex vertex : sodiumVertexArray) {
                minX = Math.min(minX, vertex.x - ox);
                maxX = Math.max(maxX, vertex.x - ox);
                minZ = Math.min(minZ, vertex.z - oz);
                maxZ = Math.max(maxZ, vertex.z - oz);
            }
            int bits = WindSway.vineBits(minX, maxX, minZ, maxZ);
            for (ChunkVertexEncoder.Vertex vertex : sodiumVertexArray) ((SwayVertex) vertex).linggango$setSway(bits);
            return;
        }
        for (ChunkVertexEncoder.Vertex vertex : sodiumVertexArray) {
            ((SwayVertex) vertex).linggango$setSway(WindSway.bits(kind, state, vertex.y - oy, linggango$column));
        }
    }
}
