package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeFx;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class ViewCapture {
    private ViewCapture() {}

    static final Matrix4f PROJECTION = new Matrix4f();
    static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    static final Matrix4f INVERSE = new Matrix4f();
    static float fogStart;
    static float fogEnd;
    static boolean fresh;

    public static void onRenderViewCaptureStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        fresh = FxConfig.cloudShadows || FxConfig.godRays || FxConfig.heatHaze || FxConfig.coldAir || FxConfig.groundMist || FxConfig.waterRays
                || FxConfig.ambientOcclusion || FxConfig.lensFlare || FxConfig.sunShadows || FxConfig.waterReflections || FxConfig.netherRays || FxConfig.netherMist || FxConfig.netherStorms || FxConfig.lavaCrust || FxConfig.indoorSunbeams || BiomeFx.forcesOn();
        if (!fresh) return;
        PROJECTION.set(event.getProjectionMatrix());
        VIEW_PROJECTION.set(PROJECTION).mul(event.getPoseStack().last().pose());
        VIEW_PROJECTION.invert(INVERSE);
        fogStart = RenderSystem.getShaderFogStart();
        fogEnd = RenderSystem.getShaderFogEnd();
    }

    public static void onRenderViewReleaseStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) fresh = false;
    }
}
