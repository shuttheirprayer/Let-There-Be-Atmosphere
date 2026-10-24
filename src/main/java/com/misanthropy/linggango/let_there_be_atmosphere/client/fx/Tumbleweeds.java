package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
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
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class Tumbleweeds {
    private Tumbleweeds() {}

    private static final ResourceLocation TEXTURE = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/tumbleweed.png");

    private static final int SLOTS = 16;
    private static final int ROWS = 4;
    private static final double REACH = 1024.0;
    private static final float HALF = 0.45F;
    private static final float GRAVITY = 0.08F;
    private static final float FADE_IN = 30F;

    private static final float[][] ROW = new float[ROWS][SLOTS * 4];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(SLOTS * 4);
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final Matrix4f MATRIX = new Matrix4f();

    private static final boolean[] LIVE = new boolean[SLOTS];
    private static final boolean[] DYING = new boolean[SLOTS];
    private static final boolean[] AIR = new boolean[SLOTS];
    private static final double[] X = new double[SLOTS], Y = new double[SLOTS], Z = new double[SLOTS];
    private static final float[] VX = new float[SLOTS], VY = new float[SLOTS], VZ = new float[SLOTS];
    private static final float[] AXIS_X = new float[SLOTS], AXIS_Z = new float[SLOTS];
    private static final float[] ANGLE = new float[SLOTS], PACE = new float[SLOTS], SEED = new float[SLOTS];
    private static final float[] ALPHA = new float[SLOTS];
    private static final int[] AGE = new int[SLOTS], STUCK = new int[SLOTS], COOL = new int[SLOTS], LIGHT = new int[SLOTS];

    private static long base = Util.getNanos();
    private static int data;
    private static boolean dirty;
    private static boolean anchored;
    private static double anchorX, anchorY, anchorZ;
    private static ClientLevel level;
    private static int alive;
    private static float spawnDebt;

    private static float now() {
        return ((Util.getNanos() - base) / 1.0E9F) % 3600F;
    }

    public static void onTumbleweedsTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        LocalPlayer player = mc.player;
        if (current == null || player == null || !FxConfig.tumbleweeds || !DynamicFogHandler.active()
                || !current.dimensionType().hasSkyLight()) {
            if (anchored) reset();
            Diagnostics.skip(Diagnostics.TUMBLEWEEDS, "disabled, or no sky here");
            return;
        }
        if (mc.isPaused()) return;
        if (current != level) {
            level = current;
            reset();
        }
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        if (!anchored || Math.abs(px - anchorX) > REACH || Math.abs(pz - anchorZ) > REACH || Math.abs(py - anchorY) > REACH) {
            reset();
            anchorX = Math.floor(px);
            anchorY = Math.floor(py);
            anchorZ = Math.floor(pz);
            anchored = true;
        }
        float now = now();
        float rain = level.getRainLevel(1F);
        CURSOR.set(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        boolean dry = dryAt(CURSOR);
        boolean storm = dry && rain > 0.15F;
        int target = !dry ? 0 : storm ? 8 + (int) (6F * Math.min(1F, rain)) : calmTarget(player);
        float seconds = Wind.seconds(level, 0F);
        float gust = Wind.gust(seconds);
        float wx = Wind.x(seconds), wz = Wind.z(seconds);
        if (alive < target) {
            spawnDebt += storm ? 0.35F : 0.03F;
            if (spawnDebt >= 1F) {
                spawnDebt = 0F;
                spawn(player, wx, wz, storm, gust);
            }
        }
        for (int i = 0; i < SLOTS; i++) {
            if (!LIVE[i]) continue;
            update(i, player, wx, wz, gust, storm, dry, target, now);
        }
        dirty = true;
        if (alive == 0 && !dry) {
            Diagnostics.skip(Diagnostics.TUMBLEWEEDS, "not a dry, warm biome here");
        } else {
            Diagnostics.mark(Diagnostics.TUMBLEWEEDS, Diagnostics.detail() ? alive + " rolling, target " + target + (storm ? ", sandstorm" : "") : null);
        }
    }

    private static boolean dryAt(BlockPos pos) {
        Biome biome = level.getBiome(pos).value();
        return biome.getPrecipitationAt(pos) == Biome.Precipitation.NONE && biome.getBaseTemperature() >= 0.5F;
    }

    private static int calmTarget(LocalPlayer player) {
        int rx = player.getBlockX() >> 6, rz = player.getBlockZ() >> 6;
        int epoch = (int) (level.getDayTime() / 6000L);
        int h = rx * 0x27D4EB2D ^ rz * 0x165667B1 ^ epoch * 0x9E3779B1;
        h = (h ^ (h >>> 15)) * 0x85EBCA6B;
        h = (h ^ (h >>> 13)) * 0xC2B2AE35;
        float v = ((h ^ (h >>> 16)) & 0xFFFF) / 65536F;
        return v < 0.3F ? 0 : v < 0.62F ? 1 : v < 0.88F ? 2 : 3;
    }

    private static void reset() {
        for (float[] row : ROW) java.util.Arrays.fill(row, 0F);
        java.util.Arrays.fill(LIVE, false);
        alive = 0;
        anchored = false;
        spawnDebt = 0F;
        dirty = true;
    }

    private static void spawn(LocalPlayer player, float wx, float wz, boolean storm, float gust) {
        int slot = -1;
        for (int i = 0; i < SLOTS && slot < 0; i++) if (!LIVE[i]) slot = i;
        if (slot < 0) return;
        RandomSource random = level.random;
        float upwind = (float) Math.atan2(-wz, -wx);
        float angle = upwind + (random.nextFloat() - 0.5F) * (storm ? 1.6F : 3.6F);
        float distance = 40F + random.nextFloat() * 16F;
        int x = Mth.floor(player.getX() + Mth.cos(angle) * distance);
        int z = Mth.floor(player.getZ() + Mth.sin(angle) * distance);
        if (!Litter.ready(x >> 4, z >> 4)) return;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int ground = Integer.MIN_VALUE;
        for (int y = top - 1; y > top - 24; y--) {
            CURSOR.set(x, y, z);
            BlockState state = level.getBlockState(CURSOR);
            if (!state.getFluidState().isEmpty()) return;
            if (state.blocksMotion() && !state.is(BlockTags.LEAVES)) {
                ground = y + 1;
                break;
            }
        }
        if (ground == Integer.MIN_VALUE || top > ground + 1 || Math.abs(ground - player.getY()) > 20) return;
        CURSOR.set(x, ground, z);
        if (!dryAt(CURSOR)) return;
        LIVE[slot] = true;
        DYING[slot] = false;
        AIR[slot] = false;
        X[slot] = x + 0.5;
        Y[slot] = ground + HALF;
        Z[slot] = z + 0.5;
        float pace = storm ? 0.3F + random.nextFloat() * 0.15F : 0.075F + random.nextFloat() * 0.05F;
        PACE[slot] = pace;
        VX[slot] = wx * pace * 0.6F;
        VZ[slot] = wz * pace * 0.6F;
        VY[slot] = 0F;
        AXIS_X[slot] = wz;
        AXIS_Z[slot] = -wx;
        ANGLE[slot] = random.nextFloat() * Mth.TWO_PI;
        SEED[slot] = random.nextFloat();
        ALPHA[slot] = 0F;
        AGE[slot] = 0;
        STUCK[slot] = 0;
        COOL[slot] = 0;
        LIGHT[slot] = Litter.light(CURSOR);
        alive++;
    }

    private static float surface(int bx, int bz, float feet) {
        int top = Mth.floor(feet + 1.2F);
        for (int yy = top; yy >= top - 8; yy--) {
            CURSOR.set(bx, yy, bz);
            BlockState state = level.getBlockState(CURSOR);
            if (!state.getFluidState().isEmpty()) return Float.NaN;
            VoxelShape shape = state.getCollisionShape(level, CURSOR);
            if (!shape.isEmpty()) return yy + (float) shape.max(Direction.Axis.Y);
        }
        return Float.NaN;
    }

    private static void update(int i, LocalPlayer player, float wx, float wz, float gust, boolean storm, boolean dry, int target, float now) {
        AGE[i]++;
        double dx = X[i] - player.getX(), dz = Z[i] - player.getZ();
        double d2 = dx * dx + dz * dz;
        if (!DYING[i] && (d2 > 3844.0 || AGE[i] > 2400 || STUCK[i] > 80 || (!dry && AGE[i] > 40) || (alive > target + 2 && AGE[i] > 200))) {
            DYING[i] = true;
        }
        float wind = (0.65F + 0.35F * gust) * (float) Math.max(FxConfig.windStrength, 0.3);
        float tx = wx * PACE[i] * wind, tz = wz * PACE[i] * wind;
        VX[i] += (tx - VX[i]) * 0.07F;
        VZ[i] += (tz - VZ[i]) * 0.07F;
        double px = X[i] - player.getX(), pz = Z[i] - player.getZ();
        double near = px * px + pz * pz;
        if (near < 1.2 && Math.abs(Y[i] - player.getY() - 0.8) < 1.6) {
            float d = Mth.sqrt((float) near);
            float ax = d > 0.05F ? (float) px / d : wx, az = d > 0.05F ? (float) pz / d : wz;
            float shove = 0.05F + 0.25F * (float) Math.sqrt(Math.pow(player.getX() - player.xo, 2) + Math.pow(player.getZ() - player.zo, 2));
            VX[i] += ax * shove;
            VZ[i] += az * shove;
            if (!AIR[i]) {
                VY[i] = 0.2F;
                AIR[i] = true;
            }
            rustle(i, 0.8F);
        }
        float feet = (float) Y[i] - HALF;
        float vx = VX[i], vz = VZ[i];
        boolean bumped = false;
        if (vx != 0F) {
            double nx = X[i] + vx;
            float h = surface(Mth.floor(nx + Math.signum(vx) * 0.4), Mth.floor(Z[i]), feet);
            if (Float.isNaN(h) || h - feet > 1.15F) {
                vx = -vx * 0.45F;
                bumped = true;
            } else if (h - feet > 0.3F) {
                if (!AIR[i]) {
                    VY[i] = 0.43F;
                    AIR[i] = true;
                }
            } else {
                X[i] = nx;
            }
        }
        if (vz != 0F) {
            double nz = Z[i] + vz;
            float h = surface(Mth.floor(X[i]), Mth.floor(nz + Math.signum(vz) * 0.4), feet);
            if (Float.isNaN(h) || h - feet > 1.15F) {
                vz = -vz * 0.45F;
                bumped = true;
            } else if (h - feet > 0.3F) {
                if (!AIR[i]) {
                    VY[i] = 0.43F;
                    AIR[i] = true;
                }
            } else {
                Z[i] = nz;
            }
        }
        if (bumped) {
            STUCK[i] += 6;
            rustle(i, 1F);
        } else if (STUCK[i] > 0) {
            STUCK[i]--;
        }
        VX[i] = vx;
        VZ[i] = vz;
        float h = surface(Mth.floor(X[i]), Mth.floor(Z[i]), (float) Y[i] - HALF);
        float vy = VY[i];
        if (Float.isNaN(h)) {
            STUCK[i] += 3;
            vy = 0F;
        } else {
            double y = Y[i];
            float floor = h + HALF;
            if (vy > 0F || y > floor + 0.002) {
                y += vy;
                vy -= GRAVITY;
                if (y <= floor) {
                    float impact = -vy;
                    y = floor;
                    float hop = storm ? 0.26F : 0.2F;
                    vy = impact > 0.08F ? Math.max(impact * 0.85F, hop * 0.7F) : hop;
                    vy = Math.min(vy, storm ? 0.34F : 0.3F);
                    if (impact > 0.1F) rustle(i, Math.min(1F, impact * 4F));
                }
            } else {
                y = floor;
                vy = storm ? 0.26F : 0.2F;
            }
            Y[i] = y;
            AIR[i] = true;
        }
        VY[i] = vy;
        float speed = Mth.sqrt(vx * vx + vz * vz);
        if (speed > 0.004F) {
            float axX = vz / speed, axZ = -vx / speed;
            AXIS_X[i] += (axX - AXIS_X[i]) * 0.2F;
            AXIS_Z[i] += (axZ - AXIS_Z[i]) * 0.2F;
        }
        ANGLE[i] = (ANGLE[i] + speed / HALF) % Mth.TWO_PI;
        float edge = Mth.clamp(((float) Math.sqrt(d2) - 40F) / 18F, 0F, 1F);
        float fade = Math.min(1F, AGE[i] / FADE_IN) * (1F - edge * edge * (3F - 2F * edge));
        ALPHA[i] = DYING[i] ? Math.max(0F, Math.min(ALPHA[i], fade) - 0.06F) : fade;
        if (DYING[i] && ALPHA[i] <= 0F) {
            LIVE[i] = false;
            alive--;
            int o = i * 4;
            for (float[] row : ROW) {
                row[o] = row[o + 1] = row[o + 2] = row[o + 3] = 0F;
            }
            return;
        }
        if ((AGE[i] & 3) == 0) {
            CURSOR.set(Mth.floor(X[i]), Mth.floor(Y[i]), Mth.floor(Z[i]));
            if (Litter.ready(CURSOR.getX() >> 4, CURSOR.getZ() >> 4)) LIGHT[i] = Litter.light(CURSOR);
        }
        int o = i * 4;
        ROW[0][o] = (float) (X[i] - anchorX);
        ROW[0][o + 1] = (float) (Y[i] - anchorY);
        ROW[0][o + 2] = (float) (Z[i] - anchorZ);
        ROW[0][o + 3] = Math.max(ALPHA[i], 0.001F);
        ROW[1][o] = AXIS_X[i];
        ROW[1][o + 1] = AXIS_Z[i];
        ROW[1][o + 2] = ANGLE[i];
        ROW[1][o + 3] = LIGHT[i];
        ROW[2][o] = vx * 20F;
        ROW[2][o + 1] = vy * 20F;
        ROW[2][o + 2] = vz * 20F;
        ROW[2][o + 3] = now;
        ROW[3][o] = HALF;
        ROW[3][o + 1] = SEED[i];
        ROW[3][o + 2] = speed * 20F / HALF;
    }

    private static void rustle(int i, float loud) {
        long tick = level.getGameTime();
        if (tick - COOL[i] < 12) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double dx = X[i] - mc.player.getX(), dz = Z[i] - mc.player.getZ();
        if (dx * dx + dz * dz > 900.0) return;
        COOL[i] = (int) tick;
        RandomSource random = level.random;
        level.playLocalSound(X[i], Y[i], Z[i], random.nextBoolean() ? SoundEvents.GRASS_STEP : SoundEvents.GRASS_HIT,
                SoundSource.AMBIENT, 0.07F + 0.09F * loud, 1.25F + random.nextFloat() * 0.35F, false);
        Diagnostics.once("a tumbleweed rustled");
    }

    public static void onRenderTumbleweedsStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) return;
        if (!anchored || alive == 0 || LightTint.lightmap == null) return;
        SkyProgram program = SkyProgram.TUMBLEWEED;
        if (!program.ready()) return;

        upload();
        Vec3 eye = event.getCamera().getPosition();
        float ox = (float) (anchorX - eye.x);
        float oy = (float) (anchorY - eye.y);
        float oz = (float) (anchorZ - eye.z);
        MATRIX.set(event.getPoseStack().last().pose()).translate(ox, oy, oz);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        program.begin();
        SkyProgram.texture(2, data);
        SkyProgram.texture(1, LightTint.lightmap.getId());
        SkyProgram.texture(0, SkyProgram.texture(TEXTURE));
        program.transform(0, MATRIX);
        GL20.glUniform3f(program.uniform(1), ox, oy, oz);
        GL20.glUniform4f(program.uniform(2), now(), RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), 0F);
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.uniform(3), fog[0], fog[1], fog[2], 1F);
        program.draw(SLOTS * 18);
        program.end();
        RenderSystem.enableCull();
    }

    private static void upload() {
        if (data == 0) {
            data = GlStateManager._genTexture();
            GlStateManager._bindTexture(data);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            SkyHeights.unpack();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, SLOTS, ROWS, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            dirty = true;
        }
        if (!dirty) return;
        GlStateManager._bindTexture(data);
        SkyHeights.unpack();
        for (int row = 0; row < ROWS; row++) {
            UPLOAD.clear();
            UPLOAD.put(ROW[row]).flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, row, SLOTS, 1, GL11.GL_RGBA, GL11.GL_FLOAT, UPLOAD);
        }
        dirty = false;
    }
}
