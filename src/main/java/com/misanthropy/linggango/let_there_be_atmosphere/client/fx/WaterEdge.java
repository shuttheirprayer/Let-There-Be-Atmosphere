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
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
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

    private static final float[][] ROW = new float[2][SLOTS * 4];
    private static final float[] SURFACE = new float[SPAN * SPAN];
    private static final int[] LIGHT = new int[SPAN * SPAN];
    private static boolean[] WET = new boolean[24];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(SLOTS * 4);
    private static final long[] HELD = new long[GRID * GRID];
    private static final int[] AGE = new int[GRID * GRID];
    private static final int[] USED = new int[GRID * GRID];
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

    static {
        java.util.Arrays.fill(HELD, Long.MIN_VALUE);
    }

    private static float now() {
        return ((Util.getNanos() - base) / 1.0E9F) % 3600F;
    }

    private static boolean on() {
        return FxConfig.shoreFoam && FxConfig.shoreFoamStrength > 0.0;
    }

    public static void onWaterEdgeTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        LocalPlayer player = mc.player;
        if (current == null || player == null || !on() || !DynamicFogHandler.active()) {
            if (anchored) reset();
            Diagnostics.skip(Diagnostics.SHORE, "disabled");
            return;
        }
        if (mc.isPaused()) return;
        if (current != level) {
            level = current;
            reset();
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
        Diagnostics.mark(Diagnostics.SHORE, Diagnostics.detail() ? count + " shore columns" : null);
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
        for (int z = cz << 4; z < (cz << 4) + 16; z++) {
            for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                int i = cell(x, z);
                SURFACE[i] = LAND;
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
                if (!fluid.isSource() || covered) {
                    SURFACE[i] = FLOW;
                    continue;
                }
                SURFACE[i] = CURSOR.getY() + fluid.getHeight(level, CURSOR);
                int block = level.getBrightness(LightLayer.BLOCK, CURSOR), sky = level.getBrightness(LightLayer.SKY, CURSOR);
                CURSOR.move(Direction.UP);
                block = Math.max(block, level.getBrightness(LightLayer.BLOCK, CURSOR));
                sky = Math.max(sky, level.getBrightness(LightLayer.SKY, CURSOR));
                LIGHT[i] = block * 16 + sky;
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
        for (int z = cz << 4; z < (cz << 4) + 16; z++) {
            for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                int i = cell(x, z);
                if (SURFACE[i] == LAND || SURFACE[i] == FLOW) continue;
                int land = 0;
                int wy = Mth.floor(SURFACE[i] - 0.05F);
                for (int b = 0; b < 25; b++) {
                    int lx = x + b % 5 - 2, lz = z + b / 5 - 2;
                    if (!held(lx, lz)) continue;
                    int p = (lz - oz) * 20 + (lx - ox);
                    boolean dry;
                    if (PROBE_Y[p] == wy) {
                        dry = PROBE_DRY[p];
                    } else {
                        dry = !level.getFluidState(CURSOR.set(lx, wy, lz)).is(FluidTags.WATER);
                        PROBE_Y[p] = wy;
                        PROBE_DRY[p] = dry;
                    }
                    if (dry) land |= 1 << b;
                }
                if (land == 0) continue;
                int o = (first + used++) * 4;
                ROW[0][o] = (float) (x - anchorX);
                ROW[0][o + 1] = (float) (SURFACE[i] - anchorY);
                ROW[0][o + 2] = (float) (z - anchorZ);
                ROW[0][o + 3] = LIGHT[i] + 1;
                ROW[1][o] = land & 0x1FFF;
                ROW[1][o + 1] = land >>> 13;
            }
        }
        for (float[] row : ROW) java.util.Arrays.fill(row, (first + used) * 4, (first + PER_CHUNK) * 4, 0F);
        count += used - USED[s];
        USED[s] = used;
        dirtyFrom = Math.min(dirtyFrom, first);
        dirtyTo = Math.max(dirtyTo, first + PER_CHUNK);
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
                (float) FxConfig.shoreFoamStrength);
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.uniform(3), fog[0], fog[1], fog[2], 1F);
        GL20.glUniform4f(program.uniform(4), (float) anchorX % 1024F, (float) anchorZ % 1024F, 0F, 0F);
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
