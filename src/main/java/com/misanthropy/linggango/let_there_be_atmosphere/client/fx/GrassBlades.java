package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
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
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class GrassBlades {
    private GrassBlades() {}

    private static final ResourceLocation SHEET = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/grass_blades.png");
    private static final int GRID = 5;
    private static final int PER_CHUNK = 256;
    private static final int SLOTS = GRID * GRID * PER_CHUNK;
    private static final ResourceLocation TALL = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/grass_blades_tall.png");
    private static final int BLADES = 12;
    private static final int TALL_BLADES = 20;
    private static final int MAX_AGE = 200;
    private static final float REBASE = 1800F;
    private static final double REACH = 1024.0;
    private static final float RADIUS = 30F;

    private static final float[][] ROW = new float[2][SLOTS * 4];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(SLOTS * 4);
    private static final long[] HELD = new long[GRID * GRID];
    private static final int[] AGE = new int[GRID * GRID];
    private static final int[] TINTS = new int[16];
    private static final int[] PLACED = new int[GRID * GRID];
    private static final int[] SLOT_X = new int[GRID * GRID];
    private static final int[] SLOT_Z = new int[GRID * GRID];
    private static final int[] LOW = new int[GRID * GRID];
    private static final int[] HIGH = new int[GRID * GRID];
    private static final IntBuffer FIRSTS = BufferUtils.createIntBuffer(GRID * GRID);
    private static final IntBuffer COUNTS = BufferUtils.createIntBuffer(GRID * GRID);
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos BELOW = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos CELL = new BlockPos.MutableBlockPos();
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
        return (Util.getNanos() - base) / 1.0E9F;
    }

    private static boolean on() {
        return FxConfig.grassBlades && FxConfig.grassBladesDensity > 0.0;
    }

    public static void onGrassBladeTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        LocalPlayer player = mc.player;
        if (current == null || player == null || !on() || !DynamicFogHandler.active() || !current.dimensionType().hasSkyLight()) {
            if (anchored) reset();
            Diagnostics.skip(Diagnostics.BLADES, "disabled, or no sky here");
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
        float now = now();
        if (now > REBASE) {
            for (int i = 0; i < SLOTS; i++) ROW[1][i * 4 + 3] = Math.max(ROW[1][i * 4 + 3] - now, -1000F);
            base = Util.getNanos();
            markDirty(0, SLOTS);
            now = 0F;
        }
        step(player.getBlockX(), player.getBlockZ(), now);
        Diagnostics.mark(Diagnostics.BLADES, Diagnostics.detail() ? count + " grass blocks" : null);
    }

    private static void reset() {
        for (float[] row : ROW) java.util.Arrays.fill(row, 0F);
        java.util.Arrays.fill(HELD, Long.MIN_VALUE);
        java.util.Arrays.fill(PLACED, 0);
        count = 0;
        anchored = false;
        markDirty(0, SLOTS);
    }

    private static void markDirty(int from, int to) {
        dirtyFrom = Math.min(dirtyFrom, from);
        dirtyTo = Math.max(dirtyTo, to);
    }

    private static void step(int blockX, int blockZ, float now) {
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
        int wx = wanted(cx, pick % GRID), wz = wanted(cz, pick / GRID);
        long key = key(wx, wz);
        fill(pick, wx, wz, HELD[pick] == key ? -1000F : now);
        HELD[pick] = ready(wx, wz) ? key : Long.MIN_VALUE;
        AGE[pick] = 0;
    }

    private static boolean ready(int cx, int cz) {
        return level.hasChunk(cx, cz) && level.getLightEngine().lightOnInSection(SectionPos.of(cx, 0, cz));
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    private static int wanted(int center, int slot) {
        int low = center - GRID / 2;
        return low + Math.floorMod(slot - low, GRID);
    }

    private static void fill(int slot, int cx, int cz, float birth) {
        int first = slot * PER_CHUNK;
        for (int i = first; i < first + PER_CHUNK; i++) {
            if (ROW[1][i * 4 + 1] > 0F) count--;
        }
        java.util.Arrays.fill(ROW[1], first * 4, (first + PER_CHUNK) * 4, 0F);
        java.util.Arrays.fill(TINTS, -1);
        PLACED[slot] = 0;
        SLOT_X[slot] = cx;
        SLOT_Z[slot] = cz;
        LOW[slot] = Integer.MAX_VALUE;
        HIGH[slot] = Integer.MIN_VALUE;
        if (ready(cx, cz)) {
            float density = (float) FxConfig.grassBladesDensity;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (place(first + z * 16 + x, (cx << 4) + x, (cz << 4) + z, birth, density)) {
                        count++;
                        PLACED[slot]++;
                        LOW[slot] = Math.min(LOW[slot], CURSOR.getY());
                        HIGH[slot] = Math.max(HIGH[slot], CURSOR.getY());
                    }
                }
            }
        }
        markDirty(first, first + PER_CHUNK);
    }

    private static boolean place(int index, int x, int z, float birth, float density) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int ground = Integer.MIN_VALUE;
        for (int y = top - 1; y > top - 40; y--) {
            BELOW.set(x, y, z);
            BlockState state = level.getBlockState(BELOW);
            if (!state.getFluidState().isEmpty() || (state.blocksMotion() && !state.is(BlockTags.LEAVES))) {
                ground = y + 1;
                break;
            }
        }
        if (ground == Integer.MIN_VALUE) return false;
        BELOW.set(x, ground - 1, z);
        if (!level.getBlockState(BELOW).is(Blocks.GRASS_BLOCK)) return false;
        CURSOR.set(x, ground, z);
        BlockState above = level.getBlockState(CURSOR);
        if (!above.isAir() && !(above.is(BlockTags.REPLACEABLE) && above.getFluidState().isEmpty() && !above.is(Blocks.SNOW))) return false;
        int o = index * 4;
        ROW[0][o] = (float) (x - anchorX);
        ROW[0][o + 1] = (float) (ground - anchorY);
        ROW[0][o + 2] = (float) (z - anchorZ);
        ROW[0][o + 3] = tint(x, z);
        ROW[1][o] = level.getBrightness(LightLayer.BLOCK, CURSOR) * 16 + level.getBrightness(LightLayer.SKY, CURSOR);
        ROW[1][o + 1] = 0.05F + 0.95F * hash(x, z);
        ROW[1][o + 2] = density;
        ROW[1][o + 3] = birth;
        return true;
    }

    private static float tint(int x, int z) {
        int cell = ((z & 15) >> 2) * 4 + ((x & 15) >> 2);
        if (TINTS[cell] < 0) {
            CELL.set((x & ~3) + 2, CURSOR.getY(), (z & ~3) + 2);
            TINTS[cell] = level.getBlockTint(CELL, net.minecraft.client.renderer.BiomeColors.GRASS_COLOR_RESOLVER) & 0xFFFFFF;
        }
        return TINTS[cell];
    }

    private static float hash(int x, int z) {
        int h = x * 0x27D4EB2D ^ z * 0x165667B1;
        h = (h ^ (h >>> 15)) * 0x85EBCA6B;
        h = (h ^ (h >>> 13)) * 0xC2B2AE35;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535F;
    }

    public static void onRenderGrassBladeStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) return;
        if (!anchored || count == 0 || LightTint.lightmap == null) return;
        SkyProgram program = SkyProgram.BLADES;
        if (!program.ready()) return;

        upload();
        Vec3 eye = event.getCamera().getPosition();
        float ox = (float) (anchorX - eye.x);
        float oy = (float) (anchorY - eye.y);
        float oz = (float) (anchorZ - eye.z);
        MATRIX.set(event.getPoseStack().last().pose()).translate(ox, oy, oz);
        LocalPlayer player = Minecraft.getInstance().player;
        float partialTick = event.getPartialTick();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        program.begin();
        SkyProgram.texture(2, data);
        SkyProgram.texture(1, LightTint.lightmap.getId());
        boolean tall = FxConfig.grassBladesStyle == 1;
        SkyProgram.texture(0, SkyProgram.texture(tall ? TALL : SHEET));
        program.transform(0, MATRIX);
        GL20.glUniform3f(program.uniform(1), ox, oy, oz);
        GL20.glUniform4f(program.uniform(2), now(), RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), tall ? 1F : 0F);
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.uniform(3), fog[0], fog[1], fog[2], 1F);
        WindSway.update();
        GL20.glUniform4f(program.uniform(4), WindSway.WIND[0], WindSway.WIND[1], WindSway.WIND[2], Math.max(WindSway.WIND[3], 0.6F));
        if (player != null) {
            GL20.glUniform4f(program.uniform(5), (float) (net.minecraft.util.Mth.lerp(partialTick, player.xo, player.getX()) - anchorX),
                    (float) (net.minecraft.util.Mth.lerp(partialTick, player.yo, player.getY()) - anchorY),
                    (float) (net.minecraft.util.Mth.lerp(partialTick, player.zo, player.getZ()) - anchorZ), RADIUS);
        }
        for (int i = 1; i < 4; i++) {
            float[] p = WindSway.PUSH[i];
            GL20.glUniform4f(program.uniform(5 + i), p[0] - ox, p[1] - oy, p[2] - oz, p[3]);
        }
        int perSlot = PER_CHUNK * (tall ? TALL_BLADES : BLADES) * 6;
        FIRSTS.clear();
        COUNTS.clear();
        int runStart = -1, runEnd = -1;
        for (int slot = 0; slot < PLACED.length; slot++) {
            if (PLACED[slot] == 0) continue;
            double x0 = SLOT_X[slot] << 4, z0 = SLOT_Z[slot] << 4;
            double dx = Math.max(Math.max(x0 - eye.x, eye.x - x0 - 16.0), 0.0);
            double dz = Math.max(Math.max(z0 - eye.z, eye.z - z0 - 16.0), 0.0);
            double dy = Math.max(Math.max(LOW[slot] - eye.y, eye.y - HIGH[slot] - 1.0), 0.0);
            if (dx * dx + dy * dy + dz * dz > (RADIUS + 1.0) * (RADIUS + 1.0)) continue;
            if (!event.getFrustum().isVisible(new AABB(x0 - 1.0, LOW[slot] - 0.5, z0 - 1.0, x0 + 17.0, HIGH[slot] + 1.6, z0 + 17.0))) continue;
            if (slot == runEnd) {
                runEnd++;
                continue;
            }
            if (runStart >= 0) {
                FIRSTS.put(runStart * perSlot);
                COUNTS.put((runEnd - runStart) * perSlot);
            }
            runStart = slot;
            runEnd = slot + 1;
        }
        if (runStart >= 0) {
            FIRSTS.put(runStart * perSlot);
            COUNTS.put((runEnd - runStart) * perSlot);
        }
        FIRSTS.flip();
        COUNTS.flip();
        if (FIRSTS.hasRemaining()) GL14.glMultiDrawArrays(GL11.GL_TRIANGLES, FIRSTS, COUNTS);
        program.end();
        RenderSystem.enableCull();
    }

    private static void upload() {
        if (data == 0) {
            data = GlStateManager._genTexture();
            GlStateManager._bindTexture(data);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            SkyHeights.unpack();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, SLOTS, 2, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            markDirty(0, SLOTS);
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
