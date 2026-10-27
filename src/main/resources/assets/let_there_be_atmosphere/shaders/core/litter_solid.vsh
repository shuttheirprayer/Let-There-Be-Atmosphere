#version 150

uniform mat4 Transform;
uniform vec3 Offset;
uniform vec4 Clock;
uniform vec4 LitterFog;
uniform vec4 Shape;
uniform sampler2D DataSampler;
uniform sampler2D Sampler1;
uniform sampler2D ModelSampler;
uniform sampler2D ListSampler;

out vec3 baseColor;
out vec2 texCoord;
flat out float atlas;
out float fogDistance;
out float fade;

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    baseColor = vec3(0.0);
    texCoord = vec2(0.0);
    atlas = 0.0;
    fogDistance = 0.0;
    fade = 0.0;
}

float hash(float n) {
    return fract(sin(n) * 43758.5453);
}

void main() {
    int stride = int(Shape.x + 0.5);
    int piece = gl_VertexID / stride;
    int local = gl_VertexID - piece * stride;
    int slot = int(texelFetch(ListSampler, ivec2(piece, 0), 0).r + 0.5);
    vec4 b = texelFetch(DataSampler, ivec2(slot, 1), 0);
    if (b.w == 0.0) {
        hide();
        return;
    }
    int model = int(b.x + 0.5);
    vec4 m1 = texelFetch(ModelSampler, ivec2(local * 2 + 1, model), 0);
    if (m1.x < 0.0) {
        hide();
        return;
    }
    vec4 a = texelFetch(DataSampler, ivec2(slot, 0), 0);
    if (length(a.xyz + Offset) > LitterFog.w) {
        hide();
        return;
    }
    vec4 m0 = texelFetch(ModelSampler, ivec2(local * 2, model), 0);
    vec4 motion = texelFetch(DataSampler, ivec2(slot, 2), 0);
    vec4 extra = texelFetch(DataSampler, ivec2(slot, 3), 0);
    float scale = (0.85 + 0.3 * hash(b.y * 91.7)) / 16.0;
    float dt = clamp(Clock.x - motion.w, 0.0, 0.07);
    float yaw = b.y * 6.283 + extra.x;
    float cs = cos(yaw), sn = sin(yaw);
    vec3 p0 = m0.xyz * scale;
    vec3 p = vec3(a.x + motion.x * dt + p0.x * cs - p0.z * sn, a.y + motion.y * dt + p0.y + 0.002, a.z + motion.z * dt + p0.x * sn + p0.z * cs);
    gl_Position = Transform * vec4(p, 1.0);
    vec3 n = vec3(m1.y * cs - m1.w * sn, m1.z, m1.y * sn + m1.w * cs);
    float shade = 0.72 * n.x * n.x + 0.86 * n.z * n.z + (n.y > 0.0 ? 1.0 : 0.55) * n.y * n.y;
    float contact = mix(0.8, 1.0, smoothstep(0.0, 1.6, m0.y));
    int tint = int(extra.y + 0.5);
    vec3 tone = tint == 0 ? mix(vec3(1.0, 0.97, 0.93), vec3(0.95, 0.98, 1.0), hash(b.y * 53.3)) : vec3(float((tint >> 16) & 255), float((tint >> 8) & 255), float(tint & 255)) / 255.0;
    int word = int(b.z + 0.5);
    int block = word / 16;
    vec3 light = texelFetch(Sampler1, ivec2(block, word - block * 16), 0).rgb;
    baseColor = tone * (shade * contact * (0.92 + 0.16 * hash(b.y * 37.1))) * light;
    atlas = m0.w >= 2.0 ? 1.0 : 0.0;
    texCoord = atlas > 0.5 ? vec2(m0.w - 2.0, m1.x) : vec2(m0.w, m1.x) / 64.0;
    float dist = length(p + Offset);
    fade = min((Clock.x - a.w) * 2.0, 1.0) * (1.0 - smoothstep(26.0, 34.0, dist));
    fogDistance = dist;
}
