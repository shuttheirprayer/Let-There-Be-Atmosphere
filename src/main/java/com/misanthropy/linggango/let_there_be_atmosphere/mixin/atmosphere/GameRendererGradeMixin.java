package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.ColorGrade;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.LensDrops;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererGradeMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V", shift = At.Shift.AFTER))
    private void ltba$colorGrade(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        ColorGrade.apply();
        LensDrops.apply();
    }
}
