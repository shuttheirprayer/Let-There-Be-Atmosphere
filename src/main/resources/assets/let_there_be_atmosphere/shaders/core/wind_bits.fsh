#version 150

uniform vec4 Clock;
uniform vec4 BitFog;

in vec2 spot;
in vec4 vertexColor;
in float fogDistance;
in vec3 look;

out vec4 fragColor;

vec4 leaf(vec2 s, int frame) {
    ivec2 c = ivec2(floor((s * 0.5 + 0.5) * 4.999)) - 2;
    bool on;
    float shade = 1.0;
    if (frame == 0) {
        on = (c.y == 0 && abs(c.x) <= 1) || c == ivec2(0, 1);
        shade = c.x == 1 ? 1.12 : c.x == 0 && c.y == 0 ? 0.82 : 1.0;
    } else if (frame == 1) {
        on = (c.x == c.y && abs(c.x) <= 1) || c == ivec2(-1, 0);
        shade = c.x == 1 ? 1.12 : c.x == 0 ? 0.82 : 1.0;
    } else if (frame == 2) {
        on = (c.x == 0 && abs(c.y) <= 1) || c == ivec2(1, 0);
        shade = c.y == 1 ? 1.12 : c.y == 0 && c.x == 0 ? 0.82 : 1.0;
    } else {
        on = (c.x == -c.y && abs(c.x) <= 1) || c == ivec2(1, 0);
        shade = c.x == -1 ? 1.12 : c.x == 0 ? 0.82 : 1.0;
    }
    return on ? vec4(vertexColor.rgb * shade, vertexColor.a) : vec4(0.0);
}

vec4 blade(vec2 s, int frame) {
    ivec2 c = ivec2(floor((s * 0.5 + 0.5) * 4.999)) - 2;
    if ((frame & 1) == 1) c.x = -c.x;
    bool on = c == ivec2(0, -1) || c == ivec2(0, 0) || c == ivec2(1, 1) || c == ivec2(1, 2);
    float shade = c.y < 0 ? 0.8 : c.y > 1 ? 1.15 : 1.0;
    return on ? vec4(vertexColor.rgb * shade, vertexColor.a) : vec4(0.0);
}

vec4 streak(vec2 s) {
    float body = smoothstep(-1.0, -0.35, s.x) * (1.0 - smoothstep(0.35, 1.0, s.x));
    float level = floor(body * 4.0) / 4.0;
    return vec4(vertexColor.rgb, vertexColor.a * level);
}

void main() {
    int kind = int(look.x + 0.5);
    int frame = int(look.y + 0.5);
    vec4 color;
    if (kind == 0) {
        color = vertexColor;
    } else if (kind == 1) {
        color = leaf(spot, frame);
    } else if (kind == 2) {
        color = blade(spot, frame);
    } else {
        color = streak(spot);
    }
    if (color.a < 0.01) {
        discard;
    }
    float fog = clamp((fogDistance - Clock.y) / max(Clock.z - Clock.y, 0.001), 0.0, 1.0);
    fragColor = vec4(mix(color.rgb, BitFog.rgb, fog), color.a);
}
