#version 150

uniform mat4 Transform;
uniform vec4 Center[6];
uniform vec4 Size[6];
uniform vec4 Tint[6];
uniform vec3 AxisU;
uniform vec3 AxisV;

out vec2 texCoord;
out vec4 vertexColor;
out vec2 local;

void main() {
    int quad = gl_VertexID / 6;
    int corner = gl_VertexID - quad * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec4 center = Center[quad];
    vec4 size = Size[quad];
    float ax = size.x * size.z;
    float ay = size.x * size.w;
    float bx = -size.y * size.w;
    float by = size.y * size.z;
    float sa = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    float sb = vertex >= 2 ? 1.0 : -1.0;
    gl_Position = Transform * vec4(center.xyz + (sa * ax + sb * bx) * AxisU + (sa * ay + sb * by) * AxisV, 1.0);
    texCoord = vec2(center.w + ((vertex == 1 || vertex == 2) ? Tint[quad].a * 0.5 : 0.0), vertex >= 2 ? 0.0 : 1.0);
    vertexColor = vec4(Tint[quad].rgb, 1.0);
    local = vec2(sa * ax + sb * bx, sa * ay + sb * by);
}
