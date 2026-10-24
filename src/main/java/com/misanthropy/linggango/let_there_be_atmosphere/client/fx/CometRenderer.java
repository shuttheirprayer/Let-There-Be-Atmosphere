package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;

@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class CometRenderer {
    private CometRenderer() {}

    private static final ResourceLocation TAIL_TEXTURE =
            new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/environment/comet_tail.png");

    private static final float RADIUS = 100F;
    private static final float SPAWN_NIGHT = 0.12F;

    private static final int[] HEAD_HEX = {0, 0xFFF3DA, 0xEAF6FF, 0xFFFFFF};
    private static final int[] TRAIL_HEX = {0, 0xFF8A38, 0x3FA6FF, 0xB8CBFF};

    private static final int U_TRANSFORM = 0;
    private static final int U_VISIBLE = 1;
    private static final int U_PATH = 2;
    private static final int U_AXIS = 3;
    private static final int U_LOOK = 4;
    private static final int U_HEAD = 5;
    private static final int U_TRAIL = 6;
    private static final float[] SCRATCH = new float[3];
    private static final float[] PATH = new float[Comets.CAPACITY * 4];
    private static final float[] AXIS = new float[Comets.CAPACITY * 4];
    private static final float[] LOOK = new float[Comets.CAPACITY * 4];
    private static final float[] HEADS = new float[Comets.CAPACITY * 3];
    private static final float[] TRAILS = new float[Comets.CAPACITY * 3];

    private static final ResourceLocation HEAD_TEXTURE =
            new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/environment/star.png");

    private static float nightAmount(float dayTimeTicks) {
        return Mth.clamp(DayCurves.starBrightness(dayTimeTicks) * 2F, 0F, 1F);
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !DynamicFogHandler.active() || !FxConfig.cometsEnabled) {
            if (Comets.liveCount() > 0) Comets.reset();
            return;
        }

        float night = nightAmount(level.getDayTime() % DayCurves.PERIOD);
        boolean canSpawn = level.dimensionType().hasSkyLight()
                && level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL
                && night > SPAWN_NIGHT
                && level.getRainLevel(1F) < 0.4F
                && DynamicFogHandler.skyVisibility() > 0.4F;

        Comets.tick(canSpawn, FxConfig.cometRate, FxConfig.cometMax, FxConfig.cometSpeed,
                FxConfig.cometTail, FxConfig.cometAngle, FxConfig.cometPalette,
                FxConfig.cometShowerChance, FxConfig.cometShowerSize);
    }

    public static void onRenderSkyStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (!DynamicFogHandler.active() || !FxConfig.cometsEnabled) {
            Diagnostics.skip(Diagnostics.COMETS, "disabled");
            return;
        }
        if (Comets.liveCount() == 0) {
            Diagnostics.skip(Diagnostics.COMETS, "none in flight");
            return;
        }

        ClientLevel level = SkyGate.sky(event, Diagnostics.COMETS, true);
        if (level == null) return;

        float partialTick = event.getPartialTick();
        float visible = nightAmount(level.getDayTime() % DayCurves.PERIOD + partialTick)
                * (1F - level.getRainLevel(partialTick))
                * DynamicFogHandler.skyVisibility()
                * (float) FxConfig.cometBrightness;
        if (visible <= 0.01F) {
            Diagnostics.skip(Diagnostics.COMETS, Diagnostics.detail()
                    ? String.format("%d in flight but daylight", Comets.liveCount()) : null);
            return;
        }

        Matrix4f matrix = event.getPoseStack().last().pose();
        draw(matrix, partialTick, visible);
        Diagnostics.mark(Diagnostics.COMETS, Diagnostics.detail()
                ? String.format("%d in flight, visible %.2f", Comets.liveCount(), visible) : null);
    }

    private static void draw(Matrix4f matrix, float partialTick, float visible) {
        SkyProgram program = SkyProgram.COMETS;
        if (!program.ready()) return;
        int live = 0;
        for (int i = 0; i < Comets.CAPACITY; i++) {
            int o = i * 4;
            int c = i * 3;
            float fade = Comets.alive(i) ? Comets.fade(i, partialTick) : 0F;
            if (fade <= 0.01F) {
                LOOK[o + 3] = 0F;
                continue;
            }
            live++;
            Comets.direction(i, SCRATCH);
            PATH[o] = SCRATCH[0];
            PATH[o + 1] = SCRATCH[1];
            PATH[o + 2] = SCRATCH[2];
            PATH[o + 3] = Comets.sweep(i, partialTick);
            Comets.ribbon(i, SCRATCH);
            AXIS[o] = SCRATCH[0];
            AXIS[o + 1] = SCRATCH[1];
            AXIS[o + 2] = SCRATCH[2];
            AXIS[o + 3] = Comets.tail(i);
            float width = Comets.width(i) * RADIUS;
            LOOK[o] = fade;
            LOOK[o + 1] = width;
            LOOK[o + 2] = width * Comets.headScale(i) * Mth.clamp((float) FxConfig.cometHeadSize, 0.2F, 4F);
            LOOK[o + 3] = 1F;
            int palette = Comets.palette(i);
            unpack(headHex(palette), HEADS, c);
            unpack(trailHex(palette), TRAILS, c);
        }

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        FogRenderer.setupNoFog();
        RenderSystem.setShaderTexture(0, HEAD_TEXTURE);

        if (live > 0) {
            program.begin();
            SkyProgram.texture(1, SkyProgram.texture(HEAD_TEXTURE));
            SkyProgram.texture(0, SkyProgram.texture(TAIL_TEXTURE));
            program.transform(U_TRANSFORM, matrix);
            GL20.glUniform1f(program.uniform(U_VISIBLE), visible);
            GL20.glUniform4fv(program.uniform(U_PATH), PATH);
            GL20.glUniform4fv(program.uniform(U_AXIS), AXIS);
            GL20.glUniform4fv(program.uniform(U_LOOK), LOOK);
            GL20.glUniform3fv(program.uniform(U_HEAD), HEADS);
            GL20.glUniform3fv(program.uniform(U_TRAIL), TRAILS);
            program.draw(Comets.CAPACITY * (Comets.TAIL_SEGMENTS + 1) * 6);
            program.end();
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    private static void unpack(int hex, float[] out, int at) {
        out[at] = ((hex >> 16) & 0xFF) / 255F;
        out[at + 1] = ((hex >> 8) & 0xFF) / 255F;
        out[at + 2] = (hex & 0xFF) / 255F;
    }

    private static int headHex(int palette) {
        return palette <= Comets.PALETTE_CUSTOM ? FxConfig.cometHex : HEAD_HEX[palette];
    }

    private static int trailHex(int palette) {
        return palette <= Comets.PALETTE_CUSTOM ? FxConfig.cometHex : TRAIL_HEX[palette];
    }
}
