package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
public final class FishJumps {
    private FishJumps() {}

    private static final int SLOTS = 6;
    private static final int VERTICES = 72;
    private static final float GRAVITY = 16F;
    private static final double REACH = 256.0;
    private static final float[] SIZE = {1.8F, 1.7F, 1.25F, 1.1F, 1.2F, 1.1F};
    private static final ResourceLocation SHEET = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/fish.png");

    private static final float[][] ROW = new float[3][SLOTS * 4];
    private static final double[][] SPOT = new double[SLOTS][5];
    private static final boolean[] LEFT = new boolean[SLOTS];
    private static final boolean[] LANDED = new boolean[SLOTS];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(SLOTS * 4);
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final long BASE = Util.getNanos();

    private static int data;
    private static boolean dirty = true;
    private static boolean anchored;
    private static double anchorX, anchorY, anchorZ;
    private static ClientLevel level;
    private static int next;
    private static int cooldown = 100;
    private static float lastDeath = -100F;
    private static float lastTime;

    private static float now() {
        return ((Util.getNanos() - BASE) / 1.0E9F) % 3600F;
    }

    private static boolean on() {
        return FxConfig.fishJumps && FxConfig.fishJumpsAmount > 0.0 && DynamicFogHandler.active() && !ColorGrade.shaderPackActive();
    }

    public static void onFishJumpsTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        LocalPlayer player = mc.player;
        if (current == null || player == null || !on() || !current.dimensionType().hasSkyLight()) {
            if (anchored) reset();
            Diagnostics.skip(Diagnostics.FISH, "disabled or no sky here");
            return;
        }
        if (mc.isPaused()) return;
        if (current != level) {
            level = current;
            reset();
        }
        Diagnostics.mark(Diagnostics.FISH, null);
        if (--cooldown > 0 || !WaveField.ready()) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera.getFluidInCamera() != FogType.NONE) {
            cooldown = 20;
            return;
        }
        float angle = level.getTimeOfDay(1F) * Mth.TWO_PI;
        float c = Mth.cos(angle);
        float twilight = Math.max(0F, 1F - Math.abs(c) / 0.35F);
        float rate = (0.4F + 0.6F * SkyGlow.smooth(-0.3F, 0.2F, c) + 0.8F * twilight) * (1F - 0.3F * level.getRainLevel(1F)) * (float) FxConfig.fishJumpsAmount;
        boolean fired = false;
        for (int i = 0; i < 6 && !fired; i++) fired = spawn(player, camera);
        cooldown = fired ? (int) ((200 + RANDOM.nextInt(200)) / Math.max(rate, 0.05F)) : 20;
    }

    private static boolean column(int x, int z, int y) {
        if (!level.hasChunk(x >> 4, z >> 4)) return false;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (top != y) return false;
        CURSOR.set(x, top, z);
        FluidState fluid = level.getFluidState(CURSOR);
        if (!fluid.isSource() || !fluid.is(FluidTags.WATER)) return false;
        return level.getFluidState(CURSOR.below()).is(FluidTags.WATER);
    }

    private static boolean spawn(LocalPlayer player, Camera camera) {
        Vec3 eye = camera.getPosition();
        float yaw = camera.getYRot() * Mth.DEG_TO_RAD;
        float a = RANDOM.nextFloat() < 0.85F ? yaw + (RANDOM.nextFloat() - 0.5F) * 1.9F : RANDOM.nextFloat() * Mth.TWO_PI;
        double d = 8.0 + Math.pow(RANDOM.nextDouble(), 1.3) * 28.0;
        int x = Mth.floor(eye.x - Mth.sin(a) * d);
        int z = Mth.floor(eye.z + Mth.cos(a) * d);
        if (!level.hasChunk(x >> 4, z >> 4)) return false;
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (!column(x, z, y)) return false;
        float life = 0.62F + RANDOM.nextFloat() * 0.28F;
        float heading = RANDOM.nextFloat() * Mth.TWO_PI;
        float speed = 2.2F + RANDOM.nextFloat() * 1.4F;
        float vx = Mth.cos(heading) * speed, vz = Mth.sin(heading) * speed;
        double ex = x + 0.5 + vx * life, ez = z + 0.5 + vz * life;
        if (!column(Mth.floor(x + 0.5 + vx * life * 0.5), Mth.floor(z + 0.5 + vz * life * 0.5), y)
                || !column(Mth.floor(ex), Mth.floor(ez), y)) return false;
        CURSOR.set(x, y, z);
        FluidState fluid = level.getFluidState(CURSOR);
        double surface = y + fluid.getHeight(level, CURSOR);
        CURSOR.set(x, y + 1, z);
        int sky = level.getBrightness(LightLayer.SKY, CURSOR);
        if (sky < 10) return false;
        Vec3 target = new Vec3(x + 0.5, surface + 0.8, z + 0.5);
        if (level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) return false;
        int slot = next;
        float time = now();
        if (time - ROW[0][slot * 4 + 3] < ROW[1][slot * 4 + 3] + 0.5F) return false;
        if (!anchored || Math.abs(eye.x - anchorX) > REACH || Math.abs(eye.z - anchorZ) > REACH) {
            reset();
            anchorX = Math.floor(eye.x);
            anchorY = Math.floor(eye.y);
            anchorZ = Math.floor(eye.z);
            anchored = true;
        }
        int kind = kind(level.getBiome(CURSOR));
        int o = slot * 4;
        ROW[0][o] = (float) (x + 0.5 - anchorX);
        ROW[0][o + 1] = (float) (surface - anchorY);
        ROW[0][o + 2] = (float) (z + 0.5 - anchorZ);
        ROW[0][o + 3] = time;
        ROW[1][o] = vx;
        ROW[1][o + 1] = GRAVITY * life * 0.5F;
        ROW[1][o + 2] = vz;
        ROW[1][o + 3] = life;
        ROW[2][o] = kind;
        ROW[2][o + 1] = level.getBrightness(LightLayer.BLOCK, CURSOR) * 16 + sky;
        ROW[2][o + 2] = SIZE[kind] * (0.9F + RANDOM.nextFloat() * 0.2F);
        ROW[2][o + 3] = RANDOM.nextFloat();
        SPOT[slot][0] = x + 0.5;
        SPOT[slot][1] = surface;
        SPOT[slot][2] = z + 0.5;
        SPOT[slot][3] = ex;
        SPOT[slot][4] = ez;
        LEFT[slot] = false;
        LANDED[slot] = false;
        next = (next + 1) % SLOTS;
        lastDeath = Math.max(lastDeath, time + life);
        dirty = true;
        return true;
    }

    private static int kind(Holder<Biome> biome) {
        String name = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
        float temperature = biome.value().getBaseTemperature();
        boolean cold = temperature < 0.25F || name.contains("frozen") || name.contains("cold") || name.contains("snow");
        float r = RANDOM.nextFloat();
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) {
            if (name.contains("warm") || name.contains("lukewarm")) return 3 + RANDOM.nextInt(3);
            if (cold) return r < 0.45F ? 0 : 1;
            return r < 0.2F ? 0 : 1;
        }
        if (cold) return r < 0.65F ? 0 : 2;
        return r < 0.85F ? 2 : 1;
    }

    private static void reset() {
        for (float[] row : ROW) java.util.Arrays.fill(row, 0F);
        for (int i = 0; i < SLOTS; i++) ROW[0][i * 4 + 3] = -1000F;
        java.util.Arrays.fill(LEFT, true);
        java.util.Arrays.fill(LANDED, true);
        anchored = false;
        lastDeath = -100F;
        dirty = true;
    }

    private static void splashes(float time) {
        for (int i = 0; i < SLOTS; i++) {
            float born = ROW[0][i * 4 + 3];
            if (!LEFT[i] && time >= born) {
                LEFT[i] = true;
                Wake.splash(SPOT[i][0], SPOT[i][1], SPOT[i][2], 0.5F, 0.4F);
            }
            if (!LANDED[i] && time >= born + ROW[1][i * 4 + 3]) {
                LANDED[i] = true;
                Wake.splash(SPOT[i][3], SPOT[i][1], SPOT[i][4], 0.7F, 0.7F);
            }
        }
    }

    public static void onRenderFishJumpsStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!anchored) return;
        float time = now();
        if (time < lastTime - 100F) {
            reset();
            return;
        }
        lastTime = time;
        if (!on() || time > lastDeath + 0.5F) return;
        if (WaveField.ready()) splashes(time);
        if (time > lastDeath || LightTint.lightmap == null || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        SkyProgram program = SkyProgram.FISH;
        if (!program.ready()) return;

        Vec3 eye = event.getCamera().getPosition();
        upload();
        float ox = (float) (anchorX - eye.x);
        float oy = (float) (anchorY - eye.y);
        float oz = (float) (anchorZ - eye.z);
        MATRIX.set(event.getPoseStack().last().pose()).translate(ox, oy, oz);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        program.begin();
        SkyProgram.texture(2, data);
        SkyProgram.texture(1, LightTint.lightmap.getId());
        SkyProgram.texture(0, SkyProgram.texture(SHEET));
        program.transform(0, MATRIX);
        GL20.glUniform3f(program.uniform(1), ox, oy, oz);
        GL20.glUniform4f(program.uniform(2), time, RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), 0F);
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.uniform(3), fog[0], fog[1], fog[2], 1F);
        program.draw(SLOTS * VERTICES);
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
