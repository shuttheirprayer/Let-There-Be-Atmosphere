#version 150

uniform mat4 Transform;
uniform vec3 Offset;
uniform vec4 Clock;
uniform vec4 Origin;
uniform sampler2D DataSampler;
uniform sampler2D Sampler1;

out vec2 world;
out vec3 lightColor;
out float fogDistance;
out float fade;
flat out ivec2 land;
flat out vec2 origin;
flat out int stream;

void main() {
    int slot = gl_VertexID / 6;
    int corner = gl_VertexID - slot * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec4 a = texelFetch(DataSampler, ivec2(slot, 0), 0);
    if (a.w <= 0.0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        world = vec2(0.0);
        lightColor = vec3(0.0);
        fogDistance = 0.0;
        fade = 0.0;
        land = ivec2(0);
        origin = vec2(0.0);
        stream = 0;
        return;
    }
    vec4 b = texelFetch(DataSampler, ivec2(slot, 1), 0);
    vec2 xz = a.xz + vec2(vertex == 1 || vertex == 2 ? 1.0 : 0.0, vertex >= 2 ? 1.0 : 0.0);
    vec3 p = vec3(xz.x, a.y + 0.004, xz.y);
    gl_Position = Transform * vec4(p, 1.0);
    world = xz + Origin.xy;
    origin = a.xz + Origin.xy;
    land = ivec2(b.xy + 0.5);
    stream = int(b.w + 0.5);
    int word = int(a.w - 0.5);
    int block = word / 16;
    lightColor = texelFetch(Sampler1, ivec2(block, word - block * 16), 0).rgb;
    fogDistance = length(p + Offset);
    float age = Clock.x - b.z;
    if (age < 0.0) age += 3600.0;
    fade = (1.0 - smoothstep(22.0, 32.0, length((p + Offset).xz))) * clamp(age * 1.2, 0.0, 1.0);
}
