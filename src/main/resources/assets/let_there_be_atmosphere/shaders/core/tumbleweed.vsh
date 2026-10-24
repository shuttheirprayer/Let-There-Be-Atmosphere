#version 150

uniform mat4 Transform;
uniform vec3 Offset;
uniform vec4 Clock;
uniform sampler2D DataSampler;
uniform sampler2D Sampler1;

out vec2 texCoord;
out vec3 lightColor;
out float fogDistance;
out float fade;

mat3 spin(vec3 axis, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    float k = 1.0 - c;
    return mat3(c + axis.x * axis.x * k, axis.y * axis.x * k + axis.z * s, axis.z * axis.x * k - axis.y * s,
                axis.x * axis.y * k - axis.z * s, c + axis.y * axis.y * k, axis.z * axis.y * k + axis.x * s,
                axis.x * axis.z * k + axis.y * s, axis.y * axis.z * k - axis.x * s, c + axis.z * axis.z * k);
}

void main() {
    int slot = gl_VertexID / 18;
    int plane = (gl_VertexID - slot * 18) / 6;
    int corner = gl_VertexID - slot * 18 - plane * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec4 a = texelFetch(DataSampler, ivec2(slot, 0), 0);
    if (a.w <= 0.0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        texCoord = vec2(0.0);
        lightColor = vec3(0.0);
        fogDistance = 0.0;
        fade = 0.0;
        return;
    }
    vec4 b = texelFetch(DataSampler, ivec2(slot, 1), 0);
    vec4 c = texelFetch(DataSampler, ivec2(slot, 2), 0);
    vec4 d = texelFetch(DataSampler, ivec2(slot, 3), 0);
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    float sy = vertex >= 2 ? 1.0 : -1.0;
    vec3 local = plane == 0 ? vec3(sx, sy, 0.0) : (plane == 1 ? vec3(sx, 0.0, sy) : vec3(0.0, sx, sy));
    local *= d.x;
    float yaw = d.y * 6.283;
    float cy = cos(yaw);
    float sn = sin(yaw);
    local = vec3(local.x * cy - local.z * sn, local.y, local.x * sn + local.z * cy);
    float dt = clamp(Clock.x - c.w, 0.0, 0.07);
    local = spin(normalize(vec3(b.x, 0.0, b.y) + vec3(0.0001, 0.0, 0.0)), b.z + d.z * dt) * local;
    vec3 world = a.xyz + c.xyz * dt + local;
    gl_Position = Transform * vec4(world, 1.0);
    texCoord = vec2(sx > 0.0 ? 0.998 : 0.002, sy > 0.0 ? 0.002 : 0.998);
    int packed = int(b.w + 0.5);
    int block = packed / 16;
    float shade = plane == 0 ? 1.0 : (plane == 1 ? 0.84 : 0.7);
    lightColor = texelFetch(Sampler1, ivec2(block, packed - block * 16), 0).rgb * shade;
    float dist = length(world + Offset);
    fogDistance = dist;
    fade = a.w;
}
