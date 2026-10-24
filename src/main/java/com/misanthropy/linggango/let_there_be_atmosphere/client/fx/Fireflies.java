package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class Fireflies {
    private Fireflies() {}

    private static final int RADIUS = 22;
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();
    private static float budget;

    public static void onFireflyTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || !FxConfig.fireflies || FxConfig.firefliesAmount <= 0.0
                || !DynamicFogHandler.active() || !level.dimensionType().hasSkyLight()) {
            Diagnostics.skip(Diagnostics.FIREFLIES, "disabled or no sky here");
            return;
        }
        if (mc.isPaused()) return;
        float angle = level.getTimeOfDay(1F) * Mth.TWO_PI;
        float night = SkyGlow.smooth(0.05F, -0.2F, Mth.cos(angle)) * (1F - level.getRainLevel(1F));
        if (night < 0.01F) {
            Diagnostics.skip(Diagnostics.FIREFLIES, "not night or raining");
            return;
        }
        budget += night * 0.6F * (float) FxConfig.firefliesAmount;
        while (budget >= 1F) {
            budget -= 1F;
            spawn(level, player.getBlockX(), player.getBlockZ());
        }
        Diagnostics.mark(Diagnostics.FIREFLIES, Diagnostics.detail() ? "night " + String.format("%.2f", night) : null);
    }

    private static void spawn(ClientLevel level, int px, int pz) {
        int x = px + RANDOM.nextInt(RADIUS * 2 + 1) - RADIUS;
        int z = pz + RANDOM.nextInt(RADIUS * 2 + 1) - RADIUS;
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int y = Integer.MIN_VALUE;
        for (int i = top - 1; i > top - 32; i--) {
            CURSOR.set(x, i, z);
            BlockState state = level.getBlockState(CURSOR);
            if (!state.getFluidState().isEmpty()) return;
            if (state.blocksMotion() && !state.is(BlockTags.LEAVES)) {
                if (!state.is(BlockTags.DIRT)) return;
                y = i + 1;
                break;
            }
        }
        if (y == Integer.MIN_VALUE) return;
        CURSOR.set(x, y, z);
        Biome biome = level.getBiome(CURSOR).value();
        float temperature = biome.getBaseTemperature();
        if (temperature < 0.5F || temperature > 1.0F || !biome.hasPrecipitation()) return;
        if (level.getBrightness(LightLayer.SKY, CURSOR) < 10 || level.getBrightness(LightLayer.BLOCK, CURSOR) > 9) return;
        float wet = biome.getModifiedClimateSettings().downfall();
        if (RANDOM.nextFloat() > 0.35F + wet * 0.65F) return;
        CaveMotes.emit(CaveMotes.FIREFLY, x + RANDOM.nextFloat(), y + 0.4 + RANDOM.nextFloat() * 1.6, z + RANDOM.nextFloat(),
                15, 15, 9F + RANDOM.nextFloat() * 7F, RANDOM.nextFloat());
    }
}
