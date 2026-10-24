package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

final class WaveField {
    private WaveField() {}

    static final int RES = 8;
    static final int TILE = 4;
    static final int CELLS = RES * TILE;
    static final int DIM = CELLS + 2;
    static final int MAX = 64;
    static final int COLS = 8;
    static final int ROWS = MAX / COLS;
    static final int ROW_TEXELS = 1 + TILE * TILE;

    private static final float ALPHA = 0.4F;
    private static final float BETA = 0.98F;
    private static final float QUIET = 0.03F;
    private static final float SPREAD = 0.05F;
    private static final int QUIET_TICKS = 8;
    private static final int REFRESH = 40;
    private static final int DRY_TICKS = 100;
    private static final double REACH = 1024.0;

    private static final class Tile {
        final Tile[] nb = new Tile[9];
        final boolean[] wet = new boolean[TILE * TILE];
        final float[] syRel = new float[TILE * TILE];
        final int[] light = new int[TILE * TILE];
        float[] f0 = new float[DIM * DIM];
        float[] f1 = new float[DIM * DIM];
        float[] f2 = new float[DIM * DIM];
        final float[] disp = new float[DIM * DIM];
        int tx, tz, by, slot, tint, quiet;
        float peak;
        boolean dirty;
        boolean anyWet;

        void clear() {
            java.util.Arrays.fill(f0, 0F);
            java.util.Arrays.fill(f1, 0F);
            java.util.Arrays.fill(f2, 0F);
            java.util.Arrays.fill(disp, 0F);
            for (int i = 0; i < 9; i++) nb[i] = null;
            quiet = 0;
            peak = 0F;
            dirty = true;
        }
    }

    private static final Long2ObjectOpenHashMap<Tile> MAP = new Long2ObjectOpenHashMap<>();
    private static final Long2IntOpenHashMap DRY = new Long2IntOpenHashMap();
    private static final ArrayList<Tile> LIST = new ArrayList<>();
    private static final ArrayDeque<Tile> POOL = new ArrayDeque<>();
    private static final boolean[] USED = new boolean[MAX];
    private static final float[] META = new float[MAX * ROW_TEXELS * 4];
    private static final FloatBuffer HEIGHT_UPLOAD = BufferUtils.createFloatBuffer(DIM * DIM);
    private static final FloatBuffer META_UPLOAD = BufferUtils.createFloatBuffer(MAX * ROW_TEXELS * 4);
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final Matrix4f MATRIX = new Matrix4f();

    private static ClientLevel level;
    private static boolean anchored;
    private static int anchorX, anchorY, anchorZ;
    private static int ticks;
    private static int drawSlots;
    private static int heightTex, metaTex;
    private static boolean metaDirty = true;

    static {
        DRY.defaultReturnValue(0);
    }

    static boolean ready() {
        return anchored && level != null;
    }

    static ClientLevel level() {
        return level;
    }

    static int anchorX() {
        return anchorX;
    }

    static int anchorY() {
        return anchorY;
    }

    static int anchorZ() {
        return anchorZ;
    }

    static int packedLight(double x, double y, double z) {
        CURSOR.set(x, y + 0.5, z);
        return level.getBrightness(LightLayer.BLOCK, CURSOR) * 16 + level.getBrightness(LightLayer.SKY, CURSOR);
    }

    static int waterTint(double x, double y, double z) {
        CURSOR.set(x, y - 0.1, z);
        return BiomeColors.getAverageWaterColor(level, CURSOR) & 0xFFFFFF;
    }

    static int tiles() {
        return LIST.size();
    }

    static boolean active() {
        return !LIST.isEmpty();
    }

    static void begin(ClientLevel current, double px, double py, double pz) {
        if (current != level) {
            level = current;
            reset();
        }
        if (!anchored || Math.abs(px - anchorX) > REACH || Math.abs(pz - anchorZ) > REACH || Math.abs(py - anchorY) > REACH) {
            reset();
            anchorX = (int) Math.floor(px);
            anchorY = (int) Math.floor(py);
            anchorZ = (int) Math.floor(pz);
            anchored = true;
        }
    }

    static void reset() {
        for (Tile t : LIST) {
            t.clear();
            POOL.add(t);
        }
        LIST.clear();
        MAP.clear();
        DRY.clear();
        java.util.Arrays.fill(USED, false);
        java.util.Arrays.fill(META, 0F);
        drawSlots = 0;
        anchored = false;
        metaDirty = true;
    }

    private static long key(int tx, int tz) {
        return ((long) tx << 32) ^ (tz & 0xFFFFFFFFL);
    }

    private static Tile obtain(int tx, int tz, int by) {
        long k = key(tx, tz);
        Tile found = MAP.get(k);
        if (found != null) return found;
        if (LIST.size() >= MAX || DRY.get(k) > ticks) return null;
        Tile t = POOL.isEmpty() ? new Tile() : POOL.poll();
        t.clear();
        t.tx = tx;
        t.tz = tz;
        t.by = by;
        refresh(t);
        if (!t.anyWet) {
            POOL.add(t);
            DRY.put(k, ticks + DRY_TICKS);
            return null;
        }
        int slot = 0;
        while (USED[slot]) slot++;
        USED[slot] = true;
        t.slot = slot;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                Tile n = MAP.get(key(tx + dx, tz + dz));
                if (n == null) continue;
                t.nb[(dz + 1) * 3 + dx + 1] = n;
                n.nb[(1 - dz) * 3 + 1 - dx] = t;
            }
        }
        MAP.put(k, t);
        LIST.add(t);
        if (slot + 1 > drawSlots) drawSlots = slot + 1;
        writeMeta(t);
        return t;
    }

    private static void release(Tile t) {
        for (int i = 0; i < 9; i++) {
            Tile n = t.nb[i];
            if (n != null) n.nb[8 - i] = null;
        }
        MAP.remove(key(t.tx, t.tz));
        USED[t.slot] = false;
        int o = t.slot * ROW_TEXELS * 4;
        java.util.Arrays.fill(META, o, o + ROW_TEXELS * 4, 0F);
        metaDirty = true;
        POOL.add(t);
        drawSlots = 0;
        for (Tile other : LIST) {
            if (other != t && other.slot + 1 > drawSlots) drawSlots = other.slot + 1;
        }
    }

    private static void refresh(Tile t) {
        boolean any = false;
        boolean tinted = false;
        for (int b = 0; b < TILE * TILE; b++) {
            int x = t.tx * TILE + (b & 3);
            int z = t.tz * TILE + (b >> 2);
            t.wet[b] = false;
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            CURSOR.set(x, t.by, z);
            FluidState fluid = level.getFluidState(CURSOR);
            if (!fluid.isSource() || !fluid.is(FluidTags.WATER)) continue;
            t.syRel[b] = t.by + fluid.getHeight(level, CURSOR) - anchorY;
            CURSOR.setY(t.by + 1);
            if (!level.getFluidState(CURSOR).isEmpty()) continue;
            t.light[b] = level.getBrightness(LightLayer.BLOCK, CURSOR) * 16 + level.getBrightness(LightLayer.SKY, CURSOR);
            if (!tinted) {
                CURSOR.setY(t.by);
                t.tint = BiomeColors.getAverageWaterColor(level, CURSOR) & 0xFFFFFF;
                tinted = true;
            }
            t.wet[b] = true;
            any = true;
        }
        t.anyWet = any;
    }

    private static void writeMeta(Tile t) {
        int o = t.slot * ROW_TEXELS * 4;
        META[o] = t.tx * TILE - anchorX;
        META[o + 1] = t.tz * TILE - anchorZ;
        META[o + 2] = t.tint;
        META[o + 3] = 1F;
        for (int b = 0; b < TILE * TILE; b++) {
            int p = o + (1 + b) * 4;
            META[p] = t.syRel[b];
            META[p + 1] = t.wet[b] ? 1F : 0F;
            META[p + 2] = t.light[b];
            META[p + 3] = 0F;
        }
        metaDirty = true;
    }

    static void impulse(double x, double surfaceY, double z, float radius, float amp, boolean kick) {
        if (!ready()) return;
        int by = (int) Math.floor(surfaceY - 0.01);
        double gx = x * RES, gz = z * RES, rc = radius * RES;
        float inv = (float) (1.0 / (rc * rc));
        int x0 = (int) Math.floor(gx - rc), x1 = (int) Math.ceil(gx + rc);
        int z0 = (int) Math.floor(gz - rc), z1 = (int) Math.ceil(gz + rc);
        Tile t = null;
        int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
        for (int cz = z0; cz <= z1; cz++) {
            for (int cx = x0; cx <= x1; cx++) {
                double dx = cx + 0.5 - gx, dz = cz + 0.5 - gz;
                float q = 1F - (float) (dx * dx + dz * dz) * inv;
                if (q <= 0F) continue;
                int tx = Math.floorDiv(cx, CELLS), tz = Math.floorDiv(cz, CELLS);
                if (tx != lastX || tz != lastZ) {
                    t = obtain(tx, tz, by);
                    lastX = tx;
                    lastZ = tz;
                }
                if (t == null || t.by != by) continue;
                int lx = cx - tx * CELLS, lz = cz - tz * CELLS;
                if (!t.wet[((lz >> 3) << 2) + (lx >> 3)]) continue;
                int i = (lz + 1) * DIM + lx + 1;
                float v = amp * q * q;
                t.f0[i] += v;
                if (!kick) t.f1[i] += v;
                t.quiet = 0;
            }
        }
    }

    private static void halo(Tile t, boolean display) {
        float[] a = display ? t.disp : t.f0;
        Tile n = t.nb[1];
        if (n == null) java.util.Arrays.fill(a, 1, DIM - 1, 0F);
        else System.arraycopy(display ? n.disp : n.f0, CELLS * DIM + 1, a, 1, CELLS);
        n = t.nb[7];
        if (n == null) java.util.Arrays.fill(a, (DIM - 1) * DIM + 1, (DIM - 1) * DIM + 1 + CELLS, 0F);
        else System.arraycopy(display ? n.disp : n.f0, DIM + 1, a, (DIM - 1) * DIM + 1, CELLS);
        Tile w = t.nb[3], e = t.nb[5];
        for (int z = 1; z <= CELLS; z++) {
            a[z * DIM] = w == null ? 0F : (display ? w.disp : w.f0)[z * DIM + CELLS];
            a[z * DIM + DIM - 1] = e == null ? 0F : (display ? e.disp : e.f0)[z * DIM + 1];
        }
        n = t.nb[0];
        a[0] = n == null ? 0F : (display ? n.disp : n.f0)[CELLS * DIM + CELLS];
        n = t.nb[2];
        a[DIM - 1] = n == null ? 0F : (display ? n.disp : n.f0)[CELLS * DIM + 1];
        n = t.nb[6];
        a[(DIM - 1) * DIM] = n == null ? 0F : (display ? n.disp : n.f0)[DIM + CELLS];
        n = t.nb[8];
        a[(DIM - 1) * DIM + DIM - 1] = n == null ? 0F : (display ? n.disp : n.f0)[DIM + 1];
    }

    private static void step(Tile t) {
        float[] u1 = t.f0, u2 = t.f1, u0 = t.f2;
        float peak = 0F;
        for (int bz = 0; bz < TILE; bz++) {
            for (int bx = 0; bx < TILE; bx++) {
                boolean wet = t.wet[bz * TILE + bx];
                for (int z = bz * RES + 1; z <= bz * RES + RES; z++) {
                    int row = z * DIM;
                    for (int x = bx * RES + 1; x <= bx * RES + RES; x++) {
                        int i = row + x;
                        if (!wet) {
                            u0[i] = 0F;
                            continue;
                        }
                        float c = u1[i];
                        float val = 0.5F * (u1[i - DIM] + u1[i + DIM] + u1[i - 1] + u1[i + 1])
                                + 0.25F * (u1[i - DIM - 1] + u1[i - DIM + 1] + u1[i + DIM - 1] + u1[i + DIM + 1])
                                - 3F * c;
                        float n = (ALPHA * val + 2F * c - u2[i]) * BETA;
                        u0[i] = n;
                        float m = n < 0F ? -n : n;
                        if (m > peak) peak = m;
                    }
                }
            }
        }
        t.peak = peak;
    }

    static void tick() {
        ticks++;
        int count = LIST.size();
        if (count == 0) return;
        for (int i = 0; i < count; i++) halo(LIST.get(i), false);
        for (int i = 0; i < count; i++) step(LIST.get(i));
        for (int i = 0; i < count; i++) {
            Tile t = LIST.get(i);
            float[] spare = t.f1;
            t.f1 = t.f0;
            t.f0 = t.f2;
            t.f2 = spare;
            float[] a = t.f0, b = t.f1, c = t.f2, d = t.disp;
            for (int z = 1; z <= CELLS; z++) {
                int row = z * DIM;
                for (int x = row + 1; x <= row + CELLS; x++) d[x] = (a[x] + b[x] + c[x]) * (1F / 3F);
            }
            t.dirty = true;
        }
        for (int i = 0; i < count; i++) halo(LIST.get(i), true);
        for (int i = 0; i < count; i++) {
            Tile t = LIST.get(i);
            if (t.peak > SPREAD) spread(t);
            if ((ticks + t.slot) % REFRESH == 0) {
                refresh(t);
                writeMeta(t);
            }
        }
        for (int i = LIST.size() - 1; i >= 0; i--) {
            Tile t = LIST.get(i);
            if (t.peak < QUIET) t.quiet++;
            else t.quiet = 0;
            if (t.quiet >= QUIET_TICKS || !t.anyWet) {
                LIST.remove(i);
                release(t);
            }
        }
    }

    private static void spread(Tile t) {
        float[] a = t.f0;
        float north = 0F, south = 0F, west = 0F, east = 0F;
        for (int k = 1; k <= CELLS; k++) {
            north = Math.max(north, Math.abs(a[DIM + k]));
            south = Math.max(south, Math.abs(a[CELLS * DIM + k]));
            west = Math.max(west, Math.abs(a[k * DIM + 1]));
            east = Math.max(east, Math.abs(a[k * DIM + CELLS]));
        }
        if (north > SPREAD && t.nb[1] == null) obtain(t.tx, t.tz - 1, t.by);
        if (south > SPREAD && t.nb[7] == null) obtain(t.tx, t.tz + 1, t.by);
        if (west > SPREAD && t.nb[3] == null) obtain(t.tx - 1, t.tz, t.by);
        if (east > SPREAD && t.nb[5] == null) obtain(t.tx + 1, t.tz, t.by);
    }

    private static void upload() {
        if (heightTex == 0) {
            heightTex = GlStateManager._genTexture();
            GlStateManager._bindTexture(heightTex);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            SkyHeights.unpack();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, COLS * DIM, ROWS * DIM, 0, GL11.GL_RED, GL11.GL_FLOAT, (FloatBuffer) null);
            metaTex = GlStateManager._genTexture();
            GlStateManager._bindTexture(metaTex);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            SkyHeights.unpack();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, ROW_TEXELS, MAX, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            metaDirty = true;
        }
        if (metaDirty) {
            GlStateManager._bindTexture(metaTex);
            SkyHeights.unpack();
            META_UPLOAD.clear();
            META_UPLOAD.put(META).flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, ROW_TEXELS, MAX, GL11.GL_RGBA, GL11.GL_FLOAT, META_UPLOAD);
            metaDirty = false;
        }
        boolean bound = false;
        for (int i = 0, n = LIST.size(); i < n; i++) {
            Tile t = LIST.get(i);
            if (!t.dirty) continue;
            if (!bound) {
                GlStateManager._bindTexture(heightTex);
                SkyHeights.unpack();
                bound = true;
            }
            HEIGHT_UPLOAD.clear();
            HEIGHT_UPLOAD.put(t.disp).flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, (t.slot % COLS) * DIM, (t.slot / COLS) * DIM, DIM, DIM, GL11.GL_RED, GL11.GL_FLOAT, HEIGHT_UPLOAD);
            t.dirty = false;
        }
    }

    static void draw(RenderLevelStageEvent event, float time) {
        if (!ready() || LIST.isEmpty() || LightTint.lightmap == null) return;
        SkyProgram program = SkyProgram.RIPPLES;
        if (!program.ready()) return;
        upload();
        Vec3 eye = event.getCamera().getPosition();
        float ox = (float) (anchorX - eye.x);
        float oy = (float) (anchorY - eye.y);
        float oz = (float) (anchorZ - eye.z);
        MATRIX.set(event.getPoseStack().last().pose()).translate(ox, oy, oz);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1F, -3F);
        program.begin();
        SkyProgram.texture(2, heightTex);
        SkyProgram.texture(1, metaTex);
        SkyProgram.texture(0, LightTint.lightmap.getId());
        program.transform(0, MATRIX);
        GL20.glUniform3f(program.uniform(1), ox, oy, oz);
        GL20.glUniform4f(program.uniform(2), time, RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), 1F);
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.uniform(3), fog[0], fog[1], fog[2], 0F);
        program.draw(drawSlots * TILE * TILE * 6);
        program.end();
        RenderSystem.polygonOffset(0F, 0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
}
