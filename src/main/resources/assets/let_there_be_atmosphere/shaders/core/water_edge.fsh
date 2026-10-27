#version 150

uniform sampler2D Sampler0;
uniform sampler2D FoamSampler;
uniform vec4 Clock;
uniform vec4 ShoreFog;
uniform vec4 Origin;

in vec2 world;
in vec3 lightColor;
in float fogDistance;
in float fade;
flat in ivec2 land;
flat in vec2 origin;
flat in int stream;

out vec4 fragColor;

const vec3 BODY = vec3(0.925, 0.98, 1.0);
const vec3 LIT = vec3(1.0);
const vec3 SHADE = vec3(0.66, 0.87, 0.92);
const vec3 CAST = vec3(0.08, 0.32, 0.47);
const vec3 NEAR = vec3(0.44, 0.89, 0.8);
const vec3 FAR = vec3(0.24, 0.71, 0.77);

vec3 shore(vec2 p) {
    float best = 81.0;
    vec2 away = vec2(0.0, 1.0);
    int bits = land.x | (land.y << 13);
    for (int i = 0; i < 25; i++) {
        if (((bits >> i) & 1) == 0) continue;
        vec2 cell = origin + vec2(float(i - i / 5 * 5 - 2), float(i / 5 - 2));
        vec2 d = p - clamp(p, cell, cell + 1.0);
        float len = dot(d, d);
        if (len < best) {
            best = len;
            away = d;
        }
    }
    float dist = sqrt(best);
    return vec3(dist, dist > 0.0 ? away / dist : vec2(0.0, 1.0));
}

bool foam(vec2 t, float a, float wobble, float slow) {
    vec2 f = texelFetch(FoamSampler, ivec2(mod(t, 64.0)), 0).rg;
    if (a < 0.13 || (f.r > 0.0 && a + wobble * 0.16 < 0.16 + 0.14 * f.r)) return true;
    for (int i = 0; i < 2; i++) {
        float phase = fract(Clock.x * 0.16 + slow * 0.8 + float(i) * 0.5);
        if (phase < 0.1) continue;
        float gap = abs(a + wobble * 0.24 - (1.0 - phase) * 1.55);
        float thick = (0.035 + 0.1 * phase) * (0.6 + 0.9 * f.r);
        if (gap < thick && f.r > 0.32 - 0.32 * phase) return true;
        if (gap < thick + 0.14 && f.g > 1.1 - 0.9 * phase) return true;
    }
    return false;
}

float rand(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float streak(vec2 t, vec2 dir, float travel) {
    vec2 tc = t + 0.5;
    float along = dot(tc, dir) - travel;
    float across = dot(tc, vec2(-dir.y, dir.x));
    float lane = floor(across / 6.0);
    float start = lane * 6.0 + floor(rand(vec2(lane, 3.7)) * 4.0);
    float width = 1.0 + step(0.5, rand(vec2(lane, 9.1)));
    if (across < start || across >= start + width) return -1.0;
    float shifted = along + rand(vec2(lane, 5.3)) * 34.0;
    float seg = floor(shifted / 34.0);
    if (rand(vec2(lane * 7.0 + seg, 1.9)) > 0.55) return -1.0;
    float from = 2.0 + rand(vec2(seg, lane + 0.5)) * 6.0;
    float len = 8.0 + rand(vec2(lane + 2.0, seg)) * 12.0;
    float u = (shifted - seg * 34.0 - from) / len;
    return u >= 0.0 && u < 1.0 ? u : -1.0;
}

void main() {
    vec2 t = floor(world * 16.0);
    vec2 c = (t + 0.5) / 16.0;
    bool shoreHere = (stream & 128) == 0 && (land.x | land.y) != 0 && Clock.w > 0.0;
    vec3 color = FAR;
    float alpha = 0.0;
    float a = 9.0;
    bool body = false;
    if (shoreHere) {
        vec3 closest = shore(c);
        a = closest.x;
        vec2 slope = closest.yz / 16.0;
        float band = a + (texture(Sampler0, c * 0.11 + 0.71).r - 0.5) * 0.6
                + (fract(sin(dot(t, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) * 0.1;
        alpha = band < 1.6 ? 0.26 : 0.0;
        if (band < 0.75) {
            color = NEAR;
            alpha = 0.44;
        }
        float wobble = texture(Sampler0, c * 0.16 + vec2(0.0, Clock.x * 0.006)).r - 0.5;
        float slow = texture(Sampler0, c * 0.045 + 0.37).g;
        bool here = foam(t, a, wobble, slow);
        bool west = foam(t + vec2(-1.0, 0.0), a - slope.x, wobble, slow);
        bool north = foam(t + vec2(0.0, -1.0), a - slope.y, wobble, slow);
        if (here) {
            bool lit = !west || !north;
            bool dark = !foam(t + vec2(1.0, 0.0), a + slope.x, wobble, slow) || !foam(t + vec2(0.0, 1.0), a + slope.y, wobble, slow);
            color = lit && !dark ? LIT : dark && !lit ? SHADE : BODY;
            alpha = 0.92;
            body = true;
        } else if (west || north) {
            float shadow = 0.3;
            color = mix(color * alpha, CAST, shadow) / (shadow + alpha * (1.0 - shadow));
            alpha = shadow + alpha * (1.0 - shadow);
        }
        alpha = min(alpha * Clock.w, 1.0);
    }
    if ((stream & 16) != 0 && Origin.z > 0.0 && !body && (!shoreHere || a > 1.1)) {
        float ang = float(stream & 15) * 0.3926991;
        vec2 dir = vec2(cos(ang), sin(ang));
        float rate = (stream & 128) != 0 ? 1.1 : float((stream >> 5) & 3) * 0.14;
        float travel = Clock.x * rate * 16.0;
        float u = streak(t, dir, travel);
        if (u >= 0.0) {
            bool lit = streak(t + vec2(-1.0, 0.0), dir, travel) < 0.0 || streak(t + vec2(0.0, -1.0), dir, travel) < 0.0;
            bool dark = streak(t + vec2(1.0, 0.0), dir, travel) < 0.0 || streak(t + vec2(0.0, 1.0), dir, travel) < 0.0;
            vec3 tone = u > 0.8 ? LIT : u < 0.3 ? NEAR : BODY;
            color = lit && !dark ? mix(tone, LIT, 0.6) : dark && !lit ? mix(tone, SHADE, 0.6) : tone;
            alpha = u < 0.3 ? 0.5 : 0.85;
        }
    }
    alpha *= fade;
    if (alpha < 0.004) {
        discard;
    }
    float fog = clamp((fogDistance - Clock.y) / max(Clock.z - Clock.y, 0.001), 0.0, 1.0);
    float lum = dot(lightColor, vec3(0.299, 0.587, 0.114));
    vec3 lit = lightColor * mix(0.45, 1.0, smoothstep(0.12, 0.7, lum));
    fragColor = vec4(mix(color * lit, ShoreFog.rgb, fog), alpha * (1.0 - fog));
}
