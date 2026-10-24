package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class Ripples {
    private Ripples() {}

    private static final int RAIN_RANGE = 16;
    private static final float RING_RADIUS = 0.25F;
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final long BASE = Util.getNanos();

    private static float rainDebt;

    static boolean wanted() {
        return (FxConfig.ripples && FxConfig.ripplesAmount > 0.0) || (FxConfig.waterWake && FxConfig.waterWakeStrength > 0.0);
    }

    private static boolean on() {
        return FxConfig.ripples && FxConfig.ripplesAmount > 0.0;
    }

    public static void emit(double x, double surfaceY, double z, float size, float strength) {
        if (!on() || !WaveField.ready()) return;
        float s = Math.min(Math.max(size, 0.2F), 2.2F);
        float amp = Math.min(0.4F + 0.55F * s, 1.9F) * (0.55F + 0.45F * Math.min(Math.max(strength, 0F), 1F));
        WaveField.impulse(x, surfaceY, z, RING_RADIUS + 0.03F * s, amp, false);
    }

    public static void onRipplesTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || !wanted() || !DynamicFogHandler.active() || ColorGrade.shaderPackActive()) {
            if (WaveField.ready()) {
                WaveField.reset();
                WaveSplash.reset();
            }
            rainDebt = 0F;
            Diagnostics.skip(Diagnostics.RIPPLES, "disabled");
            return;
        }
        if (mc.isPaused()) return;
        WaveField.begin(level, player.getX(), player.getY(), player.getZ());
        if (on()) rain(level, player);
        WaveField.tick();
        Diagnostics.mark(Diagnostics.RIPPLES, Diagnostics.detail() ? WaveField.tiles() + " wave tiles" : null);
    }

    private static void rain(ClientLevel level, LocalPlayer player) {
        float rain = level.getRainLevel(1F);
        if (rain < 0.05F) return;
        float thunder = level.getThunderLevel(1F);
        rainDebt += rain * (9F + 14F * thunder) * (float) FxConfig.ripplesAmount;
        rainDebt = Math.min(rainDebt, 40F);
        int px = player.getBlockX(), pz = player.getBlockZ();
        while (rainDebt >= 1F) {
            rainDebt -= 1F;
            int x = px + RANDOM.nextInt(RAIN_RANGE * 2 + 1) - RAIN_RANGE;
            int z = pz + RANDOM.nextInt(RAIN_RANGE * 2 + 1) - RAIN_RANGE;
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            CURSOR.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
            FluidState fluid = level.getFluidState(CURSOR);
            if (!fluid.isSource() || !fluid.is(FluidTags.WATER)) continue;
            if (level.getBiome(CURSOR).value().getPrecipitationAt(CURSOR) != Biome.Precipitation.RAIN) continue;
            double y = CURSOR.getY() + fluid.getHeight(level, CURSOR);
            WaveField.impulse(x + RANDOM.nextFloat(), y, z + RANDOM.nextFloat(), RING_RADIUS,
                    0.55F + RANDOM.nextFloat() * 0.45F, false);
        }
    }

    public static void onRenderRipplesStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!WaveField.ready() || !WaveField.active()) return;
        WaveField.draw(event, ((Util.getNanos() - BASE) / 1.0E9F) % 3600F);
    }
}
