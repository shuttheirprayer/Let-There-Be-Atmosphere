#version 150

uniform mat4 Transform;
uniform vec3 Offset;
uniform vec4 Clock;
uniform sampler2D DataSampler;
uniform sampler2D Sampler1;

out vec2 texCoord;
out vec3 lightColor;
out float fogDistance;
out float lift;

const float G = 16.0;
const int SEGMENTS = 6;

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    texCoord = vec2(0.0);
    lightColor = vec3(0.0);
    fogDistance = 0.0;
    lift = 0.0;
}

void main() {
    int slot = gl_VertexID / (SEGMENTS * 12);
    int local = gl_VertexID - slot * SEGMENTS * 12;
    int plane = local / (SEGMENTS * 6);
    int inPlane = local - plane * SEGMENTS * 6;
    int seg = inPlane / 6;
    int corner = inPlane - seg * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec4 a = texelFetch(DataSampler, ivec2(slot, 0), 0);
    vec4 b = texelFetch(DataSampler, ivec2(slot, 1), 0);
    vec4 c = texelFetch(DataSampler, ivec2(slot, 2), 0);
    float t = Clock.x - a.w;
    if (b.w <= 0.0 || t < 0.0 || t > b.w) {
        hide();
        return;
    }
    vec3 here = a.xyz + b.xyz * t + vec3(0.0, -0.5 * G * t * t, 0.0);
    vec3 forward = normalize(b.xyz + vec3(0.0, -G * t, 0.0));
    vec3 right = normalize(cross(forward, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(right, forward);
    float len = c.z;
    float u = (vertex == 1 || vertex == 2) ? float(seg + 1) / float(SEGMENTS) : float(seg) / float(SEGMENTS);
    float v = vertex >= 2 ? 1.0 : 0.0;
    float phase = t * 30.0 + c.w * 6.2831853;
    float w = clamp((0.6 - u) / 0.6, 0.0, 1.0);
    float swing = w * w * len * (0.1 + 0.16 * exp(-t * 2.5));
    vec3 world = here + forward * (u - 0.5) * len + right * sin(phase) * swing + up * cos(phase) * swing * 0.35;
    float shade;
    if (plane == 0) {
        world += up * (0.5 - v) * len * 0.5;
        shade = 0.95;
    } else {
        world += right * (v - 0.5) * len * 0.5;
        shade = 1.0;
    }
    gl_Position = Transform * vec4(world, 1.0);
    texCoord = vec2((c.x + u) / 6.0, plane == 0 ? v * 0.5 : 0.5 + v * 0.5);
    int word = int(c.y + 0.5);
    int block = word / 16;
    lightColor = texelFetch(Sampler1, ivec2(block, word - block * 16), 0).rgb * shade;
    fogDistance = length(world + Offset);
    lift = world.y - a.y;
}
