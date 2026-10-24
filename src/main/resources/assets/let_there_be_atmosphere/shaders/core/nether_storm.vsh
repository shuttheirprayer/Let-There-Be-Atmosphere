#version 150

uniform mat4 Transform;
uniform vec4 Eye;
uniform vec4 Boxes[4];
uniform vec4 Cams[4];
uniform vec4 Winds[4];
uniform vec4 Range;
uniform vec3 Right;
uniform vec3 Up;
uniform vec4 Clock;

out vec2 texCoord;
out float vertexAlpha;
out vec2 info;
out vec2 puff;

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
    texCoord = vec2(0.0);
    vertexAlpha = 0.0;
    info = vec2(0.0);
    puff = vec2(0.0);
}

void main() {
    int whole = gl_VertexID / 6;
    int corner = gl_VertexID - whole * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    int mode = whole < int(Range.x) ? 0 : (whole < int(Range.y) ? 1 : (whole < int(Range.z) ? 2 : 3));
    int slot = whole - (mode == 0 ? 0 : (mode == 1 ? int(Range.x) : (mode == 2 ? int(Range.y) : int(Range.z))));
    vec4 Box = Boxes[mode];
    vec4 Cam = Cams[mode];
    vec4 Wind = Winds[mode];
    vec4 h = rnd(uint(slot), uint(mode) * 7u + 3u);
    vec4 g = rnd(uint(slot), uint(mode) * 13u + 101u);
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    float sy = vertex >= 2 ? 1.0 : -1.0;
    float qx = sx * 0.5 + 0.5;
    float qy = sy * 0.5 + 0.5;
    float tilt = radians(10.0 + 10.0 * g.x);
    float fall = mode == 0 ? sin(tilt) * (0.5 + 0.5 * g.y) : mode == 1 ? 0.06 * g.y : mode == 3 ? 0.03 * (g.y - 0.5) : 0.0;
    vec3 dir = vec3(Wind.x * cos(tilt), -fall, Wind.y * cos(tilt));
    float speed = Wind.z * (0.75 + 0.5 * g.z);
    vec3 travel = dir * speed * Eye.w;
    vec3 rel = (fract((h.xyz * Box.xyz + travel - Cam.xyz) / Box.xyz) - 0.5) * Box.xyz;
    float wob = mode == 0 ? 1.0 : mode == 1 ? 3.0 : mode == 3 ? 4.0 : 0.0;
    if (mode == 3) rel.y = rel.y * 0.55 + 6.0;
    rel += vec3(sin(Wind.w * (1.3 + g.x) + g.y * 6.283) * 0.25, sin(Wind.w * (2.1 + g.z) + g.w * 6.283) * 0.18,
            cos(Wind.w * (1.1 + g.y) + g.z * 6.283) * 0.25) * wob;
    float dist = length(rel);
    vec3 half_ = abs(rel) / (Box.xyz * 0.5);
    float edge = 1.0 - smoothstep(0.74, 1.0, max(max(half_.x, half_.z), half_.y));
    vec3 vel = dir * speed;
    vec2 screen = vec2(dot(vel, Right), dot(vel, Up));
    float sl = length(screen);
    vec2 d = sl > 0.01 ? screen / sl : vec2(1.0, 0.0);
    vec3 along = Right * d.x + Up * d.y;
    vec3 across = Up * d.x - Right * d.y;
    vec3 pos = Eye.xyz + rel;
    vec3 world;
    float alpha;
    float code;
    float fogAmount = smoothstep(Clock.x, Clock.y, dist);
    puff = vec2(0.0);
    if (mode == 0) {
        float kind = g.w;
        float px = max(0.042 + 0.03 * h.z, dist * 0.0022);
        float idx;
        float ha = px * 4.0;
        float hb = px * 4.0;
        if (kind < 0.36) {
            idx = floor(g.y * 3.99);
            ha *= 1.0 + 0.6 * clamp(sl * 0.07, 0.0, 1.2);
            alpha = 1.0;
            code = 0.0;
        } else if (kind < 0.74) {
            idx = 4.0 + floor(g.y * 3.99);
            hb *= 0.35 + 0.65 * abs(cos(Wind.w * (2.0 + 3.0 * g.z) + g.x * 6.283));
            alpha = 0.95;
            code = 1.0;
        } else {
            idx = 8.0 + floor(g.y * 7.99);
            alpha = 0.8;
            code = 2.0;
        }
        world = pos + along * (sx * ha) + across * (sy * hb);
        texCoord = vec2((idx + qx) * 0.0625, (1.0 - qy) * 0.0625);
        alpha *= smoothstep(2.2, 7.0, dist);
    } else if (mode == 1) {
        float size = 1.38 + 2.07 * h.z;
        world = pos + along * (sx * size * 1.5) + across * (sy * size * 0.9);
        float idx = floor(g.y * 3.99);
        texCoord = vec2((idx + qx) * 0.25, 0.0625 + (1.0 - qy) * 0.25);
        alpha = (0.3 + 0.25 * g.z) * smoothstep(14.0, 28.0, dist) * (1.0 - smoothstep(54.0, 70.0, dist));
        code = g.x < 0.3 ? 4.0 : 3.0;
        fogAmount *= 0.5;
    } else if (mode == 3) {
        float size = 7.0 + 11.0 * h.z;
        float spin = g.y * 6.2831853 + Wind.w * 0.04 * (g.x - 0.5);
        vec2 q = vec2(cos(spin) * sx - sin(spin) * sy, sin(spin) * sx + cos(spin) * sy);
        world = pos + Right * (q.x * size * 1.35) + Up * (q.y * size);
        texCoord = vec2(sx, sy);
        puff = vec2(g.z * 6.2831853 + Wind.w * 0.12, g.w * 6.2831853 - Wind.w * 0.09);
        alpha = (0.34 + 0.18 * g.z) * smoothstep(3.0, 12.0, dist) * (1.0 - smoothstep(80.0, 98.0, dist)) * Clock.w;
        code = 7.0;
        fogAmount *= 0.6;
    } else {
        vec3 r = normalize(vec3(-rel.z, 0.0, rel.x) + vec3(0.0001, 0.0, 0.0));
        float halfWidth = 31.0 + 40.0 * h.z;
        float halfHeight = 5.0 + 7.0 * g.z;
        world = pos + vec3(0.0, 8.0, 0.0) + r * (sx * halfWidth) + vec3(0.0, sy * halfHeight, 0.0);
        float sheet = floor(g.y * 1.99);
        texCoord = vec2(qx, (40.0 + 32.0 * sheet) / 128.0 + (1.0 - qy) * 0.25);
        alpha = (0.19 + 0.19 * g.y) * smoothstep(40.0, 80.0, dist) * (1.0 - smoothstep(150.0, 196.0, dist));
        code = g.x < 0.25 + 0.5 * smoothstep(-10.0, 25.0, rel.y) ? 6.0 : 5.0;
        fogAmount = 0.0;
    }
    gl_Position = Transform * vec4(world, 1.0);
    vertexAlpha = alpha * edge * Clock.z;
    info = vec2(code, fogAmount);
}
