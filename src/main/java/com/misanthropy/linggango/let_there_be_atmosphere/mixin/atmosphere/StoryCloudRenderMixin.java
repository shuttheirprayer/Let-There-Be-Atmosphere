package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.StoryClouds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 500)
public abstract class StoryCloudRenderMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void linggango$storyClouds(PoseStack pose, Matrix4f projection, float partialTick,
                                       double camX, double camY, double camZ, CallbackInfo ci) {
        Diagnostics.once("storybook cloud injection is live");
        if (!StoryClouds.shouldRender(Minecraft.getInstance().level)) {
            Diagnostics.skip(Diagnostics.STORY, "disabled");
            return;
        }
        StoryClouds.render(pose, projection, partialTick, camX, camY, camZ);
        ci.cancel();
    }
}
