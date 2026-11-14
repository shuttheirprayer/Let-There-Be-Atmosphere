package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

public final class SkyFxCheck {
    public static void main(String[] args) {
        checkCometRibbon();
        checkCometSpawn();
        checkMeteorShower();
        checkSunLightCast();
        System.out.println("Sky fx OK");
    }

    private static void checkCometRibbon() {
        int slot = spawnOne();
        float[] head = new float[3], tail = new float[3], side = new float[3], forward = new float[3];

        Comets.ribbon(slot, side);
        float length = (float) Math.sqrt(side[0] * side[0] + side[1] * side[1] + side[2] * side[2]);
        check(Math.abs(length - 1f) < 1.0E-4f, "the ribbon normal must be a unit vector: " + length);

        float widest = 0f;
        for (int seg = 0; seg < Comets.TAIL_SEGMENTS; seg++) {
            float u0 = (float) seg / Comets.TAIL_SEGMENTS;
            float u1 = (float) (seg + 1) / Comets.TAIL_SEGMENTS;
            Comets.position(slot, u0, 0f, head);
            Comets.position(slot, u1, 0f, tail);

            check(Math.abs(head[0] * side[0] + head[1] * side[1] + head[2] * side[2]) < 1.0E-3f,
                    "the ribbon must stay flat against the sky");

            float width = Comets.width(slot) * 100f;
            float w0 = width * (1f - u0 * 0.72f);
            float w1 = width * (1f - u1 * 0.72f);

            float[] v0 = corner(head, side, w0);
            float[] v1 = corner(tail, side, w1);
            float[] v3 = corner(head, side, -w0);

            float area = triangle(v0, v1, v3);
            check(area > 0.05f, "the comet ribbon collapsed to a line, area " + area);
            widest = Math.max(widest, area);
        }
        check(widest > 0.5f, "the comet ribbon is far too thin: " + widest);

        Comets.position(slot, 0f, 0f, head);
        Comets.heading(slot, head, forward);
        float travel = (float) Math.sqrt(forward[0] * forward[0] + forward[1] * forward[1] + forward[2] * forward[2]);
        check(Math.abs(travel - 1f) < 1.0E-4f, "the heading must be a unit vector: " + travel);
        check(Math.abs(forward[0] * side[0] + forward[1] * side[1] + forward[2] * side[2]) < 1.0E-3f,
                "the head billboard must be square");
    }

    private static void checkCometSpawn() {
        Comets.reset();
        float[] at = new float[3];
        int seen = 0;
        for (int tick = 0; tick < 4000 && seen < 200; tick++) {
            Comets.tick(true, 1.0, Comets.CAPACITY, 1.0, 1.0, 62.0, Comets.PALETTE_MIXED, 0.0, 7);
            for (int i = 0; i < Comets.CAPACITY; i++) {
                if (!Comets.alive(i)) continue;
                Comets.position(i, 0f, 0f, at);
                float radius = (float) Math.sqrt(at[0] * at[0] + at[1] * at[1] + at[2] * at[2]);
                check(Math.abs(radius - 1f) < 1.0E-3f, "a comet left the sky dome: " + radius);
                int palette = Comets.palette(i);
                check(palette >= Comets.PALETTE_WARM && palette <= Comets.PALETTE_WHITE,
                        "mixed must resolve to a real palette: " + palette);
                seen++;
            }
        }
        check(seen > 0, "no comet ever spawned");

        Comets.reset();
        check(Comets.liveCount() == 0, "reset must clear the sky");
        Comets.tick(false, 1.0, 4, 1.0, 1.0, 62.0, Comets.PALETTE_CUSTOM, 1.0, 7);
        check(Comets.liveCount() == 0, "comets must not spawn when the sky is closed");
        Comets.tick(true, 0.0, 4, 1.0, 1.0, 62.0, Comets.PALETTE_CUSTOM, 0.0, 7);
        check(Comets.liveCount() == 0, "a zero rate must spawn nothing");

        int slot = spawnOne();
        check(Comets.palette(slot) == Comets.PALETTE_CUSTOM, "the custom palette must survive a spawn");
        check(Comets.fade(slot, 0f) >= 0f && Comets.fade(slot, 0f) <= 1f, "fade out of range");
        check(Comets.alpha(slot, 1f, 0f) == 0f, "the tail must end transparent");
    }

    private static void checkSunLightCast() {
        final int white = 0xFFFFFFFF;
        final int warmHex = 0xFFD8AC, coolHex = 0x7A7AFF, sunHex = 0xFFB775;

        int cast = LightTint.tint(white, 0f, 1f, 0f, warmHex, 1f, coolHex, 1f, sunHex, 1f);
        check((cast & 0xFF) == 255, "the sun cast must keep red at full");
        check((cast >> 8 & 0xFF) == 183, "sun cast green: " + (cast >> 8 & 0xFF));
        check((cast >> 16 & 0xFF) == 117, "sun cast blue: " + (cast >> 16 & 0xFF));

        check(LightTint.tint(white, 0f, 1f, 0f, warmHex, 1f, coolHex, 1f, sunHex, 0f) == white,
                "a sun that has set must not cast");
        check(LightTint.tint(white, 1f, 1f, 0f, warmHex, 1f, coolHex, 1f, sunHex, 1f) == white,
                "full bright must stay neutral under the sun cast");

        int half = LightTint.tint(white, 0f, 1f, 0f, warmHex, 1f, coolHex, 1f, sunHex, 0.5f);
        check((half >> 16 & 0xFF) > (cast >> 16 & 0xFF), "half the strength must cast half as far");
    }

    private static void checkMeteorShower() {
        Comets.reset();
        boolean opened = false;
        int peak = 0;
        int spawned = 0;
        for (int tick = 0; tick < 6000; tick++) {
            Comets.tick(true, 0.0, 4, 1.0, 1.0, 62.0, Comets.PALETTE_WARM, opened ? 0.0 : 0.05, 8);
            if (Comets.showerRunning()) opened = true;
            int live = Comets.liveCount();
            peak = Math.max(peak, live);
            if (opened && !Comets.showerRunning()) {
                spawned = live;
                break;
            }
        }
        check(opened, "a shower must open when the chance is high");
        check(peak >= 5, "a shower of eight should fill the sky, peak was " + peak);
        check(spawned >= 3, "the shower must still be flying when it stops spawning: " + spawned);

        int[] members = new int[Comets.CAPACITY];
        int count = 0;
        for (int i = 0; i < Comets.CAPACITY; i++) if (Comets.alive(i)) members[count++] = i;
        check(count >= 3, "not enough shower members to measure a radiant");

        for (int i = 0; i < count; i++) {
            check(Comets.palette(members[i]) == Comets.PALETTE_WARM,
                    "every streak in one shower shares its colour");
        }

        float early = spread(members, count, 0f);
        float later = spread(members, count, 24f);
        check(later > early, "shower streaks must fan out from their radiant: " + early + " to " + later);

        Comets.reset();
        check(Comets.liveCount() == 0 && !Comets.showerRunning(), "reset must close a running shower");
    }

    private static float spread(int[] members, int count, float ahead) {
        float[] a = new float[3];
        float[] b = new float[3];
        float total = 0f;
        int pairs = 0;
        for (int i = 0; i < count; i++) {
            Comets.position(members[i], 0f, ahead, a);
            for (int j = i + 1; j < count; j++) {
                Comets.position(members[j], 0f, ahead, b);
                total += 1f - (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]);
                pairs++;
            }
        }
        return pairs == 0 ? 0f : total / pairs;
    }

    private static int spawnOne() {
        Comets.reset();
        Comets.tick(true, 1.0, 4, 1.0, 1.0, 62.0, Comets.PALETTE_CUSTOM, 0.0, 7);
        for (int i = 0; i < Comets.CAPACITY; i++) if (Comets.alive(i)) return i;
        throw new AssertionError("a comet with a full spawn chance never appeared");
    }

    private static float[] corner(float[] dir, float[] side, float offset) {
        return new float[]{
                dir[0] * 100f + side[0] * offset,
                dir[1] * 100f + side[1] * offset,
                dir[2] * 100f + side[2] * offset,
        };
    }

    private static float triangle(float[] a, float[] b, float[] c) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        float vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        float cx = uy * vz - uz * vy;
        float cy = uz * vx - ux * vz;
        float cz = ux * vy - uy * vx;
        return (float) Math.sqrt(cx * cx + cy * cy + cz * cz) * 0.5f;
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
