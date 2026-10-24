package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class Rustle {
    private Rustle() {}

    private static final RandomSource RANDOM = RandomSource.create();
    private static double lastX, lastZ;
    private static boolean tracked;
    private static float walked;
    private static int played;

    static void tick(ClientLevel level, LocalPlayer player, float seconds) {
        if (!FxConfig.rustleSounds || player.isSpectator() || player.isUnderWater() || player.isPassenger()) {
            tracked = false;
            Diagnostics.skip(Diagnostics.RUSTLE, "disabled");
            return;
        }
        double dx = player.getX() - lastX, dz = player.getZ() - lastZ;
        lastX = player.getX();
        lastZ = player.getZ();
        if (!tracked) {
            tracked = true;
            return;
        }
        double step = Math.sqrt(dx * dx + dz * dz);
        if (step > 2.0 || step < 0.02) return;
        walked += (float) step;
        if (walked < (player.isSprinting() ? 0.95F : 1.35F)) return;
        walked = 0F;
        BlockPos feet = player.blockPosition();
        BlockState state = level.getBlockState(feet);
        int kind = WindSway.kind(state);
        SoundEvent event;
        float pitch;
        if (state.is(BlockTags.LEAVES) || level.getBlockState(feet.above()).is(BlockTags.LEAVES)) {
            event = SoundEvents.AZALEA_LEAVES_STEP;
            pitch = 1.0F + RANDOM.nextFloat() * 0.3F;
        } else if (kind == WindSway.WEAK || state.getBlock() instanceof GrowingPlantBlock) {
            event = SoundEvents.VINE_STEP;
            pitch = 0.9F + RANDOM.nextFloat() * 0.3F;
        } else if (state.getBlock() instanceof CropBlock) {
            event = SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES;
            pitch = 1.3F + RANDOM.nextFloat() * 0.3F;
        } else if (kind == WindSway.PLANT) {
            event = SoundEvents.GRASS_HIT;
            pitch = 1.35F + RANDOM.nextFloat() * 0.35F;
        } else {
            return;
        }
        float breeze = 0.8F + 0.4F * Mth.clamp(Wind.level(seconds), 0F, 1F);
        level.playLocalSound(player.getX(), player.getY(), player.getZ(), event, SoundSource.AMBIENT, 0.07F * breeze, pitch, false);
        played++;
        Diagnostics.mark(Diagnostics.RUSTLE, Diagnostics.detail() ? played + " rustles" : null);
    }
}
