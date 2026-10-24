package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.KeyBindings;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.FxConfig;
import com.misanthropy.linggango.let_there_be_atmosphere.config.AtmosphereConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jspecify.annotations.NonNull;

@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class BiomeOverlay {
    private BiomeOverlay() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.@NonNull Post event) {
        if (!FxConfig.biomeOverlay) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) return;
        if (mc.options.renderDebug) return;

        if (!(mc.level.getBiome(player.blockPosition()) instanceof Holder.Reference<Biome> biome)) return;
        ResourceLocation id = biome.key().location();

        boolean tuned = AtmosphereConfigManager.ATMOSPHERES.containsKey(id);
        String name = id.getPath().replace('_', ' ');
        String state = tuned ? "tuned" : "default";
        String edit = KeyBindings.OPEN_EDITOR.getTranslatedKeyMessage().getString();

        GuiGraphics g = event.getGuiGraphics();
        int nameW = mc.font.width(name);
        int stateW = mc.font.width(state);
        int editW = mc.font.width(edit);
        int boxW = Math.max(nameW, stateW + editW + 10) + 12;
        int x = g.guiWidth() - boxW - 6;

        g.fill(x, 4, x + boxW, 30, 0xB0101018);
        g.fill(x, 4, x + 2, 30, tuned ? AtmosphereUi.ON : AtmosphereUi.OFF);
        g.drawString(mc.font, name, x + 7, 8, 0xFFECECF2, false);
        g.drawString(mc.font, state, x + 7, 19, tuned ? AtmosphereUi.ON : AtmosphereUi.TEXT_SOFT, false);
        g.drawString(mc.font, edit, x + boxW - 6 - editW, 19, AtmosphereUi.TEXT_SOFT, false);
    }
}
