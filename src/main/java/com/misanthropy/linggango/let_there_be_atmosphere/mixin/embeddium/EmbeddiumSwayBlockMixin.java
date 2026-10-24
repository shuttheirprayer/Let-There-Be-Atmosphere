package com.misanthropy.linggango.let_there_be_atmosphere.mixin.embeddium;

import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.GlowTag;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.SwaySink;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.SwayVertex;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WindSway;
import com.llamalad7.mixinextras.sugar.Local;
import me.jellysquid.mods.sodium.client.compat.ccl.SinkingVertexBuilder;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockRenderer.class, remap = false)
public class EmbeddiumSwayBlockMixin {
    @Shadow @Final private ChunkVertexEncoder.Vertex[] vertices;
    @Shadow @Final private SinkingVertexBuilder sinkingVertexBuilder;

    @Unique private BlockState linggango$state;
    @Unique private int linggango$kind;
    @Unique private int linggango$glow;
    @Unique private int linggango$epoch;

    @Inject(method = "writeGeometry", require = 0, at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/vertex/builder/ChunkMeshBufferBuilder;push([Lme/jellysquid/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lme/jellysquid/mods/sodium/client/render/chunk/terrain/material/Material;)V"))
    private void linggango$tagSway(BlockRenderContext ctx, ChunkModelBuilder builder, Vec3 offset, Material material,
                                   BakedQuadView quad, int[] colors, QuadLightData light, CallbackInfo ci) {
        BlockState state = ctx.state();
        if (state != linggango$state || linggango$epoch != GlowTag.epoch) {
            linggango$state = state;
            linggango$epoch = GlowTag.epoch;
            linggango$kind = WindSway.kind(state);
            linggango$glow = GlowTag.bits(state);
        }
        int kind = linggango$kind;
        if (kind == WindSway.NONE) {
            for (ChunkVertexEncoder.Vertex vertex : vertices) ((SwayVertex) vertex).linggango$setSway(linggango$glow);
            return;
        }
        WindSway.tagged++;
        float base = ctx.origin().y() + (float) offset.y;
        for (ChunkVertexEncoder.Vertex vertex : vertices) {
            ((SwayVertex) vertex).linggango$setSway(WindSway.bits(kind, state, vertex.y - base));
        }
    }

    @Inject(method = "renderModel", require = 0, at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/compat/ccl/SinkingVertexBuilder;flush(Lme/jellysquid/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuilder;Lme/jellysquid/mods/sodium/client/render/chunk/terrain/material/Material;Lorg/joml/Vector3fc;)Z"))
    private void linggango$tagSink(CallbackInfo ci, @Local(argsOnly = true) BlockRenderContext ctx) {
        ((SwaySink) (Object) sinkingVertexBuilder).linggango$block(ctx.state());
    }
}
