#version 150

uniform mat4 Transform;
uniform vec4 Ring;
uniform vec3 Fade;
uniform vec3 Tint;

out vec4 vertexColor;

const float TWO_PI = 6.283185307179586;
const int SEGMENTS = 32;

void main() {
    int quad = gl_VertexID / 6;
    int corner = gl_VertexID - quad * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    int ring = quad / SEGMENTS;
    int seg = quad - ring * SEGMENTS + ((vertex == 1 || vertex == 2) ? 1 : 0);
    bool upper = vertex >= 2;
    int level = ring + (upper ? 1 : 0);
    float y = level == 0 ? Ring.x : (level == 1 ? Ring.y : Ring.z);
    float a = level == 0 ? Fade.x : (level == 1 ? Fade.y : Fade.z);
    float t = float(seg) * TWO_PI / float(SEGMENTS);
    gl_Position = Transform * vec4(cos(t) * Ring.w, y, sin(t) * Ring.w, 1.0);
    vertexColor = vec4(Tint, floor(a * 255.0) / 255.0);
}
