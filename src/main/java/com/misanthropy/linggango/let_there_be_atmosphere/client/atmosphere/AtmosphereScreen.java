package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.FxConfig;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.GlowTag;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.StoryCloudConfig;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.StoryClouds;
import com.misanthropy.linggango.let_there_be_atmosphere.config.AtmosphereConfigManager;
import com.misanthropy.linggango.let_there_be_atmosphere.config.AtmosphereConfigManager.AtmosphereSettings;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public class AtmosphereScreen extends Screen {
    private static final int FIRST_WORLD_PAGE = 5;
    private static final int FIRST_TOOL_PAGE = 25;

    private static final int PAGE_SUN_MOON = 7;
    private static final int PAGE_CLOUDS = 12;
    private static final int PAGE_CLOUD_DETAIL = 13;

    private static final String[] GROUP_NAMES = {"This biome", "Everywhere", "Tools"};

    private static final String[] PAGE_TITLES = {
            "Fog", "Sky & Time", "Rain & Water", "Grass & Trees", "Air",
            "Basics", "Fog", "Sun & Moon", "Haze", "Stars", "Comets", "Aurora",
            "Clouds", "Cloud Detail", "Weather", "Storms", "Light", "Air", "Plants", "Wind", "Wildlife", "Water", "Glow", "Nether", "End",
            "Presets", "Copy To Biomes", "Reset Everything"
    };

    private static final int[] PAGE_GROUP = {
            0, 0, 0, 0, 0,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1,
            2, 2, 2
    };

    private static final String[] WORLD_TABS = {
            "Basics", "Fog", "Sun & Moon", "Haze", "Stars", "Comets", "Aurora",
            "Clouds", "Cloud Detail", "Weather", "Storms", "Light", "Air", "Plants", "Wind", "Wildlife", "Water", "Glow", "Nether", "End"
    };

    private static final String[] COMET_PALETTES = {"Your Colour", "Warm", "Cool", "White", "Mixed"};
    private static final String[] BLADE_STYLES = {"Short Tufts", "Tall Meadow"};
    private static final String[] WIND_FILTERS = {"Smoke & Dust", "Most Things", "Everything Dry"};

    private static final Class<?>[] SETTING_HOLDERS = {DynamicFogHandler.class, FxConfig.class, StoryCloudConfig.class};

    private static final Set<String> NEVER_RANDOM = Set.of("FxConfig.debugOverlay");

    private static final String[][] TAB_FIELDS = {
            {"DynamicFogHandler.masterEnabled", "DynamicFogHandler.dynamicFogEnabled",
             "DynamicFogHandler.voidFogEnabled", "DynamicFogHandler.waterFogEnabled",
             "DynamicFogHandler.waterFogDistance", "FxConfig.biomeOverlay",
             "FxConfig.debugOverlay"},

            {"DynamicFogHandler.defaultFogHex", "DynamicFogHandler.biomeTintStrength",
             "DynamicFogHandler.skyFogBlendStrength", "DynamicFogHandler.fogColorVivid", "FxConfig.fogClarity",
             "DynamicFogHandler.fogStartMultiplier", "DynamicFogHandler.skyFogEnd",
             "DynamicFogHandler.fogDensity", "DynamicFogHandler.nearHazeStrength",
             "DynamicFogHandler.nightFogStartMul",
             "DynamicFogHandler.nightFogEndMul", "DynamicFogHandler.caveFogScale",
             "DynamicFogHandler.caveFogMinEnd", "DynamicFogHandler.chunkBorderFogSoftness",
             "DynamicFogHandler.fogTransitionSpeed", "DynamicFogHandler.distanceTransitionSpeed"},

            {"FxConfig.vividSky", "FxConfig.vividSkyStrength", "FxConfig.sunBurst", "FxConfig.sunBurstStrength", "FxConfig.sunScale", "FxConfig.moonScale", "FxConfig.sunsetFogStrength", "FxConfig.sunsetWide",
             "FxConfig.skyGlowEnabled", "FxConfig.sunGlowStrength", "FxConfig.moonGlowStrength",
             "FxConfig.godRays", "FxConfig.godRayStrength", "FxConfig.godRayLength", "FxConfig.lensFlare", "FxConfig.lensFlareStrength"},

            {"DynamicFogHandler.hazeOverlayEnabled", "DynamicFogHandler.hazeOverlayStrength",
             "FxConfig.hazeBandEnabled", "FxConfig.hazeBandStrength", "FxConfig.hazeBandBottom",
             "FxConfig.hazeBandMiddle", "FxConfig.hazeBandTop", "FxConfig.hazeBandRadius",
             "FxConfig.groundMist", "FxConfig.swampMist", "FxConfig.groundMistDensity", "FxConfig.groundMistHeight",
             "FxConfig.airMist", "FxConfig.airMistAmount"},

            {"DynamicFogHandler.starsEnabled", "DynamicFogHandler.starAmount",
             "DynamicFogHandler.starBrightness", "DynamicFogHandler.starTwinkle",
             "DynamicFogHandler.dayStarsEnabled", "DynamicFogHandler.dayStarAmount",
             "DynamicFogHandler.dayStarBrightness", "DynamicFogHandler.dayStarTwinkle",
             "FxConfig.starSky", "FxConfig.milkyWay"},

            {"FxConfig.cometsEnabled", "FxConfig.cometRate", "FxConfig.cometMax", "FxConfig.cometSpeed",
             "FxConfig.cometTail", "FxConfig.cometBrightness", "FxConfig.cometPalette",
             "FxConfig.cometHex", "FxConfig.cometHeadSize", "FxConfig.cometAngle",
             "FxConfig.cometShowerChance", "FxConfig.cometShowerSize"},

            {"FxConfig.auroraEnabled", "FxConfig.auroraBrightness", "FxConfig.auroraFrequency",
             "FxConfig.auroraSpeed"},

            {"DynamicFogHandler.cloudRangeChunks", "DynamicFogHandler.cloudFogEnabled",
             "FxConfig.cloudNightDarken", "FxConfig.cloudFogBlend",
             "FxConfig.cloudShadows", "FxConfig.cloudShadowStrength", "FxConfig.cloudShadowSoftness",
             "StoryCloudConfig.enabled", "StoryCloudConfig.secondLayer",
             "StoryCloudConfig.secondOffset", "StoryCloudConfig.secondCoverage",
             "StoryCloudConfig.cellSize", "StoryCloudConfig.thickness",
             "StoryCloudConfig.heightOffset", "StoryCloudConfig.coverage"},

            {"StoryCloudConfig.speed", "StoryCloudConfig.widthScale", "StoryCloudConfig.lengthScale",
             "StoryCloudConfig.shading", "StoryCloudConfig.lighting", "StoryCloudConfig.tintHex", "StoryCloudConfig.tintStrength",
             "StoryCloudConfig.fogFade", "StoryCloudConfig.alpha", "StoryCloudConfig.bottomFade",
             "StoryCloudConfig.topFade", "StoryCloudConfig.heightVariation",
             "StoryCloudConfig.detail", "StoryCloudConfig.pastel", "StoryCloudConfig.softEdges"},

            {"DynamicFogHandler.rainFogDensity", "DynamicFogHandler.weatherFogDarken",
             "DynamicFogHandler.weatherSkyDarken", "DynamicFogHandler.weatherCloudDarken",
             "FxConfig.weatherTintEnabled", "FxConfig.weatherTintStrength",
             "FxConfig.rainTintHex", "FxConfig.snowTintHex",
             "FxConfig.rainbowsEnabled", "FxConfig.rainbowChance", "FxConfig.rainbowBrightness",
             "FxConfig.lightningFlash", "FxConfig.lightningFlashStrength",
             "FxConfig.puddles", "FxConfig.rainSplashes", "FxConfig.puddleAmount"},

            {"FxConfig.stormRain", "FxConfig.stormRainAmount", "FxConfig.lensDrops", "FxConfig.lensDropsAmount", "FxConfig.stormClouds", "FxConfig.stormCloudsStrength",
             "FxConfig.stormBolts", "FxConfig.wetSurfaces", "FxConfig.wetSurfacesStrength", "FxConfig.stormWind",
             "FxConfig.blizzard", "FxConfig.blizzardStrength", "FxConfig.sandstorm", "FxConfig.sandstormStrength",
             "FxConfig.stormRange"},

            {"FxConfig.lightTintEnabled", "FxConfig.blockLightHex", "FxConfig.blockLightStrength",
             "FxConfig.skyLightNightHex", "FxConfig.skyLightStrength",
             "FxConfig.sunLightTint", "FxConfig.sunLightHex", "FxConfig.sunLightStrength",
             "FxConfig.gradeEnabled", "FxConfig.gradeSaturation", "FxConfig.gradeVibrance", "FxConfig.gradeContrast",
             "FxConfig.gradeWarmth", "FxConfig.gradeVignette", "FxConfig.gradeDarkDesaturation",
             "FxConfig.gradeDimVignette", "FxConfig.gradeDimFade"},

            {"FxConfig.mistEnabled", "FxConfig.mistRate", "FxConfig.mistRadius",
             "FxConfig.mistPerTick", "FxConfig.windStrength",
             "FxConfig.heatHaze", "FxConfig.heatHazeStrength", "FxConfig.coldAir", "FxConfig.coldAirStrength",
             "FxConfig.caveDust", "FxConfig.caveSparkle", "FxConfig.caveDustAmount",
             "FxConfig.lavaLife", "FxConfig.lavaLifeAmount", "FxConfig.deepDarkHaze"},

            {"DynamicFogHandler.biomeColorsEnabled", "DynamicFogHandler.vegSaturation",
             "DynamicFogHandler.vegBrightness", "DynamicFogHandler.vegVariation", "DynamicFogHandler.vegSunny",
             "FxConfig.groundLitter", "FxConfig.groundLitterAmount",
             "FxConfig.fallingLeaves", "FxConfig.fallingLeavesAmount", "FxConfig.litterSounds", "FxConfig.windSway", "FxConfig.windSwayStrength", "FxConfig.oreGlow", "FxConfig.oreGlowStrength", "FxConfig.stemPulse", "FxConfig.grassBlades", "FxConfig.grassBladesDensity", "FxConfig.grassBladesStyle"},

            {"FxConfig.livingWind", "FxConfig.windFront", "FxConfig.windAltitude", "FxConfig.windShelter",
             "FxConfig.windParticles", "FxConfig.windParticleStrength", "FxConfig.windParticleFilter",
             "FxConfig.windBits", "FxConfig.windBitsAmount", "FxConfig.windStreaks",
             "FxConfig.windSounds", "FxConfig.windSoundVolume", "FxConfig.rustleSounds"},

            {"FxConfig.butterflies", "FxConfig.butterfliesAmount", "FxConfig.birds", "FxConfig.birdsAmount", "FxConfig.batSwarm",
             "FxConfig.fireflies", "FxConfig.firefliesAmount", "FxConfig.tumbleweeds",
             "FxConfig.breathPuffs", "FxConfig.footstepPuffs", "FxConfig.footstepPuffsAmount"},

            {"FxConfig.skyReflection", "FxConfig.skyReflectionStrength", "FxConfig.waterReflections", "FxConfig.waterReflectionStrength",
             "FxConfig.fishJumps", "FxConfig.fishJumpsAmount", "FxConfig.waterSparkles", "FxConfig.waterSparklesAmount",
             "FxConfig.shoreFoam", "FxConfig.shoreFoamStrength", "FxConfig.waterWake", "FxConfig.waterWakeStrength",
             "FxConfig.ripples", "FxConfig.ripplesAmount", "FxConfig.rainRings", "FxConfig.rainRingsAmount",
             "FxConfig.waterfallFoam", "FxConfig.waterfallFoamAmount",
             "FxConfig.caustics", "FxConfig.causticsStrength", "FxConfig.waterRays", "FxConfig.waterRaysStrength",
             "FxConfig.underwaterBubbles", "FxConfig.underwaterBubblesAmount",
             "FxConfig.deepWater", "FxConfig.deepWaterDarkness", "FxConfig.marineSnow", "FxConfig.marineSnowAmount",
             "FxConfig.deepSounds", "FxConfig.deepSoundsVolume",
             "DynamicFogHandler.waterSaturation", "DynamicFogHandler.waterBrightness"},

            {"FxConfig.bloomEnabled", "FxConfig.bloomStrength", "FxConfig.bloomThreshold",
             "FxConfig.bloomSpread", "FxConfig.ambientOcclusion", "FxConfig.ambientOcclusionStrength",
             "FxConfig.sunShadows", "FxConfig.sunShadowStrength"},

            {"FxConfig.netherRays", "FxConfig.netherRaysStrength", "FxConfig.netherMist", "FxConfig.netherMistDensity",
             "FxConfig.netherDust", "FxConfig.netherDustAmount", "FxConfig.netherStorms", "FxConfig.netherStormRate",
             "FxConfig.lavaCrust", "FxConfig.lavaCrustAmount", "FxConfig.netherStormStrength",
             "FxConfig.lavaParticles", "FxConfig.lavaParticlesAmount", "FxConfig.lavaEruptions", "FxConfig.netherPortalFx"},

            {"FxConfig.endSky", "FxConfig.endDust", "FxConfig.endDustAmount", "FxConfig.endMist", "FxConfig.endMistAmount", "FxConfig.endShadows", "FxConfig.endShadowAmount", "FxConfig.portalGlow",
             "FxConfig.endFog", "FxConfig.endFogDensity", "FxConfig.endPortalFx"},
    };

    private static final String[][] PARTICLES = {
            {"none", "None"},
            {"minecraft:white_ash", "White Ash"},
            {"minecraft:ash", "Ash"},
            {"minecraft:spore_blossom_air", "Pollen"},
            {"minecraft:cherry_leaves", "Cherry Blossom"},
            {"minecraft:snowflake", "Snow"},
            {"minecraft:mycelium", "Spores"},
            {"minecraft:warped_spore", "Blue Spores"},
            {"minecraft:crimson_spore", "Red Spores"},
            {"minecraft:end_rod", "Sparkles"},
    };

    private static final int ROW_H = 22;
    private static final int RAIL_W = 108;
    private static final int GROUP_H = 14;
    private static final int RAIL_PAD = 5;

    private static final AtmosphereSettings BIOME_DEFAULTS = new AtmosphereSettings();

    private static final Map<String, Field> SETTINGS = new LinkedHashMap<>();

    static {
        for (Class<?> holder : SETTING_HOLDERS) {
            for (Field field : holder.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)
                        || Modifier.isFinal(modifiers)) continue;
                SETTINGS.put(holder.getSimpleName() + "." + field.getName(), field);
            }
        }
    }

    private static Map<String, Object> cachedDefaults;
    private static int lastPage;
    private static int lastScroll;

    private final ResourceLocation biomeId;
    private final AtmosphereSettings working;
    private final AtmosphereSettings openedBiome;
    private final Map<String, Object> openedWorld;

    private final List<Object> rows = new ArrayList<>();
    private final List<ResourceLocation> allBiomes = new ArrayList<>();
    private final Set<ResourceLocation> copyPicked = new HashSet<>();

    private boolean copyFogColors = true;
    private boolean copyFogDistance = true;
    private boolean copySkyAndAir = true;
    private boolean copyBlockColors;

    private List<String> presets = List.of();
    private String selectedPreset;

    private EditBox searchBox, presetNameBox, biomeFilterBox;

    private int currentPage;
    private int scroll;
    private boolean needsRebuild;
    private boolean biomeCleared;
    private boolean confirmReset;
    private boolean confirmWipe;
    private boolean confirmRandom;
    private boolean vegDirty, starsDirty;
    private String status = "";

    private final boolean[] pageModified = new boolean[PAGE_TITLES.length];
    private final int[] railY = new int[PAGE_TITLES.length];
    private final int[] groupY = new int[GROUP_NAMES.length];

    private int panelX, panelY, panelW, panelH;
    private int railX, railTop, railBottom, railRowH;
    private int railScroll, railMaxScroll;
    private int contentX, contentW, rowsTop, rowsBottom, rowsVisible;
    private int footerTop;

    public AtmosphereScreen(ResourceLocation biomeId) {
        super(Component.literal("Atmosphere"));
        this.biomeId = biomeId;
        this.currentPage = Mth.clamp(lastPage, 0, PAGE_TITLES.length - 1);
        this.scroll = Math.max(0, lastScroll);
        AtmosphereSettings existing = AtmosphereConfigManager.ATMOSPHERES.get(biomeId);
        this.working = existing != null ? existing.copy() : new AtmosphereSettings();
        this.openedBiome = existing != null ? existing.copy() : null;
        this.biomeCleared = existing == null;
        this.openedWorld = snapshot();
    }

    public static boolean isLiveEdit(Screen screen) {
        return screen instanceof AtmosphereScreen || screen instanceof ColorPickerScreen;
    }

    @Override
    protected void init() {
        if (biomeCleared) AtmosphereConfigManager.ATMOSPHERES.remove(biomeId);
        else AtmosphereConfigManager.ATMOSPHERES.put(biomeId, working);
        AtmosphereConfigManager.refreshOverrideFlags();
        BiomeColorModifier.clearCache();
        if (vegDirty) {
            vegDirty = false;
            DynamicFogHandler.refreshBiomeColors();
        }

        panelW = Math.min(width - 16, 464);
        panelH = Math.min(height - 16, 404);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        footerTop = panelY + panelH - 30;
        railX = panelX + 5;
        railTop = panelY + 30;
        railBottom = footerTop - 6;
        railRowH = Mth.clamp(
                (railBottom - railTop - RAIL_PAD * 2 - GROUP_NAMES.length * GROUP_H) / PAGE_TITLES.length,
                11, 18);

        contentX = railX + RAIL_W + 6;
        contentW = panelX + panelW - 14 - contentX;
        rowsTop = panelY + 66;
        rowsBottom = footerTop - 16;
        rowsVisible = Math.max(1, (rowsBottom - rowsTop) / ROW_H);

        if (searchBox == null) {
            searchBox = new EditBox(font, 0, 0, 10, 16, Component.empty());
            searchBox.setHint(Component.literal("Search every setting"));
            searchBox.setMaxLength(40);
            searchBox.setResponder(s -> needsRebuild = true);
        }
        if (presetNameBox == null) {
            presetNameBox = new EditBox(font, 0, 0, 10, ROW_H - 4, Component.empty());
            presetNameBox.setHint(Component.literal("Name your preset"));
            presetNameBox.setMaxLength(32);
        }
        if (biomeFilterBox == null) {
            biomeFilterBox = new EditBox(font, 0, 0, 10, ROW_H - 4, Component.empty());
            biomeFilterBox.setHint(Component.literal("Search biomes"));
            biomeFilterBox.setMaxLength(48);
            biomeFilterBox.setResponder(s -> needsRebuild = true);
        }
        searchBox.setPosition(contentX, panelY + 32);
        searchBox.setWidth(contentW);

        build();
    }

    @Override
    public void tick() {
        super.tick();
        if (needsRebuild) {
            needsRebuild = false;
            build();
        }
        refreshModifiedMarks();
    }

    private void build() {
        EditBox keepFocus = focusedBox();
        clearWidgets();
        rows.clear();

        addRenderableWidget(searchBox);

        int railSpan = railBottom - railTop - RAIL_PAD * 2;
        railMaxScroll = Math.max(0, PAGE_TITLES.length * railRowH + GROUP_NAMES.length * GROUP_H - railSpan);
        railScroll = Mth.clamp(railScroll, 0, railMaxScroll);

        int y = railTop + RAIL_PAD - railScroll;
        int lastGroup = -1;
        for (int page = 0; page < PAGE_TITLES.length; page++) {
            if (PAGE_GROUP[page] != lastGroup) {
                lastGroup = PAGE_GROUP[page];
                groupY[lastGroup] = y;
                y += GROUP_H;
            }
            railY[page] = y;
            if (y >= railTop && y + railRowH <= railBottom) {
                    RailButton rail = new RailButton(page, railX, y);
                AtmosphereUi.tip(rail, page < FIRST_TOOL_PAGE
                        ? (pageModified[page] ? "Changed from default" : "All default here")
                        : PAGE_TITLES[page]);
                addRenderableWidget(rail);
            }
            y += railRowH;
        }

        if (searching()) {
            for (int page = 0; page < FIRST_TOOL_PAGE; page++) {
                int before = rows.size();
                rows.add(GROUP_NAMES[PAGE_GROUP[page]] + " - " + PAGE_TITLES[page]);
                buildPage(page);
                if (rows.size() == before + 1) rows.remove(before);
            }
        } else {
            buildPage(currentPage);
        }

        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - rowsVisible));
        layoutRows();
        buildFooter();

        if (keepFocus != null && children().contains(keepFocus)) {
            setFocused(keepFocus);
            keepFocus.setFocused(true);
        }
    }

    private void refreshModifiedMarks() {
        for (int page = 0; page < FIRST_WORLD_PAGE; page++) pageModified[page] = biomePageChanged(page);
        Map<String, Object> defaults = defaults();
        for (int page = FIRST_WORLD_PAGE; page < FIRST_TOOL_PAGE; page++) {
            boolean changed = false;
            for (String field : TAB_FIELDS[page - FIRST_WORLD_PAGE]) {
                Object def = defaults.get(field);
                if (def != null && !def.equals(read(field))) {
                    changed = true;
                    break;
                }
            }
            pageModified[page] = changed;
        }
    }

    private boolean biomePageChanged(int page) {
        if (biomeCleared) return false;
        AtmosphereSettings d = BIOME_DEFAULTS;
        AtmosphereSettings w = working;
        return switch (page) {
            case 0 -> w.fogHex != d.fogHex || w.fogStart != d.fogStart || w.fogEnd != d.fogEnd
                    || w.fogDensity != d.fogDensity || w.sphereFog != d.sphereFog
                    || w.heightFogStr != d.heightFogStr || w.voidFogStr != d.voidFogStr
                    || w.nearHaze != d.nearHaze;
            case 1 -> w.skyHex != d.skyHex || w.skyTintStr != d.skyTintStr || w.hazeHex != d.hazeHex
                    || w.hazeStrength != d.hazeStrength || w.dawnDuskHex != d.dawnDuskHex
                    || w.dawnDuskStr != d.dawnDuskStr || w.nightHex != d.nightHex
                    || w.nightStr != d.nightStr || w.nightFogStartMul != d.nightFogStartMul
                    || w.nightFogEndMul != d.nightFogEndMul;
            case 2 -> w.rainFogHex != d.rainFogHex || w.rainMult != d.rainMult
                    || w.thunderFogHex != d.thunderFogHex || w.waterFogHex != d.waterFogHex
                    || w.waterFogDist != d.waterFogDist;
            case 3 -> w.grassHex != d.grassHex || w.foliageHex != d.foliageHex
                    || w.waterHex != d.waterHex || w.ambientHex != d.ambientHex
                    || w.satBoost != d.satBoost || w.brightness != d.brightness;
            case 4 -> !Objects.equals(w.mistParticle, d.mistParticle) || w.mistRate != d.mistRate;
            default -> false;
        };
    }

    private static Object read(String name) {
        Field field = SETTINGS.get(name);
        if (field == null) return null;
        try {
            return field.get(null);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private void buildPage(int page) {
        switch (page) {
            case 0 -> biomeFog();
            case 1 -> biomeSky();
            case 2 -> biomeWeather();
            case 3 -> biomeBlocks();
            case 4 -> biomeAir();
            case 5 -> worldBasics();
            case 6 -> worldFog();
            case 7 -> worldSunMoon();
            case 8 -> worldHaze();
            case 9 -> worldStars();
            case 10 -> worldComets();
            case 11 -> worldAurora();
            case 12 -> worldClouds();
            case 13 -> worldCloudDetail();
            case 14 -> worldWeather();
            case 15 -> worldStorms();
            case 16 -> worldLight();
            case 17 -> worldAir();
            case 18 -> worldPlants();
            case 19 -> worldWind();
            case 20 -> worldWildlife();
            case 21 -> worldWater();
            case 22 -> worldGlow();
            case 23 -> worldNether();
            case 24 -> worldEnd();
            case 25 -> presetsPage();
            case 26 -> copyPage();
            case 27 -> resetEverythingPage();
            default -> {}
        }
    }

    private EditBox focusedBox() {
        for (EditBox box : new EditBox[]{searchBox, presetNameBox, biomeFilterBox}) {
            if (box != null && box.isFocused()) return box;
        }
        return null;
    }

    private boolean searching() {
        return searchBox != null && !searchBox.getValue().isBlank();
    }

    private boolean matches(String label, String tip) {
        String needle = searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        return label.toLowerCase(Locale.ROOT).contains(needle)
                || (tip != null && tip.toLowerCase(Locale.ROOT).contains(needle));
    }

    private void layoutRows() {
        for (int i = 0; i < rows.size(); i++) {
            if (!(rows.get(i) instanceof AbstractWidget widget)) continue;
            boolean shown = i >= scroll && i < scroll + rowsVisible;
            widget.setY(rowsTop + (i - scroll) * ROW_H);
            widget.visible = shown;
            widget.active = shown;
        }
    }

    private void buildFooter() {
        int cell = (panelW - 28 - 8) / 3;
        int x = panelX + 14;
        int y = footerTop + 5;
        footer(x, y, cell, confirmReset ? "Sure?" : resetLabel(), this::resetPage);
        footer(x + cell + 4, y, cell, "Undo All", this::undoAll);
        footer(x + (cell + 4) * 2, y, cell, "Done", this::saveAndClose);
    }

    private String resetLabel() {
        if (currentPage < FIRST_WORLD_PAGE) return "Reset Biome";
        if (currentPage < FIRST_TOOL_PAGE) return "Reset Page";
        return "Reset";
    }

    private void footer(int x, int y, int w, String label, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(x, y, w, 20).build());
    }

    private <T extends AbstractWidget> T addRow(T widget, String tip) {
        AtmosphereUi.tip(widget, tip);
        rows.add(widget);
        addRenderableWidget(widget);
        return widget;
    }

    private void slider(String label, float value, float min, float max, int decimals,
                        String suffix, Consumer<Float> on, String tip) {
        if (searching() && !matches(label, tip)) return;
        addRow(new SettingSlider(contentX, rowsTop, contentW, ROW_H - 2, label, value, min, max,
                decimals, suffix, on), tip);
    }

    private void toggle(String label, BooleanSupplier get, Consumer<Boolean> set, String tip) {
        if (searching() && !matches(label, tip)) return;
        addRow(new ToggleRow(contentX, rowsTop, contentW, ROW_H - 2, label, get, set), tip);
    }

    private void color(String label, IntSupplier get, IntConsumer set, boolean allowAuto, int fallback, String tip) {
        if (searching() && !matches(label, tip)) return;
        addRow(new ColorRowButton(this, contentX, rowsTop, contentW, ROW_H - 2, label, get, set,
                allowAuto, fallback), tip);
    }

    private void action(Supplier<String> label, Supplier<String> value, Runnable onPress, String tip) {
        if (searching() && !matches(label.get(), tip)) return;
        addRow(new ActionRow(contentX, rowsTop, contentW, ROW_H - 2, label, value, onPress), tip);
    }

    private void button(String label, Runnable onPress, String tip) {
        addRow(new ActionRow(contentX, rowsTop, contentW, ROW_H - 2, () -> label, null, onPress), tip);
    }

    private void editBox(EditBox box) {
        box.setPosition(contentX, rowsTop);
        box.setWidth(contentW);
        rows.add(box);
        addRenderableWidget(box);
    }

    private void biomeFog() {
        bcolor("Fog Colour", () -> working.fogHex, v -> working.fogHex = v, false, 0x88BFEA,
                "The colour of the haze in the distance.");
        bslider("Fog Starts At", working.fogStart, 0.5f, 240f, 0, "",
                v -> working.fogStart = Math.min(v, working.fogEnd - 1f),
                "How close to you the fog begins.");
        bslider("Fog Ends At", working.fogEnd, 2f, 480f, 0, "",
                v -> working.fogEnd = Math.max(v, working.fogStart + 1f),
                "How far you can see. 120 and under are blocks, for caves. Above that it scales with your render distance: 480 is clear to the edge.");
        bslider("Fog Thickness", working.fogDensity, 0.2f, 5f, 2, "x",
                v -> working.fogDensity = v, "Higher makes the fog heavier all round.");
        action(() -> "Fog Shape", () -> working.sphereFog ? "Round" : "Flat", () -> {
            markEdited();
            working.sphereFog = !working.sphereFog;
            needsRebuild = true;
        }, "Round wraps fog evenly around you. Flat keeps the sky above clearer.");
        bslider("Ground Fog", working.heightFogStr, 0f, 1f, 0, "%",
                v -> working.heightFogStr = v, "Pools fog in valleys and low ground.");
        bslider("Cave Fog", working.voidFogStr, 0f, 1f, 0, "%",
                v -> working.voidFogStr = v, "How closed in it feels deep underground.");
        bslider("Close-Up Haze", working.nearHaze, 0f, 1f, 0, "%",
                v -> working.nearHaze = v, "A soft mist right in front of you. Good for swamps.");
    }

    private void biomeSky() {
        bcolor("Sky Colour", () -> working.skyHex, v -> working.skyHex = v, true, 0x99BBFF,
                "Repaints the sky over this biome.");
        bslider("Sky Colour Strength", working.skyTintStr, 0f, 1f, 0, "%",
                v -> working.skyTintStr = v, "How much of your sky colour shows.");
        bcolor("Horizon Glow", () -> working.hazeHex, v -> working.hazeHex = v, true, 0xBFD4E8,
                "The colour where the sky meets the ground.");
        bslider("Horizon Glow Strength", working.hazeStrength, 0f, 3f, 2, "x",
                v -> working.hazeStrength = v, "How bright that band of colour is.");
        bcolor("Sunset Colour", () -> working.dawnDuskHex, v -> working.dawnDuskHex = v, true, 0xE8A060,
                "Colour mixed in while the sun rises and sets.");
        bslider("Sunset Strength", working.dawnDuskStr, 0f, 2f, 2, "x",
                v -> working.dawnDuskStr = v, "How strong that sunset colour is.");
        bcolor("Night Colour", () -> working.nightHex, v -> working.nightHex = v, true, 0x1A1E36,
                "Colour mixed in after dark.");
        bslider("Night Strength", working.nightStr, 0f, 2f, 2, "x",
                v -> working.nightStr = v, "How strong that night colour is.");
        bslider("Night Fog Start", working.nightFogStartMul, 0.1f, 3f, 2, "x",
                v -> working.nightFogStartMul = v, "Under 1 brings night fog closer to you.");
        bslider("Night Fog End", working.nightFogEndMul, 0.1f, 3f, 2, "x",
                v -> working.nightFogEndMul = v, "Under 1 shortens how far you see at night.");
    }

    private void biomeWeather() {
        bcolor("Rain Fog Colour", () -> working.rainFogHex, v -> working.rainFogHex = v, true, 0x8899AA,
                "Fog colour while it is raining here.");
        bslider("Rain Fog Amount", working.rainMult, 0f, 2f, 2, "x",
                v -> working.rainMult = v, "How much rain thickens the fog. 0 means rain changes nothing.");
        bcolor("Storm Fog Colour", () -> working.thunderFogHex, v -> working.thunderFogHex = v, true, 0x445566,
                "Fog colour during a thunderstorm.");
        bcolor("Underwater Fog", () -> working.waterFogHex, v -> working.waterFogHex = v, true, 0x3F76E4,
                "The colour you see with your head under water.");
        bslider("Underwater View", working.waterFogDist, 0.2f, 2f, 2, "x",
                v -> working.waterFogDist = v, "How far you can see while swimming.");
    }

    private void biomeBlocks() {
        veg("Grass Colour", () -> working.grassHex, v -> working.grassHex = v, 0x7FB238,
                "Recolours grass blocks and plants here.");
        veg("Leaf Colour", () -> working.foliageHex, v -> working.foliageHex = v, 0x59AE30,
                "Recolours tree leaves and vines here.");
        veg("Water Colour", () -> working.waterHex, v -> working.waterHex = v, 0x3F76E4,
                "Recolours the surface of water here.");
        bcolor("Overall Tint", () -> working.ambientHex, v -> working.ambientHex = v, true, 0x808080,
                "A colour gently mixed into everything here.");
        bslider("Colour Richness", working.satBoost, -0.5f, 1.0f, 2, "",
                v -> working.satBoost = v, "Above 0 makes colours punchier. Below 0 washes them out.");
        bslider("Brightness", working.brightness, -0.5f, 0.5f, 2, "",
                v -> working.brightness = v, "Lightens or darkens the whole biome.");
    }

    private void biomeAir() {
        action(() -> "Floating Bits", this::particleValue, () -> {
            markEdited();
            working.mistParticle = nextParticle(working.mistParticle);
            needsRebuild = true;
        }, "Little things drifting past, like ash, snow or petals.");
        bslider("How Many Bits", working.mistRate, 0f, 4f, 2, "x",
                v -> working.mistRate = v, "How many float around you. 0 turns them off.");
    }

    private void worldBasics() {
        toggle("Everything", () -> DynamicFogHandler.masterEnabled, v -> DynamicFogHandler.masterEnabled = v,
                "The master switch. Off makes the game look like the mod is not installed.");
        toggle("Biome Fog", () -> DynamicFogHandler.dynamicFogEnabled, v -> DynamicFogHandler.dynamicFogEnabled = v,
                "Let each biome have its own fog colour and reach.");
        toggle("Deep Cave Fog", () -> DynamicFogHandler.voidFogEnabled, v -> DynamicFogHandler.voidFogEnabled = v,
                "Closes the view in when you go far underground.");
        toggle("Underwater Fog", () -> DynamicFogHandler.waterFogEnabled, v -> DynamicFogHandler.waterFogEnabled = v,
                "Let each biome set its own underwater colour.");
        slider("Underwater View", (float) DynamicFogHandler.waterFogDistance, 0.2f, 3f, 2, "x",
                v -> DynamicFogHandler.waterFogDistance = v, "How far you can see while swimming, everywhere.");
        toggle("Show Biome Name", () -> FxConfig.biomeOverlay, v -> FxConfig.biomeOverlay = v,
                "Prints the biome you are standing in on screen. Handy while tuning.");
    }

    private void worldFog() {
        slider("Clear Air", (float) FxConfig.fogClarity, 0f, 1f, 0, "%", v -> FxConfig.fogClarity = v,
                "Pushes daytime fog out to the edge of your render distance so far land stays crisp. Caves and rain keep their fog.");
        color("Default Fog Colour", () -> DynamicFogHandler.defaultFogHex,
                v -> DynamicFogHandler.defaultFogHex = v, false, 0x88BFEA,
                "Fog colour for biomes you have not tuned.");
        slider("Follow Biome Colour", (float) DynamicFogHandler.biomeTintStrength, 0f, 1f, 0, "%",
                v -> DynamicFogHandler.biomeTintStrength = v, "How much the fog copies the biome colour.");
        slider("Follow Sky Colour", (float) DynamicFogHandler.skyFogBlendStrength, 0f, 1f, 0, "%",
                v -> DynamicFogHandler.skyFogBlendStrength = v, "How much the fog matches the sky above.");
        slider("Colour Richness", (float) DynamicFogHandler.fogColorVivid, 0f, 1f, 0, "%",
                v -> DynamicFogHandler.fogColorVivid = v, "Higher makes fog colours bolder instead of grey.");
        slider("Fog Starts At", (float) DynamicFogHandler.fogStartMultiplier, 0.02f, 0.65f, 0, "%",
                v -> DynamicFogHandler.fogStartMultiplier = v, "Where fog begins, as a share of your render distance.");
        slider("Fog Ends At", (float) DynamicFogHandler.skyFogEnd, 0.3f, 1f, 2, "x",
                v -> DynamicFogHandler.skyFogEnd = v, "Where the view is fully hidden, as a share of render distance.");
        slider("Fog Thickness", (float) DynamicFogHandler.fogDensity, 0.2f, 5f, 2, "x",
                v -> DynamicFogHandler.fogDensity = v, "Higher makes fog heavier everywhere.");
        slider("Close-Up Haze", (float) DynamicFogHandler.nearHazeStrength, 0f, 1f, 0, "%",
                v -> DynamicFogHandler.nearHazeStrength = v, "A soft mist right in front of you.");
        slider("Night Fog Start", (float) DynamicFogHandler.nightFogStartMul, 0.1f, 3f, 2, "x",
                v -> DynamicFogHandler.nightFogStartMul = v, "Under 1 brings night fog closer.");
        slider("Night Fog End", (float) DynamicFogHandler.nightFogEndMul, 0.1f, 3f, 2, "x",
                v -> DynamicFogHandler.nightFogEndMul = v, "Under 1 shortens how far you see at night.");
        slider("Cave Fog", (float) DynamicFogHandler.caveFogScale, 0.1f, 2f, 2, "x",
                v -> DynamicFogHandler.caveFogScale = v, "How thick fog gets underground.");
        slider("Cave View Floor", (float) DynamicFogHandler.caveFogMinEnd, 1f, 256f, 0, " blocks",
                v -> DynamicFogHandler.caveFogMinEnd = v, "The closest cave fog may ever squeeze your view.");
        slider("Fog Edge Softness", (float) DynamicFogHandler.chunkBorderFogSoftness, 0f, 0.35f, 2, "",
                v -> DynamicFogHandler.chunkBorderFogSoftness = v, "How gradually the fog thickens toward the edge of the world.");
        slider("Colour Fade Speed", (float) DynamicFogHandler.fogTransitionSpeed, 0.001f, 0.1f, 3, "",
                v -> DynamicFogHandler.fogTransitionSpeed = v, "How fast fog colour catches up in a new biome.");
        slider("Distance Fade Speed", (float) DynamicFogHandler.distanceTransitionSpeed, 0.001f, 0.1f, 3, "",
                v -> DynamicFogHandler.distanceTransitionSpeed = v, "How fast fog reach catches up in a new biome.");
    }

    private void worldSunMoon() {
        toggle("Vivid Sky", () -> FxConfig.vividSky, v -> FxConfig.vividSky = v,
                "A bold sky that follows the sun: deep blue over a cyan horizon by day, magenta over orange at sunrise and sunset.");
        slider("Vivid Sky Strength", (float) FxConfig.vividSkyStrength, 0f, 1f, 0, "%",
                v -> FxConfig.vividSkyStrength = v, "How far the sky moves toward those colours from the biome's own.");
        toggle("Sun Rays", () -> FxConfig.sunBurst, v -> FxConfig.sunBurst = v,
                "A pixel starburst around the sun, warmer at sunrise and sunset.");
        slider("Sun Ray Brightness", (float) FxConfig.sunBurstStrength, 0f, 2f, 2, "x",
                v -> FxConfig.sunBurstStrength = v, "How bright the starburst is.");
        slider("Sun Size", (float) FxConfig.sunScale, 0.1f, 5f, 2, "x", v -> FxConfig.sunScale = v,
                "Makes the sun bigger or smaller.");
        slider("Moon Size", (float) FxConfig.moonScale, 0.1f, 5f, 2, "x", v -> FxConfig.moonScale = v,
                "Makes the moon bigger or smaller.");
        slider("Sunset Colour In Fog", (float) FxConfig.sunsetFogStrength, 0f, 1.5f, 2, "x",
                v -> FxConfig.sunsetFogStrength = v, "How much sunset orange bleeds into the distance.");
        toggle("Long Sunsets", () -> FxConfig.sunsetWide, v -> FxConfig.sunsetWide = v,
                "Holds the sunset colours for longer.");
        toggle("Sun And Moon Glow", () -> FxConfig.skyGlowEnabled, v -> FxConfig.skyGlowEnabled = v,
                "A soft halo and light rays around the sun, and a pale glow around the moon.");
        slider("Sun Glow", (float) FxConfig.sunGlowStrength, 0f, 2f, 2, "x",
                v -> FxConfig.sunGlowStrength = v, "How bright the halo and rays around the sun are.");
        slider("Moon Glow", (float) FxConfig.moonGlowStrength, 0f, 2f, 2, "x",
                v -> FxConfig.moonGlowStrength = v, "How bright the halo around the moon is. It also follows the moon phase.");
        toggle("God Rays", () -> FxConfig.godRays, v -> FxConfig.godRays = v,
                "Shafts of sunlight through gaps in clouds, trees and hills when you look toward the sun.");
        slider("Ray Brightness", (float) FxConfig.godRayStrength, 0f, 2f, 2, "x",
                v -> FxConfig.godRayStrength = v, "How bright the shafts are.");
        slider("Ray Length", (float) FxConfig.godRayLength, 0f, 1f, 0, "%",
                v -> FxConfig.godRayLength = v, "How far from the sun the shafts reach across the screen.");
        toggle("Lens Flare", () -> FxConfig.lensFlare, v -> FxConfig.lensFlare = v,
                "Soft coloured rings across the screen when you look near the sun.");
        slider("Flare Brightness", (float) FxConfig.lensFlareStrength, 0f, 2f, 2, "x",
                v -> FxConfig.lensFlareStrength = v, "How bright the rings are.");
    }

    private void worldHaze() {
        toggle("Screen Glow", () -> DynamicFogHandler.hazeOverlayEnabled,
                v -> DynamicFogHandler.hazeOverlayEnabled = v, "A soft band of colour across the horizon.");
        slider("Screen Glow Strength", (float) DynamicFogHandler.hazeOverlayStrength, 0f, 1f, 0, "%",
                v -> DynamicFogHandler.hazeOverlayStrength = v, "How strong that band is.");
        toggle("Haze Layer", () -> FxConfig.hazeBandEnabled, v -> FxConfig.hazeBandEnabled = v,
                "A layer of haze sitting out in the world, like real distance haze.");
        slider("Haze Strength", (float) FxConfig.hazeBandStrength, 0f, 2f, 2, "x",
                v -> FxConfig.hazeBandStrength = v, "How thick the haze layer is.");
        slider("Haze Bottom", (float) FxConfig.hazeBandBottom, -64f, 32f, 0, " blocks",
                v -> FxConfig.hazeBandBottom = v, "Height where the haze starts, relative to you.");
        slider("Haze Peak", (float) FxConfig.hazeBandMiddle, -48f, 48f, 0, " blocks",
                v -> FxConfig.hazeBandMiddle = v, "Height where the haze is thickest.");
        slider("Haze Top", (float) FxConfig.hazeBandTop, -32f, 192f, 0, " blocks",
                v -> FxConfig.hazeBandTop = v, "Height where the haze fades out.");
        slider("Haze Distance", (float) FxConfig.hazeBandRadius, 48f, 512f, 0, " blocks",
                v -> FxConfig.hazeBandRadius = v, "How far out the haze layer reaches.");
        toggle("Ground Mist", () -> FxConfig.groundMist, v -> FxConfig.groundMist = v,
                "Light mist pools in the low ground around you at dawn and after rain.");
        toggle("Swamp Mist", () -> FxConfig.swampMist, v -> FxConfig.swampMist = v,
                "Thick, low mist hangs over swamps and mangroves all day.");
        slider("Mist Thickness", (float) FxConfig.groundMistDensity, 0f, 3f, 2, "x",
                v -> FxConfig.groundMistDensity = v, "How thick the mist is.");
        slider("Mist Height", (float) FxConfig.groundMistHeight, 2f, 40f, 0, " blocks",
                v -> FxConfig.groundMistHeight = v, "How far above the valley floor the mist reaches.");
        toggle("Air Mist", () -> FxConfig.airMist, v -> FxConfig.airMist = v,
                "Big soft pastel wisps of mist float in the open air around you and drift with the wind.");
        slider("Air Mist Amount", (float) FxConfig.airMistAmount, 0f, 2f, 2, "x",
                v -> FxConfig.airMistAmount = v, "How many mist wisps float around you.");
    }

    private void worldStars() {
        toggle("Stars", () -> DynamicFogHandler.starsEnabled, v -> {
            DynamicFogHandler.starsEnabled = v;
            starsDirty = true;
        }, "Show stars at night.");
        slider("How Many Stars", (float) DynamicFogHandler.starAmount, 0f, 1000f, 0, "%", v -> {
            DynamicFogHandler.starAmount = v;
            starsDirty = true;
        }, "100% is normal. Higher fills the sky.");
        slider("Star Brightness", (float) DynamicFogHandler.starBrightness, 0f, 2f, 2, "x",
                v -> DynamicFogHandler.starBrightness = v, "How brightly the stars shine.");
        slider("Star Twinkle", (float) DynamicFogHandler.starTwinkle, 0f, 1f, 0, "%",
                v -> DynamicFogHandler.starTwinkle = v, "How much the stars flicker.");
        toggle("Stars By Day", () -> DynamicFogHandler.dayStarsEnabled, v -> {
            DynamicFogHandler.dayStarsEnabled = v;
            starsDirty = true;
        }, "Keeps stars in the sky during the day. Good for alien worlds.");
        slider("How Many Day Stars", (float) DynamicFogHandler.dayStarAmount, 0f, 1000f, 0, "%", v -> {
            DynamicFogHandler.dayStarAmount = v;
            starsDirty = true;
        }, "How many stars show during the day.");
        slider("Day Star Brightness", (float) DynamicFogHandler.dayStarBrightness, 0f, 2f, 2, "x",
                v -> DynamicFogHandler.dayStarBrightness = v, "How bright the daytime stars are.");
        slider("Day Star Twinkle", (float) DynamicFogHandler.dayStarTwinkle, 0f, 1f, 0, "%",
                v -> DynamicFogHandler.dayStarTwinkle = v, "How much the daytime stars flicker.");
        toggle("Pixel Star Sky", () -> FxConfig.starSky, v -> {
            FxConfig.starSky = v;
            starsDirty = true;
        }, "Crisp twinkling stars in several sizes and colours, with a soft halo round the moon.");
        toggle("Milky Way", () -> FxConfig.milkyWay, v -> FxConfig.milkyWay = v,
                "A soft band of dust and denser stars across the night sky. Needs Pixel Star Sky.");
    }

    private void worldComets() {
        toggle("Shooting Stars", () -> FxConfig.cometsEnabled, v -> FxConfig.cometsEnabled = v,
                "Streaks of light crossing the night sky.");
        slider("How Often", (float) FxConfig.cometRate, 0f, 0.2f, 3, "", v -> FxConfig.cometRate = v,
                "Chance of a shooting star appearing. Higher means more.");
        slider("At Once", (float) FxConfig.cometMax, 1f, 16f, 0, "", v -> FxConfig.cometMax = v,
                "How many shooting stars can share the sky.");
        slider("Shooting Star Speed", (float) FxConfig.cometSpeed, 0.1f, 5f, 2, "x",
                v -> FxConfig.cometSpeed = v, "How fast they fly across.");
        slider("Tail Length", (float) FxConfig.cometTail, 0.1f, 5f, 2, "x", v -> FxConfig.cometTail = v,
                "How long the trail behind them is.");
        slider("Shooting Star Brightness", (float) FxConfig.cometBrightness, 0f, 2f, 2, "x",
                v -> FxConfig.cometBrightness = v, "How brightly they glow.");
        action(() -> "Shooting Star Colours", () -> COMET_PALETTES[Mth.clamp(FxConfig.cometPalette, 0, 4)],
                () -> {
                    FxConfig.cometPalette = (FxConfig.cometPalette + 1) % COMET_PALETTES.length;
                    needsRebuild = true;
                }, "Ready-made colour schemes. Mixed gives every streak a different one.");
        color("Shooting Star Colour", () -> FxConfig.cometHex, v -> FxConfig.cometHex = v, false, 0xFFF2D0,
                "The colour of the streak, used when the scheme above is Your Colour.");
        slider("Shooting Star Head", (float) FxConfig.cometHeadSize, 0.2f, 4f, 2, "x",
                v -> FxConfig.cometHeadSize = v, "Size of the burning head at the front.");
        slider("Shooting Star Height", (float) FxConfig.cometAngle, 0f, 90f, 0, "\u00B0",
                v -> FxConfig.cometAngle = v, "How high above the horizon they start.");
        slider("Meteor Shower Chance", (float) FxConfig.cometShowerChance, 0f, 0.02f, 4, "",
                v -> FxConfig.cometShowerChance = v,
                "Chance per tick of a whole shower opening instead of one streak. Keep it small.");
        slider("Meteor Shower Size", (float) FxConfig.cometShowerSize, 3f, 16f, 0, "",
                v -> FxConfig.cometShowerSize = Math.round(v),
                "How many fall together, all radiating from the same point in the sky.");
    }

    private void worldAurora() {
        toggle("Northern Lights", () -> FxConfig.auroraEnabled, v -> FxConfig.auroraEnabled = v,
                "Curtains of light over cold biomes on clear nights. The colder it is, the higher they climb.");
        slider("Aurora Brightness", (float) FxConfig.auroraBrightness, 0f, 2f, 2, "x",
                v -> FxConfig.auroraBrightness = v, "How brightly the curtains glow.");
        slider("Aurora Nights", (float) FxConfig.auroraFrequency, 0.25f, 4f, 2, "x",
                v -> FxConfig.auroraFrequency = v,
                "How often a night is bright enough to show them. Higher means more nights.");
        slider("Aurora Speed", (float) FxConfig.auroraSpeed, 0.1f, 4f, 2, "x",
                v -> FxConfig.auroraSpeed = v, "How fast the curtains ripple and drift.");
    }

    private void worldClouds() {
        slider("How Far They Reach", (float) DynamicFogHandler.cloudRangeChunks, 8f, 64f, 0, " chunks",
                v -> DynamicFogHandler.cloudRangeChunks = Math.round(v), "How far away clouds keep being drawn.");
        toggle("Keep Cloud Colour", () -> DynamicFogHandler.cloudFogEnabled,
                v -> DynamicFogHandler.cloudFogEnabled = v, "Stops distant clouds being washed out by fog.");
        slider("Darker At Night", (float) FxConfig.cloudNightDarken, 0f, 1f, 0, "%",
                v -> FxConfig.cloudNightDarken = v, "How much clouds dim after sunset.");
        slider("Match Fog Colour", (float) FxConfig.cloudFogBlend, 0f, 1f, 0, "%",
                v -> FxConfig.cloudFogBlend = v, "Tints clouds towards the fog colour.");
        toggle("Cloud Shadows", () -> FxConfig.cloudShadows, v -> FxConfig.cloudShadows = v,
                "Clouds cast moving shadows on the ground in daylight.");
        slider("Shadow Darkness", (float) FxConfig.cloudShadowStrength, 0f, 1f, 0, "%",
                v -> FxConfig.cloudShadowStrength = v, "How dark a cloud's shadow is.");
        slider("Shadow Softness", (float) FxConfig.cloudShadowSoftness, 0.02f, 1f, 0, "%",
                v -> FxConfig.cloudShadowSoftness = v, "Low gives blocky shadows that match the clouds, high gives soft blobs.");
        toggle("Second Storybook Layer", () -> StoryCloudConfig.secondLayer,
                v -> StoryCloudConfig.secondLayer = v,
                "Adds another bank of fluffy clouds above the first. Needs Storybook Clouds on.");
        slider("Storybook Layer Height", (float) StoryCloudConfig.secondOffset, -64f, 128f, 0, " blocks",
                v -> StoryCloudConfig.secondOffset = v, "How far above the first bank it sits.");
        slider("Storybook Layer Coverage", (float) StoryCloudConfig.secondCoverage, 0.05f, 0.95f, 0, "%",
                v -> StoryCloudConfig.secondCoverage = v, "How much of the sky the second bank fills.");
        toggle("Storybook Clouds", () -> StoryCloudConfig.enabled, v -> {
            StoryCloudConfig.enabled = v;
            StoryClouds.invalidate();
        }, "Swaps the flat cloud sheet for big fluffy blocks.");
        slider("Puff Size", (float) StoryCloudConfig.cellSize, 8f, 96f, 0, " blocks",
                v -> StoryCloudConfig.cellSize = v, "How large one lump of cloud is.");
        slider("Puff Height", (float) StoryCloudConfig.thickness, 2f, 64f, 0, " blocks",
                v -> StoryCloudConfig.thickness = v, "How tall the clouds are.");
        slider("Cloud Height", (float) StoryCloudConfig.heightOffset, -64f, 128f, 0, " blocks",
                v -> StoryCloudConfig.heightOffset = v, "Raises or lowers the whole cloud layer.");
        slider("Sky Coverage", (float) StoryCloudConfig.coverage, 0.05f, 0.95f, 0, "%",
                v -> StoryCloudConfig.coverage = v, "How much of the sky is cloudy.");
    }

    private void worldCloudDetail() {
        slider("Drift Speed", (float) StoryCloudConfig.speed, 0f, 10f, 2, "x",
                v -> StoryCloudConfig.speed = v, "How fast the clouds move.");
        slider("Stretch Sideways", (float) StoryCloudConfig.widthScale, 0.25f, 4f, 2, "x",
                v -> StoryCloudConfig.widthScale = v, "Squashes or stretches clouds left to right.");
        slider("Stretch Lengthways", (float) StoryCloudConfig.lengthScale, 0.25f, 4f, 2, "x",
                v -> StoryCloudConfig.lengthScale = v, "Squashes or stretches clouds front to back.");
        toggle("Shaded Sides", () -> StoryCloudConfig.shading, v -> StoryCloudConfig.shading = v,
                "Darkens the sides so clouds look solid.");
        toggle("Cloud Lighting", () -> StoryCloudConfig.lighting, v -> StoryCloudConfig.lighting = v,
                "Soft clouds: darker cores and bright feathered edges underneath, sunlit sides, warm glow at sunrise and sunset.");
        color("Cloud Colour", () -> StoryCloudConfig.tintHex, v -> StoryCloudConfig.tintHex = v, true, 0xFFFFFF,
                "Paint the clouds a colour of your own.");
        slider("Cloud Colour Strength", (float) StoryCloudConfig.tintStrength, 0f, 1f, 0, "%",
                v -> StoryCloudConfig.tintStrength = v, "How much of that colour shows.");
        slider("Fade Into Fog", (float) StoryCloudConfig.fogFade, 0f, 1f, 0, "%",
                v -> StoryCloudConfig.fogFade = v, "How much distant clouds take on the fog colour as they fade out.");
        slider("See-Through", (float) StoryCloudConfig.alpha, 0.1f, 1f, 0, "%",
                v -> StoryCloudConfig.alpha = v, "Lower makes clouds thinner and wispier.");
        slider("Soft Bottom", (float) StoryCloudConfig.bottomFade, 0f, 0.95f, 0, "%",
                v -> StoryCloudConfig.bottomFade = v, "Fades out the underside of the clouds.");
        slider("Soft Top", (float) StoryCloudConfig.topFade, 0f, 0.95f, 0, "%",
                v -> StoryCloudConfig.topFade = v, "Fades out the top of the clouds.");
        slider("Bumpiness", (float) StoryCloudConfig.heightVariation, 0f, 1f, 0, "%",
                v -> StoryCloudConfig.heightVariation = v, "Makes some puffs taller than others.");
        slider("Ragged Edges", (float) StoryCloudConfig.detail, 0f, 0.9f, 0, "%",
                v -> StoryCloudConfig.detail = v, "Breaks up straight cloud edges.");
        toggle("Pastel Clouds", () -> StoryCloudConfig.pastel, v -> StoryCloudConfig.pastel = v,
                "Soft see-through cartoon clouds that take on the sky's colour. Overrides Cloud Lighting.");
        slider("Soft Edges", (float) StoryCloudConfig.softEdges, 0f, 3f, 2, "x",
                v -> StoryCloudConfig.softEdges = v, "Blurs cloud outlines into the sky. 0 keeps them sharp.");
    }

    private void worldWeather() {
        slider("Rain Fog", (float) DynamicFogHandler.rainFogDensity, 0.01f, 0.5f, 2, "",
                v -> DynamicFogHandler.rainFogDensity = v, "How much rain closes in your view.");
        slider("Storm Fog Darkening", (float) DynamicFogHandler.weatherFogDarken, 0f, 2f, 2, "x",
                v -> DynamicFogHandler.weatherFogDarken = v, "How gloomy the fog gets in a thunderstorm.");
        slider("Storm Sky Darkening", (float) DynamicFogHandler.weatherSkyDarken, 0f, 1f, 0, "%",
                v -> DynamicFogHandler.weatherSkyDarken = v, "How dark the sky goes in a thunderstorm.");
        slider("Storm Cloud Darkening", (float) DynamicFogHandler.weatherCloudDarken, 0f, 1f, 0, "%",
                v -> DynamicFogHandler.weatherCloudDarken = v, "How dark the clouds go in a thunderstorm.");
        toggle("Tint While Wet", () -> FxConfig.weatherTintEnabled, v -> FxConfig.weatherTintEnabled = v,
                "Washes the screen with colour while it rains or snows.");
        slider("Tint Strength", (float) FxConfig.weatherTintStrength, 0f, 1f, 0, "%",
                v -> FxConfig.weatherTintStrength = v, "How strong that wash is.");
        color("Rain Colour", () -> FxConfig.rainTintHex, v -> FxConfig.rainTintHex = v, false, 0x8AA8CC,
                "The colour used while it rains.");
        color("Snow Colour", () -> FxConfig.snowTintHex, v -> FxConfig.snowTintHex = v, false, 0xD8E4F0,
                "The colour used while it snows.");
        toggle("Rainbows", () -> FxConfig.rainbowsEnabled, v -> FxConfig.rainbowsEnabled = v,
                "A rainbow can open opposite the sun as rain clears in daylight.");
        slider("Rainbow Chance", (float) FxConfig.rainbowChance, 0f, 1f, 0, "%",
                v -> FxConfig.rainbowChance = v, "How often a clearing shower leaves one behind.");
        slider("Rainbow Brightness", (float) FxConfig.rainbowBrightness, 0f, 2f, 2, "x",
                v -> FxConfig.rainbowBrightness = v, "How vivid the bow is.");
        toggle("Lightning Flash", () -> FxConfig.lightningFlash, v -> FxConfig.lightningFlash = v,
                "Strikes flicker the sky, clouds and daylight, brightest toward the bolt. Respects Hide Lightning Flashes.");
        slider("Flash Strength", (float) FxConfig.lightningFlashStrength, 0f, 2f, 2, "x",
                v -> FxConfig.lightningFlashStrength = v, "How bright each flicker is.");
        toggle("Puddles", () -> FxConfig.puddles, v -> FxConfig.puddles = v,
                "Rain gathers into shiny puddles on open ground, with ripples while it pours.");
        toggle("Rain Splashes", () -> FxConfig.rainSplashes, v -> FxConfig.rainSplashes = v,
                "Little splashes burst where raindrops land.");
        slider("Puddle Amount", (float) FxConfig.puddleAmount, 0f, 2f, 2, "x",
                v -> FxConfig.puddleAmount = v, "How much ground the puddles cover, and how many splashes.");
    }

    private void worldStorms() {
        toggle("Custom Rain", () -> FxConfig.stormRain, v -> FxConfig.stormRain = v,
                "Angled rain streaks that stop at roofs, drip through leaves and splash where they land. Replaces the vanilla rain sheets.");
        slider("Rain Amount", (float) FxConfig.stormRainAmount, 0f, 2f, 2, "x",
                v -> FxConfig.stormRainAmount = v, "How heavy the rain falls.");
        toggle("Raindrops On Screen", () -> FxConfig.lensDrops, v -> FxConfig.lensDrops = v,
                "Drops land on the screen when you look up into the rain, then run and dry off.");
        slider("Screen Drop Amount", (float) FxConfig.lensDropsAmount, 0f, 2f, 2, "x",
                v -> FxConfig.lensDropsAmount = v, "How many drops land on the screen.");
        toggle("Storm Sky", () -> FxConfig.stormClouds, v -> FxConfig.stormClouds = v,
                "A dark rolling cloud deck slides in with the rain, lit from inside by lightning, with flickers and far rumbles in thunderstorms.");
        slider("Storm Sky Strength", (float) FxConfig.stormCloudsStrength, 0f, 1f, 0, "%",
                v -> FxConfig.stormCloudsStrength = v, "How solid the storm clouds are.");
        toggle("Bright Bolts", () -> FxConfig.stormBolts, v -> FxConfig.stormBolts = v,
                "Lightning draws as a glowing white bolt with forks that pulses with each flash.");
        toggle("Wet Surfaces", () -> FxConfig.wetSurfaces, v -> FxConfig.wetSurfaces = v,
                "Rain darkens open ground and walls and gives floors a soft sheen, drying out a few minutes after.");
        slider("Wetness", (float) FxConfig.wetSurfacesStrength, 0f, 2f, 2, "x",
                v -> FxConfig.wetSurfacesStrength = v, "How dark and shiny wet surfaces get.");
        toggle("Storm Wind", () -> FxConfig.stormWind, v -> FxConfig.stormWind = v,
                "Thunderstorms blow harder: steeper rain and more leaves tumbling past.");
        toggle("Blizzard", () -> FxConfig.blizzard, v -> FxConfig.blizzard = v,
                "Thunderstorms in snowy biomes turn into a whiteout: dense flakes driven sideways, pale fog and a quiet howl.");
        slider("Blizzard Strength", (float) FxConfig.blizzardStrength, 0f, 2f, 2, "x",
                v -> FxConfig.blizzardStrength = v, "How thick the flakes and the whiteout get.");
        toggle("Sandstorm", () -> FxConfig.sandstorm, v -> FxConfig.sandstorm = v,
                "Rain over deserts and other dry, warm biomes becomes a sandstorm: beige fog, streams of sand and drifting dust.");
        slider("Sandstorm Strength", (float) FxConfig.sandstormStrength, 0f, 2f, 2, "x",
                v -> FxConfig.sandstormStrength = v, "How thick the sand and the fog get.");
        toggle("Distant Storms", () -> FxConfig.stormRange, v -> FxConfig.stormRange = v,
                "Heavy rain, sandstorms and blizzards also fill the middle and far distance with larger, softer particles and drifting cards.");
    }

    private void worldLight() {
        toggle("Coloured Light", () -> FxConfig.lightTintEnabled, v -> FxConfig.lightTintEnabled = v,
                "Gives torchlight and moonlight a colour instead of plain grey.");
        color("Torchlight Colour", () -> FxConfig.blockLightHex, v -> FxConfig.blockLightHex = v, false, 0xFFD8AC,
                "The colour of light from torches, lanterns and lava.");
        slider("Torchlight Strength", (float) FxConfig.blockLightStrength, 0f, 1f, 0, "%",
                v -> FxConfig.blockLightStrength = v, "How much of that colour shows.");
        color("Moonlight Colour", () -> FxConfig.skyLightNightHex, v -> FxConfig.skyLightNightHex = v, false, 0x7A7AFF,
                "The colour of light from the night sky.");
        slider("Moonlight Strength", (float) FxConfig.skyLightStrength, 0f, 1f, 0, "%",
                v -> FxConfig.skyLightStrength = v, "How much of that colour shows.");
        toggle("Sunrise Light", () -> FxConfig.sunLightTint, v -> FxConfig.sunLightTint = v,
                "Warms everything the sky lights while the sun sits on the horizon.");
        color("Sunrise Light Colour", () -> FxConfig.sunLightHex, v -> FxConfig.sunLightHex = v, false, 0xFFB775,
                "The colour of that low sunlight.");
        slider("Sunrise Light Strength", (float) FxConfig.sunLightStrength, 0f, 1f, 0, "%",
                v -> FxConfig.sunLightStrength = v, "How much of that colour shows.");
        toggle("Colour Grade", () -> FxConfig.gradeEnabled, v -> FxConfig.gradeEnabled = v,
                "One gentle pass over the whole picture. Skipped while a shader pack runs.");
        slider("Saturation", (float) FxConfig.gradeSaturation, 0f, 2f, 2, "x",
                v -> FxConfig.gradeSaturation = v, "How colourful everything is. 1 leaves it alone.");
        slider("Vibrance", (float) FxConfig.gradeVibrance, 0f, 1f, 0, "%",
                v -> FxConfig.gradeVibrance = v, "Lifts dull colours and leaves already vivid ones alone, so the world feels lively without turning neon.");
        slider("Soft Contrast", (float) FxConfig.gradeContrast, 0f, 1f, 0, "%",
                v -> FxConfig.gradeContrast = v, "Deepens shadows and lifts highlights a little.");
        slider("Warmth", (float) FxConfig.gradeWarmth, -1f, 1f, 2, "",
                v -> FxConfig.gradeWarmth = v, "Warmer above 0, cooler below.");
        slider("Vignette", (float) FxConfig.gradeVignette, 0f, 1f, 0, "%",
                v -> FxConfig.gradeVignette = v, "Darkens the edges of the screen.");
        slider("Dark Areas Fade", (float) FxConfig.gradeDarkDesaturation, 0f, 1f, 0, "%",
                v -> FxConfig.gradeDarkDesaturation = v, "Drains colour from the darkest parts of the picture while you stand in the dark.");
        slider("Darkness Vignette", (float) FxConfig.gradeDimVignette, 0f, 1f, 0, "%",
                v -> FxConfig.gradeDimVignette = v, "Extra vignette while you stand in the dark. A torch nearby clears it.");
        slider("Darkness Colour Loss", (float) FxConfig.gradeDimFade, 0f, 1f, 0, "%",
                v -> FxConfig.gradeDimFade = v, "The whole picture greys while you stand in the dark. A torch nearby brings colour back.");
    }

    private void worldAir() {
        toggle("Floating Bits", () -> FxConfig.mistEnabled, v -> FxConfig.mistEnabled = v,
                "Little specks drifting through the air around you.");
        slider("How Many Bits", (float) FxConfig.mistRate, 0f, 4f, 2, "x", v -> FxConfig.mistRate = v,
                "More means a busier, dustier feel.");
        slider("How Close", (float) FxConfig.mistRadius, 4f, 48f, 0, " blocks",
                v -> FxConfig.mistRadius = v, "How far from you they can appear.");
        slider("Per Tick Limit", (float) FxConfig.mistPerTick, 0f, 32f, 0, "",
                v -> FxConfig.mistPerTick = v, "Cap on new specks each tick. Lower is lighter on your PC.");
        slider("Wind", (float) FxConfig.windStrength, 0f, 4f, 2, "x",
                v -> FxConfig.windStrength = v, "How hard the specks get blown sideways.");
        toggle("Heat Haze", () -> FxConfig.heatHaze, v -> FxConfig.heatHaze = v,
                "Deserts shimmer in the distance on clear days, the air above lava wavers, the Nether does both.");
        slider("Haze Strength", (float) FxConfig.heatHazeStrength, 0f, 2f, 2, "x",
                v -> FxConfig.heatHazeStrength = v, "How far the shimmer bends the view.");
        toggle("Cold Air", () -> FxConfig.coldAir, v -> FxConfig.coldAir = v,
                "Snowy places bend the light at the edges of your view, more while it snows.");
        slider("Cold Strength", (float) FxConfig.coldAirStrength, 0f, 2f, 2, "x",
                v -> FxConfig.coldAirStrength = v, "How strong that bending is.");
        toggle("Cave Dust", () -> FxConfig.caveDust, v -> FxConfig.caveDust = v,
                "Underground, fine dust drifts through the air wherever torchlight reaches.");
        toggle("Crystal Sparkle", () -> FxConfig.caveSparkle, v -> FxConfig.caveSparkle = v,
                "Amethyst glints in the dark and sculk breathes out faint teal motes.");
        slider("Cave Particle Amount", (float) FxConfig.caveDustAmount, 0f, 3f, 2, "x",
                v -> FxConfig.caveDustAmount = v, "How much dust and sparkle there is.");
        toggle("Lava Life", () -> FxConfig.lavaLife, v -> FxConfig.lavaLife = v,
                "Open lava throws embers and ash, pops and spits, and steams where it meets water.");
        slider("Lava Life Amount", (float) FxConfig.lavaLifeAmount, 0f, 3f, 2, "x",
                v -> FxConfig.lavaLifeAmount = v, "How busy the lava is.");
        toggle("Deep Dark Haze", () -> FxConfig.deepDarkHaze, v -> FxConfig.deepDarkHaze = v,
                "The deep dark and the air round sculk fill with a cold dark-teal haze and slow teal motes, and it pulses when a sensor or shrieker goes off nearby.");
    }

    private void worldPlants() {
        toggle("Ground Litter", () -> FxConfig.groundLitter, v -> FxConfig.groundLitter = v,
                "Twigs and fallen leaves under trees, pebbles, petals, clover and tiny flowers in the open.");
        slider("Litter Amount", (float) FxConfig.groundLitterAmount, 0f, 2f, 2, "x",
                v -> FxConfig.groundLitterAmount = v, "How much lies on the ground.");
        toggle("Falling Leaves", () -> FxConfig.fallingLeaves, v -> FxConfig.fallingLeaves = v,
                "Leaves drift down from trees, tumble in the wind and settle on the ground or float on water.");
        slider("Leaf Amount", (float) FxConfig.fallingLeavesAmount, 0f, 3f, 2, "x",
                v -> FxConfig.fallingLeavesAmount = v, "How many leaves fall.");
        toggle("Litter Sounds", () -> FxConfig.litterSounds, v -> FxConfig.litterSounds = v,
                "Sticks, pebbles and leaves give a very faint rustle or clack when something moves them.");
        toggle("Grass Blades", () -> FxConfig.grassBlades, v -> FxConfig.grassBlades = v,
                "Thin grass blades on grass blocks near you that sway in the wind and bend as you walk through them.");
        slider("Blade Density", (float) FxConfig.grassBladesDensity, 0f, 1f, 0, "%",
                v -> FxConfig.grassBladesDensity = v, "How many blades grow on each grass block.");
        action(() -> "Blade Style", () -> BLADE_STYLES[Mth.clamp(FxConfig.grassBladesStyle, 0, 1)],
                () -> {
                    FxConfig.grassBladesStyle = (FxConfig.grassBladesStyle + 1) % BLADE_STYLES.length;
                    needsRebuild = true;
                }, "Short tufts, or tall swaying meadow ribbons.");
        toggle("Wind Sway", () -> FxConfig.windSway, v -> FxConfig.windSway = v,
                "Grass, flowers, crops and leaves move with the wind, harder in storms. Needs Embeddium.");
        slider("Sway Strength", (float) FxConfig.windSwayStrength, 0f, 2f, 2, "x",
                v -> FxConfig.windSwayStrength = v, "How far plants bend in the wind.");
        toggle("Ore Glow", () -> FxConfig.oreGlow, v -> {
            FxConfig.oreGlow = v;
            GlowTag.refresh();
        }, "Coloured ore specks glow softly in the dark and barely show in daylight. Needs Embeddium.");
        slider("Ore Glow Strength", (float) FxConfig.oreGlowStrength, 0f, 2f, 2, "x",
                v -> FxConfig.oreGlowStrength = v, "How brightly ore specks glow.");
        toggle("Stem Pulse", () -> FxConfig.stemPulse, v -> {
            FxConfig.stemPulse = v;
            GlowTag.refresh();
        }, "Veins on crimson and warped stems, wart blocks and shroomlight pulse slowly like breathing. Needs Embeddium.");
        toggle("Recolour Blocks", () -> DynamicFogHandler.biomeColorsEnabled, v -> {
            DynamicFogHandler.biomeColorsEnabled = v;
            vegDirty = true;
        }, "Lets biomes override the colour of grass, leaves and water.");
        slider("Plant Richness", (float) DynamicFogHandler.vegSaturation, 0f, 2f, 2, "x", v -> {
            DynamicFogHandler.vegSaturation = v;
            vegDirty = true;
        }, "Above 1 makes grass and leaves more colourful.");
        slider("Plant Brightness", (float) DynamicFogHandler.vegBrightness, 0f, 2f, 2, "x", v -> {
            DynamicFogHandler.vegBrightness = v;
            vegDirty = true;
        }, "Lightens or darkens grass and leaves.");
        slider("Sunny Foliage", (float) DynamicFogHandler.vegSunny, 0f, 1f, 0, "%", v -> {
            DynamicFogHandler.vegSunny = v;
            vegDirty = true;
        }, "Lifts grass and leaves toward a lighter, sunnier lime green.");
        slider("Patchy Greens", (float) DynamicFogHandler.vegVariation, 0f, 1f, 0, "%", v -> {
            DynamicFogHandler.vegVariation = v;
            vegDirty = true;
        }, "Grass and leaves drift between sunny and deep green patches across the land, like real meadows.");
    }

    private void worldWind() {
        toggle("Living Wind", () -> FxConfig.livingWind, v -> FxConfig.livingWind = v,
                "Calm, breezy and windy days, gust clusters and harder wind in storms. Plants, leaves, litter and rain all follow it.");
        toggle("Storm Front", () -> FxConfig.windFront, v -> FxConfig.windFront = v,
                "In single player the wind builds before rain arrives.");
        toggle("Altitude", () -> FxConfig.windAltitude, v -> FxConfig.windAltitude = v,
                "The higher you climb, the harder it blows.");
        toggle("Shelter", () -> FxConfig.windShelter, v -> FxConfig.windShelter = v,
                "Less wind indoors, in caves and behind walls.");
        toggle("Particle Wind", () -> FxConfig.windParticles, v -> FxConfig.windParticles = v,
                "Smoke, dust, ash and spores get carried along by the wind. Water and shelter stop it.");
        slider("Particle Push", (float) FxConfig.windParticleStrength, 0f, 3f, 2, "x",
                v -> FxConfig.windParticleStrength = v, "How far the wind carries them.");
        action(() -> "Particle Filter", () -> WIND_FILTERS[Mth.clamp(FxConfig.windParticleFilter, 0, 2)],
                () -> FxConfig.windParticleFilter = (FxConfig.windParticleFilter + 1) % WIND_FILTERS.length,
                "Which particles the wind may carry.");
        toggle("Wind Bits", () -> FxConfig.windBits, v -> FxConfig.windBits = v,
                "Tiny flecks and leaf and grass scraps tear off foliage in strong wind.");
        slider("Bits Amount", (float) FxConfig.windBitsAmount, 0f, 3f, 2, "x",
                v -> FxConfig.windBitsAmount = v, "How many bits fly.");
        toggle("Wind Streaks", () -> FxConfig.windStreaks, v -> FxConfig.windStreaks = v,
                "Faint lines sweep past in gusts.");
        toggle("Wind Sounds", () -> FxConfig.windSounds, v -> FxConfig.windSounds = v,
                "A soft wind that follows strength, place and shelter. Forests add leaf rustles.");
        slider("Wind Volume", (float) FxConfig.windSoundVolume, 0f, 2f, 2, "x",
                v -> FxConfig.windSoundVolume = v, "How loud the wind is.");
        toggle("Rustle Sounds", () -> FxConfig.rustleSounds, v -> FxConfig.rustleSounds = v,
                "Walking through grass, crops, vines and leaves makes a soft rustle.");
    }

    private void worldWildlife() {
        toggle("Butterflies", () -> FxConfig.butterflies, v -> FxConfig.butterflies = v,
                "Butterflies flutter around flowers on clear days.");
        slider("Butterfly Amount", (float) FxConfig.butterfliesAmount, 0f, 3f, 2, "x",
                v -> FxConfig.butterfliesAmount = v, "How many butterflies visit the flowers.");
        toggle("Birds", () -> FxConfig.birds, v -> FxConfig.birds = v,
                "Flocks of birds cross the sky by day, and songbirds burst out of trees as you walk past.");
        slider("Bird Amount", (float) FxConfig.birdsAmount, 0f, 3f, 2, "x",
                v -> FxConfig.birdsAmount = v, "How often flocks fly over and birds leave the trees.");
        toggle("Bat Swarms", () -> FxConfig.batSwarm, v -> FxConfig.batSwarm = v,
                "Now and then at dusk a cave mouth near you pours out a swarm of bats that scatter into the sky.");
        toggle("Fireflies", () -> FxConfig.fireflies, v -> FxConfig.fireflies = v,
                "Blinking fireflies drift over grass on warm, clear nights.");
        slider("Firefly Amount", (float) FxConfig.firefliesAmount, 0f, 3f, 2, "x",
                v -> FxConfig.firefliesAmount = v, "How many fireflies come out at night.");
        toggle("Breath Clouds", () -> FxConfig.breathPuffs, v -> FxConfig.breathPuffs = v,
                "You and other creatures puff little clouds of breath in cold places.");
        toggle("Footstep Puffs", () -> FxConfig.footstepPuffs, v -> FxConfig.footstepPuffs = v,
                "Steps kick up dust on sand and dirt, powder on snow and splashes in shallow water.");
        slider("Footstep Amount", (float) FxConfig.footstepPuffsAmount, 0f, 2f, 2, "x",
                v -> FxConfig.footstepPuffsAmount = v, "How much each step kicks up.");
        toggle("Tumbleweeds", () -> FxConfig.tumbleweeds, v -> FxConfig.tumbleweeds = v,
                "Tumbleweeds roll across deserts, badlands and savannas, and by the dozen in sandstorms.");
    }

    private void worldWater() {
        toggle("Sky Reflection", () -> FxConfig.skyReflection, v -> FxConfig.skyReflection = v,
                "Water picks up the sky's colour, more strongly the flatter you look across it.");
        slider("Reflection Strength", (float) FxConfig.skyReflectionStrength, 0f, 2f, 2, "x",
                v -> FxConfig.skyReflectionStrength = v, "How much sky colour the water takes on.");
        toggle("Water Reflections", () -> FxConfig.waterReflections, v -> FxConfig.waterReflections = v,
                "Calm water mirrors the trees, hills and sky around it. Skipped while a shader pack runs.");
        slider("Mirror Strength", (float) FxConfig.waterReflectionStrength, 0f, 2f, 2, "x",
                v -> FxConfig.waterReflectionStrength = v, "How clear the reflections are.");
        toggle("Jumping Fish", () -> FxConfig.fishJumps, v -> FxConfig.fishJumps = v,
                "Fish now and then leap out of lakes and oceans with a splash.");
        slider("Fish Amount", (float) FxConfig.fishJumpsAmount, 0f, 3f, 2, "x",
                v -> FxConfig.fishJumpsAmount = v, "How often fish jump.");
        toggle("Water Sparkles", () -> FxConfig.waterSparkles, v -> FxConfig.waterSparkles = v,
                "Pixel sparkles twinkle on moonlit water at night, thickest on the path toward the moon.");
        slider("Sparkle Amount", (float) FxConfig.waterSparklesAmount, 0f, 3f, 2, "x",
                v -> FxConfig.waterSparklesAmount = v, "How many sparkles dance on the water.");
        toggle("Water Wake", () -> FxConfig.waterWake, v -> FxConfig.waterWake = v,
                "Boats, swimmers and mobs push real waves across the water, in a V behind a moving boat.");
        slider("Water Wake Strength", (float) FxConfig.waterWakeStrength, 0f, 2f, 2, "x",
                v -> FxConfig.waterWakeStrength = v, "How bright the wake foam is.");
        toggle("Water Ripples", () -> FxConfig.ripples, v -> FxConfig.ripples = v,
                "Pixel rings spread across the water where rain and leaves land, and things that fall in splash with spray.");
        slider("Ripple Amount", (float) FxConfig.ripplesAmount, 0f, 3f, 2, "x",
                v -> FxConfig.ripplesAmount = v, "How many ripples appear.");
        toggle("Rain Rings", () -> FxConfig.rainRings, v -> FxConfig.rainRings = v,
                "Small pixel rings pop up where raindrops land, across all the water you can see. Needs Embeddium.");
        slider("Rain Ring Amount", (float) FxConfig.rainRingsAmount, 0f, 3f, 2, "x",
                v -> FxConfig.rainRingsAmount = v, "How many rain rings appear.");
        toggle("Waterfall Foam", () -> FxConfig.waterfallFoam, v -> FxConfig.waterfallFoam = v,
                "Waterfalls churn white foam where they land on water or ground, throwing spray and drifting soft mist.");
        slider("Waterfall Foam Amount", (float) FxConfig.waterfallFoamAmount, 0f, 2f, 2, "x",
                v -> FxConfig.waterfallFoamAmount = v, "How much spray, mist and foam streaking waterfalls make.");
        toggle("Shore Foam", () -> FxConfig.shoreFoam, v -> FxConfig.shoreFoam = v,
                "Turquoise shallows and lapping white foam where water meets the shore.");
        slider("Shore Foam Strength", (float) FxConfig.shoreFoamStrength, 0f, 2f, 2, "x",
                v -> FxConfig.shoreFoamStrength = v, "How strong the foam and shallow tint are.");
        toggle("Underwater Light Beams", () -> FxConfig.waterRays, v -> FxConfig.waterRays = v,
                "Swaying shafts of sunlight slant down through the water when you swim in daylight.");
        slider("Beam Brightness", (float) FxConfig.waterRaysStrength, 0f, 2f, 2, "x",
                v -> FxConfig.waterRaysStrength = v, "How bright the underwater beams are.");
        toggle("Caustics", () -> FxConfig.caustics, v -> FxConfig.caustics = v,
                "Rippling sunlight patterns dance on the seabed, and fine motes drift in the water around you.");
        slider("Caustics Brightness", (float) FxConfig.causticsStrength, 0f, 2f, 2, "x",
                v -> FxConfig.causticsStrength = v, "How bright the seabed light patterns are.");
        toggle("Underwater Bubbles", () -> FxConfig.underwaterBubbles, v -> FxConfig.underwaterBubbles = v,
                "Streams of bubbles rise from the seabed, thickest round kelp, seagrass, magma and soul sand, wobbling as they climb and popping at the surface.");
        slider("Bubble Amount", (float) FxConfig.underwaterBubblesAmount, 0f, 3f, 2, "x",
                v -> FxConfig.underwaterBubblesAmount = v, "How many bubble streams there are.");
        toggle("Deep Water", () -> FxConfig.deepWater, v -> FxConfig.deepWater = v,
                "The deeper you swim, the darker and closer the water gets.");
        slider("Deep Water Darkness", (float) FxConfig.deepWaterDarkness, 0f, 1.5f, 2, "x",
                v -> FxConfig.deepWaterDarkness = v, "How dark the deep water gets.");
        toggle("Marine Snow", () -> FxConfig.marineSnow, v -> FxConfig.marineSnow = v,
                "Slow pale specks sink through the water around you, more of them the deeper you go.");
        slider("Marine Snow Amount", (float) FxConfig.marineSnowAmount, 0f, 3f, 2, "x",
                v -> FxConfig.marineSnowAmount = v, "How many specks sink around you.");
        toggle("Deep Sounds", () -> FxConfig.deepSounds, v -> FxConfig.deepSounds = v,
                "Every minute or two a low distant groan when you are more than 20 blocks under water.");
        slider("Deep Sounds Volume", (float) FxConfig.deepSoundsVolume, 0f, 2f, 2, "x",
                v -> FxConfig.deepSoundsVolume = v, "How loud the deep groans are.");
        slider("Water Richness", (float) DynamicFogHandler.waterSaturation, 0f, 2f, 2, "x", v -> {
            DynamicFogHandler.waterSaturation = v;
            vegDirty = true;
        }, "Above 1 makes water more colourful.");
        slider("Water Brightness", (float) DynamicFogHandler.waterBrightness, 0f, 2f, 2, "x", v -> {
            DynamicFogHandler.waterBrightness = v;
            vegDirty = true;
        }, "Lightens or darkens water.");
    }

    private void worldGlow() {
        toggle("Glow", () -> FxConfig.bloomEnabled, v -> FxConfig.bloomEnabled = v,
                "Bright things bleed soft light over everything around them. Skipped while a shader pack runs.");
        slider("Glow Strength", (float) FxConfig.bloomStrength, 0f, 2f, 2, "x",
                v -> FxConfig.bloomStrength = v, "How much light spills out.");
        slider("Glow Threshold", (float) FxConfig.bloomThreshold, 0f, 1f, 0, "%",
                v -> FxConfig.bloomThreshold = v, "How bright something must be before it glows. Lower and the sky and snow join in.");
        slider("Glow Spread", (float) FxConfig.bloomSpread, 0f, 0.9f, 0, "%",
                v -> FxConfig.bloomSpread = v, "Tight halos at the low end, a wide dreamy haze at the high end.");
        toggle("Ambient Occlusion", () -> FxConfig.ambientOcclusion, v -> FxConfig.ambientOcclusion = v,
                "Soft shade in corners, under overhangs and where things touch the ground. Skipped while a shader pack runs.");
        slider("Occlusion Strength", (float) FxConfig.ambientOcclusionStrength, 0f, 1.5f, 2, "x",
                v -> FxConfig.ambientOcclusionStrength = v, "How dark the shade gets.");
        toggle("Sun Shadows", () -> FxConfig.sunShadows, v -> FxConfig.sunShadows = v,
                "Trees, walls and mobs near you cast soft shadows away from the sun. Skipped while a shader pack runs.");
        slider("Shadow Strength", (float) FxConfig.sunShadowStrength, 0f, 1.5f, 2, "x",
                v -> FxConfig.sunShadowStrength = v, "How dark the shadows get.");
    }

    private void worldNether() {
        toggle("Light Shafts", () -> FxConfig.netherRays, v -> FxConfig.netherRays = v,
                "Soft pixel light streaks hang far off in the Nether haze, tinted by the biome. Nothing near you.");
        slider("Shaft Strength", (float) FxConfig.netherRaysStrength, 0f, 2f, 2, "x",
                v -> FxConfig.netherRaysStrength = v, "How bright the light shafts are.");
        toggle("Lava Mist", () -> FxConfig.netherMist, v -> FxConfig.netherMist = v,
                "A glowing haze lies low over the lava seas and drifts on the hot air.");
        slider("Mist Density", (float) FxConfig.netherMistDensity, 0f, 2f, 2, "x",
                v -> FxConfig.netherMistDensity = v, "How thick the glowing mist is.");
        toggle("Embers And Ash", () -> FxConfig.netherDust, v -> FxConfig.netherDust = v,
                "Glowing embers float up and hot ash flakes tumble down, coloured by the biome.");
        slider("Ember Amount", (float) FxConfig.netherDustAmount, 0f, 3f, 2, "x",
                v -> FxConfig.netherDustAmount = v, "How many embers and ash flakes fill the air.");
        toggle("Nether Storms", () -> FxConfig.netherStorms, v -> FxConfig.netherStorms = v,
                "Now and then a fire-dust storm sweeps through: thick fog, wind-driven ash and dust across the whole landscape, gust surges and a howling wind.");
        slider("Storm Frequency", (float) FxConfig.netherStormRate, 0f, 3f, 2, "x",
                v -> FxConfig.netherStormRate = v, "How often storms come. 0 means never.");
        toggle("Lava Surface", () -> FxConfig.lavaCrust, v -> FxConfig.lavaCrust = v,
                "The lava surface becomes a bright, flat mosaic of glowing texels with sparkling glints. Off keeps the vanilla lava texture.");
        slider("Surface Amount", (float) FxConfig.lavaCrustAmount, 0f, 2f, 2, "x",
                v -> FxConfig.lavaCrustAmount = v, "How strongly the glowing mosaic covers the lava.");
        slider("Storm Strength", (float) FxConfig.netherStormStrength, 0.3f, 2f, 2, "x",
                v -> FxConfig.netherStormStrength = v, "How close the storm fog closes in and how much dust the wind carries.");
        toggle("Lava Particles", () -> FxConfig.lavaParticles, v -> FxConfig.lavaParticles = v,
                "Hand-drawn pixel pops, splats, glowing orbs, ripples and smoke wisps replace the vanilla lava particles.");
        slider("Lava Particle Amount", (float) FxConfig.lavaParticlesAmount, 0f, 2f, 2, "x",
                v -> FxConfig.lavaParticlesAmount = v, "How busy the lava surface is. Big lava lakes erupt far more than small pools.");
        toggle("Lava Eruptions", () -> FxConfig.lavaEruptions, v -> FxConfig.lavaEruptions = v,
                "Now and then a lava pool near you bursts and throws glowing blobs that arc, trail embers and splat back.");
        toggle("Nether Portals", () -> FxConfig.netherPortalFx, v -> FxConfig.netherPortalFx = v,
                "Lit Nether portals glow violet and swirl like a pixel whirlpool, with motes spiralling in. Lighting one flashes and bursts, breaking one sheds falling shards. Vanilla portal specks next to them are replaced.");
    }

    private void worldEnd() {
        toggle("End Sky", () -> FxConfig.endSky, v -> FxConfig.endSky = v,
                "A drifting violet nebula full of twinkling stars replaces the flat End sky, and the End's fog takes on its colour.");
        toggle("End Dust", () -> FxConfig.endDust, v -> FxConfig.endDust = v,
                "Glowing motes drift and sparkle in the air of the End.");
        slider("Dust Amount", (float) FxConfig.endDustAmount, 0f, 3f, 2, "x",
                v -> FxConfig.endDustAmount = v, "How many glowing motes fill the air.");
        toggle("End Mist", () -> FxConfig.endMist, v -> FxConfig.endMist = v,
                "Pale lavender mist drifts over the End islands and a deep purple haze fills the void below them.");
        slider("Mist Amount", (float) FxConfig.endMistAmount, 0f, 2f, 2, "x",
                v -> FxConfig.endMistAmount = v, "How thick the End mist and void haze are.");
        toggle("End Shadows", () -> FxConfig.endShadows, v -> FxConfig.endShadows = v,
                "Huge soft violet shadows slide slowly across the End islands, as if something enormous drifts overhead.");
        slider("Shadow Amount", (float) FxConfig.endShadowAmount, 0f, 2f, 2, "x",
                v -> FxConfig.endShadowAmount = v, "How dark the drifting shadows are.");
        toggle("Portal Glow", () -> FxConfig.portalGlow, v -> FxConfig.portalGlow = v,
                "Light rises from open End portals with fireflies round them, and each eye you place flashes and throws up sparks.");
        toggle("End Fog", () -> FxConfig.endFog, v -> FxConfig.endFog = v,
                "A lilac haze thickens with distance in every End biome, so far islands fade into purple. The void below turns deep purple, not black.");
        slider("Fog Thickness", (float) FxConfig.endFogDensity, 0.3f, 2f, 2, "x",
                v -> FxConfig.endFogDensity = v, "Higher pulls the haze in closer.");
        toggle("End Portal Air", () -> FxConfig.endPortalFx, v -> FxConfig.endPortalFx = v,
                "Soft star motes rise inside a faint teal-violet light column over an open End portal, and a small sparkle goes up when an eye is placed.");
    }

    private void presetsPage() {
        if (presets.isEmpty()) presets = PresetManager.list();

        editBox(presetNameBox);
        button("Save As Preset", () -> {
            String name = PresetManager.sanitize(presetNameBox.getValue());
            if (name.isEmpty()) {
                status = "Type a name first";
                return;
            }
            if (PresetManager.save(name)) {
                presets = PresetManager.list();
                selectedPreset = name;
                presetNameBox.setValue("");
                status = "Saved " + name;
                ColorPickerScreen.playUi(SoundEvents.NOTE_BLOCK_BELL, 1.2f);
            } else {
                status = "Could not write that preset";
            }
            needsRebuild = true;
        }, "Writes every setting and every tuned biome into the preset folder.");

        toggle("Show Effect Report", () -> FxConfig.debugOverlay, v -> FxConfig.debugOverlay = v,
                "Lists every visual effect and whether it actually drew this frame. "
                        + "Randomize Everything leaves this one alone.");

        rows.add("Feeling lucky??");
        button(confirmRandom ? "Sure? Your settings will be gone" : "Randomize Everything", () -> {
            if (!confirmRandom) {
                confirmRandom = true;
                needsRebuild = true;
                return;
            }
            confirmRandom = false;
            randomizeEverything();
        }, "Assigns a random value at every single setting. Whatever you have tuned is overwritten as well, "
                + "so save a preset first if you want it back.");
        rows.add("Save a preset first, this overwrites EVERYTHING.");

        rows.add(presets.isEmpty() ? "No presets saved yet" : "Saved presets");
        for (String name : presets) {
            addRow(new ListRow(contentX, rowsTop, contentW, ROW_H - 2, name,
                    () -> name.equals(selectedPreset), () -> {
                selectedPreset = name;
                ColorPickerScreen.playUi(SoundEvents.NOTE_BLOCK_HAT, 1.4f);
                needsRebuild = true;
            }), "Click to pick this preset.");
        }

        if (selectedPreset != null) {
            button("Load " + selectedPreset, () -> {
                boolean ok = PresetManager.apply(selectedPreset);
                status = ok ? "Loaded " + selectedPreset : "Could not read that preset";
                if (ok) ColorPickerScreen.playUi(SoundEvents.NOTE_BLOCK_BELL, 1.4f);
                needsRebuild = true;
            }, "Replaces everything with what this preset holds.");
            button("Delete " + selectedPreset, () -> {
                if (PresetManager.delete(selectedPreset)) {
                    status = "Deleted " + selectedPreset;
                    selectedPreset = null;
                    presets = PresetManager.list();
                } else {
                    status = "Could not delete that preset";
                }
                needsRebuild = true;
            }, "Removes the preset folder from disk.");
        }

        button("Reload Folder", () -> {
            presets = PresetManager.list();
            status = "Read the preset folder again";
            needsRebuild = true;
        }, PresetManager.folder().toString());
    }

    private void randomizeEverything() {
        Random rng = new Random();
        Map<String, Object> defaults = defaults();
        for (String[] group : TAB_FIELDS) {
            for (String field : group) {
                if (NEVER_RANDOM.contains(field)) continue;
                Object def = defaults.get(field);
                if (def instanceof Boolean) {
                    write(field, rng.nextBoolean());
                } else if (def instanceof Integer value) {
                    write(field, field.endsWith("Hex")
                            ? rng.nextInt(0x1000000)
                            : (int) Math.max(1L, Math.round(value * spread(rng))));
                } else if (def instanceof Double value) {
                    write(field, value * spread(rng));
                }
            }
        }

        markEdited();
        working.fogHex = rng.nextInt(0x1000000);
        working.skyHex = rng.nextInt(0x1000000);
        working.hazeHex = rng.nextInt(0x1000000);
        working.dawnDuskHex = rng.nextInt(0x1000000);
        working.nightHex = rng.nextInt(0x1000000);
        working.rainFogHex = rng.nextInt(0x1000000);
        working.thunderFogHex = rng.nextInt(0x1000000);
        working.waterFogHex = rng.nextInt(0x1000000);
        working.fogStart = 2f + rng.nextFloat() * 60f;
        working.fogEnd = working.fogStart + 8f + rng.nextFloat() * 160f;
        working.mistParticle = PARTICLES[rng.nextInt(PARTICLES.length)][0];

        DynamicFogHandler.masterEnabled = true;
        DynamicFogHandler.dynamicFogEnabled = true;

        status = "Randomized. Undo All puts it back.";
        afterBulkChange();
    }

    private static double spread(Random rng) {
        return 0.4D + rng.nextDouble() * 1.8D;
    }

    private void copyPage() {
        if (allBiomes.isEmpty() && minecraft != null && minecraft.level != null) {
            minecraft.level.registryAccess().registryOrThrow(Registries.BIOME).keySet().stream()
                    .sorted()
                    .forEach(allBiomes::add);
        }

        rows.add("What to copy across");
        addRow(new ToggleRow(contentX, rowsTop, contentW, ROW_H - 2, "Fog colours",
                () -> copyFogColors, v -> copyFogColors = v), "Fog, sunset and night colours.");
        addRow(new ToggleRow(contentX, rowsTop, contentW, ROW_H - 2, "Fog distance",
                () -> copyFogDistance, v -> copyFogDistance = v), "How near and far the fog sits.");
        addRow(new ToggleRow(contentX, rowsTop, contentW, ROW_H - 2, "Sky and air",
                () -> copySkyAndAir, v -> copySkyAndAir = v), "Sky colour, horizon glow, floating bits, water.");
        addRow(new ToggleRow(contentX, rowsTop, contentW, ROW_H - 2, "Block colours",
                () -> copyBlockColors, v -> copyBlockColors = v), "Grass, leaf and water colours.");
        button("Copy To " + copyPicked.size() + (copyPicked.size() == 1 ? " Biome" : " Biomes"),
                this::applyCopy, "Paste the ticked groups onto every ticked biome below.");

        rows.add("Tick the biomes to paste onto");
        editBox(biomeFilterBox);
        button(copyPicked.isEmpty() ? "Tick Everything Shown" : "Untick Everything", () -> {
            if (copyPicked.isEmpty()) copyPicked.addAll(shownBiomes());
            else copyPicked.clear();
            needsRebuild = true;
        }, "Quickly tick or untick the whole list.");

        for (ResourceLocation id : shownBiomes()) {
            addRow(new ListRow(contentX, rowsTop, contentW, ROW_H - 2, friendly(id),
                    () -> copyPicked.contains(id), () -> {
                if (!copyPicked.remove(id)) copyPicked.add(id);
                needsRebuild = true;
            }), id.toString());
        }
    }

    private void resetEverythingPage() {
        rows.add("Start over");
        button(confirmWipe ? "Click again to wipe everything" : "Reset Everything To Default", () -> {
            if (!confirmWipe) {
                confirmWipe = true;
                needsRebuild = true;
                return;
            }
            confirmWipe = false;
            DynamicFogHandler.resetDefaults();
            AtmosphereConfigManager.loadDefaults();
            working.copyFrom(new AtmosphereSettings());
            biomeCleared = true;
            status = "Everything is back to default";
            afterBulkChange();
        }, "Puts every setting and every tuned biome back the way the mod ships.");
        rows.add("Biomes go back to the built-in vanilla looks");
    }

    private List<ResourceLocation> shownBiomes() {
        String needle = biomeFilterBox.getValue().toLowerCase(Locale.ROOT).trim();
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : allBiomes) {
            if (id.equals(biomeId)) continue;
            if (needle.isEmpty() || id.toString().toLowerCase(Locale.ROOT).contains(needle)) out.add(id);
        }
        return out;
    }

    private void applyCopy() {
        AtmosphereSettings from = AtmosphereConfigManager.ATMOSPHERES.get(biomeId);
        if (from == null) {
            status = "This biome has nothing to copy yet";
            return;
        }
        if (copyPicked.isEmpty()) {
            status = "Tick at least one biome below";
            return;
        }
        if (!copyFogColors && !copyFogDistance && !copySkyAndAir && !copyBlockColors) {
            status = "Turn on at least one group to copy";
            return;
        }
        for (ResourceLocation id : copyPicked) {
            AtmosphereSettings to = AtmosphereConfigManager.ATMOSPHERES.computeIfAbsent(id, k -> new AtmosphereSettings());
            if (copyFogColors) {
                to.fogHex = from.fogHex;
                to.rainFogHex = from.rainFogHex;
                to.thunderFogHex = from.thunderFogHex;
                to.dawnDuskHex = from.dawnDuskHex;
                to.nightHex = from.nightHex;
                to.ambientHex = from.ambientHex;
                to.satBoost = from.satBoost;
                to.brightness = from.brightness;
            }
            if (copyFogDistance) {
                to.fogStart = from.fogStart;
                to.fogEnd = from.fogEnd;
                to.fogDensity = from.fogDensity;
                to.nightFogStartMul = from.nightFogStartMul;
                to.nightFogEndMul = from.nightFogEndMul;
                to.nearHaze = from.nearHaze;
                to.heightFogStr = from.heightFogStr;
                to.voidFogStr = from.voidFogStr;
                to.sphereFog = from.sphereFog;
                to.rainMult = from.rainMult;
            }
            if (copySkyAndAir) {
                to.skyHex = from.skyHex;
                to.skyTintStr = from.skyTintStr;
                to.hazeHex = from.hazeHex;
                to.hazeStrength = from.hazeStrength;
                to.mistParticle = from.mistParticle;
                to.mistRate = from.mistRate;
                to.dawnDuskStr = from.dawnDuskStr;
                to.nightStr = from.nightStr;
                to.waterFogHex = from.waterFogHex;
                to.waterFogDist = from.waterFogDist;
            }
            if (copyBlockColors) {
                to.grassHex = from.grassHex;
                to.foliageHex = from.foliageHex;
                to.waterHex = from.waterHex;
            }
        }
        AtmosphereConfigManager.save();
        DynamicFogHandler.refreshBiomeColors();
        status = "Copied to " + copyPicked.size() + (copyPicked.size() == 1 ? " biome" : " biomes");
        ColorPickerScreen.playUi(SoundEvents.NOTE_BLOCK_BELL, 1.2f);
    }

    private void bslider(String label, float value, float min, float max, int decimals, String suffix,
                         Consumer<Float> on, String tip) {
        slider(label, value, min, max, decimals, suffix, v -> {
            markEdited();
            on.accept(v);
        }, tip);
    }

    private void bcolor(String label, IntSupplier get, IntConsumer set, boolean allowAuto, int fallback, String tip) {
        color(label, get, v -> {
            markEdited();
            set.accept(v);
        }, allowAuto, fallback, tip);
    }

    private void veg(String label, IntSupplier get, IntConsumer set, int fallback, String tip) {
        bcolor(label, get, v -> {
            set.accept(v);
            if (v >= 0) DynamicFogHandler.biomeColorsEnabled = true;
            AtmosphereConfigManager.refreshOverrideFlags();
            BiomeColorModifier.clearCache();
            vegDirty = true;
        }, true, fallback, tip);
    }

    private String particleValue() {
        String id = working.mistParticle == null ? "none" : working.mistParticle;
        for (String[] entry : PARTICLES) {
            if (entry[0].equalsIgnoreCase(id)) return entry[1];
        }
        return id;
    }

    private static String nextParticle(String current) {
        for (int i = 0; i < PARTICLES.length; i++) {
            if (PARTICLES[i][0].equalsIgnoreCase(current)) return PARTICLES[(i + 1) % PARTICLES.length][0];
        }
        return PARTICLES[0][0];
    }

    private static String friendly(ResourceLocation id) {
        String name = id.getPath().replace('_', ' ');
        return "minecraft".equals(id.getNamespace()) ? name : name + " (" + id.getNamespace() + ")";
    }

    private void markEdited() {
        confirmReset = false;
        if (!biomeCleared) return;
        biomeCleared = false;
        AtmosphereConfigManager.ATMOSPHERES.put(biomeId, working);
        AtmosphereConfigManager.refreshOverrideFlags();
        BiomeColorModifier.clearCache();
    }

    private void resetPage() {
        if (currentPage >= FIRST_TOOL_PAGE) {
            status = "Nothing to reset here";
            return;
        }
        if (!confirmReset) {
            confirmReset = true;
            needsRebuild = true;
            return;
        }
        confirmReset = false;
        if (currentPage < FIRST_WORLD_PAGE) {
            biomeCleared = true;
            AtmosphereConfigManager.ATMOSPHERES.remove(biomeId);
            working.copyFrom(new AtmosphereSettings());
            status = "This biome is back to default";
        } else {
            Map<String, Object> defaults = defaults();
            for (String field : TAB_FIELDS[currentPage - FIRST_WORLD_PAGE]) write(field, defaults.get(field));
            status = PAGE_TITLES[currentPage] + " is back to default";
        }
        afterBulkChange();
    }

    private void undoAll() {
        confirmReset = false;
        for (Map.Entry<String, Object> entry : openedWorld.entrySet()) write(entry.getKey(), entry.getValue());
        if (openedBiome != null) {
            biomeCleared = false;
            working.copyFrom(openedBiome);
            AtmosphereConfigManager.ATMOSPHERES.put(biomeId, working);
        } else {
            biomeCleared = true;
            AtmosphereConfigManager.ATMOSPHERES.remove(biomeId);
            working.copyFrom(new AtmosphereSettings());
        }
        status = "Back to how it was when you opened this";
        afterBulkChange();
    }

    private void afterBulkChange() {
        AtmosphereConfigManager.refreshOverrideFlags();
        BiomeColorModifier.clearCache();
        StoryClouds.invalidate();
        DynamicFogHandler.rebuildStars();
        DynamicFogHandler.refreshBiomeColors();
        starsDirty = false;
        vegDirty = false;
        needsRebuild = true;
        ColorPickerScreen.playUi(SoundEvents.NOTE_BLOCK_BASS, 0.9f);
    }

    private void remember() {
        lastPage = currentPage;
        lastScroll = scroll;
    }

    private void saveAndClose() {
        remember();
        if (biomeCleared) AtmosphereConfigManager.ATMOSPHERES.remove(biomeId);
        else AtmosphereConfigManager.ATMOSPHERES.put(biomeId, working.copy());
        AtmosphereConfigManager.save();
        DynamicFogHandler.save();
        if (starsDirty) {
            starsDirty = false;
            DynamicFogHandler.rebuildStars();
        }
        DynamicFogHandler.refreshBiomeColors();
        vegDirty = false;
        StoryClouds.invalidate();
        ColorPickerScreen.playUi(SoundEvents.NOTE_BLOCK_BELL, 1.2f);
        if (minecraft != null) minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        saveAndClose();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (railMaxScroll > 0 && mx >= railX && mx < railX + RAIL_W && my >= railTop && my <= railBottom) {
            int before = railScroll;
            railScroll = Mth.clamp(railScroll - (int) Math.signum(delta) * railRowH, 0, railMaxScroll);
            if (before != railScroll) needsRebuild = true;
            return true;
        }
        if (mx >= contentX - 6 && mx <= panelX + panelW && my >= rowsTop && my <= rowsBottom) {
            int before = scroll;
            scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, rows.size() - rowsVisible));
            if (before != scroll) {
                layoutRows();
                remember();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(@NonNull GuiGraphics g, int mx, int my, float pt) {
        AtmosphereUi.surface(g, panelX, panelY, panelW, panelH, AtmosphereUi.PANEL);
        AtmosphereUi.surface(g, railX, railTop, RAIL_W, railBottom - railTop, AtmosphereUi.RAIL);
        AtmosphereUi.inset(g, railX, railTop, RAIL_W, railBottom - railTop);
        AtmosphereUi.surface(g, contentX - 4, rowsTop - 3, contentW + 14, rowsBottom - rowsTop + 5,
                AtmosphereUi.WELL);
        AtmosphereUi.inset(g, contentX - 4, rowsTop - 3, contentW + 14, rowsBottom - rowsTop + 5);
        AtmosphereUi.bevel(g, panelX, panelY, panelW, panelH);

        AtmosphereUi.separator(g, panelX + 1, panelY + 24, panelW - 2);
        AtmosphereUi.separator(g, panelX + 1, footerTop, panelW - 2);

        g.drawString(font, title, panelX + 10, panelY + 8, AtmosphereUi.TEXT, true);
        String biome = friendly(biomeId) + (biomeCleared ? "  -  default" : "  -  tuned");
        String fitted = AtmosphereUi.fit(font, biome, panelW - 24 - font.width(title));
        g.drawString(font, fitted, panelX + panelW - 10 - font.width(fitted),
                panelY + 8, biomeCleared ? AtmosphereUi.TEXT_SOFT : AtmosphereUi.ON, true);

        for (int group = 0; group < GROUP_NAMES.length; group++) {
            int gy = groupY[group];
            if (gy < railTop || gy + GROUP_H > railBottom) continue;
            g.drawString(font, GROUP_NAMES[group], railX + 6, gy + 1, AtmosphereUi.GROUP, true);
            int ruleY = gy + 11;
            g.fill(railX + 5, ruleY, railX + RAIL_W - 5, ruleY + 1, 0xFF161A21);
            g.fill(railX + 5, ruleY + 1, railX + RAIL_W - 5, ruleY + 2, 0x66D8C089);
        }
        if (railMaxScroll > 0) {
            AtmosphereUi.scrollbar(g, railX + RAIL_W - 7, railTop + 1, railBottom - railTop - 2,
                    railBottom - railTop + railMaxScroll, railBottom - railTop, railScroll);
        }

        if (searching()) {
            g.drawString(font, "Matches across every page", contentX + 1, panelY + 54,
                    AtmosphereUi.GROUP, true);
        } else {
            g.drawString(font, PAGE_TITLES[currentPage], contentX + 1, panelY + 54,
                    AtmosphereUi.GROUP, true);
            if (currentPage < FIRST_TOOL_PAGE) {
                String state = pageModified[currentPage] ? "changed" : "default";
                g.drawString(font, state, contentX + contentW - font.width(state), panelY + 54,
                        pageModified[currentPage] ? AtmosphereUi.VALUE : AtmosphereUi.TEXT_SOFT, true);
            }
        }

        for (int i = scroll; i < Math.min(rows.size(), scroll + rowsVisible); i++) {
            if (!(rows.get(i) instanceof String header)) continue;
            int rowY = rowsTop + (i - scroll) * ROW_H;
            g.drawCenteredString(font, header, contentX + contentW / 2, rowY + 7, AtmosphereUi.GROUP);
        }

        if (rows.isEmpty()) {
            g.drawCenteredString(font, searching() ? "Nothing matches that." : "Nothing here.",
                    contentX + contentW / 2, rowsTop + 8, AtmosphereUi.TEXT_SOFT);
        }

        AtmosphereUi.scrollbar(g, panelX + panelW - 12, rowsTop, rowsVisible * ROW_H,
                rows.size(), rowsVisible, scroll);

        super.render(g, mx, my, pt);

        String line = notice();
        int color = AtmosphereUi.NOTICE;
        if (line == null) {
            line = status;
            color = AtmosphereUi.TEXT_SOFT;
        }
        if (!line.isEmpty()) {
            g.drawString(font, AtmosphereUi.fit(font, line, contentW), contentX + 1,
                    footerTop - 12, color, true);
        }
    }

    private String notice() {
        if (searching()) return null;
        if (DynamicFogHandler.masterEnabled && DynamicFogHandler.pausedBy() != null) {
            return DynamicFogHandler.running()
                    ? "A shader pack is on, so only biome colors and mist stay active."
                    : "Fast graphics is on, so every effect here is paused.";
        }
        if (currentPage == PAGE_CLOUDS || currentPage == PAGE_CLOUD_DETAIL) {
            if (anyLoaded("oculus", "iris")) return "A shader mod is on: it draws its own clouds.";
            if (anyLoaded("embeddium", "rubidium", "sodium")) {
                return "Embeddium owns the clouds: use Story Clouds.";
            }
        }
        if (currentPage == PAGE_SUN_MOON && anyLoaded("oculus", "iris", "linggango_tweaks")) {
            return "Another mod owns the sun and moon here.";
        }
        return null;
    }

    private static boolean anyLoaded(String... modIds) {
        ModList list;
        try { list = ModList.get(); } catch (Throwable ignored) { return false; }
        if (list == null) return false;
        for (String modId : modIds) {
            try { if (list.isLoaded(modId)) return true; } catch (Throwable ignored) {}
        }
        return false;
    }

    private static Map<String, Object> defaults() {
        if (cachedDefaults != null) return cachedDefaults;
        Map<String, Object> current = snapshot();
        DynamicFogHandler.resetDefaults();
        cachedDefaults = snapshot();
        for (Map.Entry<String, Object> entry : current.entrySet()) write(entry.getKey(), entry.getValue());
        return cachedDefaults;
    }

    private static Map<String, Object> snapshot() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Field> entry : SETTINGS.entrySet()) {
            try {
                values.put(entry.getKey(), entry.getValue().get(null));
            } catch (IllegalAccessException ignored) {}
        }
        return values;
    }

    private static void write(String name, Object value) {
        if (value == null) return;
        Field field = SETTINGS.get(name);
        if (field == null) return;
        try {
            field.set(null, value);
        } catch (IllegalAccessException ignored) {}
    }

    private class RailButton extends AbstractButton {
        private final int page;

        RailButton(int page, int x, int y) {
            super(x, y, RAIL_W, railRowH, Component.literal(PAGE_TITLES[page]));
            this.page = page;
        }

        @Override
        public void onPress() {
            boolean wasSearching = searching();
            if (page == currentPage && !wasSearching) return;
            if (wasSearching) searchBox.setValue("");
            currentPage = page;
            scroll = 0;
            status = "";
            confirmReset = false;
            remember();
            ColorPickerScreen.playUi(SoundEvents.NOTE_BLOCK_HAT, 1.4f);
            needsRebuild = true;
        }

        @Override
        protected void renderWidget(@NonNull GuiGraphics g, int mx, int my, float pt) {
            boolean selected = page == currentPage && !searching();
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            if (selected || isHovered) AtmosphereUi.widget(g, x, y, w, h, selected);
            if (selected) g.fill(x + 1, y + 1, x + 3, y + h - 1, AtmosphereUi.VALUE);
            if (isFocused()) AtmosphereUi.focusRing(g, x, y, w, h);

            Minecraft mc = Minecraft.getInstance();
            g.drawString(mc.font, AtmosphereUi.fit(mc.font, PAGE_TITLES[page], w - 20), x + 8,
                    y + (h - 8) / 2, selected ? AtmosphereUi.TEXT : AtmosphereUi.TEXT_DIM, true);
            if (page < FIRST_TOOL_PAGE && pageModified[page]) {
                int dotY = y + h / 2 - 2;
                g.fill(x + w - 10, dotY, x + w - 6, dotY + 4, AtmosphereUi.VALUE);
            }
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput out) {
            out.add(NarratedElementType.TITLE, Component.literal(PAGE_TITLES[page]));
        }
    }
}
