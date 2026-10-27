#version 150

uniform mat4 Transform;
uniform vec3 Offset;
uniform vec4 Clock;
uniform vec3 Right;
uniform vec3 Up;
uniform sampler2D DataSampler;

out vec2 texCoord;
out float fade;
out float haze;

void main() {
    int slot = gl_VertexID / 6;
    int corner = gl_VertexID - slot * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec4 A = texelFetch(DataSampler, ivec2(slot, 0), 0);
    vec4 B = texelFetch(DataSampler, ivec2(slot, 1), 0);
    vec4 C = texelFetch(DataSampler, ivec2(slot, 2), 0);
    float t = Clock.x - A.w;
    if (B.w <= 0.0 || t < 0.0 || t > B.w) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        texCoord = vec2(0.0);
        fade = 0.0;
        haze = 0.0;
        return;
    }
    vec3 forward = vec3(B.x, 0.0, B.y);
    vec3 side = vec3(-B.y, 0.0, B.x);
    float seed = C.w * 6.2831853;
    float s = B.z * t;
    float calm = smoothstep(0.5, 1.8, t);
    float lateral = 2.2 * sin(s * 0.2 + 0.7) * smoothstep(3.0, 12.0, s) + C.x * pow(max(t - 1.5, 0.0), 1.3);
    float rise = 0.3 * t + C.y * pow(max(t - 1.0, 0.0), 1.35);
    vec3 flutter = vec3(sin(t * 5.3 + seed * 2.7) + 0.35 * sin(t * 13.0 + seed * 5.1),
            0.7 * sin(t * 7.1 + seed * 4.3) + 0.25 * sin(t * 17.0 + seed * 1.9),
            sin(t * 4.1 + seed * 3.7) + 0.35 * sin(t * 11.0 + seed * 6.3)) * 0.42 * calm;
    float flapRate = 6.5 + 3.0 * C.w;
    float flapPhase = fract(t * flapRate + C.w * 7.3);
    float frame = flapPhase < 0.5 ? 0.0 : 1.0;
    vec3 here = A.xyz + forward * s + side * lateral + vec3(0.0, rise + 0.12 * sin(6.2831853 * flapPhase), 0.0) + flutter;

    float u = (vertex == 1 || vertex == 2) ? 1.0 : 0.0;
    float v = vertex >= 2 ? 1.0 : 0.0;
    float flip = dot(forward, Right) < 0.0 ? -1.0 : 1.0;
    float dist = length(here + Offset);
    float size = C.z * (1.0 + 0.6 * smoothstep(24.0, 110.0, dist));
    vec3 world = here + Right * (u - 0.5) * size * flip + Up * (0.5 - v) * size * 0.75;
    gl_Position = Transform * vec4(world, 1.0);
    texCoord = vec2((frame + (flip > 0.0 ? u : 1.0 - u)) * 0.5, v);
    fade = (1.0 - smoothstep(B.w - 3.0, B.w, t)) * (1.0 - smoothstep(80.0, 140.0, dist));
    haze = 0.75 * smoothstep(30.0, 110.0, dist);
}
