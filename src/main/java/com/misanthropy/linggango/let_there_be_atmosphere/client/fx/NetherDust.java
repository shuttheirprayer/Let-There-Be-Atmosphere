package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class NetherDust {
    private NetherDust() {}

    static final int EMBER = 20;
    static final int ASH = 21;

    private static final float[] EMBERS = {1.0F, 1.2F, 0.9F, 0.7F, 0.45F};
    private static final float[] ASHES = {0.6F, 0.35F, 0.8F, 0.25F, 1.5F};
    private static final float[][] STORM_FOG = {
            {0.64F, 0.28F, 0.09F}, {0.58F, 0.07F, 0.06F}, {0.09F, 0.26F, 0.36F}, {0.07F, 0.34F, 0.30F}, {0.46F, 0.29F, 0.22F},
    };
    private static final int REACH = 14;
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    private static float emberCarry, ashCarry;
    private static float storm, stormTarget, open = 1F, openTarget = 1F, surge, surgeTarget;
    private static float windAngle;
    private static final float[] FOG_TINT = new float[3];
    private static boolean tintReady;
    private static long tintAt;
    private static long stormEnd = Long.MIN_VALUE;
    private static long nextStorm = Long.MIN_VALUE;
    private static long surgeEnd = Long.MIN_VALUE;
    private static long nextSurge = Long.MIN_VALUE;
    private static int ticks;
    private static Wail wail;

    static float storm() {
        return storm;
    }

    static float gust() {
        return storm * open;
    }

    static float surge() {
        return surge;
    }

    static float density() {
        return Mth.clamp(storm * open * (float) FxConfig.netherStormStrength, 0F, 1.4F);
    }

    static float windX() {
        return Mth.cos(windAngle);
    }

    static float windZ() {
        return Mth.sin(windAngle);
    }

    public static boolean stormActive() {
        return stormTarget > 0.5F;
    }

    public static void stopStorm() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        stormEnd = now;
        nextStorm = now + 3600L;
        stormTarget = 0F;
    }

    public static void startStorm() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        stormEnd = now + 2400L;
        nextStorm = stormEnd + 3000L;
        nextSurge = now + 160L;
        windAngle = mc.level.random.nextFloat() * Mth.TWO_PI;
        stormTarget = 1F;
    }

    public static void onNetherDustTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) return;
        if (!NetherAir.here(level) || !DynamicFogHandler.active()) {
            Diagnostics.skip(Diagnostics.NETHER_DUST, "not in the Nether");
            storm = stormTarget = surge = surgeTarget = 0F;
            nextStorm = Long.MIN_VALUE;
            return;
        }
        RandomSource random = level.random;
        schedule(level, random);
        storm += (stormTarget - storm) * (stormTarget > storm ? 0.012F : 0.006F);
        if (storm < 0.002F && stormTarget == 0F) storm = 0F;
        pulse(level, random);
        if (++ticks % 20 == 0) openTarget = openness(level, player);
        open += (openTarget - open) * 0.1F;
        sound(mc, random);

        if (!FxConfig.netherDust || FxConfig.netherDustAmount <= 0.0) {
            Diagnostics.skip(Diagnostics.NETHER_DUST, "disabled");
            return;
        }
        if (CameraFluid.of(mc.gameRenderer.getMainCamera()) != FogType.NONE || !CaveMotes.resolve()) return;

        double eyeX = player.getX(), eyeY = player.getEyeY(), eyeZ = player.getZ();
        int look = NetherAir.lookAt(level, eyeX, eyeY, eyeZ);
        float amount = (float) FxConfig.netherDustAmount * (1F - 0.8F * gust());
        int spawned = 0;

        emberCarry += EMBERS[look] * 0.9F * amount;
        for (; emberCarry >= 1F; emberCarry -= 1F) {
            double x = eyeX + (random.nextDouble() * 2.0 - 1.0) * REACH;
            double y = eyeY + random.nextDouble() * 14.0 - 7.0;
            double z = eyeZ + (random.nextDouble() * 2.0 - 1.0) * REACH;
            if (!air(level, x, y, z)) continue;
            CaveMotes.emit(EMBER, x, y, z, 15, 15, 5F + random.nextFloat() * 4F, look + random.nextFloat());
            spawned++;
        }
        ashCarry += ASHES[look] * 0.35F * amount;
        for (; ashCarry >= 1F; ashCarry -= 1F) {
            double x = eyeX + (random.nextDouble() * 2.0 - 1.0) * REACH;
            double y = eyeY + 2.0 + random.nextDouble() * 10.0;
            double z = eyeZ + (random.nextDouble() * 2.0 - 1.0) * REACH;
            if (!air(level, x, y, z)) continue;
            CaveMotes.emit(ASH, x, y, z, Math.max(level.getBrightness(LightLayer.BLOCK, CURSOR), 4), 0,
                    5.5F + random.nextFloat() * 2.5F, look + random.nextFloat());
            spawned++;
        }
        Diagnostics.mark(Diagnostics.NETHER_DUST, Diagnostics.detail()
                ? String.format("look %d, storm %.2f, open %.2f, surge %.2f, +%d", look, storm, open, surge, spawned) : null);
    }

    private static void schedule(ClientLevel level, RandomSource random) {
        long now = level.getGameTime();
        float rate = FxConfig.netherStorms ? (float) FxConfig.netherStormRate : 0F;
        if (rate <= 0F) {
            stormTarget = now < stormEnd ? 1F : 0F;
            nextStorm = Long.MIN_VALUE;
            return;
        }
        if (nextStorm == Long.MIN_VALUE) nextStorm = now + (long) ((400 + random.nextInt(800)) / rate);
        if (now >= nextStorm && now >= stormEnd) {
            stormEnd = now + 1800L + random.nextInt(1800);
            nextStorm = stormEnd + (long) ((3000 + random.nextInt(3600)) / rate);
            nextSurge = now + 160L;
            windAngle = random.nextFloat() * Mth.TWO_PI;
        }
        stormTarget = now < stormEnd ? 1F : 0F;
    }

    private static void pulse(ClientLevel level, RandomSource random) {
        long now = level.getGameTime();
        if (stormTarget > 0F && storm > 0.4F && now >= nextSurge && now >= surgeEnd) {
            surgeEnd = now + 50L + random.nextInt(70);
            nextSurge = surgeEnd + 160L + random.nextInt(340);
        }
        surgeTarget = stormTarget > 0F && now < surgeEnd ? 1F : 0F;
        surge += (surgeTarget - surge) * (surgeTarget > surge ? 0.12F : 0.04F);
        if (surge < 0.002F && surgeTarget == 0F) surge = 0F;
    }

    private static boolean air(ClientLevel level, double x, double y, double z) {
        CURSOR.set(x, y, z);
        return level.isLoaded(CURSOR) && level.getBlockState(CURSOR).isAir();
    }

    private static float openness(ClientLevel level, LocalPlayer player) {
        int clear = 0;
        double x = player.getX(), y = player.getEyeY(), z = player.getZ();
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8F;
            if (air(level, x + Mth.cos(a) * 9.0, y + (i % 2 == 0 ? 2.0 : -1.0), z + Mth.sin(a) * 9.0)) clear++;
        }
        if (air(level, x, y + 8.0, z)) clear += 2;
        return Mth.clamp(clear / 8F, 0.15F, 1F);
    }

    private static void sound(Minecraft mc, RandomSource random) {
        float loud = storm * (0.35F + 0.65F * open);
        if (loud > 0.02F && (wail == null || !mc.getSoundManager().isActive(wail))) {
            wail = new Wail();
            mc.getSoundManager().play(wail);
        }
        if (loud > 0.15F && random.nextFloat() < 0.25F * loud) {
            LocalPlayer player = mc.player;
            double x = player.getX() + random.nextGaussian() * 4.0, z = player.getZ() + random.nextGaussian() * 4.0;
            mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.FIRE_AMBIENT, SoundSource.WEATHER, 0.6F * loud,
                    0.8F + random.nextFloat() * 0.5F, random, x, player.getEyeY() + random.nextGaussian(), z));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void onNetherStormFog(ViewportEvent.RenderFog event) {
        if (storm <= 0.001F || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || !FxConfig.netherStorms) return;
        float thick = Mth.clamp(storm * open, 0F, 1F);
        float closing = 38F / (float) Math.max(FxConfig.netherStormStrength, 0.3) * (1F - 0.25F * surge);
        event.setNearPlaneDistance(Mth.lerp(thick, event.getNearPlaneDistance(), 3F));
        event.setFarPlaneDistance(Mth.lerp(thick, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), Math.max(closing, 14F))));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onNetherStormFogColor(ViewportEvent.ComputeFogColor event) {
        if (storm <= 0.001F || !FxConfig.netherStorms) return;
        float[] goal = STORM_FOG[NetherAir.look()];
        long now = Util.getNanos();
        float k = tintReady ? 1F - (float) Math.exp(-Math.min((now - tintAt) / 1.0E9F, 0.25F) * 0.9F) : 1F;
        tintAt = now;
        tintReady = true;
        float blood = NetherAir.look() == NetherAir.WASTES ? 0.4F * (0.5F + 0.5F * Mth.sin(Util.getMillis() / 9000F)) : 0F;
        for (int i = 0; i < 3; i++) {
            float target = i == 0 ? Mth.lerp(blood, goal[0], 0.55F) : i == 1 ? Mth.lerp(blood, goal[1], 0.14F) : Mth.lerp(blood, goal[2], 0.06F);
            FOG_TINT[i] += (target - FOG_TINT[i]) * k;
        }
        float mix = Mth.clamp(0.92F * storm * open, 0F, 1F);
        event.setRed(Mth.lerp(mix, event.getRed(), FOG_TINT[0]));
        event.setGreen(Mth.lerp(mix, event.getGreen(), FOG_TINT[1]));
        event.setBlue(Mth.lerp(mix, event.getBlue(), FOG_TINT[2]));
    }

    private static final class Wail extends AbstractTickableSoundInstance {
        Wail() {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            volume = 0.01F;
            pitch = 0.55F;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            float loud = storm * (0.35F + 0.65F * open);
            if (loud <= 0.01F) {
                stop();
                return;
            }
            volume = Math.max(0.01F, Math.min(1F, 0.95F * loud * (1F + 0.3F * surge)));
            pitch = 0.5F + 0.2F * loud + 0.08F * surge;
        }
    }
}
