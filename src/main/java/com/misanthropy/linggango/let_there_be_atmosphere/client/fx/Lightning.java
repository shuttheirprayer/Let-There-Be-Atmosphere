package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class Lightning {
    private Lightning() {}

    private static final int STROKES = 16;
    private static final float FADE = 0.085F;
    private static final long QUIET = 1_500_000_000L;
    private static final float DISTANCE = 100F;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final long[] AT = new long[STROKES];
    private static final float[] AMP = new float[STROKES];
    private static final Matrix4f MATRIX = new Matrix4f();

    private static int next;
    private static long last = Long.MIN_VALUE / 2;
    private static float dirX, dirY = 1F, dirZ;
    private static long cachedAt;
    private static float cached;

    @SubscribeEvent
    public static void onLightningJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LightningBolt bolt) || !event.getLevel().isClientSide()) return;
        if (!FxConfig.lightningFlash || FxConfig.lightningFlashStrength <= 0.0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideLightningFlash().get() || mc.gameRenderer == null) return;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        double dx = bolt.getX() - camera.x;
        double dy = bolt.getY() + 48.0 - camera.y;
        double dz = bolt.getZ() - camera.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-3) return;
        dirX = (float) (dx / length);
        dirY = Math.max((float) (dy / length), 0.08F);
        dirZ = (float) (dz / length);
        float near = 1F - 0.7F * SkyGlow.smooth(48F, 320F, (float) Math.sqrt(dx * dx + dz * dz));
        strokes(near, 1 + (RANDOM.nextFloat() < 0.6F ? 1 : 0) + (RANDOM.nextFloat() < 0.35F ? 1 : 0)
                + (RANDOM.nextFloat() < 0.15F ? 1 : 0));
        Diagnostics.once("lightning flash heard its first bolt");
    }

    static boolean sheet(float x, float y, float z, float amp, int count) {
        Minecraft mc = Minecraft.getInstance();
        if (!FxConfig.lightningFlash || FxConfig.lightningFlashStrength <= 0.0 || mc.options.hideLightningFlash().get()) return false;
        if (Util.getNanos() - last < QUIET / 3) return false;
        dirX = x;
        dirY = Math.max(y, 0.08F);
        dirZ = z;
        strokes(amp, count);
        return true;
    }

    private static void strokes(float amp, int count) {
        long at = Util.getNanos();
        for (int i = 0; i < count; i++) {
            AT[next] = at;
            AMP[next] = amp * (i == 0 ? 1F : 0.45F + RANDOM.nextFloat() * 0.5F);
            next = (next + 1) % STROKES;
            at += 55_000_000L + (long) (RANDOM.nextFloat() * 130_000_000L);
        }
        last = at;
    }

    static float dirX() {
        return dirX;
    }

    static float dirY() {
        return dirY;
    }

    static float dirZ() {
        return dirZ;
    }

    public static float flash() {
        long now = Util.getNanos();
        if (now - last > QUIET) return 0F;
        if (now == cachedAt) return cached;
        float sum = 0F;
        for (int i = 0; i < STROKES; i++) {
            long age = now - AT[i];
            if (age >= 0L && age < QUIET) sum += AMP[i] * (float) Math.exp(-age / 1.0E9 / FADE);
        }
        cachedAt = now;
        cached = Math.min(sum, 1F) * (float) FxConfig.lightningFlashStrength;
        return cached;
    }

    public static float flashOrZero() {
        if (!FxConfig.lightningFlash || !DynamicFogHandler.active()) return 0F;
        return flash();
    }

    public static void onRenderLightningStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        float flash = flashOrZero();
        if (flash < 0.004F) {
            Diagnostics.skip(Diagnostics.LIGHTNING, FxConfig.lightningFlash ? "no strike flashing" : "disabled");
            return;
        }
        ClientLevel level = SkyGate.sky(event, Diagnostics.LIGHTNING, false);
        if (level == null) return;
        MATRIX.set(event.getPoseStack().last().pose());
        MATRIX.transformPosition(dirX * DISTANCE, dirY * DISTANCE, dirZ * DISTANCE, SkyGlow.CENTER);
        SkyGlow.quads = 0;
        float a = Math.min(flash, 1F);
        float near = 0.22F * a;
        float wide = 0.12F * a;
        SkyGlow.add(260F, 130F, 1F, 0F, 0F, 0.80F, 0.86F, 1F, near);
        SkyGlow.add(600F, 240F, 1F, 0F, 0F, 0.72F, 0.80F, 1F, wide);
        SkyGlow.flush();
        Diagnostics.mark(Diagnostics.LIGHTNING, Diagnostics.detail() ? String.format("flash %.2f", flash) : null);
    }

    public static float mix(float from, float to, float flash, float weight) {
        return Mth.lerp(Math.min(flash * weight, 1F), from, to);
    }
}
