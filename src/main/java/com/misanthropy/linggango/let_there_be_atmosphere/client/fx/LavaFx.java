package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.particle.LavaParticle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
public final class LavaFx {
    private LavaFx() {}

    static boolean takesOver() {
        return FxConfig.lavaParticles && DynamicFogHandler.active() && !ColorGrade.shaderPackActive() && SkyProgram.NETHER_FX.ready();
    }

    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class LavaProviders {
        private LavaProviders() {}

        @SubscribeEvent
        public static void onRegisterLavaProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ParticleTypes.LAVA, sprites -> {
                LavaParticle.Provider vanilla = new LavaParticle.Provider(sprites);
                return (type, level, x, y, z, vx, vy, vz) -> {
                    if (!takesOver()) return vanilla.createParticle(type, level, x, y, z, vx, vy, vz);
                    NetherFx.lava(level, x, y, z);
                    return null;
                };
            });
            Diagnostics.once("lava particles are routed through our provider, vanilla pops are replaced while Lava Particles is on");
        }
    }
}
