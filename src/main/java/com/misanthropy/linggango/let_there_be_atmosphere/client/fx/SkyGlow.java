package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.material.FogType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL20;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class SkyGlow {
    private SkyGlow() {}

    private static final ResourceLocation BURST =
            new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/environment/sun_burst.png");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/environment/sky_glow.png");

    private static final float DISTANCE = 100F;
    private static final float SUN_SIZE = 30F;
    private static final float MOON_SIZE = 20F;
    private static final float GLOW_U = 0F;
    private static final float RAYS_U = 0.5F;
    private static final float RAY_SPIN = 0.0012F;
    private static final float VISIBLE = 0.004F;
    private static final int BURST_FRAMES = 8;
    private static final float BURST_SIZE = 84F;

    private static final Matrix4f MATRIX = new Matrix4f();
    static final Vector3f CENTER = new Vector3f();
    private static final Vector3f AXIS_U = new Vector3f(1F, 0F, 0F);
    private static final Vector3f AXIS_V = new Vector3f(0F, 1F, 0F);
    private static final float[] SUNSET = new float[4];
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final float[] QUAD_CENTER = new float[24];
    private static final float[] QUAD_SIZE = new float[24];
    private static final float[] QUAD_TINT = new float[24];
    static int quads;
    private static float burst;

    public static void onRenderSkyStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (!DynamicFogHandler.active() || (!FxConfig.skyGlowEnabled && !FxConfig.sunBurst)) {
            Diagnostics.skip(Diagnostics.GLOW, "disabled");
            return;
        }
        ClientLevel level = SkyGate.sky(event, Diagnostics.GLOW, true);
        if (level == null) return;

        float partialTick = event.getPartialTick();
        float clear = (1F - level.getRainLevel(partialTick)) * open(level, event.getCamera().getPosition());
        if (clear < VISIBLE) {
            Diagnostics.skip(Diagnostics.GLOW, "the sky is covered");
            return;
        }

        float angle = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        float elevation = Mth.cos(angle);
        float dayTime = level.getDayTime() % DayCurves.PERIOD + partialTick;
        float rise = clear * smooth(-0.12F, 0.08F, elevation);
        float sun = FxConfig.skyGlowEnabled ? rise * (float) FxConfig.sunGlowStrength : 0F;
        burst = FxConfig.sunBurst ? rise * (float) FxConfig.sunBurstStrength : 0F;
        float night = Mth.clamp(DayCurves.starBrightness(dayTime) * 2F, 0F, 1F);
        float moon = clear * smooth(-0.12F, 0.08F, -elevation) * night * level.getMoonBrightness()
                * (float) FxConfig.moonGlowStrength;
        if (!FxConfig.skyGlowEnabled && !StarSky.on()) moon = 0F;
        if (sun < VISIBLE && moon < VISIBLE && burst < VISIBLE) {
            Diagnostics.skip(Diagnostics.GLOW, "sun and moon are down");
            return;
        }

        MATRIX.set(event.getPoseStack().last().pose()).rotateY(-Mth.HALF_PI).rotateX(angle);
        MATRIX.transformDirection(1F, 0F, 0F, AXIS_U);
        MATRIX.transformDirection(0F, 0F, 1F, AXIS_V);
        draw(sun, moon, dayTime, level.getGameTime(), partialTick);
        AXIS_U.set(1F, 0F, 0F);
        AXIS_V.set(0F, 1F, 0F);

        Diagnostics.mark(Diagnostics.GLOW, Diagnostics.detail()
                ? String.format("sun %.2f, moon %.2f", sun, moon) : null);
    }

    private static void draw(float sun, float moon, float dayTime, long gameTime, float partialTick) {
        quads = 0;
        if (sun >= VISIBLE) {
            quads = 1;
            MATRIX.transformPosition(0F, DISTANCE, 0F, CENTER);
            float warm = DayCurves.sunriseSunset(dayTime, SUNSET) ? SUNSET[3] : 0F;
            float r = Mth.lerp(warm, 1.0F, SUNSET[0]);
            float g = Mth.lerp(warm, 0.95F, SUNSET[1]);
            float b = Mth.lerp(warm, 0.86F, SUNSET[2]);
            float spread = SUN_SIZE * (1F + warm * 0.6F);
            float spin = (gameTime + partialTick) * RAY_SPIN;
            add(spread * 3.2F, spread * 3.2F, 1F, 0F, GLOW_U, r, g, b, 0.6F * sun);
            add(spread * 9F, spread * 9F, 1F, 0F, GLOW_U, r, g, b, 0.2F * sun);
            add(spread * 14F, spread * 0.8F, 1F, 0F, GLOW_U, r, g, b, 0.22F * sun);
            add(spread * 7F, spread * 7F, Mth.cos(spin), Mth.sin(spin), RAYS_U, r, g, b, 0.16F * sun);
            float reachX = 0F, reachY = 0F;
            for (int i = 1; i < quads; i++) {
                int o = i * 4;
                float c = Math.abs(QUAD_SIZE[o + 2]), s = Math.abs(QUAD_SIZE[o + 3]);
                reachX = Math.max(reachX, QUAD_SIZE[o] * c + QUAD_SIZE[o + 1] * s);
                reachY = Math.max(reachY, QUAD_SIZE[o] * s + QUAD_SIZE[o + 1] * c);
            }
            QUAD_CENTER[0] = CENTER.x;
            QUAD_CENTER[1] = CENTER.y;
            QUAD_CENTER[2] = CENTER.z;
            QUAD_CENTER[3] = 0F;
            QUAD_SIZE[0] = reachX;
            QUAD_SIZE[1] = reachY;
            QUAD_SIZE[2] = 1F;
            QUAD_SIZE[3] = 0F;
            QUAD_TINT[0] = QUAD_TINT[1] = QUAD_TINT[2] = QUAD_TINT[3] = 1F;
            draw(TEXTURE, -1F, true);
            quads = 0;
        }
        if (moon >= VISIBLE) {
            MATRIX.transformPosition(0F, -DISTANCE, 0F, CENTER);
            add(MOON_SIZE * 3.5F, MOON_SIZE * 3.5F, 1F, 0F, GLOW_U, 0.74F, 0.82F, 1F, 0.35F * moon);
            add(MOON_SIZE * 8F, MOON_SIZE * 8F, 1F, 0F, GLOW_U, 0.74F, 0.82F, 1F, 0.1F * moon);
        }
        flush(TEXTURE);
        if (burst >= VISIBLE) {
            quads = 0;
            MATRIX.transformPosition(0F, DISTANCE, 0F, CENTER);
            float warm = DayCurves.sunriseSunset(dayTime, SUNSET) ? SUNSET[3] : 0F;
            float size = BURST_SIZE * Mth.clamp((float) FxConfig.sunScale, 0.1F, 5F);
            int frame = (int) (((gameTime % 24000L) + partialTick) / 3F) % BURST_FRAMES;
            add(size, size, 1F, 0F, frame / (float) BURST_FRAMES, 1F, Mth.lerp(warm, 0.97F, 0.86F),
                    Mth.lerp(warm, 0.9F, 0.66F), burst, 2F / BURST_FRAMES);
            flush(BURST, burst);
        }
    }

    static void flush() {
        flush(TEXTURE);
    }

    static void flush(ResourceLocation texture) {
        flush(texture, -1F);
    }

    static void flush(ResourceLocation texture, float opacity) {
        draw(texture, opacity, false);
    }

    private static void draw(ResourceLocation texture, float opacity, boolean layered) {
        SkyProgram program = SkyProgram.GLOW;
        if (quads == 0 || !program.ready()) return;

        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.enableBlend();
        if (layered) {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        } else if (opacity >= 0F) {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        }
        RenderSystem.depthMask(false);
        program.begin();
        SkyProgram.texture(0, SkyProgram.texture(texture));
        program.transform(0, IDENTITY);
        GL20.glUniform4f(program.uniform(4), opacity >= 0F ? 1F : 0F, Math.max(opacity, 0F), layered ? 1F : 0F, 0F);
        GL20.glUniform4fv(program.uniform(1), QUAD_CENTER);
        GL20.glUniform4fv(program.uniform(2), QUAD_SIZE);
        GL20.glUniform4fv(program.uniform(3), QUAD_TINT);
        GL20.glUniform3f(program.uniform(5), AXIS_U.x, AXIS_U.y, AXIS_U.z);
        GL20.glUniform3f(program.uniform(6), AXIS_V.x, AXIS_V.y, AXIS_V.z);
        program.draw(layered ? 6 : quads * 6);
        program.end();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    static void add(float width, float height, float cos, float sin, float u,
                            float r, float g, float b, float a) {
        add(width, height, cos, sin, u, r, g, b, a, 1F);
    }

    static void add(float width, float height, float cos, float sin, float u,
                    float r, float g, float b, float a, float span) {
        int o = quads * 4;
        QUAD_CENTER[o] = CENTER.x;
        QUAD_CENTER[o + 1] = CENTER.y;
        QUAD_CENTER[o + 2] = CENTER.z;
        QUAD_CENTER[o + 3] = u;
        QUAD_SIZE[o] = width * 0.5F;
        QUAD_SIZE[o + 1] = height * 0.5F;
        QUAD_SIZE[o + 2] = cos;
        QUAD_SIZE[o + 3] = sin;
        QUAD_TINT[o] = byteOf(r * a);
        QUAD_TINT[o + 1] = byteOf(g * a);
        QUAD_TINT[o + 2] = byteOf(b * a);
        QUAD_TINT[o + 3] = span;
        quads++;
    }

    private static float byteOf(float value) {
        return (int) (value * 255F) / 255F;
    }

    public static boolean replacesSun() {
        if (!DynamicFogHandler.active() || !FxConfig.sunBurst || FxConfig.sunBurstStrength <= 0.0 || !SkyProgram.GLOW.ready()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (mc.player.hasEffect(MobEffects.BLINDNESS) || mc.player.hasEffect(MobEffects.DARKNESS))) {
            return false;
        }
        return CameraFluid.of(mc.gameRenderer.getMainCamera()) == FogType.NONE;
    }

    static float open(ClientLevel level, Vec3 eye) {
        return Math.max(DynamicFogHandler.skyVisibility(), smooth(4F, 12F, level.getBrightness(LightLayer.SKY, BlockPos.containing(eye))));
    }

    static float smooth(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0F, 1F);
        return t * t * (3F - 2F * t);
    }
}
