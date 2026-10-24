package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class Silt {
    private Silt() {}

    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();

    public static void onSiltTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || !FxConfig.caustics || !DynamicFogHandler.active()) return;
        if (CameraFluid.of(mc.gameRenderer.getMainCamera()) != FogType.WATER || !CaveMotes.resolve()) return;
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        for (int i = 0; i < 3; i++) {
            double x = eye.x + (RANDOM.nextDouble() * 2.0 - 1.0) * 7.0;
            double y = eye.y + (RANDOM.nextDouble() * 2.0 - 1.0) * 4.0;
            double z = eye.z + (RANDOM.nextDouble() * 2.0 - 1.0) * 7.0;
            CURSOR.set(x, y, z);
            if (!level.getFluidState(CURSOR).is(FluidTags.WATER)) continue;
            CaveMotes.emit(CaveMotes.SILT, x, y, z, level.getBrightness(LightLayer.BLOCK, CURSOR),
                    level.getBrightness(LightLayer.SKY, CURSOR), 6F + RANDOM.nextFloat() * 5F, RANDOM.nextFloat());
        }
    }
}
