package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import com.google.gson.JsonParser;
import com.misanthropy.linggango.let_there_be_atmosphere.config.AtmosphereConfigManager;
import net.minecraft.resources.ResourceLocation;

public final class BiomeFxCheck {
    public static void main(String[] args) {
        byte[] on = new byte[BiomeFx.KEYS.length];
        float[] amount = new float[BiomeFx.KEYS.length];

        BiomeFx.parse(null, on, amount);
        for (int i = 0; i < on.length; i++) {
            check(on[i] == BiomeFx.INHERIT && amount[i] == 1F, "absent inherits " + BiomeFx.KEYS[i]);
        }

        BiomeFx.parse(JsonParser.parseString("""
                {"heatHaze": false, "fireflies": true, "netherDust": 0.5, "birds": "off", "endMist": "on",
                 "coldAir": {"on": true, "amount": 1.5}, "caveDust": -3, "windBits": "lots", "nope": false,
                 "endDust": {"amount": "2"}}
                """).getAsJsonObject(), on, amount);
        check(on[BiomeFx.HEAT_HAZE] == BiomeFx.OFF && amount[BiomeFx.HEAT_HAZE] == 1F, "false turns off");
        check(on[BiomeFx.FIREFLIES] == BiomeFx.ON, "true turns on");
        check(on[BiomeFx.NETHER_DUST] == BiomeFx.INHERIT && amount[BiomeFx.NETHER_DUST] == 0.5F, "number scales only");
        check(on[BiomeFx.BIRDS] == BiomeFx.OFF, "off word");
        check(on[BiomeFx.END_MIST] == BiomeFx.ON, "on word");
        check(on[BiomeFx.COLD_AIR] == BiomeFx.ON && amount[BiomeFx.COLD_AIR] == 1.5F, "object form");
        check(amount[BiomeFx.CAVE_DUST] == 0F, "negative clamps to zero");
        check(on[BiomeFx.WIND_BITS] == BiomeFx.INHERIT && amount[BiomeFx.WIND_BITS] == 1F, "garbage ignored");
        check(amount[BiomeFx.END_DUST] == 2F, "amount as text");
        check(on[BiomeFx.NETHER_MIST] == BiomeFx.INHERIT && amount[BiomeFx.NETHER_MIST] == 1F, "untouched inherits");
        check(BiomeFx.index("nope") < 0 && BiomeFx.index("heatHaze") == BiomeFx.HEAT_HAZE, "index");

        BiomeFx.parse(null, on, amount);
        check(on[BiomeFx.HEAT_HAZE] == BiomeFx.INHERIT && amount[BiomeFx.NETHER_DUST] == 1F, "reset between biomes");

        AtmosphereConfigManager.fromJson("""
                {"_notes": ["x"], "minecraft:plains": {"fogHex": "#112233"},
                 "minecraft:nether_wastes": {"fogHex": "#330000", "effects": {"heatHaze": false}}}
                """);
        check(AtmosphereConfigManager.ATMOSPHERES.size() == 2, "notes are not a biome");
        check(!AtmosphereConfigManager.ATMOSPHERES.containsKey(new ResourceLocation("minecraft", "_notes")), "notes skipped");
        check(AtmosphereConfigManager.hasEffectOverrides(), "effects flagged");
        String saved = AtmosphereConfigManager.toJson();
        check(saved.contains("_notes") && saved.contains("\"heatHaze\": false"), "effects and notes saved");
        check(AtmosphereConfigManager.ATMOSPHERES.get(new ResourceLocation("minecraft", "nether_wastes")).copy().effects.has("heatHaze"), "copy keeps effects");
        AtmosphereConfigManager.fromJson(saved);
        check(AtmosphereConfigManager.ATMOSPHERES.size() == 2 && AtmosphereConfigManager.hasEffectOverrides(), "round trip");
        AtmosphereConfigManager.fromJson("{\"minecraft:plains\": {\"fogHex\": \"#112233\"}}");
        check(!AtmosphereConfigManager.hasEffectOverrides(), "old files have no effects");

        System.out.println("BiomeFx OK");
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
