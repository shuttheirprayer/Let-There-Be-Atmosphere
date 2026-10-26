package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.CaptureHook;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

@OnlyIn(Dist.CLIENT)
public final class Puffs {
    private Puffs() {}

    private static final int SLOTS = 160;
    private static final int STRIDE = 12;
    private static final double RANGE_SQ = 24.0 * 24.0;
    private static final double LOST_SQ = 34.0 * 34.0;
    private static final int PUFF = 0;
    private static final int DROP = 1;
    private static final int SELF = 2;
    private static final int BREATH = 3;
    private static final int POWDER = 4;
    private static final ResourceLocation SHEET = new ResourceLocation(Let_there_be_atmosphere.MODID, "textures/fx/puffs.png");
    private static final double[] X = new double[SLOTS];
    private static final double[] Y = new double[SLOTS];
    private static final double[] Z = new double[SLOTS];
    private static final float[] VX = new float[SLOTS];
    private static final float[] VY = new float[SLOTS];
    private static final float[] VZ = new float[SLOTS];
    private static final float[] GRAVITY = new float[SLOTS];
    private static final float[] DRAG = new float[SLOTS];
    private static final float[] FLOOR = new float[SLOTS];
    private static final float[] BORN = new float[SLOTS];
    private static final float[] LIFE = new float[SLOTS];
    private static final float[] SIZE_A = new float[SLOTS];
    private static final float[] SIZE_B = new float[SLOTS];
    private static final float[] SEED = new float[SLOTS];
    private static final float[] ALPHA = new float[SLOTS];
    private static final float[] RED = new float[SLOTS];
    private static final float[] GREEN = new float[SLOTS];
    private static final float[] BLUE = new float[SLOTS];
    private static final int[] KIND = new int[SLOTS];
    private static final float[] BLOCK_LIGHT = new float[SLOTS];
    private static final float[] SKY_LIGHT = new float[SLOTS];
    private static final float[] DATA = new float[SLOTS * STRIDE];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(SLOTS * STRIDE);
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<Integer, Walker> WALKERS = new HashMap<>();
    private static final float[] TINT = new float[3];

    private static final long BASE = Util.getNanos();
    private static long simClock = Util.getNanos();
    private static float pushX, pushZ;
    private static int alive;
    private static int ticks;
    private static int cursor;
    private static float groundY;

    private static final class Walker {
        double x, z, y, top;
        float stride;
        int breath = RANDOM.nextInt(60);
        int seen;
        int air;
        boolean ground, known;
    }

    private static float now() {
        return (Util.getNanos() - BASE) / 1.0E9F;
    }

    private static boolean allowed(ClientLevel level) {
        return (FxConfig.breathPuffs || FxConfig.footstepPuffs) && DynamicFogHandler.active();
    }

    public static void onPuffsTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || !allowed(level)) {
            if (alive > 0) java.util.Arrays.fill(LIFE, 0F);
            alive = 0;
            WALKERS.clear();
            Diagnostics.skip(Diagnostics.PUFFS, "disabled or paused by fog/shader pack");
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        float now = now();
        int count = 0;
        for (int i = 0; i < SLOTS; i++) {
            float age = now - BORN[i];
            if (LIFE[i] <= 0F || age > LIFE[i]) {
                LIFE[i] = 0F;
                continue;
            }
            double dx = X[i] - player.getX(), dy = Y[i] - player.getY(), dz = Z[i] - player.getZ();
            if (dx * dx + dy * dy + dz * dz > LOST_SQ) {
                LIFE[i] = 0F;
                continue;
            }
            count++;
            CURSOR.set(X[i], Y[i], Z[i]);
            BlockState state = level.getBlockState(CURSOR);
            if (age < LIFE[i] - 0.2F && state.isCollisionShapeFullBlock(level, CURSOR)) LIFE[i] = age + 0.2F;
            BLOCK_LIGHT[i] += (level.getBrightness(LightLayer.BLOCK, CURSOR) - BLOCK_LIGHT[i]) * 0.35F;
            SKY_LIGHT[i] += (level.getBrightness(LightLayer.SKY, CURSOR) - SKY_LIGHT[i]) * 0.35F;
        }
        alive = count;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living.isRemoved() || living.distanceToSqr(player) > RANGE_SQ) continue;
            if (!(living instanceof Player) && !(living instanceof Mob)) continue;
            visit(level, living, living == player);
        }
        if (ticks % 100 == 0) WALKERS.values().removeIf(w -> ticks - w.seen > 40);
        Diagnostics.mark(Diagnostics.PUFFS, Diagnostics.detail() ? alive + " puffs" : null);
    }

    private static void visit(ClientLevel level, LivingEntity e, boolean self) {
        Walker w = WALKERS.computeIfAbsent(e.getId(), id -> new Walker());
        w.seen = ticks;
        double dx = e.getX() - w.x, dz = e.getZ() - w.z;
        double moved = Math.sqrt(dx * dx + dz * dz);
        boolean ground = e.onGround();
        boolean hidden = e.isSpectator() || e.isInvisible() && !self || e.isPassenger() || e.isDeadOrDying();
        if (w.known && moved < 2.0 && FxConfig.footstepPuffs && !hidden && !flies(e)) {
            if (ground) {
                if (!w.ground && w.air >= 3 && w.top - e.getY() > 1.0) kick(level, e, w.top - e.getY(), 0.0, 0.0, 1.0F, true);
                boolean sprint = e.isSprinting();
                float len = (0.55F + 0.7F * e.getBbWidth()) * (sprint ? 0.85F : 1.0F);
                if (moved > 0.03 && !e.isCrouching()) {
                    w.stride += (float) moved;
                    if (w.stride >= len) {
                        w.stride = w.stride > len * 2F ? 0F : w.stride - len;
                        float speed = Mth.clamp((float) moved / 0.2F, 0.5F, 1.5F) * (sprint ? 1.35F : 1.0F);
                        kick(level, e, 0.0, dx / moved, dz / moved, speed, false);
                    }
                }
                w.air = 0;
                w.top = e.getY();
            } else {
                w.air++;
                w.top = Math.max(w.top, e.getY());
            }
        } else {
            w.top = e.getY();
            w.stride = 0F;
        }
        w.ground = ground;
        w.known = true;
        w.x = e.getX();
        w.y = e.getY();
        w.z = e.getZ();
        if (FxConfig.breathPuffs && !hidden && breathes(e) && --w.breath <= 0) {
            w.breath = (e.isSprinting() ? 28 : 48) + RANDOM.nextInt(e.isSprinting() ? 18 : 34);
            breath(level, e, self);
        }
    }

    private static boolean flies(LivingEntity e) {
        return e instanceof FlyingMob || e instanceof FlyingAnimal || e instanceof WaterAnimal || e instanceof Bat || e instanceof Slime || e.isInWater() && e.isSwimming();
    }

    private static boolean breathes(LivingEntity e) {
        if (e instanceof Player) return !e.isSpectator();
        return (e instanceof AbstractVillager || e instanceof AbstractIllager || e instanceof Animal) && !(e instanceof FlyingAnimal) && !(e instanceof Axolotl);
    }

    private static void breath(ClientLevel level, LivingEntity e, boolean self) {
        BlockPos at = e.blockPosition();
        if (!level.hasChunkAt(at) || e.isEyeInFluid(FluidTags.WATER) || !level.getBiome(at).value().coldEnoughToSnow(at)) return;
        boolean first = self && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        float scale = Mth.clamp(e.getBbHeight() / 1.8F, 0.5F, 1.4F);
        Vec3 look = e.getViewVector(1.0F);
        Vec3 eye = e.getEyePosition();
        double ahead = first ? 0.6 : 0.16 + 0.34 * e.getBbWidth();
        double mouth = first ? 0.3 : 0.16 * scale;
        double x = eye.x + look.x * ahead;
        double y = eye.y - mouth + look.y * ahead;
        double z = eye.z + look.z * ahead;
        CURSOR.set(x, y, z);
        if (level.getBlockState(CURSOR).isCollisionShapeFullBlock(level, CURSOR)) return;
        float push = first ? 0.35F : 0.4F + RANDOM.nextFloat() * 0.2F;
        float size = (first ? 0.4F : 0.75F) * scale * (0.9F + RANDOM.nextFloat() * 0.2F);
        spawn(first ? SELF : BREATH, x, y, z, (float) look.x * push, (float) look.y * push * 0.6F + 0.1F, (float) look.z * push,
                1.5F, 0.1F, 1.2F + RANDOM.nextFloat() * 0.3F, (first ? 0.1F : 0.2F) * scale, size, (first ? 0.7F : 1.0F), 0.98F, 0.99F, 1.0F, -1E9F);
    }

    private static int roll(double amount) {
        int n = (int) amount;
        return RANDOM.nextFloat() < amount - n ? n + 1 : n;
    }

    private static void kick(ClientLevel level, LivingEntity e, double fall, double dirX, double dirZ, float speed, boolean land) {
        double ex = e.getX(), ey = e.getY(), ez = e.getZ();
        int kind = ground(level, ex, ey, ez);
        if (kind == 0) return;
        float scale = Mth.clamp(e.getBbWidth() / 0.6F, 0.6F, 1.6F);
        float big = land ? 1.3F + Mth.clamp((float) fall * 0.18F, 0F, 0.7F) : 0.7F + 0.5F * speed;
        float root = Mth.sqrt(scale);
        int count = roll(FxConfig.footstepPuffsAmount * (land ? 5 : speed > 1.2F ? 4 : 3));
        for (int i = 0; i < count; i++) {
            double ox = (RANDOM.nextDouble() - 0.5) * 0.4 * scale, oz = (RANDOM.nextDouble() - 0.5) * 0.4 * scale;
            float angle = RANDOM.nextFloat() * Mth.TWO_PI;
            float spread = land ? 0.7F : 0.12F;
            float vx = (float) -dirX * 0.4F * speed + Mth.cos(angle) * spread * RANDOM.nextFloat();
            float vz = (float) -dirZ * 0.4F * speed + Mth.sin(angle) * spread * RANDOM.nextFloat();
            if (kind == 3) {
                double wy = groundY;
                int drops = land ? 3 : 2;
                for (int d = 0; d < drops; d++) {
                    float a = RANDOM.nextFloat() * Mth.TWO_PI;
                    float h = 0.25F + RANDOM.nextFloat() * 0.45F;
                    spawn(DROP, ex + ox, wy + 0.04, ez + oz, Mth.cos(a) * h, (1.5F + RANDOM.nextFloat() * 1.3F) * (0.8F + 0.3F * big), Mth.sin(a) * h,
                            0.55F, 9.0F, 0.42F + RANDOM.nextFloat() * 0.12F, 0.07F + RANDOM.nextFloat() * 0.03F, 0.09F + RANDOM.nextFloat() * 0.04F,
                            0.8F, 0.8F, 0.92F, 1.0F, (float) wy);
                }
                spawn(POWDER, ex + ox, wy + 0.06, ez + oz, vx, 0.5F, vz, 2.0F, 0F, 0.8F + RANDOM.nextFloat() * 0.2F,
                        0.09F, 0.3F * big * root, 0.75F, 0.95F, 0.98F, 1.0F, (float) wy);
            } else {
                spawn(kind == 2 ? POWDER : PUFF, ex + ox, ey + 0.04, ez + oz, vx, 0.5F + RANDOM.nextFloat() * 0.25F, vz, 2.0F, 0F, 0.9F + RANDOM.nextFloat() * 0.4F,
                        0.09F * root, (0.26F + RANDOM.nextFloat() * 0.12F) * big * root, 0.8F, TINT[0], TINT[1], TINT[2], -1E9F);
            }
        }
    }

    private static int ground(ClientLevel level, double x, double y, double z) {
        BlockPos feet = BlockPos.containing(x, y + 0.02, z);
        if (!level.hasChunkAt(feet)) return 0;
        BlockState fs = level.getBlockState(feet);
        if (fs.getFluidState().is(FluidTags.WATER)) {
            if (level.getFluidState(feet.above()).is(FluidTags.WATER)) return 0;
            groundY = feet.getY() + fs.getFluidState().getHeight(level, feet);
            return 3;
        }
        BlockPos stand = BlockPos.containing(x, y - 0.02, z);
        BlockState ss = level.getBlockState(stand);
        if (fs.is(Blocks.SNOW) || ss.is(Blocks.SNOW) || ss.is(Blocks.SNOW_BLOCK) || ss.is(Blocks.POWDER_SNOW) || fs.is(Blocks.POWDER_SNOW)) {
            TINT[0] = 0.97F;
            TINT[1] = 0.99F;
            TINT[2] = 1.0F;
            return 2;
        }
        if (!dusty(ss) || level.isRainingAt(feet)) return 0;
        int col = ss.getMapColor(level, stand).col;
        float r = (col >> 16 & 255) / 255F, g = (col >> 8 & 255) / 255F, b = (col & 255) / 255F;
        float luma = r * 0.3F + g * 0.59F + b * 0.11F;
        if (luma > 0.6F) {
            TINT[0] = Math.min(1F, r * 0.7F * 1.06F);
            TINT[1] = g * 0.7F * 0.98F;
            TINT[2] = b * 0.7F * 0.86F;
        } else {
            TINT[0] = r + (1F - r) * 0.48F;
            TINT[1] = g + (1F - g) * 0.48F;
            TINT[2] = b + (1F - b) * 0.48F;
        }
        return 1;
    }

    private static boolean dusty(BlockState state) {
        Block block = state.getBlock();
        return state.is(BlockTags.SAND) || block == Blocks.GRAVEL || block == Blocks.DIRT || block == Blocks.COARSE_DIRT
                || block == Blocks.DIRT_PATH || block == Blocks.ROOTED_DIRT || block == Blocks.PODZOL
                || block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL;
    }

    private static void spawn(int kind, double x, double y, double z, float vx, float vy, float vz, float drag, float gravity, float life,
                              float sizeA, float sizeB, float alpha, float r, float g, float b, float floor) {
        float now = now();
        int slot = -1;
        for (int n = 0; n < SLOTS; n++) {
            int i = (cursor + n) % SLOTS;
            if (LIFE[i] <= 0F || now - BORN[i] > LIFE[i]) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return;
        cursor = (slot + 1) % SLOTS;
        X[slot] = x;
        Y[slot] = y;
        Z[slot] = z;
        VX[slot] = vx;
        VY[slot] = vy;
        VZ[slot] = vz;
        DRAG[slot] = drag;
        GRAVITY[slot] = gravity;
        FLOOR[slot] = floor;
        BORN[slot] = now;
        LIFE[slot] = life;
        SIZE_A[slot] = sizeA;
        SIZE_B[slot] = sizeB;
        SEED[slot] = RANDOM.nextFloat();
        ALPHA[slot] = alpha;
        RED[slot] = r;
        GREEN[slot] = g;
        BLUE[slot] = b;
        KIND[slot] = kind;
        BLOCK_LIGHT[slot] = Minecraft.getInstance().level.getBrightness(LightLayer.BLOCK, CURSOR.set(x, y, z));
        SKY_LIGHT[slot] = Minecraft.getInstance().level.getBrightness(LightLayer.SKY, CURSOR);
        alive++;
    }

    public static void onRenderPuffsStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || alive <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !allowed(level)) return;
        Camera camera = event.getCamera();
        if (CameraFluid.of(camera) != FogType.NONE) return;
        SkyProgram program = SkyProgram.PUFFS;
        if (!program.ready()) return;

        Vec3 eye = camera.getPosition();
        float partial = event.getPartialTick();
        float day = Mth.clamp((level.getSkyDarken(partial) - 0.2F) / 0.8F, 0F, 1F);
        float now = now();
        step(level, partial, eye.y);
        int drawn = 0;
        for (int i = 0; i < SLOTS; i++) {
            float age = now - BORN[i];
            if (LIFE[i] <= 0F || age < 0F || age > LIFE[i]) continue;
            float f = age / LIFE[i];
            float grow = 1F - (1F - f) * (1F - f);
            boolean drop = KIND[i] == DROP;
            float fade = drop ? smooth(0F, 0.08F, f) * (1F - smooth(0.7F, 1F, f))
                    : smooth(0F, 0.1F, f) * (1F - smooth(0.42F, 1F, f));
            float light = Mth.clamp(Math.max(BLOCK_LIGHT[i], SKY_LIGHT[i] * (0.2F + 0.8F * day)) / 15F, 0F, 1F);
            int o = drawn * STRIDE;
            DATA[o] = (float) (X[i] - eye.x);
            DATA[o + 1] = (float) (Y[i] - eye.y);
            DATA[o + 2] = (float) (Z[i] - eye.z);
            DATA[o + 3] = Mth.lerp(drop ? f : grow, SIZE_A[i], SIZE_B[i]);
            DATA[o + 4] = fade * ALPHA[i];
            DATA[o + 5] = SEED[i];
            DATA[o + 6] = (float) Math.pow(light, 0.85);
            DATA[o + 7] = age;
            DATA[o + 8] = RED[i];
            DATA[o + 9] = GREEN[i];
            DATA[o + 10] = BLUE[i];
            DATA[o + 11] = KIND[i];
            drawn++;
        }
        if (drawn == 0) return;

        MATRIX.set(event.getPoseStack().last().pose());
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        float[] fog = RenderSystem.getShaderFogColor();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        program.begin();
        SkyProgram.texture(0, SkyProgram.texture(SHEET));
        program.transform(0, MATRIX);
        GL20.glUniform3f(program.uniform(1), -left.x(), -left.y(), -left.z());
        GL20.glUniform3f(program.uniform(2), up.x(), up.y(), up.z());
        GL20.glUniform4f(program.uniform(3), RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), day, 24F);
        GL20.glUniform4f(program.uniform(4), fog[0], fog[1], fog[2], now);
        UPLOAD.clear();
        UPLOAD.put(DATA, 0, drawn * STRIDE).flip();
        GL20.glUniform4fv(program.uniform(5), UPLOAD);
        CaptureHook.probeBegin("puffs");
        program.draw(drawn * 6);
        CaptureHook.probeEnd("puffs");
        program.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void step(ClientLevel level, float partialTick, double eyeY) {
        long nanos = Util.getNanos();
        float dt = Mth.clamp((nanos - simClock) / 1.0E9F, 0F, 0.1F);
        simClock = nanos;
        if (dt <= 0F) return;
        WindParticles.sampleDrift(level, partialTick, eyeY);
        float ease = 1F - (float) Math.exp(-dt * 1.5F);
        pushX += (WindParticles.driftX * 0.45F - pushX) * ease;
        pushZ += (WindParticles.driftZ * 0.45F - pushZ) * ease;
        for (int i = 0; i < SLOTS; i++) {
            if (LIFE[i] <= 0F) continue;
            float keep = (float) Math.exp(-DRAG[i] * dt);
            VX[i] *= keep;
            VY[i] = VY[i] * keep + (KIND[i] == DROP ? -GRAVITY[i] : GRAVITY[i]) * dt;
            VZ[i] *= keep;
            float wind = KIND[i] == DROP ? 0.2F : 1F;
            X[i] += (VX[i] + pushX * wind) * dt;
            Y[i] += VY[i] * dt;
            Z[i] += (VZ[i] + pushZ * wind) * dt;
            if (KIND[i] == DROP && Y[i] < FLOOR[i]) LIFE[i] = 0F;
        }
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0F, 1F);
        return t * t * (3F - 2F * t);
    }
}
