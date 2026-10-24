#version 150

uniform sampler2D NoiseSampler;

in vec2 local;
in vec3 tint;
in float kind;
in float glow;
in vec2 dust;

out vec4 fragColor;

void main() {
    vec2 p = abs(local);
    float v;
    vec3 color = tint;
    if (kind > 3.5) {
        vec2 n = texture(NoiseSampler, dust * vec2(0.11, 0.35)).rg;
        vec2 m = texture(NoiseSampler, dust * vec2(0.9, 1.7) + 0.31).rg;
        float body = exp(-local.y * local.y * 4.5) * (0.45 + 0.55 * n.r);
        float lanes = smoothstep(0.6, 0.85, m.g) * exp(-local.y * local.y * 9.0);
        v = max(body - lanes * 0.45, 0.0) * 0.35;
        color = mix(vec3(0.55, 0.62, 1.0), vec3(1.0, 0.92, 0.85), n.g);
    } else if (kind < 1.5) {
        v = 1.0;
    } else if (kind < 2.5) {
        v = (p.x < 0.42 || p.y < 0.42) ? (p.x < 0.42 && p.y < 0.42 ? 1.0 : 0.55) : 0.0;
    } else {
        float arm = max((p.x < 0.1 ? 1.0 - p.y : 0.0), (p.y < 0.1 ? 1.0 - p.x : 0.0));
        float core = p.x < 0.28 && p.y < 0.28 ? 1.0 : 0.0;
        float halo = exp(-length(local) * 3.5) * 0.45;
        v = max(max(arm * arm, core), halo);
    }
    v *= glow;
    if (v < 0.003) {
        discard;
    }
    fragColor = vec4(color * v, 1.0);
}
