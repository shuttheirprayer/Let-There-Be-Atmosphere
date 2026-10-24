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
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
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
public final class LavaLife {
    private LavaLife() {}

    private static final int REACH = 16;
    private static final int SPAN = 8;
    private static final int KEEP = 64;
    private static final long[] SURFACE = new long[KEEP];
    private static final long[] CONTACT = new long[KEEP];
    private static final long[] NEXT_SURFACE = new long[KEEP];
    private static final long[] NEXT_CONTACT = new long[KEEP];
    private static long scanOrigin;
    private static int scanX0, scanZ0, scanWidth, scanTotal;
    private static int scanIndex = -1;
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos SIDE = new BlockPos.MutableBlockPos();

    private static int surfaces;
    private static int contacts;
    private static int seenSurfaces;
    private static int seenContacts;
    private static int ticks;
    private static int scannedAt = -1000;
    private static long scannedFrom = Long.MIN_VALUE;
    private static float carry;

    public static void onLavaLifeTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) return;
        if (!FxConfig.lavaLife || !DynamicFogHandler.active()) {
            Diagnostics.skip(Diagnostics.LAVA, "disabled");
            surfaces = contacts = 0;
            scannedFrom = Long.MIN_VALUE;
            return;
        }
        if (!CaveMotes.resolve()) return;

        BlockPos eye = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        ticks++;
        if (scanIndex < 0 && (ticks - scannedAt >= 100 || scannedFrom == Long.MIN_VALUE || BlockPos.of(scannedFrom).distManhattan(eye) >= 4)) {
            startScan(level, eye);
            scannedAt = ticks;
            scannedFrom = eye.asLong();
        }
        if (scanIndex >= 0) stepScan(level);
        if (surfaces == 0 && contacts == 0) {
            Diagnostics.skip(Diagnostics.LAVA, "no open lava nearby");
            return;
        }

        RandomSource random = level.random;
        float amount = (float) FxConfig.lavaLifeAmount;
        int emitted = 0;
        if (surfaces > 0) {
            carry += (0.4F + 1.4F * Math.min(surfaces, KEEP) / KEEP) * amount;
            for (; carry >= 1F; carry -= 1F) {
                pick(SURFACE, surfaces, random);
                double x = CURSOR.getX() + random.nextDouble();
                double z = CURSOR.getZ() + random.nextDouble();
                double y = CURSOR.getY() + 0.9;
                if (random.nextFloat() < 0.75F) {
                    CaveMotes.emit(CaveMotes.EMBER, x, y, z, 15, 15, 2F + random.nextFloat() * 2F, random.nextFloat());
                } else {
                    CaveMotes.emit(CaveMotes.ASH, x, y + 0.2, z, light(level, CURSOR.above()), sky(level, CURSOR.above()),
                            5F + random.nextFloat() * 2F, random.nextFloat());
                }
                emitted++;
            }
            if (random.nextFloat() < 0.12F * amount * Math.min(1F, surfaces / 8F)) {
                pick(SURFACE, surfaces, random);
                double x = CURSOR.getX() + 0.2 + random.nextDouble() * 0.6;
                double z = CURSOR.getZ() + 0.2 + random.nextDouble() * 0.6;
                double y = CURSOR.getY() + 0.92;
                CaveMotes.emit(CaveMotes.POP, x, y, z, 15, 15, 0.3F, random.nextFloat());
                for (int i = 0; i < 3; i++) {
                    CaveMotes.emit(CaveMotes.DROPLET, x, y, z, 15, 15, 0.55F + random.nextFloat() * 0.2F, random.nextFloat());
                }
                emitted += 4;
            }
        }
        if (contacts > 0 && random.nextFloat() < 0.45F * amount) {
            pick(CONTACT, contacts, random);
            double x = CURSOR.getX() + random.nextDouble();
            double z = CURSOR.getZ() + random.nextDouble();
            double y = CURSOR.getY() + 0.9;
            CaveMotes.emit(CaveMotes.STEAM, x, y, z, light(level, CURSOR.above()), sky(level, CURSOR.above()),
                    2F + random.nextFloat() * 1.2F, random.nextFloat());
            emitted++;
        }
        Diagnostics.mark(Diagnostics.LAVA, Diagnostics.detail()
                ? String.format("%d surfaces, %d contacts, +%d", surfaces, contacts, emitted) : null);
    }

    private static void pick(long[] from, int count, RandomSource random) {
        CURSOR.set(BlockPos.of(from[random.nextInt(count)]));
    }

    private static int light(ClientLevel level, BlockPos pos) {
        return Math.max(level.getBrightness(LightLayer.BLOCK, pos), 8);
    }

    private static int sky(ClientLevel level, BlockPos pos) {
        return level.getBrightness(LightLayer.SKY, pos);
    }

    private static void startScan(ClientLevel level, BlockPos eye) {
        scanOrigin = eye.asLong();
        scanX0 = SectionPos.blockToSectionCoord(eye.getX() - REACH);
        scanZ0 = SectionPos.blockToSectionCoord(eye.getZ() - REACH);
        scanWidth = SectionPos.blockToSectionCoord(eye.getX() + REACH) - scanX0 + 1;
        scanTotal = scanWidth * (SectionPos.blockToSectionCoord(eye.getZ() + REACH) - scanZ0 + 1);
        scanIndex = 0;
        seenSurfaces = seenContacts = 0;
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
                if (section.hasOnlyAir() || !section.maybeHas(LavaLife::lava)) continue;
                int x0 = Math.max(cx << 4, eye.getX() - REACH), x1 = Math.min((cx << 4) + 15, eye.getX() + REACH);
                int z0 = Math.max(cz << 4, eye.getZ() - REACH), z1 = Math.min((cz << 4) + 15, eye.getZ() + REACH);
                int y0 = Math.max(sy << 4, minY), y1 = Math.min((sy << 4) + 15, maxY);
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int x = x0; x <= x1; x++) {
                            if (!lava(section.getBlockState(x & 15, y & 15, z & 15))) continue;
                            CURSOR.set(x, y + 1, z);
                            BlockState above = (y & 15) < 15 ? section.getBlockState(x & 15, (y & 15) + 1, z & 15) : level.getBlockState(CURSOR);
                            if (!above.isAir()) continue;
                            CURSOR.set(x, y, z);
                            if (touchesWater(level)) {
                                int slot = seenContacts < KEEP ? seenContacts : random.nextInt(seenContacts + 1);
                                if (slot < KEEP) NEXT_CONTACT[slot] = BlockPos.asLong(x, y, z);
                                seenContacts++;
                            }
                            int slot = seenSurfaces < KEEP ? seenSurfaces : random.nextInt(seenSurfaces + 1);
                            if (slot < KEEP) NEXT_SURFACE[slot] = BlockPos.asLong(x, y, z);
                            seenSurfaces++;
                        }
                    }
                }
            }
        }
        if (++scanIndex < scanTotal) return;
        surfaces = Math.min(seenSurfaces, KEEP);
        contacts = Math.min(seenContacts, KEEP);
        System.arraycopy(NEXT_SURFACE, 0, SURFACE, 0, surfaces);
        System.arraycopy(NEXT_CONTACT, 0, CONTACT, 0, contacts);
        scanIndex = -1;
    }

    private static boolean touchesWater(ClientLevel level) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int step = 1; step <= 2; step++) {
                SIDE.setWithOffset(CURSOR, direction.getStepX() * step, 0, direction.getStepZ() * step);
                if (level.getFluidState(SIDE).is(FluidTags.WATER)) return true;
            }
        }
        return false;
    }

    private static boolean lava(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA);
    }
}
