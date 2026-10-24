package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class StoryClouds {
    private record Shape(int signature, int anchorX, int anchorZ, int radius, float cell, float reach, float thickness,
                         float alpha, float bottomAlpha, float topAlpha, boolean shaded, float fx, float fz,
                         float variation, float detail, float threshold, boolean second, float secondOffset,
                         float secondThreshold) {
    }

    private record Mesh(Shape shape, int generation, BufferBuilder.RenderedBuffer rendered, ByteBuffer cover) {
    }

    private static final ValueNoise NOISE = new ValueNoise(7717L);
    private static final float DRIFT_PER_TICK = 0.36F;
    private static final int ANCHOR_SLACK = 8;
    private static final int MAX_RADIUS_CELLS = 48;
    private static final float FADE_START = 0.55F;
    private static final float SECOND_SEED = 311F;
    private static final float[] OPEN = {0.5F, 0.5F, 0.5F, 0.5F};
    private static final float[] LAYERS = new float[4];
    private static final float BOTTOM = 0F, TOP = 0.2F, WEST = 0.4F, EAST = 0.6F, NORTH = 0.8F, SOUTH = 1F;

    private static ShaderInstance shader;
    private static ShaderInstance farShader;
    private static BufferBuilder builder;
    private static float[] quantiles;
    private static float quantilesDetail = Float.NaN;

    private static VertexBuffer buffer;
    private static Shape drawn;
    private static final ExecutorService MESHER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LTBA cloud mesher");
        thread.setDaemon(true);
        return thread;
    });
    private static CompletableFuture<Mesh> pending;
    private static int generation;
    private static boolean forced = true;
    private static boolean failed;
    private static int shadowTexture;
    private static Shape shadowShape;

    private StoryClouds() {}

    public static boolean shouldRender(ClientLevel level) {
        return DynamicFogHandler.active()
                && StoryCloudConfig.enabled
                && shader != null
                && level != null
                && !Float.isNaN(level.effects().getCloudHeight());
    }

    public static void invalidate() {
        forced = true;
        failed = false;
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            Minecraft.getInstance().execute(StoryClouds::discard);
        }
    }

    private static void discard() {
        generation++;
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
        drawn = null;
        forced = true;
    }

    public static void render(PoseStack pose, Matrix4f projection, float partialTick, double camX, double camY, double camZ) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        double cell = StoryCloudConfig.cellSize;
        double shift = Wind.time() * 20.0 * DRIFT_PER_TICK * StoryCloudConfig.speed;
        int cellX = Mth.floor((camX + shift) / cell);
        int cellZ = Mth.floor(camZ / cell);

        collect();
        int signature = StoryCloudConfig.signature() * 31 + Float.hashCode(DynamicFogHandler.cloudFogEnd());
        if (pending == null && !failed && (forced || drawn == null || drawn.signature() != signature
                || Math.abs(cellX - drawn.anchorX()) > ANCHOR_SLACK || Math.abs(cellZ - drawn.anchorZ()) > ANCHOR_SLACK)) {
            forced = false;
            Shape shape = shape(signature, cellX, cellZ, (float) cell);
            int built = generation;
            pending = CompletableFuture.supplyAsync(() -> mesh(shape, built), MESHER);
        }
        if (buffer == null || drawn == null) {
            Diagnostics.skip(Diagnostics.STORY, pending != null ? "first mesh still building" : "mesh came out empty");
            return;
        }

        Vec3 cloud = level.getCloudColor(partialTick);
        float r = (float) cloud.x;
        float g = (float) cloud.y;
        float b = (float) cloud.z;
        int tint = StoryCloudConfig.tintHex;
        if (tint >= 0) {
            float t = Mth.clamp((float) StoryCloudConfig.tintStrength, 0F, 1F);
            r = Mth.lerp(t, r, ((tint >> 16) & 0xFF) / 255F);
            g = Mth.lerp(t, g, ((tint >> 8) & 0xFF) / 255F);
            b = Mth.lerp(t, b, (tint & 0xFF) / 255F);
        }
        float baseY = level.effects().getCloudHeight() + (float) StoryCloudConfig.heightOffset;
        float reach = drawn.reach();

        shader.safeGetUniform("CloudOffset").set((float) (drawn.anchorX() * cell - shift - camX), (float) (baseY - camY),
                (float) (drawn.anchorZ() * cell - camZ));
        shader.safeGetUniform("FadeRange").set(reach * FADE_START, reach);
        shader.safeGetUniform("FogTint").set(Mth.lerp(WeatherStorms.fogK(), Mth.clamp((float) StoryCloudConfig.fogFade, 0F, 1F), 1F));
        float angle = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        float sunX = -Mth.sin(angle);
        float sunY = Mth.cos(angle);
        boolean day = sunY > -0.1F;
        float light = day ? SkyGlow.smooth(-0.1F, 0.25F, sunY) : 0.25F * SkyGlow.smooth(0.1F, 0.4F, -sunY);
        float warmth = day ? (1F - SkyGlow.smooth(0.08F, 0.4F, sunY)) * SkyGlow.smooth(-0.12F, 0.02F, sunY) : 0F;
        shader.safeGetUniform("SunDir").set(day ? sunX : -sunX, day ? sunY : -sunY, 0F);
        Vec3 sky = level.getSkyColor(new Vec3(camX, camY, camZ), partialTick);
        shader.safeGetUniform("SkyTint").set((float) sky.x, (float) sky.y, (float) sky.z, VividSky.on() ? VividSky.strength() * 0.85F : 0F);
        shader.safeGetUniform("CellSize").set((float) cell);
        shader.safeGetUniform("Light").set(StoryCloudConfig.lighting ? 1F : 0F, light * (1F - 0.7F * level.getRainLevel(partialTick)), warmth, drawn.shaded() ? 1F : 0.4F);
        boolean blur = StoryCloudConfig.softEdges > 0.0 && CloudBlur.ready()
                && GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) == Minecraft.getInstance().getMainRenderTarget().frameBufferId;
        LAYERS[0] = baseY - (float) camY;
        LAYERS[1] = drawn.thickness();
        LAYERS[2] = LAYERS[0] + drawn.secondOffset();
        LAYERS[3] = drawn.second() ? drawn.thickness() : 0F;
        if (StoryCloudConfig.pastel) {
            float rain = level.getRainLevel(partialTick);
            float bright = SkyGlow.smooth(-0.22F, 0.02F, sunY) * (1F - 0.8F * rain);
            float night = (1F - SkyGlow.smooth(-0.3F, -0.05F, sunY)) * (1F - rain);
            float peak = Math.max(r, Math.max(g, b));
            float hr = peak > 0.001F ? r / peak : 1F, hg = peak > 0.001F ? g / peak : 1F, hb = peak > 0.001F ? b / peak : 1F;
            float moon = 0.1F + 0.12F * level.getMoonBrightness();
            r = Mth.lerp(bright, Mth.lerp(night, r, moon * 0.8F), hr);
            g = Mth.lerp(bright, Mth.lerp(night, g, moon * 0.9F), hg * Mth.lerp(warmth, 1F, 0.86F));
            b = Mth.lerp(bright, Mth.lerp(night, b, moon * 1.35F), hb * Mth.lerp(warmth, 1F, 0.8F));
            shader.safeGetUniform("Pastel").set(1F, Mth.lerp(warmth, 0.68F, 0.5F) * Mth.lerp(night, 1F, 0.6F), 0.06F, blur ? 1F : 0F);
        } else {
            shader.safeGetUniform("Pastel").set(0F, 1F, 0F, blur ? 1F : 0F);
        }

        draw(pose.last().pose(), projection, r, g, b, blur);
        Diagnostics.mark(Diagnostics.STORY, Diagnostics.detail()
                ? String.format("reach %.0f, %d cells, second layer %s", reach, drawn.radius() * 2 + 1,
                        drawn.second() ? "on" : "off") : null);
    }

    private static void draw(Matrix4f view, Matrix4f projection, float r, float g, float b, boolean blur) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (blur) CloudBlur.begin(main);
        shader.setSampler("DepthSampler", main.getDepthTextureId());
        RenderSystem.setShaderColor(r, g, b, 1F);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(view);
        if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(projection);
        if (shader.COLOR_MODULATOR != null) shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        if (shader.FOG_COLOR != null) shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        shader.safeGetUniform("DepthPass").set(1F);
        shader.apply();
        buffer.bind();
        RenderSystem.colorMask(false, false, false, false);
        buffer.draw();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(false);
        shader.safeGetUniform("DepthPass").set(0F);
        shader.apply();
        if (blur) RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        buffer.draw();
        RenderSystem.depthMask(true);
        shader.clear();
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        if (blur) CloudBlur.end(main, view, projection, LAYERS, (float) StoryCloudConfig.softEdges);
    }

    private static void collect() {
        if (pending == null || !pending.isDone()) {
            return;
        }
        Mesh mesh;
        try {
            mesh = pending.join();
        } catch (RuntimeException e) {
            pending = null;
            failed = true;
            builder = null;
            Diagnostics.LOG.error("[atmosphere] storybook cloud mesh failed to build", e);
            return;
        }
        pending = null;
        if (mesh.generation() != generation) {
            if (mesh.rendered() != null) {
                mesh.rendered().release();
            }
            return;
        }
        drawn = mesh.shape();
        uploadShadow(mesh);
        if (mesh.rendered() == null) {
            if (buffer != null) {
                buffer.close();
                buffer = null;
            }
            return;
        }
        if (buffer == null) {
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        buffer.bind();
        buffer.upload(mesh.rendered());
        VertexBuffer.unbind();
    }

    private static Shape shape(int signature, int cellX, int cellZ, float cell) {
        int radius = Mth.clamp(Mth.ceil(DynamicFogHandler.cloudFogEnd() / cell) + ANCHOR_SLACK + 1, ANCHOR_SLACK + 4, MAX_RADIUS_CELLS);
        float reach = Math.min(DynamicFogHandler.cloudFogEnd(), (radius - ANCHOR_SLACK - 1) * cell);
        float alpha = (float) StoryCloudConfig.alpha;
        float detail = Mth.clamp((float) StoryCloudConfig.detail, 0F, 0.9F);
        return new Shape(signature, cellX, cellZ, radius, cell, reach, (float) StoryCloudConfig.thickness, alpha,
                alpha * (1F - (float) StoryCloudConfig.bottomFade), alpha * (1F - (float) StoryCloudConfig.topFade),
                StoryCloudConfig.shading, 0.16F / (float) StoryCloudConfig.widthScale,
                0.16F / (float) StoryCloudConfig.lengthScale, Mth.clamp((float) StoryCloudConfig.heightVariation, 0F, 1F),
                detail, threshold((float) StoryCloudConfig.coverage, detail), StoryCloudConfig.secondLayer,
                (float) StoryCloudConfig.secondOffset, threshold((float) StoryCloudConfig.secondCoverage, detail));
    }

    private static Mesh mesh(Shape shape, int built) {
        int span = shape.radius() * 2 + 1;
        float[] grid = occupancy(shape, span, shape.threshold(), 0F);
        float[] upper = shape.second() ? occupancy(shape, span, shape.secondThreshold(), SECOND_SEED) : null;
        ByteBuffer cover = BufferUtils.createByteBuffer(span * span);
        for (float height : grid) cover.put(height > 0F ? (byte) 255 : (byte) 0);
        cover.flip();
        return new Mesh(shape, built, build(shape, grid, upper), cover);
    }

    private static void uploadShadow(Mesh mesh) {
        int span = mesh.shape().radius() * 2 + 1;
        if (shadowTexture == 0) {
            shadowTexture = GlStateManager._genTexture();
            GlStateManager._bindTexture(shadowTexture);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER);
            GL11.glTexParameterfv(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_BORDER_COLOR, new float[]{0F, 0F, 0F, 0F});
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL33.GL_TEXTURE_SWIZZLE_A, GL11.GL_RED);
        }
        GlStateManager._bindTexture(shadowTexture);
        SkyHeights.unpack();
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, span, span, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, mesh.cover());
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        shadowShape = mesh.shape();
    }

    static int shadowTexture() {
        return shadowShape == null ? 0 : shadowTexture;
    }

    static boolean shadowFrame(ClientLevel level, float partialTick, double camX, double camY, double camZ, float[] out) {
        Shape shape = shadowShape;
        if (shape == null || shadowTexture == 0) return false;
        double cell = shape.cell();
        double shift = Wind.time() * 20.0 * DRIFT_PER_TICK * StoryCloudConfig.speed;
        double originX = (shape.anchorX() - shape.radius()) * cell - shift;
        double originZ = (shape.anchorZ() - shape.radius()) * cell;
        out[0] = (float) ((camX - originX) / cell);
        out[1] = (float) ((camZ - originZ) / cell);
        out[2] = level.effects().getCloudHeight() + (float) StoryCloudConfig.heightOffset - (float) camY;
        out[3] = (float) cell;
        out[4] = shape.radius() * 2 + 1;
        return true;
    }

    private static BufferBuilder.RenderedBuffer build(Shape shape, float[] grid, float[] upper) {
        int span = shape.radius() * 2 + 1;
        float limit = (shape.radius() - 0.5F) * shape.cell();
        BufferBuilder out = builder;
        if (out == null) {
            out = builder = new BufferBuilder(1 << 20);
        }
        out.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        emit(out, shape, grid, span, 0F, limit);
        if (upper != null) {
            emit(out, shape, upper, span, shape.secondOffset(), limit);
        }
        return out.endOrDiscardIfEmpty();
    }

    private static float[] occupancy(Shape shape, int span, float threshold, float seed) {
        float[] grid = new float[span * span];
        int radius = shape.radius();
        for (int z = 0; z < span; z++) {
            float wz = (shape.anchorZ() + z - radius) * shape.fz() + seed;
            for (int x = 0; x < span; x++) {
                float wx = (shape.anchorX() + x - radius) * shape.fx() + seed;
                if (field(wx, wz, shape.detail()) <= threshold) {
                    continue;
                }
                float tall = shape.variation() <= 0F ? 1F
                        : Mth.lerp(NOISE.at(wx * 1.7F + SECOND_SEED, wz * 1.7F + SECOND_SEED), 1F - shape.variation(), 1F);
                grid[z * span + x] = Math.max(0.05F, tall);
            }
        }
        return grid;
    }

    private static float field(float x, float z, float detail) {
        int cx = Mth.floor(x), cz = Mth.floor(z);
        float best = 0F;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int gx = cx + dx, gz = cz + dz;
                int h = blobHash(gx, gz);
                float px = gx + 0.15F + 0.7F * ((h & 0xFF) / 255F);
                float pz = gz + 0.15F + 0.7F * (((h >>> 8) & 0xFF) / 255F);
                float radius = 0.3F + 0.5F * (((h >>> 16) & 0xFF) / 255F);
                float ddx = x - px, ddz = z - pz;
                best = Math.max(best, 1F - Mth.sqrt(ddx * ddx + ddz * ddz) / radius);
            }
        }
        return best * (1F - detail * 0.5F) + NOISE.at(x * 2.4F, z * 2.4F) * detail * 0.5F;
    }

    private static int blobHash(int x, int z) {
        int h = x * 0x27D4EB2D ^ z * 0x165667B1;
        h = (h ^ (h >>> 15)) * 0x85EBCA6B;
        h = (h ^ (h >>> 13)) * 0xC2B2AE35;
        return h ^ (h >>> 16);
    }

    private static float threshold(float coverage, float detail) {
        if (coverage <= 0F) {
            return Float.MAX_VALUE;
        }
        if (coverage >= 1F) {
            return -Float.MAX_VALUE;
        }
        float[] table = quantiles(detail);
        return table[Mth.clamp(Math.round((1F - coverage) * (table.length - 1)), 0, table.length - 1)];
    }

    private static float[] quantiles(float detail) {
        if (quantiles != null && detail == quantilesDetail) {
            return quantiles;
        }
        int side = 128;
        float[] values = new float[side * side];
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                values[z * side + x] = field(x * 0.16F, z * 0.16F, detail);
            }
        }
        Arrays.sort(values);
        quantiles = values;
        quantilesDetail = detail;
        return values;
    }

    private static void emit(BufferBuilder out, Shape shape, float[] grid, int span, float y0, float limit) {
        int radius = shape.radius();
        float cell = shape.cell();
        float thickness = shape.thickness();
        float limitSq = limit * limit;
        for (int z = 0; z < span; z++) {
            for (int x = 0; x < span; x++) {
                float height = grid[z * span + x];
                if (height <= 0F) {
                    continue;
                }
                float x0 = (x - radius) * cell;
                float z0 = (z - radius) * cell;
                float centerX = x0 + cell * 0.5F;
                float centerZ = z0 + cell * 0.5F;
                if (centerX * centerX + centerZ * centerZ > limitSq) {
                    continue;
                }
                float x1 = x0 + cell;
                float z1 = z0 + cell;
                float y1 = y0 + thickness * height;
                int edges = (empty(grid, span, x - 1, z) ? 1 : 0) | (empty(grid, span, x + 1, z) ? 2 : 0)
                        | (empty(grid, span, x, z - 1) ? 4 : 0) | (empty(grid, span, x, z + 1) ? 8 : 0);
                OPEN[0] = OPEN[1] = OPEN[2] = OPEN[3] = edges / 15F;
                quad(out, TOP, shape.topAlpha(), y0, thickness, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
                quad(out, BOTTOM, shape.bottomAlpha(), y0, thickness, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
                OPEN[0] = OPEN[1] = OPEN[2] = OPEN[3] = 0F;
                float west = sideBase(grid, span, x - 1, z, y0, thickness);
                if (west < y1) {
                    quad(out, WEST, shape.alpha(), y0, thickness, x0, west, z0, x0, west, z1, x0, y1, z1, x0, y1, z0);
                }
                float east = sideBase(grid, span, x + 1, z, y0, thickness);
                if (east < y1) {
                    quad(out, EAST, shape.alpha(), y0, thickness, x1, east, z1, x1, east, z0, x1, y1, z0, x1, y1, z1);
                }
                float north = sideBase(grid, span, x, z - 1, y0, thickness);
                if (north < y1) {
                    quad(out, NORTH, shape.alpha(), y0, thickness, x1, north, z0, x0, north, z0, x0, y1, z0, x1, y1, z0);
                }
                float south = sideBase(grid, span, x, z + 1, y0, thickness);
                if (south < y1) {
                    quad(out, SOUTH, shape.alpha(), y0, thickness, x0, south, z1, x1, south, z1, x1, y1, z1, x0, y1, z1);
                }
            }
        }
    }

    private static boolean empty(float[] grid, int span, int x, int z) {
        return x < 0 || z < 0 || x >= span || z >= span || grid[z * span + x] <= 0F;
    }

    private static float sideBase(float[] grid, int span, int x, int z, float y0, float thickness) {
        if (x < 0 || z < 0 || x >= span || z >= span) {
            return y0;
        }
        float neighbour = grid[z * span + x];
        return neighbour <= 0F ? y0 : y0 + thickness * neighbour;
    }

    public static ShaderInstance farCloudShader() {
        return farShader;
    }

    private static void quad(BufferBuilder out, float face, float alpha, float y0, float thickness, float ax, float ay, float az,
                             float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
        float a = Mth.clamp(alpha, 0F, 1F);
        float scale = 1F / Math.max(thickness, 0.001F);
        out.vertex(ax, ay, az).color(face, Mth.clamp((ay - y0) * scale, 0F, 1F), OPEN[0], a).endVertex();
        out.vertex(bx, by, bz).color(face, Mth.clamp((by - y0) * scale, 0F, 1F), OPEN[1], a).endVertex();
        out.vertex(cx, cy, cz).color(face, Mth.clamp((cy - y0) * scale, 0F, 1F), OPEN[2], a).endVertex();
        out.vertex(dx, dy, dz).color(face, Mth.clamp((dy - y0) * scale, 0F, 1F), OPEN[3], a).endVertex();
    }

    @Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class CloudShaders {
        private CloudShaders() {}

        @SubscribeEvent
        public static void onRegisterCloudShaders(RegisterShadersEvent event) throws IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(Let_there_be_atmosphere.MODID, "story_clouds"), DefaultVertexFormat.POSITION_COLOR),
                    loaded -> shader = loaded);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(Let_there_be_atmosphere.MODID, "clouds_far"), DefaultVertexFormat.POSITION_COLOR),
                    loaded -> farShader = loaded);
        }
    }
}
