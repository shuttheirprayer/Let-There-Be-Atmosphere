package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class WeatherStorms {
    private WeatherStorms() {}

    private static final ResourceLocation ATLAS = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/storm_air.png");
    private static final float[] SNOW_FOG = {0xDC / 255F, 0xE8 / 255F, 0xF2 / 255F};
    private static final float[] SAND_FOG = {0xE3 / 255F, 0xC4 / 255F, 0x9A / 255F};
    private static final float RISE = 0.1F / 20F;
    private static final float FALL = 0.15F / 20F;
    private static final int FINE = 9000;
    private static final int LARGE = 900;
    private static final int NEAR = 100;
    private static final int MID = 220;
    private static final int FAR = 80;
    private static final int STREAKS = 300;
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(16);
    private static final float[] FOG_OUT = new float[3];

    private static ClientLevel lastLevel;
    private static float blizzardWeather;
    private static float sandWeather;
    private static float exposure;
    private static float fogEnd = 256F;
    private static float groundRef;
    private static double driftX, driftZ;
    private static long driftAt;
    private static Howl howl;
    private static float howlVolume;
    private static float howlPitch = 1F;

    public static float blizzard() {
        return smooth(blizzardWeather) * exposure;
    }

    public static float sandstorm() {
        return smooth(sandWeather) * exposure;
    }

    private static float smooth(float x) {
        return x * x * (3F - 2F * x);
    }

    private static float blizzardShown() {
        return FxConfig.blizzard ? blizzard() : 0F;
    }

    private static float sandShown() {
        return FxConfig.sandstorm ? sandstorm() : 0F;
    }

    private static boolean usable() {
        return DynamicFogHandler.active() && !ColorGrade.shaderPackActive();
    }

    public static void onWeatherStormsTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            blizzardWeather = 0F;
            sandWeather = 0F;
            exposure = 0F;
            lastLevel = null;
            retune(0F, 1F);
            return;
        }
        if (level != lastLevel) {
            lastLevel = level;
            blizzardWeather = 0F;
            sandWeather = 0F;
            groundRef = (float) Math.floor(player.getY() + 0.05);
        }
        groundRef += ((float) Math.floor(player.getY() + 0.05) - groundRef) * 0.15F;
        float rain = level.getRainLevel(1F), thunder = level.getThunderLevel(1F);
        float snowTarget = 0F, sandTarget = 0F;
        if (level.dimensionType().hasSkyLight()) {
            BlockPos pos = player.blockPosition();
            Biome biome = level.getBiome(pos).value();
            Biome.Precipitation kind = biome.getPrecipitationAt(pos);
            if (kind == Biome.Precipitation.SNOW) {
                snowTarget = Mth.clamp((thunder - 0.2F) / 0.4F, 0F, 1F);
            } else if (kind == Biome.Precipitation.NONE && biome.getBaseTemperature() >= 0.5F) {
                sandTarget = Mth.clamp((rain - 0.05F) / 0.45F, 0F, 1F) * (0.6F + 0.4F * thunder);
            }
            float sky = Mth.clamp((level.getBrightness(LightLayer.SKY, pos) - 8F) / 7F, 0F, 1F);
            exposure += (sky - exposure) * 0.12F;
        } else {
            exposure = 0F;
        }
        blizzardWeather = ease(blizzardWeather, snowTarget);
        sandWeather = ease(sandWeather, sandTarget);

        float snow = blizzardShown(), sand = sandShown();
        float seconds = Wind.seconds(level, 1F);
        float gust = Wind.gust(seconds);
        if (snow > 0.01F && usable()) {
            retune(0.2F * snow * (float) Mth.clamp(FxConfig.blizzardStrength, 0.0, 1.5) * (0.75F + 0.5F * gust), 0.5F + 0.12F * gust);
        } else if (sand > 0.01F && usable()) {
            retune(0.17F * sand * (float) Mth.clamp(FxConfig.sandstormStrength, 0.0, 1.5) * (0.75F + 0.5F * gust), 0.72F + 0.1F * gust);
        } else {
            retune(0F, howlPitch);
        }
        if (snow > 0.01F || sand > 0.01F) {
            float heavy = Mth.clamp(snow > sand
                            ? (float) FxConfig.blizzardStrength / 1.5F
                            : (float) FxConfig.sandstormStrength * 0.65F + thunder * 0.45F, 0F, 1F);
            boolean wide = FxConfig.stormRange;
            fogEnd = snow > sand ? Mth.lerp(heavy, wide ? 64F : 40F, wide ? 40F : 28F) : (wide ? 62F - 26F * heavy : 45F - 20F * heavy);
        }
    }

    private static float ease(float current, float target) {
        return current + Mth.clamp(target - current, -FALL, RISE);
    }

    private static void retune(float volume, float pitch) {
        howlPitch = pitch;
        howlVolume = volume;
        if (volume > 0.004F && (howl == null || howl.isStopped())) {
            howl = new Howl();
            Minecraft.getInstance().getSoundManager().play(howl);
        }
    }

    public static float fogK() {
        if (!usable()) return 0F;
        return Math.max(blizzardShown(), sandShown());
    }

    public static float fogNear(float near) {
        float k = fogK();
        return k > 0.001F ? Mth.lerp(k, near, Math.min(near, fogEnd * 0.05F)) : near;
    }

    public static float fogFar(float far) {
        float k = fogK();
        return k > 0.001F ? Mth.lerp(k, far, Math.min(far, fogEnd)) : far;
    }

    public static float[] fogColor(ClientLevel level, float partialTick, float r, float g, float b) {
        float snow = usable() ? blizzardShown() : 0F, sand = usable() ? sandShown() : 0F;
        float k = Math.max(snow, sand);
        if (k <= 0.001F) return null;
        float[] base = snow >= sand ? SNOW_FOG : SAND_FOG;
        float day = Mth.clamp((Mth.cos(level.getTimeOfDay(partialTick) * Mth.TWO_PI) + 0.15F) / 0.5F, 0F, 1F);
        day = day * day * (3F - 2F * day);
        float luma = Mth.lerp(day, 0.07F, 0.8F) * (1F - 0.2F * level.getThunderLevel(partialTick));
        float norm = luma / (base[0] * 0.299F + base[1] * 0.587F + base[2] * 0.114F);
        float mix = Math.min(1F, k) * 0.92F;
        FOG_OUT[0] = Mth.lerp(mix, r, Math.min(1F, base[0] * norm));
        FOG_OUT[1] = Mth.lerp(mix, g, Math.min(1F, base[1] * norm));
        FOG_OUT[2] = Mth.lerp(mix, b, Math.min(1F, base[2] * norm));
        return FOG_OUT;
    }

    public static void onRenderWeatherStormsStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        ClientLevel level = Minecraft.getInstance().level;
        float snow = blizzardShown(), sand = sandShown();
        if (level == null || !usable() || LightTint.lightmap == null || !SkyProgram.STORM_AIR.ready()) {
            Diagnostics.skip(Diagnostics.BLIZZARD, FxConfig.blizzard ? "fog off, shader pack, or no shader" : "disabled");
            Diagnostics.skip(Diagnostics.SANDSTORM, FxConfig.sandstorm ? "fog off, shader pack, or no shader" : "disabled");
            return;
        }
        if (snow < 0.01F) Diagnostics.skip(Diagnostics.BLIZZARD, FxConfig.blizzard ? "no blizzard here" : "disabled");
        if (sand < 0.01F) Diagnostics.skip(Diagnostics.SANDSTORM, FxConfig.sandstorm ? "no sandstorm here" : "disabled");
        boolean blizzard = snow >= sand;
        float intensity = blizzard ? snow : sand;
        if (intensity < 0.01F) return;
        float strength = (float) Mth.clamp(blizzard ? FxConfig.blizzardStrength : FxConfig.sandstormStrength, 0.0, 2.0);
        int fine = (int) Math.min(FINE, 5200F * intensity * strength);
        int large = (int) Math.min(LARGE, (blizzard ? 480F : 620F) * intensity * strength);
        float reach = intensity * Math.min(strength, 1.5F);
        boolean wide = FxConfig.stormRange;
        int near = Math.min(NEAR, Math.round((blizzard ? 70F : 90F) * reach));
        int mid = wide ? Math.min(MID, Math.round((blizzard ? 160F : 200F) * reach)) : 0;
        int far = wide ? Math.min(FAR, Math.round((blizzard ? 60F : 70F) * reach)) : 0;
        int streaks = wide ? Math.min(STREAKS, Math.round((blizzard ? 300F : 240F) * reach)) : 0;
        int vertices = (fine + large + near + mid + far + streaks) * 6;
        if (vertices <= 0) return;

        float partialTick = event.getPartialTick();
        float seconds = Wind.seconds(level, partialTick);
        float gust = Wind.gust(seconds);
        float angle = Wind.angle(seconds);
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        double blockX = Math.floor(eye.x), blockY = Math.floor(eye.y), blockZ = Math.floor(eye.z);
        float fx = (float) (eye.x - blockX), fy = (float) (eye.y - blockY), fz = (float) (eye.z - blockZ);
        Matrix4f projection = event.getProjectionMatrix();
        MATRIX.set(projection).mul(event.getPoseStack().last().pose()).translate(-fx, -fy, -fz);
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        int height = Math.max(Minecraft.getInstance().getMainRenderTarget().height, 1);
        float pixel = 2F / (Math.max(projection.m11(), 0.01F) * height);

        SkyProgram program = SkyProgram.STORM_AIR;
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        boolean heights = Puddles.heightsLive() && SkyHeights.texture() != 0;
        program.begin();
        SkyProgram.texture(2, heights ? SkyHeights.texture() : 0);
        SkyProgram.texture(1, LightTint.lightmap.getId());
        SkyProgram.texture(0, SkyProgram.texture(ATLAS));
        MATRIX.get(UPLOAD);
        GL20.glUniformMatrix4fv(program.uniform(0), false, UPLOAD);
        GL20.glUniform4f(program.uniform(1), Math.floorMod((int) blockX, 56), Math.floorMod((int) blockY, 28), Math.floorMod((int) blockZ, 56),
                (float) (Wind.time() % 3600.0));
        GL20.glUniform4f(program.uniform(2), fx, fy, fz, pixel);
        if (blizzard) {
            GL20.glUniform4f(program.uniform(3), Mth.cos(angle), Mth.sin(angle), 15F * (0.8F + 0.4F * gust), 1.6F);
            GL20.glUniform4f(program.uniform(4), 0F, 1.6F, 1F, 0F);
            GL20.glUniform4f(program.uniform(6), 0.93F, 0.97F, 1F, 1F);
        } else {
            GL20.glUniform4f(program.uniform(3), Mth.cos(angle), Mth.sin(angle), 22F * (0.75F + 0.5F * gust), 0.5F);
            GL20.glUniform4f(program.uniform(4), 1F, 0.6F, 1F, 0F);
            GL20.glUniform4f(program.uniform(6), 0.93F, 0.78F, 0.55F, 1F);
        }
        long now = Util.getNanos();
        float dt = driftAt == 0L || Minecraft.getInstance().isPaused() ? 0F : Math.min((now - driftAt) / 1.0E9F, 0.25F);
        driftAt = now;
        float push = (blizzard ? 15F * (0.8F + 0.4F * gust) : 22F * (0.75F + 0.5F * gust)) * dt;
        driftX += Mth.cos(angle) * push;
        driftZ += Mth.sin(angle) * push;
        GL20.glUniform4f(program.uniform(15), wrap(driftX, 56.0), wrap(driftZ, 56.0), wrap(driftX * (blizzard ? 0.85 : 1.0), 56.0), wrap(driftZ * (blizzard ? 0.85 : 1.0), 56.0));
        GL20.glUniform4f(program.uniform(16), wrap(driftX * 0.35, 56.0), wrap(driftZ * 0.35, 56.0), wrap(driftX * 0.16, 160.0), wrap(driftZ * 0.16, 160.0));
        GL20.glUniform4f(program.uniform(17), wrap(driftX * 0.07, 384.0), wrap(driftZ * 0.07, 384.0), wrap(driftX * 0.9, 160.0), wrap(driftZ * 0.9, 160.0));
        GL20.glUniform4f(program.uniform(5), fine, large, near, mid);
        GL20.glUniform4f(program.uniform(11), Math.floorMod((int) blockX, 160), Math.floorMod((int) blockZ, 160),
                Math.floorMod((int) blockX, 384), Math.floorMod((int) blockZ, 384));
        GL20.glUniform4f(program.uniform(12), (float) blockX, (float) blockZ, (float) blockY, heights ? 1F : 0F);
        GL20.glUniform4f(program.uniform(13), far, streaks, groundRef, 0F);
        GL20.glUniform2f(program.uniform(14), SkyHeights.centerX(), SkyHeights.centerZ());
        GL20.glUniform3f(program.uniform(7), -left.x(), -left.y(), -left.z());
        GL20.glUniform3f(program.uniform(8), up.x(), up.y(), up.z());
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.uniform(9), fog[0], fog[1], fog[2], 1F);
        GL20.glUniform4f(program.uniform(10), RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), 0F, 0F);
        program.draw(vertices);
        program.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        int slot = blizzard ? Diagnostics.BLIZZARD : Diagnostics.SANDSTORM;
        Diagnostics.mark(slot, Diagnostics.detail() ? String.format("intensity %.2f, %d+%d+%d, ring %d+%d+%d, fog end %.0f", intensity, fine, large, near, mid, far, streaks, fogEnd) : null);
    }

    private static final class Howl extends AbstractTickableSoundInstance {
        Howl() {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = 0.5F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            volume += (howlVolume - volume) * 0.1F;
            pitch += (howlPitch - pitch) * 0.1F;
            if (howlVolume <= 0.004F && volume < 0.006F) stop();
        }
    }

    private static float wrap(double v, double box) {
        return (float) (v - Math.floor(v / box) * box);
    }
}
