#version 150

uniform mat4 Transform;
uniform float Visible;
uniform vec4 Path[16];
uniform vec4 Axis[16];
uniform vec4 Look[16];
uniform vec3 HeadColor[16];
uniform vec3 TrailColor[16];

out vec2 texCoord;
out vec4 vertexColor;
flat out int head;

const float RADIUS = 100.0;
const int SEGMENTS = 14;
const int CAPACITY = 16;

vec3 rotate(vec3 v, vec3 a, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return v * c + cross(a, v) * s + a * dot(a, v) * (1.0 - c);
}

vec3 quantize(vec3 rgb) {
    return floor(rgb * 255.0) / 255.0;
}

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    texCoord = vec2(0.0);
    vertexColor = vec4(0.0);
    head = 0;
}

void main() {
    int quad = gl_VertexID / 6;
    int corner = gl_VertexID - quad * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    bool tail = quad < CAPACITY * SEGMENTS;
    int comet = tail ? quad / SEGMENTS : quad - CAPACITY * SEGMENTS;
    vec4 look = Look[comet];
    if (look.w <= 0.0) {
        hide();
        return;
    }
    vec3 dir = Path[comet].xyz;
    vec3 axis = Axis[comet].xyz;
    float fade = look.x;

    if (tail) {
        int seg = quad - comet * SEGMENTS;
        float u0 = float(seg) / float(SEGMENTS);
        float u1 = float(seg + 1) / float(SEGMENTS);
        float taper0 = 1.0 - u0;
        float taper1 = 1.0 - u1;
        float a0 = min(1.0, fade * taper0 * taper0 * Visible);
        float a1 = min(1.0, fade * taper1 * taper1 * Visible);
        if (a0 <= 0.002 && a1 <= 0.002) {
            hide();
            return;
        }
        bool far = vertex == 1 || vertex == 2;
        float u = far ? u1 : u0;
        float a = far ? a1 : a0;
        float width = look.y * (1.0 - u * 0.72);
        float offset = vertex >= 2 ? -width : width;
        vec3 position = rotate(dir, axis, Path[comet].w - clamp(u, 0.0, 1.0) * Axis[comet].w) * RADIUS + axis * offset;
        gl_Position = Transform * vec4(position, 1.0);
        texCoord = vec2(u, vertex >= 2 ? 1.0 : 0.0);
        vertexColor = vec4(quantize(mix(HeadColor[comet], TrailColor[comet], u) * a), 1.0);
        head = 0;
        return;
    }

    float alpha = min(1.0, fade * Visible);
    if (alpha <= 0.002) {
        hide();
        return;
    }
    vec3 at = rotate(dir, axis, Path[comet].w);
    vec3 heading = cross(axis, at);
    float length = sqrt(dot(heading, heading));
    heading = length < 1.0E-5 ? vec3(0.0) : heading / length;
    float size = look.z;
    vec3 side = axis * size;
    vec3 forward = heading * size;
    float sx = vertex >= 2 ? 1.0 : -1.0;
    float fx = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    gl_Position = Transform * vec4(at * RADIUS + side * sx + forward * fx, 1.0);
    texCoord = vec2(vertex >= 2 ? 1.0 : 0.0, (vertex == 1 || vertex == 2) ? 1.0 : 0.0);
    vertexColor = vec4(quantize(HeadColor[comet] * alpha), 1.0);
    head = 1;
}
