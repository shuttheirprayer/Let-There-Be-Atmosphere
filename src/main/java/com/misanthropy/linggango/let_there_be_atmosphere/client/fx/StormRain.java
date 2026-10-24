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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class StormRain {
    private StormRain() {}

    private static final ResourceLocation ATLAS = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/storm_rain.png");
    private static final int RADIUS = 20;
    private static final int TRAINS = 10;
    private static final int CELLS = (RADIUS * 2 + 1) * (RADIUS * 2 + 1);
    private static final int MID_CELLS = 33 * 33 * 2;
    private static final float WINDOW = 16F;
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(16);

    private static float drizzle;
    private static long drawnAt;
    private static float slantX, slantZ, lagX, lagZ;

    public static boolean hidesVanilla() {
        return FxConfig.stormRain && DynamicFogHandler.active() && Puddles.heightsLive() && LightTint.lightmap != null
                && SkyProgram.STORM_RAIN.ready() && !ColorGrade.shaderPackActive();
    }

    public static void onRenderStormRainStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !hidesVanilla()) {
            Diagnostics.skip(Diagnostics.RAIN, FxConfig.stormRain ? "heights not ready, or a shader pack owns the image" : "disabled");
            return;
        }
        float partialTick = event.getPartialTick();
        float rain = level.getRainLevel(partialTick);
        float thunder = level.getThunderLevel(partialTick);
        long now = Util.getNanos();
        float dt = Math.min((now - drawnAt) / 1.0E9F, 0.25F);
        drizzle = Math.max(rain, drizzle - dt / 45F);
        drawnAt = now;
        float density = Mth.clamp(Math.max(rain * (0.4F + 0.6F * thunder), drizzle * 0.3F) * (float) FxConfig.stormRainAmount, 0F, 1F);
        if (density < 0.004F) {
            Diagnostics.skip(Diagnostics.RAIN, "not raining");
            return;
        }
        float seconds = Wind.seconds(level, partialTick);
        float gust = Wind.gust(seconds);
        float angle = Wind.angle(seconds);
        float slant = 0.08F + 0.14F * gust + thunder * (0.4F + 0.5F * gust);

        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        float follow = 1F - (float) Math.exp(-dt / 0.6F), linger = 1F - (float) Math.exp(-dt / 2.5F);
        slantX += (Mth.cos(angle) * slant - slantX) * follow;
        slantZ += (Mth.sin(angle) * slant - slantZ) * follow;
        lagX += (slantX - lagX) * linger;
        lagZ += (slantZ - lagZ) * linger;
        double blockX = Math.floor(eye.x + slantX * 5F), blockY = Math.floor(eye.y), blockZ = Math.floor(eye.z + slantZ * 5F);
        float fx = (float) (eye.x - blockX), fy = (float) (eye.y - blockY), fz = (float) (eye.z - blockZ);
        Matrix4f projection = event.getProjectionMatrix();
        MATRIX.set(projection).mul(event.getPoseStack().last().pose()).translate(-fx, -fy, -fz);
        Vector3f left = camera.getLeftVector();
        int height = Math.max(Minecraft.getInstance().getMainRenderTarget().height, 1);
        float pixel = 2F / (Math.max(projection.m11(), 0.01F) * height);

        SkyProgram program = SkyProgram.STORM_RAIN;
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        program.begin();
        SkyProgram.texture(2, SkyHeights.texture());
        SkyProgram.texture(1, LightTint.lightmap.getId());
        SkyProgram.texture(0, SkyProgram.texture(ATLAS));
        MATRIX.get(UPLOAD);
        GL20.glUniformMatrix4fv(program.uniform(0), false, UPLOAD);
        GL20.glUniform4f(program.uniform(1), (float) blockX, (float) blockZ, (float) blockY, (float) (Wind.time() % 3600.0));
        GL20.glUniform4f(program.uniform(2), fx, fy, fz, pixel);
        GL20.glUniform4f(program.uniform(3), slantX, slantZ, 1F + 0.2F * thunder, density);
        GL20.glUniform4f(program.uniform(4), FxConfig.rainSplashes ? 1F : 0F, RADIUS, WINDOW, 0.14F);
        GL20.glUniform2f(program.uniform(5), SkyHeights.centerX(), SkyHeights.centerZ());
        GL20.glUniform3f(program.uniform(6), -left.x(), -left.y(), -left.z());
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.uniform(7), fog[0], fog[1], fog[2], 1F);
        GL20.glUniform4f(program.uniform(8), RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), 0F, 0F);
        int base = (int) Math.ceil(density * TRAINS) * CELLS;
        float midK = FxConfig.stormRange ? Mth.clamp((density - 0.15F) / 0.5F, 0F, 1F) : 0F;
        int mid = midK > 0.02F ? MID_CELLS : 0;
        GL20.glUniform4f(program.uniform(9), base, mid, 0F, 0F);
        GL20.glUniform4f(program.uniform(10), midK, 0F, (float) Math.floor(eye.y - 1.5), 0F);
        GL20.glUniform4f(program.uniform(11), lagX, lagZ, Mth.clamp(gust * (0.3F + 0.7F * thunder), 0F, 1F), 0.55F + 1.0F * thunder);
        program.draw((base + mid) * 6);
        program.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        Diagnostics.mark(Diagnostics.RAIN, Diagnostics.detail() ? String.format("density %.2f, slant %.2f", density, slant) : null);
    }
}
