package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;

public final class Wind {
    private Wind() {}

    private static final ValueNoise NOISE = new ValueNoise(6421L);
    private static final ValueNoise MOOD = new ValueNoise(9157L);

    private static final float TURN_RATE = 0.0016F;
    private static final float GUST_RATE = 0.0180F;
    private static final float DAY = 1200F;
    private static final float FRONT_TICKS = 2400F;
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    public static float front;
    public static float storm;
    public static float shelter = 1F;
    private static float shelterTarget = 1F;
    private static int shelterAge;

    private static double clock;
    private static long clockAt;

    public static float seconds(ClientLevel level, float partialTick) {
        return (float) (time() % 50000.0);
    }

    public static double time() {
        long now = Util.getNanos();
        if (now != clockAt) {
            if (clockAt != 0L && !Minecraft.getInstance().isPaused()) clock += Math.min((now - clockAt) * 1.0E-9, 0.25);
            clockAt = now;
        }
        return clock;
    }

    private static float modelAt = Float.NaN;
    private static float pinned = Float.NaN;
    private static float angle, gust, force, level, pulse, cluster;

    private static float smooth(float a, float b, float x) {
        float k = Mth.clamp((x - a) / (b - a), 0F, 1F);
        return k * k * (3F - 2F * k);
    }

    private static float stretch(float n) {
        return Mth.clamp((n - 0.27F) / 0.46F, 0F, 1F);
    }

    private static void model(float seconds) {
        if (seconds == modelAt) return;
        modelAt = seconds;
        ClientLevel world = Minecraft.getInstance().level;
        float rain = world == null ? 0F : world.getRainLevel(1F);
        float thunder = world == null ? 0F : world.getThunderLevel(1F);
        if (!Float.isNaN(pinned)) {
            level = pinned;
            gust = 0.08F + 0.92F * Math.min(pinned, 1F);
            force = pinned * 1.9F;
            pulse = pinned > 0.5F ? 0.75F : 0F;
            cluster = pinned > 0.5F ? 0.8F : 0F;
            angle = stretch(NOISE.at(seconds * TURN_RATE)) * Mth.TWO_PI;
            return;
        }
        if (!FxConfig.livingWind) {
            float g = 0.35F + NOISE.at(seconds * GUST_RATE + 137F) * 0.65F;
            if (FxConfig.stormWind && world != null) {
                g = Math.min(1F, g + rain * 0.1F);
                g = Mth.lerp(thunder * 0.85F, g, 1F);
            }
            gust = g;
            level = g;
            force = (0.55F + 0.9F * g) * (1F + 1.1F * rain + 2.4F * thunder);
            pulse = 0.5F;
            cluster = 0.5F;
            angle = NOISE.at(seconds * TURN_RATE) * Mth.TWO_PI;
            return;
        }
        float day = 0.06F + 0.94F * (float) Math.pow(stretch(MOOD.at(seconds / DAY + 11.3F)), 1.5);
        float spell = stretch(MOOD.at(seconds / 180F + 57.1F));
        float mood = Mth.clamp(0.85F * day + 0.4F * smooth(0.62F, 0.92F, spell) * (0.3F + day), 0F, 1F);
        float envelope = Math.max(smooth(0.5F, 0.82F, stretch(MOOD.at(seconds / 70F + 203F))) * smooth(0.12F, 0.4F, mood), storm * 0.85F);
        float beat = 0.5F + 0.5F * Mth.sin(seconds * 1.15F + 1.6F * Mth.sin(seconds * 0.37F));
        pulse = beat * beat * beat * envelope;
        cluster = envelope;
        float ripple = (MOOD.at(seconds * 0.21F + 91F) - 0.5F) * 0.6F * mood;
        float w = Mth.clamp(mood + pulse * (0.3F + 0.45F * mood) + ripple + storm * 0.5F + thunder * 0.75F, 0.03F, 2.1F);
        level = w;
        gust = 0.08F + 0.92F * Math.min(w, 1F);
        force = w * 1.9F;
        angle = stretch(NOISE.at(seconds * TURN_RATE)) * Mth.TWO_PI + (beat - 0.5F) * envelope * 0.3F;
    }

    public static void pin(float wind) {
        pinned = wind;
        modelAt = Float.NaN;
    }

    public static float angle(float seconds) {
        model(seconds);
        return angle;
    }

    public static float x(float seconds) {
        return Mth.cos(angle(seconds));
    }

    public static float z(float seconds) {
        return Mth.sin(angle(seconds));
    }

    public static float gust(float seconds) {
        model(seconds);
        return gust;
    }

    public static float force(float seconds) {
        model(seconds);
        return force;
    }

    public static float level(float seconds) {
        model(seconds);
        return level;
    }

    public static float pulse(float seconds) {
        model(seconds);
        return pulse;
    }

    public static float cluster(float seconds) {
        model(seconds);
        return cluster;
    }

    public static String mood(float seconds) {
        model(seconds);
        if (!FxConfig.livingWind) return "classic";
        if (storm > 0.5F) return "storm";
        return level < 0.25F ? "calm" : level < 0.6F ? "breezy" : "windy";
    }

    public static float altitude(double y) {
        if (!FxConfig.windAltitude) return 1F;
        return 0.75F + 0.85F * smooth(40F, 190F, (float) y);
    }

    static void idle() {
        front = 0F;
        storm += (0F - storm) * 0.05F;
        shelter += (1F - shelter) * 0.05F;
    }

    static void tick(ClientLevel world, LocalPlayer player) {
        float rain = world.getRainLevel(1F), thunder = world.getThunderLevel(1F);
        front = FxConfig.windFront ? approach(world) : 0F;
        float target = Math.max(front * 0.8F, Math.max(rain, thunder));
        storm += (target - storm) * (target > storm ? 0.08F : 0.0015F);
        if (!FxConfig.windShelter) {
            shelterTarget = 1F;
        } else if (shelterAge++ >= 4) {
            shelterAge = 0;
            shelterTarget = measure(world, player);
        }
        shelter += (shelterTarget - shelter) * 0.08F;
    }

    private static float approach(ClientLevel world) {
        if (world.dimension() != Level.OVERWORLD) return 0F;
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return 0F;
        ServerLevelData data = server.getWorldData().overworldData();
        if (data.isRaining() || data.getClearWeatherTime() > 0) return 0F;
        int until = data.getRainTime();
        if (until <= 0 || until > FRONT_TICKS) return 0F;
        return smooth(0F, 1F, 1F - until / FRONT_TICKS);
    }

    private static float measure(ClientLevel world, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        CURSOR.set(eye.x, eye.y, eye.z);
        float open = Mth.clamp((world.getBrightness(LightLayer.SKY, CURSOR) - 2F) / 10F, 0F, 1F);
        float seconds = seconds(world, 0F);
        float wx = x(seconds), wz = z(seconds);
        float blocked = 0F;
        for (int k = 1; k <= 8; k++) {
            float weight = 1.3F - k * 0.1F;
            for (int h = 0; h < 2; h++) {
                CURSOR.set(eye.x - wx * k, eye.y - 1.0 + h, eye.z - wz * k);
                BlockState state = world.getBlockState(CURSOR);
                if (state.canOcclude() && !state.is(BlockTags.LEAVES)) blocked += weight;
            }
        }
        return open * (1F - 0.75F * Math.min(1F, blocked / 2.6F));
    }
}
