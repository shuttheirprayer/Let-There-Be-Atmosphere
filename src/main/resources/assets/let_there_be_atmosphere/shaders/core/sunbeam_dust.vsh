#version 150

uniform sampler2D LitSampler;
uniform sampler2D HeightSampler;
uniform mat4 Transform;
uniform vec4 Slope;
uniform vec4 Camera;
uniform vec4 Anchor;
uniform vec3 Right;
uniform vec3 Up;
uniform vec4 Tint;

out vec4 vertexColor;

const vec3 BOX = vec3(12.0, 7.0, 12.0);
const float TURN = 0.00174533;

float sunlit(vec3 l) {
    if (any(lessThan(l, vec3(0.0))) || any(greaterThanEqual(l, vec3(32.0)))) return 0.0;
    int i = int(floor((l.x - l.y * Slope.x - Slope.y) * Slope.z));
    if (l.y <= texelFetch(LitSampler, ivec2(clamp(i, 0, int(Slope.w) - 1), int(l.z)), 0).r) return 0.0;
    return l.y < texelFetch(HeightSampler, ivec2(int(l.x) + 48, int(l.z)), 0).g ? 1.0 : 0.0;
}

vec3 hash(float n) {
    return fract(sin(vec3(n * 12.9898, n * 78.233, n * 37.719)) * 43758.5453);
}

void main() {
    int id = gl_VertexID / 6;
    int v = gl_VertexID - id * 6;
    vec2 corner = vec2(v == 1 || v == 2 || v == 4 ? 1.0 : -1.0, v == 2 || v == 4 || v == 5 ? 1.0 : -1.0);
    vec3 h = hash(float(id) + 1.0);
    vec3 g = hash(float(id) + 517.0);
    float t = Anchor.w;
    vec3 rel = mod(h * BOX - Anchor.xyz, BOX) - BOX * 0.5;
    vec3 edge = abs(rel) / (BOX * 0.5);
    rel += vec3(sin(t * TURN * (180.0 + floor(g.x * 120.0)) + h.y * 6.283), sin(t * TURN * (140.0 + floor(g.y * 90.0)) + h.z * 6.283) * 0.5,
            sin(t * TURN * (200.0 + floor(g.z * 100.0)) + h.x * 6.283)) * 0.3;
    float d = length(rel);
    float glow = sunlit(Camera.xyz + rel) * (1.0 - smoothstep(0.75, 1.0, max(max(edge.x, edge.y), edge.z))) * smoothstep(1.0, 2.2, d)
            * (0.6 + 0.4 * sin(t * TURN * (600.0 + floor(g.x * 400.0)) + g.y * 6.283));
    float alpha = Tint.w * glow;
    if (alpha < 0.004) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        vertexColor = vec4(0.0);
        return;
    }
    float size = 0.018 + 0.016 * g.z;
    vertexColor = vec4(Tint.rgb * alpha, alpha);
    gl_Position = Transform * vec4(rel + (Right * corner.x + Up * corner.y) * size, 1.0);
}
