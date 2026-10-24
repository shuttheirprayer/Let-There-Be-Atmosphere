package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.PortalParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
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
public final class NetherPortalFx {
    private NetherPortalFx() {}

    private static final int RADIUS = 3;
    private static final int SIDE = RADIUS * 2 + 1;
    private static final int INNER = 3;
    private static final int KEEP = 160;
    private static final int MAX_CELLS = 960;
    private static final int MAX_RECTS = 12;
    private static final int MAX_GROUPS = 24;
    private static final int QUADS = 31;
    private static final double REACH = 60.0;
    private static final double CLEAR = 2.5;
    private static final Map<Long, Cells> CACHE = new HashMap<>();
    private static final int[] CELL_X = new int[MAX_CELLS];
    private static final int[] CELL_Y = new int[MAX_CELLS];
    private static final int[] CELL_Z = new int[MAX_CELLS];
    private static final boolean[] CELL_ALONG_Z = new boolean[MAX_CELLS];
    private static final boolean[] GROUP_ALONG_Z = new boolean[MAX_GROUPS];
    private static final int[] GROUP_PLANE = new int[MAX_GROUPS];
    private static final int[] GROUP_U0 = new int[MAX_GROUPS];
    private static final int[] GROUP_U1 = new int[MAX_GROUPS];
    private static final int[] GROUP_Y0 = new int[MAX_GROUPS];
    private static final int[] GROUP_Y1 = new int[MAX_GROUPS];
    private static final double[] GROUP_DISTANCE = new double[MAX_GROUPS];
    private static final boolean[] GROUP_TAKEN = new boolean[MAX_GROUPS];
    private static final double[] BOUNDS = new double[MAX_RECTS * 6];
    private static final float[] ROW_A = new float[MAX_RECTS * 4];
    private static final float[] ROW_B = new float[MAX_RECTS * 4];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(MAX_RECTS * 4);
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final FloatBuffer MATRIX_UPLOAD = BufferUtils.createFloatBuffer(16);
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    private static ClientLevel known;
    private static int wide, inner, ticks;
    private static int rects;
    private static boolean dirty, rebuild;
    private static int data;
    private static double anchorX, anchorY, anchorZ;

    private static final class Cells {
        final int chunkX, chunkZ;
        int count;
        long signature;
        final long[] cells = new long[KEEP];
        final boolean[] alongZ = new boolean[KEEP];

        Cells(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }

    private static boolean on() {
        return FxConfig.netherPortalFx && DynamicFogHandler.active() && !ColorGrade.shaderPackActive() && SkyProgram.NETHER_PORTAL.ready();
    }

    public static boolean owns(double x, double y, double z) {
        if (rects == 0 || !on()) return false;
        for (int i = 0; i < rects; i++) {
            int o = i * 6;
            if (x > BOUNDS[o] && x < BOUNDS[o + 3] && y > BOUNDS[o + 1] && y < BOUNDS[o + 4] && z > BOUNDS[o + 2] && z < BOUNDS[o + 5]) return true;
        }
        return false;
    }

    private static void clear() {
        CACHE.clear();
        known = null;
        rects = 0;
        wide = inner = 0;
    }

    public static void onNetherPortalTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            clear();
            return;
        }
        if (mc.isPaused()) return;
        if (!FxConfig.netherPortalFx) {
            if (rects > 0 || !CACHE.isEmpty()) clear();
            return;
        }
        if (level != known) {
            clear();
            known = level;
        }
        int px = SectionPos.blockToSectionCoord(player.getBlockX());
        int pz = SectionPos.blockToSectionCoord(player.getBlockZ());
        int slot = wide++ % (SIDE * SIDE);
        if (scan(level, px - RADIUS + slot % SIDE, pz - RADIUS + slot / SIDE)) rebuild = true;
        int near = inner++ % (INNER * INNER);
        if (scan(level, px - 1 + near % INNER, pz - 1 + near / INNER)) rebuild = true;
        if (slot == 0) {
            for (Iterator<Cells> it = CACHE.values().iterator(); it.hasNext(); ) {
                Cells cells = it.next();
                if (Math.max(Math.abs(cells.chunkX - px), Math.abs(cells.chunkZ - pz)) > RADIUS + 1) {
                    it.remove();
                    rebuild = true;
                }
            }
        }
        if (rebuild || ++ticks % 20 == 0) {
            rebuild = false;
            build(player);
        }
    }

    private static boolean scan(ClientLevel level, int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
        if (chunk == null) return CACHE.remove(key) != null;
        Cells cells = CACHE.get(key);
        int count = 0;
        long signature = 0L;
        int minY = level.getMinBuildHeight();
        long[] found = null;
        boolean[] along = null;
        for (int index = 0; index < chunk.getSectionsCount(); index++) {
            LevelChunkSection section = chunk.getSection(index);
            if (section.hasOnlyAir() || !section.maybeHas(NetherPortalFx::portal)) continue;
            int baseY = minY + (index << 4);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!state.is(Blocks.NETHER_PORTAL)) continue;
                        if (found == null) {
                            found = new long[KEEP];
                            along = new boolean[KEEP];
                        }
                        long pos = BlockPos.asLong((chunkX << 4) + x, baseY + y, (chunkZ << 4) + z);
                        boolean alongZ = state.getValue(NetherPortalBlock.AXIS) == Direction.Axis.Z;
                        signature = signature * 31L + pos + (alongZ ? 1L : 0L);
                        if (count < KEEP) {
                            found[count] = pos;
                            along[count] = alongZ;
                        }
                        count++;
                    }
                }
            }
        }
        if (count == 0) return CACHE.remove(key) != null;
        if (cells == null) {
            cells = new Cells(chunkX, chunkZ);
            CACHE.put(key, cells);
        } else if (cells.signature == signature && cells.count == count) {
            return false;
        }
        cells.count = Math.min(count, KEEP);
        cells.signature = signature;
        System.arraycopy(found, 0, cells.cells, 0, cells.count);
        System.arraycopy(along, 0, cells.alongZ, 0, cells.count);
        return true;
    }

    private static boolean portal(BlockState state) {
        return state.is(Blocks.NETHER_PORTAL);
    }

    private static void build(LocalPlayer player) {
        double ex = player.getX(), ey = player.getEyeY(), ez = player.getZ();
        int cellCount = 0;
        for (Cells cells : CACHE.values()) {
            for (int i = 0; i < cells.count && cellCount < MAX_CELLS; i++) {
                BlockPos pos = BlockPos.of(cells.cells[i]);
                if (Math.abs(pos.getX() + 0.5 - ex) > REACH || Math.abs(pos.getZ() + 0.5 - ez) > REACH) continue;
                CELL_X[cellCount] = pos.getX();
                CELL_Y[cellCount] = pos.getY();
                CELL_Z[cellCount] = pos.getZ();
                CELL_ALONG_Z[cellCount++] = cells.alongZ[i];
            }
        }
        int groups = 0;
        for (int i = 0; i < cellCount; i++) {
            boolean alongZ = CELL_ALONG_Z[i];
            int plane = alongZ ? CELL_X[i] : CELL_Z[i];
            int u = alongZ ? CELL_Z[i] : CELL_X[i];
            int y = CELL_Y[i];
            int hit = -1;
            for (int g = 0; g < groups && hit < 0; g++) {
                if (GROUP_ALONG_Z[g] == alongZ && GROUP_PLANE[g] == plane && u >= GROUP_U0[g] - 1 && u <= GROUP_U1[g] + 1
                        && y >= GROUP_Y0[g] - 1 && y <= GROUP_Y1[g] + 1) hit = g;
            }
            if (hit < 0) {
                if (groups == MAX_GROUPS) continue;
                hit = groups++;
                GROUP_ALONG_Z[hit] = alongZ;
                GROUP_PLANE[hit] = plane;
                GROUP_U0[hit] = GROUP_U1[hit] = u;
                GROUP_Y0[hit] = GROUP_Y1[hit] = y;
            }
            GROUP_U0[hit] = Math.min(GROUP_U0[hit], u);
            GROUP_U1[hit] = Math.max(GROUP_U1[hit], u);
            GROUP_Y0[hit] = Math.min(GROUP_Y0[hit], y);
            GROUP_Y1[hit] = Math.max(GROUP_Y1[hit], y);
        }
        groups = merge(groups);
        for (int g = 0; g < groups; g++) {
            double cx = GROUP_ALONG_Z[g] ? GROUP_PLANE[g] + 0.5 : (GROUP_U0[g] + GROUP_U1[g] + 1) * 0.5;
            double cz = GROUP_ALONG_Z[g] ? (GROUP_U0[g] + GROUP_U1[g] + 1) * 0.5 : GROUP_PLANE[g] + 0.5;
            double cy = (GROUP_Y0[g] + GROUP_Y1[g] + 1) * 0.5;
            GROUP_DISTANCE[g] = (cx - ex) * (cx - ex) + (cy - ey) * (cy - ey) + (cz - ez) * (cz - ez);
            GROUP_TAKEN[g] = false;
        }
        anchorX = Math.floor(ex);
        anchorY = Math.floor(ey);
        anchorZ = Math.floor(ez);
        int count = Math.min(groups, MAX_RECTS);
        for (int slot = 0; slot < count; slot++) {
            int best = -1;
            for (int g = 0; g < groups; g++) {
                if (!GROUP_TAKEN[g] && (best < 0 || GROUP_DISTANCE[g] < GROUP_DISTANCE[best])) best = g;
            }
            GROUP_TAKEN[best] = true;
            boolean alongZ = GROUP_ALONG_Z[best];
            int minX = alongZ ? GROUP_PLANE[best] : GROUP_U0[best], maxX = alongZ ? GROUP_PLANE[best] + 1 : GROUP_U1[best] + 1;
            int minZ = alongZ ? GROUP_U0[best] : GROUP_PLANE[best], maxZ = alongZ ? GROUP_U1[best] + 1 : GROUP_PLANE[best] + 1;
            int minY = GROUP_Y0[best], maxY = GROUP_Y1[best] + 1;
            int o = slot * 4;
            ROW_A[o] = (float) (minX - anchorX);
            ROW_A[o + 1] = (float) (minY - anchorY);
            ROW_A[o + 2] = (float) (minZ - anchorZ);
            ROW_A[o + 3] = alongZ ? 1F : 0F;
            ROW_B[o] = (float) (maxX - anchorX);
            ROW_B[o + 1] = (float) (maxY - anchorY);
            ROW_B[o + 2] = (float) (maxZ - anchorZ);
            ROW_B[o + 3] = ((minX * 73856093 ^ minY * 19349663 ^ minZ * 83492791) & 0xFFFF) / 65536F;
            int b = slot * 6;
            BOUNDS[b] = minX - (alongZ ? CLEAR : 1.5);
            BOUNDS[b + 1] = minY - 1.5;
            BOUNDS[b + 2] = minZ - (alongZ ? 1.5 : CLEAR);
            BOUNDS[b + 3] = maxX + (alongZ ? CLEAR : 1.5);
            BOUNDS[b + 4] = maxY + 1.5;
            BOUNDS[b + 5] = maxZ + (alongZ ? 1.5 : CLEAR);
        }
        rects = count;
        dirty = true;
    }

    private static int merge(int groups) {
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int a = 0; a < groups && !merged; a++) {
                for (int b = a + 1; b < groups && !merged; b++) {
                    if (GROUP_ALONG_Z[a] != GROUP_ALONG_Z[b] || GROUP_PLANE[a] != GROUP_PLANE[b]) continue;
                    if (GROUP_U0[b] > GROUP_U1[a] + 1 || GROUP_U1[b] < GROUP_U0[a] - 1 || GROUP_Y0[b] > GROUP_Y1[a] + 1
                            || GROUP_Y1[b] < GROUP_Y0[a] - 1) continue;
                    GROUP_U0[a] = Math.min(GROUP_U0[a], GROUP_U0[b]);
                    GROUP_U1[a] = Math.max(GROUP_U1[a], GROUP_U1[b]);
                    GROUP_Y0[a] = Math.min(GROUP_Y0[a], GROUP_Y0[b]);
                    GROUP_Y1[a] = Math.max(GROUP_Y1[a], GROUP_Y1[b]);
                    int last = groups - 1;
                    GROUP_ALONG_Z[b] = GROUP_ALONG_Z[last];
                    GROUP_PLANE[b] = GROUP_PLANE[last];
                    GROUP_U0[b] = GROUP_U0[last];
                    GROUP_U1[b] = GROUP_U1[last];
                    GROUP_Y0[b] = GROUP_Y0[last];
                    GROUP_Y1[b] = GROUP_Y1[last];
                    groups--;
                    merged = true;
                }
            }
        }
        return groups;
    }

    public static void onRenderNetherPortalStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!FxConfig.netherPortalFx) {
            Diagnostics.skip(Diagnostics.NETHER_PORTAL, "disabled");
            return;
        }
        if (rects == 0) {
            Diagnostics.skip(Diagnostics.NETHER_PORTAL, "no lit portal nearby");
            return;
        }
        SkyProgram program = SkyProgram.NETHER_PORTAL;
        Camera camera = event.getCamera();
        if (!on() || CameraFluid.of(camera) != FogType.NONE) {
            Diagnostics.skip(Diagnostics.NETHER_PORTAL, "fog handler off, shader pack, camera in a fluid or shader failed");
            return;
        }
        upload();
        Vec3 eye = camera.getPosition();
        MATRIX.set(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
        MATRIX.get(MATRIX_UPLOAD);
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        program.begin();
        SkyProgram.texture(0, data);
        GL20.glUniformMatrix4fv(program.uniform(0), false, MATRIX_UPLOAD);
        GL20.glUniform4f(program.uniform(1), (float) (anchorX - eye.x), (float) (anchorY - eye.y), (float) (anchorZ - eye.z), 0F);
        GL20.glUniform4f(program.uniform(2), (Util.getMillis() % 3_600_000L) / 1000F, rects, 0F, 0F);
        GL20.glUniform3f(program.uniform(3), -left.x(), -left.y(), -left.z());
        GL20.glUniform3f(program.uniform(4), up.x(), up.y(), up.z());
        program.draw(rects * QUADS * 6);
        program.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        Diagnostics.mark(Diagnostics.NETHER_PORTAL, Diagnostics.detail() ? String.format("%d portals", rects) : null);
    }

    private static void upload() {
        if (data == 0) {
            data = GlStateManager._genTexture();
            SkyProgram.texture(0, data);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            SkyHeights.unpack();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, MAX_RECTS, 2, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            dirty = true;
        }
        if (!dirty) return;
        SkyProgram.texture(0, data);
        SkyHeights.unpack();
        UPLOAD.clear();
        UPLOAD.put(ROW_A).flip();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, MAX_RECTS, 1, GL11.GL_RGBA, GL11.GL_FLOAT, UPLOAD);
        UPLOAD.clear();
        UPLOAD.put(ROW_B).flip();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 1, MAX_RECTS, 1, GL11.GL_RGBA, GL11.GL_FLOAT, UPLOAD);
        SkyProgram.texture(0, 0);
        dirty = false;
    }

    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class PortalProviders {
        private PortalProviders() {}

        @SubscribeEvent
        public static void onRegisterPortalProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ParticleTypes.PORTAL, sprites -> {
                PortalParticle.Provider vanilla = new PortalParticle.Provider(sprites);
                return (type, level, x, y, z, vx, vy, vz) -> {
                    if (owns(x, y, z)) {
                        Diagnostics.once("vanilla portal particles next to a decorated Nether portal are replaced by our motes");
                        return null;
                    }
                    return vanilla.createParticle(type, level, x, y, z, vx, vy, vz);
                };
            });
            Diagnostics.once("portal particles are routed through our provider, vanilla specks are dropped next to decorated portals");
        }
    }
}
