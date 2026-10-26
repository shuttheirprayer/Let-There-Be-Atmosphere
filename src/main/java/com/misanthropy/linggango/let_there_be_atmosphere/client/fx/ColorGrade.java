package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.CaptureHook;
import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere.RenderTargetColorAccessor;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import java.lang.reflect.Method;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.ARBTextureBarrier;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.NVTextureBarrier;

@OnlyIn(Dist.CLIENT)
public final class ColorGrade {
    private ColorGrade() {}

    private static final long SHADER_PACK_POLL = 1000L;
    private static final ResourceLocation SHADER = new ResourceLocation(Let_there_be_atmosphere.MODID, "color_grade");
    private static final ResourceLocation DOWN = new ResourceLocation(Let_there_be_atmosphere.MODID, "bloom_down");
    private static final ResourceLocation GATHER = new ResourceLocation(Let_there_be_atmosphere.MODID, "bloom_gather");
    private static final int BLOOM_LEVELS = 4;
    private static final float BLOOM_KNEE = 0.2F;

    private static ShaderInstance shader;
    private static int gradeUniform;
    private static int skyGradeUniform;
    private static int tintUniform;
    private static int vignetteUniform;
    private static int strengthUniform;
    private static int raysUniform;
    private static int shadowUniform;
    private static int hazeUniform;
    private static int mistUniform;
    private static int puddleUniform;
    private static int viewUniform;
    private static int viewTanUniform;
    private static int flareUniform;
    private static ShaderInstance down;
    private static int downThreshold;
    private static ShaderInstance gather;
    private static int gatherWeights;
    private static TextureTarget copy;
    private static final TextureTarget[] mips = new TextureTarget[BLOOM_LEVELS];
    private static int vao;
    private static int barrier = -1;
    private static int sceneSampler = -1;
    private static boolean loadFailed;
    private static float dim;
    private static long dimAt;

    private static boolean irisResolved;
    private static Object irisApi;
    private static Method irisInUse;
    private static boolean shaderPack;
    private static long shaderPackCheckedAt;
    private static boolean shaderPackChecked;

    public static void apply() {
        boolean gradeOn = FxConfig.gradeEnabled && DynamicFogHandler.active();
        boolean bloomOn = FxConfig.bloomEnabled && FxConfig.bloomStrength > 0.0 && DynamicFogHandler.active();
        if (!gradeOn) Diagnostics.skip(Diagnostics.GRADE, "disabled");
        if (!bloomOn) Diagnostics.skip(Diagnostics.BLOOM, "disabled");
        int sun = SunPass.takeMask();
        int rays = SunPass.takeRays();
        boolean shadows = sun != 0 && (SunPass.shadowsReady || SunPass.mistReady || SunPass.puddleReady);
        boolean haze = sun != 0 && SunPass.hazeReady;
        boolean mirror = sun != 0 && SunPass.puddleReady;
        if (!gradeOn && !bloomOn && sun == 0 && rays == 0) return;
        if ((shader == null || down == null || gather == null) && !loadFailed) {
            load();
        }
        if (shader == null || down == null || gather == null) {
            Diagnostics.skip(Diagnostics.GRADE, "shader failed to load, see the log");
            Diagnostics.skip(Diagnostics.BLOOM, "shader failed to load, see the log");
            return;
        }
        if (shaderPackActive()) {
            Diagnostics.skip(Diagnostics.GRADE, "a shader pack grades the image");
            Diagnostics.skip(Diagnostics.BLOOM, "a shader pack owns the image");
            return;
        }

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int width = main.width;
        int height = main.height;
        boolean inPlace = inPlace() && !haze && !mirror;
        int scene = main.getColorTextureId();

        if (!inPlace) {
            copy = fit(copy, width, height, false);
            if (copy.filterMode != main.filterMode) copy.setFilterMode(main.filterMode);
            if (haze || mirror) {
                swap(main, copy);
            } else {
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
                GlStateManager._glBlitFrameBuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            }
            scene = copy.getColorTextureId();
        }

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        if (vao == 0) vao = GlStateManager._glGenVertexArrays();
        GlStateManager._glBindVertexArray(vao);
        CaptureHook.probeBegin("bloom");
        int glow = bloomOn ? bloom(scene, main.filterMode, width, height) : 0;
        CaptureHook.probeEnd("bloom");

        main.bindWrite(true);
        ProgramManager.glUseProgram(shader.getId());
        texture(5, haze ? Haze.noiseTexture() : 0);
        texture(4, shadows || haze || glow != 0 ? main.getDepthTextureId() : 0);
        texture(3, sun);
        texture(2, rays);
        texture(1, glow);
        texture(0, scene);
        GL20.glUniform1f(strengthUniform, glow != 0 ? (float) (FxConfig.bloomStrength * (1.0 + FxConfig.bloomSpread)) : 0F);
        if (rays != 0) {
            GL20.glUniform3f(raysUniform, SunPass.RAYS[0], SunPass.RAYS[1], SunPass.RAYS[2]);
        } else {
            GL20.glUniform3f(raysUniform, 0F, 0F, 0F);
        }
        GL20.glUniform1f(shadowUniform, shadows ? 1F : 0F);
        if (haze) {
            GL20.glUniform4f(hazeUniform, Haze.mirageAmp(), Haze.nearAmp(), Haze.coldAmp(), Haze.seconds());
        } else {
            GL20.glUniform4f(hazeUniform, 0F, 0F, 0F, 0F);
        }
        if (sun != 0 && SunPass.puddleReady) {
            GL20.glUniform4f(puddleUniform, SunPass.PUDDLE[0], SunPass.PUDDLE[1], SunPass.PUDDLE[2], 1F);
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vector3f look = camera.getLookVector();
            Vector3f left = camera.getLeftVector();
            Vector3f up = camera.getUpVector();
            float tanX = 1F / ViewCapture.PROJECTION.m00();
            float tanY = 1F / ViewCapture.PROJECTION.m11();
            GL20.glUniform4f(viewUniform, look.y(), -left.y() * tanX, up.y() * tanY, tanX);
            GL20.glUniform4f(viewTanUniform, tanY, CaveMotes.now(), Puddles.raining(), 0F);
        } else {
            GL20.glUniform4f(puddleUniform, 0F, 0F, 0F, 0F);
        }
        GL20.glUniform4f(flareUniform, SunPass.FLARE[0], SunPass.FLARE[1], sun != 0 ? SunPass.FLARE[2] : 0F, 0F);
        if (sun != 0 && SunPass.mistReady) {
            GL20.glUniform4f(mistUniform, Mist.COLOR[0], Mist.COLOR[1], Mist.COLOR[2], 1F);
        } else {
            GL20.glUniform4f(mistUniform, 0F, 0F, 0F, 0F);
        }
        float aspect = height > 0 ? (float) width / height : 1F;
        if (gradeOn) {
            float warmth = (float) FxConfig.gradeWarmth;
            float dark = dimness();
            float saturation = (float) FxConfig.gradeSaturation;
            float dimSaturation = Math.min(saturation, 1F - (float) FxConfig.gradeDimFade);
            GL20.glUniform4f(gradeUniform, Mth.lerp(dark, saturation, dimSaturation), (float) FxConfig.gradeContrast,
                    (float) FxConfig.gradeDarkDesaturation * dark, (float) FxConfig.gradeVibrance * (1F - dark));
            GL20.glUniform4f(skyGradeUniform, saturation, 0F, 0F, 0F);
            GL20.glUniform3f(tintUniform, 1F + 0.06F * warmth, 1F + 0.012F * warmth, 1F - 0.06F * warmth);
            GL20.glUniform3f(vignetteUniform, Math.min((float) (FxConfig.gradeVignette + FxConfig.gradeDimVignette * dark), 1F),
                    aspect, (float) FxConfig.gradeDimVignette * dark);
        } else {
            GL20.glUniform4f(gradeUniform, 1F, 0F, 0F, 0F);
            GL20.glUniform4f(skyGradeUniform, 1F, 0F, 0F, 0F);
            GL20.glUniform3f(tintUniform, 1F, 1F, 1F);
            GL20.glUniform3f(vignetteUniform, 0F, aspect, 0F);
        }
        if (inPlace) barrier();
        CaptureHook.probeBegin("grade");
        int linear = haze || mirror ? sceneSampler() : 0;
        if (linear != 0) GL33.glBindSampler(0, linear);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        if (linear != 0) GL33.glBindSampler(0, 0);
        CaptureHook.probeEnd("grade");

        texture(5, 0);
        texture(4, 0);
        texture(3, 0);
        texture(2, 0);
        texture(1, 0);
        texture(0, 0);
        shader.clear();
        BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(0);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        if (gradeOn) Diagnostics.mark(Diagnostics.GRADE, Diagnostics.detail() ? width + "x" + height : null);
    }

    private static int bloom(int scene, int sceneFilter, int width, int height) {
        int w = width / 4;
        int h = height / 4;
        if (w < 16 || h < 16) {
            Diagnostics.skip(Diagnostics.BLOOM, "window too small");
            return 0;
        }
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            mips[i] = fit(mips[i], Math.max(w, 1), Math.max(h, 1), true);
            w /= 4;
            h /= 4;
        }

        float threshold = (float) FxConfig.bloomThreshold;
        ProgramManager.glUseProgram(down.getId());
        GL20.glUniform3f(downThreshold, threshold, BLOOM_KNEE, 1F);
        texture(2, Minecraft.getInstance().getMainRenderTarget().getDepthTextureId());
        texture(1, mips[BLOOM_LEVELS - 1].getColorTextureId());
        texture(0, scene);
        int linear = sceneSampler();
        if (linear != 0) {
            GL33.glBindSampler(0, linear);
            GL33.glBindSampler(2, linear);
        } else {
            if (sceneFilter != GL11.GL_LINEAR) filter(GL11.GL_LINEAR);
            GlStateManager._activeTexture(GL13.GL_TEXTURE2);
            filter(GL11.GL_LINEAR);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        }
        mips[0].bindWrite(true);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        if (linear != 0) {
            GL33.glBindSampler(0, 0);
            GL33.glBindSampler(2, 0);
        } else {
            if (sceneFilter != GL11.GL_LINEAR) filter(sceneFilter);
            GlStateManager._activeTexture(GL13.GL_TEXTURE2);
            filter(GL11.GL_NEAREST);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        }
        texture(2, 0);
        texture(1, 0);
        GL20.glUniform3f(downThreshold, threshold, BLOOM_KNEE, 0F);
        for (int i = 1; i < BLOOM_LEVELS; i++) {
            texture(0, mips[i - 1].getColorTextureId());
            mips[i].bindWrite(true);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        }

        float spread = (float) FxConfig.bloomSpread;
        float spread2 = spread * spread;
        ProgramManager.glUseProgram(gather.getId());
        GL20.glUniform3f(gatherWeights, spread2, spread2 * spread2, spread2 * spread2 * spread2);
        texture(2, mips[3].getColorTextureId());
        texture(1, mips[2].getColorTextureId());
        texture(0, mips[1].getColorTextureId());
        mips[0].bindWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();

        Diagnostics.mark(Diagnostics.BLOOM, Diagnostics.detail() ? "from " + mips[0].width + "x" + mips[0].height : null);
        return mips[0].getColorTextureId();
    }

    private static void swap(RenderTarget main, RenderTarget spare) {
        int frame = main.getColorTextureId();
        int free = spare.getColorTextureId();
        ((RenderTargetColorAccessor) main).linggango$setColorTextureId(free);
        ((RenderTargetColorAccessor) spare).linggango$setColorTextureId(frame);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, free, 0);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, spare.frameBufferId);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, frame, 0);
    }

    static int sceneSampler() {
        if (sceneSampler < 0) {
            GLCapabilities caps = GL.getCapabilities();
            sceneSampler = 0;
            if (caps.OpenGL33 || caps.GL_ARB_sampler_objects) {
                sceneSampler = GL33.glGenSamplers();
                GL33.glSamplerParameteri(sceneSampler, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL33.glSamplerParameteri(sceneSampler, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL33.glSamplerParameteri(sceneSampler, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL33.glSamplerParameteri(sceneSampler, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            }
        }
        return sceneSampler;
    }

    private static void filter(int mode) {
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mode);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mode);
    }

    static boolean inPlace() {
        if (barrier < 0) {
            GLCapabilities caps = GL.getCapabilities();
            barrier = caps.OpenGL45 ? 1 : caps.GL_ARB_texture_barrier ? 2 : caps.GL_NV_texture_barrier ? 3 : 0;
            Diagnostics.once(barrier > 0 ? "colour grade reads the frame in place" : "colour grade reads the frame through a copy");
        }
        return barrier > 0;
    }

    static void barrier() {
        switch (barrier) {
            case 1 -> GL45C.glTextureBarrier();
            case 2 -> ARBTextureBarrier.glTextureBarrier();
            case 3 -> NVTextureBarrier.glTextureBarrierNV();
            default -> {}
        }
    }

    private static void texture(int unit, int id) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(id);
    }

    private static TextureTarget fit(TextureTarget target, int width, int height, boolean wide) {
        if (target == null) {
            target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        } else {
            return target;
        }
        target.setFilterMode(GL11.GL_LINEAR);
        if (wide) {
            GlStateManager._bindTexture(target.getColorTextureId());
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, null);
        }
        return target;
    }

    private static float dimness() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        float target = 0F;
        if (player != null && level != null && level.dimensionType().hasSkyLight() && !player.hasEffect(MobEffects.NIGHT_VISION)) {
            BlockPos eye = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
            float light = Math.max(level.getBrightness(LightLayer.BLOCK, eye),
                    level.getBrightness(LightLayer.SKY, eye) * level.getSkyDarken(1F));
            light = Math.max(light, Math.max(heldLight(player.getMainHandItem()), heldLight(player.getOffhandItem())));
            float lit = Mth.clamp((light - 3F) / 7F, 0F, 1F);
            target = 1F - lit * lit * (3F - 2F * lit);
        }
        long now = Util.getNanos();
        float dt = dimAt == 0L ? 0F : Math.min((now - dimAt) / 1.0E9F, 0.25F);
        dimAt = now;
        float rate = target > dim ? 0.6F : 2.5F;
        dim += (target - dim) * (1F - (float) Math.exp(-dt * rate));
        return dim;
    }

    private static float heldLight(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item ? item.getBlock().defaultBlockState().getLightEmission() : 0F;
    }

    private static void load() {
        try {
            ResourceProvider resources = Minecraft.getInstance().getResourceManager();
            bind(new ShaderInstance(resources, SHADER, DefaultVertexFormat.POSITION));
            bindDown(new ShaderInstance(resources, DOWN, DefaultVertexFormat.POSITION));
            bindGather(new ShaderInstance(resources, GATHER, DefaultVertexFormat.POSITION));
            Diagnostics.once("colour grade and glow shaders loaded outside the shader reload");
        } catch (IOException | RuntimeException e) {
            loadFailed = true;
            Diagnostics.LOG.error("[atmosphere] colour grade or glow shader failed to load", e);
        }
    }

    private static void bind(ShaderInstance loaded) {
        int id = loaded.getId();
        gradeUniform = GlStateManager._glGetUniformLocation(id, "Grade");
        skyGradeUniform = GlStateManager._glGetUniformLocation(id, "SkyGrade");
        tintUniform = GlStateManager._glGetUniformLocation(id, "Tint");
        vignetteUniform = GlStateManager._glGetUniformLocation(id, "Vignette");
        strengthUniform = GlStateManager._glGetUniformLocation(id, "BloomStrength");
        raysUniform = GlStateManager._glGetUniformLocation(id, "Rays");
        shadowUniform = GlStateManager._glGetUniformLocation(id, "Shadows");
        hazeUniform = GlStateManager._glGetUniformLocation(id, "Haze");
        mistUniform = GlStateManager._glGetUniformLocation(id, "MistColor");
        puddleUniform = GlStateManager._glGetUniformLocation(id, "PuddleColor");
        viewUniform = GlStateManager._glGetUniformLocation(id, "View");
        viewTanUniform = GlStateManager._glGetUniformLocation(id, "ViewTan");
        flareUniform = GlStateManager._glGetUniformLocation(id, "Flare");
        units(loaded, "DiffuseSampler", "BloomSampler", "RaySampler", "SunSampler", "DepthSampler", "NoiseSampler");
        shader = loaded;
        loadFailed = false;
    }

    private static void bindDown(ShaderInstance loaded) {
        int id = loaded.getId();
        downThreshold = GlStateManager._glGetUniformLocation(id, "Threshold");
        units(loaded, "DiffuseSampler", "AverageSampler", "DepthSampler");
        down = loaded;
    }

    private static void bindGather(ShaderInstance loaded) {
        gatherWeights = GlStateManager._glGetUniformLocation(loaded.getId(), "Weights");
        units(loaded, "NearSampler", "MidSampler", "FarSampler");
        gather = loaded;
    }

    private static void units(ShaderInstance loaded, String... samplers) {
        ProgramManager.glUseProgram(loaded.getId());
        for (int i = 0; i < samplers.length; i++) {
            GlStateManager._glUniform1i(GlStateManager._glGetUniformLocation(loaded.getId(), samplers[i]), i);
        }
        loaded.clear();
    }

    public static boolean shaderPackActive() {
        long now = Util.getMillis();
        if (shaderPackChecked && now - shaderPackCheckedAt < SHADER_PACK_POLL) return shaderPack;
        shaderPackChecked = true;
        shaderPackCheckedAt = now;
        if (!irisResolved) {
            irisResolved = true;
            if (ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris")) {
                try {
                    Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, ColorGrade.class.getClassLoader());
                    irisApi = type.getMethod("getInstance").invoke(null);
                    irisInUse = type.getMethod("isShaderPackInUse");
                } catch (ReflectiveOperationException | RuntimeException e) {
                    irisInUse = null;
                }
            }
        }
        if (irisInUse == null) return shaderPack = false;
        try {
            return shaderPack = Boolean.TRUE.equals(irisInUse.invoke(irisApi));
        } catch (ReflectiveOperationException | RuntimeException e) {
            irisInUse = null;
            return shaderPack = false;
        }
    }

    @Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class GradeShaders {
        private GradeShaders() {}

        @SubscribeEvent
        public static void onRegisterGradeShaders(RegisterShadersEvent event) throws IOException {
            ResourceProvider resources = event.getResourceProvider();
            event.registerShader(new ShaderInstance(resources, SHADER, DefaultVertexFormat.POSITION), ColorGrade::bind);
            event.registerShader(new ShaderInstance(resources, DOWN, DefaultVertexFormat.POSITION), ColorGrade::bindDown);
            event.registerShader(new ShaderInstance(resources, GATHER, DefaultVertexFormat.POSITION), ColorGrade::bindGather);
        }
    }
}
