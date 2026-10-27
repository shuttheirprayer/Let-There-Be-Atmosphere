package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
public final class BatSwarm {
    private BatSwarm() {}

    private static final int SLOTS = 48;
    private static final int DUSK_FROM = 11800;
    private static final int DUSK_TO = 13600;
    private static final float CHANCE = 0.4F;
    private static final int TUNNEL = 7;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};
    private static final ResourceLocation SHEET = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/bats.png");

    private static final float[][] ROW = new float[3][SLOTS * 4];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(SLOTS * 4);
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final FloatBuffer TRANSFORM = BufferUtils.createFloatBuffer(16);

    private static ClientLevel level;
    private static int data;
    private static int sheet;
    private static boolean dirty = true;
    private static boolean live;
    private static boolean armed;
    private static long rolledDay = -1L;
    private static int armedAt;
    private static int ticks;
    private static long startNanos;
    private static float total;
    private static int anchorX, anchorY, anchorZ;
    private static int mouthX, mouthY, mouthZ, mouthDx, mouthDz;

    private static boolean on() {
        return FxConfig.batSwarm && DynamicFogHandler.active() && !ColorGrade.shaderPackActive();
    }

    private static float elapsed() {
        return (Util.getNanos() - startNanos) / 1.0E9F;
    }

    public static void onBatSwarmTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        LocalPlayer player = mc.player;
        if (current == null || player == null || !on() || current.dimension() != Level.OVERWORLD) {
            live = false;
            armed = false;
            Diagnostics.skip(Diagnostics.BAT_SWARM, "disabled or not the overworld");
            return;
        }
        if (mc.isPaused()) return;
        if (current != level) {
            level = current;
            live = false;
            armed = false;
            rolledDay = -1L;
        }
        if (live) {
            if (elapsed() > total) live = false;
            Diagnostics.mark(Diagnostics.BAT_SWARM, Diagnostics.detail() ? "swarm out, " + (int) elapsed() + " s" : null);
            return;
        }
        long time = level.getDayTime();
        int clock = (int) (time % 24000L);
        if (clock < DUSK_FROM || clock > DUSK_TO) {
            armed = false;
            rolledDay = -1L;
            Diagnostics.skip(Diagnostics.BAT_SWARM, "not dusk");
            return;
        }
        long day = time / 24000L;
        if (day != rolledDay) {
            rolledDay = day;
            armed = RANDOM.nextFloat() < CHANCE;
            ticks = 0;
            armedAt = DUSK_FROM + 100 + RANDOM.nextInt(1000);
        }
        if (!armed || clock < armedAt || level.isRaining() || ++ticks % 5 != 0) {
            Diagnostics.mark(Diagnostics.BAT_SWARM, Diagnostics.detail() ? (armed ? "armed" : "no swarm tonight") : null);
            return;
        }
        if (level.getBrightness(LightLayer.SKY, BlockPos.containing(player.getEyePosition())) < 8) return;
        boolean wide = ticks > 400;
        if (find(player, 8, wide ? Mth.PI : 1.05F)) {
            spawn();
            armed = false;
        }
        Diagnostics.mark(Diagnostics.BAT_SWARM, Diagnostics.detail() ? "armed, scanning" : null);
    }

    public static boolean trigger() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (level == null || player == null || live) return false;
        if (!find(player, 600, 1.05F) && !find(player, 3000, Mth.PI)) return false;
        spawn();
        return true;
    }

    private static boolean loaded(int x, int z) {
        return level.hasChunk(x >> 4, z >> 4) && level.getLightEngine().lightOnInSection(SectionPos.of(x >> 4, 0, z >> 4));
    }

    private static boolean air(int x, int y, int z) {
        return level.getBlockState(CURSOR.set(x, y, z)).isAir() && level.getBlockState(CURSOR.set(x, y + 1, z)).isAir();
    }

    private static boolean covered(int x, int y, int z, int roof) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) >= y + roof;
    }

    private static boolean find(LocalPlayer player, int samples, float cone) {
        float look = (player.getYRot() + 90F) * Mth.DEG_TO_RAD;
        for (int i = 0; i < samples; i++) {
            float angle = look + (RANDOM.nextFloat() * 2F - 1F) * cone;
            float distance = Mth.nextFloat(RANDOM, 16F, 64F);
            int x = Mth.floor(player.getX() + Mth.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Mth.sin(angle) * distance);
            if (!loaded(x, z)) continue;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            for (int y = top - 5; y >= Math.max(level.getMinBuildHeight() + 1, top - 30); y--) {
                if (air(x, y, z) && mouth(x, y, z, player)) return true;
            }
        }
        return false;
    }

    private static boolean mouth(int ix, int y, int iz, LocalPlayer player) {
        for (int d = 0; d < 4; d++) {
            for (int j = 1; j <= 12; j++) {
                int x = ix - DX[d] * j, z = iz - DZ[d] * j;
                if (!loaded(x, z) || !air(x, y, z)) break;
                if (covered(x, y, z, 3)) continue;
                if (tunnel(x, y, z, DX[d], DZ[d]) && Math.abs(mouthX - player.getX()) < 70 && Math.abs(mouthZ - player.getZ()) < 70
                        && Mth.square(mouthX - player.getX()) + Mth.square(mouthZ - player.getZ()) > 14 * 14) return true;
                break;
            }
        }
        return false;
    }

    private static boolean tunnel(int ox, int oy, int oz, int dx, int dz) {
        if (level.getBrightness(LightLayer.SKY, CURSOR.set(ox, oy, oz)) < 10) return false;
        for (int k = 1; k <= TUNNEL; k++) {
            int x = ox + dx * k, z = oz + dz * k;
            if (!loaded(x, z) || !air(x, oy, z) || !covered(x, oy, z, k < 3 ? 3 : 5)
                    || level.getBlockState(CURSOR.set(x, oy - 1, z)).isAir()) return false;
        }
        mouthX = ox + dx;
        mouthY = oy;
        mouthZ = oz + dz;
        mouthDx = dx;
        mouthDz = dz;
        return true;
    }

    private static void spawn() {
        anchorX = mouthX;
        anchorY = mouthY;
        anchorZ = mouthZ;
        int count = 22 + RANDOM.nextInt(19);
        float span = Mth.nextFloat(RANDOM, 5F, 8F);
        float life = 0F;
        for (int i = 0; i < SLOTS; i++) {
            int o = i * 4;
            if (i >= count) {
                ROW[1][o + 3] = 0F;
                continue;
            }
            float birth = span * i / count + RANDOM.nextFloat() * 0.1F;
            float lifespan = Mth.nextFloat(RANDOM, 11F, 14F);
            float side = Mth.nextFloat(RANDOM, -0.5F, 0.5F);
            ROW[0][o] = 0.5F + mouthDx * 4F + mouthDz * side;
            ROW[0][o + 1] = 0.9F + Mth.nextFloat(RANDOM, -0.5F, 0.5F);
            ROW[0][o + 2] = 0.5F + mouthDz * 4F - mouthDx * side;
            ROW[0][o + 3] = birth;
            ROW[1][o] = -mouthDx;
            ROW[1][o + 1] = -mouthDz;
            ROW[1][o + 2] = Mth.nextFloat(RANDOM, 5F, 6.8F);
            ROW[1][o + 3] = lifespan;
            ROW[2][o] = Mth.nextFloat(RANDOM, 0.25F, 1.25F) * (RANDOM.nextBoolean() ? 1F : -1F);
            ROW[2][o + 1] = Mth.nextFloat(RANDOM, 0.7F, 2.2F);
            ROW[2][o + 2] = 0.5F * Mth.nextFloat(RANDOM, 0.9F, 1.1F);
            ROW[2][o + 3] = RANDOM.nextFloat();
            life = Math.max(life, birth + lifespan);
        }
        total = life;
        startNanos = Util.getNanos();
        live = true;
        dirty = true;
    }

    public static void onRenderBatSwarmStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || !live) return;
        SkyProgram program = SkyProgram.BATS;
        if (!program.ready() || sheet() <= 0) return;

        upload();
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        float ox = (float) (anchorX - eye.x);
        float oy = (float) (anchorY - eye.y);
        float oz = (float) (anchorZ - eye.z);
        MATRIX.set(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose()).translate(ox, oy, oz);
        MATRIX.get(TRANSFORM);
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        program.begin();
        SkyProgram.texture(1, data);
        SkyProgram.texture(0, sheet);
        GL20.glUniformMatrix4fv(program.uniform(0), false, TRANSFORM);
        GL20.glUniform3f(program.uniform(1), ox, oy, oz);
        GL20.glUniform4f(program.uniform(2), elapsed(), 0F, 0F, 0F);
        GL20.glUniform3f(program.uniform(3), -left.x(), -left.y(), -left.z());
        GL20.glUniform3f(program.uniform(4), up.x(), up.y(), up.z());
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.uniform(5), fog[0], fog[1], fog[2], 1F);
        program.draw(SLOTS * 6);
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
            TextureUtil.prepareImage(id, 1, image.getWidth(), image.getHeight());
            image.upload(0, 0, 0, false);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            sheet = id;
        } catch (Exception e) {
            Diagnostics.LOG.error("[atmosphere] bat sprites failed to load", e);
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
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, SLOTS, 3, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            dirty = true;
        }
        if (!dirty) return;
        GlStateManager._bindTexture(data);
        SkyHeights.unpack();
        for (int row = 0; row < 3; row++) {
            UPLOAD.clear();
            UPLOAD.put(ROW[row]).flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, row, SLOTS, 1, GL11.GL_RGBA, GL11.GL_FLOAT, UPLOAD);
        }
        dirty = false;
    }
}
