package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FogType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class SunPass {
    private SunPass() {}

    private static final ResourceLocation CLOUDS = new ResourceLocation("textures/environment/clouds.png");
    private static final Vector4f SUN = new Vector4f();
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final float[] STORY = new float[5];
    private static final BlockPos.MutableBlockPos SURFACE = new BlockPos.MutableBlockPos();
    static final float[] RAYS = new float[3];

    private static TextureTarget mask;
    private static TextureTarget streak;
    private static TextureTarget rays;
    private static int maskReady;
    private static int raysReady;
    static boolean shadowsReady;
    static boolean hazeReady;
    static boolean mistReady;
    static boolean puddleReady;
    static final float[] PUDDLE = new float[3];
    private static int sampler = -1;
    private static float exposure;
    private static long exposureAt;

    static int takeMask() {
        int texture = maskReady;
        maskReady = 0;
        return texture;
    }

    static int takeRays() {
        int texture = raysReady;
        raysReady = 0;
        return texture;
    }

    public static void onRenderSunPassStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        maskReady = 0;
        raysReady = 0;
        shadowsReady = false;
        hazeReady = false;
        mistReady = false;
        puddleReady = false;
        if (!ViewCapture.fresh) {
            skipAll("disabled");
            return;
        }
        ClientLevel water = Minecraft.getInstance().level;
        if (water != null && CameraFluid.of(event.getCamera()) == FogType.WATER) {
            skipAll("the camera is under water");
            underwater(event, water);
            return;
        }
        Diagnostics.skip(Diagnostics.WATER_RAYS, "the camera is not under water");
        ClientLevel level = SkyGate.air(event, Diagnostics.HAZE);
        if (level == null) {
            skipAll("camera in a fluid, blind, or no level");
            return;
        }
        if (!DynamicFogHandler.active() || ColorGrade.shaderPackActive()) {
            skipAll("fog handler off or a shader pack owns the image");
            return;
        }
        SkyProgram maskProgram = SkyProgram.SUN_MASK;
        SkyProgram blurProgram = SkyProgram.RAYS_BLUR;
        if (!maskProgram.ready() || !blurProgram.ready()) {
            skipAll("shader failed to load, see the log");
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        float partialTick = event.getPartialTick();
        Vec3 camera = event.getCamera().getPosition();
        float angle = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        float sunX = -Mth.sin(angle);
        float sunY = Mth.cos(angle);
        float clear = 1F - level.getRainLevel(partialTick);
        boolean sky = level.dimensionType().hasSkyLight() && level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL;
        if (!sky) {
            Diagnostics.skip(Diagnostics.SHADOWS, "this dimension has no normal sky");
            Diagnostics.skip(Diagnostics.RAYS, "this dimension has no normal sky");
        }
        float shadow = sky ? shadowStrength(level, mc, camera, sunY, clear) : 0F;
        float ray = sky ? rayStrength(sunX, sunY, clear) : 0F;
        Haze.update();
        boolean haze = Haze.wanted() && Haze.mirageAmp() + Haze.nearAmp() + Haze.coldAmp() > 1.0E-5F;
        if (!haze) Diagnostics.skip(Diagnostics.HAZE, FxConfig.heatHaze || FxConfig.coldAir ? "not hot or cold here" : "disabled");
        Mist.update(level, partialTick);
        boolean mist = sky && Mist.wanted();
        if (!mist) Diagnostics.skip(Diagnostics.GROUND_MIST, FxConfig.groundMist ? "no mist right now" : "disabled");
        boolean puddle = sky && Puddles.wanted();
        if (!puddle) Diagnostics.skip(Diagnostics.PUDDLES, FxConfig.puddles ? "the ground is dry" : "disabled");
        float damp = sky && FxConfig.wetSurfaces && Puddles.heightsLive() ? Puddles.damp() * (float) FxConfig.wetSurfacesStrength : 0F;
        boolean wet = damp > 0.02F;
        if (shadow <= 0F && ray <= 0F && !haze && !mist && !puddle && !wet && !(sky && StarSky.on())) return;

        RenderTarget main = mc.getMainRenderTarget();
        mask = fit(mask, Math.max(main.width / 2, 1), Math.max(main.height / 2, 1), false);
        float aspect = (float) main.width / Math.max(main.height, 1);
        float u = ray > 0F ? SUN.x / SUN.w * 0.5F + 0.5F : 0.5F;
        float v = ray > 0F ? SUN.y / SUN.w * 0.5F + 0.5F : 0.5F;

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();

        maskProgram.begin();
        boolean story = StoryCloudConfig.enabled && StoryClouds.shadowFrame(level, partialTick, camera.x, camera.y, camera.z, STORY);
        int filter = story ? 0 : sampler();
        SkyProgram.texture(1, story ? StoryClouds.shadowTexture() : SkyProgram.texture(CLOUDS));
        if (filter != 0) GL33.glBindSampler(1, filter);
        SkyProgram.texture(0, main.getDepthTextureId());
        ViewCapture.INVERSE.get(MATRIX);
        GL20.glUniformMatrix4fv(maskProgram.uniform(0), false, MATRIX);
        double drift = (event.getRenderTick() + partialTick) * 0.03F;
        double cloudU = (camera.x + drift) / 12.0;
        double cloudV = camera.z / 12.0 + 0.33F;
        cloudU -= Math.floor(cloudU / 256.0) * 256.0;
        cloudV -= Math.floor(cloudV / 256.0) * 256.0;
        float cloudHeight = level.effects().getCloudHeight();
        if (story) {
            GL20.glUniform4f(maskProgram.uniform(1), STORY[0], STORY[1], STORY[2], shadow);
            GL20.glUniform2f(maskProgram.uniform(10), STORY[3], STORY[4]);
        } else {
            GL20.glUniform4f(maskProgram.uniform(1), (float) cloudU, (float) cloudV,
                    Float.isNaN(cloudHeight) ? 0F : cloudHeight + 2.33F - (float) camera.y, shadow);
            GL20.glUniform2f(maskProgram.uniform(10), 12F, 256F);
        }
        GL20.glUniform4f(maskProgram.uniform(2), sunX, Math.max(sunY, 0.2F), ViewCapture.fogStart, ViewCapture.fogEnd);
        GL20.glUniform4f(maskProgram.uniform(3), u, v, ray > 0F ? aspect : 0F, (float) FxConfig.cloudShadowSoftness * 0.5F);
        if (mist || puddle) SkyProgram.texture(2, Haze.noiseTexture());
        boolean heights = Puddles.heightsLive() && SkyHeights.texture() != 0;
        if (heights) SkyProgram.texture(3, SkyHeights.texture());
        double blockX = Math.floor(camera.x);
        double blockZ = Math.floor(camera.z);
        GL20.glUniform4f(maskProgram.uniform(6), (float) (camera.x - blockX), (float) (camera.z - blockZ), (float) camera.y, heights ? 1F : 0F);
        GL20.glUniform4f(maskProgram.uniform(7), (float) blockX, (float) blockZ, CaveMotes.now(), Puddles.raining());
        GL20.glUniform2f(maskProgram.uniform(9), SkyHeights.centerX(), SkyHeights.centerZ());
        if (puddle) {
            double scale = 1.0 / 18.0;
            double puddleU = blockX * scale, puddleV = blockZ * scale;
            float threshold = 0.9F - 0.2F * Puddles.wetness() * (float) Math.min(FxConfig.puddleAmount, 2.0);
            GL20.glUniform4f(maskProgram.uniform(8), (float) (puddleU - Math.floor(puddleU)), (float) (puddleV - Math.floor(puddleV)), (float) scale, threshold);
        } else {
            GL20.glUniform4f(maskProgram.uniform(8), 0F, 0F, 0F, 0F);
        }
        GL20.glUniform4f(maskProgram.uniform(12), wet ? 1F : 0F, Math.min(0.24F * damp, 0.45F), Math.min(0.12F * damp, 0.3F), 0F);
        if (mist) {
            GL20.glUniform4f(maskProgram.uniform(4), Mist.top(camera.y), Mist.density(), Mist.falloff(), 0.75F);
            GL20.glUniform4f(maskProgram.uniform(5), Mist.noiseU(camera.x), Mist.noiseV(camera.z), Mist.noiseScale(), 0F);
        } else {
            GL20.glUniform4f(maskProgram.uniform(4), 0F, 0F, 1F, 0F);
        }
        int cover = CloudBlur.takeCover();
        if (cover != 0) SkyProgram.texture(4, cover);
        GL20.glUniform1f(maskProgram.uniform(11), cover != 0 ? 1F : 0F);
        mask.bindWrite(true);
        maskProgram.draw(3);
        if (filter != 0) GL33.glBindSampler(1, 0);
        maskProgram.end();
        maskReady = mask.getColorTextureId();
        shadowsReady = shadow > 0F;
        hazeReady = haze;
        mistReady = mist;
        puddleReady = puddle || wet;
        if (puddle || wet) {
            float fogR = DynamicFogHandler.fogColorR(), fogG = DynamicFogHandler.fogColorG(), fogB = DynamicFogHandler.fogColorB();
            Vec3 skyColor = level.getSkyColor(camera, partialTick);
            PUDDLE[0] = Mth.lerp(0.5F, fogR, (float) skyColor.x);
            PUDDLE[1] = Mth.lerp(0.5F, fogG, (float) skyColor.y);
            PUDDLE[2] = Mth.lerp(0.5F, fogB, (float) skyColor.z);
        }
        if (puddle) {
            Diagnostics.mark(Diagnostics.PUDDLES, Diagnostics.detail()
                    ? String.format("wetness %.2f, rain %.2f, damp %.2f", Puddles.wetness(), Puddles.raining(), damp) : null);
        }

        if (ray > 0F) {
            int width = Math.max(main.width / 4, 1);
            int height = Math.max(main.height / 4, 1);
            streak = fit(streak, width, height, true);
            rays = fit(rays, width, height, true);
            blurProgram.begin();
            SkyProgram.texture(0, mask.getColorTextureId());
            GL20.glUniform4f(blurProgram.uniform(0), u, v, 1F, 0.94F);
            GL20.glUniform2f(blurProgram.uniform(1), aspect, 0F);
            streak.bindWrite(true);
            blurProgram.draw(3);
            SkyProgram.texture(0, streak.getColorTextureId());
            GL20.glUniform4f(blurProgram.uniform(0), u, v, 1F / 12F, 1F);
            GL20.glUniform2f(blurProgram.uniform(1), aspect, Mth.lerp((float) FxConfig.godRayLength, 8F, 0.6F));
            rays.bindWrite(true);
            blurProgram.draw(3);
            blurProgram.end();
            float warm = SkyGlow.smooth(0.0F, 0.45F, sunY);
            RAYS[0] = ray;
            RAYS[1] = ray * Mth.lerp(warm, 0.62F, 0.93F);
            RAYS[2] = ray * Mth.lerp(warm, 0.32F, 0.78F);
            raysReady = rays.getColorTextureId();
        }

        main.bindWrite(true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        if (shadow > 0F) Diagnostics.mark(Diagnostics.SHADOWS, Diagnostics.detail()
                ? String.format("strength %.2f, out to %.0f blocks", shadow, ViewCapture.fogEnd) : null);
        if (ray > 0F) Diagnostics.mark(Diagnostics.RAYS, Diagnostics.detail()
                ? String.format("strength %.2f, sun at %.2f %.2f", ray, u, v) : null);
        if (mist) Diagnostics.mark(Diagnostics.GROUND_MIST, Diagnostics.detail()
                ? String.format("amount %.2f, floor %.1f above camera", Mist.amount(), Mist.top(camera.y)) : null);
        if (haze) Diagnostics.mark(Diagnostics.HAZE, Diagnostics.detail()
                ? String.format("mirage %.4f, near %.4f, cold %.4f", Haze.mirageAmp(), Haze.nearAmp(), Haze.coldAmp()) : null);
    }

    private static void underwater(RenderLevelStageEvent event, ClientLevel level) {
        boolean wantRays = FxConfig.waterRays && FxConfig.waterRaysStrength > 0.0;
        boolean wantCaustics = FxConfig.caustics && FxConfig.causticsStrength > 0.0;
        if (!(wantRays || wantCaustics) || !DynamicFogHandler.active()
                || !level.dimensionType().hasSkyLight() || ColorGrade.shaderPackActive()) {
            Diagnostics.skip(Diagnostics.WATER_RAYS, "disabled, no sky here, or a shader pack owns the image");
            Diagnostics.skip(Diagnostics.CAUSTICS, "disabled, no sky here, or a shader pack owns the image");
            return;
        }
        float partialTick = event.getPartialTick();
        float angle = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        float sunX = -Mth.sin(angle);
        float sunY = Mth.cos(angle);
        float light = SkyGlow.smooth(0.0F, 0.3F, sunY) * (1F - 0.6F * level.getRainLevel(partialTick));
        Vec3 camera = event.getCamera().getPosition();
        SURFACE.set(Mth.floor(camera.x), Mth.floor(camera.y), Mth.floor(camera.z));
        int climb = 0;
        while (climb < 48 && level.getFluidState(SURFACE).is(FluidTags.WATER)) {
            SURFACE.move(Direction.UP);
            climb++;
        }
        light *= SkyGlow.smooth(4F, 12F, level.getBrightness(LightLayer.SKY, SURFACE));
        if (climb >= 48 || light < 0.004F) {
            Diagnostics.skip(Diagnostics.WATER_RAYS, "no sunlit open water surface above");
            Diagnostics.skip(Diagnostics.CAUSTICS, "no sunlit open water surface above");
            return;
        }
        float slant = Mth.clamp(sunX / Math.max(sunY, 0.2F), -2F, 2F) * 0.6F;
        float top = SURFACE.getY() - 0.12F - (float) camera.y;
        if (wantCaustics) {
            Caustics.draw(camera, top, slant, light * (float) FxConfig.causticsStrength);
        } else {
            Diagnostics.skip(Diagnostics.CAUSTICS, "disabled");
        }
        float strength = light * (float) FxConfig.waterRaysStrength;
        if (!wantRays || strength < 0.004F) {
            Diagnostics.skip(Diagnostics.WATER_RAYS, "disabled");
            return;
        }
        SkyProgram program = SkyProgram.WATER_RAYS;
        SkyProgram soften = SkyProgram.WATER_SOFTEN;
        if (!program.ready() || !soften.ready()) {
            Diagnostics.skip(Diagnostics.WATER_RAYS, "shader failed to load, see the log");
            return;
        }

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        rays = fit(rays, Math.max(main.width / 4, 1), Math.max(main.height / 4, 1), true);
        streak = fit(streak, Math.max(main.width / 4, 1), Math.max(main.height / 4, 1), true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        program.begin();
        SkyProgram.texture(1, Haze.noiseTexture());
        SkyProgram.texture(0, main.getDepthTextureId());
        ViewCapture.INVERSE.get(MATRIX);
        GL20.glUniformMatrix4fv(program.uniform(0), false, MATRIX);
        GL20.glUniform4f(program.uniform(1), top, slant, 0F, 32F);
        double scale = 1.0 / 48.0;
        double u = camera.x * scale;
        double v = camera.z * scale;
        GL20.glUniform4f(program.uniform(2), (float) (u - Math.floor(u)), (float) (v - Math.floor(v)), (float) scale, CaveMotes.now());
        streak.bindWrite(true);
        program.draw(3);
        program.end();
        soften.begin();
        SkyProgram.texture(0, streak.getColorTextureId());
        rays.bindWrite(true);
        soften.draw(3);
        soften.end();
        main.bindWrite(true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        RAYS[0] = 0.55F * strength * 1.6F;
        RAYS[1] = 0.88F * strength * 1.6F;
        RAYS[2] = 1.0F * strength * 1.6F;
        raysReady = rays.getColorTextureId();
        Diagnostics.mark(Diagnostics.WATER_RAYS, Diagnostics.detail()
                ? String.format("strength %.2f, surface %d blocks up", strength, climb) : null);
    }

    private static void skipAll(String reason) {
        Diagnostics.skip(Diagnostics.PUDDLES, reason);
        Diagnostics.skip(Diagnostics.GROUND_MIST, reason);
        Diagnostics.skip(Diagnostics.SHADOWS, reason);
        Diagnostics.skip(Diagnostics.RAYS, reason);
        Diagnostics.skip(Diagnostics.HAZE, reason);
    }

    private static float shadowStrength(ClientLevel level, Minecraft mc, Vec3 camera, float sunY, float clear) {
        if (!FxConfig.cloudShadows || FxConfig.cloudShadowStrength <= 0.0) {
            Diagnostics.skip(Diagnostics.SHADOWS, "disabled");
            return 0F;
        }
        if (Float.isNaN(level.effects().getCloudHeight()) || mc.options.getCloudsType() == CloudStatus.OFF) {
            Diagnostics.skip(Diagnostics.SHADOWS, "no clouds here");
            return 0F;
        }
        if (StoryCloudConfig.enabled && StoryClouds.shadowTexture() == 0) {
            Diagnostics.skip(Diagnostics.SHADOWS, "storybook clouds still building");
            return 0F;
        }
        float strength = (float) FxConfig.cloudShadowStrength * SkyGlow.smooth(0.05F, 0.3F, sunY) * clear
                * exposure(level, camera) * (StoryCloudConfig.enabled && StoryCloudConfig.pastel ? 0.55F : 1F);
        if (strength < 0.004F || ViewCapture.fogEnd <= ViewCapture.fogStart) {
            Diagnostics.skip(Diagnostics.SHADOWS, "too faint right now");
            return 0F;
        }
        return strength;
    }

    private static float rayStrength(float sunX, float sunY, float clear) {
        if (!FxConfig.godRays || FxConfig.godRayStrength <= 0.0) {
            Diagnostics.skip(Diagnostics.RAYS, "disabled");
            return 0F;
        }
        ViewCapture.VIEW_PROJECTION.transform(SUN.set(sunX, sunY, 0F, 0F));
        if (SUN.w <= 0.05F) {
            Diagnostics.skip(Diagnostics.RAYS, "the sun is behind you");
            return 0F;
        }
        float outside = Math.max(Math.abs(SUN.x / SUN.w), Math.abs(SUN.y / SUN.w)) * 0.5F - 0.5F;
        float strength = (float) FxConfig.godRayStrength * 1.5F * SkyGlow.smooth(-0.02F, 0.12F, sunY)
                * (1F - SkyGlow.smooth(0F, 0.6F, outside)) * clear * DynamicFogHandler.skyVisibility();
        if (strength < 0.004F) {
            Diagnostics.skip(Diagnostics.RAYS, "too faint right now");
            return 0F;
        }
        return strength;
    }

    private static float exposure(ClientLevel level, Vec3 camera) {
        float target = SkyGlow.smooth(4F, 12F, level.getBrightness(LightLayer.SKY, BlockPos.containing(camera)));
        long now = Util.getNanos();
        float dt = exposureAt == 0L ? 1F : Math.min((now - exposureAt) / 1.0E9F, 1F);
        exposureAt = now;
        exposure += (target - exposure) * (1F - (float) Math.exp(-dt * 1.5F));
        return exposure;
    }

    private static int sampler() {
        if (sampler < 0) {
            GLCapabilities caps = GL.getCapabilities();
            sampler = 0;
            if (caps.OpenGL33 || caps.GL_ARB_sampler_objects) {
                sampler = GL33.glGenSamplers();
                GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
                GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            }
            Diagnostics.once(sampler != 0 ? "cloud shadows sample through a linear sampler" : "cloud shadows sample the cloud texture as is");
        }
        return sampler;
    }

    private static TextureTarget fit(TextureTarget target, int width, int height, boolean single) {
        if (target == null) {
            target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        } else {
            return target;
        }
        target.setFilterMode(GL11.GL_LINEAR);
        if (single) {
            GlStateManager._bindTexture(target.getColorTextureId());
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, width, height, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, null);
        }
        return target;
    }
}
