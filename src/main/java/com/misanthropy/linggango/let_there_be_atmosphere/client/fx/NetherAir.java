package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import java.util.Arrays;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class NetherAir {
    private NetherAir() {}

    static final int WASTES = 0;
    static final int CRIMSON = 1;
    static final int SOUL = 2;
    static final int WARPED = 3;
    static final int BASALT = 4;

    private static final float[][] LOOKS = {
            {1.00F, 0.40F, 0.12F, 0.95F, 1.00F, 0.42F, 0.12F, 1.00F, 0.85F},
            {1.00F, 0.22F, 0.14F, 0.75F, 1.00F, 0.24F, 0.16F, 0.90F, 0.85F},
            {0.45F, 0.85F, 1.00F, 1.00F, 0.25F, 0.72F, 1.00F, 0.85F, 0.50F},
            {0.30F, 1.00F, 0.82F, 0.35F, 0.18F, 0.80F, 0.78F, 0.80F, 0.55F},
            {1.00F, 0.80F, 0.64F, 0.70F, 0.85F, 0.74F, 0.70F, 1.00F, 0.45F},
    };
    private static final float[] CRUST = {1.0F, 0.8F, 1.0F, 0.8F, 1.4F};
    private static final float MARCH = 72F;
    private static final double MIST_SCALE = 1.0 / 40.0;
    private static final int[] RING = {0, 10, 10, 10, 10, 10, 10, 22, 22, 22, 22, 22, 22};
    private static final int[] FLOORS = new int[RING.length];
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final Vector4f HORIZON = new Vector4f();
    private static final Matrix4f SURFACE_VIEW = new Matrix4f();
    private static final Matrix4f SURFACE_INVERSE = new Matrix4f();
    static final float[] LOOK = LOOKS[WASTES].clone();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    private static TextureTarget glow;
    private static TextureTarget soft;
    private static int look = WASTES;
    private static long frameAt;
    private static float floor = Float.NaN;
    private static float floorTarget = Float.NaN;
    private static int ticks;
    private static double driftX, driftZ;
    private static float lavaRed;

    static boolean here(ClientLevel level) {
        return level != null && level.dimensionType().ultraWarm();
    }

    static int look() {
        return look;
    }

    public static boolean warm(ClientLevel level, double x, double y, double z) {
        int at = lookAt(level, x, y, z);
        return at != SOUL && at != WARPED;
    }

    static int lookAt(ClientLevel level, double x, double y, double z) {
        Holder<Biome> biome = level.getBiome(CURSOR.set(x, y, z));
        if (biome.is(Biomes.CRIMSON_FOREST)) return CRIMSON;
        if (biome.is(Biomes.SOUL_SAND_VALLEY)) return SOUL;
        if (biome.is(Biomes.WARPED_FOREST)) return WARPED;
        if (biome.is(Biomes.BASALT_DELTAS)) return BASALT;
        return WASTES;
    }

    public static void onNetherAirTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (!here(level) || player == null || !(FxConfig.netherRays || FxConfig.netherMist || FxConfig.netherStorms)) {
            floor = floorTarget = Float.NaN;
            return;
        }
        if (ticks++ % 20 == 0 || Float.isNaN(floorTarget)) floorTarget = sampleFloor(level, player.getX(), player.getEyeY(), player.getZ());
    }

    private static float sampleFloor(ClientLevel level, double x, double y, double z) {
        int count = 0;
        int top = Mth.floor(y);
        int bottom = Math.max(top - 64, level.getMinBuildHeight());
        for (int i = 0; i < RING.length; i++) {
            float a = i * Mth.TWO_PI / 6F + (RING[i] > 10 ? Mth.PI / 6F : 0F);
            int sx = Mth.floor(x + Mth.cos(a) * RING[i]);
            int sz = Mth.floor(z + Mth.sin(a) * RING[i]);
            if (!level.hasChunk(sx >> 4, sz >> 4)) continue;
            int sy = top;
            while (sy > bottom && level.getBlockState(CURSOR.set(sx, sy, sz)).isAir()) sy--;
            FLOORS[count++] = sy + 1;
        }
        if (count == 0) return (float) y - 24F;
        Arrays.sort(FLOORS, 0, count);
        return FLOORS[count / 4];
    }

    public static void onRenderNetherAirStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!here(level)) {
            Diagnostics.skip(Diagnostics.NETHER, "not in the Nether");
            frameAt = 0L;
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float dt = ease(level, camera);
        float storm = NetherDust.storm();
        boolean air = FxConfig.netherMist && FxConfig.netherMistDensity > 0.0 && !Float.isNaN(floor);
        if (!air || !DynamicFogHandler.active() || ColorGrade.shaderPackActive() || !ViewCapture.fresh) {
            if (!air) {
                if (!crustOn()) Diagnostics.skip(Diagnostics.NETHER, "mist and lava surface are off");
            } else {
                Diagnostics.skip(Diagnostics.NETHER, "fog handler off or a shader pack owns the image");
            }
            return;
        }
        if (SkyGate.air(event, Diagnostics.NETHER) == null) return;
        SkyProgram march = SkyProgram.NETHER_AIR;
        SkyProgram soften = SkyProgram.NETHER_SOFTEN;
        SkyProgram composite = SkyProgram.NETHER_GLOW;
        if (!march.ready() || !soften.ready() || !composite.ready()) {
            Diagnostics.skip(Diagnostics.NETHER, "shader failed to load, see the log");
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();

        float mist = (float) FxConfig.netherMistDensity * LOOK[7] * (1F - 0.65F * storm);
        driftX += NetherDust.windX() * (0.4F + 5.5F * storm) * dt;
        driftZ += NetherDust.windZ() * (0.4F + 5.5F * storm) * dt;

        int width = Math.max(main.width / 4, 1);
        int height = Math.max(main.height / 4, 1);
        glow = fit(glow, width, height);
        soft = fit(soft, width, height);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        march.begin();
        SkyProgram.texture(1, Haze.noiseTexture());
        SkyProgram.texture(0, main.getDepthTextureId());
        ViewCapture.INVERSE.get(MATRIX);
        GL20.glUniformMatrix4fv(march.uniform(0), false, MATRIX);
        GL20.glUniform4f(march.uniform(2), 0F, 0F, (float) camera.y, floor);
        GL20.glUniform4f(march.uniform(3), wrap((camera.x - driftX) * MIST_SCALE), wrap((camera.z - driftZ) * MIST_SCALE),
                (float) MIST_SCALE, mist * 0.02F);
        GL20.glUniform4f(march.uniform(4), 0F, 0.16F, Math.min(MARCH, ViewCapture.fogEnd), Math.max(ViewCapture.fogEnd, 8F));
        glow.bindWrite(true);
        march.draw(3);
        march.end();
        soften.begin();
        SkyProgram.texture(0, glow.getColorTextureId());
        soft.bindWrite(true);
        soften.draw(3);
        soften.end();

        main.bindWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR);
        composite.begin();
        SkyProgram.texture(0, soft.getColorTextureId());
        GL20.glUniform4f(composite.uniform(0), 0F, 0F, 0F, 1F);
        GL20.glUniform4f(composite.uniform(1), LOOK[4], LOOK[5], LOOK[6], 1F);
        GL20.glUniform4f(composite.uniform(2), 0F, 0F, 0F, 1F);
        composite.draw(3);
        composite.end();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        Diagnostics.mark(Diagnostics.NETHER, Diagnostics.detail()
                ? String.format("look %d, floor %.0f, mist %.2f, storm %.2f", look, floor, mist, storm) : null);
    }

    private static boolean crustOn() {
        return FxConfig.lavaCrust && FxConfig.lavaCrustAmount > 0.0;
    }

    public static void onRenderLavaSurfaceStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!here(level) || !crustOn()) return;
        if (!DynamicFogHandler.active() || ColorGrade.shaderPackActive() || !SkyProgram.LAVA_CRUST.ready()) {
            Diagnostics.skip(Diagnostics.NETHER, "fog handler off, a shader pack owns the image or the lava shader failed");
            return;
        }
        if (SkyGate.air(event, Diagnostics.NETHER) == null) return;
        SURFACE_VIEW.set(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
        SURFACE_VIEW.invert(SURFACE_INVERSE);
        crust(mc.getMainRenderTarget(), event.getCamera().getPosition(), event.getCamera().getLookVector(),
                (float) FxConfig.lavaCrustAmount * CRUST[look]);
        Diagnostics.mark(Diagnostics.NETHER, Diagnostics.detail() ? String.format("look %d, lava surface", look) : null);
    }

    private static void crust(RenderTarget main, Vec3 camera, Vector3f look, float amount) {
        int top = main.height;
        float flat = Mth.sqrt(look.x() * look.x() + look.z() * look.z());
        if (flat > 0.02F) {
            HORIZON.set(look.x() / flat * 1000F, 0F, look.z() / flat * 1000F, 1F);
            SURFACE_VIEW.transform(HORIZON);
            if (HORIZON.w > 0F) top = Mth.clamp((int) ((HORIZON.y / HORIZON.w * 0.5F + 0.5F) * main.height) + main.height / 16, 0, main.height);
        } else if (look.y() > 0F) {
            top = 0;
        }
        if (top <= 0) return;
        SkyProgram program = SkyProgram.LAVA_CRUST;
        main.bindWrite(true);
        RenderSystem.enableScissor(0, 0, main.width, top);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        program.begin();
        SkyProgram.texture(0, main.getDepthTextureId());
        SURFACE_INVERSE.get(MATRIX);
        GL20.glUniformMatrix4fv(program.uniform(0), false, MATRIX);
        double anchorX = Math.floor(camera.x), anchorZ = Math.floor(camera.z);
        GL20.glUniform4f(program.uniform(1), (float) (camera.x - anchorX), (float) (camera.z - anchorZ), (float) camera.y,
                Mth.clamp(0.5F * amount, 0F, 0.95F));
        float fogStart = RenderSystem.getShaderFogStart();
        GL20.glUniform4f(program.uniform(3), fogStart, Math.max(RenderSystem.getShaderFogEnd(), fogStart + 1F), lavaRed,
                (Util.getMillis() % 3_600_000L) / 1000F);
        GL20.glUniform4f(program.uniform(4), Math.floorMod((long) anchorX, 4096L), Math.floorMod((long) anchorZ, 4096L), 0F, 0F);
        program.draw(3);
        program.end();
        RenderSystem.disableScissor();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static float ease(ClientLevel level, Vec3 camera) {
        long now = Util.getNanos();
        float dt = frameAt == 0L ? 0F : Math.min((now - frameAt) / 1.0E9F, 0.25F);
        boolean snap = frameAt == 0L;
        frameAt = now;
        look = lookAt(level, camera.x, camera.y, camera.z);
        lavaRed += ((look == CRIMSON ? 1F : 0F) - lavaRed) * (snap ? 1F : 1F - (float) Math.exp(-dt * 0.8F));
        float[] target = LOOKS[look];
        float k = snap ? 1F : 1F - (float) Math.exp(-dt * 0.8F);
        for (int i = 0; i < LOOK.length; i++) LOOK[i] += (target[i] - LOOK[i]) * k;
        if (!Float.isNaN(floorTarget)) {
            floor = Float.isNaN(floor) || snap ? floorTarget : floor + (floorTarget - floor) * (1F - (float) Math.exp(-dt * 0.5F));
        }
        return dt;
    }

    private static float wrap(double v) {
        return (float) (v - Math.floor(v));
    }

    private static TextureTarget fit(TextureTarget target, int width, int height) {
        if (target == null) {
            target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        } else {
            return target;
        }
        target.setFilterMode(GL11.GL_LINEAR);
        return target;
    }
}
