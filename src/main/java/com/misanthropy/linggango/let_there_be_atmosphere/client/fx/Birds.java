package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeFx;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class Birds {
    private Birds() {}

    static final int FINCH = 0;
    static final int SWALLOW = 1;
    static final int GOOSE = 2;
    static final int GULL = 3;
    static final int ROBIN = 4;

    private static final int WOODS = 0;
    private static final int OPEN = 1;
    private static final int SHORE = 2;

    private static final int SLOTS = 64;
    private static final int ROOSTS = 4;
    private static final int PATH = 100;
    private static final double REACH = 1024.0;
    private static final float REBASE = 1800F;
    private static final ResourceLocation SHEET = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/birds.png");

    private static final float[][] ROW = new float[4][SLOTS * 4];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(SLOTS * 4);
    private static final List<double[]> ROOST = new ArrayList<>();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final FloatBuffer TRANSFORM = BufferUtils.createFloatBuffer(16);

    private static long base = Util.getNanos();
    private static int data;
    private static int sheet;
    private static boolean dirty = true;
    private static boolean anchored;
    private static double anchorX, anchorY, anchorZ;
    private static ClientLevel level;
    private static int next;
    private static float lastDeath = -100F;
    private static float flockTimer = 80F;
    private static int here = -1;
    private static int ticks;

    private static float now() {
        return (Util.getNanos() - base) / 1.0E9F;
    }

    public static void onBirdTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        LocalPlayer player = mc.player;
        if (current == null || player == null || !BiomeFx.on(FxConfig.birds, BiomeFx.BIRDS) || BiomeFx.amount(FxConfig.birdsAmount, BiomeFx.BIRDS) <= 0.0
                || !DynamicFogHandler.active() || !current.dimensionType().hasSkyLight() || current.dimensionType().hasCeiling()) {
            if (anchored) reset();
            Diagnostics.skip(Diagnostics.BIRDS, "disabled or no sky here");
            return;
        }
        if (mc.isPaused()) return;
        if (current != level) {
            level = current;
            reset();
        }
        anchor(player.getX(), player.getY(), player.getZ());
        float time = now();
        if (time > REBASE) rebase(time);
        float sun = Mth.cos(level.getTimeOfDay(1F) * Mth.TWO_PI);
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        if (sun < -0.06F || level.isRaining()) {
            ROOST.clear();
            Diagnostics.skip(Diagnostics.BIRDS, "night or rain");
            return;
        }
        if (level.getBrightness(LightLayer.SKY, eye) < 10) {
            Diagnostics.skip(Diagnostics.BIRDS, "no open sky");
            return;
        }
        if (ticks++ % 20 == 0) here = habitat(level.getBiome(eye));
        int habitat = here;
        if (habitat >= 0) {
            flockTimer -= (float) BiomeFx.amount(FxConfig.birdsAmount, BiomeFx.BIRDS);
            if (flockTimer <= 0F) {
                flockTimer = 240F + RANDOM.nextFloat() * 480F;
                flock(player, pick(habitat), Mth.nextFloat(RANDOM, 6F, 34F) * (RANDOM.nextBoolean() ? 1F : -1F),
                        RANDOM.nextFloat() * Mth.TWO_PI, PATH, Double.NaN);
            }
        }
        roosts(player, time);
        Diagnostics.mark(Diagnostics.BIRDS, Diagnostics.detail() ? alive(time) + " birds, " + ROOST.size() + " roosts" : null);
    }

    private static int habitat(Holder<Biome> biome) {
        if (biome.value().getBaseTemperature() < 0.15F) return -1;
        if (biome.is(BiomeTags.IS_BEACH)) return SHORE;
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(Biomes.CHERRY_GROVE)) return WOODS;
        if (biome.is(Tags.Biomes.IS_PLAINS) || biome.is(Biomes.MEADOW) || biome.is(BiomeTags.IS_RIVER)) return OPEN;
        return -1;
    }

    private static int pick(int habitat) {
        float r = RANDOM.nextFloat();
        return switch (habitat) {
            case SHORE -> r < 0.6F ? GULL : r < 0.8F ? SWALLOW : GOOSE;
            case OPEN -> r < 0.45F ? SWALLOW : r < 0.75F ? GOOSE : FINCH;
            default -> r < 0.4F ? FINCH : r < 0.7F ? GOOSE : r < 0.85F ? SWALLOW : ROBIN;
        };
    }

    private static void anchor(double x, double y, double z) {
        if (anchored && Math.abs(x - anchorX) <= REACH && Math.abs(z - anchorZ) <= REACH && Math.abs(y - anchorY) <= REACH) return;
        reset();
        anchorX = Math.floor(x);
        anchorY = Math.floor(y);
        anchorZ = Math.floor(z);
        anchored = true;
    }

    private static void flock(LocalPlayer player, int species, float offset, float heading, int half, double height) {
        float speed = switch (species) {
            case GOOSE -> Mth.nextFloat(RANDOM, 8.5F, 10.5F);
            case SWALLOW -> Mth.nextFloat(RANDOM, 8F, 10.5F);
            case GULL -> Mth.nextFloat(RANDOM, 6F, 7.5F);
            default -> Mth.nextFloat(RANDOM, 6F, 7.5F);
        };
        float clearance = switch (species) {
            case GOOSE -> Mth.nextFloat(RANDOM, 22F, 34F);
            case GULL -> Mth.nextFloat(RANDOM, 9F, 16F);
            case SWALLOW -> Mth.nextFloat(RANDOM, 5F, 11F);
            default -> Mth.nextFloat(RANDOM, 6F, 11F);
        };
        int count = switch (species) {
            case GOOSE -> 5 + RANDOM.nextInt(5);
            case GULL -> 3 + RANDOM.nextInt(4);
            case SWALLOW -> 4 + RANDOM.nextInt(6);
            case ROBIN -> 3 + RANDOM.nextInt(3);
            default -> 3 + RANDOM.nextInt(5);
        };
        float fx = Mth.cos(heading), fz = Mth.sin(heading);
        double cx = player.getX() - fz * offset, cz = player.getZ() + fx * offset;
        double sx = cx - fx * half, sz = cz - fz * half;
        int ground = (int) player.getY();
        for (int i = -half; i <= half; i += 16) {
            int x = Mth.floor(cx + fx * i), z = Mth.floor(cz + fz * i);
            if (level.hasChunk(x >> 4, z >> 4)) ground = Math.max(ground, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
        }
        double y = Double.isNaN(height) ? Math.min(ground + clearance, level.getMaxBuildHeight() + 32) : player.getEyeY() + height;
        float curve = (RANDOM.nextFloat() - 0.5F) * 0.006F;
        float life = (half * 2F + 12F) / speed;
        float time = now();
        boolean vee = species == GOOSE;
        for (int i = 0; i < count; i++) {
            float back, side, up;
            if (vee) {
                int rank = (i + 1) / 2;
                float sign = i % 2 == 1 ? 1F : -1F;
                back = rank * 2.3F + RANDOM.nextFloat() * 0.3F;
                side = sign * rank * 2.0F;
                up = -0.18F * rank;
            } else {
                back = RANDOM.nextFloat() * 7F;
                side = (RANDOM.nextFloat() - 0.5F) * 7F;
                up = (RANDOM.nextFloat() - 0.5F) * 3F;
            }
            write(sx, y, sz, time, heading, curve, speed, life + back / speed, back, side, up, species, 0F,
                    Mth.nextFloat(RANDOM, 0.9F, 1.1F), 0F);
        }
    }

    private static void roosts(LocalPlayer player, float time) {
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        for (int i = ROOST.size() - 1; i >= 0; i--) {
            double[] r = ROOST.get(i);
            double dx = r[0] - px, dy = r[1] - py, dz = r[2] - pz;
            double d2 = dx * dx + dz * dz;
            if (d2 > 48 * 48) {
                ROOST.remove(i);
            } else if ((d2 < 7 * 7 && Math.abs(dy) < 10) || (time > r[4] && d2 < 36 * 36)) {
                ROOST.remove(i);
                flush(r, (float) Mth.atan2(dz, dx));
            }
        }
        if (ROOST.size() >= ROOSTS || ticks % 4 != 0) return;
        float a = RANDOM.nextFloat() * Mth.TWO_PI;
        float d = Mth.nextFloat(RANDOM, 10F, 32F);
        int x = Mth.floor(px + Mth.cos(a) * d), z = Mth.floor(pz + Mth.sin(a) * d);
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        CURSOR.set(x, top, z);
        if (!level.getBlockState(CURSOR).is(BlockTags.LEAVES)) return;
        int habitat = habitat(level.getBiome(CURSOR));
        if (habitat != WOODS && habitat != OPEN) return;
        if (level.getBrightness(LightLayer.SKY, CURSOR.above()) < 12) return;
        for (double[] r : ROOST) {
            if (Math.abs(r[0] - x) < 8 && Math.abs(r[2] - z) < 8) return;
        }
        int species = habitat == WOODS && RANDOM.nextFloat() < 0.4F ? ROBIN : FINCH;
        float wait = Mth.nextFloat(RANDOM, 40F, 120F) / (float) Math.max(BiomeFx.amount(FxConfig.birdsAmount, BiomeFx.BIRDS), 0.1);
        ROOST.add(new double[]{x + 0.5, top + 0.6, z + 0.5, species, time + wait});
    }

    private static void flush(double[] roost, float away) {
        int species = (int) roost[3];
        int count = species == ROBIN ? 2 + RANDOM.nextInt(3) : 3 + RANDOM.nextInt(5);
        float time = now();
        for (int i = 0; i < count; i++) {
            float heading = away + (RANDOM.nextFloat() - 0.5F) * 2.4F;
            write(roost[0] + (RANDOM.nextFloat() - 0.5F) * 2.4F, roost[1] - RANDOM.nextFloat() * 0.6F,
                    roost[2] + (RANDOM.nextFloat() - 0.5F) * 2.4F, time + RANDOM.nextFloat() * 0.35F, heading,
                    (RANDOM.nextFloat() - 0.5F) * 0.08F, Mth.nextFloat(RANDOM, 7F, 9F), Mth.nextFloat(RANDOM, 9F, 12F),
                    0F, 0F, 0F, species, 1F, Mth.nextFloat(RANDOM, 0.9F, 1.1F), Mth.nextFloat(RANDOM, 4F, 7F));
        }
    }

    private static void write(double x, double y, double z, float birth, float heading, float curve, float speed, float life,
                              float back, float side, float up, int species, float mode, float size, float burst) {
        int o = next * 4;
        next = (next + 1) % SLOTS;
        float[] a = ROW[0], b = ROW[1], c = ROW[2], d = ROW[3];
        a[o] = (float) (x - anchorX);
        a[o + 1] = (float) (y - anchorY);
        a[o + 2] = (float) (z - anchorZ);
        a[o + 3] = birth;
        b[o] = heading;
        b[o + 1] = curve;
        b[o + 2] = speed;
        b[o + 3] = life;
        c[o] = back;
        c[o + 1] = side;
        c[o + 2] = up;
        c[o + 3] = RANDOM.nextFloat();
        d[o] = species;
        d[o + 1] = mode;
        d[o + 2] = size;
        d[o + 3] = burst;
        lastDeath = Math.max(lastDeath, birth + life);
        dirty = true;
    }

    private static int alive(float time) {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) {
            float t = time - ROW[0][i * 4 + 3];
            if (t >= 0F && t <= ROW[1][i * 4 + 3]) n++;
        }
        return n;
    }

    private static void rebase(float time) {
        for (int i = 0; i < SLOTS; i++) ROW[0][i * 4 + 3] -= time;
        for (double[] r : ROOST) r[4] -= time;
        lastDeath -= time;
        base = Util.getNanos();
        dirty = true;
    }

    private static void reset() {
        for (float[] row : ROW) Arrays.fill(row, 0F);
        ROOST.clear();
        anchored = false;
        lastDeath = -100F;
        flockTimer = 80F;
        dirty = true;
    }

    public static void capture(String arg) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || level == null || !anchored) return;
        String[] parts = arg.split(" ");
        float yaw = (player.getYRot() + 90F) * Mth.DEG_TO_RAD;
        double distance = parts.length > 2 ? Double.parseDouble(parts[2]) : 40.0;
        flockTimer = 1.0E9F;
        if (parts[0].equals("flock")) {
            int species = Integer.parseInt(parts[1]);
            flock(player, species, (float) -distance, yaw + Mth.HALF_PI, 30, Double.parseDouble(parts[3]));
        } else if (parts[0].equals("flush")) {
            double x = 0, z = 0;
            int top = 0;
            for (double d = 3; d <= distance; d += 0.5) {
                x = player.getX() + Mth.cos(yaw) * d;
                z = player.getZ() + Mth.sin(yaw) * d;
                top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
                if (level.getBlockState(CURSOR.set(Mth.floor(x), top - 1, Mth.floor(z))).is(BlockTags.LEAVES)) break;
            }
            float turn = parts.length > 3 ? Float.parseFloat(parts[3]) * Mth.DEG_TO_RAD : 0F;
            flush(new double[]{Math.floor(x) + 0.5, top - 0.4, Math.floor(z) + 0.5, Integer.parseInt(parts[1]), 0}, yaw + turn);
        }
    }

    public static void onRenderBirdStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        if (!anchored || LightTint.lightmap == null) return;
        float time = now();
        if (time > lastDeath) return;
        SkyProgram program = SkyProgram.BIRDS;
        if (!program.ready() || sheet() <= 0) return;

        upload();
        Vec3 eye = event.getCamera().getPosition();
        float ox = (float) (anchorX - eye.x);
        float oy = (float) (anchorY - eye.y);
        float oz = (float) (anchorZ - eye.z);
        MATRIX.set(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose()).translate(ox, oy, oz);
        MATRIX.get(TRANSFORM);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        program.begin();
        SkyProgram.texture(2, data);
        SkyProgram.texture(1, LightTint.lightmap.getId());
        SkyProgram.texture(0, sheet);
        GL20.glUniformMatrix4fv(program.uniform(0), false, TRANSFORM);
        GL20.glUniform3f(program.uniform(1), ox, oy, oz);
        GL20.glUniform4f(program.uniform(2), time, RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), 0F);
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.uniform(3), fog[0], fog[1], fog[2], 1F);
        program.draw(SLOTS * 36);
        program.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static int sheet() {
        if (sheet != 0) return sheet;
        sheet = -1;
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(SHEET); NativeImage image = NativeImage.read(in)) {
            int id = TextureUtil.generateTextureId();
            TextureUtil.prepareImage(id, 4, image.getWidth(), image.getHeight());
            image.upload(0, 0, 0, false);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            sheet = id;
        } catch (Exception e) {
            Diagnostics.LOG.error("[atmosphere] bird sprites failed to load", e);
        }
        return sheet;
    }

    private static void upload() {
        if (data == 0) {
            data = GlStateManager._genTexture();
            GlStateManager._bindTexture(data);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            SkyHeights.unpack();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, SLOTS, 4, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            dirty = true;
        }
        if (!dirty) return;
        GlStateManager._bindTexture(data);
        SkyHeights.unpack();
        for (int row = 0; row < 4; row++) {
            UPLOAD.clear();
            UPLOAD.put(ROW[row]).flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, row, SLOTS, 1, GL11.GL_RGBA, GL11.GL_FLOAT, UPLOAD);
        }
        dirty = false;
    }
}
