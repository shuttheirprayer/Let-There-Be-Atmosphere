#version 150

uniform vec4 Clock;
uniform vec4 RippleFog;
uniform sampler2D HeightSampler;

in vec2 world;
in vec3 lightColor;
in float fogDistance;
flat in vec4 tile;

out vec4 fragColor;

const int DIM = 34;
const int COLS = 8;
const float RES = 8.0;

float height(ivec2 base, ivec2 c) {
    return texelFetch(HeightSampler, base + clamp(c, ivec2(0), ivec2(DIM - 1)), 0).r;
}

void main() {
    vec2 t = floor(world * 16.0);
    vec2 pos = (t + 0.5) / 16.0 - tile.yz;
    vec2 s = pos * RES + 0.5;
    ivec2 i0 = ivec2(floor(s));
    vec2 f = s - vec2(i0);
    int slot = int(tile.x + 0.5);
    ivec2 base = ivec2((slot % COLS) * DIM, (slot / COLS) * DIM);
    float h = mix(mix(height(base, i0), height(base, i0 + ivec2(1, 0)), f.x),
                  mix(height(base, i0 + ivec2(0, 1)), height(base, i0 + ivec2(1, 1)), f.x), f.y);

    int packedTint = int(tile.w + 0.5);
    vec3 water = vec3(float((packedTint >> 16) & 255), float((packedTint >> 8) & 255), float(packedTint & 255)) / 255.0;

    vec3 col;
    float alpha;
    if (h > 0.16) {
        col = mix(water, vec3(1.0), 0.92);
        alpha = 0.85;
    } else if (h > 0.06) {
        col = mix(water, vec3(1.0), 0.55);
        alpha = 0.55;
    } else if (h < -0.25) {
        col = water * 0.4;
        alpha = 0.45;
    } else if (h < -0.10) {
        col = water * 0.6;
        alpha = 0.3;
    } else {
        discard;
    }
    alpha *= Clock.w;
    float fog = clamp((fogDistance - Clock.y) / max(Clock.z - Clock.y, 0.001), 0.0, 1.0);
    float lum = dot(lightColor, vec3(0.299, 0.587, 0.114));
    vec3 lit = lightColor * mix(0.45, 1.0, smoothstep(0.12, 0.7, lum));
    fragColor = vec4(mix(col * lit, RippleFog.rgb, fog), alpha * (1.0 - fog));
}
