package com.misanthropy.linggango.let_there_be_atmosphere.mixin.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.SkyHeights;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelHeightsMixin {
    @Inject(method = "setBlocksDirty", at = @At("HEAD"))
    private void linggango$heightsChanged(BlockPos pos, BlockState old, BlockState now, CallbackInfo ci) {
        SkyHeights.changed((ClientLevel) (Object) this, pos);
    }
}
