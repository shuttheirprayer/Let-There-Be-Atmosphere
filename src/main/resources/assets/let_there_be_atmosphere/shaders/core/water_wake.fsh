#version 150

uniform vec4 Clock;
uniform vec4 WakeFog;

in vec2 local;
in vec3 lightColor;
in float fogDistance;
flat in vec4 info;
flat in vec3 water;

out vec4 fragColor;

float hash(float n) {
    return fract(sin(n * 127.1 + 311.7) * 43758.5453);
}

void main() {
    vec3 col;
    float alpha;
    if (WakeFog.w < 0.5) {
        float tx = floor(local.x * 16.0);
        float ty = floor(local.y * 16.0);
        float u = clamp(abs(tx + 0.5) / 16.0 / max(info.x, 0.001), 0.0, 1.0);
        float top = info.y * (1.0 - pow(u, 1.8)) * (0.62 + 0.38 * hash(tx + info.w * 13.0));
        top = floor(top * 16.0 + 0.5) / 16.0;
        if (local.y > top || top < 0.05) {
            discard;
        }
        float fade = 1.0 - smoothstep(0.38, 0.6, info.z);
        if (local.y > top - 0.07) {
            col = mix(water, vec3(1.0), 0.92);
            alpha = 0.9 * fade;
        } else {
            col = mix(water, vec3(1.0), 0.35 + 0.4 * local.y / max(info.y, 0.05));
            alpha = 0.62 * fade;
        }
    } else {
        col = mix(water, vec3(1.0), 0.88);
        alpha = 0.95 * (1.0 - smoothstep(0.65, 1.0, info.z));
    }
    alpha *= Clock.w;
    float fog = clamp((fogDistance - Clock.y) / max(Clock.z - Clock.y, 0.001), 0.0, 1.0);
    float lum = dot(lightColor, vec3(0.299, 0.587, 0.114));
    vec3 lit = lightColor * mix(0.45, 1.0, smoothstep(0.12, 0.7, lum));
    fragColor = vec4(mix(col * lit, WakeFog.rgb, fog), alpha * (1.0 - fog));
}
