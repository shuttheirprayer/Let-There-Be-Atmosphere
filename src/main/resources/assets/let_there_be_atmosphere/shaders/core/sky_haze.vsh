#version 150

uniform mat4 Transform;
uniform vec4 Ring;

out float height;

const float TWO_PI = 6.283185307179586;
const int SEGMENTS = 32;

void main() {
    int quad = gl_VertexID / 6;
    int corner = gl_VertexID - quad * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    int ring = quad / SEGMENTS;
    int seg = quad - ring * SEGMENTS + ((vertex == 1 || vertex == 2) ? 1 : 0);
    int level = ring + (vertex >= 2 ? 1 : 0);
    float y = level == 0 ? Ring.x : (level == 1 ? Ring.y : Ring.z);
    float t = float(seg) * TWO_PI / float(SEGMENTS);
    height = y;
    gl_Position = Transform * vec4(cos(t) * Ring.w, y, sin(t) * Ring.w, 1.0);
    gl_Position.z = gl_Position.w;
}
