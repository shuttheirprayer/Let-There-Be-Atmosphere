package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.CameraFluid;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 500)
public abstract class CloudRangeMixin {
    @Unique private float linggango$savedFogStart;
    @Unique private float linggango$savedFogEnd;
    @Unique private boolean linggango$fogWidened;

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void linggango$cloudProbe(PoseStack pose, Matrix4f projection, float partialTick,
                                      double camX, double camY, double camZ, CallbackInfo ci) {
        if (DynamicFogHandler.active()
                && CameraFluid.of(Minecraft.getInstance().gameRenderer.getMainCamera()) != FogType.NONE) {
            Diagnostics.skip(Diagnostics.CLOUDS, "the camera is inside a fluid");
            ci.cancel();
            return;
        }
        Diagnostics.mark(Diagnostics.CLOUDS, "vanilla renderClouds entered");
    }

    @ModifyConstant(method = "buildClouds", constant = @Constant(intValue = -3), require = 0)
    private int linggango$cloudStart(int original) {
        Diagnostics.once("cloud mesh width injection is live");
        return DynamicFogHandler.active() ? -DynamicFogHandler.cloudCells() : original;
    }

    @ModifyConstant(method = "buildClouds", constant = @Constant(intValue = 4, ordinal = 1), require = 0)
    private int linggango$cloudEndX(int original) {
        Diagnostics.once("cloud mesh east edge injection is live");
        return DynamicFogHandler.active() ? DynamicFogHandler.cloudCells() + 1 : original;
    }

    @ModifyConstant(method = "buildClouds", constant = @Constant(intValue = 4, ordinal = 2), require = 0)
    private int linggango$cloudEndZ(int original) {
        Diagnostics.once("cloud mesh south edge injection is live");
        return DynamicFogHandler.active() ? DynamicFogHandler.cloudCells() + 1 : original;
    }

    @Inject(method = "renderClouds",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/FogRenderer;levelFogColor()V",
                     shift = At.Shift.AFTER),
            require = 0)
    private void linggango$widenCloudFog(PoseStack pose, Matrix4f projection, float partialTick,
                                         double camX, double camY, double camZ, CallbackInfo ci) {
        if (!DynamicFogHandler.active() || !DynamicFogHandler.cloudFogEnabled) return;
        Diagnostics.once("cloud fog reach injection is live");

        linggango$savedFogStart = RenderSystem.getShaderFogStart();
        linggango$savedFogEnd = RenderSystem.getShaderFogEnd();
        linggango$fogWidened = true;

        float reach = DynamicFogHandler.cloudFogEnd();
        RenderSystem.setShaderFogStart(reach * 0.55F);
        RenderSystem.setShaderFogEnd(reach);
    }

    @Inject(method = "renderClouds", at = @At("RETURN"), require = 0)
    private void linggango$restoreCloudFog(PoseStack pose, Matrix4f projection, float partialTick,
                                           double camX, double camY, double camZ, CallbackInfo ci) {
        Diagnostics.once("cloud fog restore injection is live");
        if (!linggango$fogWidened) return;
        linggango$fogWidened = false;
        RenderSystem.setShaderFogStart(linggango$savedFogStart);
        RenderSystem.setShaderFogEnd(linggango$savedFogEnd);
    }
}
