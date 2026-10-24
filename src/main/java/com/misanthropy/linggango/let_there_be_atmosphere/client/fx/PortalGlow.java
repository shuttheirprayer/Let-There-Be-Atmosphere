package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class PortalGlow {
    private PortalGlow() {}

    private static final int RAY = 29;
    private static final int SPARK = 30;
    private static final int FLY = 31;
    private static final int STAR = 35;
    private static final int COLUMN = 36;
    private static final int MAX_COLUMNS = 6;
    private static final int REACH = 24;
    private static final int SPAN = 12;
    private static final int KEEP = 96;
    private static final int MAX_FRAMES = 48;
    private static final long[] PORTALS = new long[KEEP];
    private static final long[] NEXT_PORTALS = new long[KEEP];
    private static final long[] FRAMES = new long[MAX_FRAMES];
    private static final long[] NEXT_FRAMES = new long[MAX_FRAMES];
    private static final boolean[] EYE = new boolean[MAX_FRAMES];
    private static final boolean[] OPEN = new boolean[MAX_FRAMES];
    private static final double[] SUM_X = new double[MAX_COLUMNS];
    private static final double[] SUM_Y = new double[MAX_COLUMNS];
    private static final double[] SUM_Z = new double[MAX_COLUMNS];
    private static final int[] SUM_N = new int[MAX_COLUMNS];
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos SIDE = new BlockPos.MutableBlockPos();

    private static int portals;
    private static int frames;
    private static int eyes;
    private static int seenPortals;
    private static int seenFrames;
    private static long scanOrigin;
    private static int scanX0, scanZ0, scanWidth, scanTotal;
    private static int scanIndex = -1;
    private static int ticks;
    private static int scannedAt = -1000;
    private static long scannedFrom = Long.MIN_VALUE;
    private static float rayBudget;
    private static float flyBudget;
    private static float starBudget;
    private static int columns;

    public static void onPortalGlowTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) return;
        if (!(FxConfig.portalGlow || FxConfig.endPortalFx) || !DynamicFogHandler.active()) {
            Diagnostics.skip(Diagnostics.PORTAL, "disabled");
            portals = frames = eyes = 0;
            scannedFrom = Long.MIN_VALUE;
            return;
        }
        if (!CaveMotes.resolve()) return;
        RandomSource random = level.random;
        watch(level, random);
        BlockPos feet = player.blockPosition();
        ticks++;
        if (scanIndex < 0 && (ticks - scannedAt >= 100 || scannedFrom == Long.MIN_VALUE || BlockPos.of(scannedFrom).distManhattan(feet) >= 4)) {
            startScan(feet);
            scannedAt = ticks;
            scannedFrom = feet.asLong();
        }
        if (scanIndex >= 0) stepScan(level);
        if (portals == 0 && eyes == 0) {
            Diagnostics.skip(Diagnostics.PORTAL, frames > 0 ? "empty portal frames" : "no End portal nearby");
            return;
        }
        if (FxConfig.portalGlow) {
            if (portals > 0) {
                rayBudget += 0.35F + 0.08F * Math.min(portals, 20);
                for (; rayBudget >= 1F; rayBudget -= 1F) ray(random);
            }
            flyBudget += (portals > 0 ? 0.22F : 0F) + 0.02F * eyes;
            for (; flyBudget >= 1F; flyBudget -= 1F) firefly(level, random);
        }
        if (FxConfig.endPortalFx) air(random);
        Diagnostics.mark(Diagnostics.PORTAL, Diagnostics.detail()
                ? String.format("%d portal, %d frames, %d eyes", portals, frames, eyes) : null);
    }

    private static void watch(ClientLevel level, RandomSource random) {
        int lit = 0;
        int opened = 0;
        double cx = 0.0, cy = 0.0, cz = 0.0;
        for (int i = 0; i < frames; i++) {
            CURSOR.set(BlockPos.of(FRAMES[i]));
            BlockState state = level.getBlockState(CURSOR);
            if (!state.is(Blocks.END_PORTAL_FRAME)) {
                EYE[i] = OPEN[i] = false;
                continue;
            }
            boolean eye = state.getValue(EndPortalFrameBlock.HAS_EYE);
            Direction facing = state.getValue(EndPortalFrameBlock.FACING);
            SIDE.setWithOffset(CURSOR, facing);
            boolean open = level.getBlockState(SIDE).is(Blocks.END_PORTAL);
            if (eye && !EYE[i]) {
                if (FxConfig.portalGlow) eyeBurst(random);
                if (FxConfig.endPortalFx) eyeSparkle(random);
            }
            if (open && !OPEN[i]) {
                opened++;
                cx += CURSOR.getX() + facing.getStepX() * 2 + 0.5;
                cy += CURSOR.getY();
                cz += CURSOR.getZ() + facing.getStepZ() * 2 + 0.5;
            }
            EYE[i] = eye;
            OPEN[i] = open;
            if (eye) lit++;
        }
        eyes = lit;
        if (opened > 0) {
            if (FxConfig.portalGlow) openBurst(cx / opened, cy / opened, cz / opened, random);
            if (FxConfig.endPortalFx) openStars(cx / opened, cy / opened, cz / opened, random);
            scannedFrom = Long.MIN_VALUE;
        }
    }

    private static void eyeBurst(RandomSource random) {
        double x = CURSOR.getX() + 0.5, y = CURSOR.getY() + 0.95, z = CURSOR.getZ() + 0.5;
        CaveMotes.emit(SPARK, x, y, z, 15, 15, 0.6F, 1F + random.nextFloat());
        for (int i = 0; i < 20; i++) {
            CaveMotes.emit(SPARK, x, y - 0.05, z, 15, 15, 1.0F + random.nextFloat() * 0.6F, random.nextFloat());
        }
        for (int i = 0; i < 3; i++) {
            CaveMotes.emit(RAY, x + (random.nextDouble() - 0.5) * 0.4, y - 0.2, z + (random.nextDouble() - 0.5) * 0.4,
                    15, 15, 1.2F + random.nextFloat() * 0.6F, random.nextFloat());
        }
    }

    private static void openBurst(double x, double y, double z, RandomSource random) {
        CaveMotes.emit(SPARK, x, y + 0.9, z, 15, 15, 0.9F, 2F + random.nextFloat());
        for (int i = 0; i < 56; i++) {
            CaveMotes.emit(SPARK, x + (random.nextDouble() - 0.5) * 2.8, y + 0.8, z + (random.nextDouble() - 0.5) * 2.8,
                    15, 15, 1.2F + random.nextFloat(), random.nextFloat());
        }
        for (int i = 0; i < 12; i++) {
            CaveMotes.emit(RAY, x + (random.nextDouble() - 0.5) * 2.6, y + 0.75, z + (random.nextDouble() - 0.5) * 2.6,
                    15, 15, 1.8F + random.nextFloat() * 1.4F, random.nextFloat());
        }
    }

    private static void eyeSparkle(RandomSource random) {
        double x = CURSOR.getX() + 0.5, y = CURSOR.getY() + 0.95, z = CURSOR.getZ() + 0.5;
        for (int i = 0; i < 8; i++) {
            CaveMotes.emit(SPARK, x, y - 0.05, z, 15, 15, 0.8F + random.nextFloat() * 0.5F, random.nextFloat());
        }
        for (int i = 0; i < 3; i++) {
            CaveMotes.emit(STAR, x + (random.nextDouble() - 0.5) * 0.5, y, z + (random.nextDouble() - 0.5) * 0.5,
                    15, 15, 2F + random.nextFloat() * 2F, random.nextFloat());
        }
    }

    private static void openStars(double x, double y, double z, RandomSource random) {
        for (int i = 0; i < 16; i++) {
            CaveMotes.emit(STAR, x + (random.nextDouble() - 0.5) * 2.8, y + 0.8, z + (random.nextDouble() - 0.5) * 2.8,
                    15, 15, 3F + random.nextFloat() * 2F, random.nextFloat());
        }
    }

    private static void air(RandomSource random) {
        if (portals == 0) return;
        starBudget += 0.12F + 0.008F * Math.min(portals, 24);
        for (; starBudget >= 1F; starBudget -= 1F) {
            CURSOR.set(BlockPos.of(PORTALS[random.nextInt(portals)]));
            CaveMotes.emit(STAR, CURSOR.getX() + 0.1 + random.nextDouble() * 0.8, CURSOR.getY() + 0.8,
                    CURSOR.getZ() + 0.1 + random.nextDouble() * 0.8, 15, 15, 4F + random.nextFloat() * 3F, random.nextFloat());
        }
        for (int i = 0; i < columns; i++) {
            if ((ticks + i * 23) % 60 != 0) continue;
            CaveMotes.emit(COLUMN, SUM_X[i] / SUM_N[i], SUM_Y[i] / SUM_N[i] + 0.8, SUM_Z[i] / SUM_N[i], 15, 15, 6F, random.nextFloat());
        }
    }

    private static void cluster() {
        columns = 0;
        for (int i = 0; i < portals; i++) {
            BlockPos at = BlockPos.of(PORTALS[i]);
            int hit = -1;
            for (int k = 0; k < columns && hit < 0; k++) {
                if (Math.abs(at.getX() + 0.5 - SUM_X[k] / SUM_N[k]) < 4.0 && Math.abs(at.getZ() + 0.5 - SUM_Z[k] / SUM_N[k]) < 4.0
                        && Math.abs(at.getY() - SUM_Y[k] / SUM_N[k]) < 3.0) hit = k;
            }
            if (hit < 0) {
                if (columns == MAX_COLUMNS) continue;
                hit = columns++;
                SUM_X[hit] = SUM_Y[hit] = SUM_Z[hit] = 0.0;
                SUM_N[hit] = 0;
            }
            SUM_X[hit] += at.getX() + 0.5;
            SUM_Y[hit] += at.getY();
            SUM_Z[hit] += at.getZ() + 0.5;
            SUM_N[hit]++;
        }
    }

    private static void ray(RandomSource random) {
        CURSOR.set(BlockPos.of(PORTALS[random.nextInt(portals)]));
        CaveMotes.emit(RAY, CURSOR.getX() + 0.15 + random.nextDouble() * 0.7, CURSOR.getY() + 0.75,
                CURSOR.getZ() + 0.15 + random.nextDouble() * 0.7, 15, 15, 1.8F + random.nextFloat() * 1.4F, random.nextFloat());
    }

    private static void firefly(ClientLevel level, RandomSource random) {
        if (portals > 0) {
            CURSOR.set(BlockPos.of(PORTALS[random.nextInt(portals)]));
        } else {
            int pick = random.nextInt(frames);
            if (!EYE[pick]) return;
            CURSOR.set(BlockPos.of(FRAMES[pick]));
        }
        double x = CURSOR.getX() + 0.5 + (random.nextDouble() - 0.5) * 5.0;
        double y = CURSOR.getY() + 0.8 + random.nextDouble() * 2.2;
        double z = CURSOR.getZ() + 0.5 + (random.nextDouble() - 0.5) * 5.0;
        SIDE.set(x, y, z);
        if (!level.getBlockState(SIDE).isAir()) return;
        CaveMotes.emit(FLY, x, y, z, 15, 15, 8F + random.nextFloat() * 6F, random.nextFloat());
    }

    private static void startScan(BlockPos feet) {
        scanOrigin = feet.asLong();
        scanX0 = SectionPos.blockToSectionCoord(feet.getX() - REACH);
        scanZ0 = SectionPos.blockToSectionCoord(feet.getZ() - REACH);
        scanWidth = SectionPos.blockToSectionCoord(feet.getX() + REACH) - scanX0 + 1;
        scanTotal = scanWidth * (SectionPos.blockToSectionCoord(feet.getZ() + REACH) - scanZ0 + 1);
        scanIndex = 0;
        seenPortals = seenFrames = 0;
    }

    private static void stepScan(ClientLevel level) {
        BlockPos origin = BlockPos.of(scanOrigin);
        int cx = scanX0 + scanIndex % scanWidth;
        int cz = scanZ0 + scanIndex / scanWidth;
        int minY = Math.max(origin.getY() - SPAN, level.getMinBuildHeight());
        int maxY = Math.min(origin.getY() + SPAN, level.getMaxBuildHeight() - 1);
        RandomSource random = level.random;
        LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
        if (chunk != null) {
            for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
                LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                if (section.hasOnlyAir() || !section.maybeHas(PortalGlow::portalish)) continue;
                int x0 = Math.max(cx << 4, origin.getX() - REACH), x1 = Math.min((cx << 4) + 15, origin.getX() + REACH);
                int z0 = Math.max(cz << 4, origin.getZ() - REACH), z1 = Math.min((cz << 4) + 15, origin.getZ() + REACH);
                int y0 = Math.max(sy << 4, minY), y1 = Math.min((sy << 4) + 15, maxY);
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int x = x0; x <= x1; x++) {
                            BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                            if (state.is(Blocks.END_PORTAL)) {
                                int slot = seenPortals < KEEP ? seenPortals : random.nextInt(seenPortals + 1);
                                if (slot < KEEP) NEXT_PORTALS[slot] = BlockPos.asLong(x, y, z);
                                seenPortals++;
                            } else if (state.is(Blocks.END_PORTAL_FRAME) && seenFrames < MAX_FRAMES) {
                                NEXT_FRAMES[seenFrames++] = BlockPos.asLong(x, y, z);
                            }
                        }
                    }
                }
            }
        }
        if (++scanIndex < scanTotal) return;
        portals = Math.min(seenPortals, KEEP);
        System.arraycopy(NEXT_PORTALS, 0, PORTALS, 0, portals);
        cluster();
        frames = seenFrames;
        System.arraycopy(NEXT_FRAMES, 0, FRAMES, 0, frames);
        eyes = 0;
        for (int i = 0; i < frames; i++) {
            CURSOR.set(BlockPos.of(FRAMES[i]));
            BlockState state = level.getBlockState(CURSOR);
            EYE[i] = state.is(Blocks.END_PORTAL_FRAME) && state.getValue(EndPortalFrameBlock.HAS_EYE);
            OPEN[i] = state.is(Blocks.END_PORTAL_FRAME)
                    && level.getBlockState(SIDE.setWithOffset(CURSOR, state.getValue(EndPortalFrameBlock.FACING))).is(Blocks.END_PORTAL);
            if (EYE[i]) eyes++;
        }
        scanIndex = -1;
    }

    private static boolean portalish(BlockState state) {
        return state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME);
    }
}
