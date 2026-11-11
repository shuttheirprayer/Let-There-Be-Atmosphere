#version 150

uniform sampler2D TileSampler;
uniform vec4 Tiles;

out vec2 texCoord;

void main() {
    int width = int(Tiles.x);
    int tile = gl_VertexID / 6;
    int k = gl_VertexID - tile * 6;
    ivec2 cell = ivec2(tile % width, tile / width);
    if (texelFetch(TileSampler, cell, 0).r < 0.5) {
        texCoord = vec2(0.0);
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }
    vec2 corner = vec2(k == 1 || k == 2 || k == 4 ? 1.0 : 0.0, k == 2 || k == 4 || k == 5 ? 1.0 : 0.0);
    vec2 uv = min((vec2(cell) + corner) * Tiles.zw, vec2(1.0));
    texCoord = uv;
    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
}
