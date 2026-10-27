#version 150

uniform mat4 Transform;
uniform vec4 Offset;
uniform vec4 Clock;
uniform vec3 Right;
uniform vec3 Up;
uniform sampler2D DataSampler;

out vec4 info;
out vec4 shape;
out vec4 extra;

const int PLANES = 3;
const int MOTES = 28;
const int SURFACE = PLANES + MOTES;
const int BURST = SURFACE + 2;
const int QUADS = BURST + 16;
const float FACE = 0.137;

uvec4 pcg(uvec4 v) {
    v = v * 1664525u + 1013904223u;
    v.x += v.y * v.w;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    v.w += v.y * v.z;
    v ^= v >> 16u;
    v.x += v.y * v.w;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    v.w += v.y * v.z;
    return v;
}

vec4 rnd(uint n, uint salt) {
    return vec4(pcg(uvec4(n, salt, 17u, 91u)) >> 8u) / 16777216.0;
}

float ago(float t) {
    float d = Clock.x - t;
    return d < 0.0 ? d + 3600.0 : d;
}

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    info = vec4(0.0);
    shape = vec4(0.0);
    extra = vec4(0.0);
}

void main() {
    int slot = gl_VertexID / 6;
    int corner = gl_VertexID - slot * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    int rect = slot / QUADS;
    int k = slot - rect * QUADS;
    if (rect >= int(Clock.y + 0.5)) {
        hide();
        return;
    }
    vec4 a = texelFetch(DataSampler, ivec2(rect, 0), 0);
    vec4 b = texelFetch(DataSampler, ivec2(rect, 1), 0);
    vec4 c = texelFetch(DataSampler, ivec2(rect, 2), 0);
    bool ghost = c.y >= 0.0;
    bool fresh = !ghost && c.x >= 0.0;
    float age = ghost ? ago(c.y) : (fresh ? ago(c.x) : 99.0);
    if (ghost && age > 1.7) {
        hide();
        return;
    }
    bool alongZ = a.w > 0.5;
    vec3 lo = a.xyz;
    vec3 hi = b.xyz;
    vec3 center = (lo + hi) * 0.5 + Offset.xyz;
    vec3 u = alongZ ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
    vec3 n = alongZ ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 0.0, 1.0);
    float hu = alongZ ? (hi.z - lo.z) * 0.5 : (hi.x - lo.x) * 0.5;
    float hv = (hi.y - lo.y) * 0.5;
    float cycle = fract(Clock.x / 14.4 + b.w);
    float pulse = cycle < 0.45 ? pow(sin(3.14159 * cycle / 0.45), 2.0) : 0.0;
    float scale = ghost ? 1.0 - smoothstep(0.0, 0.6, age) : 1.0;
    float fade = ghost ? 1.0 - smoothstep(0.4, 0.65, age) : 1.0;
    float flash = fresh ? exp(-age * 5.5) : 0.0;
    float pinch = ghost ? smoothstep(0.1, 0.5, age) : 0.0;
    float fill = fresh && age < 0.52 ? age / 0.5 : 2.0;
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    float sy = vertex >= 2 ? 1.0 : -1.0;
    vec3 world;
    float alpha;
    float kind;
    extra = vec4(0.0);
    if (k < PLANES) {
        float margin = 1.3 * (1.0 + 0.25 * pulse) * (1.0 + 0.7 * flash) * (ghost ? 0.35 + 0.65 * scale : 1.0);
        float off = (float(k) - 1.0) * 0.6;
        world = center + u * (sx * (hu * scale + margin)) + vec3(0.0, sy * (hv * scale + margin), 0.0) + n * off;
        alpha = (k == 1 ? 0.36 : 0.2) * (1.0 + 0.7 * pulse + 2.2 * flash + 1.6 * pinch) * fade * smoothstep(0.6, 3.0, length(world));
        shape = vec4(hu * scale, hv * scale, margin, clamp(flash * 0.9 + pinch * 0.7, 0.0, 1.0));
        kind = 0.0;
    } else if (k < SURFACE) {
        int i = k - PLANES;
        int count = int(clamp(10.0 + hu * hv * 4.8, 10.0, 28.0));
        if (ghost || i >= count) {
            hide();
            return;
        }
        vec4 h = rnd(uint(rect * 64 + i), 41u);
        vec4 g = rnd(uint(rect * 64 + i), 77u);
        float f = fract(Clock.x * floor(720.0 + 480.0 * h.x) / 3600.0 + h.y);
        float r = pow(1.0 - f, 1.6);
        vec2 target = (vec2(h.z, h.w) * 2.0 - 1.0) * vec2(hu, hv) * 0.9;
        float angle = g.x * 6.2832 + f * (7.0 + 3.0 * g.y);
        float radius = 0.12 + 2.4 * r * (0.6 + 0.4 * g.z);
        vec2 swirl = vec2(cos(angle), sin(angle)) * radius;
        float side = (g.w < 0.5 ? -1.0 : 1.0) * (0.08 + 2.6 * r);
        vec3 mote = center + u * (target.x + swirl.x) + vec3(0.0, target.y + swirl.y * 0.85, 0.0) + n * side;
        float cell = 0.14 * (0.75 + 0.5 * g.z) * (0.6 + 0.4 * (1.0 - f)) * (1.0 + 0.3 * pulse);
        world = mote + Right * (sx * cell) + Up * (sy * cell);
        alpha = smoothstep(0.0, 0.15, f) * (1.0 - smoothstep(0.78, 1.0, f)) * (0.8 + 0.4 * pulse) * smoothstep(0.5, 2.0, length(mote));
        shape = vec4(0.0);
        kind = 1.0;
    } else if (k < BURST) {
        if (ghost && age > 0.7) {
            hide();
            return;
        }
        float side = k == SURFACE ? 1.0 : -1.0;
        world = center + u * (sx * hu * scale) + vec3(0.0, sy * hv * scale, 0.0) + n * (side * FACE);
        alpha = fade;
        shape = vec4(hu * scale, hv * scale, 1.0, flash);
        extra = vec4(fill, Clock.x / 9.0 + b.w + (ghost ? age * age * 5.0 : 0.0), 0.0, 0.0);
        kind = 2.0;
    } else {
        int i = k - BURST;
        vec4 h = rnd(uint(rect * 64 + i), 113u);
        vec4 g = rnd(uint(rect * 64 + i), 233u);
        if (ghost) {
            float t = max(age - 0.12 * h.z, 0.0);
            vec2 start = (h.xy * 2.0 - 1.0) * vec2(hu, hv) * 0.95;
            vec2 off = start + vec2((g.x * 2.0 - 1.0) * 1.1 * t, (g.y * 1.5 - 0.3) * t - 4.5 * t * t);
            float cell = 0.05 + 0.05 * g.w;
            world = center + u * off.x + vec3(0.0, off.y, 0.0) + n * ((g.z - 0.5) * 0.25) + Right * (sx * cell * 0.7 + sy * cell * 0.5 * (g.z * 2.0 - 1.0)) + Up * (sy * cell * 1.6);
            alpha = smoothstep(0.0, 0.03, age) * (1.0 - smoothstep(1.0, 1.6, age));
            extra = vec4(0.0, 0.0, g.w, 0.0);
            kind = 3.0;
        } else {
            if (!fresh || age > 1.1) {
                hide();
                return;
            }
            float grow = 1.0 - exp(-age * 5.0);
            float ang = (float(i) + h.x * 0.9) * 0.3927;
            float rr = (0.1 + 1.9 * grow) * (0.93 + 0.14 * h.y);
            vec2 ring = vec2(cos(ang) * (hu + 0.5), sin(ang) * (hv + 0.5)) * rr;
            float cell = 0.2 * (1.0 - 0.45 * age);
            world = center + u * ring.x + vec3(0.0, ring.y, 0.0) + n * ((h.z - 0.5) * 0.9 * grow) + Right * (sx * cell) + Up * (sy * cell);
            alpha = smoothstep(0.0, 0.03, age) * (1.0 - smoothstep(0.55, 1.1, age));
            kind = 1.0;
        }
        shape = vec4(0.0);
    }
    alpha *= 1.0 - smoothstep(36.0, 56.0, length(world));
    gl_Position = Transform * vec4(world, 1.0);
    info = vec4(kind, sx * 0.5 + 0.5, sy * 0.5 + 0.5, alpha);
}
