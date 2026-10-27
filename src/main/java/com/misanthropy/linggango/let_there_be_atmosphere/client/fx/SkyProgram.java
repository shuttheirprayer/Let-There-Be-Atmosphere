package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public final class SkyProgram {
    private static final List<SkyProgram> ALL = new ArrayList<>();

    static final SkyProgram AURORA = new SkyProgram("sky_aurora", new String[]{"Noise"},
            "Transform", "Shape", "Layout", "TopColor", "MidColor", "LowColor", "Band", "Phase", "Glow");
    static final SkyProgram COMETS = new SkyProgram("sky_comets", new String[]{"TailSampler", "HeadSampler"},
            "Transform", "Visible", "Path", "Axis", "Look", "HeadColor", "TrailColor");
    static final SkyProgram HAZE = new SkyProgram("sky_haze", new String[0], "Transform", "Ring", "Fade", "Tint");
    static final SkyProgram GLOW = new SkyProgram("sky_glow", new String[]{"Sampler0"}, "Transform", "Center", "Size", "Tint", "Mode", "AxisU", "AxisV");
    static final SkyProgram RAINBOW = new SkyProgram("sky_rainbow", new String[0], "Transform", "Sun");
    static final SkyProgram SUN_MASK = new SkyProgram("sun_mask", new String[]{"DepthSampler", "CloudSampler", "NoiseSampler", "HeightSampler", "CoverSampler"},
            "InverseTransform", "Cloud", "Sun", "Ray", "Mist", "MistDrift", "Ground", "GroundOrigin", "Puddle", "HeightCenter", "CloudGrid", "Cover", "Wet", "Occlusion", "ViewProjection", "SunShadow");
    static final SkyProgram CAVE_MOTES = new SkyProgram("cave_motes", new String[]{"Sampler0", "Sampler1", "DataSampler"},
            "Transform", "Right", "Up", "Offset", "Clock", "MoteFog", "Sprites");
    static final SkyProgram LITTER = new SkyProgram("litter", new String[]{"Sampler0", "Sampler1", "DataSampler"},
            "Transform", "Offset", "Clock", "LitterFog");
    static final SkyProgram BLADES = new SkyProgram("grass_blades", new String[]{"Sampler0", "Sampler1", "DataSampler"},
            "Transform", "Offset", "Clock", "BladeFog", "Wind", "Player", "Push1", "Push2", "Push3");
    static final SkyProgram LITTER_SOLID = new SkyProgram("litter_solid", new String[]{"Sampler0", "Sampler1", "DataSampler", "ModelSampler", "ListSampler", "AtlasSampler"},
            "Transform", "Offset", "Clock", "LitterFog", "Shape");
    static final SkyProgram TUMBLEWEED = new SkyProgram("tumbleweed", new String[]{"Sampler0", "Sampler1", "DataSampler"},
            "Transform", "Offset", "Clock", "TumbleFog");
    static final SkyProgram WIND_BITS = new SkyProgram("wind_bits", new String[]{"Sampler1", "DataSampler"},
            "Transform", "Right", "Up", "Offset", "Clock", "BitFog");
    static final SkyProgram BUTTERFLY = new SkyProgram("butterfly", new String[]{"Sampler0", "Sampler1", "DataSampler"},
            "Transform", "Offset", "Clock", "BugFog");
    static final SkyProgram FISH = new SkyProgram("fish", new String[]{"Sampler0", "Sampler1", "DataSampler"},
            "Transform", "Offset", "Clock", "FishFog");
    static final SkyProgram AIR_MIST = new SkyProgram("air_mist", new String[]{"Sampler0"},
            "Transform", "Right", "Up", "Clock", "MistFog", "Wisps");
    static final SkyProgram PUFFS = new SkyProgram("puffs", new String[]{"Sampler0"},
            "Transform", "Right", "Up", "Clock", "PuffFog", "Puffs");
    static final SkyProgram BIRDS = new SkyProgram("birds", new String[]{"Sampler0", "Sampler1", "DataSampler"},
            "Transform", "Offset", "Clock", "BirdFog");
    static final SkyProgram BATS = new SkyProgram("bats", new String[]{"Sampler0", "DataSampler"},
            "Transform", "Offset", "Clock", "Right", "Up", "BatFog");
    static final SkyProgram WAKE = new SkyProgram("water_wake", new String[]{"Sampler1", "DataSampler"},
            "Transform", "Offset", "Clock", "WakeFog", "Right", "Up");
    static final SkyProgram RIPPLES = new SkyProgram("water_ripples", new String[]{"Sampler1", "DataSampler", "HeightSampler"},
            "Transform", "Offset", "Clock", "RippleFog");
    static final SkyProgram SHORE = new SkyProgram("water_edge", new String[]{"Sampler0", "Sampler1", "DataSampler", "FoamSampler"},
            "Transform", "Offset", "Clock", "ShoreFog", "Origin");
    static final SkyProgram WATERFALL = new SkyProgram("waterfall_foam", new String[]{"Sampler1", "DataSampler", "FoamSampler"},
            "Transform", "Offset", "Clock", "FoamFog", "Right", "Up");
    static final SkyProgram WATER_GLINT = new SkyProgram("water_glint", new String[]{"Sampler0", "DataSampler"},
            "Transform", "Offset", "Clock", "Sun", "Screen");
    static final SkyProgram WATER_SHEEN = new SkyProgram("water_sheen", new String[]{"DepthSampler"},
            "InverseTransform", "Surface", "Horizon", "Zenith");
    static final SkyProgram WATER_REFLECT = new SkyProgram("water_reflect", new String[]{"SceneSampler", "DepthSampler", "SolidSampler", "NoiseSampler", "NearSampler"},
            "InverseTransform", "ViewProjection", "Surface", "Waves", "Horizon", "Zenith");
    static final SkyProgram WATER_REFLECT_MIX = new SkyProgram("water_reflect_mix", new String[]{"ReflectSampler", "SceneSampler", "BaseSampler", "DepthSampler", "SolidSampler"},
            "InverseTransform", "Spread");
    static final SkyProgram WATER_REFLECT_NEAR = new SkyProgram("water_reflect_near", new String[]{"SolidSampler"});
    static final SkyProgram CAUSTICS = new SkyProgram("water_caustics", new String[]{"DepthSampler", "NoiseSampler"},
            "InverseTransform", "Surface", "Light");
    static final SkyProgram WATER_RAYS = new SkyProgram("water_rays", new String[]{"DepthSampler", "NoiseSampler"},
            "InverseTransform", "Surface", "Light");
    static final SkyProgram WATER_SOFTEN = new SkyProgram("water_soften", new String[]{"MaskSampler"});
    static final SkyProgram RAYS_BLUR = new SkyProgram("rays_blur", new String[]{"MaskSampler"}, "Ray", "Fall");
    static final SkyProgram STARS = new SkyProgram("sky_stars", new String[]{"NoiseSampler"}, "Transform", "Field", "Screen", "Band");
    static final SkyProgram CLOUD_BLUR = new SkyProgram("cloud_blur", new String[]{"LayerSampler"}, "Step");
    static final SkyProgram CLOUD_SOFT = new SkyProgram("cloud_soft", new String[]{"BlurSampler", "DepthSampler"},
            "InverseTransform", "Layers");
    static final SkyProgram NETHER_AIR = new SkyProgram("nether_air", new String[]{"DepthSampler", "NoiseSampler"},
            "InverseTransform", "Shafts", "ShaftShape", "Mist", "MistShape", "Drift", "Storm", "StormShape");
    static final SkyProgram NETHER_GLOW = new SkyProgram("nether_glow", new String[]{"GlowSampler"}, "ShaftColor", "MistColor", "StormColor");
    static final SkyProgram NETHER_SOFTEN = new SkyProgram("nether_soften", new String[]{"GlowSampler"});
    static final SkyProgram LAVA_CRUST = new SkyProgram("lava_crust", new String[]{"DepthSampler"},
            "InverseTransform", "Crust", "Plates", "CrustFog", "Anchor");
    static final SkyProgram NETHER_STORM = new SkyProgram("nether_storm", new String[]{"Sampler0", "DepthSampler"},
            "Transform", "Eye", "Boxes", "Cams", "Winds", "Ember", "Ash", "Fume", "Murk", "Right", "Up", "Clock", "FogColor", "Range", "Depth");
    static final SkyProgram STORM_DEPTH = new SkyProgram("storm_depth", new String[]{"DepthSampler"});
    static final SkyProgram STORM_MIX = new SkyProgram("storm_mix", new String[]{"LayerSampler", "LowDepthSampler", "DepthSampler"}, "Depth");
    static final SkyProgram NETHER_FX = new SkyProgram("nether_fx", new String[]{"Sampler0", "Sampler1", "DataSampler"},
            "Transform", "Eye", "Right", "Up", "Clock", "FogColor", "Gain");
    static final SkyProgram NETHER_PORTAL = new SkyProgram("nether_portal", new String[]{"DataSampler"},
            "Transform", "Offset", "Clock", "Right", "Up");
    static final SkyProgram END_NEBULA = new SkyProgram("end_nebula", new String[]{"NebulaSampler"}, "Face");
    static final SkyProgram END_SKY = new SkyProgram("end_sky", new String[]{"CubeSampler"}, "InverseTransform", "Spin", "Sky", "Horizon");
    static final SkyProgram END_AIR = new SkyProgram("end_air", new String[]{"DepthSampler", "NoiseSampler"},
            "InverseTransform", "Cam", "Mist", "Shadow", "Drift", "Haze");
    static final SkyProgram STORM_RAIN =new SkyProgram("storm_rain", new String[]{"Sampler0", "Sampler1", "HeightSampler"},
            "Transform", "Origin", "Eye", "Fall", "Shape", "HeightCenter", "Right", "RainFog", "FogRange", "Range", "Reach", "Lag");
    static final SkyProgram STORM_AIR = new SkyProgram("storm_air", new String[]{"Sampler0", "Sampler1", "HeightSampler"},
            "Transform", "Origin", "Eye", "Wind", "Look", "Counts", "Tint", "Right", "Up", "AirFog", "FogRange",
            "Origin2", "Anchor", "Reach", "HeightCenter", "Drift0", "Drift1", "Drift2");
    static final SkyProgram STORM_SKY = new SkyProgram("storm_sky", new String[]{"NoiseSampler"},
            "InverseTransform", "Deck", "Scud", "DarkColor", "LightColor", "HorizonColor", "Flash");
    private static final Matrix4f COMBINED = new Matrix4f();
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static int vao;

    private final ResourceLocation id;
    private final String[] samplers;
    private final String[] uniforms;
    private final int[] locations;
    private ShaderInstance shader;

    SkyProgram(String name, String[] samplers, String... uniforms) {
        this.id = new ResourceLocation(Let_there_be_atmosphere.MODID, name);
        this.samplers = samplers;
        this.uniforms = uniforms;
        this.locations = new int[uniforms.length];
        ALL.add(this);
    }

    boolean ready() {
        return shader != null;
    }

    int uniform(int index) {
        return locations[index];
    }

    void begin() {
        if (vao == 0) vao = GlStateManager._glGenVertexArrays();
        GlStateManager._glBindVertexArray(vao);
        ProgramManager.glUseProgram(shader.getId());
    }

    void draw(int vertices) {
        if (vertices > 0) GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertices);
    }

    void drawRange(int first, int vertices) {
        if (vertices > 0) GL11.glDrawArrays(GL11.GL_TRIANGLES, first, vertices);
    }

    void end() {
        for (int unit = samplers.length - 1; unit >= 0; unit--) texture(unit, 0);
        shader.clear();
        BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(0);
    }

    void transform(int index, Matrix4f view) {
        COMBINED.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(view);
        COMBINED.get(MATRIX);
        GL20.glUniformMatrix4fv(locations[index], false, MATRIX);
    }

    static void texture(int unit, int id) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(id);
    }

    static int texture(ResourceLocation location) {
        return Minecraft.getInstance().getTextureManager().getTexture(location).getId();
    }

    private void bind(ShaderInstance loaded) {
        int program = loaded.getId();
        for (int i = 0; i < uniforms.length; i++) {
            locations[i] = GlStateManager._glGetUniformLocation(program, uniforms[i]);
        }
        ProgramManager.glUseProgram(program);
        for (int i = 0; i < samplers.length; i++) {
            GlStateManager._glUniform1i(GlStateManager._glGetUniformLocation(program, samplers[i]), i);
        }
        loaded.clear();
        shader = loaded;
    }

    @Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class SkyProgramShaders {
        private SkyProgramShaders() {}

        @SubscribeEvent
        public static void onRegisterSkyProgramShaders(RegisterShadersEvent event) throws IOException {
            for (SkyProgram program : ALL) {
                try {
                    event.registerShader(new ShaderInstance(event.getResourceProvider(), program.id,
                            DefaultVertexFormat.POSITION), program::bind);
                } catch (IOException | RuntimeException e) {
                    program.shader = null;
                    Diagnostics.LOG.error("[atmosphere] sky shader {} failed to load", program.id, e);
                }
            }
        }
    }
}
