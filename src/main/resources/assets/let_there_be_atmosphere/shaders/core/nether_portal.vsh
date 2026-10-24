#version 150

uniform mat4 Transform;
uniform vec4 Offset;
uniform vec4 Clock;
uniform vec3 Right;
uniform vec3 Up;
uniform sampler2D DataSampler;

out vec4 info;
out vec4 shape;

const int PLANES = 3;
const int QUADS = 31;

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

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    info = vec4(0.0);
    shape = vec4(0.0);
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
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    float sy = vertex >= 2 ? 1.0 : -1.0;
    vec3 world;
    float alpha;
    float kind;
    if (k < PLANES) {
        float margin = 1.3 * (1.0 + 0.25 * pulse);
        float off = (float(k) - 1.0) * 0.6;
        world = center + u * (sx * (hu + margin)) + vec3(0.0, sy * (hv + margin), 0.0) + n * off;
        alpha = (k == 1 ? 0.36 : 0.2) * (1.0 + 0.7 * pulse) * smoothstep(0.6, 3.0, length(world));
        shape = vec4(hu, hv, margin, 0.0);
        kind = 0.0;
    } else {
        int i = k - PLANES;
        int count = int(clamp(10.0 + hu * hv * 4.8, 10.0, 28.0));
        if (i >= count) {
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
        vec3 c = center + u * (target.x + swirl.x) + vec3(0.0, target.y + swirl.y * 0.85, 0.0) + n * side;
        float cell = 0.14 * (0.75 + 0.5 * g.z) * (0.6 + 0.4 * (1.0 - f)) * (1.0 + 0.3 * pulse);
        world = c + Right * (sx * cell) + Up * (sy * cell);
        alpha = smoothstep(0.0, 0.15, f) * (1.0 - smoothstep(0.78, 1.0, f)) * (0.8 + 0.4 * pulse) * smoothstep(0.5, 2.0, length(c));
        shape = vec4(0.0);
        kind = 1.0;
    }
    alpha *= 1.0 - smoothstep(36.0, 56.0, length(world));
    gl_Position = Transform * vec4(world, 1.0);
    info = vec4(kind, sx * 0.5 + 0.5, sy * 0.5 + 0.5, alpha);
}
