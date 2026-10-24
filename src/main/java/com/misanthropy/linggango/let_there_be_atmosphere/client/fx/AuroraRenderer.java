package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class AuroraRenderer {
    private AuroraRenderer() {}

    private static final float SKY_SCALE = 1F / 512F;
    private static final double HEIGHT_OFFSET = -4000.0;
    private static final double LIFT = 1.66;
    private static final double SEA_LEVEL = 64.0;
    private static final double DISTANCE_FACTOR = 1.5;
    private static final double WAVE_AMPLITUDE = 1200.0;
    private static final double TWO_PI = 2.0 * Math.PI;
    private static final float BASE_GLOW_FREQUENCY = 20F;
    private static final float BASE_GLOW_RATE = 0.02F;
    private static final float BASE_GLOW_AMP = 0.6F;

    private static final Matrix4f MATRIX = new Matrix4f();
    private static final int U_TRANSFORM = 0;
    private static final int U_SHAPE = 1;
    private static final int U_LAYOUT = 2;
    private static final int U_TOP = 3;
    private static final int U_MID = 4;
    private static final int U_LOW = 5;
    private static final int U_BAND = 6;
    private static final int U_PHASE = 7;
    private static final int U_GLOW = 8;
    private static final int PHASE_STRIDE = Aurora.GLOW_CAPACITY + 1;
    private static final int RISE_ROWS = Aurora.MAX_BANDS + 2;
    private static final int WAVE_ROWS = Aurora.MAX_BANDS;
    private static final int RISE_ROW = 0;
    private static final int RISE_PREV_ROW = RISE_ROW + RISE_ROWS;
    private static final int WAVE_ROW = RISE_PREV_ROW + RISE_ROWS;
    private static final int WAVE_PREV_ROW = WAVE_ROW + WAVE_ROWS;
    private static final int NOISE_ROWS = WAVE_PREV_ROW + WAVE_ROWS;
    private static final float[] BAND = new float[Aurora.MAX_BANDS * 4];
    private static final float[] PHASE = new float[Aurora.MAX_BANDS * PHASE_STRIDE];
    private static final float[] GLOW = new float[PHASE_STRIDE * 2];
    private static int noise;
    private static int uploaded;

    private static float nightAmount(float dayTimeTicks) {
        return Mth.clamp(DayCurves.starBrightness(dayTimeTicks) * 2F, 0F, 1F);
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || !DynamicFogHandler.active() || !FxConfig.auroraEnabled) {
            Aurora.reset();
            return;
        }
        if (!level.dimensionType().hasSkyLight()
                || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            Aurora.sleep("this dimension has no normal sky");
            return;
        }
        if (mc.isPaused()) return;

        long dayTime = level.getDayTime();
        float night = nightAmount(dayTime % DayCurves.PERIOD);
        if (night <= 0F) {
            Aurora.sleep("daylight");
            return;
        }

        float temperature = level.getBiome(player.blockPosition()).value().getBaseTemperature();
        int detail = mc.options.graphicsMode().get() == GraphicsStatus.FAST ? Aurora.DETAIL_FAST : Aurora.DETAIL;
        Aurora.tick(dayTime, temperature, player.getX(), detail, night, level.getRainLevel(1F),
                DynamicFogHandler.skyVisibility(), FxConfig.auroraSpeed, FxConfig.auroraFrequency,
                FxConfig.auroraBrightness);
    }

    public static void onRenderSkyStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (!DynamicFogHandler.active() || !FxConfig.auroraEnabled) {
            Diagnostics.skip(Diagnostics.AURORA, "disabled");
            return;
        }
        if (!Aurora.ready()) {
            Diagnostics.skip(Diagnostics.AURORA, Aurora.quiet());
            return;
        }

        ClientLevel level = SkyGate.sky(event, Diagnostics.AURORA, true);
        if (level == null) return;

        float partialTick = event.getPartialTick();
        float n = Aurora.north(partialTick);
        float strength = Aurora.strength(nightAmount(level.getDayTime() % DayCurves.PERIOD + partialTick), n,
                level.getRainLevel(partialTick), DynamicFogHandler.skyVisibility(),
                FxConfig.auroraFrequency, FxConfig.auroraBrightness);
        if (strength < 0.01F) {
            Diagnostics.skip(Diagnostics.AURORA, "too faint right now");
            return;
        }

        float poleDistance = Aurora.poleDistance();
        double lift = (poleDistance * 0.25F + HEIGHT_OFFSET) * (1F - n * 0.5F);
        double reach = -(1.0 - n) * poleDistance;
        double eyeY = event.getCamera().getPosition().y;
        MATRIX.set(event.getPoseStack().last().pose())
                .scale(SKY_SCALE)
                .translate(0F, (float) (lift * LIFT - (eyeY - SEA_LEVEL)), (float) (reach * DISTANCE_FACTOR));

        draw(n, poleDistance, strength, partialTick);
        Diagnostics.mark(Diagnostics.AURORA, Diagnostics.detail()
                ? String.format("%d bands, strength %.2f, north %.2f", Aurora.bands(), strength, n) : null);
    }

    private static void draw(float n, float poleDistance, float strength, float partialTick) {
        SkyProgram program = SkyProgram.AURORA;
        if (!program.ready()) return;
        int bands = Aurora.bands();
        int detail = Aurora.detail();
        if (bands <= 0 || detail < 2) return;

        double n2 = (double) n * n;
        double n4 = n2 * n2;
        double n8 = n4 * n4;
        double n12 = n8 * n4;
        float radiusX = (float) (poleDistance * -0.25 * n12);
        float radiusZ = (float) (poleDistance * -0.25 * n8);
        float stretch = (float) (1.0 - n12);
        float waveAmp = (float) (WAVE_AMPLITUDE * n);
        float edge = 0.5F - 0.4F * (float) n8;

        int glows = Aurora.glowCount();
        GLOW[0] = BASE_GLOW_AMP;
        GLOW[1] = BASE_GLOW_FREQUENCY;
        for (int i = 0; i < glows; i++) {
            GLOW[2 + i * 2] = Aurora.glowAmp(i);
            GLOW[3 + i * 2] = Aurora.glowFrequency(i);
        }
        for (int b = 0; b < bands; b++) {
            int o = b * 4;
            BAND[o] = strength * Aurora.bandStrength(b);
            BAND[o + 1] = poleDistance * 0.5F * Aurora.bandWidth(b) + 1.5F;
            BAND[o + 2] = poleDistance * 0.02F * Aurora.bandHeight(b);
            double time = Aurora.glowTime(partialTick) + Aurora.bandPhase(b);
            int p = b * PHASE_STRIDE;
            PHASE[p] = (float) (time * BASE_GLOW_RATE % TWO_PI);
            for (int i = 0; i < glows; i++) PHASE[p + 1 + i] = (float) (time * Aurora.glowRate(i) % TWO_PI);
        }

        program.begin();
        uploadNoise();
        program.transform(U_TRANSFORM, MATRIX);
        GL20.glUniform4f(program.uniform(U_SHAPE), radiusX, radiusZ, stretch, waveAmp);
        GL20.glUniform4f(program.uniform(U_LAYOUT), detail, edge, partialTick, glows);
        color(program.uniform(U_TOP), Aurora.TOP);
        color(program.uniform(U_MID), Aurora.MID);
        color(program.uniform(U_LOW), Aurora.LOW);
        GL20.glUniform4fv(program.uniform(U_BAND), BAND);
        GL20.glUniform1fv(program.uniform(U_PHASE), PHASE);
        GL20.glUniform2fv(program.uniform(U_GLOW), GLOW);

        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        program.draw(bands * (detail - 1) * 24);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        program.end();
    }

    private static void uploadNoise() {
        if (noise == 0) {
            noise = GlStateManager._genTexture();
            GlStateManager._bindTexture(noise);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, Aurora.stride(), NOISE_ROWS, 0,
                    GL11.GL_RED, GL11.GL_FLOAT, null);
            uploaded = Aurora.revision() - 1;
        }
        SkyProgram.texture(0, noise);
        if (uploaded == Aurora.revision()) return;
        uploaded = Aurora.revision();
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        int width = Aurora.stride();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, RISE_ROW, width, RISE_ROWS, GL11.GL_RED, GL11.GL_FLOAT, Aurora.riseRows());
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, RISE_PREV_ROW, width, RISE_ROWS, GL11.GL_RED, GL11.GL_FLOAT, Aurora.previousRiseRows());
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, WAVE_ROW, width, WAVE_ROWS, GL11.GL_RED, GL11.GL_FLOAT, Aurora.waveRows());
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, WAVE_PREV_ROW, width, WAVE_ROWS, GL11.GL_RED, GL11.GL_FLOAT, Aurora.previousWaveRows());
    }

    private static void color(int location, int offset) {
        GL20.glUniform3f(location, (int) (Aurora.color(offset) * 255F) / 255F,
                (int) (Aurora.color(offset + 1) * 255F) / 255F, (int) (Aurora.color(offset + 2) * 255F) / 255F);
    }
}
