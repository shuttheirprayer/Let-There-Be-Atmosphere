package com.misanthropy.linggango.let_there_be_atmosphere.client.gui.options;

import com.misanthropy.linggango.let_there_be_atmosphere.config.AtmosphereConfigManager;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;

public class OptionsStorageForEmbeddium implements OptionStorage<Void> {
    public static final OptionsStorageForEmbeddium INSTANCE = new OptionsStorageForEmbeddium();

    @Override
    public Void getData() {
        return null;
    }

    @Override
    public void save() {
        AtmosphereConfigManager.save();
        DynamicFogHandler.save();
        DynamicFogHandler.rebuildStars();
        DynamicFogHandler.refreshBiomeColors();
    }
}
