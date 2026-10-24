package com.misanthropy.linggango.let_there_be_atmosphere.mixin.embeddium;

import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.SwayVertex;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ChunkVertexEncoder.Vertex.class, remap = false)
public class EmbeddiumSwayVertexMixin implements SwayVertex {
    @Unique
    private int linggango$sway;

    @Override
    public int linggango$sway() {
        return linggango$sway;
    }

    @Override
    public void linggango$setSway(int sway) {
        linggango$sway = sway;
    }
}
