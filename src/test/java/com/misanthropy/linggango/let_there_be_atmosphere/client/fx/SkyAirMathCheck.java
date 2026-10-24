package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.config.ConfigText;

public final class SkyAirMathCheck {
    public static void main(String[] args) throws Exception {
        checkComets();
        checkHazeBand();
        checkFogDepth();
        checkNoise();
        checkConfigRoundTrip();
        System.out.println("Sky and air math OK");
    }

    private static void checkComets() {
        Comets.reset();
        require(Comets.liveCount() == 0, "pool starts empty");

        for (int i = 0; i < 4000 && Comets.liveCount() == 0; i++) {
            Comets.tick(true, 0.05, 4, 1.0, 1.0, 62.0, Comets.PALETTE_CUSTOM, 0.0, 7);
        }
        require(Comets.liveCount() > 0, "a comet eventually spawns");

        for (int i = 0; i < 3000; i++) Comets.tick(true, 1.0, 3, 1.0, 1.0, 62.0, Comets.PALETTE_MIXED, 0.0, 7);
        require(Comets.liveCount() <= 3, "cap is respected");

        int index = -1;
        for (int i = 0; i < Comets.CAPACITY; i++) if (Comets.alive(i)) { index = i; break; }
        require(index >= 0, "a live comet is findable");

        float[] head = new float[3];
        float[] tail = new float[3];
        float[] side = new float[3];
        Comets.position(index, 0F, 0.5F, head);
        Comets.position(index, 1F, 0.5F, tail);
        require(Math.abs(length(head) - 1F) < 1.0E-3F, "head stays on the unit sphere");
        require(Math.abs(length(tail) - 1F) < 1.0E-3F, "tail stays on the unit sphere");
        require(dot(head, tail) < 0.99999F, "the tail trails behind the head");

        Comets.ribbon(index, side);
        require(Math.abs(length(side) - 1F) < 1.0E-3F, "the offset direction is normalised");
        require(Math.abs(dot(side, head)) < 1.0E-3F, "the offset is perpendicular to the streak");
        require(Math.abs(dot(side, tail)) < 1.0E-3F, "the offset stays perpendicular along the streak");
        require(quadArea(head, tail, side, Comets.width(index) * 100F) > 0.05F,
                "the ribbon must be a surface, not a line");

        float previous = Float.MAX_VALUE;
        for (int step = 0; step <= 10; step++) {
            float u = step / 10F;
            float alpha = Comets.alpha(index, u, 0F);
            require(alpha >= 0F && alpha <= 1F, "alpha stays in range");
            require(alpha <= previous + 1.0E-5F, "alpha never grows toward the tail");
            previous = alpha;
        }
        require(Comets.alpha(index, 1F, 0F) < 1.0E-5F, "the tail end is transparent");
        Comets.reset();
    }

    private static void checkHazeBand() {
        float bottom = -20F, middle = 4F, top = 56F;
        require(HazeBand.alpha(bottom - 1F, bottom, middle, top) == 0F, "nothing below the band");
        require(HazeBand.alpha(top + 1F, bottom, middle, top) == 0F, "nothing above the band");
        require(HazeBand.alpha(bottom, bottom, middle, top) == 0F, "the bottom edge is empty");
        require(HazeBand.alpha(top, bottom, middle, top) == 0F, "the top edge is empty");
        require(HazeBand.alpha(middle, bottom, middle, top) > 0.99F, "the middle is the peak");

        float previous = 0F;
        for (int i = 1; i < 24; i++) {
            float y = bottom + (middle - bottom) * i / 24F;
            float a = HazeBand.alpha(y, bottom, middle, top);
            require(a >= previous - 1.0E-5F, "the lower half rises");
            require(a >= 0F && a <= 1F, "alpha stays in range");
            previous = a;
        }
        require(HazeBand.alpha(10F, 20F, 30F, 10F) == 0F, "an inverted window draws nothing");
    }

    private static void checkFogDepth() {
        float[] out = new float[2];

        FogDepth.spread(40F, 200F, 0F, 0.5F, 0.5F, 1F, 1F, 1F, 1F, out);
        require(Math.abs(out[0] - 40F) < 1.0E-3F, "day leaves the near plane alone");
        require(Math.abs(out[1] - 200F) < 1.0E-3F, "day leaves the far plane alone");

        FogDepth.spread(40F, 200F, 1F, 0.5F, 0.5F, 1F, 1F, 1F, 1F, out);
        require(Math.abs(out[1] - 100F) < 1.0E-3F, "full night applies the multiplier");

        FogDepth.spread(40F, 200F, 0.5F, 0.5F, 0.5F, 1F, 1F, 1F, 1F, out);
        require(out[1] > 100F && out[1] < 200F, "dusk sits between the two");

        FogDepth.spread(40F, 200F, 0F, 1F, 1F, 2F, 1F, 1F, 1F, out);
        require(Math.abs(out[1] - 100F) < 1.0E-3F, "density pulls the far plane in");

        FogDepth.spread(40F, 200F, 0F, 1F, 1F, 1F, 0F, 0.25F, 1F, out);
        require(Math.abs(out[1] - 50F) < 1.0E-3F, "a sealed cave closes the fog in");

        FogDepth.spread(40F, 200F, 0F, 1F, 1F, 1F, 1F, 0.25F, 1F, out);
        require(Math.abs(out[1] - 200F) < 1.0E-3F, "open sky ignores the cave scale");

        FogDepth.spread(40F, 200F, 0F, 0.01F, 0.01F, 5F, 0F, 0.1F, 24F, out);
        require(out[1] >= 24F, "the minimum end is honoured");
        require(out[0] <= out[1], "the near plane never passes the far plane");
    }

    private static void checkNoise() {
        ValueNoise a = new ValueNoise(1234L);
        ValueNoise b = new ValueNoise(1234L);
        float min = 1F, max = 0F;
        for (int i = 0; i < 2000; i++) {
            float x = i * 0.37F;
            float y = i * 0.11F;
            float va = a.at(x, y);
            require(Math.abs(va - b.at(x, y)) < 1.0E-6F, "the same seed gives the same field");
            require(va >= 0F && va <= 1F, "2D noise stays in range");
            require(a.at(x) >= 0F && a.at(x) <= 1F, "1D noise stays in range");
            min = Math.min(min, va);
            max = Math.max(max, va);
        }
        require(max - min > 0.3F, "the field actually varies");
    }

    private static void checkConfigRoundTrip() throws Exception {
        FxConfig.cometsEnabled = false;
        FxConfig.cometRate = 0.077;
        FxConfig.cometMax = 9;
        FxConfig.cometSpeed = 2.5;
        FxConfig.cometTail = 3.25;
        FxConfig.cometBrightness = 1.75;
        FxConfig.cometHex = 0x123456;
        FxConfig.sunScale = 2.5;
        FxConfig.moonScale = 0.5;
        FxConfig.cloudNightDarken = 0.8;
        FxConfig.cloudFogBlend = 0.6;
        FxConfig.hazeBandEnabled = false;
        FxConfig.hazeBandStrength = 1.25;
        FxConfig.hazeBandBottom = -33.0;
        FxConfig.hazeBandMiddle = 12.0;
        FxConfig.hazeBandTop = 77.0;
        FxConfig.hazeBandRadius = 333.0;
        FxConfig.mistEnabled = false;
        FxConfig.mistRate = 2.5;
        FxConfig.mistRadius = 33.0;
        FxConfig.mistPerTick = 7;
        FxConfig.windStrength = 3.5;
        FxConfig.biomeOverlay = true;
        FxConfig.sunsetFogStrength = 1.25;
        FxConfig.weatherTintStrength = 0.33;
        FxConfig.blockLightHex = 0xABCDEF;

        StoryCloudConfig.enabled = true;
        StoryCloudConfig.cellSize = 48.0;
        StoryCloudConfig.thickness = 30.0;
        StoryCloudConfig.heightOffset = 17.0;
        StoryCloudConfig.coverage = 0.75;
        StoryCloudConfig.speed = 4.5;
        StoryCloudConfig.widthScale = 2.5;
        StoryCloudConfig.lengthScale = 0.5;
        StoryCloudConfig.alpha = 0.6;
        StoryCloudConfig.bottomFade = 0.5;
        StoryCloudConfig.topFade = 0.35;
        StoryCloudConfig.secondLayer = true;
        StoryCloudConfig.secondOffset = 44.0;
        StoryCloudConfig.secondCoverage = 0.6;

        java.util.Map<String, String> before = snapshot();

        ConfigText text = new ConfigText();
        FxConfig.write(text);
        StoryCloudConfig.write(text);

        java.util.Properties properties = new java.util.Properties();
        properties.load(new java.io.StringReader(text.toString()));

        FxConfig.read(new java.util.Properties());
        StoryCloudConfig.read(new java.util.Properties());
        require(!snapshot().equals(before), "defaults differ from the tuned values");

        FxConfig.read(properties);
        StoryCloudConfig.read(properties);
        java.util.Map<String, String> after = snapshot();

        for (java.util.Map.Entry<String, String> entry : before.entrySet()) {
            String now = after.get(entry.getKey());
            require(entry.getValue().equals(now),
                    entry.getKey() + " survives a write and read, was " + entry.getValue() + " now " + now);
        }

        FxConfig.read(new java.util.Properties());
        StoryCloudConfig.read(new java.util.Properties());
    }

    private static java.util.Map<String, String> snapshot() {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        for (Class<?> type : new Class<?>[] {FxConfig.class, StoryCloudConfig.class}) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!java.lang.reflect.Modifier.isPublic(modifiers)) continue;
                if (!java.lang.reflect.Modifier.isStatic(modifiers)) continue;
                if (java.lang.reflect.Modifier.isFinal(modifiers)) continue;
                try {
                    values.put(type.getSimpleName() + "." + field.getName(), String.valueOf(field.get(null)));
                } catch (IllegalAccessException e) {
                    throw new AssertionError("cannot read " + field.getName());
                }
            }
        }
        return values;
    }

    private static float length(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float quadArea(float[] head, float[] tail, float[] side, float width) {
        float ax = head[0] * 100F + side[0] * width;
        float ay = head[1] * 100F + side[1] * width;
        float az = head[2] * 100F + side[2] * width;
        float bx = tail[0] * 100F + side[0] * width;
        float by = tail[1] * 100F + side[1] * width;
        float bz = tail[2] * 100F + side[2] * width;
        float cx = head[0] * 100F - side[0] * width;
        float cy = head[1] * 100F - side[1] * width;
        float cz = head[2] * 100F - side[2] * width;
        float ux = bx - ax, uy = by - ay, uz = bz - az;
        float vx = cx - ax, vy = cy - ay, vz = cz - az;
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        return (float) Math.sqrt(nx * nx + ny * ny + nz * nz) * 0.5F;
    }

    private static void require(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
