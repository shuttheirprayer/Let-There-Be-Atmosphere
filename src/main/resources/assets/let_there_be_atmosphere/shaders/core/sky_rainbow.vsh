#version 150

uniform mat4 Transform;
uniform vec3 Sun;

out vec4 vertexColor;

const float TWO_PI = 6.283185307179586;
const float DEG_TO_RAD = 0.017453292;
const float DISTANCE = 100.0;
const int SEGMENTS = 96;
const int PRIMARY_BANDS = 8;
const float STOPS[85] = float[](
        25.0, 1.0, 1.0, 1.0, 0.0,
        39.3, 1.0, 1.0, 1.0, 0.08,
        40.0, 0.55, 0.3, 0.95, 0.5,
        40.6, 0.3, 0.45, 1.0, 0.7,
        41.2, 0.3, 0.95, 0.4, 0.8,
        41.7, 1.0, 0.95, 0.35, 0.8,
        42.1, 1.0, 0.6, 0.2, 0.75,
        42.5, 1.0, 0.25, 0.2, 0.6,
        43.1, 1.0, 0.2, 0.2, 0.0,
        50.1, 1.0, 0.2, 0.2, 0.0,
        50.7, 1.0, 0.25, 0.2, 0.6,
        51.3, 1.0, 0.6, 0.2, 0.75,
        51.9, 1.0, 0.95, 0.35, 0.8,
        52.5, 0.3, 0.95, 0.4, 0.8,
        53.1, 0.3, 0.45, 1.0, 0.7,
        53.6, 0.55, 0.3, 0.95, 0.5,
        54.3, 0.55, 0.3, 0.95, 0.0);

float smoothEdge(float edge0, float edge1, float x) {
    float t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

vec4 ring(float degrees, int i) {
    float radius = degrees * DEG_TO_RAD;
    float along = cos(radius) * DISTANCE;
    float out_ = sin(radius) * DISTANCE;
    float t = float(i) * TWO_PI / float(SEGMENTS);
    float c = cos(t) * out_;
    float s = sin(t) * out_;
    float x = Sun.x * along - Sun.y * s;
    float y = Sun.y * along + Sun.x * s;
    return vec4(x, y, c, smoothEdge(-3.0, 5.0, y));
}

void main() {
    int quad = gl_VertexID / 6;
    int corner = gl_VertexID - quad * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    int strip = quad / SEGMENTS;
    int seg = quad - strip * SEGMENTS;
    bool outer = strip >= PRIMARY_BANDS;
    int first = outer ? strip + 2 : strip + 1;
    int a = (first - 1) * 5;
    int b = first * 5;
    float strength = outer ? Sun.z * 0.35 : Sun.z;
    float ia = STOPS[a + 4] * strength;
    float ib = STOPS[b + 4] * strength;
    vec4 a0 = ring(STOPS[a], seg);
    vec4 a1 = ring(STOPS[a], seg + 1);
    vec4 b0 = ring(STOPS[b], seg);
    vec4 b1 = ring(STOPS[b], seg + 1);
    float fa0 = ia * a0.w;
    float fa1 = ia * a1.w;
    float fb0 = ib * b0.w;
    float fb1 = ib * b1.w;
    if ((ia <= 0.0 && ib <= 0.0) || fa0 + fa1 + fb0 + fb1 <= 0.002) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        vertexColor = vec4(0.0);
        return;
    }
    vec4 point;
    float alpha;
    int stop;
    if (vertex == 0) { point = a0; alpha = fa0; stop = a; }
    else if (vertex == 1) { point = a1; alpha = fa1; stop = a; }
    else if (vertex == 2) { point = b1; alpha = fb1; stop = b; }
    else { point = b0; alpha = fb0; stop = b; }
    gl_Position = Transform * vec4(point.xyz, 1.0);
    gl_Position.z = gl_Position.w;
    vertexColor = floor(vec4(STOPS[stop + 1], STOPS[stop + 2], STOPS[stop + 3], alpha) * 255.0) / 255.0;
}
