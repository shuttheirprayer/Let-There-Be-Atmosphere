package com.misanthropy.linggango.let_there_be_atmosphere.client.compat;

import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import org.embeddedt.embeddium.api.render.clouds.ModifyCloudRenderingEvent;

public final class EmbeddiumClouds {
    private EmbeddiumClouds() {}

    public static void register() {
        ModifyCloudRenderingEvent.BUS.addListener(event -> {
            if (!DynamicFogHandler.active()) return;
            int chunks = DynamicFogHandler.cloudRangeChunks;
            if (chunks > 0) event.setCloudRenderDistance(chunks);
        });
    }
}
