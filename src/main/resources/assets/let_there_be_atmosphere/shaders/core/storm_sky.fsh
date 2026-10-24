#version 150

uniform sampler2D NoiseSampler;
uniform mat4 InverseTransform;
uniform vec4 Deck;
uniform vec4 Scud;
uniform vec4 DarkColor;
uniform vec4 LightColor;
uniform vec4 HorizonColor;
uniform vec4 Flash;

in vec2 texCoord;

out vec4 fragColor;

float layer(vec2 p, float detail) {
    float v = (texture(NoiseSampler, p).r - 0.5) * 0.6;
    v += (texture(NoiseSampler, p * 2.07 + vec2(0.37, 0.11)).g - 0.5) * 0.3 * detail;
    v += (texture(NoiseSampler, p * 4.31 + vec2(0.71, 0.53)).r - 0.5) * 0.15 * detail * detail;
    return 0.5 + v;
}

void main() {
    vec4 far = InverseTransform * vec4(texCoord * 2.0 - 1.0, 1.0, 1.0);
    vec3 dir = normalize(far.xyz / far.w);
    if (dir.y < 0.015 || Deck.x <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }
    float t = Deck.x / dir.y;
    float detail = 1.0 - smoothstep(Deck.x * 4.0, Deck.x * 14.0, t);
    vec2 p = (dir.xz * t + Deck.yz) / 3072.0;
    float n = layer(p, detail);
    float thick = smoothstep(0.47, 0.54, n);
    float core = smoothstep(0.62, 0.68, n);
    float rim = smoothstep(0.03, 0.0, abs(n - 0.505)) * detail;
    vec3 color = mix(LightColor.rgb, DarkColor.rgb, thick * 0.65 + core * 0.35);
    color = mix(color, LightColor.rgb * 1.15, rim * 0.6);
    if (Scud.w > 0.0 && Scud.x > 0.0) {
        float s = Scud.x / dir.y;
        float m = layer((dir.xz * s + Scud.yz) / 1400.0 + vec2(0.5, 0.25), 1.0 - smoothstep(Scud.x * 3.0, Scud.x * 10.0, s));
        float wisp = smoothstep(0.6, 0.68, m) * (1.0 - smoothstep(Scud.x * 6.0, Scud.x * 16.0, s));
        color = mix(color, DarkColor.rgb * 0.8, wisp * Scud.w);
    }
    if (Flash.w > 0.0) {
        float facing = max(dot(dir, Flash.xyz), 0.0);
        float glow = Flash.w * pow(facing, 6.0) * (0.45 + 0.9 * thick + 0.4 * rim);
        color += vec3(0.78, 0.84, 1.0) * glow;
    }
    float haze = smoothstep(0.015, 0.32, dir.y);
    color = mix(HorizonColor.rgb, color, 0.35 + 0.65 * haze);
    fragColor = vec4(color, Deck.w * smoothstep(0.015, 0.12, dir.y));
}
