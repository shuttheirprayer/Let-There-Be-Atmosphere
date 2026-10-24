package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import java.util.Arrays;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
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
public final class NetherFx {
    private NetherFx() {}

    private static final int RAY = 0;
    private static final int CROWN = 1;
    private static final int FRAG = 2;
    private static final int RING = 3;
    private static final int ORB = 4;
    private static final int WISP = 5;
    private static final int SLOTS = 512;
    private static final int RAY_SLOTS = 32;
    private static final float REBASE = 1800F;
    private static final double REACH = 2048.0;
    private static final ResourceLocation LAVA = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/lava_fx.png");
    private static final ResourceLocation RAYS = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/nether_rays.png");
    private static final float[] ROW_A = new float[SLOTS * 4];
    private static final float[] ROW_B = new float[SLOTS * 4];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(SLOTS * 4);
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final FloatBuffer MATRIX_UPLOAD = BufferUtils.createFloatBuffer(16);

    private static long base = Util.getNanos();
    private static int rayHead, partHead = RAY_SLOTS;
    private static boolean anchored, dirty, rayInit;
    private static double anchorX, anchorY, anchorZ;
    private static float lastBirth = -100F;
    private static int rayDelay;
    private static int data;

    private static float now() {
        return (Util.getNanos() - base) / 1.0E9F;
    }

    private static void emit(int kind, double x, double y, double z, float seed, float life, float aux, float age) {
        if (!anchored || Math.abs(x - anchorX) > REACH || Math.abs(z - anchorZ) > REACH || Math.abs(y - anchorY) > REACH) {
            Arrays.fill(ROW_B, 0F);
            anchorX = Math.floor(x);
            anchorY = Math.floor(y);
            anchorZ = Math.floor(z);
            anchored = true;
            rayInit = false;
            dirty = true;
        }
        float now = now();
        if (now > REBASE) {
            for (int i = 0; i < SLOTS; i++) ROW_A[i * 4 + 3] -= now;
            lastBirth -= now;
            base = Util.getNanos();
            now = now();
        }
        int slot;
        if (kind == RAY) {
            slot = rayHead;
            rayHead = (rayHead + 1) % RAY_SLOTS;
        } else {
            slot = partHead;
            partHead = partHead + 1 >= SLOTS ? RAY_SLOTS : partHead + 1;
        }
        int o = slot * 4;
        ROW_A[o] = (float) (x - anchorX);
        ROW_A[o + 1] = (float) (y - anchorY);
        ROW_A[o + 2] = (float) (z - anchorZ);
        ROW_A[o + 3] = now - age;
        ROW_B[o] = kind;
        ROW_B[o + 1] = seed;
        ROW_B[o + 2] = aux;
        ROW_B[o + 3] = life;
        dirty = true;
        lastBirth = now;
    }

    private static boolean ready() {
        return DynamicFogHandler.active() && !ColorGrade.shaderPackActive() && SkyProgram.NETHER_FX.ready();
    }

    static void lava(ClientLevel level, double x, double y, double z) {
        if (!ready()) return;
        Diagnostics.once("vanilla lava particle intercepted, our pixel lava particles replace it");
        RandomSource random = level.random;
        float amount = (float) FxConfig.lavaParticlesAmount;
        if (amount <= 0F || random.nextFloat() > 0.6F * amount) return;
        BlockPos pos = BlockPos.containing(x, y - 0.5, z);
        FluidState fluid = level.getFluidState(pos);
        double surface = fluid.isEmpty() ? y - 0.11 : pos.getY() + fluid.getHeight(level, pos);
        int tone = NetherAir.here(level) && NetherAir.lookAt(level, x, y, z) == NetherAir.CRIMSON ? 1 : 0;
        erupt(random, x, surface, z, tone, 0F);
    }

    static boolean erupt(RandomSource random, double x, double surface, double z, int tone, float heat) {
        float big = 0.1F + 0.1F * heat, orb = 0.2F, ring = 0.24F - 0.12F * heat, wisp = 0.12F - 0.06F * heat;
        float r = random.nextFloat();
        if (r < big) {
            pop(random, x, surface, z, tone, 1.7F, 6 + random.nextInt(3));
            return true;
        }
        r -= big;
        if (r < orb) {
            emit(ORB, x, surface + 0.05, z, tone + random.nextFloat(), 1.8F + random.nextFloat() * 1.5F, 1F + random.nextFloat() * 0.5F, 0F);
            emit(RING, x, surface, z, tone + random.nextFloat(), 0.9F + random.nextFloat() * 0.4F, 1F, 0F);
            return false;
        }
        r -= orb;
        if (r < ring) {
            emit(RING, x, surface, z, tone + random.nextFloat(), 0.9F + random.nextFloat() * 0.4F, 1F, 0F);
            if (random.nextBoolean()) {
                emit(RING, x + random.nextGaussian() * 0.6, surface, z + random.nextGaussian() * 0.6, tone + random.nextFloat(),
                        0.8F + random.nextFloat() * 0.4F, 0.8F, 0.15F);
            }
            return false;
        }
        r -= ring;
        if (r < wisp) {
            emit(WISP, x, surface + 0.1, z, tone + random.nextFloat(), 2.6F + random.nextFloat() * 1.4F, 1F, 0F);
            return false;
        }
        pop(random, x, surface, z, tone, 1F, 3 + random.nextInt(3));
        return true;
    }

    public static void capture(String arg) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        String[] v = arg.split(" ");
        double x = Double.parseDouble(v[0]), y = Double.parseDouble(v[1]), z = Double.parseDouble(v[2]);
        RandomSource random = level.random;
        int tone = v.length > 3 ? Integer.parseInt(v[3]) : 0;
        for (int i = 0; i < 4; i++) {
            double ox = x + (i - 1.5) * 1.8, oz = z - i * 0.9;
            pop(random, ox, y, oz, tone, i == 3 ? 1.7F : 1F, 4);
            emit(ORB, ox + 0.6, y + 0.05, oz - 0.5, tone + random.nextFloat(), 3F, 1.3F, 0F);
            emit(RING, ox - 0.8, y, oz + 0.4, tone + random.nextFloat(), 1.2F, 1F, 0F);
            emit(WISP, ox + 1.1, y + 0.1, oz, tone + random.nextFloat(), 3.5F, 1F, 0F);
        }
        for (int i = 0; i < 8; i++) ray(level, Minecraft.getInstance().player, random, random.nextFloat() * 20F);
    }

    private static void pop(RandomSource random, double x, double surface, double z, int tone, float scale, int fragments) {
        emit(CROWN, x, surface, z, tone + random.nextFloat(), 0.55F, scale, 0F);
        emit(RING, x, surface, z, tone + random.nextFloat(), 1F, scale, 0F);
        for (int i = 0; i < fragments; i++) {
            emit(FRAG, x, surface + 0.02, z, tone + random.nextFloat(), 0.6F + random.nextFloat() * 0.35F, 0F, 0F);
        }
    }

    public static void onNetherFxTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) return;
        if (!FxConfig.netherRays || !NetherAir.here(level) || !ready()) {
            rayInit = false;
            return;
        }
        RandomSource random = level.random;
        if (!rayInit) {
            rayInit = true;
            for (int i = 0; i < 10; i++) ray(level, player, random, random.nextFloat() * 22F);
            rayDelay = 40;
        }
        if (--rayDelay <= 0) {
            ray(level, player, random, 0F);
            rayDelay = 50 + random.nextInt(60);
        }
    }

    private static void ray(ClientLevel level, LocalPlayer player, RandomSource random, float age) {
        float angle = random.nextFloat() * Mth.TWO_PI;
        float reach = 36F + random.nextFloat() * 46F;
        double x = player.getX() + Mth.cos(angle) * reach, z = player.getZ() + Mth.sin(angle) * reach;
        double ceiling = level.getMinBuildHeight() + Math.min(level.dimensionType().logicalHeight(), level.getHeight()) - 4;
        double top = Math.min(ceiling, player.getY() + 36.0 + random.nextFloat() * 34.0);
        float height = 42F + random.nextFloat() * 26F;
        int look = NetherAir.lookAt(level, x, player.getEyeY(), z);
        emit(RAY, x, top - height, z, look + random.nextFloat(), 30F + random.nextFloat() * 18F, height, age);
    }

    public static void onRenderNetherFxStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        boolean rays = FxConfig.netherRays && NetherAir.here(level);
        boolean lava = FxConfig.lavaParticles;
        if (!rays) Diagnostics.skip(Diagnostics.NETHER_RAYS, FxConfig.netherRays ? "not in the Nether" : "disabled");
        if (!lava) Diagnostics.skip(Diagnostics.LAVA_FX, "disabled");
        if (level == null || !(rays || lava)) return;
        SkyProgram program = SkyProgram.NETHER_FX;
        Camera camera = event.getCamera();
        if (!ready() || CameraFluid.of(camera) != FogType.NONE) {
            Diagnostics.skip(Diagnostics.NETHER_RAYS, "fog handler off, shader pack, camera in a fluid or shader failed");
            Diagnostics.skip(Diagnostics.LAVA_FX, "fog handler off, shader pack, camera in a fluid or shader failed");
            return;
        }
        float time = now();
        if (!anchored || time - lastBirth > 60F) {
            if (rays) Diagnostics.skip(Diagnostics.NETHER_RAYS, "waiting for the first ray");
            if (lava) Diagnostics.skip(Diagnostics.LAVA_FX, "no open lava nearby");
            return;
        }
        upload();
        Vec3 eye = camera.getPosition();
        float ex = (float) (eye.x - anchorX), ey = (float) (eye.y - anchorY), ez = (float) (eye.z - anchorZ);
        MATRIX.set(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose()).translate(-ex, -ey, -ez);
        MATRIX.get(MATRIX_UPLOAD);
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        float[] fog = RenderSystem.getShaderFogColor();
        float rayGain = rays ? Mth.clamp(0.22F * (float) FxConfig.netherRaysStrength * NetherAir.LOOK[3] * (1F - NetherDust.gust()), 0F, 0.3F) : 0F;

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        program.begin();
        SkyProgram.texture(2, data);
        SkyProgram.texture(1, SkyProgram.texture(RAYS));
        SkyProgram.texture(0, SkyProgram.texture(LAVA));
        GL20.glUniformMatrix4fv(program.uniform(0), false, MATRIX_UPLOAD);
        GL20.glUniform4f(program.uniform(1), ex, ey, ez, 0F);
        GL20.glUniform3f(program.uniform(2), -left.x(), -left.y(), -left.z());
        GL20.glUniform3f(program.uniform(3), up.x(), up.y(), up.z());
        GL20.glUniform4f(program.uniform(5), fog[0], fog[1], fog[2], 1F);
        GL20.glUniform4f(program.uniform(6), rayGain, lava ? 1F : 0F, 0F, 0F);
        if (rayGain > 0F) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR);
            GL20.glUniform4f(program.uniform(4), time, RenderSystem.getShaderFogStart(), Math.max(RenderSystem.getShaderFogEnd(), 16F), 0F);
            program.draw(RAY_SLOTS * 6);
        }
        if (lava) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GL20.glUniform4f(program.uniform(4), time, RenderSystem.getShaderFogStart(), Math.max(RenderSystem.getShaderFogEnd(), 16F), 1F);
            program.draw(SLOTS * 6);
        }
        program.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        if (rays) Diagnostics.mark(Diagnostics.NETHER_RAYS, Diagnostics.detail() ? String.format("gain %.2f", rayGain) : null);
        if (lava) Diagnostics.mark(Diagnostics.LAVA_FX, null);
    }

    private static void upload() {
        if (data == 0) {
            data = GlStateManager._genTexture();
            GlStateManager._bindTexture(data);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            SkyHeights.unpack();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, SLOTS, 2, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            dirty = true;
        }
        if (!dirty) return;
        GlStateManager._bindTexture(data);
        SkyHeights.unpack();
        UPLOAD.clear();
        UPLOAD.put(ROW_A).flip();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, SLOTS, 1, GL11.GL_RGBA, GL11.GL_FLOAT, UPLOAD);
        UPLOAD.clear();
        UPLOAD.put(ROW_B).flip();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 1, SLOTS, 1, GL11.GL_RGBA, GL11.GL_FLOAT, UPLOAD);
        dirty = false;
    }
}
