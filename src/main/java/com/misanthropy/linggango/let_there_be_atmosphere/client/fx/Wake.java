package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class Wake {
    private Wake() {}

    private static final double RANGE = 32.0;
    private static final int BUDGET = 24;
    private static final double STEP = 0.125;
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final Int2ObjectOpenHashMap<State> STATES = new Int2ObjectOpenHashMap<>();

    private static final class State {
        int wet;
        int entered = -100;
    }

    private static int clock;

    private static boolean on() {
        return FxConfig.waterWake && FxConfig.waterWakeStrength > 0.0;
    }

    public static void splash(double x, double surfaceY, double z, float width, float power) {
        if (!WaveField.ready()) return;
        float p = Math.min(Math.max(power, 0F), 1F);
        float amp = Math.min(0.8F + 0.9F * p, 1.9F);
        WaveField.impulse(x, surfaceY, z, 0.22F + 0.12F * Math.min(Math.max(width, 0.4F), 1.4F), amp, false);
        WaveSplash.emit(x, surfaceY, z, width, p);
    }

    public static void onWakeTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || !on() || !DynamicFogHandler.active() || ColorGrade.shaderPackActive()) {
            STATES.clear();
            Diagnostics.skip(Diagnostics.WAKE, "disabled");
            return;
        }
        if (mc.isPaused() || !WaveField.ready()) return;
        clock++;
        if (clock % 200 == 0) STATES.values().removeIf(s -> clock - s.wet > 100);
        float strength = (float) FxConfig.waterWakeStrength;
        AABB box = player.getBoundingBox().inflate(RANGE, 8.0, RANGE);
        int budget = BUDGET;
        for (Entity entity : level.getEntities((Entity) null, box,
                e -> (e instanceof Boat || (e instanceof LivingEntity || e instanceof ItemEntity) && e.isInWater()) && !e.isPassenger() && !e.isSpectator())) {
            State state = STATES.get(entity.getId());
            if (state == null) {
                if (STATES.size() > 256) STATES.clear();
                state = new State();
                STATES.put(entity.getId(), state);
            }
            boolean fresh = clock - state.wet > 5;
            state.wet = clock;
            if (budget <= 0) continue;
            float surface = surface(level, entity);
            if (Float.isNaN(surface) || entity.getY() > surface + 0.08 || entity.getY() + entity.getBbHeight() < surface - 0.05) continue;
            double x = entity.getX(), z = entity.getZ();
            double dx = x - entity.xo, dz = z - entity.zo;
            double speed = Math.sqrt(dx * dx + dz * dz);
            float width = Math.min(Math.max(entity.getBbWidth(), 0.4F), 1.4F);

            if (fresh) {
                state.entered = clock;
                double drop = Math.max(entity.yo - entity.getY(), 0.0);
                float power = (float) Math.min(Math.max(drop * 2.2, speed * 1.5), 1.0);
                if (power > 0.08F) {
                    float amp = Math.min((0.7F + 1.0F * power) * strength, 1.9F);
                    WaveField.impulse(x, surface, z, 0.22F + 0.12F * width, amp, false);
                    if (power > 0.4F) WaveSplash.emit(x, surface, z, width, power);
                    budget--;
                    continue;
                }
            }
            if (clock - state.entered < 4) continue;
            if (speed > 4.0) continue;

            if (entity instanceof Boat boat) {
                if (speed >= 0.03) boatWake(boat, x, z, dx, dz, speed, surface, strength);
                budget--;
            } else if (speed >= 0.03) {
                trail(x, z, dx, dz, speed, surface, 0.2F + 0.1F * width, Math.min((0.1F + 0.55F * (float) speed) * 0.6F * strength, 0.35F * Math.max(strength, 1F)));
                budget--;
            } else if (entity instanceof LivingEntity && (entity.tickCount + entity.getId()) % 40 == 0) {
                WaveField.impulse(x, surface, z, 0.26F, 0.8F * Math.min(strength, 1.2F), false);
                budget--;
            }
        }
        Diagnostics.mark(Diagnostics.WAKE, Diagnostics.detail() ? "wake driver" : null);
    }

    private static void trail(double x, double z, double dx, double dz, double speed, float surface, float radius, float perTick) {
        int n = Math.max(1, (int) Math.ceil(speed / Math.max(STEP, radius * 0.6)));
        float amp = perTick / n;
        for (int k = 1; k <= n; k++) {
            double f = (double) k / n;
            WaveField.impulse(x - dx * (1.0 - f), surface, z - dz * (1.0 - f), radius, amp, true);
        }
    }

    private static void boatWake(Boat boat, double x, double z, double dx, double dz, double speed, float surface, float strength) {
        double dirX = dx / speed, dirZ = dz / speed;
        double nx = -dirZ, nz = dirX;
        double half = boat.getBbWidth() * 0.32;
        float wake = 0.16F * (float) Math.min(Math.max(speed / 0.3, 0.25), 1.6) * strength * (float) Math.max(1.0, speed / 0.3);
        trail(x, z, dx, dz, speed, surface, 0.33F, wake);
        trail(x + nx * half, z + nz * half, dx, dz, speed, surface, 0.33F, wake);
        trail(x - nx * half, z - nz * half, dx, dz, speed, surface, 0.33F, wake);
        for (int i = 0; i < 2; i++) {
            if (!boat.getPaddleState(i)) continue;
            double phase = boat.getRowingTime(i, 1F) % (Math.PI * 2.0);
            if (phase < 0.19634955 || phase > 1.1780972) continue;
            net.minecraft.world.phys.Vec3 rot = boat.getViewVector(1F);
            double px = x + (i == 1 ? -rot.z : rot.z), pz = z + (i == 1 ? rot.x : -rot.x);
            WaveField.impulse(px, surface, pz, 0.25F, 0.5F * strength, false);
            if (clock % 4 == 0) WaveSplash.emit(px, surface, pz, 0.3F, 0.1F);
        }
    }

    private static float surface(ClientLevel level, Entity entity) {
        CURSOR.set(entity.getX(), entity.getY() + 0.6, entity.getZ());
        for (int i = 0; i < 3; i++) {
            FluidState fluid = level.getFluidState(CURSOR);
            if (fluid.is(FluidTags.WATER)) {
                if (!level.getFluidState(CURSOR.above()).is(FluidTags.WATER)) {
                    return CURSOR.getY() + fluid.getHeight(level, CURSOR);
                }
                CURSOR.move(0, 1, 0);
            } else {
                CURSOR.move(0, -1, 0);
            }
        }
        return Float.NaN;
    }
}
