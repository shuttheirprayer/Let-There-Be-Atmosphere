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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class WaterfallFoam {
    private WaterfallFoam() {}

    private static final ResourceLocation ATLAS = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/waterfall_foam.png");
    private static final int GRID = 7;
    private static final int SPAN = GRID * 16;
    private static final int PER_CHUNK = 10;
    private static final int SLOTS = GRID * GRID * PER_CHUNK;
    private static final int QUADS = 22;
    private static final int MAX_AGE = 60;
    private static final int RETRY = 8;
    private static final int MAX_DROP = 32;
    private static final int CANDIDATES = 256;
    private static final double REACH = 1024.0;
    private static final float GROUND_SURFACE = 0.78F;

    private static final float[][] ROW = new float[2][SLOTS * 4];
    private static final long[] HELD = new long[GRID * GRID];
    private static final int[] AGE = new int[GRID * GRID];
    private static final int[] USED = new int[GRID * GRID];
    private static final int[] CX = new int[CANDIDATES], CY = new int[CANDIDATES], CZ = new int[CANDIDATES];
    private static final int[] CH = new int[CANDIDATES], CFACE = new int[CANDIDATES];
    private static final float[] CSURFACE = new float[CANDIDATES];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(SLOTS * 4);
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final Vector3f RIGHT = new Vector3f();
    private static final Vector3f UP = new Vector3f();
    private static final long BASE = Util.getNanos();

    private static int data;
    private static int dirtyFrom = Integer.MAX_VALUE;
    private static int dirtyTo = -1;
    private static boolean anchored;
    private static double anchorX, anchorY, anchorZ;
    private static ClientLevel level;
    private static int sweep;
    private static int count;
    private static int nearChunks;

    static {
        java.util.Arrays.fill(HELD, Long.MIN_VALUE);
    }

    private static float now() {
        return ((Util.getNanos() - BASE) / 1.0E9F) % 3600F;
    }

    private static boolean on() {
        return FxConfig.waterfallFoam && FxConfig.waterfallFoamAmount > 0.0;
    }

    public static void onWaterfallFoamTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        LocalPlayer player = mc.player;
        if (current == null || player == null || !on() || !DynamicFogHandler.active() || ColorGrade.shaderPackActive()) {
            if (anchored) reset();
            Diagnostics.skip(Diagnostics.WATERFALL, "disabled");
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
        nearChunks = Math.max(1, Math.min(GRID / 2, mc.options.getEffectiveRenderDistance()));
        step(player.getBlockX(), player.getBlockZ());
        Diagnostics.mark(Diagnostics.WATERFALL, Diagnostics.detail() ? count + " waterfall spots" : null);
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
        refresh(pick, wanted(cx, pick % GRID), wanted(cz, pick / GRID), cx, cz);
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    private static int wanted(int center, int slot) {
        int low = center - GRID / 2;
        return low + Math.floorMod(slot - low, GRID);
    }

    private static void refresh(int slot, int cx, int cz, int playerCx, int playerCz) {
        boolean near = Math.abs(cx - playerCx) <= nearChunks && Math.abs(cz - playerCz) <= nearChunks;
        LevelChunk chunk = near ? level.getChunkSource().getChunkNow(cx, cz) : null;
        int used = chunk == null ? 0 : scan(chunk, cx, cz, slot * PER_CHUNK);
        HELD[slot] = key(cx, cz);
        AGE[slot] = near && (chunk == null || used < 0) ? MAX_AGE - RETRY : 0;
        used = Math.max(used, 0);
        int first = slot * PER_CHUNK;
        for (float[] row : ROW) java.util.Arrays.fill(row, (first + used) * 4, (first + PER_CHUNK) * 4, 0F);
        count += used - USED[slot];
        USED[slot] = used;
        dirtyFrom = Math.min(dirtyFrom, first);
        dirtyTo = Math.max(dirtyTo, first + PER_CHUNK);
    }

    private static boolean falling(BlockState state) {
        return state.is(Blocks.WATER) && state.getValue(LiquidBlock.LEVEL) >= 8;
    }

    private static int scan(LevelChunk chunk, int cx, int cz, int first) {
        int found = 0;
        int minSection = level.getMinSection();
        LevelChunkSection[] sections = chunk.getSections();
        for (int s = 0; s < sections.length; s++) {
            LevelChunkSection section = sections[s];
            if (section == null || section.hasOnlyAir() || !section.getStates().maybeHas(WaterfallFoam::falling)) continue;
            int baseY = (minSection + s) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!falling(section.getBlockState(x, y, z))) continue;
                        int wx = (cx << 4) + x, wy = baseY + y, wz = (cz << 4) + z;
                        if (found < CANDIDATES && candidate(wx, wy, wz, found)) found++;
                    }
                }
            }
        }
        int used = Math.min(found, PER_CHUNK);
        for (int n = 0; n < used; n++) {
            int best = n;
            for (int m = n + 1; m < found; m++) {
                if (CH[m] > CH[best]) best = m;
            }
            swap(n, best);
            if (!level.getLightEngine().lightOnInSection(SectionPos.of(CX[n] >> 4, CY[n] >> 4, CZ[n] >> 4))) return -1;
        }
        for (int n = 0; n < used; n++) {
            CURSOR.set(CX[n], CY[n], CZ[n]);
            int light = level.getBrightness(LightLayer.BLOCK, CURSOR) * 16 + level.getBrightness(LightLayer.SKY, CURSOR);
            float intensity = Math.min(Math.max(0.25F + 0.75F * (CH[n] - 2) / 6F, 0.25F), 1F);
            int o = (first + n) * 4;
            ROW[0][o] = (float) (CX[n] - anchorX);
            ROW[0][o + 1] = (float) (CSURFACE[n] - anchorY);
            ROW[0][o + 2] = (float) (CZ[n] - anchorZ);
            ROW[0][o + 3] = light + 1;
            ROW[1][o] = intensity;
            ROW[1][o + 1] = Math.min(CH[n], 3);
            ROW[1][o + 2] = CFACE[n];
            ROW[1][o + 3] = CH[n] >= 6 ? 1F : 0F;
        }
        return used;
    }

    private static boolean candidate(int wx, int wy, int wz, int index) {
        BlockState below = level.getBlockState(CURSOR.set(wx, wy - 1, wz));
        if (falling(below)) return false;
        FluidState fluid = below.getFluidState();
        float surface;
        if (fluid.is(FluidTags.WATER)) {
            surface = wy - 1 + fluid.getOwnHeight();
        } else if (below.isFaceSturdy(level, CURSOR, Direction.UP)) {
            surface = wy + GROUND_SURFACE;
        } else {
            return false;
        }
        int height = 1;
        while (height < MAX_DROP && falling(level.getBlockState(CURSOR.set(wx, wy + height, wz)))) height++;
        if (height < 2) return false;
        int faces = 0;
        for (int d = 0; d < 4; d++) {
            if (level.getBlockState(CURSOR.set(wx + SIDES[d].getStepX(), wy, wz + SIDES[d].getStepZ())).isAir()) faces |= 1 << d;
        }
        CX[index] = wx;
        CY[index] = wy;
        CZ[index] = wz;
        CH[index] = height;
        CFACE[index] = faces;
        CSURFACE[index] = surface;
        return true;
    }

    private static void swap(int a, int b) {
        if (a == b) return;
        int t = CX[a]; CX[a] = CX[b]; CX[b] = t;
        t = CY[a]; CY[a] = CY[b]; CY[b] = t;
        t = CZ[a]; CZ[a] = CZ[b]; CZ[b] = t;
        t = CH[a]; CH[a] = CH[b]; CH[b] = t;
        t = CFACE[a]; CFACE[a] = CFACE[b]; CFACE[b] = t;
        float f = CSURFACE[a]; CSURFACE[a] = CSURFACE[b]; CSURFACE[b] = f;
    }

    public static void onRenderWaterfallFoamStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!anchored || count == 0 || LightTint.lightmap == null) return;
        SkyProgram program = SkyProgram.WATERFALL;
        if (!program.ready()) return;

        int atlas = SkyProgram.texture(ATLAS);
        upload();
        Vec3 eye = event.getCamera().getPosition();
        float ox = (float) (anchorX - eye.x);
        float oy = (float) (anchorY - eye.y);
        float oz = (float) (anchorZ - eye.z);
        MATRIX.set(event.getPoseStack().last().pose()).translate(ox, oy, oz);
        RIGHT.set(event.getCamera().getLeftVector()).negate();
        UP.set(event.getCamera().getUpVector());

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1F, -3F);
        program.begin();
        SkyProgram.texture(2, atlas);
        SkyProgram.texture(1, data);
        SkyProgram.texture(0, LightTint.lightmap.getId());
        program.transform(0, MATRIX);
        GL20.glUniform3f(program.uniform(1), ox, oy, oz);
        GL20.glUniform4f(program.uniform(2), now(), RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(),
                (float) FxConfig.waterfallFoamAmount);
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.uniform(3), fog[0], fog[1], fog[2], 0F);
        GL20.glUniform3f(program.uniform(4), RIGHT.x, RIGHT.y, RIGHT.z);
        GL20.glUniform3f(program.uniform(5), UP.x, UP.y, UP.z);
        program.draw(SLOTS * QUADS * 6);
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
