package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class CaveMotes {
    private CaveMotes() {}

    static final int DUST = 0;
    static final int GLINT = 1;
    static final int SPORE = 2;
    static final int SPLASH = 3;
    static final int EMBER = 4;
    static final int ASH = 5;
    static final int POP = 6;
    static final int DROPLET = 7;
    static final int STEAM = 8;
    static final int FIREFLY = 9;
    static final int SILT = 10;
    static final int POLLEN = 11;

    private static final int SLOTS = 1024;
    private static final float REBASE = 1800F;
    private static final double REACH = 2048.0;
    private static final float[] ROW_A = new float[SLOTS * 4];
    private static final float[] ROW_B = new float[SLOTS * 4];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(SLOTS * 4);
    private static final int SPRITE_COUNT = 17;
    private static final float[] SPRITES = new float[SPRITE_COUNT * 4];
    private static final float[] PUSH_X = new float[SLOTS];
    private static final float[] PUSH_Z = new float[SLOTS];
    private static long driftClock = Util.getNanos();
    private static AbstractTexture atlas;
    private static final Matrix4f MATRIX = new Matrix4f();

    private static long base = Util.getNanos();
    private static int head;
    private static int dirtyFrom = -1;
    private static int dirtyCount;
    private static boolean drifted;
    private static boolean anchored;
    private static double anchorX, anchorY, anchorZ;
    private static float lastBirth = -100F;
    private static int data;

    static float now() {
        return (Util.getNanos() - base) / 1.0E9F;
    }

    static void emit(int kind, double x, double y, double z, int block, int sky, float life, float seed) {
        if (!anchored || Math.abs(x - anchorX) > REACH || Math.abs(z - anchorZ) > REACH || Math.abs(y - anchorY) > REACH) {
            clear();
            anchorX = Math.floor(x);
            anchorY = Math.floor(y);
            anchorZ = Math.floor(z);
            anchored = true;
        }
        float now = now();
        if (now > REBASE) rebase(now);
        int slot = head;
        head = (head + 1) % SLOTS;
        int o = slot * 4;
        ROW_A[o] = (float) (x - anchorX);
        ROW_A[o + 1] = (float) (y - anchorY);
        ROW_A[o + 2] = (float) (z - anchorZ);
        ROW_A[o + 3] = now();
        ROW_B[o] = kind;
        ROW_B[o + 1] = seed;
        ROW_B[o + 2] = block * 16 + sky;
        ROW_B[o + 3] = life;
        PUSH_X[slot] = 0F;
        PUSH_Z[slot] = 0F;
        if (dirtyFrom < 0) {
            dirtyFrom = slot;
            dirtyCount = 1;
        } else {
            dirtyCount = Math.min(dirtyCount + 1, SLOTS);
        }
        lastBirth = ROW_A[o + 3];
    }

    private static void clear() {
        java.util.Arrays.fill(ROW_B, 0F);
        dirtyFrom = 0;
        dirtyCount = SLOTS;
    }

    private static void rebase(float now) {
        for (int i = 0; i < SLOTS; i++) ROW_A[i * 4 + 3] -= now;
        lastBirth -= now;
        base = Util.getNanos();
        dirtyFrom = 0;
        dirtyCount = SLOTS;
    }

    static boolean resolve() {
        AbstractTexture current = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        if (current == atlas) return true;
        if (!(current instanceof TextureAtlas particles)) return false;
        for (int i = 0; i < SPRITE_COUNT; i++) {
            String name = i < 3 ? "generic_" + i : i < 11 ? "glitter_" + (i - 3) : i < 15 ? "splash_" + (i - 11) : i == 15 ? "generic_5" : "generic_7";
            TextureAtlasSprite sprite = particles.getSprite(new ResourceLocation(name));
            if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                Diagnostics.once("motes could not find " + name + " in the particle atlas");
                return false;
            }
            SPRITES[i * 4] = sprite.getU0();
            SPRITES[i * 4 + 1] = sprite.getV0();
            SPRITES[i * 4 + 2] = sprite.getU1();
            SPRITES[i * 4 + 3] = sprite.getV1();
        }
        atlas = current;
        Diagnostics.once("motes found their particle sprites");
        return true;
    }

    public static void onRenderCaveMoteStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!anchored || now() - lastBirth > 10F || LightTint.lightmap == null) return;
        SkyProgram program = SkyProgram.CAVE_MOTES;
        if (!program.ready() || !resolve()) return;

        drift(event.getPartialTick(), event.getCamera().getPosition().y);
        upload();
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        float ox = (float) (anchorX - eye.x);
        float oy = (float) (anchorY - eye.y);
        float oz = (float) (anchorZ - eye.z);
        MATRIX.set(event.getPoseStack().last().pose()).translate(ox, oy, oz);
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        program.begin();
        SkyProgram.texture(2, data);
        SkyProgram.texture(1, LightTint.lightmap.getId());
        SkyProgram.texture(0, atlas.getId());
        program.transform(0, MATRIX);
        GL20.glUniform3f(program.uniform(1), -left.x(), -left.y(), -left.z());
        GL20.glUniform3f(program.uniform(2), up.x(), up.y(), up.z());
        GL20.glUniform3f(program.uniform(3), ox, oy, oz);
        GL20.glUniform4f(program.uniform(4), now(), RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), 0F);
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.uniform(5), fog[0], fog[1], fog[2], 1F);
        GL20.glUniform4fv(program.uniform(6), SPRITES);
        program.draw(SLOTS * 6);
        program.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void drift(float partialTick, double eyeY) {
        long nanos = Util.getNanos();
        float dt = Mth.clamp((nanos - driftClock) / 1.0E9F, 0F, 0.1F);
        driftClock = nanos;
        if (Minecraft.getInstance().level == null || dt <= 0F || FxConfig.windStrength <= 0.0) return;
        WindParticles.sampleDrift(Minecraft.getInstance().level, partialTick, eyeY);
        float ease = 1F - (float) Math.exp(-dt * 1.2F);
        float now = now();
        boolean moved = false;
        for (int i = 0; i < SLOTS; i++) {
            int o = i * 4;
            int kind = (int) ROW_B[o];
            if (kind != POLLEN && kind != FIREFLY) continue;
            float age = now - ROW_A[o + 3];
            if (ROW_B[o + 3] <= 0F || age < 0F || age > ROW_B[o + 3]) continue;
            float open = WindParticles.sky((int) ROW_B[o + 2]) * (kind == FIREFLY ? 0.25F : 1F);
            PUSH_X[i] += (WindParticles.driftX * open - PUSH_X[i]) * ease;
            PUSH_Z[i] += (WindParticles.driftZ * open - PUSH_Z[i]) * ease;
            ROW_A[o] += PUSH_X[i] * dt;
            ROW_A[o + 2] += PUSH_Z[i] * dt;
            moved = true;
        }
        if (moved) drifted = true;
    }

    private static void upload() {
        if (data == 0) {
            data = GlStateManager._genTexture();
            GlStateManager._bindTexture(data);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            SkyHeights.unpack();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, SLOTS, 2, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            dirtyFrom = 0;
            dirtyCount = SLOTS;
        }
        if (dirtyFrom < 0 && !drifted) return;
        GlStateManager._bindTexture(data);
        SkyHeights.unpack();
        if (drifted) {
            drifted = false;
            UPLOAD.clear();
            UPLOAD.put(ROW_A, 0, SLOTS * 4).flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, SLOTS, 1, GL11.GL_RGBA, GL11.GL_FLOAT, UPLOAD);
            if (dirtyFrom < 0) return;
        }
        int first = dirtyFrom;
        int count = dirtyCount;
        int run = Math.min(count, SLOTS - first);
        rows(first, run);
        if (count > run) rows(0, count - run);
        dirtyFrom = -1;
        dirtyCount = 0;
    }

    private static void rows(int first, int count) {
        UPLOAD.clear();
        UPLOAD.put(ROW_A, first * 4, count * 4).flip();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, first, 0, count, 1, GL11.GL_RGBA, GL11.GL_FLOAT, UPLOAD);
        UPLOAD.clear();
        UPLOAD.put(ROW_B, first * 4, count * 4).flip();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, first, 1, count, 1, GL11.GL_RGBA, GL11.GL_FLOAT, UPLOAD);
    }
}
