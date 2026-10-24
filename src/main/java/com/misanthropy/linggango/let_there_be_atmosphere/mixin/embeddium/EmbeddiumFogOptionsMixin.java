package com.misanthropy.linggango.let_there_be_atmosphere.mixin.embeddium;

import com.llamalad7.mixinextras.sugar.Local;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.FxConfig;
import com.misanthropy.linggango.let_there_be_atmosphere.client.gui.options.OptionsStorageForEmbeddium;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptionPages;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpact;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpl;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.gui.options.control.SliderControl;
import me.jellysquid.mods.sodium.client.gui.options.control.TickBoxControl;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@Mixin(value = SodiumGameOptionPages.class, remap = false)
public class EmbeddiumFogOptionsMixin {
    @Inject(
            method = "quality",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", remap = false, ordinal = 0, shift = At.Shift.AFTER)
    )
    private static void addAtmosphereOptions(CallbackInfoReturnable<OptionPage> cir,
                                              @Local(name = "groups") @NonNull List<OptionGroup> groups) {
        groups.add(OptionGroup.createBuilder()
                .add(linggango$toggle("Let There Be Atmosphere",
                        "Master switch for everything this mod does. Turn it off and the world renders "
                                + "exactly as vanilla does, without a restart. Useful for comparing the two.",
                        v -> DynamicFogHandler.masterEnabled = v, () -> DynamicFogHandler.masterEnabled))
                .add(linggango$toggle("Dynamic Atmospheric Fog",
                        "Fog that takes its colour and its distance from the biome you are in.",
                        v -> DynamicFogHandler.dynamicFogEnabled = v, () -> DynamicFogHandler.dynamicFogEnabled))
                .add(linggango$percent("Near Haze Strength (%)",
                        "Pushes the fog near-plane toward 0 so air has visible " +
                                "colour depth close to the player.",
                        0, 100, 1,
                        v -> DynamicFogHandler.nearHazeStrength = v / 100.0,
                        () -> (int) (DynamicFogHandler.nearHazeStrength * 100.0)))
                .add(linggango$percent("Fog Colour Vivid (%)",
                        "Boosts the saturation" +
                                "Biome fog colours are often very pale, so at 5–10% blend (near haze close up) " +
                                "25% (default) adds a noticeable saturation" +
                                "0% = disables.",
                        0, 100, 1,
                        v -> DynamicFogHandler.fogColorVivid = v / 100.0,
                        () -> (int) (DynamicFogHandler.fogColorVivid * 100.0)))
                .add(linggango$percent("Fog Start Distance (%)",
                        "Lower = denser world overall. " +
                                "Near Haze then pushes this toward 0 on top of this value.",
                        2, 65, 1,
                        v -> DynamicFogHandler.fogStartMultiplier = v / 100.0,
                        () -> (int) (DynamicFogHandler.fogStartMultiplier * 100.0)))
                .add(linggango$percent("Biome Fog Tint (%)",
                        "How strongly the biome's fog colour changes ambient",
                        0, 100, 1,
                        v -> DynamicFogHandler.biomeTintStrength = v / 100.0,
                        () -> (int) (DynamicFogHandler.biomeTintStrength * 100.0)))
                .add(linggango$percent("Sky Fog Blend (%)",
                        "Blends sky colour into the fog.",
                        0, 100, 1,
                        v -> DynamicFogHandler.skyFogBlendStrength = v / 100.0,
                        () -> (int) (DynamicFogHandler.skyFogBlendStrength * 100.0)))
                .add(linggango$percent("Chunk Border Softness (%)",
                        "Makes the borders softer or thicker, important for atmosphere",
                        0, 35, 1,
                        v -> DynamicFogHandler.chunkBorderFogSoftness = v / 100.0,
                        () -> (int) (DynamicFogHandler.chunkBorderFogSoftness * 100.0)))
                .add(linggango$toggle("Horizon Haze Overlay",
                        "Subtle fullscreen horizon glow for atmospheric depth without shaders.",
                        v -> DynamicFogHandler.hazeOverlayEnabled = v, () -> DynamicFogHandler.hazeOverlayEnabled))
                .add(linggango$percent("Horizon Haze Strength (%)",
                        "Intensity of the horizon haze overlay.",
                        0, 100, 1,
                        v -> DynamicFogHandler.hazeOverlayStrength = v / 100.0,
                        () -> (int) (DynamicFogHandler.hazeOverlayStrength * 100.0)))
                .add(linggango$toggle("Dynamic Stars",
                        "Enables the better looking stars.",
                        v -> DynamicFogHandler.starsEnabled = v, () -> DynamicFogHandler.starsEnabled))
                .add(linggango$slider("Night Star Density (%)",
                        "Density of the night sky stars. If above 100% it makes multiple layers of stars.",
                        0, 1000, 10, ControlValueFormatter.percentage(), OptionImpact.MEDIUM,
                        v -> DynamicFogHandler.starAmount = v,
                        () -> (int) DynamicFogHandler.starAmount))
                .add(linggango$percent("Night Star Brightness (%)",
                        "Base brightness multiplier for night stars.",
                        0, 200, 5,
                        v -> DynamicFogHandler.starBrightness = v / 100.0,
                        () -> (int) (DynamicFogHandler.starBrightness * 100.0)))
                .add(linggango$percent("Night Star Twinkle (%)",
                        "Intensity of the shimmering effect at night.",
                        0, 100, 1,
                        v -> DynamicFogHandler.starTwinkle = v / 100.0,
                        () -> (int) (DynamicFogHandler.starTwinkle * 100.0)))
                .add(linggango$toggle("Daytime Stars",
                        "Allows stars to be visible during the day.",
                        v -> DynamicFogHandler.dayStarsEnabled = v, () -> DynamicFogHandler.dayStarsEnabled))
                .add(linggango$slider("Day Star Density (%)",
                        "Density of the daytime stars.",
                        0, 1000, 10, ControlValueFormatter.percentage(), OptionImpact.MEDIUM,
                        v -> DynamicFogHandler.dayStarAmount = v,
                        () -> (int) DynamicFogHandler.dayStarAmount))
                .add(linggango$toggle("Atmospheric Void Fog",
                        "Darkens deep caves with old fog from 1.8 or some",
                        v -> DynamicFogHandler.voidFogEnabled = v, () -> DynamicFogHandler.voidFogEnabled))
                .add(linggango$toggle("Underwater Fog",
                        "Lets a biome set its own underwater colour and how far you can see through it. "
                                + "Vanilla only offers one flat \"murkier\" flag; this is a slider per biome.",
                        v -> DynamicFogHandler.waterFogEnabled = v, () -> DynamicFogHandler.waterFogEnabled))
                .add(linggango$percent("Underwater Visibility (%)",
                        "Scales vanilla's 96-block underwater reach. 100% = untouched. Per-biome "
                                + "overrides in the Atmosphere Editor (F8), Depth tab, multiply on top.",
                        20, 300, 10,
                        v -> DynamicFogHandler.waterFogDistance = v / 100.0,
                        () -> (int) (DynamicFogHandler.waterFogDistance * 100.0)))
                .add(linggango$slider("Cloud Range (chunks)",
                        "How far the cloud sheet reaches, independent of render distance. Vanilla is "
                                + "fixed at about 24 and stops in mid-air on a long view. Fancy clouds only.",
                        8, 64, 4, ControlValueFormatter.number(), OptionImpact.LOW,
                        v -> DynamicFogHandler.cloudRangeChunks = v,
                        () -> DynamicFogHandler.cloudRangeChunks))
                .add(linggango$toggle("Clouds Ignore Terrain Fog",
                        "Gives clouds their own fog reach instead of the terrain's, so a wide cloud "
                                + "range is actually visible. Turn off if you want them to fade with the ground.",
                        v -> DynamicFogHandler.cloudFogEnabled = v, () -> DynamicFogHandler.cloudFogEnabled))
                .add(linggango$percent("Sky Fog End (%)",
                        "Fogs the sky itself ahead of the terrain, so the horizon dissolves instead of "
                                + "meeting a hard edge. 100% = vanilla; try 70-85% for the melt.",
                        30, 100, 5,
                        v -> DynamicFogHandler.skyFogEnd = v / 100.0,
                        () -> (int) (DynamicFogHandler.skyFogEnd * 100.0)))
                .add(linggango$percent("Rain Mist Density (%)",
                        "How close rain and storms pull fog. Lower = heavier weather.",
                        1, 50, 1,
                        v -> DynamicFogHandler.rainFogDensity = v / 100.0,
                        () -> (int) (DynamicFogHandler.rainFogDensity * 100.0)))
                .add(linggango$percent("Fog Blend Speed (%)",
                        "How quickly fog adapts to new environments. Lower = slower",
                        1, 100, 1,
                        v -> DynamicFogHandler.fogTransitionSpeed = v / 1000.0,
                        () -> (int) (DynamicFogHandler.fogTransitionSpeed * 1000.0)))
                .add(linggango$toggle("Biome Colour Theming",
                        "Master switch for recolouring grass, foliage and water. Use the sliders below " +
                                "for a global look, or set per-biome colours in the Atmosphere Editor (F8).",
                        v -> DynamicFogHandler.biomeColorsEnabled = v, () -> DynamicFogHandler.biomeColorsEnabled))
                .add(linggango$percent("Vegetation Saturation (%)",
                        "Global saturation of grass and foliage. 100% = vanilla, higher = more vivid.",
                        0, 300, 5,
                        v -> DynamicFogHandler.vegSaturation = v / 100.0,
                        () -> (int) (DynamicFogHandler.vegSaturation * 100.0)))
                .add(linggango$percent("Vegetation Brightness (%)",
                        "Global brightness of grass and foliage. 100% = vanilla.",
                        30, 200, 5,
                        v -> DynamicFogHandler.vegBrightness = v / 100.0,
                        () -> (int) (DynamicFogHandler.vegBrightness * 100.0)))
                .add(linggango$percent("Water Saturation (%)",
                        "Global saturation of water. 100% = vanilla, higher = more vivid.",
                        0, 300, 5,
                        v -> DynamicFogHandler.waterSaturation = v / 100.0,
                        () -> (int) (DynamicFogHandler.waterSaturation * 100.0)))
                .add(linggango$percent("Water Brightness (%)",
                        "Global brightness of water. 100% = vanilla.",
                        30, 200, 5,
                        v -> DynamicFogHandler.waterBrightness = v / 100.0,
                        () -> (int) (DynamicFogHandler.waterBrightness * 100.0)))
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(linggango$percent("Sunset Fog Tint (%)",
                        "Same effect pushed into the distance fog, so the horizon glows too.",
                        0, 150, 5,
                        v -> FxConfig.sunsetFogStrength = v / 100.0,
                        () -> (int) (FxConfig.sunsetFogStrength * 100.0)))
                .add(linggango$toggle("Long Sunset Window",
                        "On: a wider, longer-holding glow that walks gold to deep red. "
                                + "Off: the narrow 1.20 window.",
                        v -> FxConfig.sunsetWide = v, () -> FxConfig.sunsetWide))
                .add(linggango$toggle("Weather Screen Tint",
                        "Casts the screen cold while weather falls on you: steel blue in rain, pale "
                                + "white in snow. Only while open sky is above you, so it stops indoors.",
                        v -> FxConfig.weatherTintEnabled = v, () -> FxConfig.weatherTintEnabled))
                .add(linggango$percent("Weather Tint Strength (%)",
                        "How heavy the cast gets in a full downpour. Thunder pushes past this, and "
                                + "night keeps only a trace of it.",
                        0, 100, 5,
                        v -> FxConfig.weatherTintStrength = v / 100.0,
                        () -> (int) (FxConfig.weatherTintStrength * 100.0)))
                .add(linggango$toggle("Coloured Light",
                        "Warm block light, cold sky light after dusk. Current Minecraft ships this as "
                                + "dimension data; the defaults here are its own colours.",
                        v -> FxConfig.lightTintEnabled = v, () -> FxConfig.lightTintEnabled))
                .add(linggango$percent("Block Light Warmth (%)",
                        "How warm torches, lava and campfires light the world.",
                        0, 100, 5,
                        v -> FxConfig.blockLightStrength = v / 100.0,
                        () -> (int) (FxConfig.blockLightStrength * 100.0)))
                .add(linggango$percent("Night Sky Light Tint (%)",
                        "How blue moonlight is compared to daylight.",
                        0, 100, 5,
                        v -> FxConfig.skyLightStrength = v / 100.0,
                        () -> (int) (FxConfig.skyLightStrength * 100.0)))
                .build());
    }

    @Unique
    private static Option<?> linggango$toggle(String name, String tooltip,
                                              Consumer<Boolean> set, Supplier<Boolean> get) {
        return OptionImpl.createBuilder(Boolean.TYPE, OptionsStorageForEmbeddium.INSTANCE)
                .setName(Component.literal(name))
                .setTooltip(Component.literal(tooltip))
                .setControl(TickBoxControl::new)
                .setBinding((opts, val) -> set.accept(val), opts -> get.get())
                .setImpact(OptionImpact.LOW)
                .build();
    }

    @Unique
    private static Option<?> linggango$percent(String name, String tooltip, int min, int max, int step,
                                               IntConsumer set, IntSupplier get) {
        return linggango$slider(name, tooltip, min, max, step,
                ControlValueFormatter.percentage(), OptionImpact.LOW, set, get);
    }

    @Unique
    private static Option<?> linggango$slider(String name, String tooltip, int min, int max, int step,
                                              ControlValueFormatter formatter, OptionImpact impact,
                                              IntConsumer set, IntSupplier get) {
        return OptionImpl.createBuilder(Integer.TYPE, OptionsStorageForEmbeddium.INSTANCE)
                .setName(Component.literal(name))
                .setTooltip(Component.literal(tooltip))
                .setControl(option -> new SliderControl(option, min, max, step, formatter))
                .setBinding((opts, val) -> set.accept(val), opts -> get.getAsInt())
                .setImpact(impact)
                .build();
    }
}
