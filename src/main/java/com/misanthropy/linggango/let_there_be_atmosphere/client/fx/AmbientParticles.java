package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.config.AtmosphereConfigManager;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class AmbientParticles {
    private AmbientParticles() {}

    private static final Map<String, SimpleParticleType> RESOLVED = new ConcurrentHashMap<>();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final String POLLEN = "minecraft:spore_blossom_air";

    private static float windX = 1F;
    private static float windZ = 0F;
    private static float windForce = 0.5F;

    public static float windX() { return windX; }
    public static float windZ() { return windZ; }
    public static float windForce() { return windForce; }

    public static void clearCache() { RESOLVED.clear(); }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) return;

        updateWind(level.getGameTime());

        if (!DynamicFogHandler.running() || !FxConfig.mistEnabled) {
            Diagnostics.skip(Diagnostics.MIST, "disabled");
            return;
        }
        if (AtmosphereConfigManager.ATMOSPHERES.isEmpty()) {
            Diagnostics.skip(Diagnostics.MIST, "no biome in biomes.json asks for particles");
            return;
        }
        if (CameraFluid.of(mc.gameRenderer.getMainCamera()) != FogType.NONE) return;
        if (mc.isPaused()) return;

        int budget = Mth.clamp((int) Math.round(FxConfig.mistPerTick), 0, 32);
        if (budget <= 0 || FxConfig.mistRate <= 0.0) return;

        float radius = (float) FxConfig.mistRadius;
        RandomSource random = level.random;
        int spawned = 0;

        for (int attempt = 0; attempt < budget * 3 && spawned < budget; attempt++) {
            double x = player.getX() + (random.nextDouble() * 2.0 - 1.0) * radius;
            double y = player.getEyeY() + (random.nextDouble() * 2.0 - 1.0) * radius * 0.5;
            double z = player.getZ() + (random.nextDouble() * 2.0 - 1.0) * radius;

            CURSOR.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
            if (!level.getBlockState(CURSOR).isAir()) continue;

            if (!(level.getBiome(CURSOR) instanceof Holder.Reference<Biome> biome)) continue;
            AtmosphereConfigManager.AtmosphereSettings settings =
                    AtmosphereConfigManager.ATMOSPHERES.get(biome.key().location());
            if (settings == null) continue;

            boolean pollen = POLLEN.equals(settings.mistParticle == null ? null : settings.mistParticle.trim());
            SimpleParticleType type = pollen ? null : resolve(settings.mistParticle);
            if (type == null && !pollen) continue;

            float chance = Mth.clamp(settings.mistRate * (float) FxConfig.mistRate, 0F, 4F) * 0.5F;
            if (random.nextFloat() >= chance) continue;

            if (pollen) {
                CaveMotes.emit(CaveMotes.POLLEN, x, y, z, level.getBrightness(LightLayer.BLOCK, CURSOR),
                        level.getBrightness(LightLayer.SKY, CURSOR), 6F + random.nextFloat() * 5F, random.nextFloat());
                spawned++;
                continue;
            }

            float strength = Math.min(windForce * Wind.altitude(y) * (float) FxConfig.windStrength, 1.2F);
            float drift = strength * strength * 0.1F;
            level.addParticle(type, x, y, z,
                    windX * drift + (random.nextFloat() - 0.5F) * 0.005F,
                    (random.nextFloat() - 0.35F) * 0.004F,
                    windZ * drift + (random.nextFloat() - 0.5F) * 0.005F);
            spawned++;
        }
        if (spawned > 0) Diagnostics.mark(Diagnostics.MIST, spawned + " spawned this tick");
        else Diagnostics.skip(Diagnostics.MIST, "this biome asks for none");
    }

    private static void updateWind(long gameTime) {
        float t = gameTime * 0.05F;
        windX = Wind.x(t);
        windZ = Wind.z(t);
        windForce = Wind.level(t);
    }

    private static SimpleParticleType resolve(String id) {
        if (id == null) return null;
        String trimmed = id.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("none")) return null;
        SimpleParticleType cached = RESOLVED.get(trimmed);
        if (cached != null) return cached;

        ResourceLocation location = ResourceLocation.tryParse(trimmed);
        if (location == null) return null;
        ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(location);
        if (!(type instanceof SimpleParticleType simple)) return null;
        RESOLVED.put(trimmed, simple);
        return simple;
    }
}
