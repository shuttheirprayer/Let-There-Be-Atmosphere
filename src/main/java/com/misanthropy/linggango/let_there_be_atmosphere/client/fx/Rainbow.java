package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class Rainbow {
    private Rainbow() {}

    private static final float START_RAIN = 0.6F;
    private static final float CLEAR_RAIN = 0.35F;
    private static final float MAX_LIFT = 0.12F;
    private static final int LIFE = 3600;
    private static final float FADE_IN = 240F;
    private static final float FADE_OUT = 1000F;
    private static final int SEGMENTS = 96;
    private static final int BANDS = 15;

    private static final Matrix4f MATRIX = new Matrix4f();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    private static boolean armed;
    private static int age = -1;
    private static float side = 1F;

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || !DynamicFogHandler.active() || !FxConfig.rainbowsEnabled
                || !level.dimensionType().hasSkyLight()
                || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            armed = false;
            age = -1;
            return;
        }
        if (mc.isPaused()) return;

        float rain = level.getRainLevel(1F);
        if (rain >= START_RAIN) armed = true;
        if (age >= 0) {
            if (++age > LIFE || rain >= START_RAIN) age = -1;
            return;
        }
        if (!armed || rain >= CLEAR_RAIN) return;
        armed = false;
        float sun = Mth.cos(level.getTimeOfDay(1F) * Mth.TWO_PI);
        if (sun <= 0.02F || level.random.nextFloat() >= FxConfig.rainbowChance) return;
        side = Mth.sin(level.getTimeOfDay(1F) * Mth.TWO_PI) > 0F ? -1F : 1F;
        CURSOR.set(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        if (level.getBiome(CURSOR).value().getPrecipitationAt(CURSOR) == Biome.Precipitation.RAIN) age = 0;
    }

    public static void onRenderRainbowStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) return;
        if (age < 0) {
            Diagnostics.skip(Diagnostics.RAINBOW, FxConfig.rainbowsEnabled ? "no rain has just cleared" : "disabled");
            return;
        }
        ClientLevel level = SkyGate.sky(event, Diagnostics.RAINBOW, true);
        if (level == null) return;

        float partialTick = event.getPartialTick();
        float t = age + partialTick;
        float envelope = Math.min(t / FADE_IN, 1F) * Math.min((LIFE - t) / FADE_OUT, 1F);
        float angle = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        float sunY = Mth.cos(angle);
        float strength = SkyGlow.smooth(0F, 1F, envelope) * SkyGlow.smooth(-0.02F, 0.06F, sunY)
                * (1F - SkyGlow.smooth(CLEAR_RAIN, START_RAIN, level.getRainLevel(partialTick))) * DynamicFogHandler.skyVisibility()
                * (float) FxConfig.rainbowBrightness;
        if (strength < 0.004F) {
            Diagnostics.skip(Diagnostics.RAINBOW, "too faint right now");
            return;
        }

        MATRIX.set(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
        float lift = Math.min((float) Math.asin(Mth.clamp(sunY, -1F, 1F)), MAX_LIFT);
        draw(side * Mth.cos(lift), Mth.sin(lift), strength);

        Diagnostics.mark(Diagnostics.RAINBOW, Diagnostics.detail()
                ? String.format("strength %.2f, %d of %d ticks", strength, age, LIFE) : null);
    }

    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(16);

    private static void draw(float sunX, float sunY, float strength) {
        SkyProgram program = SkyProgram.RAINBOW;
        if (!program.ready()) return;
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        program.begin();
        MATRIX.get(UPLOAD);
        GL20.glUniformMatrix4fv(program.uniform(0), false, UPLOAD);
        GL20.glUniform3f(program.uniform(1), -sunX, -sunY, strength);
        program.draw(BANDS * SEGMENTS * 6);
        program.end();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}
