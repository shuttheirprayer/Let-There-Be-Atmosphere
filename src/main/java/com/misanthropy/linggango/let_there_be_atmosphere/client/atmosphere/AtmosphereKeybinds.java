package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.misanthropy.linggango.let_there_be_atmosphere.client.KeyBindings.OPEN_EDITOR;

@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AtmosphereKeybinds {
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();

        if (!OPEN_EDITOR.consumeClick()) return;
        openScreen(mc);
    }

    private static void openScreen(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;

        Holder<Biome> biomeHolder = mc.level.getBiome(mc.player.blockPosition());
        ResourceLocation biomeId = biomeHolder.unwrapKey()
                .map(ResourceKey::location)
                .orElseGet(() -> mc.level.registryAccess()
                        .registryOrThrow(Registries.BIOME)
                        .getKey(biomeHolder.get()));

        if (biomeId == null) return;
        mc.setScreen(new AtmosphereScreen(biomeId));
    }
}
