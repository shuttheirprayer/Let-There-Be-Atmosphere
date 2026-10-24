package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface LevelRendererStarAccessor {
    @Invoker("createStars")
    void linggango$createStars();
}
