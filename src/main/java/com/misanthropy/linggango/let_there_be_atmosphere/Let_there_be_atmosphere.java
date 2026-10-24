package com.misanthropy.linggango.let_there_be_atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.client.compat.EmbeddiumClouds;
import com.misanthropy.linggango.let_there_be_atmosphere.config.AtmosphereConfigManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Let_there_be_atmosphere.MODID)
public class Let_there_be_atmosphere {
    public static final String MODID = "let_there_be_atmosphere";

    public Let_there_be_atmosphere() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            AtmosphereConfigManager.load();
            hookEmbeddiumClouds();
        }
    }

    private static void hookEmbeddiumClouds() {
        try {
            if (ModList.get().isLoaded("embeddium")) EmbeddiumClouds.register();
        } catch (Throwable ignored) {
        }
    }
}
