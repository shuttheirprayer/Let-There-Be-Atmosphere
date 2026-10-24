package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderTarget.class)
public interface RenderTargetColorAccessor {
    @Accessor("colorTextureId")
    void linggango$setColorTextureId(int id);
}
