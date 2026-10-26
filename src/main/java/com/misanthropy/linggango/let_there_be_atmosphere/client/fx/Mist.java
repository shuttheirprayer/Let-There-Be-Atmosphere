package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeFx;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import java.util.Arrays;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class Mist {
    private Mist() {}

    private static final float DENSITY = 0.1F;
    private static final float NOISE_SCALE = 1F / 96F;
    private static final int[] RING = {0, 24, 24, 24, 24, 24, 24, 24, 24, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56};
    private static final int[] HEIGHTS = new int[RING.length];
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    static final float[] COLOR = new float[3];

    private static float target;
    private static float amount;
    private static float floorTarget = Float.NaN;
    private static float floor = Float.NaN;
    private static float wetness;
    private static float swamp;
    private static float swampTarget;
    private static double driftX;
    private static double driftZ;
    private static long frameAt;
    private static int ticks;

    static boolean wanted() {
        return (BiomeFx.on(FxConfig.groundMist, BiomeFx.GROUND_MIST) || BiomeFx.on(FxConfig.swampMist, BiomeFx.SWAMP_MIST)) && amount > 0.003F && !Float.isNaN(floor);
    }

    public static void onMistTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || !(BiomeFx.on(FxConfig.groundMist, BiomeFx.GROUND_MIST) || BiomeFx.on(FxConfig.swampMist, BiomeFx.SWAMP_MIST)) || !DynamicFogHandler.active()
                || !level.dimensionType().hasSkyLight() || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            target = 0F;
            swampTarget = 0F;
            return;
        }
        if (mc.isPaused()) return;

        float rain = level.getRainLevel(1F);
        wetness = rain > 0.2F ? Math.min(1F, wetness + 0.01F) : wetness * 0.9985F;
        float angle = level.getTimeOfDay(1F) * Mth.TWO_PI;
        float sunX = -Mth.sin(angle);
        float sunY = Mth.cos(angle);
        float low = SkyGlow.smooth(-0.3F, -0.05F, sunY) * (1F - SkyGlow.smooth(0.12F, 0.42F, sunY));
        float base = Math.max(Math.max(sunX > 0F ? 0.6F * low : 0.35F * low, 0.6F * SkyGlow.smooth(0.0F, -0.25F, sunY)), 0.08F);
        float swampy = 0F, river = 0F;
        for (int i = 0; i < 5; i++) {
            CURSOR.set(player.getBlockX() + (i == 1 ? 12 : i == 2 ? -12 : 0), player.getBlockY(), player.getBlockZ() + (i == 3 ? 12 : i == 4 ? -12 : 0));
            Holder<Biome> biome = level.getBiome(CURSOR);
            if (biome.is(Tags.Biomes.IS_SWAMP)) swampy += 0.2F;
            if (biome.is(BiomeTags.IS_RIVER)) river += 0.2F;
        }
        swampTarget = BiomeFx.on(FxConfig.swampMist, BiomeFx.SWAMP_MIST) ? swampy * (float) BiomeFx.amount(1.0, BiomeFx.SWAMP_MIST) : 0F;
        base *= 1F + 0.3F * river;
        base = Math.max(base, 0.6F * wetness * (1F - rain));
        base *= 1F - 0.4F * rain;
        target = BiomeFx.on(FxConfig.groundMist, BiomeFx.GROUND_MIST) ? Math.min(base, 1F) * (float) BiomeFx.amount(FxConfig.groundMistDensity, BiomeFx.GROUND_MIST) : 0F;

        if (ticks++ % 40 == 0 || Float.isNaN(floorTarget)) floorTarget = sampleFloor(level, player.getBlockX(), player.getBlockZ());
    }

    private static float sampleFloor(ClientLevel level, int x, int z) {
        int count = 0;
        int ringStart = 0;
        for (int i = 0; i < RING.length; i++) {
            int radius = RING[i];
            int spokes = radius == 0 ? 1 : radius == 24 ? 8 : 12;
            if (i > 0 && RING[i - 1] != radius) ringStart = i;
            double a = (i - ringStart) * Mth.TWO_PI / spokes;
            int sx = x + (int) Math.round(Math.cos(a) * radius);
            int sz = z + (int) Math.round(Math.sin(a) * radius);
            if (!level.hasChunk(sx >> 4, sz >> 4)) continue;
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz);
            for (int k = 0; k < 48 && h > level.getMinBuildHeight(); k++) {
                BlockState state = level.getBlockState(CURSOR.set(sx, h - 1, sz));
                if (!state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && (state.blocksMotion() || !state.getFluidState().isEmpty())) break;
                h--;
            }
            if (h > level.getMinBuildHeight()) HEIGHTS[count++] = h;
        }
        if (count == 0) return Float.NaN;
        Arrays.sort(HEIGHTS, 0, count);
        return HEIGHTS[count / 4];
    }

    static void update(ClientLevel level, float partialTick) {
        long now = Util.getNanos();
        float dt = frameAt == 0L ? 1F : Math.min((now - frameAt) / 1.0E9F, 1F);
        frameAt = now;
        swamp += (swampTarget - swamp) * (1F - (float) Math.exp(-dt * 0.15F));
        float want = Mth.lerp(swamp, target, Math.min(1.3F * (float) FxConfig.groundMistDensity, 3F));
        amount += (want - amount) * (1F - (float) Math.exp(-dt * 0.15F));
        if (!Float.isNaN(floorTarget)) {
            floor = Float.isNaN(floor) ? floorTarget : floor + (floorTarget - floor) * (1F - (float) Math.exp(-dt * 0.3F));
        }
        float seconds = Wind.seconds(level, partialTick);
        float speed = 1.2F * Wind.gust(seconds) * dt;
        driftX += Wind.x(seconds) * speed;
        driftZ += Wind.z(seconds) * speed;

        float bright = Mth.clamp(level.getSkyDarken(partialTick) * 1.1F, 0.08F, 1F);
        COLOR[0] = Mth.lerp(0.3F, DynamicFogHandler.fogColorR(), 0.92F * bright);
        COLOR[1] = Mth.lerp(0.3F, DynamicFogHandler.fogColorG(), 0.94F * bright);
        COLOR[2] = Mth.lerp(0.3F, DynamicFogHandler.fogColorB(), 0.97F * bright);
    }

    static float top(double cameraY) {
        return floor - (float) cameraY;
    }

    static float density() {
        return DENSITY * amount;
    }

    static float falloff() {
        return 2.3F / Mth.lerp(swamp, (float) FxConfig.groundMistHeight, Math.min((float) FxConfig.groundMistHeight, 7F));
    }

    static float noiseU(double cameraX) {
        double u = (cameraX + driftX) * NOISE_SCALE;
        return (float) (u - Math.floor(u));
    }

    static float noiseV(double cameraZ) {
        double v = (cameraZ + driftZ) * NOISE_SCALE;
        return (float) (v - Math.floor(v));
    }

    static float noiseScale() {
        return NOISE_SCALE;
    }

    static float amount() {
        return amount;
    }
}
