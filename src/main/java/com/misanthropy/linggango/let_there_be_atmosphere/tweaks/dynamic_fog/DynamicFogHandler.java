package com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.AtmosphereScreen;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeColorModifier;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeBlend;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.CameraFluid;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.ColorGrade;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.VividSky;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.DayCurves;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Lightning;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.FogDepth;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.FxConfig;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.StoryCloudConfig;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.SunTint;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.ValueNoise;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WeatherStorms;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WeatherTint;
import com.misanthropy.linggango.let_there_be_atmosphere.config.AtmosphereConfigManager;
import com.misanthropy.linggango.let_there_be_atmosphere.config.ConfigText;
import com.misanthropy.linggango.let_there_be_atmosphere.config.SettingsWriter;
import com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere.LevelRendererStarAccessor;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Camera;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.NetherAir;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.EndAir;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.DeepDark;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jspecify.annotations.NonNull;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Mod.EventBusSubscriber(modid = "let_there_be_atmosphere", value = Dist.CLIENT)
public class DynamicFogHandler {
    public static int     defaultFogHex         = 0x88BFEA;

    public static double  fogStartMultiplier     = 0.45;
    public static boolean masterEnabled          = true;
    public static boolean dynamicFogEnabled      = true;
    public static boolean voidFogEnabled         = true;
    public static boolean waterFogEnabled        = true;
    public static int     cloudRangeChunks       = 32;
    public static boolean cloudFogEnabled        = true;
    public static double  skyFogEnd              = 1.0;
    public static double  waterFogDistance       = 1.0;
    public static double  rainFogDensity         = 0.05;
    public static double  biomeTintStrength      = 0.82;
    public static double  skyFogBlendStrength    = 0.55;
    public static double  chunkBorderFogSoftness = 0.14;
    public static double  fogTransitionSpeed     = 0.015;
    public static double  distanceTransitionSpeed = 0.015;

    public static double  fogDensity        = 1.0;
    public static double  nightFogStartMul  = 1.0;
    public static double  nightFogEndMul    = 1.0;
    public static double  caveFogScale      = 1.0;
    public static double  caveFogMinEnd     = 24.0;

    public static double  weatherFogDarken   = 1.0;
    public static double  weatherSkyDarken   = 0.0;
    public static double  weatherCloudDarken = 0.0;

    public static double nearHazeStrength       = 0.25;

    public static boolean starsEnabled      = true;
    public static double  starAmount        = 100.0;
    public static double  starBrightness    = 1.0;
    public static double  starTwinkle       = 0.35;

    public static boolean dayStarsEnabled   = false;
    public static double  dayStarAmount     = 100.0;
    public static double  dayStarBrightness = 1.0;
    public static double  dayStarTwinkle    = 0.35;

    public static double fogColorVivid = 0.42;

    public static boolean hazeOverlayEnabled   = true;
    public static double  hazeOverlayStrength  = 0.15;

    public static boolean biomeColorsEnabled   = true;
    public static double  vegSaturation        = 1.15;
    public static double  vegBrightness        = 1.1;
    public static double  vegVariation         = 0.6;
    public static double  vegSunny             = 0.0;
    public static double  waterSaturation      = 1.0;
    public static double  waterBrightness      = 1.0;

    private static final String CONFIG_DIR               = "let_there_be_atmosphere";
    private static final String CONFIG_FILE              = "settings.properties";
    private static final String LEGACY_FILE              = "linggango_fog.properties";
    private static final String KEY_DEFAULT_FOG_HEX      = "defaultFogHex";
    private static final String KEY_FOG_START            = "fogStartMultiplier";
    private static final String KEY_MASTER               = "enabled";
    private static final String KEY_DYNAMIC_FOG          = "dynamicFogEnabled";
    private static final String KEY_VOID_FOG             = "voidFogEnabled";
    private static final String KEY_WATER_FOG            = "waterFogEnabled";
    private static final String KEY_CLOUD_RANGE          = "cloudRangeChunks";
    private static final String KEY_CLOUD_FOG            = "cloudOwnFog";
    private static final String KEY_SKY_FOG_END          = "skyFogEnd";
    private static final String KEY_WATER_FOG_DISTANCE   = "waterFogDistance";
    private static final String KEY_RAIN_DENSITY         = "rainFogDensity";
    private static final String KEY_BIOME_TINT           = "biomeTintStrength";
    private static final String KEY_SKY_BLEND            = "skyFogBlendStrength";
    private static final String KEY_CHUNK_SOFTNESS       = "chunkBorderFogSoftness";
    private static final String KEY_FOG_SPEED            = "fogTransitionSpeed";
    private static final String KEY_DIST_SPEED           = "distanceTransitionSpeed";
    private static final String KEY_FOG_DENSITY          = "fogDensity";
    private static final String KEY_NIGHT_FOG_START      = "nightFogStartMul";
    private static final String KEY_NIGHT_FOG_END        = "nightFogEndMul";
    private static final String KEY_CAVE_FOG_SCALE       = "caveFogScale";
    private static final String KEY_CAVE_FOG_MIN_END     = "caveFogMinEnd";
    private static final String KEY_WEATHER_FOG_DARKEN   = "weatherFogDarkening";
    private static final String KEY_WEATHER_SKY_DARKEN   = "weatherSkyDarkening";
    private static final String KEY_WEATHER_CLOUD_DARKEN = "weatherCloudDarkening";
    private static final String KEY_NEAR_HAZE            = "nearHazeStrength";
    private static final String KEY_FOG_COLOR_VIVID      = "fogColorVivid";

    private static final String KEY_STARS_ENABLED       = "starsEnabled";
    private static final String KEY_STAR_AMOUNT         = "starAmount";
    private static final String KEY_STAR_BRIGHTNESS     = "starBrightness";
    private static final String KEY_STAR_TWINKLE        = "starTwinkle";
    private static final String KEY_DAY_STARS           = "dayStarsEnabled";
    private static final String KEY_DAY_STAR_AMOUNT     = "dayStarAmount";
    private static final String KEY_DAY_STAR_BRIGHTNESS = "dayStarBrightness";
    private static final String KEY_DAY_STAR_TWINKLE    = "dayStarTwinkle";
    private static final String KEY_HAZE_OVERLAY        = "hazeOverlayEnabled";
    private static final String KEY_HAZE_STRENGTH       = "hazeOverlayStrength";
    private static final String KEY_BIOME_COLORS        = "biomeColorsEnabled";
    private static final String KEY_VEG_SATURATION      = "vegSaturation";
    private static final String KEY_VEG_BRIGHTNESS      = "vegBrightness";
    private static final String KEY_VEG_VARIATION       = "vegVariation";
    private static final String KEY_VEG_SUNNY           = "vegSunny";
    private static final String KEY_WATER_SATURATION    = "waterSaturation";
    private static final String KEY_WATER_BRIGHTNESS    = "waterBrightness";

    private static final float COLOR_EPSILON                   = 0.0005F;
    private static final float SPHERE_NEAR_SCALE               = 1.15F;
    private static final float SPHERE_FAR_SCALE                = 1.25F;
    private static final float CHUNK_EDGE_MARGIN               = 16.0F;
    private static final float THICK_FOG_BLOCKS                = 120.0F;
    private static final float CLEAREST_FOG_BLOCKS             = 480.0F;
    private static final float CLEAR_FOG_SHARE                 = 0.95F;
    private static final float FOG_SHARE_SPREAD                = 0.45F;
    private static final float DAY_FOG_LUMA                    = 0.8F;

    private static final float WEATHER_TINT_EASE               = 0.05F;

    private static final float VOID_ONSET_RANGE                = 32F;
    private static final int   COLOR_SAMPLE_INTERVAL_TICKS    = 8;
    private static final int   COLOR_SAMPLE_REUSE_DISTANCE_SQR= 16;
    private static final int   COLOR_SAMPLE_RADIUS            = 6;
    private static final int   SKY_OPENNESS_SAMPLE_HEIGHT     = 6;
    private static final int[] SAMPLE_X = {0, COLOR_SAMPLE_RADIUS, -COLOR_SAMPLE_RADIUS, 0, 0,  COLOR_SAMPLE_RADIUS,  COLOR_SAMPLE_RADIUS, -COLOR_SAMPLE_RADIUS, -COLOR_SAMPLE_RADIUS};
    private static final int[] SAMPLE_Z = {0, 0, 0, COLOR_SAMPLE_RADIUS, -COLOR_SAMPLE_RADIUS, COLOR_SAMPLE_RADIUS, -COLOR_SAMPLE_RADIUS,  COLOR_SAMPLE_RADIUS, -COLOR_SAMPLE_RADIUS};
    private static final float[] SAMPLE_WEIGHT = {4f, 2f, 2f, 2f, 2f, 1f, 1f, 1f, 1f};

    private static final BlockPos.MutableBlockPos SAMPLE_CURSOR = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos BIOME_CURSOR = new BlockPos.MutableBlockPos();
    private static final float[] SAT_BUF = new float[3];
    private static final float[] SUNSET_BUF = new float[4];
    private static final float[] FOG_MULT_BUF = new float[3];
    private static final float[] TINT_BUF = new float[3];
    private static final float[] FOG_SPREAD_BUF = new float[2];
    private static final float[] HEX_BUF = new float[3];

    private static boolean loaded = false;
    private static final ValueNoise noiseGen = new ValueNoise(89234L);

    private static long  lastNoiseTick  = Long.MIN_VALUE;
    private static float cachedAtmNoise = 0.5F;

    private static ResourceKey<Level> lastLevel = null;
    private static float currentNearPlane = -1F, currentFarPlane = -1F;
    private static float currentR = -1F, currentG = -1F, currentB = -1F;

    private static long lastSampleTick = Long.MIN_VALUE;
    private static int  lastSampleX = Integer.MIN_VALUE, lastSampleY = Integer.MIN_VALUE, lastSampleZ = Integer.MIN_VALUE;

    private static float sampledBiomeR = 0.5F, sampledBiomeG = 0.6F, sampledBiomeB = 0.7F;
    private static float sampledSkyR   = 0.6F, sampledSkyG   = 0.7F, sampledSkyB   = 0.9F;
    private static float sampledSkyOpenness = 1F, sampledSkyVisibility = 1F;

    private static float sampledWetFraction  = 0F;
    private static float sampledSnowFraction = 0F;

    private static float currentWaterR = -1F, currentWaterG = -1F, currentWaterB = -1F;

    private static float rainFogMultiplier = 0F;
    private static float weatherTintAmount = 0F;
    private static float weatherSnowMix    = 0F;

    private static long lastColorComputeTick = Long.MIN_VALUE;

    public static void load() {
        Properties p = new Properties();
        try {
            Path path = settingsPath();
            if (Files.exists(path)) {
                try (Reader r = Files.newBufferedReader(path)) { p.load(r); }
                Diagnostics.LOG.info("[atmosphere] settings loaded: {} keys from {}", p.size(), path);
            } else {
                Diagnostics.LOG.info("[atmosphere] no settings file at {}, using defaults", path);
            }
        } catch (Exception failure) {
            Diagnostics.LOG.error("[atmosphere] settings failed to load, using defaults", failure);
        }
        apply(p);
        loaded = true;
    }

    public static void resetDefaults() {
        apply(new Properties());
    }

    private static void apply(Properties p) {
        try {
            {
                String dfh = p.getProperty(KEY_DEFAULT_FOG_HEX, "88BFEA");
                try { defaultFogHex = Integer.parseInt(dfh.startsWith("#") ? dfh.substring(1) : dfh, 16); }
                catch (Exception ignored) { defaultFogHex = 0x88BFEA; }

                fogStartMultiplier     = prop(p, KEY_FOG_START,          0.45,  0.02, 0.65);
                masterEnabled          = Boolean.parseBoolean(p.getProperty(KEY_MASTER,       "true"));
                dynamicFogEnabled      = Boolean.parseBoolean(p.getProperty(KEY_DYNAMIC_FOG,  "true"));
                voidFogEnabled         = Boolean.parseBoolean(p.getProperty(KEY_VOID_FOG,     "true"));
                waterFogEnabled        = Boolean.parseBoolean(p.getProperty(KEY_WATER_FOG,    "true"));
                cloudRangeChunks       = (int) prop(p, KEY_CLOUD_RANGE,   32,    8,    64);
                cloudFogEnabled        = Boolean.parseBoolean(p.getProperty(KEY_CLOUD_FOG, "true"));
                skyFogEnd              = prop(p, KEY_SKY_FOG_END,         1.0,   0.3,  1.0);
                waterFogDistance       = prop(p, KEY_WATER_FOG_DISTANCE,  1.0,   0.2,  3.0);
                rainFogDensity         = prop(p, KEY_RAIN_DENSITY,        0.05,  0.01, 0.50);
                biomeTintStrength      = prop(p, KEY_BIOME_TINT,          0.82,  0.0,  1.0);
                skyFogBlendStrength    = prop(p, KEY_SKY_BLEND,           0.55,  0.0,  1.0);
                chunkBorderFogSoftness = prop(p, KEY_CHUNK_SOFTNESS,      0.14,  0.0,  0.35);
                starsEnabled      = Boolean.parseBoolean(p.getProperty(KEY_STARS_ENABLED, "true"));
                starAmount        = prop(p, KEY_STAR_AMOUNT,         100.0, 0.0, 1000.0);
                starBrightness    = prop(p, KEY_STAR_BRIGHTNESS,     1.0,   0.0, 2.0);
                starTwinkle       = prop(p, KEY_STAR_TWINKLE,        0.35,  0.0, 1.0);
                dayStarsEnabled   = Boolean.parseBoolean(p.getProperty(KEY_DAY_STARS, "false"));
                dayStarAmount     = prop(p, KEY_DAY_STAR_AMOUNT,     100.0, 0.0, 1000.0);
                dayStarBrightness = prop(p, KEY_DAY_STAR_BRIGHTNESS, 1.0,   0.0, 2.0);
                dayStarTwinkle    = prop(p, KEY_DAY_STAR_TWINKLE,    0.35,  0.0, 1.0);
                fogTransitionSpeed     = prop(p, KEY_FOG_SPEED,           0.015, 0.001, 0.1);
                distanceTransitionSpeed = prop(p, KEY_DIST_SPEED,         0.015, 0.001, 0.1);
                fogDensity             = prop(p, KEY_FOG_DENSITY,         1.0,   0.2,  5.0);
                nightFogStartMul       = prop(p, KEY_NIGHT_FOG_START,     1.0,   0.1,  3.0);
                nightFogEndMul         = prop(p, KEY_NIGHT_FOG_END,       1.0,   0.1,  3.0);
                caveFogScale           = prop(p, KEY_CAVE_FOG_SCALE,      1.0,   0.1,  2.0);
                caveFogMinEnd          = prop(p, KEY_CAVE_FOG_MIN_END,    24.0,  1.0,  256.0);
                weatherFogDarken       = prop(p, KEY_WEATHER_FOG_DARKEN,   1.0,  0.0,  2.0);
                weatherSkyDarken       = prop(p, KEY_WEATHER_SKY_DARKEN,   0.0,  0.0,  1.0);
                weatherCloudDarken     = prop(p, KEY_WEATHER_CLOUD_DARKEN, 0.0,  0.0,  1.0);
                nearHazeStrength       = prop(p, KEY_NEAR_HAZE,           0.25,  0.0,  1.0);
                fogColorVivid          = prop(p, KEY_FOG_COLOR_VIVID,     0.42,  0.0,  1.0);
                hazeOverlayEnabled     = Boolean.parseBoolean(p.getProperty(KEY_HAZE_OVERLAY, "true"));
                hazeOverlayStrength    = prop(p, KEY_HAZE_STRENGTH,       0.15,  0.0,  1.0);
                biomeColorsEnabled     = Boolean.parseBoolean(p.getProperty(KEY_BIOME_COLORS, "true"));
                vegSaturation          = prop(p, KEY_VEG_SATURATION,     1.15,   0.0,  3.0);
                vegBrightness          = prop(p, KEY_VEG_BRIGHTNESS,     1.1,   0.3,  2.0);
                vegVariation           = prop(p, KEY_VEG_VARIATION,      0.6,   0.0,  1.0);
                vegSunny               = prop(p, KEY_VEG_SUNNY,          0.0,   0.0,  1.0);
                waterSaturation        = prop(p, KEY_WATER_SATURATION,   1.0,   0.0,  3.0);
                waterBrightness        = prop(p, KEY_WATER_BRIGHTNESS,   1.0,   0.3,  2.0);
                FxConfig.read(p);
                StoryCloudConfig.read(p);
            }
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            String text = settingsText();
            SettingsWriter.submit("settings", () -> {
                Path path = settingsPath();
                Files.createDirectories(path.getParent());
                Files.writeString(path, text);
            });
        } catch (Exception ignored) {}
    }

    public static String settingsText() {
        ConfigText out = new ConfigText();
        {
            out.header(
                    "Let There Be Atmosphere",
                    "Global settings. Per-biome settings live in biomes.json next to this file.",
                    "Colours are RRGGBB hex. Delete a line to get its default back.");

            out.section("Master");
            out.note("Turns the mod off without removing it. Everything below is ignored",
                     "while this is false.");
            out.put(KEY_MASTER, masterEnabled);

            out.section("Fog");
            out.note("Fog that takes its colour and its distance from the biome you are in.");
            out.put(KEY_DYNAMIC_FOG, dynamicFogEnabled);
            out.note("Fog colour for any biome you have not given one of its own.");
            out.putHex(KEY_DEFAULT_FOG_HEX, defaultFogHex);
            out.note("Where the fog starts, as a share of your render distance.",
                     "Lower brings it closer to you.");
            out.put(KEY_FOG_START, fogStartMultiplier);
            out.note("How strongly the biome's own colour pulls the fog. 0 keeps vanilla's.");
            out.put(KEY_BIOME_TINT, biomeTintStrength);
            out.note("How much of the sky colour bleeds down into the fog.");
            out.put(KEY_SKY_BLEND, skyFogBlendStrength);
            out.note("Extra saturation on the finished fog colour. 0 is untouched.");
            out.put(KEY_FOG_COLOR_VIVID, fogColorVivid);
            out.note("Widens the band where fog fades in, so the edge of the loaded",
                     "world is less obvious.");
            out.put(KEY_CHUNK_SOFTNESS, chunkBorderFogSoftness);
            out.note("How fast the fog colour catches up when it changes.",
                     "Lower is slower and smoother.");
            out.put(KEY_FOG_SPEED, fogTransitionSpeed);
            out.note("Same, for the fog distances rather than the colour.");
            out.put(KEY_DIST_SPEED, distanceTransitionSpeed);
            out.note("Thickness multiplier on the whole fog. Above 1 pulls both ends in.");
            out.put(KEY_FOG_DENSITY, fogDensity);
            out.note("Night fog, as multipliers on the day distances. 1.0 leaves night alone.");
            out.put(KEY_NIGHT_FOG_START, nightFogStartMul);
            out.put(KEY_NIGHT_FOG_END, nightFogEndMul);
            out.note("Fog underground, where no sky reaches. Below 1 closes the walls in,",
                     "and the second value is the shortest the fog is ever allowed to end.");
            out.put(KEY_CAVE_FOG_SCALE, caveFogScale);
            out.put(KEY_CAVE_FOG_MIN_END, caveFogMinEnd);

            out.section("Depth and distance");
            out.note("Haze on the terrain in front of you, so distance reads as distance.");
            out.put(KEY_NEAR_HAZE, nearHazeStrength);
            out.note("How far in that haze pulls the far end of the fog.");
            out.note("Darkness closing in as you get near bedrock.");
            out.put(KEY_VOID_FOG, voidFogEnabled);
            out.note("A soft band of fog colour along the horizon line.");
            out.put(KEY_HAZE_OVERLAY, hazeOverlayEnabled);
            out.put(KEY_HAZE_STRENGTH, hazeOverlayStrength);
            out.note("Fogs the sky itself ahead of the terrain so the skyline dissolves.",
                     "1.0 is off. Try 0.8 or so.");
            out.put(KEY_SKY_FOG_END, skyFogEnd);

            out.section("Weather");
            out.note("How close rain pulls the fog in. Lower means heavier weather.");
            out.put(KEY_RAIN_DENSITY, rainFogDensity);
            out.note("How hard a storm pulls the fog toward its rain colour. 1.0 is normal.");
            out.put(KEY_WEATHER_FOG_DARKEN, weatherFogDarken);
            out.note("Extra darkening a storm lays on the sky and on the clouds,",
                     "on top of what the game already does. 0 is off.");
            out.put(KEY_WEATHER_SKY_DARKEN, weatherSkyDarken);
            out.put(KEY_WEATHER_CLOUD_DARKEN, weatherCloudDarken);

            out.section("Underwater");
            out.note("Lets a biome set its own underwater colour and visibility.");
            out.put(KEY_WATER_FOG, waterFogEnabled);
            out.note("How far you can see underwater. 1.0 is vanilla.");
            out.put(KEY_WATER_FOG_DISTANCE, waterFogDistance);

            out.section("Clouds");
            out.note("How far clouds reach, in chunks, whatever your render distance is.");
            out.put(KEY_CLOUD_RANGE, cloudRangeChunks);
            out.note("Keeps clouds out of the terrain fog, so a long cloud range is",
                     "actually visible instead of fading into haze.");
            out.put(KEY_CLOUD_FOG, cloudFogEnabled);

            out.section("Stars");
            out.note("Density is a percentage. Above 100 stacks extra layers of stars.");
            out.put(KEY_STARS_ENABLED, starsEnabled);
            out.put(KEY_STAR_AMOUNT, starAmount);
            out.put(KEY_STAR_BRIGHTNESS, starBrightness);
            out.note("How much the stars shimmer. 0 holds them still.");
            out.put(KEY_STAR_TWINKLE, starTwinkle);
            out.note("Stars during the day. Off by default because it is not subtle.");
            out.put(KEY_DAY_STARS, dayStarsEnabled);
            out.put(KEY_DAY_STAR_AMOUNT, dayStarAmount);
            out.put(KEY_DAY_STAR_BRIGHTNESS, dayStarBrightness);
            out.put(KEY_DAY_STAR_TWINKLE, dayStarTwinkle);

            out.section("Grass, leaves and water");
            out.note("Recolours blocks rather than the air. Chunks rebuild when this",
                     "changes, so expect a short pause.");
            out.put(KEY_BIOME_COLORS, biomeColorsEnabled);
            out.note("1.0 is vanilla for all four.");
            out.put(KEY_VEG_SATURATION, vegSaturation);
            out.put(KEY_VEG_BRIGHTNESS, vegBrightness);
            out.note("How much grass and leaves drift between sunny and deep green patches across the land. 0 is off.");
            out.put(KEY_VEG_VARIATION, vegVariation);
            out.note("Lifts grass and leaves toward a lighter, sunnier lime green. 0 is off.");
            out.put(KEY_VEG_SUNNY, vegSunny);
            out.put(KEY_WATER_SATURATION, waterSaturation);
            out.put(KEY_WATER_BRIGHTNESS, waterBrightness);

            FxConfig.write(out);
            StoryCloudConfig.write(out);
        }
        return out.toString();
    }

    private static Path settingsPath() throws java.io.IOException {
        Path dir = FMLPaths.CONFIGDIR.get().resolve(CONFIG_DIR);
        Path path = dir.resolve(CONFIG_FILE);
        Path legacy = FMLPaths.CONFIGDIR.get().resolve(LEGACY_FILE);
        if (!Files.exists(path) && Files.exists(legacy)) {
            Files.createDirectories(dir);
            Files.move(legacy, path);
        }
        return path;
    }

    private static boolean fastGraphics;
    private static boolean shaderPack;

    public static boolean running() { return masterEnabled && !fastGraphics; }

    public static boolean active() { return running() && !shaderPack; }

    public static String pausedBy() {
        if (fastGraphics) return "Fast graphics";
        if (shaderPack) return "a shader pack";
        return null;
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        boolean wasRunning = running();
        boolean wasActive = active();
        fastGraphics = Minecraft.getInstance().options.graphicsMode().get() == GraphicsStatus.FAST;
        shaderPack = ColorGrade.shaderPackActive();
        if (wasActive != active()) rebuildStars();
        if (wasRunning != running() && biomeColorsEnabled) refreshBiomeColors();
    }

    public static int cloudCells() {
        return Mth.clamp(Mth.ceil(cloudRangeChunks * 16f / 96f), 1, 16);
    }

    public static float cloudFogEnd() {
        return cloudRangeChunks * 16f * 1.2f;
    }

    public static float skyR() { return sampledSkyR; }
    public static float skyG() { return sampledSkyG; }
    public static float skyB() { return sampledSkyB; }
    public static float skyVisibility() { return sampledSkyVisibility; }

    public static float fogColorR() { return currentR < 0 ? sampledSkyR : currentR; }
    public static float fogColorG() { return currentG < 0 ? sampledSkyG : currentG; }
    public static float fogColorB() { return currentB < 0 ? sampledSkyB : currentB; }

    public static double effectiveStarDensity() {
        if (!starsEnabled) return 100.0;
        double d = starAmount;
        if (dayStarsEnabled) d = Math.max(d, dayStarAmount);
        return d;
    }

    public static void rebuildStars() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.levelRenderer instanceof LevelRendererStarAccessor acc) acc.linggango$createStars();
        });
    }

    public static void refreshBiomeColors() {
        BiomeColorModifier.clearCache();
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.level != null) mc.level.clearTintCaches();
            if (mc.levelRenderer != null) mc.levelRenderer.allChanged();
        });
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.CLIENT) return;
        if (event.player != Minecraft.getInstance().player) return;

        if (active()) updateRainFog(event.player);

        if (!active() || !voidFogEnabled || !dynamicFogEnabled
                || event.player.hasEffect(MobEffects.NIGHT_VISION)) return;
        float vs = event.player.level().getMinBuildHeight() + 32f, py = (float) event.player.getY();
        if (py >= vs || (event.player.tickCount & 1) != 0) return;
        float depth = ss(Mth.clamp((vs - py) / 32f, 0f, 1f));
        RandomSource rand = event.player.getRandom();
        for (int i = 0, checks = 2 + Mth.floor(depth * 6f); i < checks; ++i)
            if (rand.nextFloat() < depth * 0.72f)
                event.player.level().addParticle(ParticleTypes.ASH,
                        event.player.getX() + rand.nextInt(16) - rand.nextInt(16),
                        event.player.getY() + rand.nextInt(14) - rand.nextInt(10),
                        event.player.getZ() + rand.nextInt(16) - rand.nextInt(16), 0, 0, 0);
    }

    private static float biomeFogEnd(float fogEnd, float renderDist) {
        if (fogEnd <= THICK_FOG_BLOCKS) return fogEnd;
        float clearness = Mth.clamp(fogEnd / CLEAREST_FOG_BLOCKS, 0f, 1f);
        return renderDist * (CLEAR_FOG_SHARE - (1f - clearness) * FOG_SHARE_SPREAD);
    }

    private static void applySkyFog(ViewportEvent.@NonNull RenderFog event) {
        float fraction = (float) Mth.clamp(skyFogEnd, 0.3, 1.0);
        if (fraction >= 0.999f) return;

        event.setNearPlaneDistance(0f);
        event.setFarPlaneDistance(event.getFarPlaneDistance() * fraction);
        event.setFogShape(FogShape.CYLINDER);
        event.setCanceled(true);
    }

    private static void applyWaterFog(ViewportEvent.@NonNull RenderFog event) {
        if (!waterFogEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        AtmosphereConfigManager.AtmosphereSettings bs = settingsAt(mc.level, event.getCamera().getPosition());
        float end = 96f * (float) Mth.clamp(waterFogDistance, 0.2, 3.0);
        if (bs != null) end *= Mth.clamp(bs.waterFogDist, 0.2f, 2.0f);
        if (mc.player != null) end *= Math.max(0.25f, mc.player.getWaterVision());

        float renderDist = event.getFarPlaneDistance();
        if (end > renderDist) {
            end = renderDist;
            event.setFogShape(FogShape.CYLINDER);
        }

        event.setNearPlaneDistance(-8f);
        event.setFarPlaneDistance(end);
        event.setCanceled(true);
    }

    private static AtmosphereConfigManager.AtmosphereSettings settingsAt(
            net.minecraft.world.level.@NonNull Level level, @NonNull Vec3 pos) {
        if (AtmosphereConfigManager.ATMOSPHERES.isEmpty()) return null;
        Holder<Biome> holder = level.getBiome(
                BIOME_CURSOR.set(Mth.floor(pos.x), Mth.floor(pos.y), Mth.floor(pos.z)));
        return holder instanceof Holder.Reference<Biome> ref
                ? AtmosphereConfigManager.ATMOSPHERES.get(ref.key().location()) : null;
    }

    private static void updateRainFog(net.minecraft.world.entity.player.@NonNull Player player) {
        var level = player.level();
        float skyLight = Mth.clamp(
                (level.getBrightness(LightLayer.SKY, player.blockPosition()) - 8f) / 7f, 0f, 1f);
        float target = level.getRainLevel(1f) * skyLight * Mth.lerp(sampledWetFraction, 0.5f, 1.0f);
        rainFogMultiplier += (target - rainFogMultiplier) * 0.2f;
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.@NonNull RenderFog event) {
        if (!loaded) load();
        if (!active() || !dynamicFogEnabled || event.isCanceled()) {
            Diagnostics.skip(Diagnostics.FOG, !masterEnabled ? "master switch is off"
                    : pausedBy() != null ? "paused by " + pausedBy() : "biome fog is off");
            return;
        }
        FogType fluid = CameraFluid.of(event.getCamera());
        if (fluid == FogType.WATER) { applyWaterFog(event); return; }
        if (fluid != FogType.NONE) return;
        if (event.getMode() == FogRenderer.FogMode.FOG_SKY) { applySkyFog(event); return; }
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { reset(); lastLevel = null; return; }
        var level = mc.level;
        if (lastLevel != level.dimension()) { reset(); lastLevel = level.dimension(); }
        if (mc.player.isSpectator()) {
            float clear = event.getFarPlaneDistance() * 2f;
            event.setNearPlaneDistance(clear);
            event.setFarPlaneDistance(clear);
            event.setCanceled(true);
            return;
        }
        if (event.getCamera().getEntity() != mc.player
                || mc.player.hasEffect(MobEffects.NIGHT_VISION)
                || mc.player.hasEffect(MobEffects.BLINDNESS)
                || mc.player.hasEffect(MobEffects.DARKNESS)) return;

        Vec3  pos = event.getCamera().getPosition();
        float pt  = (float) event.getPartialTick();
        sampleColors(mc, pos, pt);

        AtmosphereConfigManager.AtmosphereSettings bs = BiomeBlend.settings();

        float renderDist      = event.getFarPlaneDistance();
        float originalEnd     = renderDist;
        float cwNear          = renderDist * Mth.clamp((float) fogStartMultiplier, 0.02F, 0.60F);
        float cwFar           = renderDist * 0.92F;

        if (bs != null) {
            float cov = BiomeBlend.coverage();
            float end   = biomeFogEnd(bs.fogEnd, renderDist);
            float start = end * Mth.clamp(bs.fogStart / Math.max(bs.fogEnd, 1f), 0f, 1f);
            cwNear      = Mth.lerp(cov, cwNear, start);
            cwFar       = Mth.lerp(cov, cwFar,  end);
            originalEnd = Math.min(Mth.lerp(cov, originalEnd, end), renderDist);
        }

        float hazeStr = Mth.clamp(
                (bs != null && bs.nearHaze > 0f) ? bs.nearHaze : (float) nearHazeStrength, 0f, 1f);
        if (hazeStr > 0f) {
            float nearPush      = 1f - (1f - hazeStr) * (1f - hazeStr);
            cwNear              = Mth.lerp(nearPush, cwNear, 0f);
        }

        float heightFog = bs != null ? bs.heightFogStr : 0.5f;
        if (heightFog > 0f) {
            float sea = level.getSeaLevel();
            float altFactor = ss(Mth.clamp((sea + 24f - (float) pos.y) / 72f, 0f, 1f)) * heightFog;
            if (altFactor > 0f) {
                cwNear = Mth.lerp(altFactor * 0.55f, cwNear, cwNear * 0.35f);
                cwFar  = Mth.lerp(altFactor * 0.40f, cwFar,  cwFar  * (0.55f + 0.15f * (1f - sampledSkyVisibility)));
            }
        }

        float dayTicks = level.getDayTime() % DayCurves.PERIOD + pt;
        float nightAmount = DayCurves.nightFraction(dayTicks);
        FogDepth.spread(cwNear, cwFar, nightAmount,
                bs != null ? bs.nightFogStartMul : (float) nightFogStartMul,
                bs != null ? bs.nightFogEndMul   : (float) nightFogEndMul,
                bs != null ? bs.fogDensity       : (float) fogDensity,
                sampledSkyVisibility, (float) caveFogScale, (float) caveFogMinEnd,
                FOG_SPREAD_BUF);
        cwNear = FOG_SPREAD_BUF[0];
        cwFar  = FOG_SPREAD_BUF[1];

        long tick = level.getGameTime();
        if (tick != lastNoiseTick) {
            cachedAtmNoise = noiseGen.at((tick * 0.0024F) + (float)((pos.x + pos.z) * 0.008));
            lastNoiseTick  = tick;
        }
        cwNear *= Mth.lerp(cachedAtmNoise, 0.97F, 1.03F);
        cwFar  *= Mth.lerp(cachedAtmNoise, 0.985F, 1.015F);

        float rainLvl = level.getRainLevel(pt);
        if (rainFogMultiplier > 0.001f) {
            float ts = ss(level.getThunderLevel(pt));
            float rm = bs != null ? bs.rainMult : 1f;
            float rNear = originalEnd * (float) Mth.clamp(rainFogDensity, 0.01, 0.50);
            float rFar  = originalEnd * Mth.lerp(ts, 0.80F, 0.58F);

            float bl = Mth.clamp(rainFogMultiplier * rm, 0f, 1f);
            cwNear = Mth.lerp(bl, cwNear, rNear);
            cwFar  = Mth.lerp(bl, cwFar,  rFar);
        }

        if (EndAir.fog(level)) {
            cwNear = EndAir.fogNear();
            cwFar  = EndAir.fogFar();
        }

        if (voidFogEnabled) {
            float vs = bs != null ? bs.voidFogStr : 1f, py = (float) pos.y;
            float voidStart = mc.level.getMinBuildHeight() + 32f;
            if (py < voidStart) {
                float vd = ss(Mth.clamp((voidStart - py) / 32f, 0f, 1f)) * vs;
                cwNear = Mth.lerp(vd, cwNear, 0f); cwFar = Mth.lerp(vd, cwFar, 18f);
            }
        }

        float deepHaze = DeepDark.presence();
        if (deepHaze > 0f) {
            cwNear = Mth.lerp(deepHaze, cwNear, DeepDark.fogNear());
            cwFar  = Mth.lerp(deepHaze, cwFar,  DeepDark.fogFar());
        }

        float clarity = (float) FxConfig.fogClarity * sampledSkyVisibility * (1f - rainLvl);
        if (clarity > 0f) {
            cwNear = Mth.lerp(clarity, cwNear, renderDist * 0.72f);
            cwFar  = Mth.lerp(clarity, cwFar,  renderDist);
        }

        float limit   = Math.max(32f, renderDist - CHUNK_EDGE_MARGIN);
        float tFar    = Mth.clamp(cwFar, Math.min(limit, Math.max(32f, renderDist * 0.15F)), limit);
        float edge    = tFar * (float) Mth.clamp(chunkBorderFogSoftness, 0.0, 0.35) * (1f - rainLvl * 0.45F);
        float tNear   = Mth.clamp(cwNear - edge, 0f, tFar - Math.max(16f, tFar * 0.35F));

        float lerp = AtmosphereScreen.isLiveEdit(mc.screen) ? 1f : (float) distanceTransitionSpeed;
        currentNearPlane = sv(currentNearPlane, tNear, lerp);
        currentFarPlane  = sv(currentFarPlane,  tFar,  lerp);

        boolean sphere = bs != null && bs.sphereFog;
        float shapeNear = sphere ? currentNearPlane * SPHERE_NEAR_SCALE : currentNearPlane;
        float shapeFar = Math.min(sphere ? currentFarPlane * SPHERE_FAR_SCALE : currentFarPlane, limit);
        shapeNear = WeatherStorms.fogNear(shapeNear);
        shapeFar = WeatherStorms.fogFar(shapeFar);
        event.setNearPlaneDistance(shapeNear);
        event.setFarPlaneDistance(shapeFar);
        event.setFogShape(sphere ? FogShape.SPHERE : FogShape.CYLINDER);
        Diagnostics.mark(Diagnostics.FOG, Diagnostics.detail()
                ? String.format("%s, near %.0f far %.0f",
                        sphere ? "sphere" : "cylinder", shapeNear, shapeFar) : null);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.@NonNull ComputeFogColor event) {
        if (!loaded) load();
        if (!active() || !dynamicFogEnabled) return;
        FogType cameraFluid = CameraFluid.of(event.getCamera());
        if (cameraFluid == FogType.WATER) { applyWaterFogColor(event); return; }
        if (cameraFluid != FogType.NONE) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { reset(); lastLevel = null; return; }
        var level = mc.level;
        if (lastLevel != level.dimension()) { reset(); lastLevel = level.dimension(); }
        if (mc.player != null && (mc.player.hasEffect(MobEffects.NIGHT_VISION)
                || mc.player.hasEffect(MobEffects.BLINDNESS)
                || mc.player.hasEffect(MobEffects.DARKNESS))) return;

        Vec3  pos = event.getCamera().getPosition();
        float pt  = (float) event.getPartialTick();
        sampleColors(mc, pos, pt);

        AtmosphereConfigManager.AtmosphereSettings bs = BiomeBlend.settings();

        float r = event.getRed(), g = event.getGreen(), b = event.getBlue();

        float vL = Math.max(0.05F, luma(r, g, b));
        float sL = Math.max(0.05F, luma(sampledSkyR,   sampledSkyG,   sampledSkyB));
        float bR = Mth.clamp(vL / DAY_FOG_LUMA, 0f, 1.15f), sR = vL / sL;

        float tBR = Math.min(1f, sampledBiomeR * bR), tBG = Math.min(1f, sampledBiomeG * bR), tBB = Math.min(1f, sampledBiomeB * bR);
        float tSR = Math.min(1f, sampledSkyR   * sR), tSG = Math.min(1f, sampledSkyG   * sR), tSB = Math.min(1f, sampledSkyB   * sR);

        float rain = level.getRainLevel(pt), thunder = level.getThunderLevel(pt);
        float sun  = Mth.cos(level.getTimeOfDay(pt) * ((float) Math.PI * 2f));
        boolean nether = level.dimensionType().ultraWarm();
        float dawn = nether ? 0f : ss(Mth.clamp(1f - Math.abs(sun) * 5f, 0f, 1f));
        float nite = nether ? 0f : ss(Mth.clamp((-sun * 0.70F) + 0.12F,  0f, 1f));

        boolean end = level.effects().skyType() == DimensionSpecialEffects.SkyType.END;
        float bBlend = Mth.clamp((float) biomeTintStrength, 0f, 1f) * (nether || end ? BiomeBlend.coverage() : 1f);
        float sBlend = (bs != null ? bs.skyTintStr : (float) skyFogBlendStrength) * sampledSkyVisibility;
        r = Mth.lerp(bBlend, r, tBR); g = Mth.lerp(bBlend, g, tBG); b = Mth.lerp(bBlend, b, tBB);
        r = Mth.lerp(sBlend, r, tSR); g = Mth.lerp(sBlend, g, tSG); b = Mth.lerp(sBlend, b, tSB);

        if (dawn > 0f && rain < 0.7F) {
            float ds = bs != null ? bs.dawnDuskStr : 1f;
            float wR, wG, wB;
            if (bs != null && bs.dawnDuskHex >= 0) {
                float[] c = hexRGB(bs.dawnDuskHex); float ra = vL / Math.max(0.05f, luma(c[0], c[1], c[2]));
                wR = Math.min(1f, c[0] * 1.06f * ra); wG = Math.min(1f, c[1] * 0.98f * ra); wB = Math.min(1f, c[2] * 0.88f * ra);
            } else {
                wR = Math.min(1f, (sampledSkyR * 1.06f + 0.05f) * sR); wG = Math.min(1f, (sampledSkyG * 0.98f + 0.02f) * sR); wB = Math.min(1f, sampledSkyB * 0.88f * sR);
            }
            float wb = dawn * sampledSkyVisibility * (1f - rain) * 0.40f * ds
                    * (bs != null && bs.dawnDuskHex >= 0 ? BiomeBlend.dawnDuskHexCoverage() : 1f);
            r = Mth.lerp(wb, r, wR); g = Mth.lerp(wb * 0.85f, g, wG); b = Mth.lerp(wb * 0.55f, b, wB);
        }

        if (nite > 0f) {
            float ns = bs != null ? bs.nightStr : 1f;
            float cR, cG, cB;
            if (bs != null && bs.nightHex >= 0) {
                float[] c = hexRGB(bs.nightHex); float ra = vL / Math.max(0.05f, luma(c[0], c[1], c[2]));
                cR = Math.min(1f, (c[0] * 0.45f + 0.04f) * ra); cG = Math.min(1f, (c[1] * 0.52f + 0.05f) * ra); cB = Math.min(1f, (c[2] * 0.75f + 0.10f) * ra);
            } else {
                DayCurves.fogMultiply(level.getDayTime() % DayCurves.PERIOD + pt, FOG_MULT_BUF);
                cR = Math.min(1f, sampledBiomeR * FOG_MULT_BUF[0]);
                cG = Math.min(1f, sampledBiomeG * FOG_MULT_BUF[1]);
                cB = Math.min(1f, sampledBiomeB * FOG_MULT_BUF[2]);
            }
            float nb = 0.45f * nite * ns
                    * (bs != null && bs.nightHex >= 0 ? BiomeBlend.nightHexCoverage() : 1f);
            r = Mth.lerp(nb, r, cR); g = Mth.lerp(nb, g, cG); b = Mth.lerp(nb, b, cB);
        }

        if (rain > 0f) {
            float rs = ss(rain), ts = ss(thunder), lm = luma(r, g, b), rm = bs != null ? bs.rainMult : 1f;
            float rR, rG, rB;
            if (bs != null && bs.rainFogHex >= 0) {
                float[] c = hexRGB(bs.rainFogHex); float ra = lm / Math.max(0.01f, luma(c[0], c[1], c[2]));
                rR = Math.min(1f, c[0] * ra); rG = Math.min(1f, c[1] * ra); rB = Math.min(1f, c[2] * ra);
            } else {
                rR = Math.min(1f, tSR * (1f - rs * 0.5f));
                rG = Math.min(1f, tSG * (1f - rs * 0.5f));
                rB = Math.min(1f, tSB * (1f - rs * 0.4f));
            }
            if (bs != null && bs.thunderFogHex >= 0) {
                float[] c = hexRGB(bs.thunderFogHex); float ta = lm / Math.max(0.01f, luma(c[0], c[1], c[2]));
                rR = Mth.lerp(ts, rR, Math.min(1f, c[0] * ta)); rG = Mth.lerp(ts, rG, Math.min(1f, c[1] * ta)); rB = Mth.lerp(ts, rB, Math.min(1f, c[2] * ta));
            } else {
                float storm = 1f - ts * 0.5f;
                rR *= storm; rG *= storm; rB *= storm;
            }
            float bl = Mth.clamp(rs * rm * (float) weatherFogDarken, 0f, 1f);
            r = Mth.lerp(bl * 0.78f, r, rR); g = Mth.lerp(bl * 0.78f, g, rG); b = Mth.lerp(bl * 0.82f, b, rB);
        }

        boolean endFog = EndAir.fog(level);
        float endVoid = 0f;
        if (voidFogEnabled) {
            float py = (float) pos.y, voidStart = mc.level.getMinBuildHeight() + VOID_ONSET_RANGE;
            if (py < voidStart) {
                float darkness = Mth.clamp((voidStart - py) / VOID_ONSET_RANGE, 0f, 1f)
                        * (bs != null ? bs.voidFogStr : 1f);
                float brightness = (1f - darkness) * (1f - darkness);
                if (endFog) endVoid = darkness;
                else { r *= brightness; g *= brightness; b *= brightness; }
            }
        }

        if (endFog || DeepDark.presence() > 0f) {
            float[] c = TINT_BUF;
            c[0] = r; c[1] = g; c[2] = b;
            if (endFog) EndAir.tint(c, sampledBiomeR, sampledBiomeG, sampledBiomeB, endVoid);
            if (DeepDark.presence() > 0f) DeepDark.tint(c);
            r = c[0]; g = c[1]; b = c[2];
        }

        if (bs != null && bs.ambientHex >= 0) {
            float ab = 0.08f * sampledSkyVisibility * BiomeBlend.ambientHexCoverage();
            float[] c = hexRGB(bs.ambientHex);
            r = Mth.clamp(r + c[0] * ab, 0f, 1f); g = Mth.clamp(g + c[1] * ab, 0f, 1f); b = Mth.clamp(b + c[2] * ab, 0f, 1f);
        }

        r = Mth.clamp(r, 0f, 1f); g = Mth.clamp(g, 0f, 1f); b = Mth.clamp(b, 0f, 1f);

        float satTotal = (1f + (bs != null ? bs.satBoost : 0f)) * (1f + (float) fogColorVivid) - 1f;
        if (satTotal != 0f) {
            float[] buf = SAT_BUF;
            sat(buf, r, g, b, satTotal);
            r = Mth.clamp(buf[0], 0f, 1f); g = Mth.clamp(buf[1], 0f, 1f); b = Mth.clamp(buf[2], 0f, 1f);
        }
        if (bs != null && bs.brightness != 0f) {
            r = Mth.clamp(r + bs.brightness, 0f, 1f);
            g = Mth.clamp(g + bs.brightness, 0f, 1f);
            b = Mth.clamp(b + bs.brightness, 0f, 1f);
        }

        float hazeStr = Mth.clamp((bs != null && bs.nearHaze > 0f) ? bs.nearHaze : (float) nearHazeStrength, 0f, 1f);
        if (hazeStr > 0f) {
            float nearR = Mth.clamp(r * 1.08f + sampledBiomeR * 0.04f, 0f, 1f);
            float nearG = Mth.clamp(g * 1.06f + sampledBiomeG * 0.04f, 0f, 1f);
            float nearB = Mth.clamp(b * 1.04f + sampledBiomeB * 0.05f, 0f, 1f);
            float farL = luma(r, g, b);
            float farR = Mth.lerp(farL * 0.92f, r, sampledSkyR * 0.85f);
            float farG = Mth.lerp(farL * 0.94f, g, sampledSkyG * 0.88f);
            float farB = Mth.lerp(farL * 0.96f, b, sampledSkyB * 0.92f);
            float aerial = hazeStr * sampledSkyVisibility * (0.35f + (bs != null ? bs.heightFogStr * 0.25f : 0.12f));
            r = Mth.lerp(aerial, nearR, farR);
            g = Mth.lerp(aerial, nearG, farG);
            b = Mth.lerp(aerial, nearB, farB);
        }

        if (FxConfig.sunsetFogStrength > 0.0 && level.dimensionType().hasSkyLight()) {
            float sunStr = (float) FxConfig.sunsetFogStrength * (bs != null ? bs.dawnDuskStr : 1f);
            float sunAmt = SunTint.amount(level, event.getCamera().getLookVector(), pt,
                    FxConfig.sunsetWide, sunStr, SUNSET_BUF)
                    * sampledSkyVisibility * (1f - rain * 0.5f);
            if (sunAmt > 0f) {
                r = Mth.lerp(sunAmt, r, SUNSET_BUF[0]);
                g = Mth.lerp(sunAmt, g, SUNSET_BUF[1]);
                b = Mth.lerp(sunAmt, b, SUNSET_BUF[2]);
            }
        }

        if (VividSky.on()) {
            VividSky.update(level, pt, SunTint.lookFactor(level, event.getCamera().getLookVector(), pt));
            float vk = VividSky.strength() * sampledSkyVisibility;
            r = Mth.lerp(vk, r, VividSky.HORIZON[0]);
            g = Mth.lerp(vk, g, VividSky.HORIZON[1]);
            b = Mth.lerp(vk, b, VividSky.HORIZON[2]);
        }

        if (nether && NetherAir.warm(level, pos.x, pos.y, pos.z)) {
            float k = Mth.clamp((Math.max(g, b) - r) * 4f, 0f, 0.85f);
            if (k > 0f) {
                float l = luma(r, g, b);
                r = Mth.lerp(k, r, Math.min(1f, l * 1.9f));
                g = Mth.lerp(k, g, Math.min(1f, l * 0.72f));
                b = Mth.lerp(k, b, Math.min(1f, l * 0.42f));
            }
        }

        float[] stormFog = WeatherStorms.fogColor(level, pt, r, g, b);
        if (stormFog != null) { r = stormFog[0]; g = stormFog[1]; b = stormFog[2]; }
        float cl = AtmosphereScreen.isLiveEdit(mc.screen) ? 1f : (float) fogTransitionSpeed;
        currentR = sv(currentR, r, cl); currentG = sv(currentG, g, cl); currentB = sv(currentB, b, cl);
        float flash = Lightning.flash();
        if (flash > 0f) {
            event.setRed(Lightning.mix(currentR, 0.72f, flash, 0.5f));
            event.setGreen(Lightning.mix(currentG, 0.78f, flash, 0.5f));
            event.setBlue(Lightning.mix(currentB, 0.95f, flash, 0.5f));
        } else {
            event.setRed(currentR); event.setGreen(currentG); event.setBlue(currentB);
        }
    }

    private static void applyWaterFogColor(ViewportEvent.@NonNull ComputeFogColor event) {
        if (!waterFogEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        AtmosphereConfigManager.AtmosphereSettings bs = settingsAt(mc.level, event.getCamera().getPosition());
        if (bs == null || bs.waterFogHex < 0) {
            currentWaterR = currentWaterG = currentWaterB = -1f;
            return;
        }

        float[] c = hexRGB(bs.waterFogHex);
        float lerp = AtmosphereScreen.isLiveEdit(mc.screen) ? 1f : (float) fogTransitionSpeed;
        currentWaterR = sv(currentWaterR, c[0], lerp);
        currentWaterG = sv(currentWaterG, c[1], lerp);
        currentWaterB = sv(currentWaterB, c[2], lerp);
        event.setRed(currentWaterR);
        event.setGreen(currentWaterG);
        event.setBlue(currentWaterB);
    }

    @SubscribeEvent
    public static void onRenderAtmosphereOverlays(RenderGuiEvent.@NonNull Pre event) {
        if (!active() || !dynamicFogEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        if (!mc.level.dimensionType().hasSkyLight()) return;
        if (mc.player.isSpectator()
                || mc.player.hasEffect(MobEffects.BLINDNESS)
                || mc.player.hasEffect(MobEffects.DARKNESS)
                || mc.player.hasEffect(MobEffects.NIGHT_VISION)) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        if (CameraFluid.of(camera) != FogType.NONE) return;

        float vis = sampledSkyVisibility;
        if (vis <= 0.03f) return;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        if (w <= 0 || h <= 0) return;

        drawWeatherTint(mc, event, w, h, vis);
    }

    public static float horizonGlow(float partialTick, float @NonNull [] rgb) {
        Minecraft mc = Minecraft.getInstance();
        if (FxConfig.hazeBandEnabled || !hazeOverlayEnabled || hazeOverlayStrength <= 0.0) return 0f;
        if (!active() || !dynamicFogEnabled || mc.level == null || mc.player == null) return 0f;
        if (!mc.level.dimensionType().hasSkyLight()) return 0f;
        if (mc.player.isSpectator()
                || mc.player.hasEffect(MobEffects.BLINDNESS)
                || mc.player.hasEffect(MobEffects.DARKNESS)
                || mc.player.hasEffect(MobEffects.NIGHT_VISION)) return 0f;
        if (CameraFluid.of(mc.gameRenderer.getMainCamera()) != FogType.NONE) return 0f;
        float vis = sampledSkyVisibility;
        if (vis <= 0.03f) return 0f;
        rgb[0] = Mth.clamp(currentR < 0 ? sampledSkyR : currentR, 0f, 1f);
        rgb[1] = Mth.clamp(currentG < 0 ? sampledSkyG : currentG, 0f, 1f);
        rgb[2] = Mth.clamp(currentB < 0 ? sampledSkyB : currentB, 0f, 1f);
        float rain = mc.level.getRainLevel(partialTick);
        float day = Mth.clamp((mc.level.getSkyDarken(partialTick) - 0.2f) / 0.8f, 0f, 1f);
        return Mth.clamp((float) hazeOverlayStrength, 0f, 1f) * vis * day * day * (0.6f + rain * 0.4f) * (150f / 255f);
    }

    private static void drawWeatherTint(@NonNull Minecraft mc, RenderGuiEvent.@NonNull Pre event,
                                        int w, int h, float vis) {
        float pt = (float) event.getPartialTick();
        var level = mc.level;
        if (level == null) return;

        AtmosphereConfigManager.AtmosphereSettings bs = BiomeBlend.settings();

        float target = 0f;
        if (FxConfig.weatherTintEnabled && sampledWetFraction > 0f) {
            target = WeatherTint.amount(level.getRainLevel(pt), level.getThunderLevel(pt), vis,
                    level.getSkyDarken(pt), FxConfig.weatherTintStrength)
                    * sampledWetFraction * (bs != null ? bs.rainMult : 1f);
        }

        weatherTintAmount = sv(weatherTintAmount, Mth.clamp(target, 0f, 1f), WEATHER_TINT_EASE);
        weatherSnowMix    = sv(weatherSnowMix, sampledSnowFraction, WEATHER_TINT_EASE);
        if (weatherTintAmount <= 0.002f) return;

        int alpha = (int) (weatherTintAmount * 255f);
        if (alpha <= 0) return;
        int rgb = WeatherTint.color(WeatherTint.color(FxConfig.rainTintHex, FxConfig.snowTintHex, weatherSnowMix), 0xD8A860,
                WeatherStorms.sandstorm());
        event.getGuiGraphics().fill(RenderType.guiOverlay(), 0, 0, w, h, (alpha << 24) | rgb);
    }

    private static void sampleColors(@NonNull Minecraft mc, @NonNull Vec3 pos, float pt) {
        if (mc.level == null) return;
        var level = mc.level;
        long t = level.getGameTime();

        if (t == lastColorComputeTick) return;

        int sx = Mth.floor(pos.x), sy = Mth.floor(pos.y), sz = Mth.floor(pos.z);
        int dx = sx - lastSampleX, dy = sy - lastSampleY, dz = sz - lastSampleZ;
        if (Math.abs(t - lastSampleTick) < COLOR_SAMPLE_INTERVAL_TICKS && dx*dx+dy*dy+dz*dz <= COLOR_SAMPLE_REUSE_DISTANCE_SQR) {
            lastColorComputeTick = t;
            return;
        }

        BiomeBlend.update(level, pos);
        sampledBiomeR = BiomeBlend.fogR();
        sampledBiomeG = BiomeBlend.fogG();
        sampledBiomeB = BiomeBlend.fogB();

        BlockPos.MutableBlockPos cur = SAMPLE_CURSOR;
        float tw = 0, sky = 0, wet = 0, snow = 0;
        int skySY = sy + SKY_OPENNESS_SAMPLE_HEIGHT;
        for (int i = 0; i < SAMPLE_X.length; i++) {
            int ox = sx + SAMPLE_X[i], oz = sz + SAMPLE_Z[i];
            float w = SAMPLE_WEIGHT[i];

            cur.set(ox, level.getHeight(Heightmap.Types.MOTION_BLOCKING, ox, oz), oz);
            Biome.Precipitation fall = level.getBiome(cur).value().getPrecipitationAt(cur);
            if (fall != Biome.Precipitation.NONE) {
                wet += w;
                if (fall == Biome.Precipitation.SNOW) snow += w;
            }

            cur.set(ox, skySY, oz); sky += (level.canSeeSky(cur) ? 1f : 0f) * w; tw += w;
        }
        if (tw > 0) {
            sampledSkyOpenness  = sky / tw;
            sampledWetFraction  = wet / tw;
            sampledSnowFraction = wet > 0 ? snow / wet : 0f;
        }
        Vec3 sk = level.getSkyColor(pos, pt);
        sampledSkyR = (float) sk.x; sampledSkyG = (float) sk.y; sampledSkyB = (float) sk.z;
        float seaClr = ss(Mth.clamp(((float) pos.y - level.getSeaLevel() + 12f) / 96f, 0f, 1f));
        sampledSkyVisibility = Mth.clamp(sampledSkyOpenness * sampledSkyOpenness * (0.88f + seaClr * 0.12f), 0f, 1f);
        lastSampleTick = t; lastSampleX = sx; lastSampleY = sy; lastSampleZ = sz;
        lastColorComputeTick = t;
    }

    private static double prop(Properties p, String k, double def, double mn, double mx) {
        String v = p.getProperty(k);
        if (v == null) return Mth.clamp(def, mn, mx);
        try { return Mth.clamp(Double.parseDouble(v), mn, mx); }
        catch (Exception e) { return def; }
    }
    private static float  luma(float r, float g, float b) { return r*0.299f + g*0.587f + b*0.114f; }
    private static float  ss(float v) { return v*v*(3f-2f*v); }
    private static float  sv(float c, float t, float rate) { if (c<0||Math.abs(c-t)<COLOR_EPSILON) return t; return Mth.lerp(rate,c,t); }

    private static float[] hexRGB(int hex) {
        float[] out = HEX_BUF;
        out[0] = ((hex >> 16) & 0xFF) / 255f;
        out[1] = ((hex >> 8)  & 0xFF) / 255f;
        out[2] = ( hex        & 0xFF) / 255f;
        return out;
    }

    private static void reset() {
        currentNearPlane = currentFarPlane = currentR = currentG = currentB = -1f;
        weatherTintAmount = weatherSnowMix = rainFogMultiplier = 0f;
        currentWaterR = currentWaterG = currentWaterB = -1f;
        lastSampleTick = lastNoiseTick = lastColorComputeTick = Long.MIN_VALUE;
        BiomeBlend.reset();
    }

    private static void sat(float[] out, float r, float g, float b, float boost) {
        float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
        if (mx == mn) { out[0]=r; out[1]=g; out[2]=b; return; }
        float l=(mx+mn)*0.5f, d=mx-mn;
        float s = Mth.clamp((l>0.5f ? d/(2f-mx-mn) : d/(mx+mn)) * (1f+boost), 0f, 1f);
        if (s==0f) { out[0]=out[1]=out[2]=l; return; }
        float q = l<0.5f ? l*(1f+s) : l+s-l*s, p = 2f*l-q, h;
        if      (mx==r) h=(g-b)/d+(g<b?6f:0f);
        else if (mx==g) h=(b-r)/d+2f;
        else            h=(r-g)/d+4f;
        h/=6f;
        out[0]=h2r(p,q,h+1f/3f); out[1]=h2r(p,q,h); out[2]=h2r(p,q,h-1f/3f);
    }
    private static float h2r(float p, float q, float t) {
        if(t<0)t+=1f; if(t>1)t-=1f;
        if(t<1f/6f) return p+(q-p)*6f*t;
        if(t<0.5f)  return q;
        if(t<2f/3f) return p+(q-p)*(2f/3f-t)*6f;
        return p;
    }
}
