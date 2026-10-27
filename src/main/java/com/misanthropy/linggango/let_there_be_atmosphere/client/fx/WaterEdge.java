package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class WaterEdge {
    private WaterEdge() {}

    private static final int GRID = 5;
    private static final int SPAN = GRID * 16;
    private static final int PER_CHUNK = 256;
    private static final int SLOTS = GRID * GRID * PER_CHUNK;
    private static final int MAX_AGE = 200;
    private static final float LAND = -1.0E9F;
    private static final float FLOW = -2.0E9F;
    private static final double REACH = 1024.0;
    private static final int REACH_RIVER = 8;
    private static final int AREA = 16 + 2 * REACH_RIVER;
    private static final int TABLE = AREA + 1;
    private static final float BIAS_X = 0.8F;
    private static final float BIAS_Z = 0.6F;
    private static final float STEP = (float) (Math.PI / 8.0);

    private static final float[][] ROW = new float[2][SLOTS * 4];
    private static final float[] SURFACE = new float[SPAN * SPAN];
    private static final int[] LIGHT = new int[SPAN * SPAN];
    private static boolean[] WET = new boolean[24];
    private static final byte[] MOVE = new byte[SPAN * SPAN];
    private static final float[] FLOW_X = new float[SPAN * SPAN];
    private static final float[] FLOW_Z = new float[SPAN * SPAN];
    private static final boolean[] RIVERY = new boolean[GRID * GRID];
    private static final int[] SUM_N = new int[TABLE * TABLE];
    private static final int[] SUM_X = new int[TABLE * TABLE];
    private static final int[] SUM_Z = new int[TABLE * TABLE];
    private static final int[] SUM_XX = new int[TABLE * TABLE];
    private static final int[] SUM_XZ = new int[TABLE * TABLE];
    private static final int[] SUM_ZZ = new int[TABLE * TABLE];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(SLOTS * 4);
    private static final long[] HELD = new long[GRID * GRID];
    private static final int[] AGE = new int[GRID * GRID];
    private static final int[] USED = new int[GRID * GRID];
    private static final float[] BORN = new float[GRID * GRID];
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final Matrix4f MATRIX = new Matrix4f();

    private static long base = Util.getNanos();
    private static int data;
    private static int dirtyFrom = Integer.MAX_VALUE;
    private static int dirtyTo = -1;
    private static boolean anchored;
    private static double anchorX, anchorY, anchorZ;
    private static ClientLevel level;
    private static int sweep;
    private static int count;
    private static boolean riverWas;

    static {
        java.util.Arrays.fill(HELD, Long.MIN_VALUE);
    }

    private static float now() {
        return ((Util.getNanos() - base) / 1.0E9F) % 3600F;
    }

    private static boolean shore() {
        return FxConfig.shoreFoam && FxConfig.shoreFoamStrength > 0.0;
    }

    private static boolean river() {
        return FxConfig.riverFoam;
    }

    private static boolean on() {
        return shore() || river();
    }

    public static void onWaterEdgeTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        LocalPlayer player = mc.player;
        if (current == null || player == null || !on() || !DynamicFogHandler.active()) {
            if (anchored) reset();
            Diagnostics.skip(Diagnostics.SHORE, "disabled");
            Diagnostics.skip(Diagnostics.RIVERS, "disabled");
            return;
        }
        if (mc.isPaused()) return;
        if (current != level) {
            level = current;
            reset();
        }
        if (river() != riverWas) {
            riverWas = river();
            java.util.Arrays.fill(AGE, MAX_AGE);
        }
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        if (!anchored || Math.abs(px - anchorX) > REACH || Math.abs(pz - anchorZ) > REACH || Math.abs(py - anchorY) > REACH) {
            reset();
            anchorX = Math.floor(px);
            anchorY = Math.floor(py);
            anchorZ = Math.floor(pz);
            anchored = true;
        }
        step(player.getBlockX(), player.getBlockZ());
        if (shore()) Diagnostics.mark(Diagnostics.SHORE, Diagnostics.detail() ? count + " shore columns" : null);
        else Diagnostics.skip(Diagnostics.SHORE, "disabled");
        if (river()) Diagnostics.mark(Diagnostics.RIVERS, Diagnostics.detail() ? count + " columns" : null);
        else Diagnostics.skip(Diagnostics.RIVERS, "disabled");
    }

    private static void reset() {
        for (float[] row : ROW) java.util.Arrays.fill(row, 0F);
        java.util.Arrays.fill(HELD, Long.MIN_VALUE);
        java.util.Arrays.fill(USED, 0);
        count = 0;
        anchored = false;
        dirtyFrom = 0;
        dirtyTo = SLOTS;
    }

    private static void step(int blockX, int blockZ) {
        int cx = SectionPos.blockToSectionCoord(blockX);
        int cz = SectionPos.blockToSectionCoord(blockZ);
        for (int i = 0; i < AGE.length; i++) AGE[i]++;
        int pick = -1;
        for (int i = 0; i < HELD.length && pick < 0; i++) {
            int slot = (sweep + i) % HELD.length;
            if (HELD[slot] != key(wanted(cx, slot % GRID), wanted(cz, slot / GRID))) pick = slot;
        }
        if (pick < 0) {
            sweep = (sweep + 1) % HELD.length;
            if (AGE[sweep] < MAX_AGE) return;
            pick = sweep;
        }
        refresh(pick, wanted(cx, pick % GRID), wanted(cz, pick / GRID));
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    private static int wanted(int center, int slot) {
        int low = center - GRID / 2;
        return low + Math.floorMod(slot - low, GRID);
    }

    private static int slot(int cx, int cz) {
        return Math.floorMod(cx, GRID) + Math.floorMod(cz, GRID) * GRID;
    }

    private static int cell(int x, int z) {
        return Math.floorMod(x, SPAN) + Math.floorMod(z, SPAN) * SPAN;
    }

    private static boolean held(int x, int z) {
        return HELD[slot(x >> 4, z >> 4)] == key(x >> 4, z >> 4);
    }

    private static void refresh(int slot, int cx, int cz) {
        boolean loaded = level.hasChunk(cx, cz) && level.getLightEngine().lightOnInSection(SectionPos.of(cx, 0, cz));
        if (loaded && HELD[slot] != key(cx, cz)) BORN[slot] = now();
        HELD[slot] = loaded ? key(cx, cz) : Long.MIN_VALUE;
        AGE[slot] = 0;
        if (loaded) classify(cx, cz);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) columns(cx + dx, cz + dz);
        }
    }

    private static boolean watery(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
    }

    private static void classify(int cx, int cz) {
        LevelChunk chunk = level.getChunk(cx, cz);
        int sections = chunk.getSectionsCount();
        if (WET.length < sections) WET = new boolean[sections];
        for (int i = 0; i < sections; i++) {
            LevelChunkSection section = chunk.getSection(i);
            WET[i] = !section.hasOnlyAir() && section.maybeHas(WaterEdge::watery);
        }
        int minSection = chunk.getMinSection();
        boolean moving = river();
        RIVERY[slot(cx, cz)] = false;
        for (int z = cz << 4; z < (cz << 4) + 16; z++) {
            for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                int i = cell(x, z);
                SURFACE[i] = LAND;
                MOVE[i] = 0;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                int y = top, found = Integer.MIN_VALUE;
                while (y >= top - 64) {
                    int index = (y >> 4) - minSection;
                    if (index < 0) break;
                    if (index >= sections) {
                        y--;
                        continue;
                    }
                    if (!WET[index]) {
                        y = ((y >> 4) << 4) - 1;
                        continue;
                    }
                    if (chunk.getSection(index).getFluidState(x & 15, y & 15, z & 15).is(FluidTags.WATER)) {
                        found = y;
                        break;
                    }
                    y--;
                }
                if (found == Integer.MIN_VALUE) continue;
                CURSOR.set(x, found, z);
                FluidState fluid = level.getFluidState(CURSOR);
                CURSOR.move(Direction.UP);
                boolean covered = !level.getFluidState(CURSOR).isEmpty();
                CURSOR.move(Direction.DOWN);
                boolean exposed = moving && !covered && found == top;
                boolean flows = false;
                if (exposed && !fluid.isSource() && fluid.getType() instanceof FlowingFluid && !fluid.getValue(FlowingFluid.FALLING)) {
                    Vec3 push = fluid.getFlow(level, CURSOR);
                    FLOW_X[i] = (float) push.x;
                    FLOW_Z[i] = (float) push.z;
                    flows = push.x * push.x + push.z * push.z > 0.01;
                }
                if ((!fluid.isSource() && !flows) || covered) {
                    SURFACE[i] = FLOW;
                    continue;
                }
                SURFACE[i] = CURSOR.getY() + fluid.getHeight(level, CURSOR);
                int block = level.getBrightness(LightLayer.BLOCK, CURSOR), sky = level.getBrightness(LightLayer.SKY, CURSOR);
                CURSOR.move(Direction.UP);
                block = Math.max(block, level.getBrightness(LightLayer.BLOCK, CURSOR));
                sky = Math.max(sky, level.getBrightness(LightLayer.SKY, CURSOR));
                LIGHT[i] = block * 16 + sky;
                if (flows) {
                    MOVE[i] = 2;
                } else if (exposed && level.getBiome(CURSOR).is(BiomeTags.IS_RIVER)) {
                    MOVE[i] = 1;
                    RIVERY[slot(cx, cz)] = true;
                }
            }
        }
    }

    private static final int[] PROBE_Y = new int[400];
    private static final boolean[] PROBE_DRY = new boolean[400];

    private static void columns(int cx, int cz) {
        int s = slot(cx, cz);
        if (HELD[s] != key(cx, cz)) return;
        int first = s * PER_CHUNK;
        int used = 0;
        java.util.Arrays.fill(PROBE_Y, Integer.MIN_VALUE);
        int ox = (cx << 4) - 2, oz = (cz << 4) - 2;
        int ax = (cx << 4) - REACH_RIVER, az = (cz << 4) - REACH_RIVER;
        if (RIVERY[s]) sums(ax, az);
        for (int z = cz << 4; z < (cz << 4) + 16; z++) {
            for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                int i = cell(x, z);
                if (SURFACE[i] == LAND || SURFACE[i] == FLOW) continue;
                int land = 0;
                int move = MOVE[i];
                int wy = Mth.floor(SURFACE[i] - 0.05F);
                for (int b = 0; b < 25 && move != 2; b++) {
                    int lx = x + b % 5 - 2, lz = z + b / 5 - 2;
                    if (!held(lx, lz)) continue;
                    int p = (lz - oz) * 20 + (lx - ox);
                    boolean dry;
                    if (PROBE_Y[p] == wy) {
                        dry = PROBE_DRY[p];
                    } else {
                        BlockState beside = level.getBlockState(CURSOR.set(lx, wy, lz));
                        dry = !beside.getFluidState().is(FluidTags.WATER) && !beside.isAir();
                        PROBE_Y[p] = wy;
                        PROBE_DRY[p] = dry;
                    }
                    if (dry) land |= 1 << b;
                }
                int code = move == 2 ? current(x, z) : move == 1 ? drift(x - ax, z - az) : 0;
                if (land == 0 && code == 0) continue;
                int o = (first + used++) * 4;
                ROW[0][o] = (float) (x - anchorX);
                ROW[0][o + 1] = (float) (SURFACE[i] - anchorY);
                ROW[0][o + 2] = (float) (z - anchorZ);
                ROW[0][o + 3] = LIGHT[i] + 1;
                ROW[1][o] = land & 0x1FFF;
                ROW[1][o + 1] = land >>> 13;
                ROW[1][o + 2] = BORN[s];
                ROW[1][o + 3] = code;
            }
        }
        for (float[] row : ROW) java.util.Arrays.fill(row, (first + used) * 4, (first + PER_CHUNK) * 4, 0F);
        count += used - USED[s];
        USED[s] = used;
        dirtyFrom = Math.min(dirtyFrom, first);
        dirtyTo = Math.max(dirtyTo, first + PER_CHUNK);
    }

    private static int pack(float angle, int speed, boolean flowing) {
        return Math.floorMod(Math.round(angle / STEP), 16) | 16 | speed << 5 | (flowing ? 128 : 0);
    }

    private static int current(int x, int z) {
        float fx = 0F, fz = 0F;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (!held(x + dx, z + dz)) continue;
                int n = cell(x + dx, z + dz);
                if (MOVE[n] != 2) continue;
                fx += FLOW_X[n];
                fz += FLOW_Z[n];
            }
        }
        return fx * fx + fz * fz < 0.01F ? 0 : pack((float) Math.atan2(fz, fx), 3, true);
    }

    private static void sums(int ax, int az) {
        for (int j = 0; j < AREA; j++) {
            int n = 0, sx = 0, sz = 0, sxx = 0, sxz = 0, szz = 0;
            for (int i = 0; i < AREA; i++) {
                if (held(ax + i, az + j) && MOVE[cell(ax + i, az + j)] == 1) {
                    n++;
                    sx += i;
                    sz += j;
                    sxx += i * i;
                    sxz += i * j;
                    szz += j * j;
                }
                int k = (j + 1) * TABLE + i + 1, up = j * TABLE + i + 1;
                SUM_N[k] = SUM_N[up] + n;
                SUM_X[k] = SUM_X[up] + sx;
                SUM_Z[k] = SUM_Z[up] + sz;
                SUM_XX[k] = SUM_XX[up] + sxx;
                SUM_XZ[k] = SUM_XZ[up] + sxz;
                SUM_ZZ[k] = SUM_ZZ[up] + szz;
            }
        }
    }

    private static float window(int[] table, int lx, int lz) {
        int i0 = lx - REACH_RIVER, i1 = lx + REACH_RIVER + 1, j0 = lz - REACH_RIVER, j1 = lz + REACH_RIVER + 1;
        return table[j1 * TABLE + i1] - table[j0 * TABLE + i1] - table[j1 * TABLE + i0] + table[j0 * TABLE + i0];
    }

    private static int drift(int lx, int lz) {
        float n = window(SUM_N, lx, lz);
        if (n < 6F) return 0;
        float mx = window(SUM_X, lx, lz) / n, mz = window(SUM_Z, lx, lz) / n;
        float cxx = window(SUM_XX, lx, lz) / n - mx * mx;
        float czz = window(SUM_ZZ, lx, lz) / n - mz * mz;
        float cxz = window(SUM_XZ, lx, lz) / n - mx * mz;
        float trace = cxx + czz, diff = cxx - czz;
        float aniso = trace > 1.0E-3F ? (float) Math.sqrt(diff * diff + 4F * cxz * cxz) / trace : 0F;
        if (aniso < 0.12F) return 0;
        float theta = 0.5F * (float) Math.atan2(2F * cxz, diff);
        float along = Mth.cos(theta) * BIAS_X + Mth.sin(theta) * BIAS_Z;
        float sign = along / (float) Math.sqrt(along * along + 0.01F);
        float strength = Math.abs(sign) * Mth.clamp((aniso - 0.12F) / 0.28F, 0F, 1F);
        return pack(sign >= 0F ? theta : theta + Mth.PI, Mth.clamp(Math.round(strength * 3F), 0, 3), false);
    }

    public static void onRenderWaterEdgeStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!anchored || count == 0 || LightTint.lightmap == null) return;
        SkyProgram program = SkyProgram.SHORE;
        if (!program.ready()) return;

        int foam = SkyProgram.texture(WaterShine.FOAM);
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
        SkyProgram.texture(3, foam);
        SkyProgram.texture(2, data);
        SkyProgram.texture(1, LightTint.lightmap.getId());
        SkyProgram.texture(0, Haze.noiseTexture());
        program.transform(0, MATRIX);
        GL20.glUniform3f(program.uniform(1), ox, oy, oz);
        GL20.glUniform4f(program.uniform(2), now(), RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(),
                shore() ? (float) FxConfig.shoreFoamStrength : 0F);
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.uniform(3), fog[0], fog[1], fog[2], 1F);
        GL20.glUniform4f(program.uniform(4), (float) anchorX % 1024F, (float) anchorZ % 1024F, river() ? 1F : 0F, 0F);
        program.draw(SLOTS * 6);
        program.end();
        RenderSystem.polygonOffset(0F, 0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void upload() {
        if (data == 0) {
            data = GlStateManager._genTexture();
            GlStateManager._bindTexture(data);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            SkyHeights.unpack();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, SLOTS, 2, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            dirtyFrom = 0;
            dirtyTo = SLOTS;
        }
        int n = dirtyTo - dirtyFrom;
        if (n <= 0) return;
        GlStateManager._bindTexture(data);
        SkyHeights.unpack();
        for (int row = 0; row < 2; row++) {
            UPLOAD.clear();
            UPLOAD.put(ROW[row], dirtyFrom * 4, n * 4).flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, dirtyFrom, row, n, 1, GL11.GL_RGBA, GL11.GL_FLOAT, UPLOAD);
        }
        dirtyFrom = Integer.MAX_VALUE;
        dirtyTo = -1;
    }
}
