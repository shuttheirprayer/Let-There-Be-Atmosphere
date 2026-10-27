package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.CaptureHook;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
public final class IndoorSunbeams {
    private IndoorSunbeams() {}

    private static final int SIZE = 32;
    private static final int SIDE = 48;
    private static final int COLUMNS = 128;
    private static final int LIT_WIDTH = 4096;
    private static final int SLICES_PER_TICK = 4;
    private static final int MOTES = 2048;
    private static final int REFRESH_TICKS = 200;
    private static final float REACH = 16F;
    private static final float OPEN = -1.0E4F;
    private static final float MAX_SLOPE = 7F;

    private static final int[] OCC = new int[SIZE * SIZE];
    private static final int[] OCC_BUILD = new int[SIZE * SIZE];
    private static final float[] HEIGHTS = new float[COLUMNS * SIZE * 2];
    private static final float[] HEIGHTS_BUILD = new float[COLUMNS * SIZE * 2];
    private static final float[] NEAR = new float[4];
    private static final IntBuffer OCC_UPLOAD = BufferUtils.createIntBuffer(SIZE * SIZE);
    private static final FloatBuffer HEIGHT_UPLOAD = BufferUtils.createFloatBuffer(COLUMNS * SIZE * 2);
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final float[] SLOPE = new float[4];

    private static ClientLevel level;
    private static boolean live;
    private static int liveX, liveY, liveZ;
    private static int buildX, buildY, buildZ;
    private static int buildSlice = -1;
    private static int age;
    private static boolean dirty;
    private static boolean litStale = true;
    private static long litTick = Long.MIN_VALUE;
    private static int occTexture;
    private static int heightTexture;
    private static TextureTarget litMap;
    private static TextureTarget raw;
    private static TextureTarget beam;
    private static float indoor;
    private static long indoorAt;
    private static double driftX, driftY, driftZ;
    private static long driftAt;

    public static void onIndoorSunbeamsTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        if (!FxConfig.indoorSunbeams || current == null || mc.player == null || !current.dimensionType().hasSkyLight()) return;
        if (current != level) {
            level = current;
            live = false;
            buildSlice = -1;
        }
        float sunY = Mth.cos(current.getTimeOfDay(1F) * Mth.TWO_PI);
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        if (sunY < 0.1F || current.getRainLevel(1F) > 0.95F || !covered(current, eye)) return;
        int ox = (Mth.floor(eye.x) >> 2 << 2) - SIZE / 2;
        int oy = (Mth.floor(eye.y) >> 2 << 2) - 12;
        int oz = (Mth.floor(eye.z) >> 2 << 2) - SIZE / 2;
        if (buildSlice < 0 && (!live || ox != liveX || oy != liveY || oz != liveZ || ++age > REFRESH_TICKS)) {
            buildX = ox;
            buildY = oy;
            buildZ = oz;
            buildSlice = 0;
            age = 0;
        }
        if (buildSlice < 0) return;
        for (int i = 0; i < SLICES_PER_TICK && buildSlice < SIZE; i++) slice(OCC_BUILD, HEIGHTS_BUILD, buildX, buildY, buildZ, buildSlice++);
        if (buildSlice < SIZE) return;
        cover(HEIGHTS_BUILD);
        System.arraycopy(OCC_BUILD, 0, OCC, 0, OCC.length);
        System.arraycopy(HEIGHTS_BUILD, 0, HEIGHTS, 0, HEIGHTS.length);
        liveX = buildX;
        liveY = buildY;
        liveZ = buildZ;
        live = true;
        dirty = true;
        buildSlice = -1;
    }

    public static void changed(ClientLevel current, BlockPos pos) {
        if (current != level) return;
        if (live && patch(OCC, HEIGHTS, liveX, liveY, liveZ, pos)) {
            cover(HEIGHTS);
            dirty = true;
        }
        if (buildSlice >= 0 && pos.getZ() - buildZ < buildSlice) patch(OCC_BUILD, HEIGHTS_BUILD, buildX, buildY, buildZ, pos);
    }

    private static boolean patch(int[] occ, float[] heights, int ox, int oy, int oz, BlockPos pos) {
        int z = pos.getZ() - oz;
        int c = pos.getX() - ox + SIDE;
        if (z < 0 || z >= SIZE || c < 0 || c >= COLUMNS) return false;
        heights[(z * COLUMNS + c) * 2] = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - oy;
        int x = c - SIDE;
        if (x >= 0 && x < SIZE) occ[z * SIZE + x] = column(level, pos.getX(), oy, pos.getZ());
        return true;
    }

    private static void slice(int[] occ, float[] heights, int ox, int oy, int oz, int z) {
        int wz = oz + z;
        for (int c = 0; c < COLUMNS; c++) {
            heights[(z * COLUMNS + c) * 2] = level.getHeight(Heightmap.Types.MOTION_BLOCKING, ox - SIDE + c, wz) - oy;
        }
        for (int x = 0; x < SIZE; x++) occ[z * SIZE + x] = column(level, ox + x, oy, wz);
    }

    private static int column(ClientLevel current, int wx, int oy, int wz) {
        LevelChunk chunk = current.getChunkSource().getChunk(wx >> 4, wz >> 4, false);
        if (chunk == null) return 0;
        int bits = 0;
        LevelChunkSection section = null;
        int sectionY = Integer.MIN_VALUE;
        for (int y = 0; y < SIZE; y++) {
            int wy = oy + y;
            if (wy < chunk.getMinBuildHeight() || wy >= chunk.getMaxBuildHeight()) continue;
            if (wy >> 4 != sectionY) {
                sectionY = wy >> 4;
                section = chunk.getSection(chunk.getSectionIndex(wy));
            }
            if (section.hasOnlyAir()) continue;
            if (solid(section.getBlockState(wx & 15, wy & 15, wz & 15))) bits |= 1 << y;
        }
        return bits;
    }

    private static boolean solid(BlockState state) {
        return state.canOcclude() || state.is(BlockTags.LEAVES) || !state.getFluidState().isEmpty();
    }

    private static void cover(float[] heights) {
        for (int z = 0; z < SIZE; z++) {
            for (int c = 0; c < COLUMNS; c++) {
                int o = (z * COLUMNS + c) * 2;
                float top = heights[o];
                if (c < SIDE || c >= SIDE + SIZE) {
                    heights[o + 1] = top;
                    continue;
                }
                NEAR[0] = c >= 2 ? heights[o - 4] : OPEN;
                NEAR[1] = c < COLUMNS - 2 ? heights[o + 4] : OPEN;
                NEAR[2] = z >= 2 ? heights[o - COLUMNS * 4] : OPEN;
                NEAR[3] = z < SIZE - 2 ? heights[o + COLUMNS * 4] : OPEN;
                Arrays.sort(NEAR);
                heights[o + 1] = Math.max(top, NEAR[1]);
            }
        }
    }

    private static boolean covered(ClientLevel current, Vec3 eye) {
        return eye.y < current.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(eye.x), Mth.floor(eye.z));
    }

    public static void onRenderIndoorSunbeamsStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (!FxConfig.indoorSunbeams) {
            Diagnostics.skip(Diagnostics.INDOOR_SUNBEAMS, "disabled");
            return;
        }
        ClientLevel current = SkyGate.sky(event, Diagnostics.INDOOR_SUNBEAMS, true);
        if (current == null) return;
        if (!ViewCapture.fresh) {
            Diagnostics.skip(Diagnostics.INDOOR_SUNBEAMS, "no view captured this frame");
            return;
        }
        if (!DynamicFogHandler.active() || ColorGrade.shaderPackActive()) {
            Diagnostics.skip(Diagnostics.INDOOR_SUNBEAMS, "fog handler off or a shader pack owns the image");
            return;
        }
        SkyProgram litProgram = SkyProgram.SUNBEAM_LIT;
        SkyProgram airProgram = SkyProgram.SUNBEAM_AIR;
        SkyProgram mixProgram = SkyProgram.SUNBEAM_MIX;
        SkyProgram dustProgram = SkyProgram.SUNBEAM_DUST;
        SkyProgram soften = SkyProgram.NETHER_SOFTEN;
        if (!litProgram.ready() || !airProgram.ready() || !mixProgram.ready() || !dustProgram.ready() || !soften.ready()) {
            Diagnostics.skip(Diagnostics.INDOOR_SUNBEAMS, "shader failed to load, see the log");
            return;
        }
        float partialTick = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        float angle = current.getTimeOfDay(partialTick) * Mth.TWO_PI;
        float sunX = -Mth.sin(angle);
        float sunY = Mth.cos(angle);
        float clear = 1F - current.getRainLevel(partialTick);
        long now = Util.getNanos();
        float dt = indoorAt == 0L ? 1F : Math.min((now - indoorAt) / 1.0E9F, 1F);
        indoorAt = now;
        indoor += ((covered(current, eye) ? 1F : 0F) - indoor) * (1F - (float) Math.exp(-dt * 2.5F));
        float strength = SkyGlow.smooth(0.14F, 0.3F, sunY) * clear * clear * indoor;
        if (strength < 0.004F) {
            Diagnostics.skip(Diagnostics.INDOOR_SUNBEAMS, "night, rain or open sky");
            return;
        }
        float cx = (float) (eye.x - liveX), cy = (float) (eye.y - liveY), cz = (float) (eye.z - liveZ);
        if (!live || current != level || cx < 4F || cx > 28F || cy < 4F || cy > 28F || cz < 4F || cz > 28F) {
            Diagnostics.skip(Diagnostics.INDOOR_SUNBEAMS, "still reading the blocks around you");
            return;
        }

        CaptureHook.probeBegin("sunbeams");
        upload();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        float k = Mth.clamp(sunX / Math.max(sunY, 0.01F), -MAX_SLOPE, MAX_SLOPE);
        long tick = current.getGameTime();
        if (litStale || tick != litTick) {
            litTick = tick;
            litStale = false;
            buildLit(litProgram, k);
        }

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int width = Math.max(main.width / 4, 1);
        int height = Math.max(main.height / 4, 1);
        raw = SunPass.fit(raw, width, height, true);
        beam = SunPass.fit(beam, width, height, true);

        airProgram.begin();
        SkyProgram.texture(2, heightTexture);
        SkyProgram.texture(1, litMap.getColorTextureId());
        SkyProgram.texture(0, main.getDepthTextureId());
        ViewCapture.INVERSE.get(MATRIX);
        GL20.glUniformMatrix4fv(airProgram.uniform(0), false, MATRIX);
        GL20.glUniform4f(airProgram.uniform(1), SLOPE[0], SLOPE[1], SLOPE[2], SLOPE[3]);
        GL20.glUniform4f(airProgram.uniform(2), cx, cy, cz, REACH);
        GL20.glUniform4f(airProgram.uniform(3), sunX, sunY, 0F, 0.7F);
        raw.bindWrite(true);
        airProgram.draw(3);
        airProgram.end();
        soften.begin();
        SkyProgram.texture(0, raw.getColorTextureId());
        beam.bindWrite(true);
        soften.draw(3);
        soften.end();

        float warm = SkyGlow.smooth(0.1F, 0.7F, sunY);
        float red = 1F, green = Mth.lerp(warm, 0.6F, 0.9F), blue = Mth.lerp(warm, 0.3F, 0.72F);
        main.bindWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        mixProgram.begin();
        SkyProgram.texture(3, heightTexture);
        SkyProgram.texture(2, litMap.getColorTextureId());
        SkyProgram.texture(1, main.getDepthTextureId());
        SkyProgram.texture(0, beam.getColorTextureId());
        GL20.glUniformMatrix4fv(mixProgram.uniform(0), false, MATRIX);
        GL20.glUniform4f(mixProgram.uniform(1), SLOPE[0], SLOPE[1], SLOPE[2], SLOPE[3]);
        GL20.glUniform4f(mixProgram.uniform(2), cx, cy, cz, REACH);
        GL20.glUniform4f(mixProgram.uniform(3), red, green, blue, 0.4F * strength);
        GL20.glUniform4f(mixProgram.uniform(4), 0.16F * strength, 0F, 0F, 0F);
        mixProgram.draw(3);
        mixProgram.end();

        drift(now);
        float tx = (float) wrap(eye.x - driftX, 12.0), ty = (float) wrap(eye.y - driftY, 7.0), tz = (float) wrap(eye.z - driftZ, 12.0);
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        dustProgram.begin();
        SkyProgram.texture(1, heightTexture);
        SkyProgram.texture(0, litMap.getColorTextureId());
        ViewCapture.VIEW_PROJECTION.get(MATRIX);
        GL20.glUniformMatrix4fv(dustProgram.uniform(0), false, MATRIX);
        GL20.glUniform4f(dustProgram.uniform(1), SLOPE[0], SLOPE[1], SLOPE[2], SLOPE[3]);
        GL20.glUniform4f(dustProgram.uniform(2), cx, cy, cz, REACH);
        GL20.glUniform4f(dustProgram.uniform(3), tx, ty, tz, (Util.getMillis() % 3_600_000L) / 1000F);
        GL20.glUniform3f(dustProgram.uniform(4), -left.x(), -left.y(), -left.z());
        GL20.glUniform3f(dustProgram.uniform(5), up.x(), up.y(), up.z());
        GL20.glUniform4f(dustProgram.uniform(6), red, Math.min(green + 0.05F, 1F), Math.min(blue + 0.08F, 1F), 0.9F * strength);
        dustProgram.draw(MOTES * 6);
        dustProgram.end();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        CaptureHook.probeEnd("sunbeams");
        Diagnostics.mark(Diagnostics.INDOOR_SUNBEAMS, Diagnostics.detail()
                ? String.format("strength %.2f, indoors %.2f, slope %.2f", strength, indoor, k) : null);
    }

    private static void buildLit(SkyProgram program, float k) {
        if (litMap == null) {
            litMap = new TextureTarget(LIT_WIDTH, SIZE, false, Minecraft.ON_OSX);
            litMap.setFilterMode(GL11.GL_NEAREST);
            GlStateManager._bindTexture(litMap.getColorTextureId());
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, LIT_WIDTH, SIZE, 0, GL11.GL_RED, GL11.GL_FLOAT, null);
        }
        int width = Math.min(Mth.ceil((SIZE + SIZE * Math.abs(k)) * 16F), LIT_WIDTH);
        SLOPE[0] = k;
        SLOPE[1] = Math.min(0F, -SIZE * k);
        SLOPE[2] = 16F;
        SLOPE[3] = width;
        program.begin();
        SkyProgram.texture(1, heightTexture);
        SkyProgram.texture(0, occTexture);
        GL20.glUniform4f(program.uniform(0), SLOPE[0], SLOPE[1], SLOPE[2], SLOPE[3]);
        litMap.bindWrite(false);
        RenderSystem.viewport(0, 0, width, SIZE);
        program.draw(3);
        program.end();
    }

    private static void upload() {
        if (occTexture == 0) {
            occTexture = texture();
            heightTexture = texture();
            dirty = true;
        }
        if (!dirty) return;
        dirty = false;
        litStale = true;
        SkyHeights.unpack();
        OCC_UPLOAD.clear();
        OCC_UPLOAD.put(OCC).flip();
        GlStateManager._bindTexture(occTexture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32UI, SIZE, SIZE, 0, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT, OCC_UPLOAD);
        HEIGHT_UPLOAD.clear();
        HEIGHT_UPLOAD.put(HEIGHTS).flip();
        GlStateManager._bindTexture(heightTexture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG32F, COLUMNS, SIZE, 0, GL30.GL_RG, GL11.GL_FLOAT, HEIGHT_UPLOAD);
    }

    private static int texture() {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        return id;
    }

    private static void drift(long now) {
        float dt = driftAt == 0L ? 0F : Mth.clamp((now - driftAt) / 1.0E9F, 0F, 0.1F);
        driftAt = now;
        driftX += 0.05 * dt;
        driftY -= 0.02 * dt;
        driftZ += 0.03 * dt;
    }

    private static double wrap(double value, double box) {
        return value - Math.floor(value / box) * box;
    }
}
