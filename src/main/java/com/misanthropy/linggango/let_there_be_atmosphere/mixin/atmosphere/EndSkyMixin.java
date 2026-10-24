package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.EndSky;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class EndSkyMixin {
    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true, require = 0)
    private void linggango$skipEndSky(PoseStack poseStack, CallbackInfo ci) {
        if (!EndSky.covers(Minecraft.getInstance().gameRenderer.getMainCamera())) return;
        Diagnostics.once("vanilla End sky replaced by the nebula");
        ci.cancel();
    }
}
