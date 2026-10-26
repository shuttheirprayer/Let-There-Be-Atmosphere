package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeFx;
import java.util.Locale;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;

public final class WindParticles {
    private WindParticles() {}

    private static final int CHECKS = 96;
    private static final float DRIFT = 0.03F;
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final String[] NEVER = {"rain", "splash", "drip", "bubble", "water", "wake", "fish", "flame", "lava", "portal",
            "flytowards", "terrain", "breakingitem", "pickup", "trail", "vibration", "shriek", "sonic", "dragon", "guardian",
            "sweep", "sculk", "firework", "glow", "light", "barrier", "damage", "attack", "enchant", "nautilus", "ominous"};
    private static final String[][] TIERS = {
            {"smoke", "ash", "dust", "spore", "leaves", "leaf", "pollen", "snow", "cloud", "poof", "explode", "cherry"},
            {"spell", "note", "heart", "crit", "totem", "squid", "spit", "sneeze", "puff", "mist", "bit"},
    };
    private static final float[][] GAINS = {
            {1.0F, 1.0F, 0.7F, 1.0F, 1.1F, 1.1F, 1.0F, 1.3F, 0.6F, 0.6F, 0.5F, 1.1F},
            {0.35F, 0.35F, 0.35F, 0.35F, 0.35F, 0.35F, 0.35F, 0.35F, 0.35F, 0.35F, 0.35F},
    };

    public static boolean active;
    public static long tick;
    public static float pushX, pushZ;
    public static float driftX, driftZ;
    private static final float AMBIENT = 2.2F;
    private static int budget;

    private static final ClassValue<float[]> TYPES = new ClassValue<>() {
        @Override
        protected float[] computeValue(Class<?> type) {
            String name = type.getSimpleName().toLowerCase(Locale.ROOT);
            for (String bad : NEVER) {
                if (name.contains(bad)) return new float[]{-1F, 9F};
            }
            for (int tier = 0; tier < TIERS.length; tier++) {
                for (int i = 0; i < TIERS[tier].length; i++) {
                    if (name.contains(TIERS[tier][i])) return new float[]{GAINS[tier][i], tier};
                }
            }
            return new float[]{0.3F, 2F};
        }
    };

    static void update(ClientLevel level, float seconds, double eyeY, boolean on) {
        tick++;
        budget = CHECKS;
        if (!on) {
            active = false;
            Diagnostics.skip(Diagnostics.WINDPUSH, "disabled");
            return;
        }
        float force = Wind.level(seconds) * Wind.altitude(eyeY) * (float) BiomeFx.amount(FxConfig.windParticleStrength, BiomeFx.WIND_PARTICLES) * (float) FxConfig.windStrength;
        pushX += (Wind.x(seconds) * force * DRIFT - pushX) * 0.2F;
        pushZ += (Wind.z(seconds) * force * DRIFT - pushZ) * 0.2F;
        active = force > 0.02F;
        if (active) Diagnostics.mark(Diagnostics.WINDPUSH, Diagnostics.detail() ? String.format("push %.3f, %s", force * DRIFT, Wind.mood(seconds)) : null);
        else Diagnostics.skip(Diagnostics.WINDPUSH, "air is still");
    }

    static void sampleDrift(ClientLevel level, float partialTick, double eyeY) {
        float seconds = Wind.seconds(level, partialTick);
        float w = Math.min(Wind.level(seconds) * Wind.altitude(eyeY) * (float) FxConfig.windStrength, 1.2F);
        float speed = AMBIENT * w * w;
        driftX = Wind.x(seconds) * speed;
        driftZ = Wind.z(seconds) * speed;
    }

    static float sky(int packedLight) {
        return Mth.clamp(((packedLight & 15) - 3F) / 8F, 0F, 1F);
    }

    public static float response(Class<?> type, ClientLevel level, double x, double y, double z) {
        if (level == null) return 0F;
        float[] entry = TYPES.get(type);
        if (entry[0] < 0F || entry[1] > FxConfig.windParticleFilter) return 0F;
        if (budget <= 0) return -1F;
        budget--;
        CURSOR.set(x, y, z);
        if (!level.getFluidState(CURSOR).isEmpty()) return 0F;
        float sky = Mth.clamp((level.getBrightness(LightLayer.SKY, CURSOR) - 3F) / 8F, 0F, 1F);
        return entry[0] * sky;
    }
}
