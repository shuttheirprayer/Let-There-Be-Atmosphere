package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.config.AtmosphereConfigManager;
import com.misanthropy.linggango.let_there_be_atmosphere.config.SettingsWriter;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class PresetManager {
    private PresetManager() {}

    private static final String SETTINGS_FILE = "settings.properties";
    private static final String BIOMES_FILE = "biomes.json";
    private static final String SEEDED = ".built-in-presets-1";
    private static final String[][] BUILT_IN = {{"vanilla_plus", "Vanilla Plus"}, {"storybook", "Storybook"}, {"stormy", "Stormy"}};

    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve("let_there_be_atmosphere").resolve("presets");
    }

    public static List<String> list() {
        List<String> names = new ArrayList<>();
        Path dir = folder();
        seed(dir);
        if (!Files.isDirectory(dir)) return names;
        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .forEach(names::add);
        } catch (Exception ignored) {}
        return names;
    }

    private static void seed(Path dir) {
        if (Files.exists(dir.resolve(SEEDED))) return;
        try {
            Files.createDirectories(dir);
            for (String[] preset : BUILT_IN) {
                Path target = dir.resolve(preset[1]);
                if (Files.exists(target)) continue;
                try (InputStream in = PresetManager.class.getResourceAsStream("/assets/let_there_be_atmosphere/presets/" + preset[0] + "/" + SETTINGS_FILE)) {
                    if (in == null) continue;
                    Files.createDirectories(target);
                    Files.copy(in, target.resolve(SETTINGS_FILE));
                }
            }
            Files.writeString(dir.resolve(SEEDED), "");
        } catch (Exception ignored) {}
    }

    public static String sanitize(String name) {
        String trimmed = name == null ? "" : name.trim();
        String cleaned = trimmed.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    public static boolean save(String name) {
        String safe = sanitize(name);
        if (safe.isEmpty()) return false;
        try {
            Path dir = folder().resolve(safe);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(SETTINGS_FILE), DynamicFogHandler.settingsText());
            Files.writeString(dir.resolve(BIOMES_FILE), AtmosphereConfigManager.toJson());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean apply(String name) {
        Path dir = folder().resolve(name);
        if (!Files.isDirectory(dir)) return false;
        SettingsWriter.flush();
        try {
            Path settings = dir.resolve(SETTINGS_FILE);
            Path biomes = dir.resolve(BIOMES_FILE);
            if (Files.exists(settings)) {
                Files.writeString(configFile(SETTINGS_FILE), Files.readString(settings));
            }
            if (Files.exists(biomes)) {
                AtmosphereConfigManager.fromJson(Files.readString(biomes));
                AtmosphereConfigManager.save();
            }
            DynamicFogHandler.load();
            DynamicFogHandler.rebuildStars();
            DynamicFogHandler.refreshBiomeColors();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean delete(String name) {
        Path dir = folder().resolve(name);
        if (!Files.isDirectory(dir)) return false;
        try {
            Files.deleteIfExists(dir.resolve(SETTINGS_FILE));
            Files.deleteIfExists(dir.resolve(BIOMES_FILE));
            Files.deleteIfExists(dir);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path configFile(String file) {
        return FMLPaths.CONFIGDIR.get().resolve("let_there_be_atmosphere").resolve(file);
    }
}
