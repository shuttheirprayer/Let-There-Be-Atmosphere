package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class EndAir {
    private EndAir() {}

    private static final Matrix4f INVERSE = new Matrix4f();
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static boolean captured;

    private static final float[] LILAC = {0.22F, 0.125F, 0.39F};
    private static final float[] DEEP = {0.075F, 0.03F, 0.17F};

    static boolean here(ClientLevel level) {
        return level != null && level.effects().skyType() == DimensionSpecialEffects.SkyType.END;
    }

    public static boolean fog(ClientLevel level) {
        return FxConfig.endFog && here(level);
    }

    public static float fogNear() {
        return Mth.clamp(32F / (float) FxConfig.endFogDensity, 12F, 64F);
    }

    public static float fogFar() {
        return Mth.clamp(135F / (float) FxConfig.endFogDensity, 70F, 200F);
    }

    public static void tint(float[] rgb, float biomeR, float biomeG, float biomeB, float voidDepth) {
        float scale = (LILAC[0] * 0.3F + LILAC[1] * 0.59F + LILAC[2] * 0.11F) / Math.max(biomeR * 0.3F + biomeG * 0.59F + biomeB * 0.11F, 0.05F);
        float[] biome = {biomeR, biomeG, biomeB};
        for (int i = 0; i < 3; i++) {
            float target = Mth.lerp(0.6F, Math.min(1F, biome[i] * scale), LILAC[i]);
            rgb[i] = Mth.lerp(voidDepth, Mth.lerp(0.85F, rgb[i], target), DEEP[i]);
        }
    }

    public static void onRenderEndAirCapture(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        captured = here(Minecraft.getInstance().level) && (FxConfig.endMist || FxConfig.endShadows);
        if (!captured) return;
        INVERSE.set(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose()).invert();
    }

    public static void onRenderEndAirStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!here(level) || !(FxConfig.endMist || FxConfig.endShadows)) {
            Diagnostics.skip(Diagnostics.END_AIR, "disabled or not the End");
            return;
        }
        SkyProgram program = SkyProgram.END_AIR;
        if (!captured || !DynamicFogHandler.active() || ColorGrade.shaderPackActive() || !program.ready()) {
            Diagnostics.skip(Diagnostics.END_AIR, program.ready() ? "fog handler off or a shader pack owns the image" : "shader failed to load, see the log");
            return;
        }
        if (SkyGate.air(event, Diagnostics.END_AIR) == null) return;

        Vec3 camera = event.getCamera().getPosition();
        double seconds = (Util.getMillis() % 3_600_000L) / 1000.0;
        float mist = FxConfig.endMist ? (float) FxConfig.endMistAmount : 0F;
        float shadow = FxConfig.endShadows ? (float) FxConfig.endShadowAmount : 0F;
        float presence = Mth.clamp(smooth(0.55F, 0.9F, 0.5F + 0.5F * (float) Math.sin(seconds * 0.05 + 1.2)) * shadow, 0F, 1.5F);

        RenderTarget main = mc.getMainRenderTarget();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        program.begin();
        SkyProgram.texture(1, Haze.noiseTexture());
        SkyProgram.texture(0, main.getDepthTextureId());
        INVERSE.get(MATRIX);
        GL20.glUniformMatrix4fv(program.uniform(0), false, MATRIX);
        GL20.glUniform4f(program.uniform(1), (float) Math.floorMod((long) Math.floor(camera.x), 4096L) + (float) (camera.x - Math.floor(camera.x)),
                (float) camera.y, (float) Math.floorMod((long) Math.floor(camera.z), 4096L) + (float) (camera.z - Math.floor(camera.z)), (float) seconds);
        GL20.glUniform4f(program.uniform(2), 0F, 0F, mist, 5F);
        GL20.glUniform4f(program.uniform(3), wrap(seconds * 0.9 / 256.0), wrap(seconds * 0.15 / 128.0), presence, 0F);
        GL20.glUniform4f(program.uniform(4), wrap(seconds * 0.35 / 32.0), wrap(seconds * 0.1 / 32.0),
                wrap(-seconds * 0.2 / 64.0), wrap(seconds * 0.25 / 64.0));
        GL20.glUniform4f(program.uniform(5), mist, 0F, 0F, 0F);
        program.draw(3);
        program.end();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        Diagnostics.mark(Diagnostics.END_AIR, Diagnostics.detail() ? String.format("mist %.2f, shadow %.2f", mist, presence) : null);
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    private static float wrap(double v) {
        return (float) (v - Math.floor(v));
    }
}
