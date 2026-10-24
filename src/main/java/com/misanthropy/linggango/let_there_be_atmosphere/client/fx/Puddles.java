package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class Puddles {
    private Puddles() {}

    private static final float SPLASH_RADIUS = 14F;
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    private static float wetness;
    private static float damp;
    private static float raining;
    private static float carry;
    private static boolean heightsLive;

    static float wetness() {
        return wetness;
    }

    static float damp() {
        return damp;
    }

    static float raining() {
        return raining;
    }

    static boolean heightsLive() {
        return heightsLive;
    }

    static boolean wanted() {
        return FxConfig.puddles && wetness > 0.02F;
    }

    public static void onPuddleTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        boolean any = FxConfig.puddles || FxConfig.rainSplashes || FxConfig.cloudShadows || FxConfig.stormRain || FxConfig.wetSurfaces;
        if (level == null || player == null || !any || !DynamicFogHandler.active()
                || !level.dimensionType().hasSkyLight() || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            wetness = raining = damp = 0F;
            heightsLive = false;
            return;
        }
        if (mc.isPaused()) return;

        SkyHeights.tick(level, player.getBlockX(), player.getBlockZ());
        heightsLive = true;

        CURSOR.set(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        boolean rainHere = level.getBiome(CURSOR).value().getPrecipitationAt(CURSOR) == Biome.Precipitation.RAIN;
        raining = rainHere ? level.getRainLevel(1F) : 0F;
        if (raining > 0.2F) {
            wetness = Math.min(1F, wetness + raining / 1200F);
            damp = Math.min(1F, damp + raining / 240F);
        } else {
            wetness = Math.max(0F, wetness - 1F / 3600F);
            damp = Math.max(0F, damp - 1F / 4800F);
        }

        if (FxConfig.rainSplashes && raining > 0.05F && !StormRain.hidesVanilla()) splash(level, player, level.random);
    }

    private static void splash(ClientLevel level, LocalPlayer player, RandomSource random) {
        carry += raining * 5F * (float) FxConfig.puddleAmount;
        for (; carry >= 1F; carry -= 1F) {
            double x = player.getX() + (random.nextDouble() * 2.0 - 1.0) * SPLASH_RADIUS;
            double z = player.getZ() + (random.nextDouble() * 2.0 - 1.0) * SPLASH_RADIUS;
            int bx = Mth.floor(x), bz = Mth.floor(z);
            if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            if (Math.abs(top - player.getY()) > 16) continue;
            CURSOR.set(bx, top, bz);
            if (level.getBiome(CURSOR).value().getPrecipitationAt(CURSOR) != Biome.Precipitation.RAIN) continue;
            CaveMotes.emit(CaveMotes.SPLASH, x, top + 0.03, z, level.getBrightness(LightLayer.BLOCK, CURSOR),
                    level.getBrightness(LightLayer.SKY, CURSOR), 0.3F + random.nextFloat() * 0.15F, random.nextFloat());
        }
    }
}
