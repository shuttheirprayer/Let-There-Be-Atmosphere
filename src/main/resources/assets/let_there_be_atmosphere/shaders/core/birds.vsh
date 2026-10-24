#version 150

uniform mat4 Transform;
uniform vec3 Offset;
uniform vec4 Clock;
uniform sampler2D DataSampler;
uniform sampler2D Sampler1;

out vec2 texCoord;
out vec3 tint;
out float fogDistance;

const vec4 FLAP[5] = vec4[](
        vec4(9.0, 55.0, -40.0, 4.0),
        vec4(6.5, 42.0, -30.0, 6.0),
        vec4(2.6, 30.0, -36.0, 3.0),
        vec4(2.3, 26.0, -28.0, 10.0),
        vec4(8.0, 55.0, -40.0, 4.0));
const vec4 GLIDE[5] = vec4[](
        vec4(0.85, 0.55, 1.0, 0.35),
        vec4(1.9, 0.45, 0.0, 0.0),
        vec4(6.0, 0.92, 0.0, 0.0),
        vec4(4.5, 0.28, 0.0, 0.0),
        vec4(1.1, 0.7, 0.6, 0.2));
const vec4 DRIFT[5] = vec4[](
        vec4(0.8, 0.5, 0.0, 0.0),
        vec4(1.4, 0.8, 2.6, 0.0),
        vec4(0.25, 0.15, 0.0, 0.0),
        vec4(1.2, 0.9, 0.0, 0.0),
        vec4(0.8, 0.5, 0.0, 0.0));

vec4 A;
vec4 B;
vec4 C;
vec4 D;
int species;

vec3 travel(float t) {
    float s = B.z * t;
    float lift = 0.0;
    if (D.y > 0.5) {
        s = B.z * (1.15 * t - 0.5 * (1.0 - exp(-t / 0.5)));
        lift = D.w * (1.0 - exp(-t / 0.6)) + 0.6 * t;
    }
    s -= C.x;
    float h = B.x + B.y * s;
    vec2 arc = abs(B.y) < 1.0e-5 ? vec2(cos(B.x), sin(B.x)) * s : vec2(sin(h) - sin(B.x), cos(B.x) - cos(h)) / B.y;
    vec2 side = vec2(-sin(h), cos(h));
    vec4 drift = DRIFT[species];
    vec4 glide = GLIDE[species];
    float seed = C.w * 6.2831853;
    vec3 wobble = vec3(sin(t * 0.43 + seed * 3.0) + 0.5 * sin(t * 1.1 + seed * 7.0),
            sin(t * 0.61 + seed * 5.0),
            cos(t * 0.37 + seed * 2.0) + 0.5 * cos(t * 0.97 + seed * 9.0));
    float swoop = drift.z * pow(max(0.0, sin(t * 0.31 + seed * 4.0)), 8.0);
    float bounce = glide.w * sin(6.2831853 * fract(t / glide.x + C.w * 3.17));
    vec3 p = A.xyz + vec3(arc.x, lift + C.z, arc.y) + vec3(side.x, 0.0, side.y) * C.y;
    return p + vec3(wobble.x * drift.x, wobble.y * drift.y - swoop + bounce, wobble.z * drift.x);
}

float wings(float t, out float frame, out float span) {
    vec4 flap = FLAP[species];
    vec4 glide = GLIDE[species];
    float cycle = fract(t / glide.x + C.w * 3.17);
    float k = smoothstep(0.0, 0.06, cycle) * (1.0 - smoothstep(glide.y - 0.08, glide.y, cycle));
    if (D.y > 0.5) k = max(k, 1.0 - smoothstep(1.4, 2.0, t));
    float psi = fract(flap.x * (t + 0.25 * min(t, 2.0) * D.y) + C.w * 7.3);
    float mid = radians(0.5 * (flap.y + flap.z));
    float amp = radians(0.5 * (flap.y - flap.z));
    float theta = mix(radians(flap.w), mid + amp * cos(6.2831853 * psi), k);
    if (k > 0.5) {
        frame = psi < 0.5 ? 0.0 : (abs(psi - 0.75) < 0.13 ? 2.0 : 1.0);
    } else {
        frame = glide.z > 0.5 ? 2.0 : 0.0;
    }
    span = mix(1.0 - 0.75 * glide.z, 1.0, k);
    return theta;
}

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    texCoord = vec2(0.0);
    tint = vec3(0.0);
    fogDistance = 0.0;
}

int piece(int k, bool camRight, bool camAbove, bool wingUp) {
    int farPlan = camRight ? 2 : 3;
    int nearPlan = camRight ? 3 : 2;
    int farWing = camRight ? 5 : 4;
    int nearWing = camRight ? 4 : 5;
    bool wingFirst = wingUp != camAbove;
    if (k == 0) return wingFirst ? farWing : farPlan;
    if (k == 1) return wingFirst ? farPlan : farWing;
    if (k == 2) return camAbove ? 1 : 0;
    if (k == 3) return camAbove ? 0 : 1;
    if (k == 4) return wingFirst ? nearWing : nearPlan;
    return wingFirst ? nearPlan : nearWing;
}

void main() {
    int slot = gl_VertexID / 36;
    int local = gl_VertexID - slot * 36;
    int k = local / 6;
    int corner = local - k * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    A = texelFetch(DataSampler, ivec2(slot, 0), 0);
    B = texelFetch(DataSampler, ivec2(slot, 1), 0);
    C = texelFetch(DataSampler, ivec2(slot, 2), 0);
    D = texelFetch(DataSampler, ivec2(slot, 3), 0);
    float t = Clock.x - A.w;
    if (B.w <= 0.0 || t < 0.0 || t > B.w) {
        hide();
        return;
    }
    species = clamp(int(D.x + 0.5), 0, 4);
    vec3 here = travel(t);
    vec3 forward = travel(t + 0.06) - here;
    forward = length(forward) > 1.0e-4 ? normalize(forward) : vec3(cos(B.x), 0.0, sin(B.x));
    vec3 right = cross(forward, vec3(0.0, 1.0, 0.0));
    right = length(right) > 1.0e-3 ? normalize(right) : vec3(-sin(B.x), 0.0, cos(B.x));
    vec3 up = cross(right, forward);
    float bank = clamp(B.y * B.z * 0.6, -0.7, 0.7);
    right = right * cos(bank) - up * sin(bank);
    up = cross(right, forward);

    float frame;
    float span;
    float theta = wings(t, frame, span);
    float grow = min(1.0, (B.w - t) * 0.8);
    if (D.y < 0.5) grow = min(grow, t * 2.0);
    vec3 view = here + Offset;
    float size = D.z * grow * (1.0 + 0.6 * smoothstep(24.0, 110.0, length(view)));
    int p = piece(k, dot(right, view) < 0.0, dot(up, view) < 0.0, theta > 0.0);
    float u = (vertex == 1 || vertex == 2) ? 1.0 : 0.0;
    float v = vertex >= 2 ? 1.0 : 0.0;
    if (p < 4) v = (p == 0 || p == 2) ? v * 0.5 : 0.5 + v * 0.5;
    vec3 world;
    float column;
    float shade;
    if (p < 2) {
        float tilt = dot(up, normalize(view)) * (p == 0 ? 1.0 : -1.0);
        world = here + forward * (u - 0.5) * size + up * (0.5 - v) * size * (1.0 - smoothstep(0.35, 0.85, tilt));
        column = 0.0;
        shade = 0.95;
    } else if (p < 4) {
        world = here + forward * (u - 0.5) * size + right * (v - 0.5) * size;
        bool top = dot(up, view) < 0.0;
        column = top ? 1.0 : 2.0;
        shade = top ? 1.0 : 0.88;
    } else {
        float side = p == 4 ? 1.0 : -1.0;
        vec3 reach = right * side * cos(theta) + up * sin(theta);
        vec3 normal = up * cos(theta) - right * side * sin(theta);
        world = here + right * side * (size / 16.0) + reach * u * size * span + forward * (0.5 - v) * size;
        bool top = dot(normal, view) < 0.0;
        column = (top ? 3.0 : 6.0) + frame;
        shade = top ? 1.0 : 0.88;
    }
    gl_Position = Transform * vec4(world, 1.0);
    texCoord = vec2((column + u) / 9.0, (float(species) + v) / 5.0);
    fogDistance = length(world + Offset);
    tint = texelFetch(Sampler1, ivec2(0, 15), 0).rgb * shade;
}
