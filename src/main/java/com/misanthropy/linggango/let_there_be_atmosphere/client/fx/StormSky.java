package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class StormSky {
    private StormSky() {}

    private static final double WRAP = 307200.0;
    private static final Matrix4f INVERSE = new Matrix4f();
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(16);
    private static final RandomSource RANDOM = RandomSource.create();

    private static double deckX, deckZ, scudX, scudZ;
    private static long drawnAt;
    private static int nextSheet = 100;
    private static int rumble;
    private static float rumbleX, rumbleY, rumbleZ, rumbleVolume;

    public static void onStormSkyTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || !FxConfig.stormClouds || !DynamicFogHandler.active() || mc.gameRenderer == null) return;
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        if (rumble > 0 && --rumble == 0) {
            level.playLocalSound(eye.x + rumbleX * 10.0, eye.y + rumbleY * 10.0, eye.z + rumbleZ * 10.0, SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.WEATHER, rumbleVolume, 0.5F + RANDOM.nextFloat() * 0.25F, false);
        }
        float thunder = level.getThunderLevel(1F);
        if (thunder < 0.3F || !level.dimensionType().hasSkyLight() || --nextSheet > 0) return;
        nextSheet = 70 + RANDOM.nextInt(230);
        float yaw = RANDOM.nextFloat() * Mth.TWO_PI;
        float pitch = 0.2F + RANDOM.nextFloat() * 0.7F;
        float x = Mth.cos(yaw) * Mth.cos(pitch), y = Mth.sin(pitch), z = Mth.sin(yaw) * Mth.cos(pitch);
        float amp = (0.22F + RANDOM.nextFloat() * 0.3F) * thunder;
        if (Lightning.sheet(x, y, z, amp, 1 + RANDOM.nextInt(3))) {
            rumbleX = x;
            rumbleY = y;
            rumbleZ = z;
            rumbleVolume = 0.25F + amp;
            rumble = 20 + RANDOM.nextInt(50);
        }
    }

    public static void onRenderStormSkyStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) return;
        if (!FxConfig.stormClouds || !DynamicFogHandler.active() || ColorGrade.shaderPackActive()) {
            Diagnostics.skip(Diagnostics.STORM, FxConfig.stormClouds ? "fog handler off or a shader pack owns the image" : "disabled");
            return;
        }
        ClientLevel level = SkyGate.sky(event, Diagnostics.STORM, true);
        if (level == null) return;
        float partialTick = event.getPartialTick();
        float rain = level.getRainLevel(partialTick);
        float thunder = level.getThunderLevel(partialTick);
        float strength = Mth.clamp((float) FxConfig.stormCloudsStrength * SkyGlow.smooth(0.05F, 0.9F, rain) * (0.8F + 0.2F * thunder), 0F, 1F);
        SkyProgram program = SkyProgram.STORM_SKY;
        if (strength < 0.004F || !program.ready()) {
            Diagnostics.skip(Diagnostics.STORM, program.ready() ? "not raining" : "shader failed to load, see the log");
            drawnAt = 0L;
            return;
        }
        long now = Util.getNanos();
        float dt = drawnAt == 0L ? 0F : Math.min((now - drawnAt) / 1.0E9F, 0.25F);
        drawnAt = now;
        float seconds = Wind.seconds(level, partialTick);
        float angle = Wind.angle(seconds);
        float wx = Mth.cos(angle), wz = Mth.sin(angle);
        float speed = (4F + 8F * thunder) * (0.6F + 0.6F * Wind.gust(seconds));
        deckX = (deckX + wx * speed * dt) % WRAP;
        deckZ = (deckZ + wz * speed * dt) % WRAP;
        scudX = (scudX + wx * speed * 2.6F * dt) % WRAP;
        scudZ = (scudZ + wz * speed * 2.6F * dt) % WRAP;

        Vec3 eye = event.getCamera().getPosition();
        float cloudHeight = level.effects().getCloudHeight();
        float base = Float.isNaN(cloudHeight) ? 192F : cloudHeight;
        Vec3 sky = level.getSkyColor(eye, partialTick);
        float lum = Math.max(0.03F, (float) (0.2126 * sky.x + 0.7152 * sky.y + 0.0722 * sky.z));
        float[] fog = RenderSystem.getShaderFogColor();

        INVERSE.set(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose()).invert();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        program.begin();
        SkyProgram.texture(0, Haze.noiseTexture());
        INVERSE.get(UPLOAD);
        GL20.glUniformMatrix4fv(program.uniform(0), false, UPLOAD);
        GL20.glUniform4f(program.uniform(1), base + 40F - (float) eye.y, (float) ((eye.x + deckX) % WRAP), (float) ((eye.z + deckZ) % WRAP), strength);
        GL20.glUniform4f(program.uniform(2), base - 30F - (float) eye.y, (float) ((eye.x + scudX) % WRAP), (float) ((eye.z + scudZ) % WRAP),
                0.65F * Mth.sqrt(thunder) * strength);
        float sand = WeatherStorms.sandstorm(), snow = WeatherStorms.blizzard();
        float day = Mth.clamp(Mth.cos(level.getTimeOfDay(partialTick) * Mth.TWO_PI) * 2.5F + 0.4F, 0.12F, 1F);
        float darkR = 0.36F * lum, darkG = 0.4F * lum, darkB = 0.55F * lum;
        float lightR = Mth.lerp(0.6F, (float) sky.x, 1.2F * lum), lightG = Mth.lerp(0.6F, (float) sky.y, 1.26F * lum), lightB = Mth.lerp(0.6F, (float) sky.z, 1.4F * lum);
        darkR = Mth.lerp(sand, Mth.lerp(snow, darkR, 0.62F * day), 0.66F * day);
        darkG = Mth.lerp(sand, Mth.lerp(snow, darkG, 0.68F * day), 0.52F * day);
        darkB = Mth.lerp(sand, Mth.lerp(snow, darkB, 0.78F * day), 0.36F * day);
        lightR = Mth.lerp(sand, Mth.lerp(snow, lightR, 0.86F * day), 0.95F * day);
        lightG = Mth.lerp(sand, Mth.lerp(snow, lightG, 0.9F * day), 0.82F * day);
        lightB = Mth.lerp(sand, Mth.lerp(snow, lightB, 0.96F * day), 0.62F * day);
        GL20.glUniform4f(program.uniform(3), darkR, darkG, darkB, 1F);
        GL20.glUniform4f(program.uniform(4), lightR, lightG, lightB, 1F);
        GL20.glUniform4f(program.uniform(5), fog[0], fog[1], fog[2], 1F);
        GL20.glUniform4f(program.uniform(6), Lightning.dirX(), Lightning.dirY(), Lightning.dirZ(), Lightning.flashOrZero() * 1.6F);
        program.draw(3);
        program.end();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        Diagnostics.mark(Diagnostics.STORM, Diagnostics.detail() ? String.format("deck %.2f, sheet in %d ticks", strength, nextSheet) : null);
    }
}
