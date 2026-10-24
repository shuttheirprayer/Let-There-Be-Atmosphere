package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WindParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Particle.class)
public abstract class ParticleWindMixin {
    @Unique private long linggango$stamp = Long.MIN_VALUE;
    @Unique private long linggango$recheck;
    @Unique private float linggango$gain;

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, index = 1, require = 0)
    private double linggango$pushX(double dx) {
        return WindParticles.active ? dx + WindParticles.pushX * linggango$gain() : dx;
    }

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, index = 5, require = 0)
    private double linggango$pushZ(double dz) {
        return WindParticles.active ? dz + WindParticles.pushZ * linggango$gain() : dz;
    }

    @Unique
    private float linggango$gain() {
        long now = WindParticles.tick;
        if (now == linggango$stamp) return linggango$gain;
        linggango$stamp = now;
        if (now >= linggango$recheck) {
            Particle self = (Particle) (Object) this;
            AABB box = self.getBoundingBox();
            float response = WindParticles.response(self.getClass(), Minecraft.getInstance().level,
                    (box.minX + box.maxX) * 0.5, box.minY, (box.minZ + box.maxZ) * 0.5);
            if (response < 0F) {
                linggango$recheck = now + 1;
            } else {
                linggango$gain = response;
                linggango$recheck = now + 6 + (int) (System.identityHashCode(this) & 3L);
                Diagnostics.once("vanilla particles are being pushed by the wind");
            }
        }
        return linggango$gain;
    }
}
