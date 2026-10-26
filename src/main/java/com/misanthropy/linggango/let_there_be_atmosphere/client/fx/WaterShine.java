package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class WaterShine {
    private WaterShine() {}

    static final ResourceLocation FOAM = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/water_foam.png");
    private static final ResourceLocation GLINT = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/water_glint.png");
    private static final int SLOTS = 512;
    private static final int TRIES = 6;
    private static final double REACH = 1024.0;

    private static final float[][] ROW = new float[2][SLOTS * 4];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(SLOTS * 4);
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Vector4f HORIZON = new Vector4f();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();

    private static long base = Util.getNanos();
    private static int data;
    private static boolean dirty = true;
    private static boolean anchored;
    private static double anchorX, anchorY, anchorZ;
    private static ClientLevel level;
    private static int next;
    private static float newest = -100F;
    private static float water = -100F;
    private static Vec3 sky = Vec3.ZERO;
    private static long skyTick = Long.MIN_VALUE;

    public static final float[] SHEEN = new float[4];
    public static final float[] ZENITH = new float[4];
    public static volatile boolean sheenBound;
    private static volatile boolean sheenPatched;

    private static final String SHEEN_FUNCTION = """

            in vec4 v_LinggangoSheen;
            uniform vec4 u_LinggangoSheen;
            uniform vec4 u_LinggangoSheenZenith;

            vec4 linggango_sheen(vec4 color, vec3 horizon) {
                if (u_LinggangoSheen.x <= 0.0 || v_LinggangoSheen.w < 0.999 || v_LinggangoSheen.y > -0.05 || color.a > 0.98) return color;
                vec3 view = normalize(v_LinggangoSheen.xyz);
                float graze = 1.0 - clamp(-view.y, 0.0, 1.0);
                float fresnel = graze * graze * graze * graze;
                vec3 sky = mix(horizon, u_LinggangoSheenZenith.rgb, smoothstep(0.02, 0.45, -view.y));
                float a = clamp(u_LinggangoSheen.x * (0.06 + 0.64 * fresnel), 0.0, 1.0);
                float alpha = 1.0 - (1.0 - color.a) * (1.0 - a);
                return vec4((color.rgb * color.a * (1.0 - a) + sky * a) / max(alpha, 1.0e-4), alpha);
            }
            """;

    public static String patchSheenVertex(String source) {
        if (source.contains("v_LinggangoSheen")) return source;
        if (!source.contains(RainRings.V_DECL) || !source.contains(RainRings.V_HOOK)) {
            Diagnostics.once("sky reflection could not find its place in the terrain vertex shader, it stays a screen pass");
            return source;
        }
        return source.replace(RainRings.V_DECL, RainRings.V_DECL + "\nout vec4 v_LinggangoSheen;")
                .replace(RainRings.V_HOOK, RainRings.V_HOOK
                        + "\n    v_LinggangoSheen = vec4(position, abs(fract(_vert_position.y) - 0.8884) < 0.004 ? 1.0 : 0.0);");
    }

    public static String patchSheenFragment(String source) {
        if (source.contains("linggango_sheen(")) return source;
        if (!source.contains(RainRings.F_DECL) || !source.contains(RainRings.F_HOOK) || !source.contains("u_FogColor")
                || !RainRings.vertexReady()) {
            sheenPatched = false;
            Diagnostics.once("sky reflection could not find its place in the terrain fragment shader, it stays a screen pass");
            return source;
        }
        sheenPatched = true;
        Diagnostics.once("sky reflection patched into the terrain shader");
        return source.replace(RainRings.F_DECL, RainRings.F_DECL + SHEEN_FUNCTION)
                .replace(RainRings.F_HOOK, "diffuseColor = linggango_sheen(diffuseColor, u_FogColor.rgb);\n    " + RainRings.F_HOOK);
    }

    private static boolean sheenInShader() {
        return sheenBound && sheenPatched;
    }

    public static void updateSheen() {
        SHEEN[0] = 0F;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel world = mc.level;
        if (world == null || !sheen() || !world.dimensionType().hasSkyLight() || !DynamicFogHandler.active()) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        if (CameraFluid.of(camera) != FogType.NONE) return;
        long tick = world.getGameTime();
        if (tick != skyTick) {
            skyTick = tick;
            sky = world.getSkyColor(camera.getPosition(), mc.getFrameTime());
        }
        SHEEN[0] = (float) FxConfig.skyReflectionStrength;
        ZENITH[0] = (float) sky.x;
        ZENITH[1] = (float) sky.y;
        ZENITH[2] = (float) sky.z;
    }

    private static float now() {
        return ((Util.getNanos() - base) / 1.0E9F) % 3600F;
    }

    private static boolean sparkles() {
        return FxConfig.waterSparkles && FxConfig.waterSparklesAmount > 0.0;
    }

    private static boolean sheen() {
        return FxConfig.skyReflection && FxConfig.skyReflectionStrength > 0.0;
    }

    private static float moonlight(ClientLevel world, float partialTick) {
        float moonY = -Mth.cos(world.getTimeOfDay(partialTick) * Mth.TWO_PI);
        return SkyGlow.smooth(0.05F, 0.25F, moonY) * (1F - world.getRainLevel(partialTick)) * Math.max(world.getMoonBrightness(), 0.3F);
    }

    public static void onWaterShineTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        if (current == null || mc.player == null || !(sparkles() || sheen()) || !DynamicFogHandler.active()) {
            if (anchored) reset();
            return;
        }
        if (mc.isPaused()) return;
        if (current != level) {
            level = current;
            reset();
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        if (!anchored || Math.abs(eye.x - anchorX) > REACH || Math.abs(eye.z - anchorZ) > REACH || Math.abs(eye.y - anchorY) > REACH) {
            reset();
            anchorX = Math.floor(eye.x);
            anchorY = Math.floor(eye.y);
            anchorZ = Math.floor(eye.z);
            anchored = true;
        }
        float light = current.dimensionType().hasSkyLight() ? moonlight(current, 1F) : 0F;
        int spawns = sparkles() && light >= 0.02F ? Math.round(TRIES * (float) FxConfig.waterSparklesAmount * light) : 0;
        float moonX = Mth.sin(current.getTimeOfDay(1F) * Mth.TWO_PI);
        float toward = spawns > 0 ? 0.85F * SkyGlow.smooth(0.2F, 0.8F, Math.abs(moonX)) : 0F;
        double reach = Math.min(96.0, mc.options.getEffectiveRenderDistance() * 12.0);
        float time = now();
        int tries = Math.max(spawns, 16);
        for (int i = 0; i < tries; i++) {
            double r = 2.5 * Math.pow(reach / 2.5, RANDOM.nextDouble());
            double heading = RANDOM.nextFloat() < toward
                    ? (moonX > 0F ? 0.0 : Math.PI) + (RANDOM.nextDouble() - 0.5) * 1.1
                    : RANDOM.nextDouble() * Math.PI * 2.0;
            double x = eye.x + r * Math.cos(heading);
            double z = eye.z + r * Math.sin(heading);
            int bx = Mth.floor(x), bz = Mth.floor(z);
            if (!current.hasChunk(bx >> 4, bz >> 4)) continue;
            CURSOR.set(bx, current.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1, bz);
            FluidState fluid = current.getFluidState(CURSOR);
            if (!fluid.is(FluidTags.WATER) || !current.getBlockState(CURSOR.above()).isAir()) continue;
            float surface = CURSOR.getY() + fluid.getHeight(current, CURSOR);
            if (surface > eye.y - 0.2) continue;
            water = time;
            if (i < spawns) emit(x, surface, z, time);
        }
    }

    private static void emit(double x, float surface, double z, float time) {
        int o = next * 4;
        ROW[0][o] = (float) (x - anchorX);
        ROW[0][o + 1] = (float) (surface - anchorY);
        ROW[0][o + 2] = (float) (z - anchorZ);
        ROW[0][o + 3] = time;
        ROW[1][o] = 0.55F + RANDOM.nextFloat() * 0.55F;
        ROW[1][o + 1] = RANDOM.nextFloat();
        next = (next + 1) % SLOTS;
        newest = time;
        dirty = true;
    }

    private static void reset() {
        for (float[] row : ROW) java.util.Arrays.fill(row, 0F);
        for (int i = 0; i < SLOTS; i++) ROW[0][i * 4 + 3] = -1000F;
        anchored = false;
        newest = -100F;
        water = -100F;
        dirty = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderWaterShineStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        boolean wantSheen = sheen();
        boolean wantSparkles = sparkles();
        if (!wantSheen) Diagnostics.skip(Diagnostics.SHEEN, "disabled");
        if (!wantSparkles) Diagnostics.skip(Diagnostics.SPARKLES, "disabled");
        if (!wantSheen && !wantSparkles) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel world = mc.level;
        Camera camera = event.getCamera();
        String why = world == null || !world.dimensionType().hasSkyLight() ? "no sky here"
                : !DynamicFogHandler.active() || ColorGrade.shaderPackActive() ? "fog handler off or a shader pack owns the image"
                : Minecraft.useShaderTransparency() ? "Fabulous graphics keeps water in its own layer"
                : CameraFluid.of(camera) != FogType.NONE ? "the camera is in a fluid" : null;
        if (why != null) {
            if (wantSheen) Diagnostics.skip(Diagnostics.SHEEN, why);
            if (wantSparkles) Diagnostics.skip(Diagnostics.SPARKLES, why);
            return;
        }
        Vec3 eye = camera.getPosition();
        float partialTick = event.getPartialTick();
        float time = now();
        if (wantSheen && sheenInShader()) Diagnostics.mark(Diagnostics.SHEEN, "drawn in the water shader");
        else if (wantSheen && (time - water > 15F || time < water)) Diagnostics.skip(Diagnostics.SHEEN, "no water below you nearby");
        else if (wantSheen) drawSheen(event, world, eye, partialTick);
        if (wantSparkles) drawSparkles(event, world, eye, partialTick);
    }

    private static void drawSheen(RenderLevelStageEvent event, ClientLevel world, Vec3 eye, float partialTick) {
        SkyProgram program = SkyProgram.WATER_SHEEN;
        if (!program.ready()) {
            Diagnostics.skip(Diagnostics.SHEEN, "shader failed to load, see the log");
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        VIEW.set(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
        Vector3f look = event.getCamera().getLookVector();
        VIEW.transform(HORIZON.set(look.x(), 0F, look.z(), 0F));
        int rows = HORIZON.w < 1.0E-4F ? (look.y() < 0F ? main.height : 0)
                : Mth.clamp(Math.round((HORIZON.y / HORIZON.w * 0.5F + 0.52F) * main.height) + 2, 0, main.height);
        if (rows == 0) {
            Diagnostics.skip(Diagnostics.SHEEN, "looking above the horizon");
            return;
        }
        long tick = world.getGameTime();
        if (tick != skyTick) {
            skyTick = tick;
            sky = world.getSkyColor(eye, partialTick);
        }
        float[] fog = RenderSystem.getShaderFogColor();
        float strength = (float) FxConfig.skyReflectionStrength;
        Caustics.bindColour(main);
        RenderSystem.enableScissor(0, 0, main.width, rows);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        program.begin();
        SkyProgram.texture(0, main.getDepthTextureId());
        VIEW.invert().get(MATRIX);
        GL20.glUniformMatrix4fv(program.uniform(0), false, MATRIX);
        GL20.glUniform4f(program.uniform(1), (float) (eye.y - Math.floor(eye.y)), strength,
                RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd());
        GL20.glUniform4f(program.uniform(2), fog[0], fog[1], fog[2], 0F);
        GL20.glUniform4f(program.uniform(3), (float) sky.x, (float) sky.y, (float) sky.z, 0F);
        program.draw(3);
        program.end();
        RenderSystem.disableScissor();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        main.bindWrite(true);
        Diagnostics.mark(Diagnostics.SHEEN, Diagnostics.detail() ? String.format("strength %.2f", strength) : null);
    }

    private static void drawSparkles(RenderLevelStageEvent event, ClientLevel world, Vec3 eye, float partialTick) {
        float time = now();
        if (!anchored || time - newest > 1.2F || time < newest) {
            Diagnostics.skip(Diagnostics.SPARKLES, "no moonlit water nearby");
            return;
        }
        SkyProgram program = SkyProgram.WATER_GLINT;
        if (!program.ready()) {
            Diagnostics.skip(Diagnostics.SPARKLES, "shader failed to load, see the log");
            return;
        }
        int sprites = SkyProgram.texture(GLINT);
        upload();
        float ox = (float) (anchorX - eye.x);
        float oy = (float) (anchorY - eye.y);
        float oz = (float) (anchorZ - eye.z);
        VIEW.set(event.getPoseStack().last().pose()).translate(ox, oy, oz);
        float angle = world.getTimeOfDay(partialTick) * Mth.TWO_PI;
        float light = moonlight(world, partialTick);
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        program.begin();
        SkyProgram.texture(1, data);
        SkyProgram.texture(0, sprites);
        program.transform(0, VIEW);
        GL20.glUniform3f(program.uniform(1), ox, oy, oz);
        GL20.glUniform4f(program.uniform(2), time, RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), light);
        GL20.glUniform4f(program.uniform(3), Mth.sin(angle), -Mth.cos(angle), 0F, 0F);
        GL20.glUniform4f(program.uniform(4), main.width, main.height, Math.max(1, Math.round(main.height / 720F)), 0F);
        program.draw(SLOTS * 6);
        program.end();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        Diagnostics.mark(Diagnostics.SPARKLES, Diagnostics.detail() ? String.format("moon %.2f", light) : null);
    }

    private static void upload() {
        if (data == 0) {
            data = GlStateManager._genTexture();
            GlStateManager._bindTexture(data);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            SkyHeights.unpack();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, SLOTS, 2, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            dirty = true;
        }
        if (!dirty) return;
        GlStateManager._bindTexture(data);
        SkyHeights.unpack();
        for (int row = 0; row < 2; row++) {
            UPLOAD.clear();
            UPLOAD.put(ROW[row]).flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, row, SLOTS, 1, GL11.GL_RGBA, GL11.GL_FLOAT, UPLOAD);
        }
        dirty = false;
    }
}
