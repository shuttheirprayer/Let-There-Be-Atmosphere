package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.BiomeFx;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class WindLife {
    private WindLife() {}

    public static void onWindLifeTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            WindParticles.active = false;
            WindSound.silence();
            return;
        }
        if (mc.isPaused()) return;
        boolean sky = level.dimensionType().hasSkyLight() && DynamicFogHandler.active();
        float seconds = Wind.seconds(level, 0F);
        if ((FxConfig.livingWind || FxConfig.windShelter) && sky) {
            Wind.tick(level, player);
            Diagnostics.mark(Diagnostics.WIND, Diagnostics.detail()
                    ? String.format("%s level %.2f, front %.2f, storm %.2f, shelter %.2f", Wind.mood(seconds), Wind.level(seconds), Wind.front, Wind.storm, Wind.shelter)
                    : null);
        } else {
            Wind.idle();
            Diagnostics.skip(Diagnostics.WIND, FxConfig.livingWind ? "no sky or master off" : "classic wind");
        }
        WindParticles.update(level, seconds, player.getEyeY(), sky && BiomeFx.on(FxConfig.windParticles, BiomeFx.WIND_PARTICLES) && BiomeFx.amount(FxConfig.windParticleStrength, BiomeFx.WIND_PARTICLES) > 0.0);
        if (sky) {
            WindBits.tick(level, player, seconds);
            WindSound.tick(level, player, seconds);
            Rustle.tick(level, player, seconds);
        } else {
            WindSound.silence();
        }
    }
}
