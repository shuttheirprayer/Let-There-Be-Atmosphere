#version 150

in vec4 info;
in vec4 shape;
in vec4 extra;

out vec4 fragColor;

const vec3 VIOLET = vec3(0.6, 0.22, 1.0);
const vec3 LIGHT = vec3(0.88, 0.64, 1.0);
const vec3 DEEP = vec3(0.3, 0.07, 0.62);
const vec3 CORE = vec3(0.98, 0.92, 1.0);

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
    vec2 local = (info.yz * 2.0 - 1.0) * shape.xy;
    vec2 q = (floor(local * 16.0) + 0.5) / 16.0;
    vec2 n = q / max(shape.xy, vec2(0.05));
    float fy = n.y * 0.5 + 0.5;
    if (fy > extra.x) {
        discard;
    }
    float r = length(n);
    float arm = fract(atan(n.y, n.x) / 6.2831853 * 3.0 + r * 1.3 - extra.y);
    vec2 edge = shape.xy - abs(q);
    vec3 color = arm < 0.42 ? DEEP * 1.25 : (arm < 0.8 ? VIOLET : LIGHT);
    float body = arm < 0.42 ? 0.36 : (arm < 0.8 ? 0.42 : 0.52);
    if (min(edge.x, edge.y) < 1.5 / 16.0) {
        color = DEEP;
        body = 0.55;
    } else if (r < 0.1) {
        color = CORE;
        body = 0.7;
    } else if (r < 0.2) {
        color = LIGHT;
        body = 0.58;
    }
    if (fy > extra.x - 1.0 / (32.0 * max(shape.y, 0.1))) {
        color = CORE;
        body = 0.8;
    }
    color = mix(color, CORE, shape.w * 0.6);
    float alpha = a * body;
    fragColor = vec4(color * alpha, alpha);
}
