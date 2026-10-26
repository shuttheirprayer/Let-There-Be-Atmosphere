package com.misanthropy.linggango.let_there_be_atmosphere.client;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.FxConfig;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class Diagnostics {
    private Diagnostics() {}

    public static final Logger LOG = LogUtils.getLogger();

    public static final int FOG      = 0;
    public static final int CLOUDS   = 1;
    public static final int STORY    = 2;
    public static final int COMETS   = 3;
    public static final int HAZEBAND = 4;
    public static final int LIGHTMAP = 5;
    public static final int MIST     = 6;
    public static final int AURORA   = 7;
    public static final int GLOW     = 8;
    public static final int RAINBOW  = 9;
    public static final int GRADE    = 10;
    public static final int BLOOM    = 11;
    public static final int SHADOWS  = 12;
    public static final int RAYS     = 13;
    public static final int LIGHTNING = 14;
    public static final int HAZE     = 15;
    public static final int GROUND_MIST = 16;
    public static final int CAVE     = 17;
    public static final int PUDDLES  = 18;
    public static final int LAVA     = 19;
    public static final int WATER_RAYS = 20;
    public static final int LITTER   = 21;
    public static final int SWAY     = 22;
    public static final int BLADES   = 23;
    public static final int SHORE    = 24;
    public static final int WAKE     = 25;
    public static final int CAUSTICS = 26;
    public static final int FIREFLIES = 27;
    public static final int BUTTERFLIES = 28;
    public static final int STARS    = 29;
    public static final int SPARKLES = 30;
    public static final int SHEEN    = 31;
    public static final int BIRDS    = 32;
    public static final int NETHER   = 33;
    public static final int NETHER_DUST = 34;
    public static final int END_SKY  = 35;
    public static final int END_DUST = 36;
    public static final int PORTAL   = 37;
    public static final int RAIN     = 38;
    public static final int STORM    = 39;
    public static final int RIPPLES  = 40;
    public static final int BLIZZARD = 41;
    public static final int SANDSTORM = 42;
    public static final int TUMBLEWEEDS = 43;
    public static final int END_AIR = 44;
    public static final int NETHER_STORM = 45;
    public static final int NETHER_RAYS = 46;
    public static final int LAVA_FX = 47;
    public static final int WIND = 48;
    public static final int WINDPUSH = 49;
    public static final int WINDBITS = 50;
    public static final int WINDSOUND = 51;
    public static final int RUSTLE = 52;
    public static final int BUBBLES = 53;
    public static final int DEEP_DARK = 54;
    public static final int WATERFALL = 55;
    public static final int NETHER_PORTAL = 56;
    public static final int DEEP_WATER = 57;
    public static final int AIR_MIST = 58;
    public static final int OCCLUSION = 59;
    public static final int FLARE = 60;
    public static final int SUN_SHADOWS = 61;
    public static final int REFLECTIONS = 62;
    public static final int LENS_DROPS = 63;
    public static final int PUFFS = 64;
    public static final int FISH = 65;

    private static final String[] LABELS = {
            "fog", "clouds", "story", "comets", "hazeband", "lightmap", "mist", "aurora", "glow", "rainbow", "grade", "bloom", "shadows", "rays", "lightning", "haze", "groundmist", "cave", "puddles", "lava", "waterrays", "litter", "sway", "blades", "shore", "wake", "caustics", "fireflies", "butterflies", "stars", "sparkles", "sheen", "birds", "nether", "netherdust", "endsky", "enddust", "portal", "rain", "storm", "ripples", "blizzard", "sandstorm", "tumbleweeds", "endair", "netherstorm", "netherrays", "lavafx", "wind", "windpush", "windbits", "windsound", "rustle", "bubbles", "deepdark", "waterfall", "netherportal", "deepwater", "airmist", "occlusion", "flare", "sunshadows", "reflections", "lensdrops", "puffs", "fish"
    };

    private static final String[] COMPAT = {"embeddium", "rubidium", "sodium", "oculus", "iris", "optifine"};

    private static final int GRACE = 20;

    private static final String[] DETAIL = new String[LABELS.length];
    private static final boolean[] RAN = new boolean[LABELS.length];
    private static final int[] STAMP = new int[LABELS.length];
    private static final Set<String> ONCE = ConcurrentHashMap.newKeySet();

    private static int frame = GRACE + 2;
    private static String compat;
    private static String signature = "";

    public static boolean detail() {
        return FxConfig.debugOverlay;
    }

    public static void mark(int slot, String detail) {
        DETAIL[slot] = detail;
        RAN[slot] = true;
        STAMP[slot] = frame;
    }

    public static void skip(int slot, String reason) {
        DETAIL[slot] = reason;
        RAN[slot] = false;
        STAMP[slot] = frame;
    }

    public static void once(String message) {
        if (!ONCE.contains(message) && ONCE.add(message)) LOG.info("[atmosphere] {}", message);
    }

    public static String compat() {
        if (compat != null) return compat;
        StringBuilder out = new StringBuilder();
        try {
            ModList list = ModList.get();
            for (String id : COMPAT) {
                list.getModContainerById(id).ifPresent(container ->
                        out.append(out.length() == 0 ? "" : " ")
                           .append(id).append(' ').append(container.getModInfo().getVersion()));
            }
        } catch (Throwable ignored) {
            return "unknown";
        }
        compat = out.length() == 0 ? "none" : out.toString();
        return compat;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.@NonNull Post event) {
        int drawnFrame = frame++;
        if (!FxConfig.debugOverlay) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;

        String head = "atmosphere  master " + (DynamicFogHandler.active() ? "on" : "OFF")
                + "  daytime " + (mc.level.getDayTime() % 24000L)
                + "  rain " + String.format("%.2f", mc.level.getRainLevel(1F))
                + "  sky " + String.format("%.2f", DynamicFogHandler.skyVisibility())
                + "  compat " + compat();
        logChanges(drawnFrame, head);

        int lineHeight = mc.font.lineHeight + 1;
        int width = mc.font.width(head);
        String[] lines = new String[LABELS.length];
        for (int slot = 0; slot < LABELS.length; slot++) {
            lines[slot] = line(slot, drawnFrame);
            width = Math.max(width, mc.font.width(lines[slot]));
        }

        GuiGraphics g = event.getGuiGraphics();
        int x = 4;
        int y = 4;
        g.fill(x, y, x + width + 8, y + (LABELS.length + 1) * lineHeight + 7, 0xC0101018);
        g.drawString(mc.font, head, x + 4, y + 4, 0xFFFFE9A8, false);
        for (int slot = 0; slot < LABELS.length; slot++) {
            g.drawString(mc.font, lines[slot], x + 4, y + 4 + (slot + 1) * lineHeight, color(slot, drawnFrame), false);
        }
    }

    private static String line(int slot, int drawnFrame) {
        StringBuilder out = new StringBuilder(LABELS[slot]);
        while (out.length() < 9) out.append(' ');
        if (stale(slot, drawnFrame)) return out.append("NOT REACHED").toString();
        return out.append(RAN[slot] ? "on   " : "off  ").append(DETAIL[slot] == null ? "" : DETAIL[slot]).toString();
    }

    private static int color(int slot, int drawnFrame) {
        if (stale(slot, drawnFrame)) return 0xFFFF6B5C;
        return RAN[slot] ? 0xFF7FE07F : 0xFF8A909C;
    }

    private static boolean stale(int slot, int drawnFrame) {
        return drawnFrame - STAMP[slot] > GRACE;
    }

    private static void logChanges(int drawnFrame, String head) {
        StringBuilder now = new StringBuilder();
        for (int slot = 0; slot < LABELS.length; slot++) {
            now.append(stale(slot, drawnFrame) ? '?' : RAN[slot] ? '+' : '-');
        }
        String current = now.toString();
        if (current.equals(signature)) return;
        signature = current;
        LOG.info("[atmosphere] {}", head);
        for (int slot = 0; slot < LABELS.length; slot++) {
            LOG.info("[atmosphere] {}", line(slot, drawnFrame));
        }
    }

    @Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Startup {
        private Startup() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOG.info("[atmosphere] rendering compat mods present: {}", compat());
        }
    }
}
