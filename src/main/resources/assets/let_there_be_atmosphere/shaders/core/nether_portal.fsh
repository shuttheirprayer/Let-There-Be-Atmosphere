#version 150

in vec4 info;
in vec4 shape;

out vec4 fragColor;

const vec3 VIOLET = vec3(0.6, 0.22, 1.0);
const vec3 LIGHT = vec3(0.88, 0.64, 1.0);

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
        fragColor = vec4(color * alpha, alpha * 0.4);
        return;
    }
    vec2 cell = abs(floor(info.yz * 3.0) - 1.0);
    float level = cell.x + cell.y < 0.5 ? 1.0 : (cell.x + cell.y < 1.5 ? 0.55 : 0.0);
    if (level <= 0.0) {
        discard;
    }
    vec3 color = mix(VIOLET * 1.15, vec3(0.97, 0.88, 1.0), level * level);
    float alpha = a * level;
    fragColor = vec4(color * alpha, alpha * 0.7);
}
