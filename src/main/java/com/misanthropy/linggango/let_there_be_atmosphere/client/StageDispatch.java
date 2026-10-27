package com.misanthropy.linggango.let_there_be_atmosphere.client;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.AirMist;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.FxConfig;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.FishJumps;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Puffs;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.AmbientParticles;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.AuroraRenderer;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.BatSwarm;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Birds;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Bubbles;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Butterflies;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.CaveDust;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.CaveMotes;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.CometRenderer;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.DeepDark;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.DeepWater;
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
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.SkyHeights;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.StarSky;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.StormRain;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.StormSky;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.SunPass;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Tumbleweeds;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.ViewCapture;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Wake;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WaterEdge;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WaterReflections;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WaterShine;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WaterfallFoam;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WaveSplash;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WeatherStorms;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.ScreenFrost;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WetPlayer;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WindBits;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WindLife;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class StageDispatch {
    private StageDispatch() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onStageHighest(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            SkyHeights.flush();
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            ViewCapture.onRenderViewCaptureStage(event);
            EndAir.onRenderEndAirCapture(event);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            WaterReflections.onRenderReflectionWaterStage(event);
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        boolean probed = stage == RenderLevelStageEvent.Stage.AFTER_SKY || stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS
                || stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || stage == RenderLevelStageEvent.Stage.AFTER_WEATHER;
        if (probed) CaptureHook.probeBegin(stage.toString());
        dispatch(event, stage);
        if (probed) CaptureHook.probeEnd(stage.toString());
    }

    private static void dispatch(RenderLevelStageEvent event, RenderLevelStageEvent.Stage stage) {
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
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            WaterReflections.onRenderReflectionSolidStage(event);
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
            AirMist.onRenderAirMistStage(event);
            Puffs.onRenderPuffsStage(event);
            FishJumps.onRenderFishJumpsStage(event);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            WeatherStorms.onRenderWeatherStormsStage(event);
            StormRain.onRenderStormRainStage(event);
            NetherStorm.onRenderNetherStormStage(event);
            NetherFx.onRenderNetherFxStage(event);
            Birds.onRenderBirdStage(event);
            BatSwarm.onRenderBatSwarmStage(event);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            SunPass.onRenderSunPassStage(event);
            WaterReflections.onRenderReflectionStage(event);
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

    private static final List<Consumer<TickEvent.ClientTickEvent>> FX_TICKS = List.of(
            WindLife::onWindLifeTick,
            WeatherStorms::onWeatherStormsTick,
            WaterfallFoam::onWaterfallFoamTick,
            WaterShine::onWaterShineTick,
            WaterEdge::onWaterEdgeTick,
            Wake::onWakeTick,
            Tumbleweeds::onTumbleweedsTick,
            StormSky::onStormSkyTick,
            Silt::onSiltTick,
            Ripples::onRipplesTick,
            Rainbow::onClientTick,
            Puddles::onPuddleTick,
            PortalGlow::onPortalGlowTick,
            NetherPortalFx::onNetherPortalTick,
            NetherFx::onNetherFxTick,
            NetherDust::onNetherDustTick,
            NetherAir::onNetherAirTick,
            Mist::onMistTick,
            Litter::onLitterTick,
            LavaSurface::onLavaSurfaceTick,
            LavaLife::onLavaLifeTick,
            Haze::onHazeTick,
            GrassBlades::onGrassBladeTick,
            Fireflies::onFireflyTick,
            EndDust::onEndDustTick,
            DeepDark::onDeepDarkTick,
            CometRenderer::onClientTick,
            CaveDust::onCaveDustTick,
            Butterflies::onButterflyTick,
            Bubbles::onBubblesTick,
            DeepWater::onDeepWaterTick,
            Birds::onBirdTick,
            AuroraRenderer::onClientTick,
            AmbientParticles::onClientTick,
            AirMist::onAirMistTick,
            Puffs::onPuffsTick,
            FishJumps::onFishJumpsTick,
            BatSwarm::onBatSwarmTick,
            WetPlayer::onWetPlayerTick,
            ScreenFrost::onScreenFrostTick
    );

    private static final long FX_TICK_BUDGET_NANOS = 150_000L;
    private static TickEvent.ClientTickEvent pendingTick;
    private static int pendingIndex = FX_TICKS.size();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        DynamicFogHandler.onClientTick(event);
        runPendingFxTicks(Long.MAX_VALUE);
        if (event.phase == TickEvent.Phase.END && FxConfig.spreadFxTicks) {
            pendingTick = event;
            pendingIndex = 0;
        } else {
            for (Consumer<TickEvent.ClientTickEvent> tick : FX_TICKS) tick.accept(event);
        }
        CaptureHook.onCaptureHookTick(event);
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) runPendingFxTicks(FX_TICK_BUDGET_NANOS);
    }

    private static void runPendingFxTicks(long budgetNanos) {
        if (pendingIndex >= FX_TICKS.size()) return;
        long start = System.nanoTime();
        TickEvent.ClientTickEvent event = pendingTick;
        do {
            FX_TICKS.get(pendingIndex++).accept(event);
        } while (pendingIndex < FX_TICKS.size() && System.nanoTime() - start < budgetNanos);
        if (pendingIndex >= FX_TICKS.size()) pendingTick = null;
    }
}
