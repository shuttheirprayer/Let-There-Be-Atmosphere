package com.misanthropy.linggango.let_there_be_atmosphere.client;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.AmbientParticles;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.AuroraRenderer;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Birds;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Bubbles;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Butterflies;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.CaveDust;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.CaveMotes;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.CometRenderer;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.DeepDark;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.EndAir;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.EndDust;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.EndSky;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Fireflies;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.GrassBlades;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Haze;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.HazeBandRenderer;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.LavaLife;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.LavaSurface;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Lightning;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Litter;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Mist;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.NetherAir;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.NetherDust;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.NetherFx;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.NetherPortalFx;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.NetherStorm;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.PortalGlow;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Puddles;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Rainbow;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Ripples;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Silt;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.SkyGlow;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.StarSky;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.StormRain;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.StormSky;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.SunPass;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Tumbleweeds;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.ViewCapture;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Wake;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WaterEdge;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WaterShine;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WaterfallFoam;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WaveSplash;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WeatherStorms;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WindBits;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WindLife;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class StageDispatch {
    private StageDispatch() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onStageHighest(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            ViewCapture.onRenderViewCaptureStage(event);
            EndAir.onRenderEndAirCapture(event);
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            StarSky.onRenderStarSkyStage(event);
            SkyGlow.onRenderSkyStage(event);
            Lightning.onRenderLightningStage(event);
            HazeBandRenderer.onRenderSkyStage(event);
            EndSky.onRenderEndSkyStage(event);
            CometRenderer.onRenderSkyStage(event);
            AuroraRenderer.onRenderSkyStage(event);
            CaptureHook.onCaptureHookStage(event);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            Tumbleweeds.onRenderTumbleweedsStage(event);
            StormSky.onRenderStormSkyStage(event);
            NetherAir.onRenderLavaSurfaceStage(event);
            Litter.onRenderLitterStage(event);
            GrassBlades.onRenderGrassBladeStage(event);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            WaveSplash.onRenderSplashStage(event);
            WaterfallFoam.onRenderWaterfallFoamStage(event);
            WaterEdge.onRenderWaterEdgeStage(event);
            Ripples.onRenderRipplesStage(event);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            WindBits.onWindBitsStage(event);
            NetherPortalFx.onRenderNetherPortalStage(event);
            CaveMotes.onRenderCaveMoteStage(event);
            Butterflies.onRenderButterflyStage(event);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            WeatherStorms.onRenderWeatherStormsStage(event);
            StormRain.onRenderStormRainStage(event);
            NetherStorm.onRenderNetherStormStage(event);
            NetherFx.onRenderNetherFxStage(event);
            Birds.onRenderBirdStage(event);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            SunPass.onRenderSunPassStage(event);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onStageLow(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            NetherAir.onRenderNetherAirStage(event);
            EndAir.onRenderEndAirStage(event);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onStageLowest(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            Rainbow.onRenderRainbowStage(event);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            ViewCapture.onRenderViewReleaseStage(event);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        DynamicFogHandler.onClientTick(event);
        WindLife.onWindLifeTick(event);
        WeatherStorms.onWeatherStormsTick(event);
        WaterfallFoam.onWaterfallFoamTick(event);
        WaterShine.onWaterShineTick(event);
        WaterEdge.onWaterEdgeTick(event);
        Wake.onWakeTick(event);
        Tumbleweeds.onTumbleweedsTick(event);
        StormSky.onStormSkyTick(event);
        Silt.onSiltTick(event);
        Ripples.onRipplesTick(event);
        Rainbow.onClientTick(event);
        Puddles.onPuddleTick(event);
        PortalGlow.onPortalGlowTick(event);
        NetherPortalFx.onNetherPortalTick(event);
        NetherFx.onNetherFxTick(event);
        NetherDust.onNetherDustTick(event);
        NetherAir.onNetherAirTick(event);
        Mist.onMistTick(event);
        Litter.onLitterTick(event);
        LavaSurface.onLavaSurfaceTick(event);
        LavaLife.onLavaLifeTick(event);
        Haze.onHazeTick(event);
        GrassBlades.onGrassBladeTick(event);
        Fireflies.onFireflyTick(event);
        EndDust.onEndDustTick(event);
        DeepDark.onDeepDarkTick(event);
        CometRenderer.onClientTick(event);
        CaveDust.onCaveDustTick(event);
        Butterflies.onButterflyTick(event);
        Bubbles.onBubblesTick(event);
        Birds.onBirdTick(event);
        AuroraRenderer.onClientTick(event);
        AmbientParticles.onClientTick(event);
        CaptureHook.onCaptureHookTick(event);
    }
}
