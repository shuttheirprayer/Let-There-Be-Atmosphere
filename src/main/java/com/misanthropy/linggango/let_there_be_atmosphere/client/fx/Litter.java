package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeFx;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class Litter {
    private Litter() {}

    private static final ResourceLocation SHEET = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/litter.png");
    private static final ResourceLocation MODEL_SHEET = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/litter_models.png");
    private static final String MODELS = "/assets/" + Let_there_be_atmosphere.MODID + "/litter/models.json";
    private static final ResourceLocation STONE = new ResourceLocation("minecraft", "block/stone");

    static final int GRID = 5;
    static final int PER_CHUNK = 260;
    private static final int GROUND = GRID * GRID * PER_CHUNK;
    static final int LEAF_BASE = GROUND;
    private static final int LEAVES = FallingLeaves.N;
    private static final float ALIVE = 1.0E6F;
    static final int FLAT_MOVERS = 96;
    static final int FLAT_MOVE_BASE = GROUND + LEAVES;
    static final int SOLID_PER_CHUNK = 40;
    static final int SOLID_BASE = FLAT_MOVE_BASE + FLAT_MOVERS;
    static final int SOLID_MOVERS = 64;
    static final int SOLID_MOVE_BASE = SOLID_BASE + GRID * GRID * SOLID_PER_CHUNK;
    private static final int SOLIDS = GRID * GRID * SOLID_PER_CHUNK + SOLID_MOVERS;
    private static final int SLOTS = SOLID_BASE + SOLIDS;
    private static final int ROWS = 4;
    private static final int MAX_AGE = 200;
    private static final float[] PADDING = {0F, 0F, 0F, 0F, -1F, 0F, 0F, 0F};
    private static final float REBASE = 1800F;
    private static final double REACH = 1024.0;
    private static final float CULL = 35F;

    private static final int FALLING = 0;
    private static final int FLAT = 1;

    private static final int OAK = 0, ROUND = 1, LONG = 2, NEEDLES = 3, PETAL = 4, BROAD = 5, SMALL = 6, DRY = 7;
    private static final int TWIG = 8, FORK = 9, PEBBLES = 10, ROCK = 11, CRACKED = 12, MOSSY = 13, SHELL = 14, PETALS = 15;
    private static final int DAISY = 16, BUTTERCUP = 17, CORNFLOWER = 18, POPPY = 19, ALLIUM = 20, MARIGOLD = 21, COSMOS = 22, BLUEBELL = 23;
    private static final int BREATH = 24, CLOVER = 25, WHITE_CLOVER = 26, PUFF = 27, BROWN_CAP = 28, RED_CAP = 29, BLOSSOM = 30, CONE = 31;
    private static final int[] PETAL_COLORS = {0xFFFFFF, 0xF6B9D2, 0xFFE27A, 0xC9B6F2, 0xFFF0E0};
    private static final int[] MEADOW = {CORNFLOWER, DAISY, BUTTERCUP, ALLIUM, BREATH, COSMOS};
    private static final int[] GARDEN = {COSMOS, ALLIUM, POPPY, DAISY, MARIGOLD, BLUEBELL};
    private static final int[] FIELD = {DAISY, BUTTERCUP, PUFF, POPPY, CORNFLOWER};
    private static final int[] WOOD = {BLUEBELL, DAISY, BREATH, BUTTERCUP};
    private static final int[] DRYLAND = {MARIGOLD, BUTTERCUP, PUFF};

    static final float[][] ROW = new float[ROWS][SLOTS * 4];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(SLOTS * 4);
    private static final long[] HELD = new long[GRID * GRID];
    private static final int[] AGE = new int[GRID * GRID];
    private static final int[] USED = new int[GRID * GRID];
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos BELOW = new BlockPos.MutableBlockPos();
    private static final Map<Block, Integer> LEAF_COLORS = new IdentityHashMap<>();
    private static final int[] GRASS_CELLS = new int[16];
    private static final int[] FOLIAGE_CELLS = new int[16];
    private static final BlockPos.MutableBlockPos CELL = new BlockPos.MutableBlockPos();
    private static final Matrix4f MATRIX = new Matrix4f();

    private static long base = Util.getNanos();
    private static int data;
    private static int models = -1;
    private static float atlasStamp = Float.NaN;
    private static int modelStride;
    private static int pieces;
    private static int listed;
    private static final float[] LIST = new float[SOLIDS];
    private static final FloatBuffer LIST_UPLOAD = BufferUtils.createFloatBuffer(SOLIDS);
    private static final int[] DIRTY_FROM = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
    private static final int[] DIRTY_TO = {-1, -1, -1};
    private static int solidNext, solidEnd;
    private static boolean anchored;
    static double anchorX, anchorY, anchorZ;
    static ClientLevel level;
    private static int sweep;
    private static int leafHead;
    private static float leafCarry;
    private static float gustCarry;
    private static int grounded;

    static {
        java.util.Arrays.fill(HELD, Long.MIN_VALUE);
    }

    static float now() {
        return (Util.getNanos() - base) / 1.0E9F;
    }

    private static boolean groundOn() {
        return FxConfig.groundLitter && FxConfig.groundLitterAmount > 0.0;
    }

    private static boolean leavesOn() {
        return BiomeFx.on(FxConfig.fallingLeaves, BiomeFx.FALLING_LEAVES) && BiomeFx.amount(FxConfig.fallingLeavesAmount, BiomeFx.FALLING_LEAVES) > 0.0;
    }

    public static void onLitterTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        LocalPlayer player = mc.player;
        boolean any = groundOn() || leavesOn();
        if (current == null || player == null || !any || !DynamicFogHandler.active() || !current.dimensionType().hasSkyLight()) {
            if (anchored) reset();
            Diagnostics.skip(Diagnostics.LITTER, "disabled, or no sky here");
            return;
        }
        if (mc.isPaused()) return;
        if (current != level) {
            level = current;
            reset();
        }
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        if (!anchored || Math.abs(px - anchorX) > REACH || Math.abs(pz - anchorZ) > REACH || Math.abs(py - anchorY) > REACH) {
            reset();
            anchorX = Math.floor(px);
            anchorY = Math.floor(py);
            anchorZ = Math.floor(pz);
            anchored = true;
        }
        float now = now();
        if (now > REBASE) rebase(now);

        if (groundOn()) {
            groundTick(player.getBlockX(), player.getBlockZ(), now);
        } else if (grounded > 0) {
            clearGround();
        }
        if (leavesOn()) {
            leafTick(player, now);
            FallingLeaves.tick(player, now);
        } else if (FallingLeaves.count() > 0) {
            FallingLeaves.reset();
            java.util.Arrays.fill(ROW[1], LEAF_BASE * 4, (LEAF_BASE + LEAVES) * 4, 0F);
            markDirty(LEAF_BASE, LEAF_BASE + LEAVES);
        }
        if (groundOn()) LitterMotion.tick(player, now);
        Diagnostics.mark(Diagnostics.LITTER, Diagnostics.detail() ? grounded + " on the ground, " + LitterMotion.count() + " moving, " + FallingLeaves.count() + " leaves" : null);
    }

    private static void reset() {
        for (float[] row : ROW) java.util.Arrays.fill(row, 0F);
        java.util.Arrays.fill(HELD, Long.MIN_VALUE);
        java.util.Arrays.fill(USED, 0);
        grounded = 0;
        anchored = false;
        LitterMotion.reset();
        FallingLeaves.reset();
        markDirty(0, SLOTS);
    }

    private static void clearGround() {
        LitterMotion.reset();
        java.util.Arrays.fill(ROW[1], 0, GROUND * 4, 0F);
        java.util.Arrays.fill(ROW[1], FLAT_MOVE_BASE * 4, SLOTS * 4, 0F);
        markDirty(FLAT_MOVE_BASE, SLOTS);
        java.util.Arrays.fill(HELD, Long.MIN_VALUE);
        java.util.Arrays.fill(USED, 0);
        grounded = 0;
        markDirty(0, GROUND);
    }

    private static void rebase(float now) {
        for (int i = 0; i < SLOTS; i++) ROW[0][i * 4 + 3] = Math.max(ROW[0][i * 4 + 3] - now, -1000F);
        base = Util.getNanos();
        markDirty(0, SLOTS);
    }

    static void markDirty(int from, int to) {
        if (from < GROUND) range(0, from, Math.min(to, GROUND));
        if (to > GROUND && from < SOLID_BASE) range(1, Math.max(from, GROUND), Math.min(to, SOLID_BASE));
        if (to > SOLID_BASE) range(2, Math.max(from, SOLID_BASE), to);
    }

    private static void range(int which, int from, int to) {
        DIRTY_FROM[which] = Math.min(DIRTY_FROM[which], from);
        DIRTY_TO[which] = Math.max(DIRTY_TO[which], to);
    }

    private static void groundTick(int blockX, int blockZ, float now) {
        int cx = SectionPos.blockToSectionCoord(blockX);
        int cz = SectionPos.blockToSectionCoord(blockZ);
        for (int i = 0; i < AGE.length; i++) AGE[i]++;
        int pick = -1;
        for (int i = 0; i < HELD.length && pick < 0; i++) {
            int slot = (sweep + i) % HELD.length;
            if (HELD[slot] != key(wanted(cx, slot % GRID), wanted(cz, slot / GRID))) pick = slot;
        }
        if (pick < 0) {
            sweep = (sweep + 1) % HELD.length;
            if (AGE[sweep] < MAX_AGE) return;
            pick = sweep;
            if (Math.abs(wanted(cx, pick % GRID) - cx) <= 1 && Math.abs(wanted(cz, pick / GRID) - cz) <= 1) {
                AGE[pick] = 0;
                return;
            }
        }
        int sx = pick % GRID, sz = pick / GRID;
        int wx = wanted(cx, sx), wz = wanted(cz, sz);
        long key = key(wx, wz);
        boolean same = HELD[pick] == key;
        fillChunk(pick, wx, wz, same ? -1000F : now);
        HELD[pick] = ready(wx, wz) ? key : Long.MIN_VALUE;
        AGE[pick] = 0;
    }

    static boolean ready(int cx, int cz) {
        return level.hasChunk(cx, cz) && level.getLightEngine().lightOnInSection(SectionPos.of(cx, 0, cz));
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    private static int wanted(int center, int slot) {
        int low = center - GRID / 2;
        return low + Math.floorMod(slot - low, GRID);
    }

    private static void fillChunk(int slot, int cx, int cz, float birth) {
        int first = slot * PER_CHUNK;
        grounded -= USED[slot];
        java.util.Arrays.fill(ROW[1], first * 4, (first + PER_CHUNK) * 4, 0F);
        solidNext = SOLID_BASE + slot * SOLID_PER_CHUNK;
        solidEnd = solidNext + SOLID_PER_CHUNK;
        java.util.Arrays.fill(ROW[1], solidNext * 4, solidEnd * 4, 0F);
        java.util.Arrays.fill(ROW[2], solidNext * 4, solidEnd * 4, 0F);
        java.util.Arrays.fill(ROW[3], solidNext * 4, solidEnd * 4, 0F);
        markDirty(solidNext, solidEnd);
        java.util.Arrays.fill(GRASS_CELLS, -1);
        java.util.Arrays.fill(FOLIAGE_CELLS, -1);
        int used = 0;
        if (ready(cx, cz)) {
            double amount = FxConfig.groundLitterAmount;
            for (int i = 0; i < 256 && used < PER_CHUNK; i++) {
                int column = (i * 167 + 59) & 255;
                used += place(first + used, PER_CHUNK - used, (cx << 4) + (column & 15), (cz << 4) + (column >> 4), amount, birth);
            }
        }
        USED[slot] = used;
        grounded += used;
        markDirty(first, first + PER_CHUNK);
    }

    private static int place(int index, int room, int x, int z, double amount, float birth) {
        int h = hash(x, z);
        float roll = (h & 0xFFFF) / 65536F;
        if (roll > Math.min(0.95F, 0.5F * (float) amount)) return 0;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int ground = ground(x, top, z);
        if (ground == Integer.MIN_VALUE) return 0;
        CURSOR.set(x, ground, z);
        BELOW.set(x, ground - 1, z);
        BlockState above = level.getBlockState(CURSOR);
        BlockState floor = level.getBlockState(BELOW);
        if (!floor.getFluidState().isEmpty()) return 0;
        boolean open = above.isAir();
        if (!open && !(above.is(BlockTags.REPLACEABLE) && above.getFluidState().isEmpty() && !above.is(Blocks.SNOW))) return 0;
        boolean canopy = top > ground + 1;
        float pick = ((h >>> 16) & 0xFFFF) / 65536F;
        String id = name(level.getBiome(CURSOR));
        float r = roll / (float) amount;
        int light = light(CURSOR);
        int sprite;
        int tint = groundTint(floor);
        if (floor.is(BlockTags.DIRT)) {
            boolean conifer = id.contains("taiga") || id.contains("spruce") || id.contains("pine") || id.contains("conifer");
            if (canopy) {
                boolean gloomy = id.contains("dark") || id.contains("old_growth") || id.contains("swamp") || id.contains("mushroom");
                float pile = clump(x, z, 3.5F);
                if (r < 0.03F + 0.4F * pile * pile * pile) {
                    int foliage = cellColor(FOLIAGE_CELLS, x, z, BiomeColors.FOLIAGE_COLOR_RESOLVER);
                    int count = Math.min(room, 1 + (int) (pick * (pile > 0.7F ? 2.99F : 1.5F)));
                    for (int k = 0; k < count; k++) {
                        int kh = hash(x * 31 + k, z * 17 - k);
                        float kp = ((kh >>> 16) & 0xFFFF) / 65536F;
                        int leaf = conifer ? (kp < 0.8F ? NEEDLES : CONE) : leafSprite(kp);
                        if (leaf == CONE) {
                            writeSolid(CONE, x + 0.1F + 0.8F * ((kh & 0xFF) / 255F), ground, z + 0.1F + 0.8F * (((kh >>> 8) & 0xFF) / 255F),
                                    birth, ((kh >>> 4) & 0xFFFF) / 65536F, light, 0);
                            continue;
                        }
                        write(index + k, FLAT, leaf, x + 0.1F + 0.8F * ((kh & 0xFF) / 255F), ground + 0.01F + k * 0.002F,
                                z + 0.1F + 0.8F * (((kh >>> 8) & 0xFF) / 255F), birth, -1F, ((kh >>> 4) & 0xFFFF) / 65536F,
                                light, dried(foliage, kp), 0F, 0F, 0F, 0F, 0.5F, 0F);
                    }
                    return count;
                } else if (r >= 0.44F && r < 0.47F) {
                    sprite = conifer && pick < 0.6F ? CONE : pick < 0.6F ? TWIG : FORK;
                } else if (r >= 0.47F && r < (gloomy ? 0.49F : 0.475F)) {
                    if (!open) return 0;
                    sprite = pick < 0.7F ? BROWN_CAP : RED_CAP;
                } else if (r >= 0.43F && r < 0.44F) {
                    sprite = pick < 0.5F ? PEBBLES : MOSSY;
                } else {
                    return 0;
                }
            } else {
                boolean flowery = id.contains("flower") || id.contains("meadow") || id.contains("cherry") || id.contains("garden");
                float bed = clump(x, z, 6F);
                bed = bed * bed;
                float flowers = (flowery ? 0.22F : 0.1F) * bed;
                float clover = flowers + 0.025F * clump(x + 911, z - 317, 4F);
                float petals = clover + (id.contains("cherry") ? 0.12F : flowery ? 0.015F : 0.003F);
                if (r < flowers && open) {
                    sprite = flowerFor(id, x, z, pick);
                    tint = cellColor(GRASS_CELLS, x, z, BiomeColors.GRASS_COLOR_RESOLVER);
                } else if (r < clover) {
                    int grass = cellColor(GRASS_CELLS, x, z, BiomeColors.GRASS_COLOR_RESOLVER);
                    if (pick < 0.3F && open) {
                        sprite = WHITE_CLOVER;
                        tint = grass;
                    } else {
                        sprite = CLOVER;
                        tint = scale(grass, 0.82F);
                    }
                } else if (r < petals) {
                    sprite = pick < 0.5F ? PETALS : BLOSSOM;
                    tint = PETAL_COLORS[(int) (pick * 9.7F) % PETAL_COLORS.length];
                } else if (r < petals + 0.012F) {
                    sprite = pick < 0.45F ? PEBBLES : pick < 0.8F ? ROCK : CRACKED;
                } else if (r < petals + 0.016F) {
                    sprite = conifer ? CONE : pick < 0.6F ? TWIG : FORK;
                } else {
                    return 0;
                }
            }
        } else if (floor.is(BlockTags.SAND)) {
            boolean beach = id.contains("beach") || id.contains("shore") || id.contains("coast");
            if (beach && r < 0.04F) {
                sprite = SHELL;
            } else if (r < (beach ? 0.08F : 0.015F)) {
                sprite = pick < 0.5F ? PEBBLES : ROCK;
            } else {
                return 0;
            }
        } else if (floor.is(Blocks.GRAVEL) || floor.is(BlockTags.BASE_STONE_OVERWORLD)) {
            if (r >= 0.03F) return 0;
            sprite = pick < 0.35F ? PEBBLES : pick < 0.65F ? ROCK : pick < 0.85F ? CRACKED : MOSSY;
        } else {
            return 0;
        }
        float ox = 0.18F + 0.64F * (((h >>> 5) & 0xFF) / 255F);
        float oz = 0.18F + 0.64F * (((h >>> 13) & 0xFF) / 255F);
        if (model(sprite) >= 0) {
            writeSolid(sprite, x + ox, ground, z + oz, birth, ((h >>> 7) & 0xFFFF) / 65536F, light, stoneTint(floor, id, pick));
            return 0;
        }
        write(index, FLAT, sprite, x + ox, ground + 0.01F, z + oz, birth, -1F,
                ((h >>> 7) & 0xFFFF) / 65536F, light, tint, 0F, 0F, 0F, 0F, 0.5F, 0F);
        return 1;
    }

    static boolean rooted(int code) {
        int sprite = code - FLAT * 32;
        return (sprite >= DAISY && sprite <= BREATH) || sprite == PUFF;
    }

    private static int model(int sprite) {
        return switch (sprite) {
            case TWIG -> 0;
            case FORK -> 1;
            case PEBBLES -> 2;
            case ROCK -> 3;
            case CRACKED -> 4;
            case MOSSY -> 5;
            case CONE -> 6;
            case SHELL -> 7;
            case BROWN_CAP -> 8;
            case RED_CAP -> 9;
            default -> -1;
        };
    }

    static boolean cap(int model) {
        return model == 8 || model == 9;
    }

    private static int variant(int sprite, float seed) {
        float pick = (seed * 13.37F) % 1F;
        return switch (sprite) {
            case PEBBLES -> pick < 0.5F ? 2 : 10;
            case ROCK -> pick < 0.4F ? 3 : pick < 0.7F ? 11 : 12;
            case MOSSY -> pick < 0.5F ? 5 : 13;
            default -> model(sprite);
        };
    }

    private static int stoneTint(BlockState floor, String biome, float pick) {
        if (floor.is(Blocks.RED_SAND) || biome.contains("badlands")) return pick < 0.5F ? 0xF2B48E : 0xFAD0AA;
        if (floor.is(BlockTags.SAND) || biome.contains("desert")) return pick < 0.5F ? 0xFFF3DA : 0xF6E6C4;
        return 0;
    }

    private static void writeSolid(int sprite, double x, double y, double z, float birth, float seed, int light, int tint) {
        if (solidNext >= solidEnd || LitterMotion.displaced((float) (x - anchorX), (float) (z - anchorZ))) return;
        int o = solidNext++ * 4;
        ROW[0][o] = (float) (x - anchorX);
        ROW[0][o + 1] = (float) (y - anchorY);
        ROW[0][o + 2] = (float) (z - anchorZ);
        ROW[0][o + 3] = birth;
        ROW[1][o] = variant(sprite, seed);
        ROW[1][o + 1] = seed;
        ROW[1][o + 2] = light;
        ROW[1][o + 3] = -1F;
        ROW[2][o + 3] = -1000F;
        ROW[3][o + 1] = tint;
    }

    private static int flowerFor(String id, int x, int z, float pick) {
        int[] set = id.contains("meadow") ? MEADOW
                : id.contains("flower") || id.contains("cherry") || id.contains("garden") ? GARDEN
                : id.contains("forest") || id.contains("birch") || id.contains("grove") ? WOOD
                : id.contains("savanna") || id.contains("badlands") || id.contains("dry") ? DRYLAND
                : FIELD;
        int patch = hash(x >> 3, z >> 3);
        int choice = pick < 0.75F ? (patch >>> 8) : (int) (pick * 9973F);
        return set[Math.floorMod(choice, set.length)];
    }

    private static float clump(int x, int z, float scale) {
        float fx = x / scale, fz = z / scale;
        int x0 = Mth.floor(fx), z0 = Mth.floor(fz);
        float tx = fx - x0, tz = fz - z0;
        tx = tx * tx * (3F - 2F * tx);
        tz = tz * tz * (3F - 2F * tz);
        float a = (hash(x0, z0) & 0xFFFF) / 65535F, b = (hash(x0 + 1, z0) & 0xFFFF) / 65535F;
        float c = (hash(x0, z0 + 1) & 0xFFFF) / 65535F, d = (hash(x0 + 1, z0 + 1) & 0xFFFF) / 65535F;
        return Mth.lerp(tz, Mth.lerp(tx, a, b), Mth.lerp(tx, c, d));
    }

    private static int cellColor(int[] cells, int x, int z, net.minecraft.world.level.ColorResolver resolver) {
        int cell = ((z & 15) >> 2) * 4 + ((x & 15) >> 2);
        if (cells[cell] < 0) {
            CELL.set((x & ~3) + 2, CURSOR.getY(), (z & ~3) + 2);
            cells[cell] = level.getBlockTint(CELL, resolver) & 0xFFFFFF;
        }
        return cells[cell];
    }

    private static int groundTint(BlockState floor) {
        if (floor.is(Blocks.GRASS_BLOCK)) return scale(cellColor(GRASS_CELLS, BELOW.getX(), BELOW.getZ(), BiomeColors.GRASS_COLOR_RESOLVER), 0.62F);
        return scale(floor.getMapColor(level, BELOW).col, 0.85F);
    }

    private static int ground(int x, int top, int z) {
        for (int y = top - 1; y > top - 40; y--) {
            BELOW.set(x, y, z);
            BlockState state = level.getBlockState(BELOW);
            if (!state.getFluidState().isEmpty() || (state.blocksMotion() && !state.is(BlockTags.LEAVES))) return y + 1;
        }
        return Integer.MIN_VALUE;
    }

    private static String name(Holder<Biome> biome) {
        return biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
    }

    private static int leafSprite(float pick) {
        return pick < 0.25F ? OAK : pick < 0.45F ? BROAD : pick < 0.6F ? ROUND : pick < 0.72F ? LONG : pick < 0.86F ? SMALL : DRY;
    }

    private static int dried(int color, float pick) {
        float t = 0.3F + 0.6F * pick;
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        float warm = (pick * 7.3F) % 1F;
        r = (int) Mth.lerp(t, r, Mth.lerp(warm, 168, 196));
        g = (int) Mth.lerp(t, g, Mth.lerp(warm, 128, 112));
        b = (int) Mth.lerp(t, b, Mth.lerp(warm, 62, 44));
        return scale((r << 16) | (g << 8) | b, 1.05F);
    }

    private static int scale(int color, float f) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((color & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    static int light(BlockPos pos) {
        return level.getBrightness(LightLayer.BLOCK, pos) * 16 + level.getBrightness(LightLayer.SKY, pos);
    }

    private static int hash(int x, int z) {
        int h = x * 0x27D4EB2D ^ z * 0x165667B1;
        h = (h ^ (h >>> 15)) * 0x85EBCA6B;
        h = (h ^ (h >>> 13)) * 0xC2B2AE35;
        return h ^ (h >>> 16);
    }

    private static void leafTick(LocalPlayer player, float now) {
        RandomSource random = level.random;
        float seconds = Wind.seconds(level, 0F);
        float gust = Wind.gust(seconds);
        float force = (0.4F + 1.2F * gust) * (float) FxConfig.windStrength;
        leafCarry += (0.56F + 1.2F * gust) * (float) BiomeFx.amount(FxConfig.fallingLeavesAmount, BiomeFx.FALLING_LEAVES) * (1F + level.getRainLevel(1F));
        int tries = 0;
        for (; leafCarry >= 1F && tries < 40; tries++) {
            int x = Mth.floor(player.getX() + (random.nextDouble() * 2.0 - 1.0) * 22.0);
            int z = Mth.floor(player.getZ() + (random.nextDouble() * 2.0 - 1.0) * 22.0);
            if (!ready(x >> 4, z >> 4)) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            CURSOR.set(x, top - 1, z);
            BlockState leaves = level.getBlockState(CURSOR);
            if (!leaves.is(BlockTags.LEAVES)) continue;
            leafCarry -= 1F;
            int bottom = top - 1;
            while (bottom > top - 10) {
                CURSOR.setY(bottom - 1);
                if (!level.getBlockState(CURSOR).is(BlockTags.LEAVES)) break;
                bottom--;
            }
            CURSOR.setY(bottom - 1);
            if (!level.getBlockState(CURSOR).isAir()) continue;
            int ground = ground(x, bottom, z);
            if (ground == Integer.MIN_VALUE || bottom - ground > 28 || Math.abs(bottom - player.getY()) > 24) continue;
            CURSOR.setY(bottom);
            int sprite;
            int tint = Minecraft.getInstance().getBlockColors().getColor(leaves, level, CURSOR, 0);
            String path = ForgeRegistries.BLOCKS.getKey(leaves.getBlock()).getPath();
            if (path.contains("cherry")) {
                sprite = PETAL;
                tint = 0xFFFFFF;
            } else if (path.contains("spruce") || path.contains("pine") || path.contains("fir") || path.contains("larch")) {
                sprite = NEEDLES;
            } else if (path.contains("birch") || path.contains("aspen")) {
                sprite = ROUND;
            } else if (path.contains("jungle") || path.contains("acacia") || path.contains("willow") || path.contains("mangrove")) {
                sprite = random.nextFloat() < 0.5F ? BROAD : SMALL;
            } else {
                float roll = random.nextFloat();
                sprite = roll < 0.4F ? OAK : roll < 0.75F ? BROAD : SMALL;
            }
            if (sprite != PETAL && sprite != NEEDLES && random.nextFloat() < 0.015F) sprite = LONG;
            if (tint == -1 && sprite != PETAL) tint = averageColor(leaves);
            int index = freeLeaf();
            if (index < 0) break;
            float angle = Wind.angle(seconds);
            float drift = force * (sprite == PETAL ? 0.04F : 0.03F);
            float sx = x + random.nextFloat(), sz = z + random.nextFloat();
            CURSOR.set(x, bottom - 1, z);
            write(index, FALLING, sprite, sx, bottom - 0.05F, sz, now, ALIVE,
                    random.nextFloat(), light(CURSOR), tint, ground, 0F, 0F, 0F, 0.85F + random.nextFloat() * 0.35F, 0F);
            FallingLeaves.spawn(index - LEAF_BASE, sx, bottom - 0.05F, sz, Mth.cos(angle) * drift, -0.004F, Mth.sin(angle) * drift,
                    sprite == PETAL, random, now);
        }
        if (tries >= 40) leafCarry = Math.min(leafCarry, 4F);
        if (gust > 0.62F) gustCarry += (gust - 0.62F) * 0.22F * (float) BiomeFx.amount(FxConfig.fallingLeavesAmount, BiomeFx.FALLING_LEAVES) * (float) FxConfig.windStrength;
        float angle = Wind.angle(seconds);
        float wx = Mth.cos(angle), wz = Mth.sin(angle);
        for (tries = 0; gustCarry >= 1F && tries < 12; tries++) {
            int x = Mth.floor(player.getX() - wx * 9.0 + (random.nextDouble() * 2.0 - 1.0) * 14.0);
            int z = Mth.floor(player.getZ() - wz * 9.0 + (random.nextDouble() * 2.0 - 1.0) * 14.0);
            if (!ready(x >> 4, z >> 4)) continue;
            int ground = ground(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
            if (ground == Integer.MIN_VALUE || Math.abs(ground - player.getY()) > 12) continue;
            float reach = (2.2F + 2.4F) * gust * 3.5F;
            int fx = Mth.floor(x + wx * reach), fz = Mth.floor(z + wz * reach);
            if (!ready(fx >> 4, fz >> 4) || Math.abs(level.getHeight(Heightmap.Types.MOTION_BLOCKING, fx, fz) - ground) > 1) continue;
            BELOW.set(x, ground - 1, z);
            if (!level.getBlockState(BELOW).is(BlockTags.DIRT)) continue;
            CURSOR.set(x, ground, z);
            if (level.getBrightness(LightLayer.SKY, CURSOR) < 10) continue;
            gustCarry -= 1F;
            boolean dry = random.nextFloat() < 0.3F;
            int sprite = dry ? DRY : random.nextFloat() < 0.5F ? OAK : SMALL;
            int tint = dry ? 0xFFFFFF : BiomeColors.getAverageFoliageColor(level, CURSOR);
            int index = freeLeaf();
            if (index < 0) break;
            float push = (0.11F + random.nextFloat() * 0.12F) * gust;
            float side = (random.nextFloat() - 0.5F) * 0.8F;
            double sx = x + random.nextFloat(), sy = ground + 0.1F, sz = z + random.nextFloat();
            write(index, FALLING, sprite, sx, sy, sz, now, ALIVE, random.nextFloat(), light(CURSOR), tint, ground,
                    0F, 0F, 0F, 0.85F + random.nextFloat() * 0.3F, 0F);
            FallingLeaves.spawn(index - LEAF_BASE, sx, sy, sz, (wx - wz * side) * push, 0.05F + random.nextFloat() * 0.06F,
                    (wz + wx * side) * push, false, random, now);
        }
        if (tries >= 12) gustCarry = Math.min(gustCarry, 3F);
    }

    private static int freeLeaf() {
        for (int n = 0; n < LEAVES; n++) {
            int i = (leafHead + n) % LEAVES;
            if (!FallingLeaves.live(i)) {
                leafHead = (i + 1) % LEAVES;
                return LEAF_BASE + i;
            }
        }
        return -1;
    }

    private static int averageColor(BlockState state) {
        return LEAF_COLORS.computeIfAbsent(state.getBlock(), block -> {
            TextureAtlasSprite icon = Minecraft.getInstance().getBlockRenderer().getBlockModel(state).getParticleIcon();
            long r = 0, g = 0, b = 0, n = 0;
            int w = icon.contents().width(), hgt = icon.contents().height();
            for (int y = 0; y < hgt; y++) {
                for (int x = 0; x < w; x++) {
                    int c = icon.getPixelRGBA(0, x, y);
                    if ((c >>> 24) < 128) continue;
                    r += c & 0xFF;
                    g += (c >> 8) & 0xFF;
                    b += (c >> 16) & 0xFF;
                    n++;
                }
            }
            if (n == 0) return 0x6A8F3A;
            return scale((int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n), 1.25F);
        });
    }

    private static void write(int index, int kind, int sprite, double x, double y, double z, float birth, float life,
                              float seed, int light, int tint, float landY, float driftX, float driftZ,
                              float speed, float size, float flag) {
        if (kind == FLAT && LitterMotion.displaced((float) (x - anchorX), (float) (z - anchorZ))) return;
        int o = index * 4;
        ROW[0][o] = (float) (x - anchorX);
        ROW[0][o + 1] = (float) (y - anchorY);
        ROW[0][o + 2] = (float) (z - anchorZ);
        ROW[0][o + 3] = birth;
        ROW[1][o] = kind * 32 + sprite;
        ROW[1][o + 1] = seed;
        ROW[1][o + 2] = light;
        ROW[1][o + 3] = life;
        ROW[2][o] = tint & 0xFFFFFF;
        ROW[2][o + 1] = (float) (landY - anchorY);
        ROW[2][o + 2] = driftX;
        ROW[2][o + 3] = driftZ;
        ROW[3][o] = speed;
        ROW[3][o + 1] = 0F;
        ROW[3][o + 2] = size;
        ROW[3][o + 3] = flag;
        markDirty(index, index + 1);
    }

    public static void onRenderLitterStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) return;
        if (!anchored || LightTint.lightmap == null) return;
        SkyProgram program = SkyProgram.LITTER;
        if (!program.ready()) return;

        upload();
        Vec3 eye = event.getCamera().getPosition();
        float ox = (float) (anchorX - eye.x);
        float oy = (float) (anchorY - eye.y);
        float oz = (float) (anchorZ - eye.z);
        MATRIX.set(event.getPoseStack().last().pose()).translate(ox, oy, oz);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1F, -2F);
        program.begin();
        SkyProgram.texture(2, data);
        SkyProgram.texture(1, LightTint.lightmap.getId());
        SkyProgram.texture(0, SkyProgram.texture(SHEET));
        program.transform(0, MATRIX);
        GL20.glUniform3f(program.uniform(1), ox, oy, oz);
        GL20.glUniform4f(program.uniform(2), now(), RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), 0F);
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.uniform(3), fog[0], fog[1], fog[2], CULL);
        program.draw(SOLID_BASE * 6);
        program.end();
        SkyProgram solid = SkyProgram.LITTER_SOLID;
        if (models < 0 || atlasMoved()) loadModels();
        if (solid.ready() && modelStride > 0 && listNear(ox, oy, oz) > 0) {
            solid.begin();
            SkyProgram.texture(5, SkyProgram.texture(TextureAtlas.LOCATION_BLOCKS));
            SkyProgram.texture(4, pieces);
            SkyProgram.texture(3, models);
            SkyProgram.texture(2, data);
            SkyProgram.texture(1, LightTint.lightmap.getId());
            SkyProgram.texture(0, SkyProgram.texture(MODEL_SHEET));
            solid.transform(0, MATRIX);
            GL20.glUniform3f(solid.uniform(1), ox, oy, oz);
            GL20.glUniform4f(solid.uniform(2), now(), RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), SOLID_BASE);
            GL20.glUniform4f(solid.uniform(3), fog[0], fog[1], fog[2], CULL);
            GL20.glUniform4f(solid.uniform(4), modelStride, 0F, 0F, 0F);
            solid.draw(listed * modelStride);
            solid.end();
        }
        RenderSystem.polygonOffset(0F, 0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
    }

    private static int listNear(float ox, float oy, float oz) {
        int count = 0;
        boolean changed = false;
        float reach = (CULL + 1F) * (CULL + 1F);
        for (int s = SOLID_BASE; s < SLOTS; s++) {
            int o = s * 4;
            if (ROW[1][o + 3] == 0F) continue;
            float dx = ROW[0][o] + ox, dy = ROW[0][o + 1] + oy, dz = ROW[0][o + 2] + oz;
            if (dx * dx + dy * dy + dz * dz > reach) continue;
            if (count >= listed || LIST[count] != s) changed = true;
            LIST[count++] = s;
        }
        if (count != listed) changed = true;
        listed = count;
        if (pieces == 0) {
            pieces = GlStateManager._genTexture();
            GlStateManager._bindTexture(pieces);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            SkyHeights.unpack();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, SOLIDS, 1, 0, GL11.GL_RED, GL11.GL_FLOAT, (FloatBuffer) null);
            changed = count > 0;
        }
        if (changed && count > 0) {
            GlStateManager._bindTexture(pieces);
            SkyHeights.unpack();
            LIST_UPLOAD.clear();
            LIST_UPLOAD.put(LIST, 0, count).flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, count, 1, GL11.GL_RED, GL11.GL_FLOAT, LIST_UPLOAD);
        }
        return count;
    }

    private static boolean atlasMoved() {
        TextureAtlasSprite stone = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(STONE);
        return stone.getU0() + stone.getV0() * 7F != atlasStamp;
    }

    private static void loadModels() {
        if (models > 0) GlStateManager._deleteTexture(models);
        models = 0;
        java.util.function.Function<ResourceLocation, TextureAtlasSprite> atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite stone = atlas.apply(STONE);
        atlasStamp = stone.getU0() + stone.getV0() * 7F;
        try (InputStream in = Litter.class.getResourceAsStream(MODELS)) {
            if (in == null) throw new IOException("missing " + MODELS);
            com.google.gson.JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray list = root.getAsJsonArray("models");
            JsonArray swatches = root.getAsJsonArray("swatches");
            int stride = 0;
            for (JsonElement model : list) stride = Math.max(stride, model.getAsJsonArray().size());
            FloatBuffer buffer = BufferUtils.createFloatBuffer(stride * 8 * list.size());
            for (JsonElement model : list) {
                JsonArray vertices = model.getAsJsonArray();
                for (int v = 0; v < stride; v++) {
                    if (v >= vertices.size()) {
                        buffer.put(PADDING);
                        continue;
                    }
                    JsonArray f = vertices.get(v).getAsJsonArray();
                    float u = f.get(3).getAsFloat(), w = f.get(4).getAsFloat();
                    for (JsonElement entry : swatches) {
                        JsonArray sw = entry.getAsJsonArray();
                        float sx = sw.get(0).getAsFloat(), sy = sw.get(1).getAsFloat();
                        if (u < sx || w < sy || u > sx + sw.get(2).getAsFloat() || w > sy + sw.get(3).getAsFloat()) continue;
                        if (sw.get(4).isJsonNull()) break;
                        TextureAtlasSprite sprite = atlas.apply(new ResourceLocation(sw.get(4).getAsString()));
                        float lu = u - sx, lv = w - sy;
                        boolean turned = sw.get(5).getAsBoolean();
                        u = sprite.getU(turned ? lv : lu) + 2F;
                        w = sprite.getV(turned ? lu : lv);
                        break;
                    }
                    buffer.put(f.get(0).getAsFloat()).put(f.get(1).getAsFloat()).put(f.get(2).getAsFloat()).put(u).put(w);
                    for (int k = 5; k < 8; k++) buffer.put(f.get(k).getAsFloat());
                }
            }
            buffer.flip();
            models = GlStateManager._genTexture();
            GlStateManager._bindTexture(models);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            SkyHeights.unpack();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, stride * 2, list.size(), 0, GL11.GL_RGBA, GL11.GL_FLOAT, buffer);
            modelStride = stride;
        } catch (IOException | RuntimeException e) {
            Diagnostics.LOG.error("[atmosphere] litter models failed to load", e);
        }
    }

    private static void upload() {
        if (data == 0) {
            data = GlStateManager._genTexture();
            GlStateManager._bindTexture(data);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            SkyHeights.unpack();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, SLOTS, ROWS, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            markDirty(0, SLOTS);
        }
        for (int which = 0; which < 3; which++) {
            int from = DIRTY_FROM[which];
            int count = DIRTY_TO[which] - from;
            if (count <= 0) continue;
            GlStateManager._bindTexture(data);
            SkyHeights.unpack();
            for (int row = 0; row < ROWS; row++) {
                UPLOAD.clear();
                UPLOAD.put(ROW[row], from * 4, count * 4).flip();
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, from, row, count, 1, GL11.GL_RGBA, GL11.GL_FLOAT, UPLOAD);
            }
            DIRTY_FROM[which] = Integer.MAX_VALUE;
            DIRTY_TO[which] = -1;
        }
    }
}
