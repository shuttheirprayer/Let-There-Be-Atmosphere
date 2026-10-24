package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class StarSky {
    private StarSky() {}

    private static final int BASE_STARS = 2200;
    private static final int BAND_QUADS = 96;
    private static final Matrix4f MATRIX = new Matrix4f();

    public static boolean on() {
        return FxConfig.starSky && DynamicFogHandler.active() && DynamicFogHandler.starsEnabled;
    }

    public static void onRenderStarSkyStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (!on()) {
            Diagnostics.skip(Diagnostics.STARS, "disabled");
            return;
        }
        ClientLevel level = SkyGate.sky(event, Diagnostics.STARS, true);
        if (level == null) return;
        SkyProgram program = SkyProgram.STARS;
        if (!program.ready()) {
            Diagnostics.skip(Diagnostics.STARS, "shader failed to load, see the log");
            return;
        }
        float partialTick = event.getPartialTick();
        float dayTime = level.getDayTime() % DayCurves.PERIOD + partialTick;
        float night = DayCurves.starBrightness(dayTime);
        float bright = (night * (float) DynamicFogHandler.starBrightness
                + (DynamicFogHandler.dayStarsEnabled ? (1F - night) * (float) DynamicFogHandler.dayStarBrightness : 0F))
                * (1F - level.getRainLevel(partialTick)) * DynamicFogHandler.skyVisibility();
        if (bright < 0.004F) {
            Diagnostics.skip(Diagnostics.STARS, "daylight, rain or no sky in view");
            return;
        }
        float angle = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        MATRIX.set(event.getPoseStack().last().pose()).rotateY(-Mth.HALF_PI).rotateX(angle);
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int stars = Mth.clamp((int) Math.round(BASE_STARS * DynamicFogHandler.effectiveStarDensity() / 100.0), 0, 12000);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        program.begin();
        SkyProgram.texture(0, Haze.noiseTexture());
        program.transform(0, MATRIX);
        GL20.glUniform4f(program.uniform(1), Math.min(bright * 2.2F, 2.5F), CaveMotes.now(), stars,
                (float) DynamicFogHandler.starTwinkle);
        GL20.glUniform4f(program.uniform(2), 2F / Math.max(main.width, 1), 2F / Math.max(main.height, 1),
                Mth.cos(angle), Mth.sin(angle));
        GL20.glUniform4f(program.uniform(3), FxConfig.milkyWay ? 1F : 0F, main.height / 1080F, 0F, 0F);
        program.draw(stars * 6 + (FxConfig.milkyWay ? BAND_QUADS * 6 : 0));
        program.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        Diagnostics.mark(Diagnostics.STARS, Diagnostics.detail() ? String.format("%d stars, %.2f bright", stars, bright) : null);
    }
}
