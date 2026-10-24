package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

final class LitterMotion {
    private LitterMotion() {}

    private static final int SM = Litter.SOLID_MOVERS;
    private static final int N = SM + Litter.FLAT_MOVERS;
    private static final int STONE = 0, STICK = 1, LIGHT = 2;
    private static final float LIFT = 0.01F;
    private static final float GRAVITY = 0.055F;
    private static final float STEP = 0.56F;
    private static final float[] FRICTION = {0.74F, 0.80F, 0.86F};
    private static final float[] SPEED_CAP = {0.11F, 0.15F, 0.20F};
    private static final float[] RADIUS = {0.09F, 0.22F, 0.2F};
    private static final float[] MASS = {0.5F, 0.7F, 1F};

    private static final boolean[] LIVE = new boolean[N];
    private static final boolean[] AIR = new boolean[N];
    private static final boolean[] WET = new boolean[N];
    private static final boolean[] EXPOSED = new boolean[N];
    private static final byte[] TYPE = new byte[N];
    private static final long[] ORIGIN = new long[N];
    private static final double[] X = new double[N], Y = new double[N], Z = new double[N];
    private static final float[] VX = new float[N], VY = new float[N], VZ = new float[N];
    private static final float[] YAW = new float[N], SPIN = new float[N];
    private static final float[] SLOPE_X = new float[N], SLOPE_Z = new float[N];
    private static final int[] REST = new int[N], AGE = new int[N], COOL = new int[N], HIT = new int[N];
    private static final float[] FLOOR = new float[N];
    private static final BlockPos.MutableBlockPos P = new BlockPos.MutableBlockPos();

    private static boolean water;
    private static int alive;
    private static long secondStart;
    private static int sounds;
    private static double playerX, playerZ;

    static void reset() {
        for (int i = 0; i < N; i++) LIVE[i] = false;
        alive = 0;
    }

    static int count() {
        return alive;
    }

    static boolean displaced(float rx, float rz) {
        if (alive == 0) return false;
        long key = key(rx, rz);
        for (int i = 0; i < N; i++) if (LIVE[i] && ORIGIN[i] == key) return true;
        return false;
    }

    private static long key(float rx, float rz) {
        return (long) Float.floatToIntBits(rx) << 32 | (Float.floatToIntBits(rz) & 0xFFFFFFFFL);
    }

    private static int slotOf(int i) {
        return i < SM ? Litter.SOLID_MOVE_BASE + i : Litter.FLAT_MOVE_BASE + (i - SM);
    }

    static void tick(LocalPlayer player, float now) {
        ClientLevel level = Litter.level;
        playerX = player.getX();
        playerZ = player.getZ();
        float rain = level.getRainLevel(1F);
        float thunder = level.getThunderLevel(1F);
        float seconds = Wind.seconds(level, 0F);
        float windPower = Wind.gust(seconds) * (float) FxConfig.windStrength;
        float wx = Wind.x(seconds), wz = Wind.z(seconds);
        boolean storm = rain > 0.4F && thunder > 0.35F;
        float pulse = 0F;
        if (storm) {
            float s = 0.5F + 0.5F * Mth.sin(seconds * 0.85F + 1.7F * Mth.sin(seconds * 0.31F));
            pulse = Mth.clamp(s - 0.45F, 0F, 0.55F) / 0.55F;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) && !(entity instanceof Boat)) continue;
            double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
            double d2 = dx * dx + dz * dz;
            if (d2 < 0.0012 || d2 > 1.0 || entity.isSpectator() || entity.isPassenger()) continue;
            double px = entity.getX() - playerX, pz = entity.getZ() - playerZ;
            if (px * px + pz * pz > 576.0 || !entity.onGround()) continue;
            push(entity, (float) dx, (float) dz, (float) Math.sqrt(d2), now);
        }
        if (storm && pulse > 0.2F) stirWind(player, wx, wz, pulse * windPower);
        if (rain > 0.4F) stirRain(player, wx, wz, rain, windPower);
        for (int i = 0; i < N; i++) {
            if (!LIVE[i]) continue;
            AGE[i]++;
            double dx = X[i] - playerX, dz = Z[i] - playerZ;
            if (dx * dx + dz * dz > 2500.0 && REST[i] > 20) {
                free(i);
                continue;
            }
            int type = TYPE[i];
            if (storm) {
                float a = (0.003F + 0.02F * pulse) * windPower * (type == LIGHT ? 1F : type == STICK ? 0.12F : 0.04F);
                VX[i] += wx * a;
                VZ[i] += wz * a;
            }
            if (rain > 0.4F && type == LIGHT) {
                if ((AGE[i] + i) % 8 == 0) sampleRain(i);
                if (EXPOSED[i]) {
                    float a = 0.0025F * rain * windPower;
                    VX[i] += wx * a + SLOPE_X[i] * 0.004F;
                    VZ[i] += wz * a + SLOPE_Z[i] * 0.004F;
                }
            }
            clamp(i);
            if (VX[i] != 0F || VZ[i] != 0F || VY[i] != 0F || AIR[i]) {
                step(i);
                store(i, now);
            } else {
                REST[i]++;
            }
        }
    }

    private static void clamp(int i) {
        float cap = SPEED_CAP[TYPE[i]];
        float s2 = VX[i] * VX[i] + VZ[i] * VZ[i];
        if (s2 > cap * cap) {
            float k = cap / Mth.sqrt(s2);
            VX[i] *= k;
            VZ[i] *= k;
        }
    }

    private static float surface(int bx, int bz, float fromY, boolean light) {
        ClientLevel level = Litter.level;
        water = false;
        int top = Mth.floor(fromY + STEP);
        for (int yy = top; yy >= top - 7; yy--) {
            P.set(bx, yy, bz);
            BlockState state = level.getBlockState(P);
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                if (!light || !fluid.is(FluidTags.WATER)) return Float.NaN;
                water = true;
                float h = yy + fluid.getHeight(level, P);
                return h - fromY > STEP ? Float.NaN : h;
            }
            VoxelShape shape = state.getCollisionShape(level, P);
            if (!shape.isEmpty()) {
                float h = yy + (float) shape.max(Direction.Axis.Y);
                return h - fromY > STEP ? Float.NaN : h;
            }
        }
        return Float.NaN;
    }

    private static void step(int i) {
        int type = TYPE[i];
        boolean light = type == LIGHT;
        float r = RADIUS[type];
        float y = (float) Y[i];
        float vx = VX[i], vz = VZ[i];
        if (vx != 0F) {
            double nx = X[i] + vx;
            float h = surface(Mth.floor(nx + Math.signum(vx) * r), Mth.floor(Z[i]), y, light);
            if (Float.isNaN(h)) {
                if (Math.abs(vx) > 0.05F) sound(i, Math.min(1F, Math.abs(vx) * 5F));
                vx = -vx * 0.22F;
                SPIN[i] += (Litter.level.random.nextFloat() - 0.5F) * 0.3F;
            } else {
                X[i] = nx;
            }
        }
        if (vz != 0F) {
            double nz = Z[i] + vz;
            float h = surface(Mth.floor(X[i]), Mth.floor(nz + Math.signum(vz) * r), y, light);
            if (Float.isNaN(h)) {
                if (Math.abs(vz) > 0.05F) sound(i, Math.min(1F, Math.abs(vz) * 5F));
                vz = -vz * 0.22F;
                SPIN[i] += (Litter.level.random.nextFloat() - 0.5F) * 0.3F;
            } else {
                Z[i] = nz;
            }
        }
        float h = surface(Mth.floor(X[i]), Mth.floor(Z[i]), y, light);
        float vy = VY[i];
        if (!Float.isNaN(h)) {
            WET[i] = water;
            FLOOR[i] = h;
            if (vy > 0F || y > h + 0.002F) {
                y += vy;
                vy -= GRAVITY;
                if (y <= h) {
                    float impact = -vy;
                    y = h;
                    if (impact > 0.09F) {
                        if (impact > 0.14F) sound(i, 0.5F);
                        vy = light ? impact * 0.28F : 0F;
                    } else {
                        vy = 0F;
                    }
                }
            } else {
                y = h;
                vy = 0F;
            }
        } else {
            vy = 0F;
        }
        Y[i] = y;
        VY[i] = vy;
        AIR[i] = vy != 0F || (!Float.isNaN(h) && y > h + 0.002F);
        float f = WET[i] && light ? 0.94F : FRICTION[type];
        vx *= f;
        vz *= f;
        if (vx * vx + vz * vz < 0.000016F && !AIR[i]) {
            vx = 0F;
            vz = 0F;
        }
        VX[i] = vx;
        VZ[i] = vz;
        YAW[i] += SPIN[i];
        SPIN[i] *= 0.86F;
        if (Math.abs(SPIN[i]) < 0.002F) SPIN[i] = 0F;
        REST[i] = vx == 0F && vz == 0F && !AIR[i] ? REST[i] + 1 : 0;
    }

    private static void store(int i, float now) {
        int o = slotOf(i) * 4;
        float[][] row = Litter.ROW;
        boolean solid = i < SM;
        row[0][o] = (float) (X[i] - Litter.anchorX);
        row[0][o + 1] = (float) (Y[i] - Litter.anchorY) + (solid ? 0F : LIFT + 0.002F * (1 + i % 5));
        row[0][o + 2] = (float) (Z[i] - Litter.anchorZ);
        float vy = AIR[i] ? Math.max(VY[i], (FLOOR[i] - (float) Y[i]) / 1.4F) * 20F : 0F;
        if (solid) {
            row[2][o] = VX[i] * 20F;
            row[2][o + 1] = vy;
            row[2][o + 2] = VZ[i] * 20F;
            row[2][o + 3] = now;
            row[3][o] = YAW[i];
        } else {
            row[2][o + 1] = vy;
            row[2][o + 2] = VX[i] * 20F;
            row[2][o + 3] = VZ[i] * 20F;
            row[3][o] = now;
            row[3][o + 1] = YAW[i];
            row[3][o + 3] = 1F;
        }
        if ((AGE[i] & 7) == 0 && (VX[i] != 0F || VZ[i] != 0F)) {
            int bx = Mth.floor(X[i]), bz = Mth.floor(Z[i]);
            if (Litter.ready(bx >> 4, bz >> 4)) {
                P.set(bx, Mth.floor(Y[i] + 0.1), bz);
                row[1][o + 2] = Litter.light(P);
            }
        }
        Litter.markDirty(slotOf(i), slotOf(i) + 1);
    }

    private static int alloc(boolean solid) {
        int from = solid ? 0 : SM, to = solid ? SM : N;
        int pick = -1;
        for (int i = from; i < to && pick < 0; i++) if (!LIVE[i]) pick = i;
        if (pick < 0) {
            double far = -1.0;
            for (int i = from; i < to; i++) {
                if (REST[i] < 4) continue;
                double dx = X[i] - playerX, dz = Z[i] - playerZ;
                double d2 = dx * dx + dz * dz;
                if (d2 > far) {
                    far = d2;
                    pick = i;
                }
            }
        }
        if (pick < 0) {
            int oldest = -1;
            for (int i = from; i < to; i++) if (AGE[i] > oldest) {
                oldest = AGE[i];
                pick = i;
            }
        }
        if (LIVE[pick]) free(pick);
        return pick;
    }

    private static void free(int i) {
        LIVE[i] = false;
        alive--;
        int o = slotOf(i) * 4;
        Litter.ROW[1][o + 3] = 0F;
        Litter.markDirty(slotOf(i), slotOf(i) + 1);
    }

    private static int promote(int staticSlot, boolean solid) {
        int i = alloc(solid);
        float[][] row = Litter.ROW;
        int o = staticSlot * 4, m = slotOf(i) * 4;
        for (int r = 0; r < 4; r++) for (int k = 0; k < 4; k++) row[r][m + k] = row[r][o + k];
        row[1][o + 3] = 0F;
        Litter.markDirty(staticSlot, staticSlot + 1);
        ORIGIN[i] = key(row[0][o], row[0][o + 2]);
        X[i] = row[0][o] + Litter.anchorX;
        Z[i] = row[0][o + 2] + Litter.anchorZ;
        Y[i] = row[0][o + 1] + Litter.anchorY - (solid ? 0.0 : LIFT);
        int code = (int) row[1][o];
        TYPE[i] = (byte) (solid ? (code <= 1 || code == 6 ? STICK : STONE) : LIGHT);
        VX[i] = VZ[i] = VY[i] = SPIN[i] = 0F;
        YAW[i] = 0F;
        AIR[i] = false;
        WET[i] = false;
        EXPOSED[i] = false;
        SLOPE_X[i] = SLOPE_Z[i] = 0F;
        REST[i] = 0;
        AGE[i] = 0;
        COOL[i] = Integer.MIN_VALUE / 2;
        HIT[i] = -100;
        FLOOR[i] = (float) Y[i];
        if (solid) {
            row[2][m] = row[2][m + 1] = row[2][m + 2] = 0F;
            row[2][m + 3] = -1000F;
            row[3][m] = 0F;
        } else {
            row[2][m + 1] = row[2][m + 2] = row[2][m + 3] = 0F;
            row[3][m] = 0F;
            row[3][m + 1] = 0F;
            row[3][m + 3] = 1F;
        }
        LIVE[i] = true;
        alive++;
        Litter.markDirty(slotOf(i), slotOf(i) + 1);
        return i;
    }

    private static void impulse(int i, float dx, float dz, float amount, float hop, float loud) {
        float k = amount * MASS[TYPE[i]];
        VX[i] += dx * k;
        VZ[i] += dz * k;
        clamp(i);
        if (hop > 0F && TYPE[i] == LIGHT && !AIR[i] && !WET[i]) {
            VY[i] = hop;
            AIR[i] = true;
        }
        SPIN[i] += (Litter.level.random.nextFloat() - 0.5F) * (TYPE[i] == LIGHT ? 0.25F : 0.5F);
        REST[i] = 0;
        if (k >= 0.03F) sound(i, loud);
    }

    private static void push(Entity entity, float mx, float mz, float speed, float now) {
        float reach = entity.getBbWidth() * 0.5F + 0.28F;
        double ex = entity.getX(), ez = entity.getZ(), ey = entity.getY();
        int x0 = SectionPos.blockToSectionCoord(Mth.floor(ex - reach));
        int x1 = SectionPos.blockToSectionCoord(Mth.floor(ex + reach));
        int z0 = SectionPos.blockToSectionCoord(Mth.floor(ez - reach));
        int z1 = SectionPos.blockToSectionCoord(Mth.floor(ez + reach));
        float[][] row = Litter.ROW;
        float exr = (float) (ex - Litter.anchorX), ezr = (float) (ez - Litter.anchorZ);
        float eyr = (float) (ey - Litter.anchorY);
        float reach2 = reach * reach;
        float mn = 1F / speed;
        for (int cz = z0; cz <= z1; cz++) {
            for (int cx = x0; cx <= x1; cx++) {
                int chunk = Math.floorMod(cx, Litter.GRID) + Math.floorMod(cz, Litter.GRID) * Litter.GRID;
                int first = chunk * Litter.PER_CHUNK;
                for (int s = first; s < first + Litter.PER_CHUNK; s++) {
                    int o = s * 4;
                    if (row[1][o + 3] == 0F || (int) row[1][o] / 32 != 1 || Litter.rooted((int) row[1][o])) continue;
                    float dx = row[0][o] - exr, dz = row[0][o + 2] - ezr;
                    float d2 = dx * dx + dz * dz;
                    if (d2 > reach2 || Math.abs(row[0][o + 1] - eyr) > 0.7F) continue;
                    contact(promote(s, false), dx, dz, d2, reach, mx * mn, mz * mn, speed);
                }
                int sfirst = Litter.SOLID_BASE + chunk * Litter.SOLID_PER_CHUNK;
                for (int s = sfirst; s < sfirst + Litter.SOLID_PER_CHUNK; s++) {
                    int o = s * 4;
                    if (row[1][o + 3] == 0F || Litter.cap((int) row[1][o])) continue;
                    float dx = row[0][o] - exr, dz = row[0][o + 2] - ezr;
                    float d2 = dx * dx + dz * dz;
                    if (d2 > reach2 || Math.abs(row[0][o + 1] - eyr) > 0.7F) continue;
                    contact(promote(s, true), dx, dz, d2, reach, mx * mn, mz * mn, speed);
                }
            }
        }
        for (int i = 0; i < N; i++) {
            if (!LIVE[i]) continue;
            float dx = (float) (X[i] - ex), dz = (float) (Z[i] - ez);
            float d2 = dx * dx + dz * dz;
            if (d2 > reach2 || Math.abs(Y[i] - ey) > 0.7) continue;
            contact(i, dx, dz, d2, reach, mx * mn, mz * mn, speed);
        }
    }

    private static void contact(int i, float dx, float dz, float d2, float reach, float mx, float mz, float speed) {
        float d = Mth.sqrt(d2);
        float ax = d > 0.01F ? dx / d : mx, az = d > 0.01F ? dz / d : mz;
        float dirx = 0.75F * ax + 0.25F * mx, dirz = 0.75F * az + 0.25F * mz;
        float len = Mth.sqrt(dirx * dirx + dirz * dirz);
        if (len < 0.001F) return;
        dirx /= len;
        dirz /= len;
        boolean fresh = AGE[i] - HIT[i] > 10;
        HIT[i] = AGE[i];
        int type = TYPE[i];
        float near = 1F - d / reach;
        float target = speed * (type == LIGHT ? 0.6F : type == STICK ? 0.45F : 0.35F) * (0.5F + 0.5F * near) + 0.01F;
        float along = VX[i] * dirx + VZ[i] * dirz;
        if (along < target) {
            VX[i] += dirx * (target - along);
            VZ[i] += dirz * (target - along);
            clamp(i);
        }
        REST[i] = 0;
        if (!fresh) return;
        if (type == LIGHT && !AIR[i] && !WET[i] && speed > 0.08F) {
            VY[i] = 0.035F + Litter.level.random.nextFloat() * 0.03F;
            AIR[i] = true;
        }
        SPIN[i] += (Litter.level.random.nextFloat() - 0.5F) * (type == LIGHT ? 0.2F : 0.35F);
        if (target >= 0.05F) sound(i, Math.min(1F, speed * 4F));
    }

    private static void stirWind(LocalPlayer player, float wx, float wz, float force) {
        RandomSource random = Litter.level.random;
        int pcx = player.getBlockX() >> 4, pcz = player.getBlockZ() >> 4;
        float[][] row = Litter.ROW;
        int budget = 3;
        for (int n = 0; n < 8 && budget > 0; n++) {
            boolean solid = random.nextInt(5) == 0;
            int chunk = Math.floorMod(pcx + random.nextInt(3) - 1, Litter.GRID) + Math.floorMod(pcz + random.nextInt(3) - 1, Litter.GRID) * Litter.GRID;
            int s = solid ? Litter.SOLID_BASE + chunk * Litter.SOLID_PER_CHUNK + random.nextInt(Litter.SOLID_PER_CHUNK)
                    : chunk * Litter.PER_CHUNK + random.nextInt(Litter.PER_CHUNK);
            int o = s * 4;
            if (row[1][o + 3] == 0F) continue;
            int code = (int) row[1][o];
            if (solid ? Litter.cap(code) : code / 32 != 1 || Litter.rooted(code)) continue;
            float chance = solid ? (code <= 1 || code == 6 ? 0.08F : 0.03F) : 1F;
            if (random.nextFloat() > chance * force * 0.9F) continue;
            float dx = (float) (row[0][o] + Litter.anchorX - playerX), dz = (float) (row[0][o + 2] + Litter.anchorZ - playerZ);
            if (dx * dx + dz * dz > 400F) continue;
            int i = promote(s, solid);
            impulse(i, wx, wz, 0.05F + 0.1F * force, solid ? 0F : 0.03F + random.nextFloat() * 0.03F, 0F);
            budget--;
        }
    }

    private static void stirRain(LocalPlayer player, float wx, float wz, float rain, float windPower) {
        RandomSource random = Litter.level.random;
        if (random.nextFloat() > (rain - 0.4F) * 0.9F) return;
        int pcx = player.getBlockX() >> 4, pcz = player.getBlockZ() >> 4;
        int chunk = Math.floorMod(pcx + random.nextInt(3) - 1, Litter.GRID) + Math.floorMod(pcz + random.nextInt(3) - 1, Litter.GRID) * Litter.GRID;
        int s = chunk * Litter.PER_CHUNK + random.nextInt(Litter.PER_CHUNK);
        float[][] row = Litter.ROW;
        int o = s * 4;
        if (row[1][o + 3] == 0F || (int) row[1][o] / 32 != 1 || Litter.rooted((int) row[1][o])) return;
        double x = row[0][o] + Litter.anchorX, z = row[0][o + 2] + Litter.anchorZ;
        double dx = x - playerX, dz = z - playerZ;
        if (dx * dx + dz * dz > 576.0) return;
        P.set(Mth.floor(x), Mth.floor(row[0][o + 1] + Litter.anchorY + 0.5), Mth.floor(z));
        if (!Litter.level.canSeeSky(P)) return;
        int i = promote(s, false);
        VX[i] = wx * 0.01F * windPower;
        VZ[i] = wz * 0.01F * windPower;
    }

    private static void sampleRain(int i) {
        ClientLevel level = Litter.level;
        P.set(Mth.floor(X[i]), Mth.floor(Y[i] + 0.5), Mth.floor(Z[i]));
        EXPOSED[i] = level.canSeeSky(P) && level.getBiome(P).value().getPrecipitationAt(P) == Biome.Precipitation.RAIN;
        if (!EXPOSED[i]) return;
        float y = (float) Y[i];
        float px = surface(Mth.floor(X[i] + 0.7), Mth.floor(Z[i]), y, true);
        float mx = surface(Mth.floor(X[i] - 0.7), Mth.floor(Z[i]), y, true);
        float pz = surface(Mth.floor(X[i]), Mth.floor(Z[i] + 0.7), y, true);
        float mz = surface(Mth.floor(X[i]), Mth.floor(Z[i] - 0.7), y, true);
        SLOPE_X[i] = Float.isNaN(px) || Float.isNaN(mx) ? 0F : Mth.clamp(mx - px, -1F, 1F);
        SLOPE_Z[i] = Float.isNaN(pz) || Float.isNaN(mz) ? 0F : Mth.clamp(mz - pz, -1F, 1F);
    }

    private static void sound(int i, float loud) {
        if (!FxConfig.litterSounds) return;
        ClientLevel level = Litter.level;
        long tick = level.getGameTime();
        if (tick - COOL[i] < 16) return;
        if (tick - secondStart >= 20) {
            secondStart = tick;
            sounds = 0;
        }
        if (sounds >= 4) return;
        double dx = X[i] - playerX, dz = Z[i] - playerZ;
        if (dx * dx + dz * dz > 196.0) return;
        RandomSource random = level.random;
        SoundEvent event;
        float volume, pitch;
        switch (TYPE[i]) {
            case STICK -> {
                event = random.nextBoolean() ? SoundEvents.WOOD_STEP : SoundEvents.WOOD_HIT;
                volume = 0.08F + 0.08F * loud;
                pitch = 1.5F + random.nextFloat() * 0.4F;
            }
            case STONE -> {
                event = random.nextBoolean() ? SoundEvents.STONE_STEP : SoundEvents.GRAVEL_STEP;
                volume = 0.08F + 0.08F * loud;
                pitch = 1.55F + random.nextFloat() * 0.45F;
            }
            default -> {
                event = SoundEvents.AZALEA_LEAVES_STEP;
                volume = 0.05F + 0.06F * loud;
                pitch = 1.1F + random.nextFloat() * 0.3F;
            }
        }
        COOL[i] = (int) tick;
        sounds++;
        level.playLocalSound(X[i], Y[i], Z[i], event, SoundSource.AMBIENT, volume, pitch, false);
        Diagnostics.once("litter made a sound");
    }
}
