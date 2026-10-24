package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

public final class SkyFxCheck {
    public static void main(String[] args) {
        checkStarField();
        checkStarGroups();
        checkTwinkle();
        checkNightAmount();
        checkCometRibbon();
        checkCometSpawn();
        checkMeteorShower();
        checkDrift();
        checkSunLightCast();
        checkTorchFlicker();
        System.out.println("Sky fx OK");
    }

    private static void checkStarField() {
        float[] plain = StarField.build(400, 1f, 0.6f, false, 0f, 32f);
        check(plain.length == 400 * StarField.STRIDE, "plain star count: " + plain.length / StarField.STRIDE);

        float[] banded = StarField.build(400, 1f, 0.6f, true, 1f, 32f);
        check(banded.length > plain.length, "the milky way band must add stars");

        float[] again = StarField.build(400, 1f, 0.6f, true, 1f, 32f);
        for (int i = 0; i < banded.length; i++) {
            check(banded[i] == again[i], "star generation is not deterministic at " + i);
        }

        int stars = banded.length / StarField.STRIDE;
        for (int i = 0; i < stars; i++) {
            int at = i * StarField.STRIDE;
            float px = banded[at], py = banded[at + 1], pz = banded[at + 2];
            float ux = banded[at + 3], uy = banded[at + 4], uz = banded[at + 5];
            float vx = banded[at + 6], vy = banded[at + 7], vz = banded[at + 8];

            float radius = (float) Math.sqrt(px * px + py * py + pz * pz);
            check(Math.abs(radius - StarField.RADIUS) < 0.01f, "star " + i + " sits at radius " + radius);

            check(Math.abs(px * ux + py * uy + pz * uz) < 0.02f, "star " + i + " tangent is not flat");
            check(Math.abs(px * vx + py * vy + pz * vz) < 0.02f, "star " + i + " bitangent is not flat");
            check(Math.abs(ux * vx + uy * vy + uz * vz) < 0.001f, "star " + i + " basis is not square");

            float half = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
            float other = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            check(half > 0.02f, "star " + i + " has no size");
            check(Math.abs(half - other) < 0.001f, "star " + i + " is not square");

            for (int c = 9; c < 12; c++) {
                check(banded[at + c] >= 0f && banded[at + c] <= 1f,
                        "star " + i + " colour out of range: " + banded[at + c]);
            }
            check(banded[at + 9] + banded[at + 10] + banded[at + 11] > 0.05f, "star " + i + " is black");
        }

        float[] white = StarField.build(64, 1f, 0f, false, 0f, 32f);
        for (int i = 0; i < 64; i++) {
            int at = i * StarField.STRIDE;
            check(Math.abs(white[at + 9] - white[at + 10]) < 0.001f, "zero spread must leave stars white");
            check(Math.abs(white[at + 10] - white[at + 11]) < 0.001f, "zero spread must leave stars white");
        }

        check(StarField.build(0, 1f, 0.5f, true, 1f, 32f).length == 0, "zero stars must build nothing");
        check(StarField.count(0.0) == 0, "zero density must be zero stars");
        check(StarField.count(100.0) == StarField.VANILLA_COUNT, "100% must match vanilla");
        check(StarField.count(100000.0) == StarField.MAX_STARS, "the star count must stay capped");
    }

    private static void checkStarGroups() {
        check(StarField.groupsFor(0, 1500) == 0, "no stars means no groups");
        check(StarField.groupsFor(1500, 0) == 0, "no baked stars means no groups");
        check(StarField.groupsFor(1500, 1500) == StarField.GROUPS, "a full share must draw every group");
        check(StarField.groupsFor(3000, 1500) == StarField.GROUPS, "the share must stay capped");

        int half = StarField.groupsFor(750, 1500);
        check(half == StarField.GROUPS / 2, "half the stars should be half the groups: " + half);

        int previous = 0;
        for (int wanted = 0; wanted <= 1500; wanted += 25) {
            int groups = StarField.groupsFor(wanted, 1500);
            check(groups >= previous, "the group count must not fall as stars rise");
            previous = groups;
        }
    }

    private static void checkTwinkle() {
        for (int group = 0; group < StarField.GROUPS; group++) {
            check(StarField.twinkle(group, 12.5f, 0f) == 1f, "zero twinkle must hold still");
            for (int step = 0; step < 400; step++) {
                float value = StarField.twinkle(group, step * 0.37f, 1f);
                check(value >= 0.5f && value <= 1.5f, "twinkle out of range: " + value);
            }
        }

        float a = StarField.twinkle(0, 1.7f, 1f);
        float b = StarField.twinkle(5, 1.7f, 1f);
        check(Math.abs(a - b) > 0.001f, "twinkle groups must not move together");
    }

    private static void checkNightAmount() {
        check(StarField.nightAmount(6000f) == 0f, "noon is not night");
        check(Math.abs(StarField.nightAmount(18000f) - 1f) < 1.0E-4f, "midnight must be fully night");
        float dusk = StarField.nightAmount(12399f);
        check(dusk > 0f && dusk < 1f, "dusk must sit between: " + dusk);
        for (int t = 0; t < DayCurves.PERIOD; t += 7) {
            float value = StarField.nightAmount(t);
            check(value >= 0f && value <= 1f, "night amount out of range at tick " + t + ": " + value);
        }
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

        check(LightTint.tint(white, 1f, 0f, 0f, warmHex, 1f, coolHex, 1f)
                        == LightTint.tint(white, 1f, 0f, 0f, warmHex, 1f, coolHex, 1f, sunHex, 0f, 0f),
                "no sun cast and no flicker must match the old behaviour");

        int cast = LightTint.tint(white, 0f, 1f, 0f, warmHex, 1f, coolHex, 1f, sunHex, 1f, 0f);
        check((cast & 0xFF) == 255, "the sun cast must keep red at full");
        check((cast >> 8 & 0xFF) == 183, "sun cast green: " + (cast >> 8 & 0xFF));
        check((cast >> 16 & 0xFF) == 117, "sun cast blue: " + (cast >> 16 & 0xFF));

        check(LightTint.tint(white, 0f, 1f, 0f, warmHex, 1f, coolHex, 1f, sunHex, 0f, 0f) == white,
                "a sun that has set must not cast");
        check(LightTint.tint(white, 1f, 1f, 0f, warmHex, 1f, coolHex, 1f, sunHex, 1f, 0f) == white,
                "full bright must stay neutral under the sun cast");

        int half = LightTint.tint(white, 0f, 1f, 0f, warmHex, 1f, coolHex, 1f, sunHex, 0.5f, 0f);
        check((half >> 16 & 0xFF) > (cast >> 16 & 0xFF), "half the strength must cast half as far");
    }

    private static void checkTorchFlicker() {
        final int white = 0xFFFFFFFF;
        final int warmHex = 0xFFD8AC, coolHex = 0x7A7AFF, sunHex = 0xFFB775;

        int steady = LightTint.tint(white, 1f, 0f, 0f, warmHex, 1f, coolHex, 1f, sunHex, 0f, 0f);
        int dipped = LightTint.tint(white, 1f, 0f, 0f, warmHex, 1f, coolHex, 1f, sunHex, 0f, 1f);
        check((dipped & 0xFF) < (steady & 0xFF), "flicker must dim a torch-lit cell");
        check((dipped >>> 24) == 0xFF, "flicker must not touch alpha");

        check(LightTint.tint(white, 1f, 1f, 0f, warmHex, 1f, coolHex, 1f, sunHex, 0f, 1f) == white,
                "flicker must leave full bright alone");
        check(LightTint.tint(white, 0f, 1f, 1f, warmHex, 1f, coolHex, 1f, sunHex, 0f, 1f)
                        == LightTint.tint(white, 0f, 1f, 1f, warmHex, 1f, coolHex, 1f, sunHex, 0f, 0f),
                "flicker must not touch sky light");

        int gentle = LightTint.tint(white, 1f, 0f, 0f, warmHex, 1f, coolHex, 1f, sunHex, 0f, 0.5f);
        check((gentle & 0xFF) > (dipped & 0xFF) && (gentle & 0xFF) < (steady & 0xFF),
                "half the flicker must dim half as far");
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

    private static void checkDrift() {
        for (int seed = 0; seed < 4096; seed++) {
            float value = Drift.hash01(seed);
            check(value >= 0f && value < 1f, "hash out of range at " + seed + ": " + value);
            float signed = Drift.signed(seed);
            check(signed >= -1f && signed < 1f, "signed hash out of range at " + seed + ": " + signed);
        }
        check(Drift.hash01(7) != Drift.hash01(8), "neighbouring seeds must differ");
        check(Drift.hash01(11) == Drift.hash01(11), "the hash must be stable");

        for (int step = -400; step <= 400; step++) {
            float wrapped = Drift.wrap(step * 0.77f, 12f);
            check(wrapped >= -12f && wrapped <= 12f, "wrap escaped the box: " + wrapped);
        }
        check(Math.abs(Drift.wrap(3f, 10f) - 3f) < 1.0E-4f, "wrap must leave inside values alone");
        check(Math.abs(Drift.wrap(21f, 10f) - 1f) < 1.0E-4f, "wrap must fold around: " + Drift.wrap(21f, 10f));
        check(Drift.wrap(5f, 0f) == 0f, "a zero box wraps to nothing");

        check(Drift.edgeFade(0f, 100f) == 1f, "the middle of the box is full strength");
        check(Drift.edgeFade(100f, 100f) == 0f, "the edge of the box is empty");
        check(Drift.edgeFade(400f, 100f) == 0f, "past the edge stays empty");
        float near = Drift.edgeFade(25f, 100f);
        check(near > 0f && near < 1f, "the fade must ramp: " + near);

        check(Drift.nearFade(0f, 3f) == 0f, "nothing renders on top of the camera");
        check(Drift.nearFade(100f, 3f) == 1f, "far enough out is full strength");
        float mid = Drift.nearFade(4f, 3f);
        check(mid > 0f && mid < 1f, "the near fade must ramp: " + mid);
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
