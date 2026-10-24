package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class EndDust {
    private EndDust() {}

    static final int KIND = 28;
    private static final double RADIUS = 13.0;
    private static final int[] DROPS = {5, 12, 24, 40};
    private static final int CHORUS_REACH = 24;
    private static final int CHORUS_SPAN = CHORUS_REACH * 2 + 1;
    private static final int CHORUS_SLOTS = 24;
    private static final long[] CHORUS = new long[CHORUS_SLOTS];
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos PROBE = new BlockPos.MutableBlockPos();
    private static float budget;
    private static int sweep;
    private static int chorusCount;

    public static void onEndDustTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) return;
        if (!FxConfig.endDust || FxConfig.endDustAmount <= 0.0 || !DynamicFogHandler.active()
                || level.effects().skyType() != DimensionSpecialEffects.SkyType.END) {
            chorusCount = 0;
            Diagnostics.skip(Diagnostics.END_DUST, "disabled or not the End");
            return;
        }
        if (CameraFluid.of(mc.gameRenderer.getMainCamera()) != FogType.NONE || !CaveMotes.resolve()) return;
        RandomSource random = level.random;
        budget += 3.2F * (float) FxConfig.endDustAmount;
        int spawned = 0;
        for (int tries = 0; budget >= 1F && tries < 24; tries++) {
            double x = player.getX() + (random.nextDouble() * 2.0 - 1.0) * RADIUS;
            double y = player.getEyeY() + (random.nextDouble() * 2.0 - 1.0) * RADIUS * 0.6;
            double z = player.getZ() + (random.nextDouble() * 2.0 - 1.0) * RADIUS;
            CURSOR.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
            if (!level.getBlockState(CURSOR).isAir()) continue;
            if (!voidBelow(level, CURSOR) && random.nextFloat() > 0.3F) continue;
            budget -= 1F;
            CaveMotes.emit(KIND, x, y, z, 15, 15, 6F + random.nextFloat() * 4F, random.nextFloat());
            spawned++;
        }
        budget = Math.min(budget, 4F);
        spawned += petals(level, player, random);
        Diagnostics.mark(Diagnostics.END_DUST, Diagnostics.detail() ? "+" + spawned + ", chorus " + chorusCount : null);
    }

    private static boolean voidBelow(ClientLevel level, BlockPos from) {
        if (from.getY() < level.getMinBuildHeight() + 40) return true;
        for (int drop : DROPS) {
            PROBE.set(from.getX(), from.getY() - drop, from.getZ());
            if (PROBE.getY() < level.getMinBuildHeight()) return true;
            if (!level.getBlockState(PROBE).isAir()) return false;
        }
        return true;
    }

    private static boolean chorus(BlockState state) {
        return state.is(Blocks.CHORUS_PLANT) || state.is(Blocks.CHORUS_FLOWER);
    }

    private static int petals(ClientLevel level, LocalPlayer player, RandomSource random) {
        int px = Mth.floor(player.getX()), pz = Mth.floor(player.getZ());
        int top = Mth.floor(player.getEyeY()) + 14, bottom = Math.max(level.getMinBuildHeight(), top - 36);
        for (int i = 0; i < 4; i++) {
            sweep = (sweep + 1) % (CHORUS_SPAN * CHORUS_SPAN);
            int x = px - CHORUS_REACH + sweep % CHORUS_SPAN, z = pz - CHORUS_REACH + sweep / CHORUS_SPAN;
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            for (int y = top; y >= bottom; y--) {
                if (!chorus(level.getBlockState(CURSOR.set(x, y, z)))) continue;
                long key = CURSOR.asLong();
                boolean known = false;
                for (int k = 0; k < chorusCount && !known; k++) known = CHORUS[k] == key;
                if (!known) CHORUS[chorusCount < CHORUS_SLOTS ? chorusCount++ : random.nextInt(CHORUS_SLOTS)] = key;
                break;
            }
        }
        if (chorusCount == 0 || random.nextFloat() > 0.4F * (float) Math.min(FxConfig.endDustAmount, 2.0)) return 0;
        int pick = random.nextInt(chorusCount);
        BlockPos at = BlockPos.of(CHORUS[pick]);
        if (!chorus(level.getBlockState(at))) {
            CHORUS[pick] = CHORUS[--chorusCount];
            return 0;
        }
        if (at.distSqr(player.blockPosition()) > 26.0 * 26.0) return 0;
        CaveMotes.emit(KIND, at.getX() + 0.5 + (random.nextDouble() - 0.5) * 2.2, at.getY() + 0.3 + random.nextDouble() * 1.6,
                at.getZ() + 0.5 + (random.nextDouble() - 0.5) * 2.2, 15, 15, 5F + random.nextFloat() * 3F, 1F + random.nextFloat());
        return 1;
    }
}
