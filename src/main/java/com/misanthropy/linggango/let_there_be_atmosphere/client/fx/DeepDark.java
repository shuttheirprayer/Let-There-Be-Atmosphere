package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
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
public final class DeepDark {
    private DeepDark() {}

    private static final int MOTE = 33;
    private static final int PULSE = 34;
    private static final int REACH = 20;
    private static final int SPAN = 12;
    private static final int KEEP = 32;
    private static final int NEAR_SQR = 81;
    private static final float[] BASE = {0.04F, 0.16F, 0.19F};
    private static final float[] FLASH = {0.05F, 0.24F, 0.24F};
    private static final long[] LISTENERS = new long[KEEP];
    private static final long[] NEXT_LISTENERS = new long[KEEP];
    private static final boolean[] LIT = new boolean[KEEP];
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    private static int listeners;
    private static int seenListeners;
    private static int seenSculk;
    private static long scanOrigin;
    private static int scanX0, scanZ0, scanWidth, scanTotal;
    private static int scanIndex = -1;
    private static int ticks;
    private static int scannedAt = -1000;
    private static long scannedFrom = Long.MIN_VALUE;
    private static float presence;
    private static float near;
    private static float pulse;
    private static float carry;

    public static float pulse() {
        return pulse;
    }

    public static float presence() {
        return presence;
    }

    public static float fogNear() {
        return 3F;
    }

    public static float fogFar() {
        return 52F * (1F + 0.3F * Math.min(pulse, 1.5F));
    }

    public static void tint(float[] rgb) {
        float flash = Math.min(pulse, 1.2F);
        float k = presence * 0.85F;
        for (int i = 0; i < 3; i++) rgb[i] = Mth.lerp(k, rgb[i], Mth.clamp(BASE[i] + FLASH[i] * flash, 0F, 1F));
    }

    public static void onDeepDarkTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) return;
        if (!FxConfig.deepDarkHaze || !DynamicFogHandler.active() || !level.dimensionType().hasSkyLight()) {
            Diagnostics.skip(Diagnostics.DEEP_DARK, "disabled or no sky in this dimension");
            presence = near = pulse = 0F;
            listeners = 0;
            scannedFrom = Long.MIN_VALUE;
            return;
        }
        BlockPos eye = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        ticks++;
        if (scanIndex < 0 && (ticks - scannedAt >= 100 || scannedFrom == Long.MIN_VALUE || BlockPos.of(scannedFrom).distManhattan(eye) >= 4)) {
            startScan(eye);
            scannedAt = ticks;
            scannedFrom = eye.asLong();
        }
        if (scanIndex >= 0) stepScan(level);
        RandomSource random = level.random;
        pulse = Math.max(0F, pulse - 0.025F);
        boolean deep = level.getBiome(eye).is(Biomes.DEEP_DARK);
        presence += Mth.clamp(Math.max(deep ? 1F : 0F, near * 0.8F) - presence, -0.02F, 0.02F);
        int spawned = watch(level, random);
        if (presence > 0.05F && CameraFluid.of(mc.gameRenderer.getMainCamera()) == FogType.NONE && CaveMotes.resolve()) {
            carry += presence * 0.35F;
            for (; carry >= 1F; carry -= 1F) {
                double x = player.getX() + (random.nextDouble() * 2.0 - 1.0) * 12.0;
                double y = player.getEyeY() + random.nextDouble() * 8.0 - 3.0;
                double z = player.getZ() + (random.nextDouble() * 2.0 - 1.0) * 12.0;
                if (!level.getBlockState(CURSOR.set(Mth.floor(x), Mth.floor(y), Mth.floor(z))).isAir()) continue;
                CaveMotes.emit(MOTE, x, y, z, 12, 12, 8F + random.nextFloat() * 6F, random.nextFloat());
                spawned++;
            }
        }
        if (presence > 0.01F) {
            Diagnostics.mark(Diagnostics.DEEP_DARK, Diagnostics.detail()
                    ? String.format("haze %.2f, near %.2f, %d sensors, pulse %.2f, +%d", presence, near, listeners, pulse, spawned) : null);
        } else {
            Diagnostics.skip(Diagnostics.DEEP_DARK, "not in the deep dark or near sculk");
        }
    }

    private static int watch(ClientLevel level, RandomSource random) {
        int spawned = 0;
        for (int i = 0; i < listeners; i++) {
            BlockState state = level.getBlockState(CURSOR.set(BlockPos.of(LISTENERS[i])));
            boolean shrieker = state.is(Blocks.SCULK_SHRIEKER);
            boolean lit = shrieker ? state.getValue(SculkShriekerBlock.SHRIEKING)
                    : state.getBlock() instanceof SculkSensorBlock && state.getValue(SculkSensorBlock.PHASE) == SculkSensorPhase.ACTIVE;
            if (lit && !LIT[i]) {
                pulse = Math.max(pulse, shrieker ? 1.4F : 0.8F);
                if (CaveMotes.resolve()) spawned += burst(CURSOR.getX() + 0.5, CURSOR.getY() + 0.7, CURSOR.getZ() + 0.5, shrieker ? 10 : 5, random);
            }
            LIT[i] = lit;
        }
        return spawned;
    }

    private static int burst(double x, double y, double z, int motes, RandomSource random) {
        CaveMotes.emit(PULSE, x, y, z, 15, 15, 1.6F, random.nextFloat());
        for (int i = 0; i < motes; i++) {
            CaveMotes.emit(MOTE, x + (random.nextDouble() - 0.5) * 1.6, y + (random.nextDouble() - 0.3) * 1.2,
                    z + (random.nextDouble() - 0.5) * 1.6, 15, 15, 3F + random.nextFloat() * 3F, random.nextFloat());
        }
        return motes + 1;
    }

    private static void startScan(BlockPos eye) {
        scanOrigin = eye.asLong();
        scanX0 = SectionPos.blockToSectionCoord(eye.getX() - REACH);
        scanZ0 = SectionPos.blockToSectionCoord(eye.getZ() - REACH);
        scanWidth = SectionPos.blockToSectionCoord(eye.getX() + REACH) - scanX0 + 1;
        scanTotal = scanWidth * (SectionPos.blockToSectionCoord(eye.getZ() + REACH) - scanZ0 + 1);
        scanIndex = 0;
        seenListeners = seenSculk = 0;
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
                if (section.hasOnlyAir() || !section.maybeHas(DeepDark::sculk)) continue;
                int x0 = Math.max(cx << 4, eye.getX() - REACH), x1 = Math.min((cx << 4) + 15, eye.getX() + REACH);
                int z0 = Math.max(cz << 4, eye.getZ() - REACH), z1 = Math.min((cz << 4) + 15, eye.getZ() + REACH);
                int y0 = Math.max(sy << 4, minY), y1 = Math.min((sy << 4) + 15, maxY);
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int x = x0; x <= x1; x++) {
                            BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                            if (!sculk(state)) continue;
                            int dx = x - eye.getX(), dy = y - eye.getY(), dz = z - eye.getZ();
                            if (dx * dx + dy * dy + dz * dz <= NEAR_SQR) seenSculk++;
                            if (listener(state)) {
                                int slot = seenListeners < KEEP ? seenListeners : random.nextInt(seenListeners + 1);
                                if (slot < KEEP) NEXT_LISTENERS[slot] = BlockPos.asLong(x, y, z);
                                seenListeners++;
                            }
                        }
                    }
                }
            }
        }
        if (++scanIndex < scanTotal) return;
        listeners = Math.min(seenListeners, KEEP);
        System.arraycopy(NEXT_LISTENERS, 0, LISTENERS, 0, listeners);
        for (int i = 0; i < listeners; i++) {
            BlockState state = level.getBlockState(CURSOR.set(BlockPos.of(LISTENERS[i])));
            LIT[i] = state.is(Blocks.SCULK_SHRIEKER) ? state.getValue(SculkShriekerBlock.SHRIEKING)
                    : state.getBlock() instanceof SculkSensorBlock && state.getValue(SculkSensorBlock.PHASE) == SculkSensorPhase.ACTIVE;
        }
        near += (Math.min(1F, seenSculk / 40F) - near) * 0.5F;
        scanIndex = -1;
    }

    private static boolean listener(BlockState state) {
        return state.getBlock() instanceof SculkSensorBlock || state.is(Blocks.SCULK_SHRIEKER);
    }

    private static boolean sculk(BlockState state) {
        return state.is(Blocks.SCULK) || state.is(Blocks.SCULK_VEIN) || state.is(Blocks.SCULK_CATALYST) || listener(state);
    }
}
