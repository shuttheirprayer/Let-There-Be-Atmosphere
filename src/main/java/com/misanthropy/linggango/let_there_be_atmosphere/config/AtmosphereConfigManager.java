package com.misanthropy.linggango.let_there_be_atmosphere.config;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeFx;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AtmosphereConfigManager {
    public static final Map<ResourceLocation, AtmosphereSettings> ATMOSPHERES = new ConcurrentHashMap<>();
    private static final String CONFIG_DIR  = "let_there_be_atmosphere";
    private static final String CONFIG_FILE = "biomes.json";
    private static final String DEFAULTS = "/assets/let_there_be_atmosphere/defaults/biomes.json";
    private static final String LEGACY_FILE = "linggango_atmospheres.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class AtmosphereSettings {
        public int   fogHex   = 0x88BFEA;
        public float fogStart = 5.0f;
        public float fogEnd   = 30.0f;

        public int   skyHex     = -1;
        public float skyTintStr = 0.55f;

        public int   rainFogHex    = -1;
        public float rainMult      = 1.0f;
        public int   thunderFogHex = -1;

        public float nearHaze = 0.0f;

        public float voidFogStr = 1.0f;

        public int   waterFogHex  = -1;
        public float waterFogDist = 1.0f;

        public int ambientHex = -1;

        public float satBoost   = 0.0f;
        public float brightness = 0.0f;

        public int   dawnDuskHex = -1;
        public float dawnDuskStr = 1.0f;
        public int   nightHex    = -1;
        public float nightStr    = 1.0f;

        public boolean sphereFog = false;

        public float heightFogStr = 0.5f;

        public float fogDensity       = 1.0f;
        public float nightFogStartMul = 1.0f;
        public float nightFogEndMul   = 1.0f;

        public int   hazeHex      = -1;
        public float hazeStrength = 1.0f;

        public String mistParticle = "none";
        public float  mistRate     = 1.0f;

        public int grassHex   = -1;
        public int foliageHex = -1;
        public int waterHex   = -1;

        public JsonObject effects = null;

        public AtmosphereSettings() {}

        public AtmosphereSettings(int fogHex, int skyHex, float fogStart, float fogEnd) {
            this.fogHex   = fogHex;
            this.skyHex   = skyHex;
            this.fogStart = fogStart;
            this.fogEnd   = fogEnd;
        }

        public AtmosphereSettings copy() {
            AtmosphereSettings c = new AtmosphereSettings();
            c.copyFrom(this);
            return c;
        }

        public void copyFrom(AtmosphereSettings o) {
            this.fogHex = o.fogHex;
            this.fogStart = o.fogStart;
            this.fogEnd = o.fogEnd;
            this.skyHex = o.skyHex;
            this.skyTintStr = o.skyTintStr;
            this.rainFogHex = o.rainFogHex;
            this.rainMult = o.rainMult;
            this.thunderFogHex = o.thunderFogHex;
            this.nearHaze = o.nearHaze;
            this.voidFogStr = o.voidFogStr;
            this.waterFogHex = o.waterFogHex;
            this.waterFogDist = o.waterFogDist;
            this.ambientHex = o.ambientHex;
            this.satBoost = o.satBoost;
            this.brightness = o.brightness;
            this.dawnDuskHex = o.dawnDuskHex;
            this.dawnDuskStr = o.dawnDuskStr;
            this.nightHex = o.nightHex;
            this.nightStr = o.nightStr;
            this.sphereFog = o.sphereFog;
            this.heightFogStr = o.heightFogStr;
            this.fogDensity = o.fogDensity;
            this.nightFogStartMul = o.nightFogStartMul;
            this.nightFogEndMul = o.nightFogEndMul;
            this.hazeHex = o.hazeHex;
            this.hazeStrength = o.hazeStrength;
            this.mistParticle = o.mistParticle;
            this.mistRate = o.mistRate;
            this.grassHex = o.grassHex;
            this.foliageHex = o.foliageHex;
            this.waterHex = o.waterHex;
            this.effects = o.effects == null ? null : o.effects.deepCopy();
        }
    }

    private static Path biomesPath() throws java.io.IOException {
        Path dir = FMLPaths.CONFIGDIR.get().resolve(CONFIG_DIR);
        Path path = dir.resolve(CONFIG_FILE);
        Path legacy = FMLPaths.CONFIGDIR.get().resolve(LEGACY_FILE);
        if (!Files.exists(path) && Files.exists(legacy)) {
            Files.createDirectories(dir);
            Files.move(legacy, path);
        }
        return path;
    }

    public static void load() {
        try {
            Path path = biomesPath();
            if (Files.exists(path)) fromJson(Files.readString(path));
            if (ATMOSPHERES.isEmpty()) {
                loadDefaults();
                save();
            }
            Diagnostics.LOG.info("[atmosphere] biomes loaded: {} tuned from {}", ATMOSPHERES.size(), path);
        } catch (Exception failure) {
            Diagnostics.LOG.error("[atmosphere] biome file failed to load", failure);
        }
    }

    public static void loadDefaults() {
        try (java.io.InputStream in = AtmosphereConfigManager.class.getResourceAsStream(DEFAULTS)) {
            if (in == null) {
                ATMOSPHERES.clear();
                return;
            }
            fromJson(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception failure) {
            Diagnostics.LOG.error("[atmosphere] built-in vanilla biome looks failed to load", failure);
        }
    }

    public static void fromJson(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            ATMOSPHERES.clear();

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                try {
                    JsonObject o = entry.getValue().getAsJsonObject();
                    AtmosphereSettings s = new AtmosphereSettings();

                    s.fogHex        = hex(o, "fogHex",       s.fogHex);
                    s.fogStart      = f  (o, "fogStart",     s.fogStart);
                    s.fogEnd        = f  (o, "fogEnd",       s.fogEnd);
                    s.skyHex        = hex(o, "skyHex",       s.skyHex);
                    s.skyTintStr    = f  (o, "skyTintStr",   s.skyTintStr);
                    s.rainFogHex    = hex(o, "rainFogHex",   s.rainFogHex);
                    s.rainMult      = f  (o, "rainMult",     s.rainMult);
                    s.thunderFogHex = hex(o, "thunderFogHex",s.thunderFogHex);
                    s.nearHaze      = f  (o, "nearHaze",     s.nearHaze);
                    s.waterFogHex   = hex(o, "waterFogHex",  s.waterFogHex);
                    s.waterFogDist  = f  (o, "waterFogDist", s.waterFogDist);
                    s.heightFogStr  = f  (o, "heightFogStr", s.heightFogStr);
                    s.voidFogStr    = f  (o, "voidFogStr",   s.voidFogStr);
                    s.ambientHex    = hex(o, "ambientHex",   s.ambientHex);
                    s.satBoost      = f  (o, "satBoost",     s.satBoost);
                    s.brightness    = f  (o, "brightness",   s.brightness);
                    s.dawnDuskHex   = hex(o, "dawnDuskHex",  s.dawnDuskHex);
                    s.dawnDuskStr   = f  (o, "dawnDuskStr",  s.dawnDuskStr);
                    s.nightHex      = hex(o, "nightHex",     s.nightHex);
                    s.nightStr      = f  (o, "nightStr",     s.nightStr);
                    s.sphereFog     = b  (o, s.sphereFog);
                    s.fogDensity       = f(o, "fogDensity",       s.fogDensity);
                    s.nightFogStartMul = f(o, "nightFogStartMul", s.nightFogStartMul);
                    s.nightFogEndMul   = f(o, "nightFogEndMul",   s.nightFogEndMul);
                    s.hazeHex          = hex(o, "hazeHex",        s.hazeHex);
                    s.hazeStrength     = f(o, "hazeStrength",     s.hazeStrength);
                    s.mistParticle     = str(o, "mistParticle",   s.mistParticle);
                    s.mistRate         = f(o, "mistRate",         s.mistRate);
                    s.grassHex      = hex(o, "grassHex",     s.grassHex);
                    s.foliageHex    = hex(o, "foliageHex",   s.foliageHex);
                    s.waterHex      = hex(o, "waterHex",     s.waterHex);
                    if (o.has("effects") && o.get("effects").isJsonObject()) {
                        s.effects = o.getAsJsonObject("effects");
                        for (String key : s.effects.keySet()) {
                            if (BiomeFx.index(key) < 0) Diagnostics.LOG.warn("[atmosphere] biome {} has an unknown effect '{}' in biomes.json", entry.getKey(), key);
                        }
                    }

                    ResourceLocation loc = ResourceLocation.tryParse(entry.getKey());
                    if (loc != null) ATMOSPHERES.put(loc, s);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        refreshOverrideFlags();
    }

    private static volatile int colorOverrideCount = 0;
    private static volatile int skyOverrideCount = 0;
    private static volatile int effectOverrideCount = 0;
    private static volatile int effectsVersion = 0;

    public static boolean hasColorOverrides() { return colorOverrideCount > 0; }

    public static boolean hasSkyOverrides() { return skyOverrideCount > 0; }

    public static boolean hasEffectOverrides() { return effectOverrideCount > 0; }

    public static int effectsVersion() { return effectsVersion; }

    public static void refreshOverrideFlags() {
        int colors = 0, skies = 0, effects = 0;
        for (AtmosphereSettings s : ATMOSPHERES.values()) {
            if (s.grassHex >= 0 || s.foliageHex >= 0 || s.waterHex >= 0) colors++;
            if (s.skyHex >= 0) skies++;
            if (s.effects != null && s.effects.size() > 0) effects++;
        }
        colorOverrideCount = colors;
        skyOverrideCount = skies;
        effectOverrideCount = effects;
        effectsVersion++;
    }

    public static void save() {
        try {
            String json = toJson();
            SettingsWriter.submit("biomes", () -> Files.writeString(biomesPath(), json));
        } catch (Exception ignored) {}
        refreshOverrideFlags();
    }

    public static String toJson() {
        JsonObject root = new JsonObject();
        JsonArray notes = new JsonArray();
        for (String line : NOTES) notes.add(line);
        root.add("_notes", notes);
        for (Map.Entry<ResourceLocation, AtmosphereSettings> e : ATMOSPHERES.entrySet()) {
            root.add(e.getKey().toString(), serialize(e.getValue()));
        }
        return GSON.toJson(root);
    }

    private static JsonObject serialize(AtmosphereSettings s) {
        JsonObject o = new JsonObject();
        o.addProperty("fogHex",        hexStr(s.fogHex));
        o.addProperty("fogStart",      s.fogStart);
        o.addProperty("fogEnd",        s.fogEnd);
        o.addProperty("skyHex",        s.skyHex        < 0 ? "auto" : hexStr(s.skyHex));
        o.addProperty("skyTintStr",    s.skyTintStr);
        o.addProperty("rainFogHex",    s.rainFogHex    < 0 ? "auto" : hexStr(s.rainFogHex));
        o.addProperty("rainMult",      s.rainMult);
        o.addProperty("thunderFogHex", s.thunderFogHex < 0 ? "auto" : hexStr(s.thunderFogHex));
        o.addProperty("nearHaze",      s.nearHaze);
        o.addProperty("waterFogHex",   s.waterFogHex   < 0 ? "auto" : hexStr(s.waterFogHex));
        o.addProperty("waterFogDist",  s.waterFogDist);
        o.addProperty("heightFogStr",  s.heightFogStr);
        o.addProperty("voidFogStr",    s.voidFogStr);
        o.addProperty("ambientHex",    s.ambientHex    < 0 ? "none" : hexStr(s.ambientHex));
        o.addProperty("satBoost",      s.satBoost);
        o.addProperty("brightness",    s.brightness);
        o.addProperty("dawnDuskHex",   s.dawnDuskHex   < 0 ? "auto" : hexStr(s.dawnDuskHex));
        o.addProperty("dawnDuskStr",   s.dawnDuskStr);
        o.addProperty("nightHex",      s.nightHex      < 0 ? "auto" : hexStr(s.nightHex));
        o.addProperty("nightStr",      s.nightStr);
        o.addProperty("sphereFog",     s.sphereFog);
        o.addProperty("fogDensity",       s.fogDensity);
        o.addProperty("nightFogStartMul", s.nightFogStartMul);
        o.addProperty("nightFogEndMul",   s.nightFogEndMul);
        o.addProperty("hazeHex",          s.hazeHex       < 0 ? "auto" : hexStr(s.hazeHex));
        o.addProperty("hazeStrength",     s.hazeStrength);
        o.addProperty("mistParticle",     s.mistParticle);
        o.addProperty("mistRate",         s.mistRate);
        o.addProperty("grassHex",      s.grassHex      < 0 ? "auto" : hexStr(s.grassHex));
        o.addProperty("foliageHex",    s.foliageHex    < 0 ? "auto" : hexStr(s.foliageHex));
        o.addProperty("waterHex",      s.waterHex      < 0 ? "auto" : hexStr(s.waterHex));
        if (s.effects != null && s.effects.size() > 0) o.add("effects", s.effects.deepCopy());
        return o;
    }

    private static final String[] NOTES = {
            "Each entry below is one biome, named by its id. Only biomes listed here are changed.",
            "Per-biome effects: add an \"effects\" block to a biome to change effects only while you stand in it.",
            "  false (or \"off\") turns the effect off in that biome. true (or \"on\") turns it on there even if it is off in settings.",
            "  A number multiplies the effect's amount or strength from settings: 0.5 is half, 2 is double, 0 hides it.",
            "  To set both, write {\"on\": true, \"amount\": 1.5}. Effects you leave out follow settings.properties.",
            "  Example: \"effects\": { \"heatHaze\": false, \"netherDust\": 0.5 }",
            "Effects you can use: heatHaze (also the Nether's wobbly heat air), coldAir, netherMist, netherRays, netherDust,",
            "  netherStorms, caveDust, caveSparkle, groundMist, swampMist, fireflies, butterflies, birds, fallingLeaves,",
            "  tumbleweeds, windParticles, windBits, endDust, endMist, endFog.",
            "The biome is the one at your eyes, so effects switch as you cross a biome border.",
            "This _notes block is rewritten on every save; editing it does nothing."
    };

    private static String hexStr(int v) { return String.format("#%06X", v & 0xFFFFFF); }

    private static int hex(JsonObject o, String key, int def) {
        if (!o.has(key)) return def;
        String v = o.get(key).getAsString().trim();
        if (v.equalsIgnoreCase("auto") || v.equalsIgnoreCase("none")) return -1;
        try { return Integer.parseInt(v.startsWith("#") ? v.substring(1) : v, 16); }
        catch (Exception e) { return def; }
    }

    private static String str(JsonObject o, String key, String def) {
        if (!o.has(key)) return def;
        try { return o.get(key).getAsString().trim(); } catch (Exception e) { return def; }
    }

    private static float f(JsonObject o, String key, float def) {
        if (!o.has(key)) return def;
        try { return o.get(key).getAsFloat(); } catch (Exception e) { return def; }
    }

    private static boolean b(JsonObject o, boolean def) {
        if (!o.has("sphereFog")) return def;
        try { return o.get("sphereFog").getAsBoolean(); } catch (Exception e) { return def; }
    }
}
