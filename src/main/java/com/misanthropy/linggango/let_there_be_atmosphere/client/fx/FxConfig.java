package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import net.minecraft.util.Mth;

import com.misanthropy.linggango.let_there_be_atmosphere.config.ConfigText;

import java.util.Properties;

public final class FxConfig {
    private FxConfig() {}

    public static double  sunsetFogStrength  = 0.70;
    public static boolean sunsetWide         = true;

    public static boolean weatherTintEnabled  = true;
    public static double  weatherTintStrength = 0.55;
    public static int     rainTintHex         = 0x8AA8CC;
    public static int     snowTintHex         = 0xD8E4F0;

    public static boolean lightTintEnabled   = true;
    public static int     blockLightHex      = 0xFFD8AC;
    public static double  blockLightStrength = 0.55;
    public static int     skyLightNightHex   = 0x7A7AFF;
    public static double  skyLightStrength   = 0.60;

    public static boolean cometsEnabled    = true;
    public static double  cometRate        = 0.015;
    public static double  cometMax         = 4;
    public static double  cometSpeed       = 1.0;
    public static double  cometTail        = 1.0;
    public static double  cometBrightness  = 1.0;
    public static int     cometHex         = 0xFFF2D0;
    public static int     cometPalette     = 0;
    public static double  cometHeadSize    = 1.0;
    public static double  cometAngle       = 62.0;
    public static double  cometShowerChance = 0.0006;
    public static double  cometShowerSize   = 7;

    public static boolean auroraEnabled    = true;
    public static double  auroraBrightness = 1.0;
    public static double  auroraFrequency  = 1.0;
    public static double  auroraSpeed      = 1.0;

    public static double  sunScale  = 1.0;
    public static double  moonScale = 1.0;

    public static boolean skyGlowEnabled   = true;
    public static double  sunGlowStrength  = 0.45;
    public static double  moonGlowStrength = 1.0;

    public static boolean godRays        = true;
    public static double  godRayStrength = 0.6;
    public static double  godRayLength   = 0.4;
    public static boolean lensFlare         = true;
    public static double  lensFlareStrength = 0.6;
    public static boolean waterRays         = true;
    public static double  waterRaysStrength = 1.0;
    public static boolean caustics          = false;
    public static double  causticsStrength  = 1.0;

    public static boolean rainbowsEnabled   = true;
    public static double  rainbowChance     = 0.6;
    public static double  rainbowBrightness = 1.0;

    public static boolean lightningFlash         = true;
    public static double  lightningFlashStrength = 1.0;

    public static boolean puddles      = true;
    public static boolean rainSplashes = true;
    public static double  puddleAmount = 1.0;

    public static boolean gradeEnabled          = true;
    public static double  gradeSaturation       = 1.25;
    public static double  gradeVibrance         = 0.45;
    public static double  gradeContrast         = 0.15;
    public static double  gradeWarmth           = 0.25;
    public static double  gradeVignette         = 0.15;
    public static double  gradeDarkDesaturation = 0.15;
    public static double  gradeDimVignette      = 0.2;
    public static double  gradeDimFade          = 0.15;

    public static boolean bloomEnabled   = true;
    public static double  bloomStrength  = 1.0;
    public static double  bloomThreshold = 0.85;
    public static double  bloomSpread    = 0.75;
    public static boolean ambientOcclusion         = true;
    public static double  ambientOcclusionStrength = 0.45;
    public static boolean sunShadows               = true;
    public static double  sunShadowStrength        = 0.5;

    public static boolean sunLightTint     = true;
    public static double  sunLightStrength = 0.55;
    public static int     sunLightHex      = 0xFFB775;

    public static double  cloudNightDarken = 0.25;
    public static double  cloudFogBlend    = 0.0;

    public static boolean cloudShadows        = true;
    public static double  cloudShadowStrength = 0.35;
    public static double  cloudShadowSoftness = 0.6;

    public static boolean hazeBandEnabled  = true;
    public static double  hazeBandStrength = 0.45;
    public static double  hazeBandBottom   = -20.0;
    public static double  hazeBandMiddle   = 4.0;
    public static double  hazeBandTop      = 56.0;
    public static double  hazeBandRadius   = 192.0;

    public static boolean groundMist        = true;
    public static double  groundMistDensity = 1.0;
    public static boolean swampMist         = true;
    public static double  groundMistHeight  = 16.0;

    public static boolean mistEnabled   = true;
    public static double  mistRate      = 1.0;
    public static double  mistRadius    = 16.0;
    public static double  mistPerTick   = 2;
    public static double  windStrength  = 1.0;

    public static boolean heatHaze          = true;
    public static double  heatHazeStrength  = 1.0;
    public static boolean coldAir           = true;
    public static double  coldAirStrength   = 1.0;

    public static boolean caveDust          = true;
    public static boolean caveSparkle       = true;
    public static double  caveDustAmount    = 1.0;

    public static boolean lavaLife          = true;
    public static double  lavaLifeAmount    = 1.0;

    public static boolean netherRays         = true;
    public static double  netherRaysStrength = 0.5;
    public static boolean netherMist         = true;
    public static double  netherMistDensity  = 1.0;
    public static boolean netherDust         = true;
    public static double  netherDustAmount   = 1.0;
    public static boolean netherStorms       = false;
    public static double  netherStormRate    = 1.0;
    public static boolean lavaCrust          = true;
    public static boolean netherPortalFx     = true;
    public static double  lavaCrustAmount    = 1.0;
    public static double  netherStormStrength = 1.0;
    public static boolean lavaParticles      = false;
    public static double  lavaParticlesAmount = 1.0;
    public static boolean lavaEruptions      = true;

    public static boolean groundLitter         = true;
    public static double  groundLitterAmount   = 0.5;
    public static boolean fallingLeaves        = true;
    public static double  fallingLeavesAmount  = 0.5;
    public static boolean litterSounds         = true;
    public static boolean tumbleweeds          = false;
    public static boolean windSway             = true;
    public static boolean oreGlow              = true;
    public static double  oreGlowStrength      = 1.0;
    public static boolean stemPulse            = true;
    public static boolean grassBlades         = true;
    public static boolean spreadFxTicks        = true;
    public static boolean shoreFoam            = true;
    public static double  shoreFoamStrength    = 0.6;
    public static boolean waterWake            = true;
    public static double  waterWakeStrength    = 1.0;
    public static boolean ripples              = true;
    public static double  ripplesAmount        = 1.0;
    public static boolean rainRings            = true;
    public static double  rainRingsAmount      = 1.0;
    public static boolean waterfallFoam        = true;
    public static double  waterfallFoamAmount  = 1.0;
    public static boolean waterSparkles        = false;
    public static double  waterSparklesAmount  = 1.0;
    public static boolean skyReflection        = false;
    public static double  skyReflectionStrength = 1.0;
    public static boolean waterReflections        = true;
    public static double  waterReflectionStrength = 0.8;
    public static boolean fishJumps               = true;
    public static double  fishJumpsAmount         = 1.0;
    public static boolean wetPlayer               = true;
    public static boolean fireflies            = true;
    public static double  firefliesAmount      = 1.0;
    public static boolean breathPuffs          = true;
    public static boolean footstepPuffs        = true;
    public static double  footstepPuffsAmount  = 1.0;
    public static boolean butterflies          = true;
    public static double  butterfliesAmount    = 1.0;
    public static boolean airMist              = true;
    public static double  airMistAmount        = 1.0;
    public static boolean birds                = false;
    public static double  birdsAmount          = 1.0;
    public static boolean batSwarm             = true;
    public static double  grassBladesDensity   = 1.0;
    public static int     grassBladesStyle     = 0;
    public static boolean vividSky             = true;
    public static double  fogClarity           = 0.0;
    public static boolean sunBurst             = false;
    public static boolean starSky              = false;
    public static boolean milkyWay             = true;
    public static boolean endSky               = true;
    public static boolean endDust              = true;
    public static double  endDustAmount        = 1.0;
    public static boolean endMist              = true;
    public static double  endMistAmount        = 1.0;
    public static boolean endShadows           = true;
    public static double  endShadowAmount      = 1.0;
    public static boolean portalGlow           = true;
    public static boolean endFog               = true;
    public static double  endFogDensity        = 1.0;
    public static boolean endPortalFx          = true;
    public static boolean underwaterBubbles    = true;
    public static double  underwaterBubblesAmount = 1.0;
    public static boolean deepWater            = true;
    public static double  deepWaterDarkness    = 1.0;
    public static boolean marineSnow           = true;
    public static double  marineSnowAmount     = 1.0;
    public static boolean deepSounds           = true;
    public static double  deepSoundsVolume     = 1.0;
    public static boolean deepDarkHaze         = true;
    public static double  sunBurstStrength     = 1.0;
    public static double  vividSkyStrength     = 0.85;
    public static double  windSwayStrength     = 1.0;
    public static boolean livingWind           = false;
    public static boolean windFront            = false;
    public static boolean windAltitude         = false;
    public static boolean windShelter          = false;
    public static boolean windParticles        = false;
    public static double  windParticleStrength = 1.0;
    public static int     windParticleFilter   = 0;
    public static boolean windBits             = false;
    public static double  windBitsAmount       = 1.0;
    public static boolean windStreaks          = false;
    public static boolean windSounds           = false;
    public static double  windSoundVolume      = 1.0;
    public static boolean rustleSounds         = false;

    public static boolean stormRain            = false;
    public static boolean stormRange           = false;
    public static double  stormRainAmount      = 1.0;
    public static boolean lensDrops            = false;
    public static double  lensDropsAmount      = 1.0;
    public static boolean stormClouds          = false;
    public static double  stormCloudsStrength  = 1.0;
    public static boolean stormBolts           = false;
    public static boolean wetSurfaces          = false;
    public static double  wetSurfacesStrength  = 1.0;
    public static boolean stormWind            = false;
    public static boolean blizzard             = false;
    public static double  blizzardStrength     = 1.0;
    public static boolean screenFrost          = true;
    public static boolean sandstorm            = false;
    public static double  sandstormStrength    = 1.0;

    public static boolean biomeOverlay = false;
    public static boolean debugOverlay = false;

    private static final String P = "fx.";

    public static void read(Properties p) {
        sunsetFogStrength   = num(p, "sunsetFog",     0.70, 0.0, 1.5);
        sunsetWide          = bool(p, "sunsetWide",   true);
        weatherTintEnabled  = bool(p, "weatherTint",  true);
        weatherTintStrength = num(p, "weatherTintStrength", 0.55, 0.0, 1.0);
        rainTintHex         = hex(p, "rainTintHex",   0x8AA8CC);
        snowTintHex         = hex(p, "snowTintHex",   0xD8E4F0);
        lightTintEnabled    = bool(p, "lightTint",    true);
        blockLightHex       = hex(p, "blockLightHex", 0xFFD8AC);
        blockLightStrength  = num(p, "blockLightStrength", 0.55, 0.0, 1.0);
        skyLightNightHex    = hex(p, "skyLightNightHex",   0x7A7AFF);
        skyLightStrength    = num(p, "skyLightStrength",   0.60, 0.0, 1.0);
        cometsEnabled       = bool(p, "comets",           true);
        cometRate           = num(p, "cometRate",         0.015, 0.0, 1.0);
        cometMax            = num(p, "cometMax",          4,     1.0, 16.0);
        cometSpeed          = num(p, "cometSpeed",        1.0,   0.1, 5.0);
        cometTail           = num(p, "cometTail",         1.0,   0.1, 5.0);
        cometBrightness     = num(p, "cometBrightness",   1.0,   0.0, 2.0);
        cometHex            = hex(p, "cometHex",          0xFFF2D0);
        cometPalette        = (int) num(p, "cometPalette", 0,    0.0, 4.0);
        cometHeadSize       = num(p, "cometHeadSize",     1.0,   0.2, 4.0);
        cometAngle          = num(p, "cometAngle",       62.0,   0.0, 90.0);
        cometShowerChance   = num(p, "cometShowerChance", 0.0006, 0.0, 0.05);
        cometShowerSize     = num(p, "cometShowerSize",   7,      3.0, 16.0);
        auroraEnabled       = bool(p, "aurora",           true);
        auroraBrightness    = num(p, "auroraBrightness",  1.0,   0.0, 2.0);
        auroraFrequency     = num(p, "auroraFrequency",   1.0,   0.25, 4.0);
        auroraSpeed         = num(p, "auroraSpeed",       1.0,   0.1, 4.0);
        sunScale            = num(p, "sunScale",          1.0,   0.1, 5.0);
        moonScale           = num(p, "moonScale",         1.0,   0.1, 5.0);
        skyGlowEnabled      = bool(p, "skyGlow",          true);
        sunGlowStrength     = num(p, "sunGlowStrength",   0.45,   0.0, 2.0);
        moonGlowStrength    = num(p, "moonGlowStrength",  1.0,   0.0, 2.0);
        godRays             = bool(p, "godRays",          true);
        godRayStrength      = num(p, "godRayStrength",    0.6,   0.0, 2.0);
        godRayLength        = num(p, "godRayLength",      0.4,   0.0, 1.0);
        lensFlare           = bool(p, "lensFlare",        true);
        lensFlareStrength   = num(p, "lensFlareStrength", 0.6,   0.0, 2.0);
        waterRays           = bool(p, "waterRays",        true);
        waterRaysStrength   = num(p, "waterRaysStrength", 1.0,   0.0, 2.0);
        caustics            = bool(p, "caustics",         false);
        causticsStrength    = num(p, "causticsStrength",  1.0,   0.0, 2.0);
        rainbowsEnabled     = bool(p, "rainbows",         true);
        rainbowChance       = num(p, "rainbowChance",     0.6,   0.0, 1.0);
        rainbowBrightness   = num(p, "rainbowBrightness", 1.0,   0.0, 2.0);
        lightningFlash      = bool(p, "lightningFlash",   true);
        lightningFlashStrength = num(p, "lightningFlashStrength", 1.0, 0.0, 2.0);
        puddles             = bool(p, "puddles",          true);
        rainSplashes        = bool(p, "rainSplashes",     true);
        puddleAmount        = num(p, "puddleAmount",      1.0,   0.0, 2.0);
        gradeEnabled        = bool(p, "grade",            true);
        gradeSaturation     = num(p, "gradeSaturation",   1.25,   0.0, 2.0);
        gradeVibrance       = num(p, "gradeVibrance",     0.45,   0.0, 1.0);
        gradeContrast       = num(p, "gradeContrast",     0.15,  0.0, 1.0);
        gradeWarmth         = num(p, "gradeWarmth",       0.25, -1.0, 1.0);
        gradeVignette       = num(p, "gradeVignette",     0.15,  0.0, 1.0);
        gradeDarkDesaturation = num(p, "gradeDarkDesaturation", 0.15, 0.0, 1.0);
        gradeDimVignette    = num(p, "gradeDimVignette",  0.2,   0.0, 1.0);
        gradeDimFade        = num(p, "gradeDimFade",      0.15,   0.0, 1.0);
        bloomEnabled        = bool(p, "bloom",            true);
        bloomStrength       = num(p, "bloomStrength",     1.0,   0.0, 2.0);
        bloomThreshold      = num(p, "bloomThreshold",    0.85,  0.0, 1.0);
        bloomSpread         = num(p, "bloomSpread",       0.75,  0.0, 0.9);
        ambientOcclusion    = bool(p, "ambientOcclusion", true);
        ambientOcclusionStrength = num(p, "ambientOcclusionStrength", 0.45, 0.0, 1.5);
        sunShadows          = bool(p, "sunShadows",       true);
        sunShadowStrength   = num(p, "sunShadowStrength", 0.5,   0.0, 1.5);
        sunLightTint        = bool(p, "sunLightTint",     true);
        sunLightStrength    = num(p, "sunLightStrength",  0.55,  0.0, 1.0);
        sunLightHex         = hex(p, "sunLightHex",       0xFFB775);
        cloudNightDarken    = num(p, "cloudNightDarken",  0.25,  0.0, 1.0);
        cloudFogBlend       = num(p, "cloudFogBlend",     0.0,   0.0, 1.0);
        cloudShadows        = bool(p, "cloudShadows",     true);
        cloudShadowStrength = num(p, "cloudShadowStrength", 0.35, 0.0, 1.0);
        cloudShadowSoftness = num(p, "cloudShadowSoftness", 0.6,  0.02, 1.0);
        hazeBandEnabled     = bool(p, "hazeBand",         true);
        hazeBandStrength    = num(p, "hazeBandStrength",  0.45,   0.0, 2.0);
        hazeBandBottom      = num(p, "hazeBandBottom",   -20.0, -64.0, 32.0);
        hazeBandMiddle      = num(p, "hazeBandMiddle",     4.0, -48.0, 48.0);
        hazeBandTop         = num(p, "hazeBandTop",      56.0, -32.0, 192.0);
        hazeBandRadius      = num(p, "hazeBandRadius",   192.0,  48.0, 512.0);
        groundMist          = bool(p, "groundMist",       true);
        groundMistDensity   = num(p, "groundMistDensity", 1.0,   0.0, 3.0);
        swampMist           = bool(p, "swampMist",        true);
        groundMistHeight    = num(p, "groundMistHeight",  16.0,  2.0, 40.0);
        mistEnabled         = bool(p, "mist",             true);
        mistRate            = num(p, "mistRate",          1.0,   0.0, 4.0);
        mistRadius          = num(p, "mistRadius",       16.0,   4.0, 48.0);
        mistPerTick         = num(p, "mistPerTick",       2,     0.0, 32.0);
        windStrength        = num(p, "windStrength",      1.0,   0.0, 4.0);
        heatHaze            = bool(p, "heatHaze",         true);
        heatHazeStrength    = num(p, "heatHazeStrength",  1.0,   0.0, 2.0);
        coldAir             = bool(p, "coldAir",          true);
        coldAirStrength     = num(p, "coldAirStrength",   1.0,   0.0, 2.0);
        caveDust            = bool(p, "caveDust",         true);
        caveSparkle         = bool(p, "caveSparkle",      true);
        caveDustAmount      = num(p, "caveDustAmount",    1.0,   0.0, 3.0);
        lavaLife            = bool(p, "lavaLife",         true);
        lavaLifeAmount      = num(p, "lavaLifeAmount",    1.0,   0.0, 3.0);
        netherRays          = bool(p, "netherRays",       true);
        netherRaysStrength  = num(p, "netherRaysStrength", 0.5,  0.0, 2.0);
        netherMist          = bool(p, "netherMist",       true);
        netherMistDensity   = num(p, "netherMistDensity", 1.0,   0.0, 2.0);
        netherDust          = bool(p, "netherDust",       true);
        netherDustAmount    = num(p, "netherDustAmount",  1.0,   0.0, 3.0);
        netherStorms        = bool(p, "netherStorms",     false);
        netherStormRate     = num(p, "netherStormRate",   1.0,   0.0, 3.0);
        lavaCrust           = bool(p, "lavaCrust",        true);
        netherPortalFx      = bool(p, "netherPortalFx",   true);
        lavaCrustAmount     = num(p, "lavaCrustAmount",   1.0,   0.0, 2.0);
        netherStormStrength = num(p, "netherStormStrength", 1.0, 0.3, 2.0);
        lavaParticles       = bool(p, "lavaParticles",    false);
        lavaParticlesAmount = num(p, "lavaParticlesAmount", 1.0, 0.0, 2.0);
        lavaEruptions       = bool(p, "lavaEruptions",    true);
        groundLitter        = bool(p, "groundLitter",     true);
        groundLitterAmount  = num(p, "groundLitterAmount", 0.5,  0.0, 2.0);
        fallingLeaves       = bool(p, "fallingLeaves",    true);
        fallingLeavesAmount = num(p, "fallingLeavesAmount", 0.5, 0.0, 3.0);
        litterSounds        = bool(p, "litterSounds",     true);
        tumbleweeds         = bool(p, "tumbleweeds",      false);
        windSway            = bool(p, "windSway",         true);
        oreGlow             = bool(p, "oreGlow",          true);
        oreGlowStrength     = num(p, "oreGlowStrength",   1.0,   0.0, 2.0);
        stemPulse           = bool(p, "stemPulse",        true);
        grassBlades         = bool(p, "grassBlades",      true);
        spreadFxTicks       = bool(p, "spreadFxTicks",    true);
        shoreFoam           = bool(p, "shoreFoam",        true);
        shoreFoamStrength   = num(p, "shoreFoamStrength", 0.6,   0.0, 2.0);
        waterWake           = bool(p, "waterWake",        true);
        waterWakeStrength   = num(p, "waterWakeStrength", 1.0,   0.0, 2.0);
        ripples             = bool(p, "ripples",          true);
        ripplesAmount       = num(p, "ripplesAmount",     1.0,   0.0, 3.0);
        rainRings           = bool(p, "rainRings",        true);
        rainRingsAmount     = num(p, "rainRingsAmount",   1.0,   0.0, 3.0);
        waterfallFoam       = bool(p, "waterfallFoam",    true);
        waterfallFoamAmount = num(p, "waterfallFoamAmount", 1.0, 0.0, 2.0);
        waterSparkles       = bool(p, "waterSparkles",    false);
        waterSparklesAmount = num(p, "waterSparklesAmount", 1.0, 0.0, 3.0);
        skyReflection       = bool(p, "skyReflection",    false);
        skyReflectionStrength = num(p, "skyReflectionStrength", 1.0, 0.0, 2.0);
        waterReflections    = bool(p, "waterReflections", true);
        waterReflectionStrength = num(p, "waterReflectionStrength", 0.8, 0.0, 2.0);
        fishJumps           = bool(p, "fishJumps",        true);
        fishJumpsAmount     = num(p, "fishJumpsAmount",   1.0,   0.0, 3.0);
        wetPlayer           = bool(p, "wetPlayer",        true);
        fireflies           = bool(p, "fireflies",        true);
        firefliesAmount     = num(p, "firefliesAmount",   1.0,   0.0, 3.0);
        breathPuffs         = bool(p, "breathPuffs",      true);
        footstepPuffs       = bool(p, "footstepPuffs",    true);
        footstepPuffsAmount = num(p, "footstepPuffsAmount", 1.0, 0.0, 2.0);
        butterflies         = bool(p, "butterflies",      true);
        butterfliesAmount   = num(p, "butterfliesAmount", 1.0,   0.0, 3.0);
        airMist             = bool(p, "airMist",          true);
        airMistAmount       = num(p, "airMistAmount",     1.0,   0.0, 2.0);
        birds               = bool(p, "birds",            false);
        birdsAmount         = num(p, "birdsAmount",       1.0,   0.0, 3.0);
        batSwarm            = bool(p, "batSwarm",         true);
        grassBladesDensity  = num(p, "grassBladesDensity", 1.0,  0.0, 1.0);
        grassBladesStyle    = (int) num(p, "grassBladesStyle", 0,  0.0, 1.0);
        vividSky            = bool(p, "vividSky",         true);
        fogClarity          = num(p, "fogClarity",        0.0,   0.0, 1.0);
        sunBurst            = bool(p, "sunBurst",         false);
        starSky             = bool(p, "starSky",          false);
        milkyWay            = bool(p, "milkyWay",         true);
        endSky              = bool(p, "endSky",           true);
        endDust             = bool(p, "endDust",          true);
        endDustAmount       = num(p, "endDustAmount",     1.0,   0.0, 3.0);
        endMist             = bool(p, "endMist",          true);
        endMistAmount       = num(p, "endMistAmount",     1.0,   0.0, 2.0);
        endShadows          = bool(p, "endShadows",       true);
        endShadowAmount     = num(p, "endShadowAmount",   1.0,   0.0, 2.0);
        portalGlow          = bool(p, "portalGlow",       true);
        endFog              = bool(p, "endFog",           true);
        endFogDensity       = num(p, "endFogDensity",     1.0,   0.3, 2.0);
        endPortalFx         = bool(p, "endPortalFx",      true);
        underwaterBubbles   = bool(p, "underwaterBubbles", true);
        underwaterBubblesAmount = num(p, "underwaterBubblesAmount", 1.0, 0.0, 3.0);
        deepWater           = bool(p, "deepWater",        true);
        deepWaterDarkness   = num(p, "deepWaterDarkness", 1.0,   0.0, 1.5);
        marineSnow          = bool(p, "marineSnow",       true);
        marineSnowAmount    = num(p, "marineSnowAmount",  1.0,   0.0, 3.0);
        deepSounds          = bool(p, "deepSounds",       true);
        deepSoundsVolume    = num(p, "deepSoundsVolume",  1.0,   0.0, 2.0);
        deepDarkHaze        = bool(p, "deepDarkHaze",     true);
        sunBurstStrength    = num(p, "sunBurstStrength",  1.0,   0.0, 2.0);
        vividSkyStrength    = num(p, "vividSkyStrength",  0.85,  0.0, 1.0);
        windSwayStrength    = num(p, "windSwayStrength",  1.0,   0.0, 2.0);
        livingWind          = bool(p, "livingWind",       false);
        windFront           = bool(p, "windFront",        false);
        windAltitude        = bool(p, "windAltitude",     false);
        windShelter         = bool(p, "windShelter",      false);
        windParticles       = bool(p, "windParticles",    false);
        windParticleStrength = num(p, "windParticleStrength", 1.0, 0.0, 3.0);
        windParticleFilter  = (int) num(p, "windParticleFilter", 0, 0.0, 2.0);
        windBits            = bool(p, "windBits",         false);
        windBitsAmount      = num(p, "windBitsAmount",    1.0,   0.0, 3.0);
        windStreaks         = bool(p, "windStreaks",      false);
        windSounds          = bool(p, "windSounds",       false);
        windSoundVolume     = num(p, "windSoundVolume",   1.0,   0.0, 2.0);
        rustleSounds        = bool(p, "rustleSounds",     false);
        stormRain           = bool(p, "stormRain",        false);
        stormRange          = bool(p, "stormRange",       false);
        stormRainAmount     = num(p, "stormRainAmount",   1.0,   0.0, 2.0);
        lensDrops           = bool(p, "lensDrops",        false);
        lensDropsAmount     = num(p, "lensDropsAmount",   1.0,   0.0, 2.0);
        stormClouds         = bool(p, "stormClouds",      false);
        stormCloudsStrength = num(p, "stormCloudsStrength", 1.0, 0.0, 1.0);
        stormBolts          = bool(p, "stormBolts",       false);
        wetSurfaces         = bool(p, "wetSurfaces",      false);
        wetSurfacesStrength = num(p, "wetSurfacesStrength", 1.0, 0.0, 2.0);
        stormWind           = bool(p, "stormWind",        false);
        blizzard            = bool(p, "blizzard",         false);
        blizzardStrength    = num(p, "blizzardStrength",  1.0,   0.0, 2.0);
        screenFrost         = bool(p, "screenFrost",      true);
        sandstorm           = bool(p, "sandstorm",        false);
        sandstormStrength   = num(p, "sandstormStrength", 1.0,   0.0, 2.0);
        biomeOverlay        = bool(p, "biomeOverlay",     false);
        debugOverlay        = bool(p, "debugOverlay",     false);
    }

    public static void write(ConfigText out) {
        out.section("Sunset");
        out.note("Warms the fog while you face the rising or setting sun.");
        out.put(P + "sunsetFog", sunsetFogStrength);
        out.note("On gives the long, red-walking window newer Minecraft versions use.",
                 "Off gives the narrow 1.20 one.");
        out.put(P + "sunsetWide", sunsetWide);

        out.section("Weather tint");
        out.note("Casts the screen cold while weather is falling on you. Only shows",
                 "where something actually falls, so a desert stays clear in a storm.");
        out.put(P + "weatherTint", weatherTintEnabled);
        out.note("How heavy the cast gets in a downpour.");
        out.put(P + "weatherTintStrength", weatherTintStrength);
        out.note("Colour for rain, then for snow.");
        out.putHex(P + "rainTintHex", rainTintHex);
        out.putHex(P + "snowTintHex", snowTintHex);

        out.section("Coloured light");
        out.note("Warm light from torches and lava, cold light from the sky after dark.");
        out.put(P + "lightTint", lightTintEnabled);
        out.note("Colour of block light, and how far to push it.");
        out.putHex(P + "blockLightHex", blockLightHex);
        out.put(P + "blockLightStrength", blockLightStrength);
        out.note("Colour of night sky light, and how far to push it.");
        out.putHex(P + "skyLightNightHex", skyLightNightHex);
        out.put(P + "skyLightStrength", skyLightStrength);
        out.note("Warm cast on sky light while the sun sits on the horizon.");
        out.put(P + "sunLightTint", sunLightTint);
        out.putHex(P + "sunLightHex", sunLightHex);
        out.put(P + "sunLightStrength", sunLightStrength);

        out.section("Comets");
        out.note("Streaks crossing the night sky. They only show where the stars do.");
        out.put(P + "comets", cometsEnabled);
        out.note("Chance per tick that a new one starts, and how many may fly at once.");
        out.put(P + "cometRate", cometRate);
        out.put(P + "cometMax", cometMax);
        out.note("Flight speed, tail length and brightness.");
        out.put(P + "cometSpeed", cometSpeed);
        out.put(P + "cometTail", cometTail);
        out.put(P + "cometBrightness", cometBrightness);
        out.note("Colour scheme: 0 uses the colour below, 1 warm, 2 cool, 3 white,",
                 "4 gives every comet one of the three at random.");
        out.put(P + "cometPalette", cometPalette);
        out.note("Head colour, used when the scheme is 0.");
        out.putHex(P + "cometHex", cometHex);
        out.note("Size of the burning head, and how high above the horizon they start.");
        out.put(P + "cometHeadSize", cometHeadSize);
        out.put(P + "cometAngle", cometAngle);
        out.note("Chance per tick that a whole shower opens instead of a single streak,",
                 "and how many fall from one radiant when it does.");
        out.put(P + "cometShowerChance", cometShowerChance);
        out.put(P + "cometShowerSize", cometShowerSize);

        out.section("Aurora");
        out.note("Curtains of light over cold biomes on clear nights. The colder the",
                 "biome, the higher they climb; in snow they can circle right overhead.");
        out.put(P + "aurora", auroraEnabled);
        out.note("How bright they glow, and how often a night is bright enough to show",
                 "them. 1 is how the Auroras mod tuned both.");
        out.put(P + "auroraBrightness", auroraBrightness);
        out.put(P + "auroraFrequency", auroraFrequency);
        out.note("How fast the curtains ripple and drift.");
        out.put(P + "auroraSpeed", auroraSpeed);

        out.section("Sun and moon");
        out.note("Size of each, as a multiple of what Minecraft draws.");
        out.put(P + "sunScale", sunScale);
        out.put(P + "moonScale", moonScale);
        out.note("A soft glow around each. The sun glow warms toward the sunset colour as",
                 "the sun drops and fades under cloud; the moon glow follows the moon phase.");
        out.put(P + "skyGlow", skyGlowEnabled);
        out.put(P + "sunGlowStrength", sunGlowStrength);
        out.put(P + "moonGlowStrength", moonGlowStrength);

        out.section("God rays");
        out.note("Shafts of sunlight breaking through clouds, leaves and ridges when you",
                 "look toward the sun. Strength is how bright, length how far they reach.");
        out.put(P + "godRays", godRays);
        out.put(P + "godRayStrength", godRayStrength);
        out.put(P + "godRayLength", godRayLength);
        out.note("Soft coloured rings across the screen when you look near the sun. Hidden when the sun is.");
        out.put(P + "lensFlare", lensFlare);
        out.put(P + "lensFlareStrength", lensFlareStrength);
        out.put(P + "waterRays", waterRays);
        out.put(P + "waterRaysStrength", waterRaysStrength);
        out.note("Rippling sunlight patterns on the seabed, plus fine motes drifting in the water.");
        out.put(P + "caustics", caustics);
        out.put(P + "causticsStrength", causticsStrength);

        out.section("Rainbows");
        out.note("A rainbow can open opposite the sun as rain clears in daylight, with a",
                 "faint second bow outside it. Chance per shower, and how bright it shines.");
        out.put(P + "rainbows", rainbowsEnabled);
        out.put(P + "rainbowChance", rainbowChance);
        out.put(P + "rainbowBrightness", rainbowBrightness);

        out.section("Lightning flash");
        out.note("Each strike flickers the sky, clouds, fog and daylight a few times,",
                 "brightest toward the bolt. Off whenever Hide Lightning Flashes is on.");
        out.put(P + "lightningFlash", lightningFlash);
        out.put(P + "lightningFlashStrength", lightningFlashStrength);

        out.section("Puddles and splashes");
        out.note("Rain gathers into reflective puddles on open ground, with ripples while",
                 "it pours, drying out a few minutes after. Splashes burst where drops land.");
        out.put(P + "puddles", puddles);
        out.put(P + "rainSplashes", rainSplashes);
        out.put(P + "puddleAmount", puddleAmount);

        out.section("Colour grade");
        out.note("One gentle pass over the finished picture. Skipped while a shader pack runs.");
        out.put(P + "grade", gradeEnabled);
        out.note("Saturation, where 1 leaves colour alone, and how much soft contrast to add.");
        out.put(P + "gradeSaturation", gradeSaturation);
        out.note("Vibrance lifts dull colours and leaves already vivid ones alone.");
        out.put(P + "gradeVibrance", gradeVibrance);
        out.put(P + "gradeContrast", gradeContrast);
        out.note("Warm above 0, cool below.");
        out.put(P + "gradeWarmth", gradeWarmth);
        out.note("How dark the screen edges get, and how much colour drains from dark areas while you stand in the dark.");
        out.put(P + "gradeVignette", gradeVignette);
        out.put(P + "gradeDarkDesaturation", gradeDarkDesaturation);
        out.note("While you stand somewhere dark (caves, night, no torch nearby), extra vignette",
                 "and colour loss over the whole picture. A light within a few blocks undoes it.");
        out.put(P + "gradeDimVignette", gradeDimVignette);
        out.put(P + "gradeDimFade", gradeDimFade);

        out.section("Glow");
        out.note("Bright parts of the picture bleed soft light over their surroundings.",
                 "Skipped while a shader pack runs.");
        out.put(P + "bloom", bloomEnabled);
        out.note("How much light spills, how bright a pixel must be to spill any (0 to 1),",
                 "and how far it reaches (0 tight, 0.9 a wide haze).");
        out.put(P + "bloomStrength", bloomStrength);
        out.put(P + "bloomThreshold", bloomThreshold);
        out.put(P + "bloomSpread", bloomSpread);

        out.section("Ambient occlusion");
        out.note("Soft shade in corners, under overhangs and where things touch the ground.");
        out.put(P + "ambientOcclusion", ambientOcclusion);
        out.put(P + "ambientOcclusionStrength", ambientOcclusionStrength);
        out.note("Things block the sun near you and cast soft shadows on the ground and walls.");
        out.put(P + "sunShadows", sunShadows);
        out.put(P + "sunShadowStrength", sunShadowStrength);

        out.section("Cloud colour");
        out.note("How far the clouds dim after dark, and how much of the fog colour",
                 "bleeds into them so they sit in the same air as everything else.");
        out.put(P + "cloudNightDarken", cloudNightDarken);
        out.put(P + "cloudFogBlend", cloudFogBlend);

        out.section("Cloud shadows");
        out.note("Shadows of the clouds overhead sliding across the ground in daylight,",
                 "cast along the sun so they stretch out in the morning and evening.");
        out.put(P + "cloudShadows", cloudShadows);
        out.put(P + "cloudShadowStrength", cloudShadowStrength);
        out.put(P + "cloudShadowSoftness", cloudShadowSoftness);

        out.section("Haze band");
        out.note("A band of haze standing in the world along the horizon. Replaces the",
                 "flat screen band above, which also tinted your hand and the HUD.");
        out.put(P + "hazeBand", hazeBandEnabled);
        out.put(P + "hazeBandStrength", hazeBandStrength);
        out.note("Where the band sits, in blocks above or below sea level, and how far",
                 "out it stands.");
        out.put(P + "hazeBandBottom", hazeBandBottom);
        out.put(P + "hazeBandMiddle", hazeBandMiddle);
        out.put(P + "hazeBandTop", hazeBandTop);
        out.put(P + "hazeBandRadius", hazeBandRadius);

        out.section("Ground mist");
        out.note("Mist lying in the low ground around you, light at dawn and after rain, thick and low",
                 "in swamps when swampMist is on. Height is how far above the valley floor it reaches.");
        out.put(P + "groundMist", groundMist);
        out.put(P + "groundMistDensity", groundMistDensity);
        out.put(P + "swampMist", swampMist);
        out.put(P + "groundMistHeight", groundMistHeight);

        out.section("Air");
        out.note("Drifting motes in biomes that ask for them. Each biome names a vanilla",
                 "particle in biomes.json, so nothing new is added to the game.");
        out.put(P + "mist", mistEnabled);
        out.note("Global rate multiplier, how far out they spawn, and the per-tick cap.");
        out.put(P + "mistRate", mistRate);
        out.put(P + "mistRadius", mistRadius);
        out.put(P + "mistPerTick", mistPerTick);
        out.note("How hard the wind pushes them along.");
        out.put(P + "windStrength", windStrength);

        out.section("Heat haze and cold air");
        out.note("Hot places shimmer: deserts and badlands in clear daylight ripple in",
                 "the distance, the air near lava wavers, and the Nether does both a little.",
                 "Cold places bend light at the edges of the screen, more while it snows.");
        out.put(P + "heatHaze", heatHaze);
        out.put(P + "heatHazeStrength", heatHazeStrength);
        out.put(P + "coldAir", coldAir);
        out.put(P + "coldAirStrength", coldAirStrength);

        out.section("Cave dust and sparkle");
        out.note("Underground, dust drifts through torchlit air, amethyst glints and sculk",
                 "breathes out faint teal motes. Amount scales all three.");
        out.put(P + "caveDust", caveDust);
        out.put(P + "caveSparkle", caveSparkle);
        out.put(P + "caveDustAmount", caveDustAmount);

        out.section("Lava life");
        out.note("Open lava throws embers and ash, now and then pops and spits droplets,",
                 "and steams where it meets water.");
        out.put(P + "lavaLife", lavaLife);
        out.put(P + "lavaLifeAmount", lavaLifeAmount);

        out.section("Nether");
        out.note("Soft light shafts hang in the Nether fog, tinted by the biome.");
        out.put(P + "netherRays", netherRays);
        out.put(P + "netherRaysStrength", netherRaysStrength);
        out.note("A glowing haze lies low over the lava seas.");
        out.put(P + "netherMist", netherMist);
        out.put(P + "netherMistDensity", netherMistDensity);
        out.note("Glowing embers float up and hot ash drifts down, coloured by the biome.");
        out.put(P + "netherDust", netherDust);
        out.put(P + "netherDustAmount", netherDustAmount);
        out.note("Now and then a fire-dust storm sweeps through: a thick glowing haze, streaking embers",
                 "and a howling wind. Rate 0 means never, 3 means often.");
        out.put(P + "netherStorms", netherStorms);
        out.put(P + "netherStormRate", netherStormRate);
        out.note("Lava gets glowing crust plates that drift with the flow, break up and re-form between bright breathing cracks.",
                 "Off keeps the vanilla lava texture.");
        out.put(P + "lavaCrust", lavaCrust);
        out.put(P + "lavaCrustAmount", lavaCrustAmount);
        out.note("How hard the Nether storm hits: fog closeness and how much dust it carries.");
        out.put(P + "netherStormStrength", netherStormStrength);
        out.note("Hand-drawn lava pops, splats, orbs, ripples and wisps replace the vanilla lava particles.");
        out.put(P + "lavaParticles", lavaParticles);
        out.put(P + "lavaParticlesAmount", lavaParticlesAmount);
        out.note("Now and then a lava pool near you bursts and throws glowing blobs that arc, trail embers and splat back.");
        out.put(P + "lavaEruptions", lavaEruptions);
        out.note("Lit Nether portals glow violet and swirl like a pixel whirlpool, with motes spiralling in. Lighting one flashes and bursts, breaking one sheds falling shards. Vanilla portal specks near them are replaced.");
        out.put(P + "netherPortalFx", netherPortalFx);
        out.note("Twigs, pebbles, petals, clover and tiny flowers on the ground, and leaves drifting down from trees.");
        out.put(P + "groundLitter", groundLitter);
        out.put(P + "groundLitterAmount", groundLitterAmount);
        out.put(P + "fallingLeaves", fallingLeaves);
        out.put(P + "fallingLeavesAmount", fallingLeavesAmount);
        out.note("Kicked sticks, pebbles and leaves make a very faint, re-pitched rustle or clack.");
        out.put(P + "litterSounds", litterSounds);
        out.note("Tumbleweeds roll across dry, warm land with the wind, and by the dozen in sandstorms.");
        out.put(P + "tumbleweeds", tumbleweeds);
        out.note("Grass, flowers, crops and leaves move with the wind. Needs Embeddium; skipped under a shader pack.");
        out.put(P + "windSway", windSway);
        out.note("Coloured ore specks glow softly in the dark. Needs Embeddium; toggling rebuilds the chunks.");
        out.put(P + "oreGlow", oreGlow);
        out.put(P + "oreGlowStrength", oreGlowStrength);
        out.note("Veins on crimson and warped stems, wart blocks and shroomlight pulse slowly like breathing. Needs Embeddium.");
        out.put(P + "stemPulse", stemPulse);
        out.note("Thin grass blades on grass blocks near you, swaying in the wind and bending as you walk through.");
        out.put(P + "grassBlades", grassBlades);
        out.note("Grass blade look: 0 short tufts, 1 tall meadow ribbons.");
        out.put(P + "grassBladesStyle", grassBladesStyle);
        out.note("Turquoise shallows and lapping foam where water meets the shore.");
        out.note("Spread the effect updates (shore foam, puddles, litter, lava, birds and the rest) over the frames between game ticks instead of running them all in the tick frame. Same work, smoother frame times.");
        out.put(P + "spreadFxTicks", spreadFxTicks);
        out.put(P + "shoreFoam", shoreFoam);
        out.put(P + "shoreFoamStrength", shoreFoamStrength);
        out.note("Foam rings trail behind swimmers, mobs and boats moving across water.");
        out.put(P + "waterWake", waterWake);
        out.put(P + "waterWakeStrength", waterWakeStrength);
        out.note("Rings spread on the water where rain lands and where things splash, in blocky pixel steps.");
        out.put(P + "ripples", ripples);
        out.put(P + "ripplesAmount", ripplesAmount);
        out.note("Small pixel rings pop up where raindrops land on open water, across all the water you can see. Needs Embeddium.");
        out.put(P + "rainRings", rainRings);
        out.put(P + "rainRingsAmount", rainRingsAmount);
        out.note("Waterfalls churn white foam where they land, throw spray and drift soft mist.");
        out.put(P + "waterfallFoam", waterfallFoam);
        out.put(P + "waterfallFoamAmount", waterfallFoamAmount);
        out.note("Pixel sparkles twinkle on moonlit water at night, thickest on the path toward the moon.");
        out.put(P + "waterSparkles", waterSparkles);
        out.put(P + "waterSparklesAmount", waterSparklesAmount);
        out.note("Water picks up the sky's colour, more strongly the flatter you look across it.");
        out.put(P + "skyReflection", skyReflection);
        out.put(P + "skyReflectionStrength", skyReflectionStrength);
        out.note("Calm water mirrors the trees, hills and sky around it.");
        out.put(P + "waterReflections", waterReflections);
        out.put(P + "waterReflectionStrength", waterReflectionStrength);
        out.note("Fish now and then leap out of lakes and oceans with a splash.");
        out.put(P + "fishJumps", fishJumps);
        out.put(P + "fishJumpsAmount", fishJumpsAmount);
        out.note("Climbing out of water leaves you dripping for a few seconds: drops run down your screen",
                 "in first person and fall off your body in third person and on other players.");
        out.put(P + "wetPlayer", wetPlayer);
        out.note("Blinking fireflies drift over grass on warm, clear nights.");
        out.put(P + "fireflies", fireflies);
        out.put(P + "firefliesAmount", firefliesAmount);
        out.note("Breath clouds in the cold, and dust, snow or splashes kicked up by footsteps.");
        out.put(P + "breathPuffs", breathPuffs);
        out.put(P + "footstepPuffs", footstepPuffs);
        out.put(P + "footstepPuffsAmount", footstepPuffsAmount);
        out.note("Butterflies flutter around flowers on clear days.");
        out.put(P + "butterflies", butterflies);
        out.put(P + "butterfliesAmount", butterfliesAmount);
        out.note("Faint pastel wisps of mist float in the open air around you and drift with the wind.");
        out.put(P + "airMist", airMist);
        out.put(P + "airMistAmount", airMistAmount);
        out.note("Flocks of birds cross the sky by day, and songbirds burst out of trees as you walk past.");
        out.put(P + "birds", birds);
        out.put(P + "birdsAmount", birdsAmount);
        out.note("Now and then at dusk a cave mouth near you pours out a swarm of bats that scatter into the sky.");
        out.put(P + "batSwarm", batSwarm);
        out.put(P + "grassBladesDensity", grassBladesDensity);
        out.note("A bold sky that follows the sun: deep blue over a cyan horizon by day, magenta over orange at sunrise and sunset.");
        out.put(P + "vividSky", vividSky);
        out.note("Clear air: pushes daytime fog out to the edge of your render distance. Caves and rain keep their fog.");
        out.put(P + "fogClarity", fogClarity);
        out.note("A pixel starburst around the sun.");
        out.put(P + "sunBurst", sunBurst);
        out.put(P + "sunBurstStrength", sunBurstStrength);
        out.note("Pixel star sky: crisp twinkling stars in several sizes and colours, a soft moon halo,",
                 "and an optional Milky Way band. Replaces the plain vanilla star dots.");
        out.put(P + "starSky", starSky);
        out.put(P + "milkyWay", milkyWay);
        out.note("End sky: a drifting violet nebula full of twinkling stars replaces the flat End sky,",
                 "and the End's fog takes on its colour.");
        out.put(P + "endSky", endSky);
        out.note("Glowing motes drifting through the air in the End.");
        out.put(P + "endDust", endDust);
        out.put(P + "endDustAmount", endDustAmount);
        out.put(P + "endMist", endMist);
        out.put(P + "endMistAmount", endMistAmount);
        out.put(P + "endShadows", endShadows);
        out.put(P + "endShadowAmount", endShadowAmount);
        out.note("End portals: light rising from an open portal, fireflies round the frame,",
                 "and a flash and burst of sparks when an eye goes in.");
        out.put(P + "portalGlow", portalGlow);
        out.note("End fog: a lilac haze that thickens with distance, so far islands fade into purple.",
                 "Works in every End biome, tinted by each biome's own fog colour.");
        out.put(P + "endFog", endFog);
        out.put(P + "endFogDensity", endFogDensity);
        out.note("End portal air: soft star motes rise over an open portal inside a faint teal-violet light column,",
                 "and a small sparkle goes up when an eye is placed.");
        out.put(P + "endPortalFx", endPortalFx);
        out.note("Underwater bubbles: streams of bubbles rise from the seabed, kelp, seagrass, magma and soul sand,",
                 "wobbling as they climb and popping at the surface.");
        out.put(P + "underwaterBubbles", underwaterBubbles);
        out.put(P + "underwaterBubblesAmount", underwaterBubblesAmount);
        out.note("Deep water: the further below the surface you are, the darker and closer the water fog gets,",
                 "and sun rays, caustics and silt fade out by about 24 blocks down. Near the surface nothing changes.");
        out.put(P + "deepWater", deepWater);
        out.put(P + "deepWaterDarkness", deepWaterDarkness);
        out.note("Marine snow: slow pale specks sinking through the water around you, more of them the deeper you go.");
        out.put(P + "marineSnow", marineSnow);
        out.put(P + "marineSnowAmount", marineSnowAmount);
        out.note("Deep sounds: every minute or two, a low distant groan or rumble when you are more than 20 blocks under water.");
        out.put(P + "deepSounds", deepSounds);
        out.put(P + "deepSoundsVolume", deepSoundsVolume);
        out.note("Deep dark haze: a cold dark-teal fog in the deep dark and near sculk, with slow teal motes,",
                 "and a gentle pulse when a sculk sensor or shrieker goes off nearby.");
        out.put(P + "deepDarkHaze", deepDarkHaze);
        out.put(P + "vividSkyStrength", vividSkyStrength);
        out.put(P + "windSwayStrength", windSwayStrength);

        out.section("Living wind");
        out.note("Living wind: calm, breezy and windy days, gust clusters and a rising wind before a storm.",
                 "Everything that blows follows it: plants, leaves, litter, rain slant, mist.");
        out.put(P + "livingWind", livingWind);
        out.note("Pre-storm front: in single player the wind builds before rain arrives.");
        out.put(P + "windFront", windFront);
        out.note("Altitude: wind is stronger the higher you climb.");
        out.put(P + "windAltitude", windAltitude);
        out.note("Shelter: plants and sounds feel less wind indoors, in caves and in the lee of walls.");
        out.put(P + "windShelter", windShelter);
        out.note("Particle wind: smoke, dust, ash and spores from vanilla get pushed along.",
                 "Filter 0 is smoke and dust only, 1 adds most others, 2 is everything dry.");
        out.put(P + "windParticles", windParticles);
        out.put(P + "windParticleStrength", windParticleStrength);
        out.put(P + "windParticleFilter", windParticleFilter);
        out.note("Wind bits: tiny flecks, leaf and grass scraps torn from foliage in strong wind.");
        out.put(P + "windBits", windBits);
        out.put(P + "windBitsAmount", windBitsAmount);
        out.note("Wind streaks: faint lines that sweep past during gusts.");
        out.put(P + "windStreaks", windStreaks);
        out.note("Wind sounds: a soft re-pitched vanilla wind bed that follows strength, place and shelter.");
        out.put(P + "windSounds", windSounds);
        out.put(P + "windSoundVolume", windSoundVolume);
        out.note("Rustle: walking through plants and leaves makes a soft rustle.");
        out.put(P + "rustleSounds", rustleSounds);

        out.section("Storms");
        out.note("Custom rain: angled streaks that stop at roofs, drip through leaves and splash",
                 "where they land. Replaces the vanilla rain sheets; snow is left alone.");
        out.put(P + "stormRain", stormRain);
        out.note("Distant storms: heavy rain, sandstorms and blizzards also fill the middle and far distance",
                 "with fewer, larger, softer particles and drifting cards, and the storm fog reaches further.");
        out.put(P + "stormRange", stormRange);
        out.put(P + "stormRainAmount", stormRainAmount);
        out.note("Raindrops land on the screen when you look up into the rain.");
        out.put(P + "lensDrops", lensDrops);
        out.put(P + "lensDropsAmount", lensDropsAmount);
        out.note("Storm sky: a dark rolling cloud deck slides in with the rain, lit from inside by",
                 "lightning, with flickers and far rumbles inside the clouds during thunderstorms.");
        out.put(P + "stormClouds", stormClouds);
        out.put(P + "stormCloudsStrength", stormCloudsStrength);
        out.note("Bright bolts: lightning draws as a glowing white bolt with forks that pulses with each flash.");
        out.put(P + "stormBolts", stormBolts);
        out.note("Wet surfaces: open ground and walls darken and pick up a soft sheen while it rains,",
                 "drying out over a few minutes after. Roofs and tree cover keep what is below them dry.");
        out.put(P + "wetSurfaces", wetSurfaces);
        out.put(P + "wetSurfacesStrength", wetSurfacesStrength);
        out.note("Storm wind: thunderstorms blow harder, so rain slants further and more leaves",
                 "tear loose and tumble past. Everything that sways in the wind feels it.");
        out.put(P + "stormWind", stormWind);
        out.note("Blizzard: in a snowy biome a thunderstorm becomes a whiteout, with dense small flakes",
                 "driven sideways by the wind, a pale fog closing in and a quiet howl.");
        out.put(P + "blizzard", blizzard);
        out.put(P + "blizzardStrength", blizzardStrength);
        out.note("Frost: pixel ice crystals creep in from the screen corners as you freeze in powder snow",
                 "(replacing the flat vanilla overlay) and lightly in a blizzard, melting away slowly.");
        out.put(P + "screenFrost", screenFrost);
        out.note("Sandstorm: when it rains over a warm biome with no precipitation, thick beige fog,",
                 "streams of sand, drifting dust clouds and a dry wind roll in.");
        out.put(P + "sandstorm", sandstorm);
        out.put(P + "sandstormStrength", sandstormStrength);

        out.section("Tuning aids");
        out.note("Shows the biome you are standing in, and whether it has settings.");
        out.put(P + "biomeOverlay", biomeOverlay);
        out.note("Prints one line per visual subsystem saying whether it drew this",
                 "frame, skipped itself, or was never reached at all.");
        out.put(P + "debugOverlay", debugOverlay);
    }

    private static double num(Properties p, String key, double def, double min, double max) {
        String v = p.getProperty(P + key);
        if (v == null) return Mth.clamp(def, min, max);
        try { return Mth.clamp(Double.parseDouble(v), min, max); }
        catch (Exception e) { return def; }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(P + key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    private static int hex(Properties p, String key, int def) {
        String v = p.getProperty(P + key);
        if (v == null) return def;
        v = v.trim();
        try { return Integer.parseInt(v.startsWith("#") ? v.substring(1) : v, 16) & 0xFFFFFF; }
        catch (Exception e) { return def; }
    }
}
