package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeFx;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class CaveDust {
    private CaveDust() {}

    private static final int REACH = 16;
    private static final int SPAN = 8;
    private static final int KEEP = 64;
    private static final float DUST_RADIUS = 10F;
    private static final long[] GEMS = new long[KEEP];
    private static final long[] SCULK = new long[KEEP];
    private static final long[] NEXT_GEMS = new long[KEEP];
    private static final long[] NEXT_SCULK = new long[KEEP];
    private static long scanOrigin;
    private static int scanX0, scanZ0, scanWidth, scanTotal;
    private static int scanIndex = -1;
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos SIDE = new BlockPos.MutableBlockPos();

    private static int gems;
    private static int sculk;
    private static int seenGems;
    private static int seenSculk;
    private static int ticks;
    private static int scannedAt = -1000;
    private static long scannedFrom = Long.MIN_VALUE;
    private static float cave;
    private static float carry;

    public static void onCaveDustTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) return;
        if ((!BiomeFx.on(FxConfig.caveDust, BiomeFx.CAVE_DUST) && !BiomeFx.on(FxConfig.caveSparkle, BiomeFx.CAVE_SPARKLE)) || !DynamicFogHandler.active()
                || !level.dimensionType().hasSkyLight()) {
            Diagnostics.skip(Diagnostics.CAVE, "disabled or no sky in this dimension");
            gems = sculk = 0;
            scannedFrom = Long.MIN_VALUE;
            return;
        }
        if (CameraFluid.of(mc.gameRenderer.getMainCamera()) != FogType.NONE || !CaveMotes.resolve()) return;

        BlockPos eye = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        float target = 1F - SkyGlow.smooth(1F, 6F, level.getBrightness(LightLayer.SKY, eye));
        cave += (target - cave) * 0.05F;

        ticks++;
        if (BiomeFx.on(FxConfig.caveSparkle, BiomeFx.CAVE_SPARKLE) && scanIndex < 0 && (ticks - scannedAt >= 200 || scannedFrom == Long.MIN_VALUE
                || BlockPos.of(scannedFrom).distManhattan(eye) >= 4)) {
            startScan(level, eye);
            scannedAt = ticks;
            scannedFrom = eye.asLong();
        }
        if (scanIndex >= 0) stepScan(level);
        RandomSource random = level.random;
        int spawned = 0;

        if (BiomeFx.on(FxConfig.caveDust, BiomeFx.CAVE_DUST) && cave > 0.05F) {
            carry += cave * 1.6F * (float) BiomeFx.amount(FxConfig.caveDustAmount, BiomeFx.CAVE_DUST);
            for (; carry >= 1F; carry -= 1F) {
                double x = player.getX() + (random.nextDouble() * 2.0 - 1.0) * DUST_RADIUS;
                double y = player.getEyeY() + (random.nextDouble() * 2.0 - 1.0) * DUST_RADIUS * 0.6;
                double z = player.getZ() + (random.nextDouble() * 2.0 - 1.0) * DUST_RADIUS;
                CURSOR.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
                if (!level.getBlockState(CURSOR).isAir() || level.getBrightness(LightLayer.BLOCK, CURSOR) < 3) continue;
                CaveMotes.emit(CaveMotes.DUST, x, y, z, Math.max(level.getBrightness(LightLayer.BLOCK, CURSOR), 6),
                        level.getBrightness(LightLayer.SKY, CURSOR), 5F + random.nextFloat() * 4F, random.nextFloat());
                spawned++;
            }
        }

        if (BiomeFx.on(FxConfig.caveSparkle, BiomeFx.CAVE_SPARKLE)) {
            float amount = (float) BiomeFx.amount(FxConfig.caveDustAmount, BiomeFx.CAVE_SPARKLE);
            if (gems > 0 && random.nextFloat() < 0.8F * amount) spawned += glint(mc, level, random);
            if (gems > 8 && random.nextFloat() < 0.6F * amount) spawned += glint(mc, level, random);
            if (sculk > 0 && random.nextFloat() < 0.5F * amount) spawned += spore(mc, level, random);
            if (cave > 0.5F && random.nextFloat() < 0.25F * amount && level.getBiome(eye).is(Biomes.DEEP_DARK)) {
                double x = player.getX() + (random.nextDouble() * 2.0 - 1.0) * DUST_RADIUS;
                double y = player.getY() + random.nextDouble() * 4.0;
                double z = player.getZ() + (random.nextDouble() * 2.0 - 1.0) * DUST_RADIUS;
                CURSOR.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
                if (level.getBlockState(CURSOR).isAir()) {
                    CaveMotes.emit(CaveMotes.SPORE, x, y, z, Math.max(level.getBrightness(LightLayer.BLOCK, CURSOR), 9),
                            level.getBrightness(LightLayer.SKY, CURSOR), 4F + random.nextFloat() * 3F, random.nextFloat());
                    spawned++;
                }
            }
        }

        if (spawned > 0 || gems > 0 || sculk > 0) {
            Diagnostics.mark(Diagnostics.CAVE, Diagnostics.detail()
                    ? String.format("cave %.2f, %d gems, %d sculk, +%d", cave, gems, sculk, spawned) : null);
        } else {
            Diagnostics.skip(Diagnostics.CAVE, "not underground near light, amethyst or sculk");
        }
    }

    private static int glint(Minecraft mc, ClientLevel level, RandomSource random) {
        CURSOR.set(BlockPos.of(GEMS[random.nextInt(gems)]));
        Direction face = Direction.getRandom(random);
        SIDE.setWithOffset(CURSOR, face);
        if (!level.getBlockState(SIDE).isAir()) return 0;
        double x = CURSOR.getX() + 0.5 + face.getStepX() * 0.52 + (face.getStepX() == 0 ? random.nextDouble() - 0.5 : 0.0) * 0.9;
        double y = CURSOR.getY() + 0.5 + face.getStepY() * 0.52 + (face.getStepY() == 0 ? random.nextDouble() - 0.5 : 0.0) * 0.9;
        double z = CURSOR.getZ() + 0.5 + face.getStepZ() * 0.52 + (face.getStepZ() == 0 ? random.nextDouble() - 0.5 : 0.0) * 0.9;
        CaveMotes.emit(CaveMotes.GLINT, x, y, z, 15, 15, 0.5F + random.nextFloat() * 0.4F, random.nextFloat());
        return 1;
    }

    private static int spore(Minecraft mc, ClientLevel level, RandomSource random) {
        CURSOR.set(BlockPos.of(SCULK[random.nextInt(sculk)]));
        double x = CURSOR.getX() + random.nextDouble();
        double y = CURSOR.getY() + 1.05;
        double z = CURSOR.getZ() + random.nextDouble();
        SIDE.set(CURSOR.getX(), CURSOR.getY() + 1, CURSOR.getZ());
        CaveMotes.emit(CaveMotes.SPORE, x, y, z, Math.max(level.getBrightness(LightLayer.BLOCK, SIDE), 9),
                level.getBrightness(LightLayer.SKY, SIDE), 4F + random.nextFloat() * 3F, random.nextFloat());
        return 1;
    }

    private static void startScan(ClientLevel level, BlockPos eye) {
        scanOrigin = eye.asLong();
        scanX0 = SectionPos.blockToSectionCoord(eye.getX() - REACH);
        scanZ0 = SectionPos.blockToSectionCoord(eye.getZ() - REACH);
        scanWidth = SectionPos.blockToSectionCoord(eye.getX() + REACH) - scanX0 + 1;
        scanTotal = scanWidth * (SectionPos.blockToSectionCoord(eye.getZ() + REACH) - scanZ0 + 1);
        scanIndex = 0;
        seenGems = seenSculk = 0;
    }

    private static void stepScan(ClientLevel level) {
        BlockPos eye = BlockPos.of(scanOrigin);
        int cx = scanX0 + scanIndex % scanWidth;
        int cz = scanZ0 + scanIndex / scanWidth;
        int minY = Math.max(eye.getY() - SPAN, level.getMinBuildHeight());
        int maxY = Math.min(eye.getY() + SPAN, level.getMaxBuildHeight() - 1);
        RandomSource random = level.random;
        LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
        if (chunk != null) {
            for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
                LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                if (section.hasOnlyAir()) continue;
                boolean gem = section.maybeHas(CaveDust::gem);
                boolean spore = section.maybeHas(CaveDust::sculk);
                if (!gem && !spore) continue;
                int x0 = Math.max(cx << 4, eye.getX() - REACH), x1 = Math.min((cx << 4) + 15, eye.getX() + REACH);
                int z0 = Math.max(cz << 4, eye.getZ() - REACH), z1 = Math.min((cz << 4) + 15, eye.getZ() + REACH);
                int y0 = Math.max(sy << 4, minY), y1 = Math.min((sy << 4) + 15, maxY);
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int x = x0; x <= x1; x++) {
                            BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                            if (gem && gem(state)) {
                                int slot = seenGems < KEEP ? seenGems : random.nextInt(seenGems + 1);
                                if (slot < KEEP) NEXT_GEMS[slot] = BlockPos.asLong(x, y, z);
                                seenGems++;
                            } else if (spore && sculk(state)) {
                                int slot = seenSculk < KEEP ? seenSculk : random.nextInt(seenSculk + 1);
                                if (slot < KEEP) NEXT_SCULK[slot] = BlockPos.asLong(x, y, z);
                                seenSculk++;
                            }
                        }
                    }
                }
            }
        }
        if (++scanIndex < scanTotal) return;
        gems = Math.min(seenGems, KEEP);
        sculk = Math.min(seenSculk, KEEP);
        System.arraycopy(NEXT_GEMS, 0, GEMS, 0, gems);
        System.arraycopy(NEXT_SCULK, 0, SCULK, 0, sculk);
        scanIndex = -1;
    }

    private static boolean gem(BlockState state) {
        return state.getBlock() instanceof AmethystBlock;
    }

    private static boolean sculk(BlockState state) {
        return state.is(Blocks.SCULK) || state.is(Blocks.SCULK_CATALYST) || state.is(Blocks.SCULK_SENSOR)
                || state.is(Blocks.CALIBRATED_SCULK_SENSOR) || state.is(Blocks.SCULK_SHRIEKER);
    }
}
