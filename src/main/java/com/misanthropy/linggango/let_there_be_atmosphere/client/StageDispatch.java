package com.misanthropy.linggango.let_there_be_atmosphere.client;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.*;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Minecraft;
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
            Lightning.onRenderLightningStage(event);
            EndSky.onRenderEndSkyStage(event);
            AuroraRenderer.onRenderSkyStage(event);
            CaptureHook.onCaptureHookStage(event);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            SkyGlow.onRenderSkyStage(event);
            StarSky.onRenderStarSkyStage(event);
            HazeBandRenderer.onRenderSkyStage(event);
            CometRenderer.onRenderSkyStage(event);
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
            IndoorSunbeams.onRenderIndoorSunbeamsStage(event);
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
            Tumbleweeds::onTumbleweedsTick,
            StormSky::onStormSkyTick,
            Silt::onSiltTick,
            Ripples::onRipplesTick,
            Rainbow::onClientTick,
            PortalGlow::onPortalGlowTick,
            NetherPortalFx::onNetherPortalTick,
            NetherFx::onNetherFxTick,
            NetherDust::onNetherDustTick,
            NetherAir::onNetherAirTick,
            Mist::onMistTick,
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
            Birds::onBirdTick,
            AuroraRenderer::onClientTick,
            AmbientParticles::onClientTick,
            AirMist::onAirMistTick,
            FishJumps::onFishJumpsTick,
            BatSwarm::onBatSwarmTick,
            TorchFlicker::onTorchFlickerTick,
            CaveDrips::onCaveDripsTick,
            IndoorSunbeams::onIndoorSunbeamsTick
    );

    private static final List<Consumer<TickEvent.ClientTickEvent>> REAL_TICKS = List.of(
            DeepWater::onDeepWaterTick,
            WetPlayer::onWetPlayerTick,
            ScreenFrost::onScreenFrostTick,
            Puffs::onPuffsTick,
            Wake::onWakeTick,
            Puddles::onPuddleTick,
            Litter::onLitterTick
    );

    private static final long FX_TICK_BUDGET_NANOS = 150_000L;
    private static TickEvent.ClientTickEvent pendingTick;
    private static int pendingIndex = FX_TICKS.size();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START && !Minecraft.getInstance().isPaused()) Tempo.advance();
        DynamicFogHandler.onClientTick(event);
        runPendingFxTicks(Long.MAX_VALUE);
        for (Consumer<TickEvent.ClientTickEvent> tick : REAL_TICKS) tick.accept(event);
        if (Tempo.due()) {
            if (event.phase == TickEvent.Phase.END && FxConfig.spreadFxTicks) {
                pendingTick = event;
                pendingIndex = 0;
            } else {
                for (Consumer<TickEvent.ClientTickEvent> tick : FX_TICKS) tick.accept(event);
            }
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
