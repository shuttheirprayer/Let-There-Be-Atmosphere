package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.mojang.blaze3d.platform.NativeImage;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.Tags;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class GlowTag {
    private GlowTag() {}

    public static final int ORE = 1;
    public static final int STEM = 2;
    public static final int SOFT = 3;
    public static final int DEBRIS = 4;
    public static final int PALE = 5;
    public static final int DIM = 6;
    public static final int SCULK = 7;

    private static final Map<Block, Integer> KINDS = new ConcurrentHashMap<>();

    public static final float[] GLOW = new float[4];
    public static volatile int epoch;

    private static final String V_DECL = "out float v_MaterialMipBias;";
    private static final String V_HOOK = "v_MaterialMipBias = _material_mip_bias(_material_params);";
    private static final String F_DECL = "in float v_MaterialMipBias;";
    private static final String F_TEXEL = "vec4 diffuseColor = texture(u_BlockTex, v_TexCoord, v_MaterialMipBias);";
    private static final String F_HOOK = "fragColor = _linearFog(";

    private static final String VERTEX_DECL = """

            flat out uint v_LinggangoTag;
            flat out float v_LinggangoPhase;
            uniform vec4 u_LinggangoGlowCam;
            """;

    private static final String VERTEX_HOOK = """

                v_LinggangoTag = ((_material_params >> 3u) & 3u) == 0u ? (_material_params >> 5u) & 7u : 0u;
                v_LinggangoPhase = 0.0;
                if (v_LinggangoTag != 0u) {
                    vec3 cell = mod(floor(position + u_LinggangoGlowCam.xyz + 0.5), 289.0);
                    v_LinggangoPhase = (v_LinggangoTag == 1u || (v_LinggangoTag >= 4u && v_LinggangoTag <= 6u))
                        ? fract(sin(dot(cell, vec3(12.9898, 78.233, 37.719))) * 43758.5453) * 6.2831853
                        : dot(cell, vec3(0.35, 0.6, 0.35));
                }""";

    private static final String FRAGMENT_DECL = """

            flat in uint v_LinggangoTag;
            flat in float v_LinggangoPhase;
            uniform vec4 u_LinggangoGlow;

            vec3 linggango_glow(vec3 lit, vec3 texel, vec3 light) {
                uint tag = v_LinggangoTag;
                float t = u_LinggangoGlow.x;
                float hi = max(texel.r, max(texel.g, texel.b));
                float dark = 1.0 - smoothstep(0.3, 0.95, max(light.r, max(light.g, light.b)));
                float sat = hi - min(texel.r, min(texel.g, texel.b));
                if (tag == 7u) {
                    float cyan = smoothstep(0.08, 0.25, 0.5 * (texel.g + texel.b) - texel.r) * smoothstep(0.3, 0.55, hi);
                    float wave = 0.5 + 0.5 * sin(t * 0.55 + v_LinggangoPhase * 0.8);
                    float flash = max(u_LinggangoGlow.w - 1.0, 0.0);
                    float amount = clamp(cyan * step(0.5, u_LinggangoGlow.w) * (0.3 + 0.3 * wave + 0.6 * flash) * (0.35 + 0.65 * dark), 0.0, 1.0);
                    return mix(lit, max(lit, texel * (1.2 + 0.3 * wave + 0.5 * flash)), amount);
                }
                if (tag == 1u || (tag >= 4u && tag <= 6u)) {
                    float mask = tag == 1u ? smoothstep(0.08, 0.28, sat) * smoothstep(0.45, 0.75, hi)
                        : tag == 5u ? smoothstep(0.66, 0.86, hi)
                        : tag == 6u ? smoothstep(0.06, 0.18, sat) * smoothstep(0.22, 0.4, hi)
                        : smoothstep(0.32, 0.5, dot(texel, vec3(0.299, 0.587, 0.114)));
                    float shimmer = 0.88 + 0.12 * sin(t * 0.7 + v_LinggangoPhase) + 0.04 * sin(t * 1.9 + v_LinggangoPhase * 2.3);
                    float amount = clamp(0.9 * mask * u_LinggangoGlow.y * (0.06 + 0.94 * dark) * shimmer, 0.0, 1.0);
                    return mix(lit, max(lit, texel * (1.0 + (tag == 6u ? 0.7 : 0.25) * mask)), amount);
                }
                float breath = 0.5 + 0.5 * sin(t * 0.8 + v_LinggangoPhase);
                float mask = smoothstep(0.42, 0.68, hi);
                float amount = clamp(0.8 * mask * (tag == 3u ? 0.5 : 1.0) * (0.15 + 0.85 * breath) * (0.4 + 0.6 * dark) * u_LinggangoGlow.z, 0.0, 1.0);
                return mix(lit, max(lit, texel * (1.3 + 0.2 * breath)), amount);
            }
            """;

    private static final String FRAGMENT_APPLY = "if (v_LinggangoTag != 0u) diffuseColor.rgb = linggango_glow(diffuseColor.rgb, linggango_texel, v_Color.rgb);\n    ";

    private static boolean vertexAnchors(String source) {
        return source.contains(V_DECL) && source.contains(V_HOOK);
    }

    public static String patchVertex(String source) {
        if (source.contains("v_LinggangoTag")) return source;
        if (!vertexAnchors(source)) {
            Diagnostics.once("ore glow and stem pulse could not find their place in the terrain vertex shader, so they stay off");
            return source;
        }
        Diagnostics.once("ore glow and stem pulse patched into the terrain vertex shader");
        return source.replace(V_DECL, V_DECL + VERTEX_DECL).replace(V_HOOK, V_HOOK + VERTEX_HOOK);
    }

    public static String patchFragment(String source) {
        if (source.contains("linggango_glow(")) return source;
        if (!source.contains(F_DECL) || !source.contains(F_TEXEL) || !source.contains(F_HOOK) || !vertexReady()) {
            Diagnostics.once("ore glow and stem pulse could not find their place in the terrain fragment shader, so they stay off");
            return source;
        }
        Diagnostics.once("ore glow and stem pulse patched into the terrain fragment shader");
        return source.replace(F_DECL, F_DECL + FRAGMENT_DECL)
                .replace(F_TEXEL, F_TEXEL + "\n    vec3 linggango_texel = diffuseColor.rgb;")
                .replace(F_HOOK, FRAGMENT_APPLY + F_HOOK);
    }

    private static boolean vertexReady() {
        try {
            return vertexAnchors(ShaderLoader.getShaderSource(new ResourceLocation("sodium", "blocks/block_layer_opaque.vsh")));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static int bits(BlockState state) {
        return code(state) << 5;
    }

    private static int code(BlockState state) {
        Block block = state.getBlock();
        if (FxConfig.stemPulse) {
            if (block == Blocks.STRIPPED_CRIMSON_STEM || block == Blocks.STRIPPED_CRIMSON_HYPHAE
                    || block == Blocks.STRIPPED_WARPED_STEM || block == Blocks.STRIPPED_WARPED_HYPHAE
                    || block == Blocks.SHROOMLIGHT || block == Blocks.NETHER_WART_BLOCK || block == Blocks.WARPED_WART_BLOCK) return SOFT;
            if (state.is(BlockTags.CRIMSON_STEMS) || state.is(BlockTags.WARPED_STEMS)) return STEM;
        }
        if (FxConfig.oreGlow) {
            if (block == Blocks.ANCIENT_DEBRIS) return DEBRIS;
            if (state.is(Tags.Blocks.ORES)) return KINDS.computeIfAbsent(block, b -> oreKind(state));
        }
        if (block == Blocks.SCULK || block == Blocks.SCULK_VEIN || block == Blocks.SCULK_CATALYST || block == Blocks.SCULK_SENSOR
                || block == Blocks.CALIBRATED_SCULK_SENSOR || block == Blocks.SCULK_SHRIEKER) return SCULK;
        return 0;
    }

    private static int oreKind(BlockState state) {
        Minecraft mc = Minecraft.getInstance();
        try {
            ResourceLocation name = mc.getBlockRenderer().getBlockModel(state).getParticleIcon(ModelData.EMPTY).contents().name();
            Optional<Resource> found = mc.getResourceManager().getResource(name.withPath("textures/" + name.getPath() + ".png"));
            if (found.isEmpty()) return ORE;
            try (InputStream in = found.get().open(); NativeImage image = NativeImage.read(in)) {
                int size = Math.min(image.getWidth(), image.getHeight()), total = 0, bright = 0, pale = 0, dim = 0;
                float[] his = new float[size * size], sats = new float[size * size];
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        int c = image.getPixelRGBA(x, y);
                        if ((c >>> 24) < 200) continue;
                        float r = (c & 255) / 255F, g = (c >> 8 & 255) / 255F, b = (c >> 16 & 255) / 255F;
                        float hi = Math.max(r, Math.max(g, b)), sat = hi - Math.min(r, Math.min(g, b));
                        his[total] = hi;
                        sats[total] = sat;
                        total++;
                        if (ramp(0.08F, 0.28F, sat) * ramp(0.45F, 0.75F, hi) > 0.3F) bright++;
                        else if (ramp(0.66F, 0.86F, hi) > 0.3F) pale++;
                        else if (ramp(0.06F, 0.18F, sat) * ramp(0.22F, 0.4F, hi) > 0.3F) dim++;
                    }
                }
                if (total == 0) return ORE;
                java.util.Arrays.sort(his, 0, total);
                java.util.Arrays.sort(sats, 0, total);
                float hostHi = his[total / 2], hostSat = sats[total / 2];
                if (fits(bright, total)) return ORE;
                if (fits(pale, total) && hostHi < 0.6F) return PALE;
                if (fits(dim, total) && hostSat < 0.05F) return DIM;
                return ORE;
            }
        } catch (Exception e) {
            return ORE;
        }
    }

    private static boolean fits(int count, int total) {
        return count >= total * 0.04F && count <= total * 0.45F;
    }

    private static float ramp(float a, float b, float x) {
        float k = Mth.clamp((x - a) / (b - a), 0F, 1F);
        return k * k * (3F - 2F * k);
    }

    public static void update() {
        WindSway.update();
        GLOW[0] = WindSway.WIND[0];
        GLOW[1] = FxConfig.oreGlow ? (float) FxConfig.oreGlowStrength : 0F;
        GLOW[2] = FxConfig.stemPulse ? 1F : 0F;
        GLOW[3] = FxConfig.deepDarkHaze ? 1F + DeepDark.pulse() : 0F;
    }

    public static void refresh() {
        epoch++;
        KINDS.clear();
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.levelRenderer != null) mc.levelRenderer.allChanged();
        });
    }
}
