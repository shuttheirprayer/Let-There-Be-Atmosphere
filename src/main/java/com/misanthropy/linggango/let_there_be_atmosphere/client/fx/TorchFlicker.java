package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniform;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20C;
import java.nio.FloatBuffer;
import java.util.Arrays;

public final class TorchFlicker {
    private TorchFlicker() {}

    public static final int MAX = 48;
    private static final int SPAN = 5;
    private static final int SPAN_Y = 3;
    private static final int SLOTS = SPAN * SPAN_Y * SPAN;
    private static final int PER_SECTION = 64;
    private static final int FRESH_PER_TICK = 16;
    private static final int SWEEP_PER_TICK = 3;
    private static final long EMPTY = Long.MIN_VALUE;

    private static final long[] HELD = new long[SLOTS];
    private static final long[] WANT = new long[SLOTS];
    private static final int[] COUNT = new int[SLOTS];
    private static final long[] FOUND = new long[SLOTS * PER_SECTION];
    private static final byte[] INFO = new byte[SLOTS * PER_SECTION];
    private static final long[] PICK = new long[MAX];
    private static final byte[] PICK_INFO = new byte[MAX];
    private static final long[] PICK_DIST = new long[MAX];
    private static final ValueNoise NOISE = new ValueNoise(0x70C4F11CL);

    public static final FloatBuffer FLAMES = BufferUtils.createFloatBuffer(MAX * 4);
    public static final float[] STATE = new float[4];
    public static volatile boolean bound;

    private static ClientLevel level;
    private static int picked;
    private static int sweep;
    private static long frameAt = Long.MIN_VALUE;

    static {
        Arrays.fill(HELD, EMPTY);
    }

    private static final String DECL_ANCHOR = "uvec3 _get_relative_chunk_coord(uint pos) {";
    private static final String CALL = "_sample_lightmap(u_LightTex, _vert_tex_light_coord)";
    private static final String DECL = """
            uniform vec4 u_LinggangoFlameState;
            uniform vec4 u_LinggangoFlames[48];

            vec4 linggango_flame_light(sampler2D lightMap, ivec2 uv, vec3 position) {
                vec2 coord = vec2(uv);
                int count = int(u_LinggangoFlameState.x);
                if (count > 0 && uv.x >= 24) {
                    float level = coord.x / 16.0;
                    float sum = 0.0;
                    float weight = 0.0;
                    for (int i = 0; i < count; i++) {
                        vec4 flame = u_LinggangoFlames[i];
                        vec3 gap = abs(position - flame.xyz);
                        float d = gap.x + gap.y + gap.z;
                        if (d > 16.5) continue;
                        float emit = floor(flame.w * 0.125);
                        float reach = emit + 0.5 - d;
                        float k = smoothstep(level - 2.5, level - 0.5, reach) * (1.0 - smoothstep(level + 1.5, level + 3.0, reach));
                        sum += k * (flame.w - emit * 8.0 - 4.0) * mix(u_LinggangoFlameState.y, u_LinggangoFlameState.z, smoothstep(1.5, 7.0, d));
                        weight += k;
                    }
                    coord.x += 16.0 * sum / max(weight, 1.0);
                }
                return texture(lightMap, clamp(coord / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
            }

            """;

    public static String patch(String source) {
        if (source.contains("linggango_flame_light(")) return source;
        if (!source.contains(DECL_ANCHOR) || !source.contains(CALL)) {
            Diagnostics.once("torch flicker could not find its place in the terrain shader, so flames stay steady");
            return source;
        }
        Diagnostics.once("torch flicker patched into the terrain shader");
        return source.replace(DECL_ANCHOR, DECL + DECL_ANCHOR)
                .replace(CALL, "linggango_flame_light(u_LightTex, _vert_tex_light_coord, position)");
    }

    public static boolean flame(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.TORCH || block == Blocks.WALL_TORCH || block == Blocks.LANTERN
                || block == Blocks.CAMPFIRE || block == Blocks.FIRE || block == Blocks.JACK_O_LANTERN
                || soul(block) || state.is(BlockTags.CANDLES) || state.is(BlockTags.CANDLE_CAKES);
    }

    private static boolean soul(Block block) {
        return block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH || block == Blocks.SOUL_LANTERN
                || block == Blocks.SOUL_CAMPFIRE || block == Blocks.SOUL_FIRE;
    }

    private static boolean on() {
        return bound && FxConfig.torchFlicker && DynamicFogHandler.active();
    }

    public static void onTorchFlickerTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        if (current == null || !on()) {
            picked = 0;
            return;
        }
        if (current != level) {
            level = current;
            Arrays.fill(HELD, EMPTY);
        }
        BlockPos camera = mc.gameRenderer.getMainCamera().getBlockPosition();
        int cx = SectionPos.blockToSectionCoord(camera.getX());
        int cy = SectionPos.blockToSectionCoord(camera.getY());
        int cz = SectionPos.blockToSectionCoord(camera.getZ());
        int fresh = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            int sx = wanted(cx, slot % SPAN, SPAN);
            int sy = wanted(cy, slot / SPAN % SPAN_Y, SPAN_Y);
            int sz = wanted(cz, slot / (SPAN * SPAN_Y), SPAN);
            WANT[slot] = SectionPos.asLong(sx, sy, sz);
            if (HELD[slot] != WANT[slot] && fresh < FRESH_PER_TICK) {
                scan(slot, sx, sy, sz);
                fresh++;
            }
        }
        for (int i = 0; i < SWEEP_PER_TICK; i++) {
            sweep = (sweep + 1) % SLOTS;
            long key = HELD[sweep];
            if (key == WANT[sweep]) scan(sweep, SectionPos.x(key), SectionPos.y(key), SectionPos.z(key));
        }
        pick(camera);
    }

    private static int wanted(int center, int slot, int span) {
        int low = center - span / 2;
        return low + Math.floorMod(slot - low, span);
    }

    private static void scan(int slot, int sx, int sy, int sz) {
        HELD[slot] = SectionPos.asLong(sx, sy, sz);
        COUNT[slot] = 0;
        LevelChunk chunk = level.getChunkSource().getChunk(sx, sz, false);
        if (chunk == null) return;
        int index = level.getSectionIndexFromSectionY(sy);
        if (index < 0 || index >= chunk.getSectionsCount()) return;
        LevelChunkSection section = chunk.getSection(index);
        if (section.hasOnlyAir() || !section.maybeHas(TorchFlicker::flame)) return;
        int base = slot * PER_SECTION, n = 0;
        for (int y = 0; y < 16 && n < PER_SECTION; y++) {
            for (int z = 0; z < 16 && n < PER_SECTION; z++) {
                for (int x = 0; x < 16 && n < PER_SECTION; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    if (!flame(state)) continue;
                    int emit = state.getLightEmission();
                    if (emit <= 0) continue;
                    FOUND[base + n] = BlockPos.asLong((sx << 4) + x, (sy << 4) + y, (sz << 4) + z);
                    INFO[base + n] = (byte) (Math.min(emit, 15) | (soul(state.getBlock()) ? 16 : 0));
                    n++;
                }
            }
        }
        COUNT[slot] = n;
    }

    private static void pick(BlockPos camera) {
        int n = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (HELD[slot] != WANT[slot]) continue;
            int base = slot * PER_SECTION;
            for (int i = 0; i < COUNT[slot]; i++) {
                long pos = FOUND[base + i];
                long dx = BlockPos.getX(pos) - camera.getX(), dy = BlockPos.getY(pos) - camera.getY(), dz = BlockPos.getZ(pos) - camera.getZ();
                long dist = dx * dx + dy * dy + dz * dz;
                if (n == MAX && dist >= PICK_DIST[MAX - 1]) continue;
                int at = n == MAX ? MAX - 1 : n++;
                while (at > 0 && PICK_DIST[at - 1] > dist) {
                    PICK[at] = PICK[at - 1];
                    PICK_INFO[at] = PICK_INFO[at - 1];
                    PICK_DIST[at] = PICK_DIST[at - 1];
                    at--;
                }
                PICK[at] = pos;
                PICK_INFO[at] = INFO[base + i];
                PICK_DIST[at] = dist;
            }
        }
        picked = n;
    }

    public static void update() {
        long now = Util.getMillis();
        if (now == frameAt) return;
        frameAt = now;
        if (!on() || level == null) {
            STATE[0] = 0F;
            Diagnostics.skip(Diagnostics.TORCH_FLICKER, bound ? "disabled" : "terrain shader not patched");
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float t = (now % 3_600_000L) / 1000F;
        FLAMES.clear();
        for (int i = 0; i < picked; i++) {
            long pos = PICK[i];
            int x = BlockPos.getX(pos), y = BlockPos.getY(pos), z = BlockPos.getZ(pos);
            int info = PICK_INFO[i];
            FLAMES.put((float) (x + 0.5 - camera.x)).put((float) (y + 0.5 - camera.y)).put((float) (z + 0.5 - camera.z))
                    .put((info & 15) * 8F + 4F + wave(t, (int) Mth.getSeed(x, y, z), (info & 16) != 0));
        }
        FLAMES.flip();
        STATE[0] = picked;
        STATE[1] = 0.6F;
        STATE[2] = 0.32F;
        Diagnostics.mark(Diagnostics.TORCH_FLICKER, Diagnostics.detail() ? picked + " flames" : null);
    }

    static float wave(float t, int hash, boolean soul) {
        float pace = soul ? 0.6F : 1F;
        float row = hash & 255;
        float shift = (hash >>> 8 & 1023) * 0.37F;
        float breath = NOISE.at(t * 0.8F * pace + shift, row) - 0.5F;
        float flutter = NOISE.at(t * 2.2F * pace + shift * 1.7F, (hash >>> 18 & 255)) - 0.5F;
        float gutter = Mth.clamp((NOISE.at(t * 0.4F * pace + shift * 0.6F, (hash >>> 24 & 255)) - 0.7F) / 0.22F, 0F, 1F);
        gutter = gutter * gutter * (3F - 2F * gutter);
        return (soul ? 0.6F : 1F) * (1.5F * breath + 0.7F * flutter - 1.4F * gutter);
    }

    public static final class Flames extends GlUniform<FloatBuffer> {
        public Flames(int index) {
            super(index);
        }

        @Override
        public void set(FloatBuffer value) {
            GL20C.glUniform4fv(index, value);
        }
    }
}
