package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class LavaSurface {
    private LavaSurface() {}

    private static final int RADIUS = 3;
    private static final int SIDE = RADIUS * 2 + 1;
    private static final int KEEP = 96;
    private static final float CAP = 14F;
    private static final float FULL = 500F;
    private static final float[] REACH = {1F, 1F, 0.55F, 0.25F};
    private static final Map<Long, Spots> CACHE = new HashMap<>();
    private static final Spots[] NEAR = new Spots[SIDE * SIDE];
    private static final float[] CUMULATIVE = new float[SIDE * SIDE];
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    private static ClientLevel known;
    private static int cursor;
    private static int near;
    private static float open;
    private static float budget;

    private static final class Spots {
        final int chunkX, chunkZ;
        int count;
        final int[] spots = new int[KEEP];

        Spots(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }

    public static void onLavaSurfaceTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            CACHE.clear();
            known = null;
            return;
        }
        if (mc.isPaused()) return;
        if (level != known) {
            CACHE.clear();
            known = level;
            open = 0F;
            near = 0;
        }
        float amount = (float) FxConfig.lavaParticlesAmount;
        if (!LavaFx.takesOver() || amount <= 0F) {
            open = 0F;
            return;
        }
        int px = SectionPos.blockToSectionCoord(player.getBlockX());
        int pz = SectionPos.blockToSectionCoord(player.getBlockZ());
        int slot = cursor++ % (SIDE * SIDE);
        scan(level, px - RADIUS + slot % SIDE, pz - RADIUS + slot / SIDE);
        if (slot == 0) {
            for (Iterator<Spots> it = CACHE.values().iterator(); it.hasNext(); ) {
                Spots spots = it.next();
                if (Math.max(Math.abs(spots.chunkX - px), Math.abs(spots.chunkZ - pz)) > RADIUS + 1) it.remove();
            }
        }
        weigh(px, pz);
        if (near == 0) {
            budget = 0F;
            return;
        }
        float rate = Math.min(CAP, 0.3F + 0.3F * Mth.sqrt(open)) * Math.min(amount, 1.5F);
        budget += rate / 20F;
        RandomSource random = level.random;
        float heat = Mth.clamp(open / FULL, 0F, 1F);
        int spawned = 0;
        for (; budget >= 1F; budget -= 1F) {
            if (erupt(level, random, heat)) spawned++;
        }
        if (Diagnostics.detail()) Diagnostics.mark(Diagnostics.LAVA_FX, String.format("open lava %.0f, %.1f eruptions/s, +%d", open, rate, spawned));
    }

    private static void scan(ClientLevel level, int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
        if (chunk == null) {
            CACHE.remove(key);
            return;
        }
        Spots spots = CACHE.computeIfAbsent(key, k -> new Spots(chunkX, chunkZ));
        spots.count = 0;
        RandomSource random = level.random;
        int minY = level.getMinBuildHeight();
        for (int index = 0; index < chunk.getSectionsCount(); index++) {
            LevelChunkSection section = chunk.getSection(index);
            if (section.hasOnlyAir() || !section.maybeHas(LavaSurface::lava)) continue;
            int baseY = minY + (index << 4);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!lava(state)) continue;
                        BlockState above = y < 15 ? section.getBlockState(x, y + 1, z)
                                : chunk.getBlockState(CURSOR.set((chunkX << 4) + x, baseY + 16, (chunkZ << 4) + z));
                        if (!above.isAir()) continue;
                        int slot = spots.count < KEEP ? spots.count : random.nextInt(spots.count + 1);
                        if (slot < KEEP) spots.spots[slot] = ((baseY + y - minY) << 8) | (z << 4) | x;
                        spots.count++;
                    }
                }
            }
        }
    }

    private static boolean lava(BlockState state) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && fluid.is(FluidTags.LAVA);
    }

    private static void weigh(int px, int pz) {
        near = 0;
        open = 0F;
        for (Spots spots : CACHE.values()) {
            if (spots.count == 0) continue;
            int ring = Math.max(Math.abs(spots.chunkX - px), Math.abs(spots.chunkZ - pz));
            if (ring > RADIUS) continue;
            open += spots.count * REACH[ring];
            NEAR[near] = spots;
            CUMULATIVE[near++] = open;
        }
    }

    private static boolean erupt(ClientLevel level, RandomSource random, float heat) {
        float pick = random.nextFloat() * open;
        int chosen = 0;
        while (chosen < near - 1 && CUMULATIVE[chosen] < pick) chosen++;
        Spots spots = NEAR[chosen];
        int spot = spots.spots[random.nextInt(Math.min(spots.count, KEEP))];
        int x = (spots.chunkX << 4) + (spot & 15);
        int z = (spots.chunkZ << 4) + ((spot >> 4) & 15);
        int y = level.getMinBuildHeight() + (spot >> 8);
        CURSOR.set(x, y, z);
        FluidState fluid = level.getFluidState(CURSOR);
        if (fluid.isEmpty() || !fluid.is(FluidTags.LAVA) || !level.getBlockState(CURSOR.above()).isAir()) return false;
        double surface = y + fluid.getHeight(level, CURSOR);
        double ex = x + 0.15 + random.nextDouble() * 0.7, ez = z + 0.15 + random.nextDouble() * 0.7;
        int tone = NetherAir.here(level) && NetherAir.lookAt(level, ex, surface, ez) == NetherAir.CRIMSON ? 1 : 0;
        boolean pop = NetherFx.erupt(random, ex, surface, ez, tone, heat);
        if (pop && random.nextInt(4) == 0) {
            level.playLocalSound(ex, surface, ez, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.12F + 0.1F * random.nextFloat(),
                    0.7F + random.nextFloat() * 0.5F, false);
        }
        return true;
    }
}
