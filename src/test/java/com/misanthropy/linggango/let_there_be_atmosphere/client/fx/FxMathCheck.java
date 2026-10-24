package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

public final class FxMathCheck {
    public static void main(String[] args) {
        checkNarrowCurve();
        checkVanillaSunTrack();
        checkStarTrack();
        checkFogMultiplyTrack();
        checkSkyLightTrack();
        checkLightTint();
        checkWeatherTint();
        System.out.println("Fx math OK");
    }

    private static void checkNarrowCurve() {
        float[] out = new float[4];
        float peak = 0f;

        for (int i = 0; i <= 2400; i++) {
            float time = i / 2400f;
            if (!SunTint.sampleNarrow(time, out)) continue;
            for (int c = 0; c < 4; c++) {
                check(out[c] >= 0f && out[c] <= 1f, "narrow channel " + c + " out of range at " + time + ": " + out[c]);
            }
            check(out[0] >= out[1] && out[1] >= out[2], "narrow should stay warm (r >= g >= b) at " + time);
            peak = Math.max(peak, out[3]);
        }

        check(peak > 0.9f, "narrow glow never reaches full alpha, peak was " + peak);
        check(!SunTint.sampleNarrow(0f, out), "narrow glow at noon");
        check(!SunTint.sampleNarrow(0.5f, out), "narrow glow at midnight");
        check(SunTint.sampleNarrow(0.25f, out), "narrow has no glow at sunset");
        check(out[3] > 0.95f, "narrow sunset alpha too low: " + out[3]);
    }

    private static void checkVanillaSunTrack() {
        float[] out = new float[4];

        check(!DayCurves.sunriseSunset(6000f, out), "vanilla track glows at noon");
        check(!DayCurves.sunriseSunset(18000f, out), "vanilla track glows at midnight");

        check(DayCurves.sunriseSunset(12841f, out), "no glow at sunset");
        float dusk = out[3];
        check(dusk > 0.99f, "sunset alpha too low: " + dusk);
        check(DayCurves.sunriseSunset(23159f, out), "no glow at sunrise");
        check(Math.abs(out[3] - dusk) < 0.01f, "sunrise and sunset should peak alike: " + out[3] + " vs " + dusk);

        float peak = 0f;
        for (int t = 0; t < DayCurves.PERIOD; t += 7) {
            DayCurves.sunriseSunset(t, out);
            for (int c = 0; c < 4; c++) {
                check(out[c] >= 0f && out[c] <= 1f, "vanilla channel " + c + " out of range at tick " + t + ": " + out[c]);
            }
            check(out[0] >= out[1] && out[1] >= out[2], "vanilla track should stay warm at tick " + t);
            peak = Math.max(peak, out[3]);
        }
        check(peak > 0.99f, "vanilla track never reaches full alpha, peak was " + peak);

        DayCurves.sunriseSunset(12243f, out);
        float duskGreen = out[1];
        DayCurves.sunriseSunset(14039f, out);
        check(out[1] < duskGreen, "the track should redden after sunset: " + out[1] + " vs " + duskGreen);

        float[] before = new float[4], after = new float[4];
        DayCurves.sunriseSunset(DayCurves.PERIOD - 1f, before);
        DayCurves.sunriseSunset(0f, after);
        for (int c = 0; c < 4; c++) {
            check(Math.abs(before[c] - after[c]) < 0.01f,
                    "wrap seam jumps on channel " + c + ": " + before[c] + " vs " + after[c]);
        }
    }

    private static void checkStarTrack() {
        check(DayCurves.starBrightness(6000f) == 0f, "stars visible at noon");
        check(Math.abs(DayCurves.starBrightness(18000f) - 0.5f) < 1.0E-4f,
                "midnight stars should sit at half brightness: " + DayCurves.starBrightness(18000f));

        float a = DayCurves.starBrightness(11959f);
        float b = DayCurves.starBrightness(12399f);
        float c = DayCurves.starBrightness(12729f);
        check(a < b && b < c, "dusk star ramp is not monotonic: " + a + ", " + b + ", " + c);
        check(a > 0f && c < 0.5f, "dusk ramp should sit strictly between off and full");

        for (int t = 0; t < DayCurves.PERIOD; t += 7) {
            float v = DayCurves.starBrightness(t);
            check(v >= 0f && v <= 0.5f, "star brightness out of range at tick " + t + ": " + v);
        }
    }

    private static void checkFogMultiplyTrack() {
        float[] out = new float[3];

        DayCurves.fogMultiply(6000f, out);
        check(out[0] == 1f && out[1] == 1f && out[2] == 1f, "daytime fog must not be dimmed");

        DayCurves.fogMultiply(18000f, out);
        check(out[0] < 0.15f && out[1] < 0.15f && out[2] < 0.15f, "night fog should be strongly dimmed");

        float[] dusk = new float[3];
        DayCurves.fogMultiply(13670f, dusk);
        check(dusk[2] > dusk[0], "post-dusk fog should keep more blue than red");
        DayCurves.fogMultiply(22330f, out);
        check(Math.abs(out[2] - out[0]) < 1.0E-4f, "small-hours fog should settle neutral");

        for (int t = 0; t < DayCurves.PERIOD; t += 7) {
            DayCurves.fogMultiply(t, out);
            for (int ch = 0; ch < 3; ch++) {
                check(out[ch] >= 0f && out[ch] <= 1f, "fog multiplier out of range at tick " + t);
            }
        }
    }

    private static void checkSkyLightTrack() {
        float[] out = new float[3];

        check(DayCurves.skyLightFactor(6000f) == 1f, "full sky light must survive at noon");
        check(Math.abs(DayCurves.skyLightFactor(18000f) - 0.24f) < 1.0E-4f,
                "night sky light should sit at 0.24: " + DayCurves.skyLightFactor(18000f));

        check(DayCurves.nightFraction(6000f) == 0f, "noon is not night");
        check(Math.abs(DayCurves.nightFraction(18000f) - 1f) < 1.0E-4f, "midnight should be fully night");

        float dusk = DayCurves.nightFraction(12200f);
        check(dusk > 0f && dusk < 1f, "dusk night fraction should sit between: " + dusk);

        DayCurves.skyLightColor(6000f, out);
        check(out[0] == 1f && out[1] == 1f && out[2] == 1f, "daylight sky light must not be tinted");
        DayCurves.skyLightColor(18000f, out);
        check(out[2] > out[0] && Math.abs(out[0] - out[1]) < 1.0E-4f,
                "night sky light should be blue with equal red and green");

        for (int t = 0; t < DayCurves.PERIOD; t += 7) {
            float f = DayCurves.skyLightFactor(t);
            check(f >= 0.24f && f <= 1f, "sky light factor out of range at tick " + t + ": " + f);
            float n = DayCurves.nightFraction(t);
            check(n >= 0f && n <= 1f, "night fraction out of range at tick " + t + ": " + n);
        }
    }

    private static void checkLightTint() {
        final int white = 0xFFFFFFFF;
        final int warmHex = 0xFFD8AC, coolHex = 0x7A7AFF;

        int blockOnly = LightTint.tint(white, 1f, 0f, 0f, warmHex, 1f, coolHex, 1f, 0xFFFFFF, 0f);
        check((blockOnly       & 0xFF) == 255, "warm tint should keep red at full");
        check((blockOnly >>  8 & 0xFF) == 216, "warm tint green: " + (blockOnly >> 8 & 0xFF));
        check((blockOnly >> 16 & 0xFF) == 172, "warm tint blue: " + (blockOnly >> 16 & 0xFF));
        check((blockOnly >>> 24) == 0xFF, "alpha must survive tinting");

        check(LightTint.tint(white, 0f, 1f, 0f, warmHex, 1f, coolHex, 1f, 0xFFFFFF, 0f) == white,
                "daylight sky light must not be tinted");

        int nightSky = LightTint.tint(white, 0f, 1f, 1f, warmHex, 1f, coolHex, 1f, 0xFFFFFF, 0f);
        check((nightSky >> 16 & 0xFF) == 255, "cool tint should keep blue at full");
        check((nightSky       & 0xFF) == 122, "cool tint red: " + (nightSky & 0xFF));
        check((nightSky >>  8 & 0xFF) == 122, "cool tint green: " + (nightSky >> 8 & 0xFF));

        check(LightTint.tint(white, 0f, 0f, 1f, warmHex, 1f, coolHex, 1f, 0xFFFFFF, 0f) == white, "no light means no tint");
        check(LightTint.tint(white, 1f, 1f, 1f, warmHex, 0f, coolHex, 0f, 0xFFFFFF, 0f) == white, "zero strength must be a no-op");

        int dim = LightTint.tint(white, 1f, 0f, 0f, 0x402000, 1f, coolHex, 1f, 0xFFFFFF, 0f);
        check((dim & 0xFF) == 255, "tint colour should be normalised, red was " + (dim & 0xFF));

        int half = LightTint.tint(white, 0.5f, 0f, 0f, warmHex, 1f, coolHex, 1f, 0xFFFFFF, 0f);
        check((half >> 16 & 0xFF) > (blockOnly >> 16 & 0xFF), "a dim cell should be tinted less than a bright one");

        check(LightTint.tint(white, 1f, 1f, 0f, warmHex, 1f, coolHex, 1f, 0xFFFFFF, 0f) == white,
                "full bright must stay neutral by day");
        check(LightTint.tint(white, 1f, 1f, 1f, warmHex, 1f, coolHex, 1f, 0xFFFFFF, 0f) == white,
                "full bright must stay neutral at night");
        check(LightTint.tint(white, 1f, 1f, 0f, 0xFF00FF, 1f, 0x00FF00, 1f, 0xFFFFFF, 0f) == white,
                "full bright must stay neutral whatever the tint colours are");

        int nearCorner = LightTint.tint(white, 0.87f, 1f, 0f, warmHex, 1f, coolHex, 1f, 0xFFFFFF, 0f);
        check(nearCorner != white, "one step off full bright should still be tinted");
    }

    private static void checkWeatherTint() {
        check(WeatherTint.amount(0f, 0f, 1f, 1f, 1.0) == 0f, "dry weather must not tint");
        check(WeatherTint.amount(1f, 0f, 0f, 1f, 1.0) == 0f, "no open sky must not tint");
        check(WeatherTint.amount(1f, 1f, 1f, 1f, 0.0) == 0f, "zero strength must be a no-op");

        float day = WeatherTint.amount(1f, 0f, 1f, 1f, 1.0);
        check(day > 0f && day < 1f, "full rain alpha out of range: " + day);

        check(WeatherTint.amount(0.5f, 0f, 1f, 1f, 1.0) < day, "heavier rain must tint more");
        check(WeatherTint.amount(1f, 0f, 0.5f, 1f, 1.0) < day, "less open sky must tint less");
        check(WeatherTint.amount(1f, 0f, 1f, 0f, 1.0) < day, "night must tint less than day");
        check(WeatherTint.amount(1f, 0f, 1f, 1f, 0.5) < day, "lower strength must tint less");
        check(WeatherTint.amount(1f, 1f, 1f, 1f, 1.0) > day, "thunder must tint more than rain");

        check(WeatherTint.amount(1f, 0f, 1f, 0f, 1.0) > 0f, "night must keep a trace of the cast");

        final int rain = 0x8AA8CC, snow = 0xD8E4F0;
        check(WeatherTint.color(rain, snow, 0f) == rain, "snow mix 0 must be the rain colour");
        check(WeatherTint.color(rain, snow, 1f) == snow, "snow mix 1 must be the snow colour");
        int half = WeatherTint.color(rain, snow, 0.5f);
        check((half >> 16 & 0xFF) == 0xB1, "half-mixed red: " + Integer.toHexString(half));
        check(half != rain && half != snow, "half mix should land between the two");
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
