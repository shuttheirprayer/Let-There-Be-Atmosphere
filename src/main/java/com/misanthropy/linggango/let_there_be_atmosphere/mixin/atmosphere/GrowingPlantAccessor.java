package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.GrowingPlantBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GrowingPlantBlock.class)
public interface GrowingPlantAccessor {
    @Accessor("growthDirection")
    Direction linggango$growthDirection();
}
