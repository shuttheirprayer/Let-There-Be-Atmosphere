package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;

@OnlyIn(Dist.CLIENT)
public final class CaveDrips {
    private CaveDrips() {}

    private static final int DRIP = 40;
    private static final int RADIUS = 12;
    private static final int RISE = 8;
    private static final int ATTEMPTS = 16;
    private static final int FALL_LIMIT = 24;
    private static final int PENDING = 32;
    private static final float RATE = 0.05F;
    private static final float GRAVITY_HALF = 7F;
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos LOW = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos NEAR = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos FLOOR = new BlockPos.MutableBlockPos();
    private static final int[] DUE = new int[PENDING];
    private static final float[] SPOT = new float[PENDING * 3];

    private static int pending;
    private static int clock;
    private static float budget;
    private static float landY;
    private static boolean landWater;

    public static void onCaveDripsTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) return;
        if (!FxConfig.caveDrips || !DynamicFogHandler.active() || !level.dimensionType().hasSkyLight()) {
            pending = 0;
            Diagnostics.skip(Diagnostics.CAVE_DRIPS, "disabled or no sky in this dimension");
            return;
        }
        if (CameraFluid.of(mc.gameRenderer.getMainCamera()) != FogType.NONE || !CaveMotes.resolve()) return;
        clock++;
        ripples();
        if (level.getBrightness(LightLayer.SKY, BlockPos.containing(player.getX(), player.getEyeY(), player.getZ())) > 11) {
            Diagnostics.skip(Diagnostics.CAVE_DRIPS, "standing in open air");
            return;
        }
        budget = Math.min(budget + RATE, 2F);
        int made = 0;
        for (int i = 0; i < ATTEMPTS && budget >= 1F; i++) {
            if (attempt(level, player, level.random)) {
                budget -= 1F;
                made++;
            }
        }
        Diagnostics.mark(Diagnostics.CAVE_DRIPS, Diagnostics.detail() ? "+" + made + ", " + pending + " ripples queued" : null);
    }

    private static boolean attempt(ClientLevel level, LocalPlayer player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
        int z = player.getBlockZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
        int y = player.getBlockY() + random.nextInt(RISE * 2 + 1) - RISE + 1;
        if (!level.hasChunk(x >> 4, z >> 4) || y <= level.getMinBuildHeight()) return false;
        CURSOR.set(x, y, z);
        if (!level.getBlockState(CURSOR).isAir()) return false;
        BlockState ceiling = null;
        for (int k = 0; k < RISE && ceiling == null; k++) {
            CURSOR.move(Direction.UP);
            BlockState state = level.getBlockState(CURSOR);
            if (!state.isAir()) ceiling = state;
        }
        if (ceiling == null) return false;
        int cy = CURSOR.getY();
        long h = Mth.getSeed(x, cy, z) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        if ((h & 31L) != 0L) return false;
        if (ceiling.is(Blocks.POINTED_DRIPSTONE) || !ceiling.isFaceSturdy(level, CURSOR, Direction.DOWN)) return false;
        LOW.set(x, cy - 1, z);
        if (level.getBrightness(LightLayer.SKY, LOW) > 3 || !level.getLightEngine().lightOnInSection(SectionPos.of(LOW))) return false;
        if (!fall(level, x, cy, z)) return false;
        if (!landWater && !lush(level) && !wet(level, x, cy, z)) return false;
        int q = Mth.clamp(Mth.floor((cy - landY) * 16F), 8, 1023);
        float seed = 0.05F + 0.9F * random.nextFloat();
        float bead = 0.5F + 0.9F * seed;
        float fall = (float) Math.sqrt(Math.max(q / 16F - 0.125F, 0F) / GRAVITY_HALF);
        double px = x + 0.25 + 0.5 * ((h >>> 5) & 255L) / 255.0;
        double pz = z + 0.25 + 0.5 * ((h >>> 13) & 255L) / 255.0;
        CaveMotes.emit(DRIP, px, cy, pz, level.getBrightness(LightLayer.BLOCK, LOW), level.getBrightness(LightLayer.SKY, LOW),
                bead + fall + (landWater ? 0.05F : 0.4F), q * 2 + (landWater ? 1 : 0) + seed);
        if (landWater && pending < PENDING) {
            DUE[pending] = clock + Math.round((bead + fall) * 20F);
            SPOT[pending * 3] = (float) px;
            SPOT[pending * 3 + 1] = landY;
            SPOT[pending * 3 + 2] = (float) pz;
            pending++;
        }
        return true;
    }

    private static void ripples() {
        for (int i = pending - 1; i >= 0; i--) {
            if (DUE[i] > clock) continue;
            Ripples.emit(SPOT[i * 3], SPOT[i * 3 + 1], SPOT[i * 3 + 2], 0.25F, 0.5F);
            pending--;
            DUE[i] = DUE[pending];
            System.arraycopy(SPOT, pending * 3, SPOT, i * 3, 3);
        }
    }

    private static boolean fall(ClientLevel level, int x, int cy, int z) {
        for (int y = cy - 1; y >= cy - FALL_LIMIT; y--) {
            FLOOR.set(x, y, z);
            FluidState fluid = level.getFluidState(FLOOR);
            if (fluid.is(FluidTags.WATER)) {
                landY = y + fluid.getHeight(level, FLOOR);
                landWater = true;
                return true;
            }
            if (!fluid.isEmpty()) return false;
            VoxelShape shape = level.getBlockState(FLOOR).getCollisionShape(level, FLOOR);
            if (!shape.isEmpty()) {
                landY = y + (float) shape.max(Direction.Axis.Y);
                landWater = false;
                return true;
            }
        }
        return false;
    }

    private static boolean lush(ClientLevel level) {
        Holder<Biome> biome = level.getBiome(LOW);
        return biome.is(Biomes.LUSH_CAVES) || biome.is(Biomes.DRIPSTONE_CAVES);
    }

    private static boolean wet(ClientLevel level, int x, int cy, int z) {
        for (int dy = 1; dy <= 4; dy++) {
            if (water(level, x, cy + dy, z)) return true;
        }
        for (int dy = 0; dy <= 3; dy++) {
            if (water(level, x + 1, cy + dy, z) || water(level, x - 1, cy + dy, z)
                    || water(level, x, cy + dy, z + 1) || water(level, x, cy + dy, z - 1)) return true;
        }
        return false;
    }

    private static boolean water(ClientLevel level, int x, int y, int z) {
        return level.getFluidState(NEAR.set(x, y, z)).is(FluidTags.WATER);
    }
}
