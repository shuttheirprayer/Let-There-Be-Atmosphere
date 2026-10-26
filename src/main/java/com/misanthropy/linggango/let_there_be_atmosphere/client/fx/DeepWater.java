package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class DeepWater {
    private DeepWater() {}

    private static final int SNOW = 12;
    private static final int CAP = 48;
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();

    private static float depth;
    private static float previous;
    private static float owed;
    private static int wait = 400 + RANDOM.nextInt(800);

    static float depth(float partialTick) {
        return Mth.lerp(partialTick, previous, depth);
    }

    static float shallow(float partialTick) {
        return FxConfig.deepWater ? 1F - SkyGlow.smooth(4F, 24F, depth(partialTick)) : 1F;
    }

    private static float darkness(Camera camera, float partialTick) {
        if (!FxConfig.deepWater || FxConfig.deepWaterDarkness <= 0.0 || !DynamicFogHandler.active()
                || CameraFluid.of(camera) != FogType.WATER) return 0F;
        float k = Math.min(1F, SkyGlow.smooth(6F, 34F, depth(partialTick)) * (float) FxConfig.deepWaterDarkness);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && (player.hasEffect(MobEffects.NIGHT_VISION) || player.hasEffect(MobEffects.CONDUIT_POWER))) k *= 0.3F;
        return k;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDeepWaterFog(ViewportEvent.RenderFog event) {
        float k = darkness(event.getCamera(), (float) event.getPartialTick());
        if (k <= 0F) return;
        event.setFarPlaneDistance(event.getFarPlaneDistance() * (1F - 0.65F * k));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeepWaterFogColor(ViewportEvent.ComputeFogColor event) {
        float k = darkness(event.getCamera(), (float) event.getPartialTick()) * 0.92F;
        if (k <= 0F) return;
        event.setRed(Mth.lerp(k, event.getRed(), 0.01F));
        event.setGreen(Mth.lerp(k, event.getGreen(), 0.03F));
        event.setBlue(Mth.lerp(k, event.getBlue(), 0.07F));
    }

    public static void onDeepWaterTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            depth = previous = 0F;
            return;
        }
        if (mc.isPaused()) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        boolean wet = FxConfig.deepWater && DynamicFogHandler.active() && CameraFluid.of(camera) == FogType.WATER;
        Vec3 eye = camera.getPosition();
        int column = 0;
        if (wet) {
            CURSOR.set(eye.x, eye.y, eye.z);
            int sky = level.getBrightness(LightLayer.SKY, CURSOR);
            while (column < CAP && level.getFluidState(CURSOR).is(FluidTags.WATER)) {
                CURSOR.move(Direction.UP);
                column++;
            }
            column = Math.max(column, 15 - sky);
        }
        previous = depth;
        depth += (column - depth) * 0.08F;
        if (!wet) {
            Diagnostics.skip(Diagnostics.DEEP_WATER, "disabled or the camera is not underwater");
            return;
        }
        float snow = FxConfig.marineSnow ? (float) FxConfig.marineSnowAmount * SkyGlow.smooth(3F, 14F, depth) : 0F;
        if (snow > 0F && CaveMotes.resolve()) snow(level, eye, snow);
        if (FxConfig.deepSounds && FxConfig.deepSoundsVolume > 0.0 && depth > 20F && --wait <= 0) {
            groan(level, eye);
            wait = 800 + RANDOM.nextInt(1600);
        }
        Diagnostics.mark(Diagnostics.DEEP_WATER, Diagnostics.detail()
                ? String.format("depth %.1f, snow %.2f, next groan %ds", depth, snow, wait / 20) : null);
    }

    private static void snow(ClientLevel level, Vec3 eye, float amount) {
        owed = Math.min(owed + amount * 1.5F, 8F);
        for (; owed >= 1F; owed -= 1F) {
            double x = eye.x + (RANDOM.nextDouble() * 2.0 - 1.0) * 9.0;
            double y = eye.y + (RANDOM.nextDouble() * 2.0 - 1.0) * 5.0;
            double z = eye.z + (RANDOM.nextDouble() * 2.0 - 1.0) * 9.0;
            CURSOR.set(x, y, z);
            if (!level.getFluidState(CURSOR).is(FluidTags.WATER)) continue;
            CaveMotes.emit(SNOW, x, y, z, level.getBrightness(LightLayer.BLOCK, CURSOR),
                    Math.max(level.getBrightness(LightLayer.SKY, CURSOR), 9), 8F + RANDOM.nextFloat() * 6F, RANDOM.nextFloat());
        }
    }

    private static void groan(ClientLevel level, Vec3 eye) {
        int pick = RANDOM.nextInt(3);
        SoundEvent sound = pick == 0 ? SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE
                : pick == 1 ? SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_RARE : SoundEvents.AMBIENT_CAVE.value();
        float angle = RANDOM.nextFloat() * Mth.TWO_PI;
        level.playLocalSound(eye.x + Mth.cos(angle) * 12.0, eye.y - 4.0, eye.z + Mth.sin(angle) * 12.0, sound, SoundSource.AMBIENT,
                1.4F * (float) FxConfig.deepSoundsVolume, 0.5F + RANDOM.nextFloat() * 0.1F, false);
    }
}
