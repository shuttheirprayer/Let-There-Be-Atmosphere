package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WindSway {
    private WindSway() {}

    public static final int NONE = 0;
    public static final int LEAVES = 1;
    public static final int PLANT = 2;
    public static final int WEAK = 3;
    public static final int TUFTS = 4;

    public static final float[] WIND = new float[4];
    public static final float[] CAMERA = new float[4];
    public static final float[] GUST = new float[4];
    public static final float[] FLOW = new float[4];
    private static final float FLOW_WRAP = 1024F;
    private static float flowAt = Float.NaN;

    private static final String HEAD = "uniform vec3 u_RegionOffset;";
    private static final String HOOK = "vec3 position = _vert_position + translation;";
    private static final String FUNCTION = """

            uniform vec4 u_LinggangoWind;
            uniform vec4 u_LinggangoCamera;
            uniform vec4 u_LinggangoGust;
            uniform vec4 u_LinggangoFlow;
            uniform vec4 u_LinggangoPush0;
            uniform vec4 u_LinggangoPush1;
            uniform vec4 u_LinggangoPush2;
            uniform vec4 u_LinggangoPush3;

            vec2 linggango_push(vec4 who, vec3 position, float height) {
                if (who.w <= 0.0) return vec2(0.0);
                vec3 rel = position - who.xyz;
                float d = length(rel.xz);
                if (d > 1.6 || rel.y < -0.6 || rel.y > 2.2) return vec2(0.0);
                return (d > 0.001 ? rel.xz / d : vec2(0.0)) * (1.0 - smoothstep(0.25, 1.4, d)) * 0.55 * height * who.w;
            }

            float linggango_hash(vec2 c) {
                return fract(sin(dot(c, vec2(127.1, 311.7))) * 43758.5453);
            }

            float linggango_noise(vec2 p, float period) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                vec2 j = i + 1.0;
                if (period > 0.0) {
                    i = mod(i, period);
                    j = mod(j, period);
                }
                return mix(mix(linggango_hash(i), linggango_hash(vec2(j.x, i.y)), f.x),
                           mix(linggango_hash(vec2(i.x, j.y)), linggango_hash(j), f.x), f.y);
            }

            vec3 linggango_sway(vec3 position) {
                uint kind = (_material_params >> 3u) & 3u;
                if (kind == 0u || u_LinggangoWind.w <= 0.0) return vec3(0.0);
                float height = float((_material_params >> 5u) & 7u) / 7.0;
                vec3 world = position + u_LinggangoCamera.xyz;
                float t = u_LinggangoWind.x;
                vec2 dir = u_LinggangoWind.yz;
                float force = length(dir);
                vec2 along = force > 0.001 ? dir / force : vec2(1.0, 0.0);
                vec2 side = vec2(-along.y, along.x);
                float cluster = u_LinggangoGust.x;
                float cover = mix(1.0, mix(0.2, 1.0, smoothstep(0.2, 0.8, float(_vert_tex_light_coord.y) / 240.0)), u_LinggangoGust.z);
                float lift = mix(1.0, 0.75 + 0.85 * smoothstep(40.0, 190.0, world.y), u_LinggangoGust.y);
                float blow = force * lift * cover;
                float bend = blow / (1.0 + 0.3 * blow);
                float calm = 1.0 - smoothstep(0.4, 1.4, blow);
                vec2 drift = world.xz - u_LinggangoFlow.xy;
                float n = linggango_noise(drift / 32.0, 32.0) * 0.65 + linggango_noise(drift / 16.0 + 5.3, 64.0) * 0.35;
                float crest = smoothstep(0.42, 0.8, n);
                float gust = (crest * (0.5 + 0.5 * cluster) + 0.25 * n * (0.2 + 0.8 * cluster)) * (0.8 + 0.4 * u_LinggangoGust.w);
                float phase = dot(world.xz, vec2(0.37, 0.29));
                if (kind == 1u) {
                    vec3 rustle = vec3(sin(t * 2.3 + world.x * 1.3 + world.y * 0.7),
                                       sin(t * 2.9 + world.z * 1.1 + world.x * 0.5) * 0.45,
                                       cos(t * 2.0 + world.z * 1.3 + world.y * 0.5));
                    float quiver = sin(t * 9.0 + world.x * 2.3 + world.z * 1.9 + world.y * 1.1);
                    float amp = (0.008 + 0.02 * bend + 0.025 * bend * gust) * (0.4 + 0.6 * calm);
                    vec3 lean = vec3(along.x, 0.0, along.y) * bend * (0.03 + 0.06 * gust);
                    return (rustle * amp + vec3(quiver, quiver * 0.3, -quiver) * (0.003 + 0.01 * bend * gust) + lean) * u_LinggangoWind.w;
                }
                float sway = sin(t * 1.7 + phase * 2.1 + world.y * 0.7) * 0.6 + sin(t * 2.9 + phase * 3.7) * 0.4;
                float quiver = sin(t * 11.0 + phase * 7.3) * 0.6 + sin(t * 17.0 + phase * 4.1) * 0.4;
                float amp = (kind == 2u ? 0.38 : 0.16) * pow(height, 1.5);
                float push = bend * (0.55 + 0.9 * gust);
                vec2 lean = along * (push + sway * 0.35 * calm * (0.3 + bend) + quiver * 0.06 * bend)
                          + side * (sway * 0.12 * calm + quiver * 0.035 * bend);
                vec2 off = lean * amp * u_LinggangoWind.w;
                off += linggango_push(u_LinggangoPush0, position, height) + linggango_push(u_LinggangoPush1, position, height)
                     + linggango_push(u_LinggangoPush2, position, height) + linggango_push(u_LinggangoPush3, position, height);
                float len = length(off);
                if (len > 0.6) off *= 0.6 / len;
                return vec3(off.x, -dot(off, off) * 0.7, off.y);
            }
            """;

    public static String patch(String source) {
        if (source.contains("linggango_sway(")) return source;
        if (!source.contains(HEAD) || !source.contains(HOOK)) {
            Diagnostics.once("wind sway could not find its place in the terrain shader, so plants stay still");
            return source;
        }
        Diagnostics.once("wind sway patched into the terrain shader");
        return source.replace(HEAD, HEAD + FUNCTION).replace(HOOK, HOOK + "\n    position += linggango_sway(position);");
    }

    public static int kind(BlockState state) {
        if (state.is(BlockTags.LEAVES)) return LEAVES;
        Block block = state.getBlock();
        if (block instanceof WaterlilyBlock) return NONE;
        if (block instanceof BushBlock || block instanceof SugarCaneBlock) return PLANT;
        if (block instanceof VineBlock || block instanceof GrowingPlantBlock) return WEAK;
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)) return TUFTS;
        return NONE;
    }

    public static int bits(int kind, BlockState state, float y) {
        switch (kind) {
            case LEAVES:
                return LEAVES << 3 | 7 << 5;
            case PLANT:
            case WEAK: {
                float height = Mth.clamp(y, 0F, 1F);
                if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                    height = (height + (state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? 1F : 0F)) * 0.5F;
                }
                return kind << 3 | Math.round(height * 7F) << 5;
            }
            case TUFTS: {
                if (y <= 1.01F) return 0;
                return PLANT << 3 | Math.round(Mth.clamp((y - 1F) / 0.75F, 0F, 1F) * 7F) << 5;
            }
            default:
                return 0;
        }
    }

    public static final float[][] PUSH = new float[4][4];
    public static volatile int tagged;
    public static int uploads;
    public static boolean frozen;
    private static final List<Entity> NEAR = new ArrayList<>();
    private static long frameKey = Long.MIN_VALUE;

    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            WIND[3] = 0F;
            return;
        }
        if (frozen) return;
        float partialTick = mc.getFrameTime();
        long key = level.getGameTime() * 1000L + (long) (partialTick * 999F);
        if (key == frameKey) return;
        frameKey = key;
        float seconds = Wind.seconds(level, partialTick);
        float force = Wind.force(seconds);
        float rain = level.getRainLevel(partialTick), thunder = level.getThunderLevel(partialTick);
        WIND[0] = seconds % 3600F;
        WIND[1] = Wind.x(seconds) * force;
        WIND[2] = Wind.z(seconds) * force;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        CAMERA[0] = (float) (camera.x % 4096.0);
        CAMERA[1] = (float) camera.y;
        CAMERA[2] = (float) (camera.z % 4096.0);
        CAMERA[3] = FxConfig.livingWind ? Math.min(1F, Math.max(Wind.storm * 0.7F, thunder)) : Math.max(rain * 0.5F, thunder);
        GUST[0] = Wind.cluster(seconds);
        GUST[1] = FxConfig.windAltitude ? 1F : 0F;
        GUST[2] = FxConfig.windShelter ? 1F : 0F;
        GUST[3] = Wind.pulse(seconds);
        float dt = Float.isNaN(flowAt) ? 0F : Mth.clamp(seconds - flowAt, 0F, 0.25F);
        flowAt = seconds;
        float travel = dt * (3F + 3.5F * Math.min(force, 2.5F) + 3F * CAMERA[3]);
        FLOW[0] = (FLOW[0] + Wind.x(seconds) * travel) % FLOW_WRAP;
        FLOW[1] = (FLOW[1] + Wind.z(seconds) * travel) % FLOW_WRAP;
        pushers(mc, level, camera, partialTick);
        if (!FxConfig.windSway || FxConfig.windSwayStrength <= 0.0 || !DynamicFogHandler.active()) {
            WIND[3] = 0F;
            Diagnostics.skip(Diagnostics.SWAY, "disabled");
            return;
        }
        WIND[3] = (float) (FxConfig.windSwayStrength * FxConfig.windStrength);
        Diagnostics.mark(Diagnostics.SWAY, Diagnostics.detail() ? String.format("%s force %.2f, %d tagged", Wind.mood(seconds), force, tagged) : null);
        if (tagged > 0) Diagnostics.once("wind sway is tagging plant vertices in chunk meshes");
    }

    private static void pushers(Minecraft mc, ClientLevel level, Vec3 camera, float partialTick) {
        for (float[] p : PUSH) p[3] = 0F;
        if (mc.player == null) return;
        NEAR.clear();
        if (!mc.player.isSpectator()) NEAR.add(mc.player);
        for (Entity e : level.getEntities(mc.player, mc.player.getBoundingBox().inflate(12.0))) {
            if ((e instanceof LivingEntity || e instanceof Boat) && !e.isSpectator()) NEAR.add(e);
        }
        Vec3 feet = mc.player.position();
        NEAR.sort(Comparator.comparingDouble(e -> e.distanceToSqr(feet)));
        for (int i = 0; i < PUSH.length && i < NEAR.size(); i++) {
            Entity e = NEAR.get(i);
            PUSH[i][0] = (float) (Mth.lerp(partialTick, e.xo, e.getX()) - camera.x);
            PUSH[i][1] = (float) (Mth.lerp(partialTick, e.yo, e.getY()) - camera.y);
            PUSH[i][2] = (float) (Mth.lerp(partialTick, e.zo, e.getZ()) - camera.z);
            PUSH[i][3] = Mth.clamp(e.getBbWidth() / 0.6F, 0.6F, 1.8F);
        }
    }
}
