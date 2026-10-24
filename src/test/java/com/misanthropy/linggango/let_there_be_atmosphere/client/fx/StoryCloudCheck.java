package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.lang.reflect.Method;

public final class StoryCloudCheck {
    private static final int WORST_CASE_BUDGET_MS = 40;

    public static void main(String[] args) throws Exception {
        checkField();
        long worst = timeRebuild();
        System.out.println("Story clouds OK, worst-case rebuild " + worst + " ms");
        if (worst > WORST_CASE_BUDGET_MS) {
            throw new AssertionError("a rebuild costs " + worst + " ms, over the " + WORST_CASE_BUDGET_MS + " ms budget");
        }
    }

    private static void checkField() throws Exception {
        anchorAt(0, 0);
        Method occupancy = StoryClouds.class.getDeclaredMethod(
                "occupancy", int.class, int.class, float.class, float.class);
        occupancy.setAccessible(true);

        StoryCloudConfig.widthScale = 1.0;
        StoryCloudConfig.lengthScale = 1.0;

        float[] a = (float[]) occupancy.invoke(null, 49, 24, 0.5F, 0F);
        float[] b = (float[]) occupancy.invoke(null, 49, 24, 0.5F, 0F);
        require(a.length == 49 * 49, "the grid covers the span");
        for (int i = 0; i < a.length; i++) require(a[i] == b[i], "the field is deterministic");

        float[] empty = (float[]) occupancy.invoke(null, 49, 24, 0.0F, 0F);
        require(count(empty) == 0, "zero coverage leaves a clear sky");

        float[] full = (float[]) occupancy.invoke(null, 49, 24, 1.0F, 0F);
        require(count(full) == full.length, "full coverage fills the sky");

        int half = count((float[]) occupancy.invoke(null, 99, 49, 0.5F, 0F));
        double share = half / (double) (99 * 99);
        require(share > 0.3 && share < 0.7, "half coverage fills roughly half the sky, got " + share);

        int sparse = count((float[]) occupancy.invoke(null, 99, 49, 0.2F, 0F));
        int dense = count((float[]) occupancy.invoke(null, 99, 49, 0.8F, 0F));
        require(sparse < half && half < dense, "coverage is monotonic");
    }

    private static long timeRebuild() throws Exception {
        anchorAt(0, 0);
        Method occupancy = StoryClouds.class.getDeclaredMethod(
                "occupancy", int.class, int.class, float.class, float.class);
        occupancy.setAccessible(true);
        Method emitLayer = StoryClouds.class.getDeclaredMethod(
                "emitLayer", BufferBuilder.class, float[].class, int.class, int.class,
                float.class, float.class, float.class);
        emitLayer.setAccessible(true);

        StoryCloudConfig.enabled = true;
        StoryCloudConfig.coverage = 0.95;
        StoryCloudConfig.secondCoverage = 0.95;
        StoryCloudConfig.secondLayer = true;
        StoryCloudConfig.thickness = 14.0;
        StoryCloudConfig.alpha = 0.85;

        int radius = 24;
        int span = radius * 2 + 1;
        float cell = 24F;
        float reach = 4096F;

        BufferBuilder builder = new BufferBuilder(4 * 1024 * 1024);
        long worst = 0;

        for (int run = 0; run < 12; run++) {
            long started = System.nanoTime();
            float[] primary = (float[]) occupancy.invoke(null, span, radius, 0.95F, 0F);
            float[] second = (float[]) occupancy.invoke(null, span, radius, 0.95F, 311F);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            emitLayer.invoke(null, builder, primary, span, radius, cell, 0F, reach);
            emitLayer.invoke(null, builder, second, span, radius, cell, 28F, reach);
            builder.end();
            long took = (System.nanoTime() - started) / 1_000_000L;
            if (run >= 2) worst = Math.max(worst, took);
        }

        builder.discard();
        StoryCloudConfig.read(new java.util.Properties());
        return worst;
    }

    private static void anchorAt(int x, int z) throws Exception {
        java.lang.reflect.Field anchorX = StoryClouds.class.getDeclaredField("anchorX");
        java.lang.reflect.Field anchorZ = StoryClouds.class.getDeclaredField("anchorZ");
        anchorX.setAccessible(true);
        anchorZ.setAccessible(true);
        anchorX.set(null, x);
        anchorZ.set(null, z);
    }

    private static int count(float[] grid) {
        int n = 0;
        for (float cell : grid) if (cell > 0F) n++;
        return n;
    }

    private static void require(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
