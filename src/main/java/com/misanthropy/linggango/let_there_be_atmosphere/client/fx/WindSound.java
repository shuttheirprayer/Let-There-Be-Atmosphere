package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

public final class WindSound {
    private WindSound() {}

    private static final int OPEN = 0;
    private static final int FOREST = 1;
    private static final int DESERT = 2;
    private static final int PEAKS = 3;
    private static final String[] NAMES = {"open", "forest", "desert", "peaks"};
    private static final float[][] ENV = {
            {1.0F, 0.55F, 0.10F},
            {0.8F, 0.50F, 0.08F},
            {0.9F, 0.78F, 0.16F},
            {1.3F, 0.68F, 0.22F},
    };
    private static final RandomSource RANDOM = RandomSource.create();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    private static Bed bed;
    private static ClientLevel owner;
    private static int env;
    private static int envAge;
    private static float volumeGoal, pitchGoal = 0.55F;

    static void silence() {
        volumeGoal = 0F;
    }

    static void tick(ClientLevel level, LocalPlayer player, float seconds) {
        Minecraft mc = Minecraft.getInstance();
        if (owner != level) {
            if (bed != null) bed.halt();
            bed = null;
            owner = level;
        }
        if (bed != null && bed.isStopped()) bed = null;
        if (!FxConfig.windSounds || FxConfig.windSoundVolume <= 0.0 || player.isUnderWater()) {
            volumeGoal = 0F;
            Diagnostics.skip(Diagnostics.WINDSOUND, "disabled or underwater");
            return;
        }
        if (envAge-- <= 0) {
            envAge = 40;
            env = classify(level, player);
        }
        float w = Wind.level(seconds) * Wind.altitude(player.getY()) * (float) FxConfig.windStrength;
        float raw = Mth.clamp((w - 0.2F) / 0.85F, 0F, 1F);
        raw = raw * raw * (3F - 2F * raw);
        float hush = FxConfig.windShelter ? Mth.lerp(Wind.shelter, 0.06F, 1F) : 1F;
        float[] profile = ENV[env];
        volumeGoal = 0.3F * (float) Math.pow(raw, 1.2) * profile[0] * hush * (float) FxConfig.windSoundVolume;
        pitchGoal = profile[1] + profile[2] * Wind.pulse(seconds);
        if (volumeGoal > 0.012F && bed == null) {
            bed = new Bed();
            mc.getSoundManager().play(bed);
            Diagnostics.once("wind sounds started a vanilla wind bed");
        }
        if (env == FOREST && volumeGoal > 0.04F && Wind.shelter > 0.4F
                && RANDOM.nextFloat() < 0.04F + 0.2F * Wind.pulse(seconds)) {
            rustle(level, player, raw);
        }
        Diagnostics.mark(Diagnostics.WINDSOUND, Diagnostics.detail()
                ? String.format("%s, volume %.2f, pitch %.2f, hush %.2f", NAMES[env], volumeGoal, pitchGoal, hush) : null);
    }

    private static int classify(ClientLevel level, LocalPlayer player) {
        if (player.getY() > 140.0) return PEAKS;
        Holder<Biome> biome = level.getBiome(player.blockPosition());
        if (biome.is(BiomeTags.IS_MOUNTAIN)) return PEAKS;
        Biome value = biome.value();
        if (!value.hasPrecipitation() && value.getBaseTemperature() >= 1.0F) return DESERT;
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE)) return FOREST;
        return OPEN;
    }

    private static void rustle(ClientLevel level, LocalPlayer player, float raw) {
        for (int i = 0; i < 3; i++) {
            int x = player.getBlockX() + RANDOM.nextInt(21) - 10;
            int y = player.getBlockY() - 2 + RANDOM.nextInt(11);
            int z = player.getBlockZ() + RANDOM.nextInt(21) - 10;
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            CURSOR.set(x, y, z);
            if (!level.getBlockState(CURSOR).is(BlockTags.LEAVES)) continue;
            level.playLocalSound(x + 0.5, y + 0.5, z + 0.5, SoundEvents.AZALEA_LEAVES_STEP, SoundSource.AMBIENT,
                    (0.05F + 0.1F * raw) * (float) FxConfig.windSoundVolume, 0.75F + RANDOM.nextFloat() * 0.4F, false);
            return;
        }
    }

    private static final class Bed extends AbstractTickableSoundInstance {
        Bed() {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, RandomSource.create());
            looping = true;
            delay = 0;
            volume = 0.01F;
            pitch = 0.55F;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
        }

        void halt() {
            stop();
        }

        @Override
        public void tick() {
            volume += (volumeGoal - volume) * 0.06F;
            pitch += (pitchGoal - pitch) * 0.05F;
            volume = Math.max(volume, 0.01F);
            if (volumeGoal <= 0.006F && volume < 0.014F) stop();
        }
    }
}
