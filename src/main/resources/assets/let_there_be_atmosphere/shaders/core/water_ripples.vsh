#version 150

uniform mat4 Transform;
uniform vec3 Offset;
uniform vec4 Clock;
uniform vec4 RippleFog;
uniform sampler2D DataSampler;
uniform sampler2D Sampler1;

out vec2 world;
out vec3 lightColor;
out float fogDistance;
flat out vec4 tile;

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    world = vec2(0.0);
    lightColor = vec3(0.0);
    fogDistance = 0.0;
    tile = vec4(0.0);
}

void main() {
    int quad = gl_VertexID / 6;
    int corner = gl_VertexID - quad * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    int slot = quad / 16;
    int block = quad - slot * 16;
    vec4 head = texelFetch(DataSampler, ivec2(0, slot), 0);
    if (head.w < 0.5) {
        hide();
        return;
    }
    vec4 meta = texelFetch(DataSampler, ivec2(1 + block, slot), 0);
    if (meta.y < 0.5) {
        hide();
        return;
    }
    vec2 q = vec2(vertex == 1 || vertex == 2 ? 1.0 : 0.0, vertex >= 2 ? 1.0 : 0.0);
    vec2 cell = vec2(float(block & 3), float(block >> 2));
    vec2 xz = head.xy + cell + q;
    vec3 p = vec3(xz.x, meta.x + 0.005, xz.y);
    gl_Position = Transform * vec4(p, 1.0);
    world = xz;
    tile = vec4(float(slot), head.x, head.y, head.z);
    int word = int(meta.z + 0.5);
    int blockLight = word / 16;
    lightColor = texelFetch(Sampler1, ivec2(blockLight, word - blockLight * 16), 0).rgb;
    fogDistance = length(p + Offset);
}
