package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;

@OnlyIn(Dist.CLIENT)
public final class WetPlayer {
    private WetPlayer() {}

    private static final int DRIP = 26;
    private static final int CAP = 16;
    private static final float MAX = 8F;
    private static final float DT = 0.05F;
    private static final double REACH = 24.0 * 24.0;
    private static final float FALL = 3F;
    private static final int[] ID = new int[CAP];
    private static final float[] WET = new float[CAP];
    private static final float[] OWED = new float[CAP];
    private static final boolean[] IN_WATER = new boolean[CAP];
    private static final boolean[] SEEN = new boolean[CAP];
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();

    private static ClientLevel lastLevel;
    private static float own;
    private static int dripped;

    static {
        Arrays.fill(ID, -1);
    }

    public static float soak() {
        return FxConfig.wetPlayer && DynamicFogHandler.active() ? Mth.clamp(own / 6F, 0F, 1F) : 0F;
    }

    public static void onWetPlayerTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer self = mc.player;
        if (level == null || self == null || !FxConfig.wetPlayer || !DynamicFogHandler.active()) {
            if (lastLevel != null || own > 0F) reset();
            Diagnostics.skip(Diagnostics.WET_PLAYER, FxConfig.wetPlayer ? "not in a world, or the master switch is off" : "disabled");
            return;
        }
        if (level != lastLevel) {
            reset();
            lastLevel = level;
        }
        if (mc.isPaused()) return;
        boolean first = mc.options.getCameraType().isFirstPerson();
        boolean ready = CaveMotes.resolve();
        Arrays.fill(SEEN, false);
        dripped = 0;
        float peak = 0F;
        List<AbstractClientPlayer> players = level.players();
        for (int i = 0; i < players.size(); i++) {
            AbstractClientPlayer p = players.get(i);
            boolean mine = p == self;
            if (!mine && p.distanceToSqr(self) > REACH) continue;
            int slot = slot(p.getId());
            if (slot < 0) continue;
            SEEN[slot] = true;
            boolean water = p.isInWater();
            if (water) {
                WET[slot] = Math.min(MAX, WET[slot] + (p.isUnderWater() ? 2F : 1F) * DT);
            } else {
                WET[slot] = Math.max(0F, WET[slot] - DT);
                if (IN_WATER[slot] && WET[slot] > 0.3F) OWED[slot] = Math.min(OWED[slot] + 12F + 3F * WET[slot], 36F);
            }
            IN_WATER[slot] = water;
            if (mine) own = WET[slot];
            peak = Math.max(peak, WET[slot]);
            if (water || WET[slot] <= 0F || !ready || (mine && first) || p.isSpectator() || p.isInvisible()) {
                OWED[slot] = 0F;
                continue;
            }
            float k = WET[slot] / MAX;
            OWED[slot] = Math.min(OWED[slot] + (55F * k * k + 6F * k) * DT, 36F);
            drip(level, p, slot);
        }
        for (int i = 0; i < CAP; i++) if (ID[i] >= 0 && !SEEN[i]) ID[i] = -1;
        if (peak <= 0F) {
            Diagnostics.skip(Diagnostics.WET_PLAYER, "nobody is dripping");
            return;
        }
        Diagnostics.mark(Diagnostics.WET_PLAYER, Diagnostics.detail() ? String.format("wet %.1fs, %d drips", peak, dripped) : null);
    }

    private static void drip(ClientLevel level, AbstractClientPlayer p, int slot) {
        AABB box = p.getBoundingBox();
        double height = box.maxY - box.minY;
        int n = Math.min((int) OWED[slot], 9);
        OWED[slot] -= (int) OWED[slot];
        for (int i = 0; i < n; i++) {
            double x = Mth.lerp(RANDOM.nextDouble(), box.minX, box.maxX);
            double z = Mth.lerp(RANDOM.nextDouble(), box.minZ, box.maxZ);
            double y = box.minY + height * (0.15 + 0.85 * Math.pow(RANDOM.nextDouble(), 0.7));
            CURSOR.set(x, y, z);
            float life = Math.max(0.12F, Mth.sqrt((float) (y - box.minY) / FALL));
            CaveMotes.emit(DRIP, x, y, z, level.getBrightness(LightLayer.BLOCK, CURSOR), level.getBrightness(LightLayer.SKY, CURSOR), life, RANDOM.nextFloat());
            dripped++;
        }
    }

    private static int slot(int id) {
        int free = -1;
        for (int i = 0; i < CAP; i++) {
            if (ID[i] == id) return i;
            if (ID[i] < 0 && free < 0) free = i;
        }
        if (free >= 0) {
            ID[free] = id;
            WET[free] = 0F;
            OWED[free] = 0F;
            IN_WATER[free] = false;
        }
        return free;
    }

    private static void reset() {
        lastLevel = null;
        own = 0F;
        Arrays.fill(ID, -1);
        Arrays.fill(WET, 0F);
        Arrays.fill(OWED, 0F);
    }
}
