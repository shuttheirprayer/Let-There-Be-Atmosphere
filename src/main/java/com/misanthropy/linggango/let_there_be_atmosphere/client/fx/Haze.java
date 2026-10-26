package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeFx;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.ByteBuffer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class Haze {
    private Haze() {}

    private static final int REACH = 10;
    private static final int BELOW = 8;
    private static final int ABOVE = 3;
    private static final int NOISE = 64;
    private static final float MIRAGE_PIXELS = 5.0F;
    private static final float NEAR_PIXELS = 4.0F;
    private static final float COLD_PIXELS = 7.0F;

    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    private static float lavaNear;
    private static float mirageTarget;
    private static float nearTarget;
    private static float coldTarget;
    private static float mirage;
    private static float near;
    private static float cold;
    private static long frameAt;
    private static int ticks;
    private static int noise;

    static boolean wanted() {
        return (BiomeFx.on(FxConfig.heatHaze, BiomeFx.HEAT_HAZE) || BiomeFx.on(FxConfig.coldAir, BiomeFx.COLD_AIR)) && (mirage + near + cold > 0.003F
                || mirageTarget + nearTarget + coldTarget > 0F);
    }

    public static void onHazeTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || !DynamicFogHandler.active() || (!BiomeFx.on(FxConfig.heatHaze, BiomeFx.HEAT_HAZE) && !BiomeFx.on(FxConfig.coldAir, BiomeFx.COLD_AIR))) {
            mirageTarget = nearTarget = coldTarget = 0F;
            return;
        }
        if (mc.isPaused()) return;

        CURSOR.set(player.getBlockX(), Mth.floor(player.getEyeY()), player.getBlockZ());
        float temperature = level.getBiome(CURSOR).value().getBaseTemperature();
        float open = level.dimensionType().hasSkyLight()
                ? SkyGlow.smooth(4F, 12F, level.getBrightness(LightLayer.SKY, CURSOR)) : 0F;
        float clear = 1F - level.getRainLevel(1F);
        float sunY = Mth.cos(level.getTimeOfDay(1F) * Mth.TWO_PI);

        if (BiomeFx.on(FxConfig.heatHaze, BiomeFx.HEAT_HAZE)) {
            boolean nether = level.dimensionType().ultraWarm();
            float hot = SkyGlow.smooth(1.0F, 2.0F, temperature) * SkyGlow.smooth(0.1F, 0.5F, sunY) * clear * open;
            mirageTarget = Math.max(hot, nether ? 0.5F : 0F) * (float) BiomeFx.amount(FxConfig.heatHazeStrength, BiomeFx.HEAT_HAZE);
            if (++ticks % 10 == 0) lavaNear = lavaNearness(level, player.blockPosition());
            nearTarget = Math.max(lavaNear, nether ? 0.3F : 0F) * (float) BiomeFx.amount(FxConfig.heatHazeStrength, BiomeFx.HEAT_HAZE);
        } else {
            mirageTarget = nearTarget = 0F;
        }

        if (BiomeFx.on(FxConfig.coldAir, BiomeFx.COLD_AIR)) {
            Biome biome = level.getBiome(CURSOR).value();
            boolean snowing = level.isRaining() && biome.getPrecipitationAt(CURSOR) == Biome.Precipitation.SNOW;
            coldTarget = SkyGlow.smooth(0.2F, -0.2F, temperature) * open * (snowing ? 1F : 0.45F)
                    * (float) BiomeFx.amount(FxConfig.coldAirStrength, BiomeFx.COLD_AIR);
        } else {
            coldTarget = 0F;
        }
    }

    static void update() {
        long now = Util.getNanos();
        float dt = frameAt == 0L ? 1F : Math.min((now - frameAt) / 1.0E9F, 1F);
        frameAt = now;
        float ease = 1F - (float) Math.exp(-dt * 0.8F);
        mirage += (mirageTarget - mirage) * ease;
        near += (nearTarget - near) * (1F - (float) Math.exp(-dt * 2F));
        cold += (coldTarget - cold) * ease;
    }

    static float mirageAmp() { return mirage * MIRAGE_PIXELS / 1080F; }
    static float nearAmp() { return near * NEAR_PIXELS / 1080F; }
    static float coldAmp() { return cold * COLD_PIXELS / 1080F; }

    static float seconds() {
        return (Util.getMillis() % 1_000_000L) / 1000F;
    }

    private static float lavaNearness(ClientLevel level, BlockPos feet) {
        int best = Integer.MAX_VALUE;
        int minY = Math.max(feet.getY() - BELOW, level.getMinBuildHeight());
        int maxY = Math.min(feet.getY() + ABOVE, level.getMaxBuildHeight() - 1);
        for (int cx = SectionPos.blockToSectionCoord(feet.getX() - REACH); cx <= SectionPos.blockToSectionCoord(feet.getX() + REACH); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(feet.getZ() - REACH); cz <= SectionPos.blockToSectionCoord(feet.getZ() + REACH); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.maybeHas(Haze::lava)) continue;
                    int x0 = Math.max(cx << 4, feet.getX() - REACH), x1 = Math.min((cx << 4) + 15, feet.getX() + REACH);
                    int z0 = Math.max(cz << 4, feet.getZ() - REACH), z1 = Math.min((cz << 4) + 15, feet.getZ() + REACH);
                    int y0 = Math.max(sy << 4, minY), y1 = Math.min((sy << 4) + 15, maxY);
                    for (int y = y0; y <= y1; y++) {
                        for (int z = z0; z <= z1; z++) {
                            for (int x = x0; x <= x1; x++) {
                                if (!lava(section.getBlockState(x & 15, y & 15, z & 15))) continue;
                                int dx = x - feet.getX(), dy = y - feet.getY(), dz = z - feet.getZ();
                                best = Math.min(best, dx * dx + dy * dy + dz * dz);
                            }
                        }
                    }
                }
            }
        }
        if (best == Integer.MAX_VALUE) return 0F;
        return 1F - SkyGlow.smooth(2F, REACH, (float) Math.sqrt(best));
    }

    private static boolean lava(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA);
    }

    static int noiseTexture() {
        if (noise != 0) return noise;
        ByteBuffer pixels = BufferUtils.createByteBuffer(NOISE * NOISE * 2);
        float[] a = lattice(17L);
        float[] b = lattice(91L);
        for (int y = 0; y < NOISE; y++) {
            for (int x = 0; x < NOISE; x++) {
                pixels.put((byte) Math.round(sample(a, x, y) * 255F));
                pixels.put((byte) Math.round(sample(b, x, y) * 255F));
            }
        }
        pixels.flip();
        noise = GlStateManager._genTexture();
        GlStateManager._bindTexture(noise);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        SkyHeights.unpack();
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG8, NOISE, NOISE, 0, GL30.GL_RG, GL11.GL_UNSIGNED_BYTE, pixels);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        GlStateManager._bindTexture(0);
        Diagnostics.once("haze noise texture built");
        return noise;
    }

    private static float[] lattice(long seed) {
        float[] cells = new float[64];
        java.util.Random random = new java.util.Random(seed);
        for (int i = 0; i < cells.length; i++) cells[i] = random.nextFloat();
        return cells;
    }

    private static float sample(float[] cells, int x, int y) {
        float fx = x / 8F, fy = y / 8F;
        int ix = (int) fx, iy = (int) fy;
        float tx = fx - ix, ty = fy - iy;
        tx = tx * tx * (3F - 2F * tx);
        ty = ty * ty * (3F - 2F * ty);
        float c00 = cells[(iy & 7) * 8 + (ix & 7)], c10 = cells[(iy & 7) * 8 + ((ix + 1) & 7)];
        float c01 = cells[((iy + 1) & 7) * 8 + (ix & 7)], c11 = cells[((iy + 1) & 7) * 8 + ((ix + 1) & 7)];
        return Mth.lerp(ty, Mth.lerp(tx, c00, c10), Mth.lerp(tx, c01, c11));
    }
}
