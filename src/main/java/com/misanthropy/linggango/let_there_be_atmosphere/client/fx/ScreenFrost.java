package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.CaptureHook;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class ScreenFrost {
    private ScreenFrost() {}

    private static final ResourceLocation SHADER = new ResourceLocation(Let_there_be_atmosphere.MODID, "screen_frost");
    private static final float STORM_COVER = 0.4F;
    private static final int MAX_TEXELS = 1_500_000;

    private static ShaderInstance shader;
    private static int screenUniform;
    private static int frostUniform;
    private static boolean loadFailed;
    private static int vao;
    private static int map;
    private static int mapW;
    private static int mapH;
    private static int mapCell;
    private static float freeze;
    private static float storm;
    private static float[] arrival;
    private static byte[] kind;

    public static float cover() {
        return Math.max(freeze, storm);
    }

    private static boolean usable() {
        return FxConfig.screenFrost && DynamicFogHandler.active();
    }

    public static void onScreenFrostTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !usable()) {
            freeze = 0F;
            storm = 0F;
            return;
        }
        freeze += (player.getPercentFrozen() - freeze) * 0.35F;
        float target = FxConfig.blizzard ? WeatherStorms.blizzard() * STORM_COVER : 0F;
        storm += (target - storm) * 0.012F;
        if (freeze < 0.002F) freeze = 0F;
        if (storm < 0.002F && target <= 0F) storm = 0F;
    }

    @SubscribeEvent
    public static void onFrostbiteOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.FROSTBITE.type()) return;
        if (!usable()) {
            Diagnostics.skip(Diagnostics.FROST, FxConfig.screenFrost ? "master switch off" : "disabled");
            return;
        }
        if (shader == null && !loadFailed) load();
        if (shader == null) {
            Diagnostics.skip(Diagnostics.FROST, "shader failed to load, vanilla overlay kept");
            return;
        }
        event.setCanceled(true);
        float c = cover();
        if (c < 0.004F) {
            Diagnostics.skip(Diagnostics.FROST, "not cold");
            return;
        }
        draw(event.getGuiGraphics(), c);
        Diagnostics.mark(Diagnostics.FROST, Diagnostics.detail() ? String.format("cover %.2f (freeze %.2f, storm %.2f)", c, freeze, storm) : null);
    }

    private static void draw(GuiGraphics graphics, float c) {
        Window window = Minecraft.getInstance().getWindow();
        fit(window);
        graphics.flush();
        CaptureHook.probeBegin("screen_frost");
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (vao == 0) vao = GlStateManager._glGenVertexArrays();
        GlStateManager._glBindVertexArray(vao);
        ProgramManager.glUseProgram(shader.getId());
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(map);
        GL20.glUniform4f(screenUniform, (float) window.getGuiScale() * mapCell, window.getHeight(), mapW, mapH);
        GL20.glUniform4f(frostUniform, c, 0F, 0F, 0F);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GlStateManager._bindTexture(0);
        shader.clear();
        BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(0);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        CaptureHook.probeEnd("screen_frost");
    }

    private static void fit(Window window) {
        int cell = 1;
        long texels = (long) window.getGuiScaledWidth() * window.getGuiScaledHeight();
        while (texels / ((long) cell * cell) > MAX_TEXELS) cell++;
        int w = (window.getGuiScaledWidth() + cell - 1) / cell;
        int h = (window.getGuiScaledHeight() + cell - 1) / cell;
        if (map != 0 && w == mapW && h == mapH && cell == mapCell) return;
        mapW = w;
        mapH = h;
        mapCell = cell;
        build(w, h);
        ByteBuffer data = BufferUtils.createByteBuffer(w * h * 2);
        for (int i = 0; i < w * h; i++) {
            data.put((byte) (arrival[i] > 1F ? 0 : Mth.clamp(Math.round(arrival[i] * 255F), 1, 255)));
            data.put(kind[i]);
        }
        data.flip();
        arrival = null;
        kind = null;
        if (map == 0) map = GlStateManager._genTexture();
        GlStateManager._bindTexture(map);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG8, w, h, 0, GL30.GL_RG, GL11.GL_UNSIGNED_BYTE, data);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        GlStateManager._bindTexture(0);
    }

    private static void build(int w, int h) {
        arrival = new float[w * h];
        kind = new byte[w * h];
        java.util.Arrays.fill(arrival, 2F);
        RandomSource random = RandomSource.create(20241027L);
        float aspect = (float) w / h;
        float unit = h / 240F;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float px = (x + 0.5F) / h;
                float py = (y + 0.5F) / h;
                float edge = Math.min(Math.min(px, aspect - px), Math.min(py, 1F - py));
                float cx = Math.min(px, aspect - px);
                float cy = Math.min(py, 1F - py);
                float corner = Mth.sqrt(cx * cx + cy * cy);
                float wobble = noise(px * 14F, py * 14F, 3) * 0.7F + noise(px * 31F, py * 31F, 9) * 0.3F - 0.5F;
                float field = Math.min(edge / 0.13F, corner / 0.5F) + wobble * 0.5F;
                float bayer = BAYER[(y & 3) * 4 + (x & 3)] / 16F - 0.5F;
                float a = 0.2F + 0.8F * Math.max(field, 0F) + bayer * 0.12F;
                if (a <= 1F) arrival[y * w + x] = Math.max(a, 0.03F);
            }
        }
        float thickness = 2F;
        for (int corner = 0; corner < 4; corner++) {
            float x = (corner & 1) == 0 ? 1F : w - 2F;
            float y = (corner & 2) == 0 ? 1F : h - 2F;
            float inward = (float) Math.atan2(h * 0.5F - y, w * 0.5F - x);
            for (int k = 0; k < 5; k++) {
                float angle = inward + (k - 2) * 0.36F + (random.nextFloat() - 0.5F) * 0.14F;
                float length = h * (0.3F + 0.14F * random.nextFloat()) * (k == 2 ? 1.15F : 1F - 0.1F * Math.abs(k - 2));
                grow(w, h, random, unit, x, y, angle, length, 0, random.nextFloat() * 0.12F, 0.55F / (length * 1.45F), thickness);
            }
        }
        float spacing = h * 0.12F;
        for (int edge = 0; edge < 4; edge++) {
            boolean horizontal = edge < 2;
            float run = horizontal ? w : h;
            int seeds = Math.max(1, Math.round(run / spacing));
            for (int k = 0; k < seeds; k++) {
                float along = run * (k + 0.2F + 0.6F * random.nextFloat()) / seeds;
                float x = horizontal ? along : edge == 2 ? 1F : w - 2F;
                float y = horizontal ? (edge == 0 ? 1F : h - 2F) : along;
                float inward = horizontal ? (edge == 0 ? Mth.HALF_PI : -Mth.HALF_PI) : (edge == 2 ? 0F : Mth.PI);
                float length = h * (0.1F + 0.12F * random.nextFloat());
                grow(w, h, random, unit, x, y, inward + (random.nextFloat() - 0.5F) * 0.9F, length, 0, 0.22F + 0.3F * random.nextFloat(), 0.45F / (length * 1.45F), thickness);
            }
        }
        for (int i = 0; i < 70; i++) {
            int x = 3 + random.nextInt(Math.max(1, w - 6));
            int y = 3 + random.nextInt(Math.max(1, h - 6));
            float a = arrival[y * w + x];
            if (a > 0.85F) continue;
            star(w, h, x, y, random.nextInt(3) == 0 ? 3 : 2, a + 0.04F);
        }
        outline(w, h);
    }

    private static void grow(int w, int h, RandomSource random, float unit, float x, float y, float angle, float length, int depth, float start, float rate, float thickness) {
        float dx = Mth.cos(angle);
        float dy = Mth.sin(angle);
        float spacing = (depth == 0 ? 6F : depth == 1 ? 4.5F : 3.4F) * Math.max(unit, 1F);
        float next = spacing * 0.6F;
        boolean left = random.nextBoolean();
        for (float s = 0F; s < length; s += 1F) {
            plot(w, h, Math.round(x), Math.round(y), start + s * rate, depth == 0 ? thickness : 1F);
            x += dx;
            y += dy;
            if (x < -2F || y < -2F || x > w + 2F || y > h + 2F) return;
            angle += (random.nextFloat() - 0.5F) * 0.07F;
            dx = Mth.cos(angle);
            dy = Mth.sin(angle);
            if (depth < 3 && s >= next) {
                next += spacing * (0.8F + 0.4F * random.nextFloat());
                float reach = (length - s) * (depth == 0 ? 0.6F : 0.55F);
                if (reach < 3F) continue;
                left = !left;
                float turn = (left ? 1F : -1F) * (0.85F + 0.35F * random.nextFloat());
                grow(w, h, random, unit, x, y, angle + turn, reach, depth + 1, start + s * rate, rate, 1F);
            }
        }
    }

    private static void star(int w, int h, int x, int y, int reach, float a) {
        plot(w, h, x, y, a, 1F);
        for (int i = 1; i <= reach; i++) {
            plot(w, h, x + i, y, a, 1F);
            plot(w, h, x - i, y, a, 1F);
            plot(w, h, x, y + i, a, 1F);
            plot(w, h, x, y - i, a, 1F);
        }
        for (int i = 1; i < reach; i++) {
            plot(w, h, x + i, y + i, a, 1F);
            plot(w, h, x - i, y + i, a, 1F);
            plot(w, h, x + i, y - i, a, 1F);
            plot(w, h, x - i, y - i, a, 1F);
        }
    }

    private static void plot(int w, int h, int x, int y, float a, float thickness) {
        put(w, h, x, y, a);
        if (thickness > 1.5F) {
            put(w, h, x + 1, y, a);
            put(w, h, x, y + 1, a);
        }
    }

    private static void put(int w, int h, int x, int y, float a) {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        int i = y * w + x;
        if (kind[i] == 1 && arrival[i] <= a) return;
        arrival[i] = a;
        kind[i] = 1;
    }

    private static void outline(int w, int h) {
        float[] ring = new float[w * h];
        java.util.Arrays.fill(ring, 2F);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (kind[i] == 1) continue;
                float a = 2F;
                if (x > 0 && kind[i - 1] == 1) a = Math.min(a, arrival[i - 1]);
                if (x < w - 1 && kind[i + 1] == 1) a = Math.min(a, arrival[i + 1]);
                if (y > 0 && kind[i - w] == 1) a = Math.min(a, arrival[i - w]);
                if (y < h - 1 && kind[i + w] == 1) a = Math.min(a, arrival[i + w]);
                ring[i] = a;
            }
        }
        for (int i = 0; i < w * h; i++) {
            if (ring[i] > 1F) continue;
            arrival[i] = ring[i] + 0.004F;
            kind[i] = 2;
        }
    }

    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    private static float noise(float x, float y, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        float fx = x - xi;
        float fy = y - yi;
        fx = fx * fx * (3F - 2F * fx);
        fy = fy * fy * (3F - 2F * fy);
        return Mth.lerp(fy, Mth.lerp(fx, hash(xi, yi, seed), hash(xi + 1, yi, seed)), Mth.lerp(fx, hash(xi, yi + 1, seed), hash(xi + 1, yi + 1, seed)));
    }

    private static float hash(int x, int y, int seed) {
        int v = x * 374761393 + y * 668265263 + seed * 1442695041;
        v = (v ^ (v >>> 13)) * 1274126177;
        v ^= v >>> 16;
        return (v & 0xFFFFFF) / 16777216F;
    }

    private static void load() {
        try {
            ShaderInstance loaded = new ShaderInstance(Minecraft.getInstance().getResourceManager(), SHADER, DefaultVertexFormat.POSITION);
            int id = loaded.getId();
            screenUniform = GlStateManager._glGetUniformLocation(id, "Screen");
            frostUniform = GlStateManager._glGetUniformLocation(id, "Frost");
            ProgramManager.glUseProgram(id);
            GlStateManager._glUniform1i(GlStateManager._glGetUniformLocation(id, "FrostMap"), 0);
            loaded.clear();
            shader = loaded;
        } catch (IOException | RuntimeException e) {
            loadFailed = true;
            Diagnostics.LOG.error("[atmosphere] screen frost shader failed to load", e);
        }
    }
}
