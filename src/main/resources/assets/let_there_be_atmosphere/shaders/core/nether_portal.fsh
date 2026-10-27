#version 150

in vec4 info;
in vec4 shape;
in vec4 extra;

out vec4 fragColor;

const vec3 VIOLET = vec3(0.6, 0.22, 1.0);
const vec3 LIGHT = vec3(0.88, 0.64, 1.0);
const vec3 DEEP = vec3(0.3, 0.07, 0.62);
const vec3 CORE = vec3(0.98, 0.92, 1.0);

const vec3 R0 = vec3(0.13, 0.03, 0.36);
const vec3 R1 = vec3(0.26, 0.06, 0.6);
const vec3 R2 = vec3(0.42, 0.15, 0.86);
const vec3 R3 = vec3(0.62, 0.32, 1.0);
const vec3 R4 = vec3(0.84, 0.6, 1.0);
const vec3 R5 = vec3(1.0, 0.86, 1.0);

vec3 ramp(int i) {
    return i <= 0 ? R0 : (i == 1 ? R1 : (i == 2 ? R2 : (i == 3 ? R3 : (i == 4 ? R4 : R5))));
}

float hash1(float x) {
    return fract(sin(x * 91.7) * 4375.5453);
}

int shineLift(vec2 q, vec2 hs, float t) {
    float d = (q.x + q.y * 0.6) / (hs.x + 0.6 * hs.y);
    float p = -1.5 + fract(t / 11.0) * 3.4;
    float w = abs(d - p);
    return w < 0.06 ? 2 : (w < 0.14 ? 1 : 0);
}

bool glint(vec2 q, vec2 hs, float t) {
    bool hit = false;
    for (int k = 0; k < 3; k++) {
        float cyc = t / 2.6 + float(k) * 0.37;
        float seed = floor(cyc) + float(k) * 7.0;
        float ph = fract(cyc);
        vec2 g = (vec2(hash1(seed), hash1(seed + 3.7)) * 2.0 - 1.0) * hs * 0.82;
        g = (floor(g * 16.0) + 0.5) / 16.0;
        vec2 d = abs(q - g) * 16.0;
        bool core = d.x < 0.5 && d.y < 0.5;
        bool arms = ph > 0.1 && ph < 0.3 && ((d.x < 0.5 && d.y < 1.5) || (d.y < 0.5 && d.x < 1.5));
        hit = hit || (ph < 0.4 && (core || arms));
    }
    return hit;
}

void main() {
    float a = info.w;
    if (a < 0.004) {
        discard;
    }
    if (info.x < 0.5) {
        vec2 local = (info.yz * 2.0 - 1.0) * (shape.xy + shape.z);
        vec2 outside = max(abs(local) - shape.xy, 0.0);
        float d = length(outside) / shape.z;
        float g = 1.0 - smoothstep(0.0, 1.0, d);
        float alpha = a * g * g * (0.55 + 0.45 * smoothstep(0.0, 0.25, d));
        if (alpha < 0.004) {
            discard;
        }
        vec3 color = mix(VIOLET, LIGHT, (1.0 - smoothstep(0.0, 0.6, d)) * 0.5);
        color = mix(color, CORE, shape.w * 0.75);
        fragColor = vec4(color * alpha, alpha * 0.4);
        return;
    }
    if (info.x < 1.5) {
        vec2 cell = abs(floor(info.yz * 3.0) - 1.0);
        float level = cell.x + cell.y < 0.5 ? 1.0 : (cell.x + cell.y < 1.5 ? 0.55 : 0.0);
        if (level <= 0.0) {
            discard;
        }
        vec3 color = mix(VIOLET * 1.15, vec3(0.97, 0.88, 1.0), level * level);
        float alpha = a * level;
        fragColor = vec4(color * alpha, alpha * 0.7);
        return;
    }
    if (info.x > 2.5) {
        vec3 color = mix(VIOLET, CORE, extra.z * extra.z);
        fragColor = vec4(color * a, a);
        return;
    }
    vec2 hs = shape.xy;
    float t = extra.y;
    vec2 local = (info.yz * 2.0 - 1.0) * hs;
    vec2 q = (floor(local * 16.0) + 0.5) / 16.0;
    vec2 n = q / max(hs, vec2(0.05));
    float fy = n.y * 0.5 + 0.5;
    if (fy > extra.x) {
        discard;
    }
    float r = length(n);
    float th = atan(n.y, n.x) / 6.2831853;
    float depth = -log(r + 0.12) * 0.9;
    float a1 = fract(th * 3.0 + depth - t / 8.0);
    float a2 = fract(th * 2.0 - depth * 0.5 + t / 6.0);
    int dark = int(floor((1.0 - smoothstep(0.1, 0.95, r)) * 2.6));
    int idx;
    float body;
    if (a1 >= 0.44) {
        idx = clamp((a1 < 0.78 ? 3 : 4) - dark, 0, 5);
        body = (0.36 + 0.05 * float(idx)) * 0.7;
    } else {
        idx = clamp((a2 < 0.55 ? 1 : 2) - (r < 0.3 ? 1 : 0), 0, 5);
        body = (0.3 + 0.04 * float(idx)) * 0.7;
    }
    vec3 color = ramp(idx);
    bool hot = false;
    int lift = shineLift(q, hs, t);
    if (lift > 0) {
        idx = min(idx + lift, 5);
        color = ramp(idx);
        body = max(body, (0.3 + 0.05 * float(idx)) * 0.7);
    }
    if (r < 0.05) {
        color = R5;
        hot = true;
        body = 0.64;
    } else if (r < 0.14) {
        color = R0;
        body = 0.5;
    }
    vec2 e = hs - abs(q);
    float px = floor(min(e.x, e.y) * 16.0);
    if (px < 1.0) {
        color = mix(color, R4, 0.75);
        body = max(body, 0.5);
    } else if (px < 2.0) {
        color = mix(color, R3, 0.5);
        body = max(body, 0.36);
    }
    if (glint(q, hs, t)) {
        color = R5;
        hot = true;
        body = 0.9;
    }
    if (fy > extra.x - 1.0 / (32.0 * max(hs.y, 0.1))) {
        color = CORE;
        hot = true;
        body = 0.8;
    }
    color = mix(color, CORE, shape.w * 0.6);
    float alpha = a * body;
    fragColor = vec4(min(color * (hot ? 1.0 : 1.6), vec3(1.0)) * alpha, alpha);
}
