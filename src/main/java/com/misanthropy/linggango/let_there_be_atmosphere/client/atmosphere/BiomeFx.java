package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.misanthropy.linggango.let_there_be_atmosphere.config.AtmosphereConfigManager;
import com.misanthropy.linggango.let_there_be_atmosphere.config.AtmosphereConfigManager.AtmosphereSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;

import java.util.Arrays;
import java.util.Map;

public final class BiomeFx {
    private BiomeFx() {}

    public static final String[] KEYS = {
            "heatHaze", "coldAir", "netherMist", "netherRays", "netherDust", "netherStorms",
            "caveDust", "caveSparkle", "groundMist", "swampMist", "fireflies", "butterflies", "birds",
            "fallingLeaves", "tumbleweeds", "windParticles", "windBits", "endDust", "endMist", "endFog"
    };
    public static final int HEAT_HAZE = 0, COLD_AIR = 1, NETHER_MIST = 2, NETHER_RAYS = 3, NETHER_DUST = 4,
            NETHER_STORMS = 5, CAVE_DUST = 6, CAVE_SPARKLE = 7, GROUND_MIST = 8, SWAMP_MIST = 9, FIREFLIES = 10,
            BUTTERFLIES = 11, BIRDS = 12, FALLING_LEAVES = 13, TUMBLEWEEDS = 14, WIND_PARTICLES = 15,
            WIND_BITS = 16, END_DUST = 17, END_MIST = 18, END_FOG = 19;

    static final byte INHERIT = 0, ON = 1, OFF = 2;

    private static final byte[] SWITCH = new byte[KEYS.length];
    private static final float[] AMOUNT = new float[KEYS.length];
    private static boolean forcesOn;
    private static Biome biome;
    private static ClientLevel checkedLevel;
    private static long checkedAt = Long.MIN_VALUE;
    private static int version = -1;

    static {
        Arrays.fill(AMOUNT, 1F);
    }

    public static boolean on(boolean global, int fx) {
        refresh();
        return SWITCH[fx] == INHERIT ? global : SWITCH[fx] == ON;
    }

    public static double amount(double global, int fx) {
        refresh();
        return global * AMOUNT[fx];
    }

    public static boolean forcesOn() {
        refresh();
        return forcesOn;
    }

    public static int index(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return -1;
    }

    private static void refresh() {
        int current = AtmosphereConfigManager.effectsVersion();
        boolean stale = current != version;
        version = current;
        if (!AtmosphereConfigManager.hasEffectOverrides()) {
            if (stale || biome != null) apply(null);
            biome = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Entity camera = mc.getCameraEntity();
        if (level == null || camera == null) {
            if (stale || biome != null) apply(null);
            biome = null;
            checkedLevel = null;
            return;
        }
        long now = level.getGameTime();
        if (!stale && level == checkedLevel && now == checkedAt) return;
        checkedLevel = level;
        checkedAt = now;
        Holder<Biome> here = level.getBiome(BlockPos.containing(camera.getEyePosition()));
        if (!stale && here.value() == biome) return;
        biome = here.value();
        AtmosphereSettings s = here.unwrapKey().map(ResourceKey::location)
                .map(AtmosphereConfigManager.ATMOSPHERES::get).orElse(null);
        apply(s == null ? null : s.effects);
    }

    private static void apply(JsonObject effects) {
        parse(effects, SWITCH, AMOUNT);
        boolean any = false;
        for (byte b : SWITCH) any |= b == ON;
        forcesOn = any;
    }

    static void parse(JsonObject effects, byte[] switches, float[] amounts) {
        Arrays.fill(switches, INHERIT);
        Arrays.fill(amounts, 1F);
        if (effects == null) return;
        for (Map.Entry<String, JsonElement> e : effects.entrySet()) {
            int i = index(e.getKey());
            if (i < 0) continue;
            JsonElement v = e.getValue();
            if (v.isJsonObject()) {
                JsonObject o = v.getAsJsonObject();
                if (o.has("on")) read(o.get("on"), i, switches, amounts);
                if (o.has("amount")) read(o.get("amount"), i, switches, amounts);
            } else {
                read(v, i, switches, amounts);
            }
        }
    }

    private static void read(JsonElement v, int i, byte[] switches, float[] amounts) {
        if (!v.isJsonPrimitive()) return;
        JsonPrimitive p = v.getAsJsonPrimitive();
        String text = p.getAsString().trim().toLowerCase(java.util.Locale.ROOT);
        if (p.isBoolean() || text.equals("on") || text.equals("off") || text.equals("true") || text.equals("false")) {
            switches[i] = text.equals("true") || text.equals("on") ? ON : OFF;
            return;
        }
        try {
            float amount = Float.parseFloat(text);
            if (Float.isFinite(amount)) amounts[i] = Math.max(amount, 0F);
        } catch (NumberFormatException ignored) {}
    }
}
