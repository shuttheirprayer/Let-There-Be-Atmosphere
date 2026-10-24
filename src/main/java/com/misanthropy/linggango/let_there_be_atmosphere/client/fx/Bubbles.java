package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class Bubbles {
    private Bubbles() {}

    private static final int KIND = 32;
    private static final int STREAMS = 8;
    private static final double RADIUS = 18.0;
    private static final int DEPTH = 24;
    private static final int CLIMB = 24;
    private static final double[] X = new double[STREAMS];
    private static final double[] Y = new double[STREAMS];
    private static final double[] Z = new double[STREAMS];
    private static final int[] TOP = new int[STREAMS];
    private static final int[] TTL = new int[STREAMS];
    private static final float[] RATE = new float[STREAMS];
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos SIDE = new BlockPos.MutableBlockPos();

    private static int streams;
    private static float found;

    public static void onBubblesTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) return;
        if (!FxConfig.underwaterBubbles || FxConfig.underwaterBubblesAmount <= 0.0 || !DynamicFogHandler.active()
                || CameraFluid.of(mc.gameRenderer.getMainCamera()) != FogType.WATER) {
            streams = 0;
            Diagnostics.skip(Diagnostics.BUBBLES, "disabled or the camera is not underwater");
            return;
        }
        if (!CaveMotes.resolve()) return;
        RandomSource random = level.random;
        double amount = FxConfig.underwaterBubblesAmount;
        int limit = Math.min(STREAMS, 1 + (int) Math.round(3.0 * amount));
        found += 0.25F * (float) Math.min(amount, 2.0);
        for (int tries = 0; found >= 1F && tries < 3; tries++, found -= 1F) {
            if (streams < limit) plant(level, player, random);
        }
        found = Math.min(found, 2F);
        int spawned = 0;
        for (int i = streams - 1; i >= 0; i--) {
            double dx = X[i] - player.getX(), dz = Z[i] - player.getZ();
            if (--TTL[i] <= 0 || dx * dx + dz * dz > 30.0 * 30.0 || i >= limit) {
                streams--;
                X[i] = X[streams];
                Y[i] = Y[streams];
                Z[i] = Z[streams];
                TOP[i] = TOP[streams];
                TTL[i] = TTL[streams];
                RATE[i] = RATE[streams];
                continue;
            }
            if (random.nextFloat() >= RATE[i] * Math.min((float) amount, 1.5F)) continue;
            int variant = random.nextInt(4);
            double x = X[i] + (random.nextDouble() - 0.5) * 0.3;
            double z = Z[i] + (random.nextDouble() - 0.5) * 0.3;
            float rise = (float) (TOP[i] + 0.8 - Y[i]) / (0.55F + 0.2F * variant);
            CaveMotes.emit(KIND, x, Y[i], z, 15, 15, rise, variant + random.nextFloat());
            spawned++;
        }
        Diagnostics.mark(Diagnostics.BUBBLES, Diagnostics.detail() ? streams + " streams, +" + spawned : null);
    }

    private static void plant(ClientLevel level, LocalPlayer player, RandomSource random) {
        int x = Mth.floor(player.getX() + (random.nextDouble() * 2.0 - 1.0) * RADIUS);
        int z = Mth.floor(player.getZ() + (random.nextDouble() * 2.0 - 1.0) * RADIUS);
        int y = Mth.floor(player.getEyeY()) + random.nextInt(3);
        CURSOR.set(x, y, z);
        if (!level.getFluidState(CURSOR).is(FluidTags.WATER)) return;
        float weight = 0F;
        int floor = y;
        for (int by = y; by > y - DEPTH && weight == 0F; by--) {
            CURSOR.set(x, by, z);
            BlockState state = level.getBlockState(CURSOR);
            if (state.is(Blocks.BUBBLE_COLUMN)) return;
            if (state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)
                    || state.is(Blocks.SEA_PICKLE)) {
                weight = 0.9F;
                floor = by;
            } else if (state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.SOUL_SAND)) {
                weight = 1F;
                floor = by + 1;
            } else if (!level.getFluidState(CURSOR).is(FluidTags.WATER)) {
                if (state.isAir() || !state.isFaceSturdy(level, CURSOR, Direction.UP)) return;
                weight = 0.3F;
                floor = by + 1;
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    BlockState next = level.getBlockState(SIDE.setWithOffset(CURSOR, side));
                    if (next.is(Blocks.MAGMA_BLOCK) || next.is(Blocks.SOUL_SAND)) weight = 1F;
                }
            }
        }
        if (weight == 0F || random.nextFloat() > weight) return;
        int top = floor;
        for (int i = 0; i < CLIMB; i++) {
            if (!level.getFluidState(CURSOR.set(x, top + 1, z)).is(FluidTags.WATER)) break;
            top++;
        }
        if (top - floor < 2) return;
        int slot = streams++;
        X[slot] = x + 0.5;
        Y[slot] = floor + 0.2;
        Z[slot] = z + 0.5;
        TOP[slot] = top;
        TTL[slot] = 100 + random.nextInt(160);
        RATE[slot] = weight > 0.5F ? 0.2F : 0.1F;
    }
}
