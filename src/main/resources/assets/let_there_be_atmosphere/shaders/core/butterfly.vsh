#version 150

uniform mat4 Transform;
uniform vec3 Offset;
uniform vec4 Clock;
uniform sampler2D DataSampler;
uniform sampler2D Sampler1;

out vec2 texCoord;
out vec3 lightColor;
out float fogDistance;

vec3 path(vec3 home, float t, vec3 h) {
    return home + vec3(sin(t * 0.31 + h.x * 6.283) * 2.0 + sin(t * 0.83 + h.y * 6.283) * 0.6,
            0.35 + abs(sin(t * 0.47 + h.z * 6.283)) * 1.1 + sin(t * 6.0 + h.x * 20.0) * 0.06,
            cos(t * 0.27 + h.y * 6.283) * 2.0 + cos(t * 0.71 + h.z * 6.283) * 0.6);
}

void main() {
    int slot = gl_VertexID / 12;
    int local = gl_VertexID - slot * 12;
    int wing = local / 6;
    int corner = local - wing * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec4 a = texelFetch(DataSampler, ivec2(slot, 0), 0);
    vec4 b = texelFetch(DataSampler, ivec2(slot, 1), 0);
    float t = Clock.x - a.w;
    if (b.z <= 0.0 || t < 0.0 || t > b.z) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        texCoord = vec2(0.0);
        lightColor = vec3(0.0);
        fogDistance = 0.0;
        return;
    }
    float variant = floor(b.x);
    float s = fract(b.x);
    vec3 h = fract(vec3(s, s * 7.13, s * 13.7));
    float grow = min(1.0, min(t * 1.5, (b.z - t) * 1.5));
    vec3 here = path(a.xyz, t, h);
    vec3 ahead = path(a.xyz, t + 0.08, h);
    vec2 dir = ahead.xz - here.xz;
    vec2 f2 = length(dir) > 0.0001 ? normalize(dir) : vec2(0.0, 1.0);
    vec3 forward = vec3(f2.x, 0.0, f2.y);
    vec3 right = vec3(f2.y, 0.0, -f2.x);
    float side = wing == 0 ? -1.0 : 1.0;
    float flap = 0.15 + 1.15 * (0.5 + 0.5 * sin(t * 16.0 + s * 40.0));
    vec3 span = right * side * cos(flap) + vec3(0.0, sin(flap), 0.0);
    float along = (vertex == 1 || vertex == 2) ? 1.0 : 0.0;
    float out_ = vertex >= 2 ? 1.0 : 0.0;
    float size = (0.21 + 0.06 * h.y) * grow;
    vec3 world = here + forward * (along - 0.5) * size * 1.15 + span * out_ * size;
    gl_Position = Transform * vec4(world, 1.0);
    texCoord = vec2((variant + out_) * 0.25, 1.0 - along);
    int word = int(b.y + 0.5);
    int block = word / 16;
    lightColor = texelFetch(Sampler1, ivec2(block, word - block * 16), 0).rgb;
    fogDistance = length(world + Offset);
}
