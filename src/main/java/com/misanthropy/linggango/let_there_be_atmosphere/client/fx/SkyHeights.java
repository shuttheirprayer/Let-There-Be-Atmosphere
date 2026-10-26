package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.FloatBuffer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class SkyHeights {
    private SkyHeights() {}

    static final int SIZE = 128;
    private static final int CHUNKS = SIZE / 16;
    private static final int PER_TICK = 2;
    private static final int MAX_AGE = 100;
    private static final float OPEN = -1.0E4F;
    private static final float NO_RAIN = -3.0E4F;
    static final float LEAF_FLAG = 4096F;
    static final float DRY_FLAG = 16384F;
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final long[] HELD = new long[CHUNKS * CHUNKS];
    private static final int[] AGE = new int[CHUNKS * CHUNKS];
    private static final boolean[] DIRTY = new boolean[CHUNKS * CHUNKS];
    private static final boolean[] RAIN = new boolean[16];
    private static final FloatBuffer COLUMN = BufferUtils.createFloatBuffer(16 * 16 * 2);

    private static int texture;
    private static int rainMode;
    private static ClientLevel level;
    private static int sweep;
    private static int centerX;
    private static int centerZ;
    private static boolean anyDirty;

    static {
        java.util.Arrays.fill(HELD, Long.MIN_VALUE);
    }

    static int texture() {
        return texture;
    }

    static void tick(ClientLevel current, int blockX, int blockZ) {
        if (texture == 0) create();
        int mode = (FxConfig.stormRain ? 2 : 0) | (FxConfig.wetSurfaces ? 1 : 0);
        if (current != level || mode != rainMode) {
            level = current;
            rainMode = mode;
            java.util.Arrays.fill(HELD, Long.MIN_VALUE);
        }
        centerX = SectionPos.blockToSectionCoord(blockX);
        centerZ = SectionPos.blockToSectionCoord(blockZ);
        int done = 0;
        for (int pass = 0; pass < 2 && done < PER_TICK; pass++) {
            for (int i = 0; i < HELD.length && done < PER_TICK; i++) {
                int slot = (sweep + i) % HELD.length;
                int sx = slot % CHUNKS, sz = slot / CHUNKS;
                int cx = wanted(centerX, sx), cz = wanted(centerZ, sz);
                long key = (long) cx << 32 | (cz & 0xFFFFFFFFL);
                AGE[slot]++;
                boolean stale = HELD[slot] != key;
                if (pass == 0 ? !stale : AGE[slot] < MAX_AGE) continue;
                refresh(slot, sx, sz, cx, cz, key);
                done++;
            }
        }
        sweep = (sweep + 1) % HELD.length;
    }

    public static void changed(ClientLevel current, BlockPos pos) {
        if (current != level || pos.getY() < current.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - 1) return;
        int cx = SectionPos.blockToSectionCoord(pos.getX()), cz = SectionPos.blockToSectionCoord(pos.getZ());
        int slot = Math.floorMod(cx, CHUNKS) + Math.floorMod(cz, CHUNKS) * CHUNKS;
        if (HELD[slot] != ((long) cx << 32 | (cz & 0xFFFFFFFFL))) return;
        DIRTY[slot] = true;
        anyDirty = true;
    }

    public static void flush() {
        if (!anyDirty) return;
        anyDirty = false;
        for (int slot = 0; slot < DIRTY.length; slot++) {
            if (!DIRTY[slot]) continue;
            DIRTY[slot] = false;
            long key = HELD[slot];
            if (key != Long.MIN_VALUE) refresh(slot, slot % CHUNKS, slot / CHUNKS, (int) (key >> 32), (int) key, key);
        }
    }

    private static int wanted(int center, int slot) {
        int low = center - CHUNKS / 2;
        return low + Math.floorMod(slot - low, CHUNKS);
    }

    private static void refresh(int slot, int sx, int sz, int cx, int cz, long key) {
        LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
        boolean rain = (FxConfig.stormRain || FxConfig.wetSurfaces) && chunk != null;
        if (chunk != null) quarts(chunk, cx, cz);
        COLUMN.clear();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (chunk == null) {
                    COLUMN.put(OPEN).put(NO_RAIN);
                    continue;
                }
                int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                CURSOR.set(x, top, z);
                BlockState surface = chunk.getBlockState(CURSOR);
                boolean leaves = surface.is(BlockTags.LEAVES);
                boolean wet = RAIN[(z >> 2) * 4 + (x >> 2)];
                COLUMN.put(top + 1 + (leaves ? LEAF_FLAG : 0F) + (dry(chunk, surface, x, top, z, wet) ? DRY_FLAG : 0F));
                COLUMN.put(rain && RAIN[(z >> 2) * 4 + (x >> 2)] ? (leaves && FxConfig.stormRain ? below(chunk, x, top, z) : top + 1) : NO_RAIN);
            }
        }
        COLUMN.flip();
        GlStateManager._bindTexture(texture);
        unpack();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, sx * 16, sz * 16, 16, 16, GL30.GL_RG, GL11.GL_FLOAT, COLUMN);
        HELD[slot] = chunk == null ? Long.MIN_VALUE : key;
        AGE[slot] = 0;
    }

    private static void quarts(LevelChunk chunk, int cx, int cz) {
        for (int q = 0; q < 16; q++) {
            int x = (q & 3) * 4 + 1, z = (q >> 2) * 4 + 1;
            int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1;
            CURSOR.set((cx << 4) + x, top, (cz << 4) + z);
            RAIN[q] = chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(top), QuartPos.fromBlock(z)).value()
                    .getPrecipitationAt(CURSOR) == Biome.Precipitation.RAIN;
        }
    }

    private static boolean dry(LevelChunk chunk, BlockState surface, int x, int top, int z, boolean wet) {
        if (!wet || !surface.getFluidState().isEmpty() || surface.is(BlockTags.ICE) || surface.is(BlockTags.SNOW)) return true;
        CURSOR.set(x, top + 1, z);
        BlockState above = chunk.getBlockState(CURSOR);
        return above.is(BlockTags.SNOW) || !above.getFluidState().isEmpty();
    }

    private static float below(LevelChunk chunk, int x, int top, int z) {
        int y = top - 1;
        int stop = Math.max(top - 24, chunk.getMinBuildHeight());
        for (; y > stop; y--) {
            CURSOR.set(x, y, z);
            BlockState state = chunk.getBlockState(CURSOR);
            if (!state.is(BlockTags.LEAVES) && (state.blocksMotion() || !state.getFluidState().isEmpty())) break;
        }
        return y + 1;
    }

    static void unpack() {
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
    }

    static int centerX() {
        return centerX;
    }

    static int centerZ() {
        return centerZ;
    }

    private static void create() {
        texture = GlStateManager._genTexture();
        GlStateManager._bindTexture(texture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        FloatBuffer open = BufferUtils.createFloatBuffer(SIZE * SIZE * 2);
        for (int i = 0; i < SIZE * SIZE; i++) open.put(OPEN).put(NO_RAIN);
        open.flip();
        unpack();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG32F, SIZE, SIZE, 0, GL30.GL_RG, GL11.GL_FLOAT, open);
    }
}
