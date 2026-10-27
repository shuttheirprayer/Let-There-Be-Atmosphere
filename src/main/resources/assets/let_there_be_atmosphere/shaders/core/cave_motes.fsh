#version 150

uniform sampler2D Sampler0;
uniform vec4 Clock;
uniform vec4 MoteFog;

in vec2 texCoord;
in vec4 vertexColor;
in float fogDistance;
in vec3 spot;

out vec4 fragColor;

float flake(vec2 c, float seed) {
    if (abs(c.x) > 2.0 || abs(c.y) > 2.0) return 0.0;
    float d = abs(c.x) + abs(c.y);
    float r = fract(sin(dot(c + seed * 37.0, vec2(12.9898, 78.233))) * 43758.5453);
    return d < 1.5 ? 1.0 : d < 2.5 ? step(0.3, r) : step(0.8, r);
}

vec4 nether(int kind) {
    if (kind == 21) {
        vec2 c = floor((spot.xy * 0.5 + 0.5) * 4.999) - 2.0;
        float seed = texCoord.y;
        if (flake(c, seed) < 0.5) return vec4(0.0);
        float open = 4.0 - flake(c + vec2(1.0, 0.0), seed) - flake(c - vec2(1.0, 0.0), seed)
                - flake(c + vec2(0.0, 1.0), seed) - flake(c - vec2(0.0, 1.0), seed);
        float r = fract(sin(dot(c + seed * 11.0, vec2(39.3468, 11.135))) * 43758.5453);
        vec3 body = vec3(0.16, 0.13, 0.13) * (0.8 + 0.4 * r) * (c.x - c.y < 0.0 ? 1.3 : 0.8) * (0.35 + 0.65 * texCoord.x);
        return vec4(body + vertexColor.rgb * step(0.5, open) * (0.65 + 0.35 * r), vertexColor.a);
    }
    if (kind == 23) {
        float along = spot.x * 0.5 + 0.5;
        float across = abs(spot.y);
        float hot = smoothstep(0.65, 1.0, along) * (1.0 - across);
        return vec4(mix(vertexColor.rgb, vec3(1.0, 0.96, 0.82), hot), vertexColor.a * smoothstep(0.0, 0.85, along) * (1.0 - smoothstep(0.3, 1.0, across)));
    }
    vec2 c = floor((spot.xy * 0.5 + 0.5) * 8.999) - 4.0;
    float box = max(abs(c.x), abs(c.y));
    float plus = abs(c.x) + abs(c.y);
    float reach = kind == 22 ? 2.0 : 1.0;
    if (box <= reach) {
        vec3 tone = plus < 0.5 ? mix(vertexColor.rgb, vec3(1.0, 0.98, 0.9), 0.75)
                : plus <= reach - 0.5 ? mix(vertexColor.rgb, vec3(1.0, 0.95, 0.8), 0.35)
                : plus <= reach + 0.5 ? vertexColor.rgb : vertexColor.rgb * 0.5;
        return vec4(tone, vertexColor.a);
    }
    float d = length(spot.xy);
    return vec4(vertexColor.rgb, vertexColor.a * exp(-d * d * 5.0) * (0.4 + 0.2 * texCoord.x));
}

vec4 endMote(int look) {
    if (look == 32) {
        vec2 c = floor((spot.xy * 0.5 + 0.5) * 7.999) - 3.5;
        float d = length(c);
        bool broken = texCoord.x > 0.01 && mod(floor(c.x + c.y + 8.0), 2.0) > 0.5;
        if (c.x < -1.0 && c.x > -2.0 && c.y > 1.0 && c.y < 2.0 && texCoord.x < 0.01) return vec4(1.0, 1.0, 1.0, vertexColor.a);
        if (d > 3.6 || broken) return vec4(0.0);
        if (d > 2.4) return vec4(vertexColor.rgb, vertexColor.a * 0.85);
        return vec4(mix(vertexColor.rgb, vec3(0.55, 0.85, 1.0), 0.5), vertexColor.a * 0.16);
    }
    if (look == 36) {
        float band = floor((spot.x * 0.5 + 0.5) * 7.0);
        float across = 1.0 - abs(band - 3.0) / 3.5;
        float up = floor((spot.y * 0.5 + 0.5) * 14.0) / 14.0;
        float grow = smoothstep(0.0, 0.1, up) * (1.0 - up) * (1.0 - up);
        vec3 tone = mix(vec3(0.3, 0.95, 0.85), vec3(0.68, 0.46, 1.0), up);
        return vec4(mix(tone, vec3(1.0), across * across * 0.25), vertexColor.a * across * across * grow * (0.7 + 0.3 * texCoord.x));
    }
    if (look == 30) {
        float glow = 1.0 - smoothstep(0.0, 1.0, length(spot.xy));
        return vec4(mix(vertexColor.rgb, vec3(1.0), glow * glow * 0.6), vertexColor.a * glow * glow);
    }
    if (look == 29) {
        float across = 1.0 - spot.x * spot.x;
        float along = smoothstep(-1.0, -0.75, spot.y) * (1.0 - smoothstep(-0.3, 1.0, spot.y));
        return vec4(mix(vertexColor.rgb, vec3(1.0), smoothstep(0.75, 1.0, across) * 0.45), vertexColor.a * across * across * along);
    }
    vec2 p = abs(floor((spot.xy * 0.5 + 0.5) * 8.999) - 4.0);
    float reach = 1.0 + floor(texCoord.x * 3.0 + 0.5);
    float level = p.x + p.y < 0.5 ? 1.0 : p.x + p.y < 1.5 ? 0.8
            : min(p.x, p.y) < 0.5 && max(p.x, p.y) <= reach ? max(0.62 - (max(p.x, p.y) - 2.0) * 0.17, 0.3)
            : p.x == 1.0 && p.y == 1.0 ? 0.3 : 0.0;
    vec3 tone = level > 0.9 ? mix(vertexColor.rgb, vec3(1.0), 0.75) : level > 0.7 ? mix(vertexColor.rgb, vec3(1.0), 0.35)
            : level > 0.5 ? vertexColor.rgb : vertexColor.rgb * 0.6;
    return vec4(tone, level > 0.0 ? vertexColor.a * (level > 0.35 ? 1.0 : 0.55) : 0.0);
}

vec4 drip() {
    ivec2 c = ivec2(floor((spot.xy * 0.5 + 0.5) * vec2(2.999, 4.999)));
    bool body = c.y == 1 || c.y == 2 || c.x == 1;
    if (!body) return vec4(0.0);
    bool shine = c.x == 0 && c.y == 2;
    vec3 tone = shine ? vec3(1.0) : c.y == 0 ? vec3(0.42, 0.7, 0.95) : vec3(0.6, 0.84, 1.0);
    return vec4(tone * vertexColor.rgb, vertexColor.a);
}

void main() {
    vec4 color;
    if (spot.z > 25.5 && spot.z < 26.5) {
        color = drip();
    } else if (spot.z > 19.5 && spot.z < 23.5) {
        color = nether(int(spot.z + 0.5));
    } else if (spot.z > 27.5 && spot.z < 36.5) {
        color = endMote(int(spot.z + 0.5));
    } else if (spot.z > 1.5) {
        float d = length(spot.xy);
        color = vec4(vertexColor.rgb, vertexColor.a * (1.0 - smoothstep(0.15, 1.0, d)));
    } else if (spot.z > 0.5) {
        float d = length(spot.xy);
        float halo = 1.0 - smoothstep(0.0, 1.0, d);
        float core = 1.0 - smoothstep(0.0, 0.28, d);
        color = vec4(mix(vertexColor.rgb, vec3(0.95, 1.0, 0.55), core * 0.45), vertexColor.a * max(halo * halo * 0.7, core));
    } else {
        color = texture(Sampler0, texCoord) * vertexColor;
    }
    if (color.a < 0.01) {
        discard;
    }
    float fog = clamp((fogDistance - Clock.y) / max(Clock.z - Clock.y, 0.001), 0.0, 1.0);
    fragColor = vec4(mix(color.rgb, MoteFog.rgb, fog), color.a);
}
