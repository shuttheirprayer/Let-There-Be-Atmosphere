package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

public final class RainRings {
    private RainRings() {}

    public static final float[] RAIN = new float[4];
    public static final float[] SPRITE = new float[4];
    public static volatile boolean bound;

    private static final ResourceLocation STILL = new ResourceLocation("block/water_still");
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    static final String V_DECL = "out float v_MaterialMipBias;";
    static final String V_HOOK = "v_MaterialMipBias = _material_mip_bias(_material_params);";
    static final String F_DECL = "in float v_MaterialMipBias;";
    static final String F_HOOK = "fragColor = _linearFog(";

    static final String FUNCTION = """

            in vec4 v_LinggangoRainPos;
            uniform vec4 u_LinggangoRain;
            uniform vec4 u_LinggangoRainTex;

            uint linggango_rainHash(uvec3 v) {
                v = v * 1664525u + 1013904223u;
                v.x += v.y * v.z;
                v.y += v.z * v.x;
                v.z += v.x * v.y;
                v ^= v >> 16u;
                v.x += v.y * v.z;
                v.y += v.z * v.x;
                v.z += v.x * v.y;
                return v.x ^ v.y ^ v.z;
            }

            vec4 linggango_rain(vec4 color) {
                if (u_LinggangoRain.w <= 0.0) return color;
                vec2 world = (v_LinggangoRainPos.xz + u_LinggangoRain.xy) * 16.0;
                float blur = max(fwidth(world.x), fwidth(world.y));
                vec2 uv = v_TexCoord;
                vec4 tex = u_LinggangoRainTex;
                if (v_LinggangoRainPos.y > -0.05 || uv.x < tex.x || uv.y < tex.y || uv.x > tex.z || uv.y > tex.w) return color;
                float fade = smoothstep(196.0, 212.0, v_LinggangoRainPos.w) * (1.0 - smoothstep(0.45, 0.9, blur))
                           * (1.0 - smoothstep(40.0, 64.0, length(v_LinggangoRainPos.xyz)));
                if (fade <= 0.0) return color;
                vec2 texel = floor(world);
                vec2 cell = floor(texel / 16.0);
                uvec2 id = uvec2(ivec2(cell));
                uint seed = linggango_rainHash(uvec3(id, 977u));
                float period = 1.1 + 0.9 * float(seed & 255u) / 255.0;
                float cycle = u_LinggangoRain.z / period + float((seed >> 8u) & 255u) / 255.0;
                float age = fract(cycle) * period / 0.9;
                if (age >= 1.0) return color;
                uint drop = linggango_rainHash(uvec3(id, uint(int(floor(cycle)))));
                if (float(drop & 1023u) / 1023.0 > u_LinggangoRain.w) return color;
                vec2 centre = cell * 16.0 + vec2(4.5) + vec2(float((drop >> 10u) & 7u), float((drop >> 13u) & 7u));
                float radius = floor(0.5 + 4.0 * (1.0 - (1.0 - age) * (1.0 - age)));
                float ring = step(abs(length(texel + 0.5 - centre) - radius), 0.5);
                float a = ring * fade * (1.0 - age) * (radius < 0.5 ? 0.7 : 1.0);
                if (a <= 0.0) return color;
                float hi = max(color.r, max(color.g, color.b));
                vec3 lit = mix(color.rgb, vec3(hi), 0.6) * 1.45 + 0.04 * hi;
                return vec4(mix(color.rgb, lit, 0.75 * a), mix(color.a, max(color.a, 0.9), 0.6 * a));
            }
            """;

    public static boolean live() {
        return bound && FxConfig.rainRings && FxConfig.rainRingsAmount > 0.0;
    }

    private static boolean vertexAnchors(String source) {
        return source.contains(V_DECL) && source.contains(V_HOOK);
    }

    public static String patchVertex(String source) {
        if (source.contains("v_LinggangoRainPos")) return source;
        if (!vertexAnchors(source)) {
            Diagnostics.once("rain rings could not find their place in the terrain vertex shader, so they stay off");
            return source;
        }
        return source.replace(V_DECL, V_DECL + "\nout vec4 v_LinggangoRainPos;")
                .replace(V_HOOK, V_HOOK + "\n    v_LinggangoRainPos = vec4(position, float(_vert_tex_light_coord.y));");
    }

    public static String patchFragment(String source) {
        if (source.contains("linggango_rain(")) return source;
        if (!source.contains(F_DECL) || !source.contains(F_HOOK) || !source.contains("v_TexCoord") || !vertexReady()) {
            Diagnostics.once("rain rings could not find their place in the terrain fragment shader, so they stay off");
            return source;
        }
        Diagnostics.once("rain rings patched into the terrain shader");
        return source.replace(F_DECL, F_DECL + FUNCTION).replace(F_HOOK, "diffuseColor = linggango_rain(diffuseColor);\n    " + F_HOOK);
    }

    static boolean vertexReady() {
        try {
            return vertexAnchors(ShaderLoader.getShaderSource(new ResourceLocation("sodium", "blocks/block_layer_opaque.vsh")));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void update() {
        WindSway.update();
        RAIN[0] = WindSway.CAMERA[0];
        RAIN[1] = WindSway.CAMERA[2];
        RAIN[2] = WindSway.WIND[0];
        RAIN[3] = 0F;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !live() || !level.dimensionType().hasSkyLight()) return;
        float partialTick = mc.getFrameTime();
        float rain = level.getRainLevel(partialTick);
        if (rain < 0.02F) return;
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        CURSOR.set(eye.x, eye.y, eye.z);
        if (level.getBiome(CURSOR).value().getPrecipitationAt(CURSOR) != Biome.Precipitation.RAIN) return;
        TextureAtlasSprite still = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(STILL);
        SPRITE[0] = still.getU0();
        SPRITE[1] = still.getV0();
        SPRITE[2] = still.getU1();
        SPRITE[3] = still.getV1();
        RAIN[3] = Mth.clamp(rain * (0.6F + 0.3F * level.getThunderLevel(partialTick)) * (float) FxConfig.rainRingsAmount, 0F, 0.95F);
    }
}
