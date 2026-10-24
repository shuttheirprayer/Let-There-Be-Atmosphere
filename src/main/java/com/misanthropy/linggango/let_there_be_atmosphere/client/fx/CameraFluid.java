package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Camera;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

public final class CameraFluid {
    private CameraFluid() {}

    private static Camera camera;
    private static Vec3 position;
    private static boolean initialized;
    private static FogType fluid = FogType.NONE;

    public static FogType of(Camera view) {
        if (stale(view)) fluid = view.getFluidInCamera();
        return fluid;
    }

    public static FogType of(Camera view, Operation<FogType> lookup) {
        if (stale(view)) fluid = lookup.call(view);
        return fluid;
    }

    private static boolean stale(Camera view) {
        Vec3 at = view.getPosition();
        boolean ready = view.isInitialized();
        if (view == camera && at == position && ready == initialized) return false;
        camera = view;
        position = at;
        initialized = ready;
        return true;
    }
}
