package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Lightning;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.LightTint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    @Shadow @Final private DynamicTexture lightTexture;
    @Shadow private boolean updateLightTexture;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void linggango$trackLightmap(GameRenderer renderer, Minecraft minecraft, CallbackInfo ci) {
        LightTint.lightmap = this.lightTexture;
    }

    @Inject(method = "updateLightTexture", at = @At("HEAD"))
    private void linggango$flashRefresh(float partialTick, CallbackInfo ci) {
        if (Lightning.flash() > 0.003F) this.updateLightTexture = true;
    }

    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getSkyDarken(F)F"))
    private float linggango$flashDaylight(float darken) {
        float flash = Lightning.flash();
        return flash > 0F ? Mth.lerp(Math.min(flash * 0.85F, 1F), darken, 1F) : darken;
    }
}
