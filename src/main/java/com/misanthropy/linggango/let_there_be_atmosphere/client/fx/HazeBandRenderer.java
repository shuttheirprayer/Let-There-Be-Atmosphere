package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeBlend;
import com.misanthropy.linggango.let_there_be_atmosphere.config.AtmosphereConfigManager;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;

@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class HazeBandRenderer {
    private HazeBandRenderer() {}

    public static void onRenderSkyStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (!DynamicFogHandler.active() || !FxConfig.hazeBandEnabled || FxConfig.hazeBandStrength <= 0.0) {
            Diagnostics.skip(Diagnostics.HAZEBAND, "disabled");
            return;
        }

        ClientLevel level = SkyGate.sky(event, Diagnostics.HAZEBAND, false);
        if (level == null) return;

        float visibility = DynamicFogHandler.skyVisibility();
        if (visibility <= 0.03F) {
            Diagnostics.skip(Diagnostics.HAZEBAND, "no open sky overhead");
            return;
        }

        AtmosphereConfigManager.AtmosphereSettings bs = BiomeBlend.settings();
        float strength = (float) FxConfig.hazeBandStrength * visibility
                * (bs != null ? Mth.clamp(bs.hazeStrength, 0F, 4F) : 1F);
        if (strength <= 0.002F) {
            Diagnostics.skip(Diagnostics.HAZEBAND, Diagnostics.detail()
                    ? String.format("too faint at %.4f", strength) : null);
            return;
        }

        float r = DynamicFogHandler.fogColorR();
        float g = DynamicFogHandler.fogColorG();
        float b = DynamicFogHandler.fogColorB();
        if (bs != null && bs.hazeHex >= 0) {
            float coverage = BiomeBlend.hazeHexCoverage();
            r = Mth.lerp(coverage, r, ((bs.hazeHex >> 16) & 0xFF) / 255F);
            g = Mth.lerp(coverage, g, ((bs.hazeHex >> 8) & 0xFF) / 255F);
            b = Mth.lerp(coverage, b, (bs.hazeHex & 0xFF) / 255F);
        }

        double camY = event.getCamera().getPosition().y;
        float sea = level.getSeaLevel();
        float bottom = (float) (sea + FxConfig.hazeBandBottom - camY);
        float middle = (float) (sea + FxConfig.hazeBandMiddle - camY);
        float top = (float) (sea + FxConfig.hazeBandTop - camY);
        if (top <= bottom) {
            Diagnostics.skip(Diagnostics.HAZEBAND, "the band top sits below its bottom");
            return;
        }
        float radius = (float) FxConfig.hazeBandRadius;

        Matrix4f matrix = event.getPoseStack().last().pose();
        draw(matrix, r, g, b, bottom, middle, top, radius, strength);
        Diagnostics.mark(Diagnostics.HAZEBAND, Diagnostics.detail()
                ? String.format("%d segments, strength %.3f", HazeBand.SEGMENTS, strength) : null);
    }

    private static void draw(Matrix4f matrix, float r, float g, float b, float bottom, float middle, float top,
                             float radius, float strength) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        FogRenderer.setupNoFog();
        SkyProgram program = SkyProgram.HAZE;
        if (program.ready()) {
            float mid = Mth.clamp(middle, bottom, top);
            program.begin();
            program.transform(0, matrix);
            GL20.glUniform4f(program.uniform(1), bottom, mid, top, radius);
            GL20.glUniform3f(program.uniform(2),
                    Mth.clamp(HazeBand.alpha(bottom, bottom, middle, top) * strength, 0F, 1F),
                    Mth.clamp(HazeBand.alpha(mid, bottom, middle, top) * strength, 0F, 1F),
                    Mth.clamp(HazeBand.alpha(top, bottom, middle, top) * strength, 0F, 1F));
            GL20.glUniform3f(program.uniform(3), quantize(r), quantize(g), quantize(b));
            program.draw(2 * HazeBand.SEGMENTS * 6);
            program.end();
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    private static float quantize(float value) {
        return (int) (value * 255F) / 255F;
    }
}
