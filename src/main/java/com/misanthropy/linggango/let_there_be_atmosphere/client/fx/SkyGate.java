package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.client.event.RenderLevelStageEvent;

public final class SkyGate {
    private SkyGate() {}

    public static ClientLevel sky(RenderLevelStageEvent event, int slot, boolean normalSky) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !level.dimensionType().hasSkyLight()) {
            Diagnostics.skip(slot, "this dimension has no sky");
            return null;
        }
        if (normalSky && level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            Diagnostics.skip(slot, "this dimension has no normal sky");
            return null;
        }
        return unobstructed(event, slot, mc, level);
    }

    public static ClientLevel air(RenderLevelStageEvent event, int slot) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        return level == null ? null : unobstructed(event, slot, mc, level);
    }

    private static ClientLevel unobstructed(RenderLevelStageEvent event, int slot,
                                            Minecraft mc, ClientLevel level) {
        if (CameraFluid.of(event.getCamera()) != FogType.NONE) {
            Diagnostics.skip(slot, "the camera is in a fluid");
            return null;
        }
        if (mc.player != null && (mc.player.hasEffect(MobEffects.BLINDNESS)
                || mc.player.hasEffect(MobEffects.DARKNESS))) {
            Diagnostics.skip(slot, "blindness or darkness");
            return null;
        }
        return level;
    }
}
